/*
 * Reads a Server-Sent Events response (text/event-stream) that apiStream() opened.
 *
 * The format is plain text, one field per line; an empty line ends an event:
 *
 *   event: tracking
 *   data: {"orderId":18,"status":"OUT_FOR_DELIVERY",...}
 *
 *   : keep-alive          <- a comment: the server's "I am still here" every 15 s
 *
 * (The browser's EventSource reads this format by itself, but it cannot send the login token, so we read it ourselves.)
 */

/** One event: its name ("tracking") and its data (the text after "data:"). */
export interface StreamEvent {
  event: string
  data: string
}

/**
 * Reads the stream until the server closes it (resolves) or the connection breaks / is aborted (rejects).
 * onEvent gets every complete event; onActivity is called for anything that arrives, also keep-alives, so the caller
 * can notice a connection that went silent.
 */
export async function readEventStream(response: Response, onEvent: (event: StreamEvent) => void, onActivity?: () => void): Promise<void> {
  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let name = 'message'
  let dataLines: string[] = []

  for (;;) {
    const { value, done } = await reader.read()
    if (done) return
    onActivity?.()
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? '' // the last part may be a line that is not complete yet

    for (const rawLine of lines) {
      const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
      if (line === '') {
        // an empty line: the event is complete
        if (dataLines.length > 0) onEvent({ event: name, data: dataLines.join('\n') })
        name = 'message'
        dataLines = []
      } else if (!line.startsWith(':')) {
        // "field:value" (one space after the colon is not part of the value); ":..." lines are comments
        const colon = line.indexOf(':')
        const field = colon === -1 ? line : line.slice(0, colon)
        const fieldValue = colon === -1 ? '' : line.slice(colon + 1).replace(/^ /, '')
        if (field === 'event') name = fieldValue
        else if (field === 'data') dataLines.push(fieldValue)
      }
    }
  }
}
