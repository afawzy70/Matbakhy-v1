"""MB-032 / TC-049 (CI only): a TCP proxy that forwards requests at once but holds every response back for DELAY
seconds. Placed between the app and the API it makes "the server created the order, the app never heard back"
deterministic: the test kills the app while the response is held.
Usage: python3 slow_proxy.py LISTEN_PORT UPSTREAM_PORT DELAY_SECONDS"""
import asyncio
import sys

LISTEN, UPSTREAM, DELAY = int(sys.argv[1]), int(sys.argv[2]), float(sys.argv[3])


async def pipe(reader, writer, delay):
    try:
        while data := await reader.read(65536):
            if delay:
                await asyncio.sleep(delay)
            writer.write(data)
            await writer.drain()
    except (ConnectionError, asyncio.CancelledError):
        pass
    finally:
        writer.close()


async def handle(client_reader, client_writer):
    try:
        up_reader, up_writer = await asyncio.open_connection("127.0.0.1", UPSTREAM)
    except OSError:
        client_writer.close()
        return
    await asyncio.gather(pipe(client_reader, up_writer, 0), pipe(up_reader, client_writer, DELAY))


async def main():
    server = await asyncio.start_server(handle, "127.0.0.1", LISTEN)
    async with server:
        await server.serve_forever()


asyncio.run(main())
