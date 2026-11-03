/*
 * The mock speech server: it "hears" one of the example questions, so the mock assistant answers it, and "reads"
 * answers aloud as silence (a real WAV file, so the browser plays it and the chat behaves as with the real voice).
 */

/** What the mock speech server hears in every recording. */
export const MOCK_HEARD_QUESTION = 'Which offers are running?'

/** A silent WAV (8 kHz, 8 bit, mono) a bit longer for a longer text: 0.3 s plus 10 ms per character, at most 3 s. */
export function silentWav(text: string): Blob {
  const sampleRate = 8000
  const samples = Math.round((Math.min(300 + text.length * 10, 3000) / 1000) * sampleRate)
  const buffer = new ArrayBuffer(44 + samples)
  const view = new DataView(buffer)
  const writeText = (offset: number, value: string) => [...value].forEach((char, index) => view.setUint8(offset + index, char.charCodeAt(0)))
  writeText(0, 'RIFF')
  view.setUint32(4, 36 + samples, true)
  writeText(8, 'WAVE')
  writeText(12, 'fmt ')
  view.setUint32(16, 16, true) // size of the format block
  view.setUint16(20, 1, true) // PCM
  view.setUint16(22, 1, true) // mono
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate, true) // bytes per second
  view.setUint16(32, 1, true) // bytes per sample
  view.setUint16(34, 8, true) // bits per sample
  writeText(36, 'data')
  view.setUint32(40, samples, true)
  new Uint8Array(buffer, 44).fill(128) // 128 is silence for 8-bit sound
  return new Blob([buffer], { type: 'audio/wav' })
}
