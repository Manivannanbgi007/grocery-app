import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { speakText } from '../../api/chat'
import { speakableText } from './chatLinks'

/**
 * "Fresh Mart has 3 new orders. The oldest is 41." -> ["Fresh Mart has 3 new orders.", "The oldest is 41."]
 * The speech server makes about 50 characters a second, so the first sentence alone is ready in about 2 s and plays
 * while the rest is made. A first "sentence" shorter than 20 characters ("1.", "Rs.") is not split off.
 * A Markdown link is read as its label, not its address.
 */
export function partsToRead(text: string): string[] {
  const clean = speakableText(text).trim()
  const match = /^(.{20,}?[.!?])\s+(\S[\s\S]*)$/.exec(clean)
  return match ? [match[1], match[2]] : [clean]
}

/** One answer being read: stop() aborts the requests, stops the sound and frees the audio. */
interface Reading {
  controller: AbortController
  audio: HTMLAudioElement | null
  urls: string[]
}

function endReading(reading: Reading) {
  reading.controller.abort()
  reading.audio?.pause()
  reading.urls.forEach((url) => URL.revokeObjectURL(url))
}

/** Plays one part; ends when it has played, or at once when the reading is stopped. */
function play(reading: Reading, audio: Blob): Promise<void> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(audio)
    reading.urls.push(url)
    const player = new Audio(url)
    reading.audio = player
    player.onended = () => resolve()
    player.onerror = () => reject(new Error('The answer could not be played.'))
    reading.controller.signal.addEventListener('abort', () => resolve(), { once: true })
    player.play().catch(reject)
  })
}

/** A request for one part whose failure is handled where it is awaited (not as an "unhandled rejection" when stopped). */
function fetchPart(text: string, signal: AbortSignal): Promise<Blob> {
  const request = speakText(text, signal)
  request.catch(() => {})
  return request
}

/**
 * Reads the assistant's answers aloud with our speech server (POST /api/v1/chat/speech), one answer at a time:
 * read(index, text) starts reading a message (and stops the one before), stop() ends it. `reading` is the index of the
 * message being read, or null. Reading stops when the chat closes.
 */
export function useReadAloud() {
  const [reading, setReading] = useState<number | null>(null)
  const current = useRef<Reading | null>(null)

  function stop() {
    if (current.current) endReading(current.current)
    current.current = null
    setReading(null)
  }

  async function read(index: number, text: string) {
    stop()
    const session: Reading = { controller: new AbortController(), audio: null, urls: [] }
    current.current = session
    setReading(index)
    const stillReading = () => current.current === session

    const parts = partsToRead(text)
    try {
      let next = fetchPart(parts[0], session.controller.signal)
      for (let part = 0; part < parts.length; part += 1) {
        const audio = await next
        if (!stillReading()) return
        // The next part is made while this one plays
        if (part + 1 < parts.length) next = fetchPart(parts[part + 1], session.controller.signal)
        await play(session, audio)
        if (!stillReading()) return
      }
      stop()
    } catch (error) {
      if (!stillReading()) return
      stop()
      // The browser plays sound by itself only after a click on the page; "Listen" always works
      const blocked = error instanceof DOMException && error.name === 'NotAllowedError'
      toast.error(blocked ? 'Press "Listen" to hear the answer.' : error instanceof Error ? error.message : 'The answer could not be read aloud.')
    }
  }

  // The chat closes: stop reading
  useEffect(
    () => () => {
      if (current.current) endReading(current.current)
      current.current = null
    },
    [],
  )

  return { reading, read, stop }
}
