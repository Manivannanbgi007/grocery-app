import { describe, expect, it, vi } from 'vitest'
import { readEventStream, type StreamEvent } from './eventStream'

/** A Response whose body arrives in the given pieces (as the network may cut it anywhere). */
function streamOf(...chunks: string[]): Response {
  const encoder = new TextEncoder()
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
      controller.close()
    },
  })
  return new Response(body, { headers: { 'Content-Type': 'text/event-stream' } })
}

describe('readEventStream', () => {
  it('reads named events, also when the network cuts them into pieces', async () => {
    const events: StreamEvent[] = []

    await readEventStream(streamOf('event:tracking\nda', 'ta:{"orderId":18}\n', '\nevent: tracking\ndata: {"orderId":19}\n\n'), (event) => events.push(event))

    expect(events).toEqual([
      { event: 'tracking', data: '{"orderId":18}' },
      { event: 'tracking', data: '{"orderId":19}' },
    ])
  })

  it('skips keep-alive comments, but counts them as activity', async () => {
    const events: StreamEvent[] = []
    const activity = vi.fn()

    await readEventStream(streamOf(':keep-alive\n\n', 'data:hello\r\n\r\n'), (event) => events.push(event), activity)

    expect(events).toEqual([{ event: 'message', data: 'hello' }])
    expect(activity).toHaveBeenCalledTimes(2)
  })

  it('joins several data lines of one event', async () => {
    const events: StreamEvent[] = []

    await readEventStream(streamOf('data: first\ndata: second\n\n'), (event) => events.push(event))

    expect(events).toEqual([{ event: 'message', data: 'first\nsecond' }])
  })
})
