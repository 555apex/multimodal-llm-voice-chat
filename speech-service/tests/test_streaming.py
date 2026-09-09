from fastapi.testclient import TestClient
from app.main import create_app
from app.streaming_tts import text_segments
from test_main import settings, FakeAsr, FakeTts


class FakeStreamingTts(FakeTts):
    async def stream(self,text):
        yield b'\x00\x00\x10\x00'
        if text=='failure': raise RuntimeError('internal details')
        yield b'\x20\x00'


def test_stream_has_sequenced_audio_and_explicit_completion():
    with TestClient(create_app(settings(),FakeAsr(),FakeStreamingTts(),False)) as client:
        response=client.post('/v1/tts/speech/stream',json={'text':'你好'})
        assert response.status_code==200
        assert 'audio.start' in response.text
        assert '"sequence":0' in response.text and '"sequence":1' in response.text
        assert 'audio.completed' in response.text
        assert '"chunks":2' in response.text


def test_failed_stream_does_not_claim_completion_or_expose_internal_error():
    with TestClient(create_app(settings(),FakeAsr(),FakeStreamingTts(),False)) as client:
        text=client.post('/v1/tts/speech/stream',json={'text':'failure'}).text
        assert 'audio.failed' in text
        assert 'audio.completed' not in text
        assert 'internal details' not in text


def test_text_segmentation_preserves_text_and_identifiers():
    text='道路情况。'*35+'G104-123.5公里，继续处置。'
    chunks=list(text_segments(text))
    assert ''.join(chunks)==text
    assert all(len(x)<=160 for x in chunks)
    assert any('G104-123.5' in x for x in chunks)


def test_cancelled_stream_drains_before_reusing_the_worker():
    import asyncio, queue, threading
    from app.streaming_tts import StreamingTtsEngine
    class Process:
        def is_alive(self): return True
    incoming, outgoing, cancelled = queue.Queue(1), queue.Queue(8), threading.Event()
    engine = StreamingTtsEngine(settings())
    engine.workers = [(Process(), incoming, outgoing, cancelled)]
    def produce():
        for _ in range(2):
            text = incoming.get(timeout=2)
            outgoing.put(('chunk', text.encode()))
            if text == 'first':
                cancelled.wait(2)
                outgoing.put(('chunk', b'stale'))
            outgoing.put(('done', None))
    producer = threading.Thread(target=produce)
    producer.start()
    async def check():
        stream = engine._stream_segment('first')
        assert await anext(stream) == b'first'
        await stream.aclose()
        assert engine.available.qsize() == 1
        assert [chunk async for chunk in engine._stream_segment('second')] == [b'second']
        assert engine.available.qsize() == 1
    asyncio.run(check())
    producer.join(3)
    assert not producer.is_alive()
