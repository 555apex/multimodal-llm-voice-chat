"""Bounded admission; cancellation never releases a still-running inference slot."""
import asyncio
import time
import threading
from fastapi import HTTPException

class InferenceQueue:
    def __init__(self, concurrency=1, waiting=2, timeout=90):
        self.semaphore=asyncio.Semaphore(concurrency)
        self.capacity=concurrency+waiting
        self.jobs=set()
        self.timeout=timeout
        self.requests={}
        self.queued={}
        self.cancelled_ids={}

    def cancel(self, request_id):
        now=time.monotonic()
        self.cancelled_ids={key:until for key,until in self.cancelled_ids.items() if until>now}
        if len(self.cancelled_ids)>=256:self.cancelled_ids.pop(next(iter(self.cancelled_ids)))
        self.cancelled_ids[request_id]=now+self.timeout
        if request_id in self.requests:self.requests[request_id].set()
        task=self.queued.pop(request_id,None)
        if task is not None:
            task.cancel()
            self.jobs.discard(task)

    async def run(self, operation, request=None, request_id=None):
        if request_id and self.cancelled_ids.get(request_id,0)>time.monotonic():raise HTTPException(499,'Request cancelled')
        if request_id in self.requests:raise HTTPException(409,'Duplicate speech request')
        if len(self.jobs)>=self.capacity:
            raise HTTPException(429,'Speech service is busy')
        cancelled=threading.Event()
        if request_id:self.requests[request_id]=cancelled
        started=False
        async def worker():
            nonlocal started
            async with self.semaphore:
                if request_id:self.queued.pop(request_id,None)
                if cancelled.is_set():return None
                started=True
                return await operation(cancelled)
        task=asyncio.create_task(worker())
        self.jobs.add(task)
        if request_id:self.queued[request_id]=task
        def finished(done):
            self.jobs.discard(done)
            if request_id:self.requests.pop(request_id,None)
            if request_id:self.queued.pop(request_id,None)
            if not done.cancelled():done.exception()
        task.add_done_callback(finished)
        deadline=time.monotonic()+self.timeout
        try:
            while not task.done():
                if cancelled.is_set():raise HTTPException(499,'Request cancelled')
                if request is not None and await request.is_disconnected():
                    raise HTTPException(499,'Client disconnected')
                if time.monotonic()>=deadline:raise HTTPException(504,'Speech processing timed out')
                await asyncio.wait({task},timeout=.05)
            if cancelled.is_set():raise HTTPException(499,'Request cancelled')
            return task.result()
        finally:
            if not task.done():
                cancelled.set()
                if not started:task.cancel()
                # Running calls observe cancelled at a safe inference checkpoint.
                # Calls without cancellation support retain their slot until exit.
