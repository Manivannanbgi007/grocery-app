import { cleanup, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '../../mocks/server'
import { logInAs, renderApp } from '../../test/render'
import { partsToRead } from './useReadAloud'

/*
 * Voice in the assistant's chat (mock speech server, see mocks/mockVoice.ts). jsdom has no microphone, no MediaRecorder
 * and does not play sound, so the tests bring fakes: the "recording" is a few bytes, and a played answer ends when the
 * test says so (finishPlaying).
 */

class FakeMediaRecorder {
  state: 'inactive' | 'recording' = 'inactive'
  mimeType = 'audio/webm;codecs=opus'
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null

  start() {
    this.state = 'recording'
  }

  stop() {
    this.state = 'inactive'
    this.ondataavailable?.({ data: new Blob(['a spoken question'], { type: 'audio/webm' }) })
    this.onstop?.()
  }
}

const microphone = { stop: vi.fn() }
const getUserMedia = vi.fn()
/** the audio elements that were played, newest last */
let players: HTMLMediaElement[] = []
/** the texts sent to the speech server */
let spokenTexts: string[] = []

/** The answer being played ends. */
function finishPlaying() {
  players.at(-1)!.dispatchEvent(new Event('ended'))
}

beforeEach(() => {
  players = []
  spokenTexts = []
  getUserMedia.mockResolvedValue({ getTracks: () => [microphone] })
  vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
  Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia }, configurable: true })
  URL.createObjectURL = vi.fn(() => `blob:answer-${players.length}`)
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(function (this: HTMLMediaElement) {
    players.push(this)
    return Promise.resolve()
  })
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  server.events.on('request:start', async ({ request }) => {
    if (request.url.endsWith('/api/v1/chat/speech')) spokenTexts.push(((await request.clone().json()) as { text: string }).text)
  })
})

afterEach(() => {
  // First unmount the chat (it stops the microphone and the sound), then take the fakes away
  cleanup()
  server.events.removeAllListeners()
  vi.unstubAllGlobals()
  Reflect.deleteProperty(navigator, 'mediaDevices')
  Reflect.deleteProperty(URL, 'createObjectURL')
  Reflect.deleteProperty(URL, 'revokeObjectURL')
})

async function openChat() {
  const { user } = renderApp('/')
  await user.click(await screen.findByRole('button', { name: 'Ask our assistant' }, { timeout: 10_000 }))
  return { user, panel: screen.getByRole('dialog', { name: 'Shopping assistant' }) }
}

/** The mock speech server hears this question in every recording. */
function hear(question: string) {
  server.use(http.post('*/api/v1/chat/transcriptions', () => HttpResponse.json({ success: true, message: 'Question heard', data: { text: question } })))
}

