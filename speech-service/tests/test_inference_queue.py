import asyncio
from fastapi import HTTPException
from app.inference_queue import InferenceQueue

def test_explicit_cancellation_before_arrival_and_during_inference():
    async def scenario():
        queue=InferenceQueue();queue.cancel('late')
        async def forbidden(event):raise AssertionError('Cancelled request started')
        try:await queue.run(forbidden,request_id='late')
        except HTTPException as error:assert error.status_code==499
        else:raise AssertionError('Cancellation tombstone ignored')
        entered=asyncio.Event();release=asyncio.Event()
        async def work(event):entered.set();await release.wait();assert event.is_set()
        pending=asyncio.create_task(queue.run(work,request_id='running'));await entered.wait();queue.cancel('running')
        try:await pending
        except HTTPException as error:assert error.status_code==499
        else:raise AssertionError('Explicit cancellation ignored')
        assert len(queue.jobs)==1
        release.set();await asyncio.sleep(.01)
        assert not queue.jobs and not queue.requests
    asyncio.run(scenario())

def test_queued_cancellation_immediately_releases_admission():
    async def scenario():
        queue=InferenceQueue(concurrency=1,waiting=1);entered=asyncio.Event();release=asyncio.Event()
        async def work(event):entered.set();await release.wait()
        running=asyncio.create_task(queue.run(work,request_id='running'));await entered.wait()
        queued=asyncio.create_task(queue.run(work,request_id='queued'));await asyncio.sleep(0)
        assert len(queue.jobs)==2
        queue.cancel('queued');assert len(queue.jobs)==1
        next_job=asyncio.create_task(queue.run(work,request_id='next'))
        release.set();await running;await next_job
        try:await queued
        except HTTPException as error:assert error.status_code==499
        else:raise AssertionError('Queued request was not cancelled')
    asyncio.run(scenario())

def test_disconnected_inference_retains_slot_until_worker_exits():
    async def scenario():
        queue=InferenceQueue(concurrency=1,waiting=1)
        entered=asyncio.Event();release=asyncio.Event();second_entered=asyncio.Event()
        class Request:
            async def is_disconnected(self):return entered.is_set()
        async def operation(cancelled):
            entered.set();await release.wait();assert cancelled.is_set()
        try:await queue.run(operation,Request())
        except HTTPException as error:assert error.status_code==499
        assert len(queue.jobs)==1
        async def second(cancelled):second_entered.set();return 'next'
        next_job=asyncio.create_task(queue.run(second));await asyncio.sleep(.01)
        assert not second_entered.is_set()
        try:await queue.run(second)
        except HTTPException as error:assert error.status_code==429
        else:raise AssertionError('Unbounded admission')
        release.set();assert await next_job=='next'
        await asyncio.sleep(0);assert not queue.jobs
    asyncio.run(scenario())

def test_cancelled_queued_request_never_reaches_inference():
    async def scenario():
        queue=InferenceQueue();release=asyncio.Event();entered=asyncio.Event()
        async def first(cancelled):entered.set();await release.wait()
        running=asyncio.create_task(queue.run(first));await entered.wait()
        class Request:
            async def is_disconnected(self):return True
        async def forbidden(cancelled):raise AssertionError('Cancelled queued job started')
        try:await queue.run(forbidden,Request())
        except HTTPException as error:assert error.status_code==499
        release.set();await running
    asyncio.run(scenario())
