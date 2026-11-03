import { useEffect, useRef, useState } from 'react'
import { transcribeQuestion } from '../../api/chat'

/** A recording stops by itself after this many seconds (the speech server takes about 30 s, at most 2 MB). */
export const MAX_RECORDING_SECONDS = 30

/** idle: nothing happens · recording: the microphone is on · transcribing: the speech server writes the words down */
export type RecorderState = 'idle' | 'recording' | 'transcribing'

/** True when this browser can record a question; without it the chat offers typing only. */
export function canRecord(): boolean {
  return typeof window.MediaRecorder !== 'undefined' && typeof navigator.mediaDevices?.getUserMedia === 'function'
}

/** Why the microphone could not start, for the user. */
function microphoneMessage(error: unknown): string {
  const name = error instanceof DOMException ? error.name : ''
  if (name === 'NotAllowedError' || name === 'SecurityError') return 'Please allow the microphone to ask by voice, or type your question.'
  if (name === 'NotFoundError') return 'No microphone was found. Please type your question.'
  return 'The microphone could not be started. Please type your question.'
}

/**
 * Records a spoken question and turns it into text with our speech server:
 * start() turns the microphone on, stop() (or 30 s) ends the recording, which goes to POST /api/v1/chat/transcriptions;
 * onQuestion gets the words. The recording stays in the browser's memory and is never stored.
 * Closing the chat while recording throws the recording away.
 */
export function useVoiceRecorder(onQuestion: (text: string) => void) {
  const [state, setState] = useState<RecorderState>('idle')
  const [seconds, setSeconds] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const recorderRef = useRef<MediaRecorder | null>(null)
  /** true once the chat is closed: a recording or a transcription that ends later is ignored */
  const closedRef = useRef(false)
  // The latest onQuestion, for a transcription that ends after a re-render
  const onQuestionRef = useRef(onQuestion)
  useEffect(() => {
    onQuestionRef.current = onQuestion
  })

  async function start() {
    if (state !== 'idle') return
    setError(null)
    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch (failure) {
      setError(microphoneMessage(failure))
      return
    }

    // No format is asked for: every browser records one the speech server understands (webm, ogg or mp4)
    const recorder = new MediaRecorder(stream)
    const chunks: Blob[] = []
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) chunks.push(event.data)
    }
    recorder.onstop = () => {
      stream.getTracks().forEach((track) => track.stop())
      recorderRef.current = null
      if (!closedRef.current) void transcribe(new Blob(chunks, { type: recorder.mimeType || chunks[0]?.type || 'audio/webm' }))
    }
    recorder.start()
    recorderRef.current = recorder
    setSeconds(0)
    setState('recording')
  }

  function stop() {
    if (recorderRef.current?.state !== 'recording') return
    setState('transcribing')
    recorderRef.current.stop() // onstop sends the recording
  }

  async function transcribe(audio: Blob) {
    if (audio.size === 0) {
      setError("I couldn't hear a question. Please try again or type it.")
      setState('idle')
      return
    }
    try {
      const text = await transcribeQuestion(audio)
      if (closedRef.current) return
      setState('idle')
      onQuestionRef.current(text)
    } catch (failure) {
      if (closedRef.current) return
      // The backend's messages are written for the user ("I couldn't hear a question", "Voice is not available right now")
      setError(failure instanceof Error ? failure.message : 'Your question could not be understood. Please type it.')
      setState('idle')
    }
  }

  // The time on the button, and the 30 s limit
  useEffect(() => {
    if (state !== 'recording') return
    const started = Date.now()
    const timer = setInterval(() => {
      const elapsed = Math.floor((Date.now() - started) / 1000)
      setSeconds(Math.min(elapsed, MAX_RECORDING_SECONDS))
      if (elapsed >= MAX_RECORDING_SECONDS && recorderRef.current?.state === 'recording') {
        setState('transcribing')
        recorderRef.current.stop()
      }
    }, 250)
    return () => clearInterval(timer)
  }, [state])

  // The chat closes: the microphone goes off and the recording is thrown away
  useEffect(() => {
    closedRef.current = false
    return () => {
      closedRef.current = true
      if (recorderRef.current?.state === 'recording') recorderRef.current.stop()
    }
  }, [])

  return { state, seconds, error, clearError: () => setError(null), start, stop }
}
