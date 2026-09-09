"""Isolated CUDA-graph workers; each request owns its worker until completion."""
from __future__ import annotations
import asyncio
import multiprocessing as mp
import queue
import subprocess
import time
import re


def text_segments(text, maximum=160):
    current=''
    for token in re.findall(r'[A-Za-z0-9.+%_-]+|.', text, flags=re.S):
        if current and len(current)+len(token)>maximum:
            yield current
            current=''
        current+=token
    if current: yield current


def _worker(settings, requests, replies, cancelled):
    try:
        import numpy as np
        import torch
        from faster_qwen3_tts import FasterQwen3TTS
        torch.set_num_threads(2)
        model = FasterQwen3TTS.from_pretrained(settings.tts_model_path,
            device=settings.tts_device, dtype=settings.tts_dtype, max_seq_len=1024,
            attn_implementation='sdpa', local_files_only=True)
        model.warmup()
        for _ in model.generate_custom_voice_streaming(text='路智通已准备就绪。',
                speaker=settings.tts_voice.lower(), language=settings.tts_language, chunk_size=8):
            pass
        replies.put(('ready', None))
    except Exception as exc:
        replies.put(('error', str(exc)))
        return
    while True:
        text = requests.get()
        if text is None: return
        try:
            for audio, rate, _timing in model.generate_custom_voice_streaming(
                    text=text, language=settings.tts_language,
                    speaker=settings.tts_voice.lower(), chunk_size=24, max_new_tokens=800):
                if cancelled.is_set(): break
                if rate != 24000: raise ValueError('Expected 24 kHz audio')
                pcm=(np.clip(audio, -1, 1)*32767).astype('<i2').tobytes()
                while not cancelled.is_set():
                    try:
                        replies.put(('chunk', pcm), timeout=.2)
                        break
                    except queue.Full: pass
            replies.put(('done', None))
        except Exception as exc:
            replies.put(('error', str(exc)))


def _receive(q):
    try: return q.get(timeout=.2)
    except queue.Empty: return None


class StreamingTtsEngine:
    def __init__(self, settings):
        self.settings=settings
        self.workers=[]
        self.available=None
        self.cleanup_tasks=set()
        self.failed=False

    def healthy(self):
        return not self.failed and bool(self.workers) and all(worker[0].is_alive() for worker in self.workers)

    def load(self):
        ctx=mp.get_context('spawn')
        for _ in range(self.settings.tts_max_concurrency):
            incoming, outgoing, cancelled=ctx.Queue(1), ctx.Queue(8), ctx.Event()
            process=ctx.Process(target=_worker,args=(self.settings,incoming,outgoing,cancelled),daemon=True)
            process.start()
            self.workers.append((process,incoming,outgoing,cancelled))
            try: status, detail=outgoing.get(timeout=300)
            except Exception:
                self.close()
                raise
            if status!='ready':
                self.close()
                raise RuntimeError('TTS worker warmup failed: '+str(detail))

    def close(self):
        for process, incoming, outgoing, cancelled in self.workers:
            cancelled.set()
            if process.is_alive(): process.terminate()
            process.join(timeout=5)
            incoming.close(); outgoing.close()

    async def stream(self,text):
        for segment in text_segments(text):
            async for chunk in self._stream_segment(segment): yield chunk

    async def _stream_segment(self,text):
        if self.available is None:
            self.available=asyncio.Queue()
            for worker in self.workers: self.available.put_nowait(worker)
        worker=await asyncio.wait_for(self.available.get(),timeout=30)
        process,incoming,outgoing,cancelled=worker
        finished=False
        cancelled.clear()
        deadline=time.monotonic()+180
        try:
            incoming.put_nowait(text)
            while True:
                if time.monotonic()>deadline: raise TimeoutError('TTS generation timed out')
                item=await asyncio.to_thread(_receive,outgoing)
                if item is None:
                    if not process.is_alive(): raise RuntimeError('TTS worker stopped')
                    if time.monotonic()>deadline: raise TimeoutError('TTS generation timed out')
                    continue
                kind,data=item
                if kind in ('done','error'):
                    finished=True
                    if kind=='error': raise RuntimeError(data)
                    break
                yield data
        finally:
            cancelled.set()
            async def release():
                reusable=finished
                if not reusable:
                    until=time.monotonic()+10
                    while process.is_alive() and time.monotonic()<until:
                        item=await asyncio.to_thread(_receive,outgoing)
                        if item and item[0] in ('done','error'):
                            reusable=True
                            break
                if reusable and process.is_alive(): self.available.put_nowait(worker)
                else:
                    self.failed=True
                    if process.is_alive(): process.terminate()
                    await asyncio.to_thread(process.join, 5)
            # Response cancellation must not cancel cleanup and leak the worker slot.
            cleanup=asyncio.create_task(release())
            self.cleanup_tasks.add(cleanup)
            cleanup.add_done_callback(self.cleanup_tasks.discard)
            try: await asyncio.shield(cleanup)
            except asyncio.CancelledError: pass

    async def synthesize(self,text):
        chunks=[chunk async for chunk in self.stream(text)]
        def encode():
            return subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-f','s16le',
                '-ar','24000','-ac','1','-i','pipe:0','-codec:a','libmp3lame','-b:a','96k',
                '-f','mp3','pipe:1'],input=b''.join(chunks),capture_output=True,check=True).stdout
        return await asyncio.to_thread(encode)