describe('Voice in the assistant', () => {
  it('sends a spoken question as soon as the recording stops, and reads the answer aloud, first sentence first', async () => {
    hear('Do you have tomatoes?')
    const { user, panel } = await openChat()

    await user.click(within(panel).getByRole('button', { name: 'Ask by voice' }))
    expect(getUserMedia).toHaveBeenCalledWith({ audio: true })
    expect(within(panel).getByText('Listening… 0:00 / 0:30. Press stop when you are done.')).toBeInTheDocument()
    expect(within(panel).getByRole('button', { name: 'Send' })).toBeDisabled()

    await user.click(within(panel).getByRole('button', { name: 'Stop recording' }))
    expect(microphone.stop).toHaveBeenCalled()

    // What was understood is the user's message; the answer comes as for a typed question
    const log = await within(panel).findByRole('log', { name: 'Chat' })
    expect(await within(log).findByText('Do you have tomatoes?')).toBeInTheDocument()
    const answer = await within(log).findByText(/^Yes, we have Tomato/)
    const parts = partsToRead(answer.textContent!)
    expect(parts).toHaveLength(2)

    // The first sentence alone, then the rest while it plays; "Stop" shows while the answer is read
    await waitFor(() => expect(spokenTexts).toEqual(parts))
    await waitFor(() => expect(players).toHaveLength(1))
    expect(within(log).getByRole('button', { name: 'Stop reading' })).toBeInTheDocument()
    finishPlaying()
    await waitFor(() => expect(players).toHaveLength(2))
    finishPlaying()
    expect(await within(log).findByRole('button', { name: 'Listen' })).toBeInTheDocument()
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2)
  })

  it('reads a typed answer only on "Listen", and "Stop" ends it', async () => {
    const { user, panel } = await openChat()
    await user.click(within(panel).getByRole('button', { name: 'Which offers are running?' }))
    const log = within(panel).getByRole('log', { name: 'Chat' })
    const answer = await within(log).findByText(/^Running offers: WELCOME50/)
    expect(spokenTexts).toEqual([])

    await user.click(within(log).getByRole('button', { name: 'Listen' }))
    await waitFor(() => expect(players).toHaveLength(1))
    expect(spokenTexts).toEqual(partsToRead(answer.textContent!).slice(0, 1))

    await user.click(within(log).getByRole('button', { name: 'Stop reading' }))
    expect(HTMLMediaElement.prototype.pause).toHaveBeenCalled()
    expect(within(log).getByRole('button', { name: 'Listen' })).toBeInTheDocument()
  })

  it('says when the microphone is not allowed, and sends nothing', async () => {
    getUserMedia.mockRejectedValue(new DOMException('Permission denied', 'NotAllowedError'))
    const { user, panel } = await openChat()

    await user.click(within(panel).getByRole('button', { name: 'Ask by voice' }))

    expect(await within(panel).findByRole('alert')).toHaveTextContent('Please allow the microphone to ask by voice, or type your question.')
    expect(within(panel).getByRole('list', { name: 'Example questions' })).toBeInTheDocument()
  })

  it('says when no question was heard, and sends nothing', async () => {
    server.use(
      http.post('*/api/v1/chat/transcriptions', () =>
        HttpResponse.json({ success: false, message: "I couldn't hear a question. Please try again, or type it." }, { status: 400 }),
      ),
    )
    const { user, panel } = await openChat()

    await user.click(within(panel).getByRole('button', { name: 'Ask by voice' }))
    await user.click(within(panel).getByRole('button', { name: 'Stop recording' }))

    expect(await within(panel).findByRole('alert')).toHaveTextContent("I couldn't hear a question. Please try again, or type it.")
    expect(within(panel).getByRole('list', { name: 'Example questions' })).toBeInTheDocument()
    // The microphone can be used again at once
    expect(within(panel).getByRole('button', { name: 'Ask by voice' })).toBeEnabled()
  })

  it('stops a recording by itself after 30 seconds and sends it', async () => {
    const { user, panel } = await openChat()
    await user.click(within(panel).getByRole('button', { name: 'Ask by voice' }))

    // 31 seconds later (the clock on the button is checked 4 times a second)
    vi.spyOn(Date, 'now').mockReturnValue(Date.now() + 31_000)

    expect(await within(panel).findByText(/^Running offers: WELCOME50/)).toBeInTheDocument()
    expect(within(panel).getByRole('log', { name: 'Chat' })).toHaveTextContent('Which offers are running?')
  })

  it('tells the user when the voice is not available', async () => {
    server.use(
      http.post('*/api/v1/chat/speech', () =>
        HttpResponse.json({ success: false, message: 'Voice is not available right now. Please type your question.' }, { status: 503 }),
      ),
    )
    const { user, panel } = await openChat()
    await user.click(within(panel).getByRole('button', { name: 'Ask by voice' }))
    await user.click(within(panel).getByRole('button', { name: 'Stop recording' }))

    expect(await screen.findByText('Voice is not available right now. Please type your question.')).toBeInTheDocument()
    expect(within(panel).getByText(/^Running offers: WELCOME50/)).toBeInTheDocument()
    expect(within(panel).getByRole('button', { name: 'Listen' })).toBeInTheDocument()
  })

  it('is in the panel assistants too', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor')
    await user.click(await screen.findByRole('button', { name: 'Ask the assistant' }, { timeout: 10_000 }))
    expect(within(screen.getByRole('dialog', { name: 'Store assistant' })).getByRole('button', { name: 'Ask by voice' })).toBeInTheDocument()
  })

  it('offers no microphone when the browser cannot record', async () => {
    vi.stubGlobal('MediaRecorder', undefined)
    const { panel } = await openChat()
    expect(within(panel).queryByRole('button', { name: 'Ask by voice' })).not.toBeInTheDocument()
    expect(within(panel).getByLabelText('Your question')).toBeInTheDocument()
  })
})

describe('partsToRead', () => {
  it('splits off the first sentence, unless it is very short', () => {
    expect(partsToRead('Fresh Mart has 3 new orders. The oldest is order 41.')).toEqual(['Fresh Mart has 3 new orders.', 'The oldest is order 41.'])
    expect(partsToRead('Amul Butter costs ₹58.00 for 100 g. It is in stock.')).toEqual(['Amul Butter costs ₹58.00 for 100 g.', 'It is in stock.'])
    expect(partsToRead('1. Tomato\n2. Potato')).toEqual(['1. Tomato\n2. Potato'])
    expect(partsToRead('  Only one sentence here.  ')).toEqual(['Only one sentence here.'])
  })
})
