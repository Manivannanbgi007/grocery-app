import { Maximize2, MessageSquarePlus, Mic, Minimize2, SendHorizontal, Sparkles, Square, X } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { CHAT_MESSAGE_MAX } from '../../api/chat'
import { Spinner } from '../../components/ui/Spinner'
import { cn } from '../../utils/cn'
import { ASSISTANT_TEXTS, type AssistantKind } from './assistantTexts'
import { ChatConversation } from './ChatConversation'
import { saveChatSize, savedChatSize, type ChatSize } from './chatStorage'
import type { ActionDecision, ChatLine } from './useChat'
import { useReadAloud } from './useReadAloud'
import { canRecord, MAX_RECORDING_SECONDS, useVoiceRecorder } from './useVoiceRecorder'

/** 7 -> "0:07" */
function clock(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/** The round icon buttons on the green title bar: white, a light hover, and a white focus ring (green would not show). */
const HEADER_BUTTON =
  'grid size-9 place-items-center rounded-full text-white transition duration-200 hover:scale-105 hover:bg-white/15 active:scale-95 focus-visible:outline-white disabled:cursor-not-allowed disabled:opacity-50'

interface ChatPanelProps {
  id: string
  /** the shop or a panel: only the texts differ */
  kind: AssistantKind
  lines: ChatLine[]
  pending: boolean
  error: Error | null
  loadingHistory: boolean
  historyError: Error | null
  /** the user's first name, or undefined for a guest */
  firstName: string | undefined
  /** `spoken`: asked by voice, so the answer is read aloud */
  onAsk: (question: string, spoken?: boolean) => void
  onRetry: () => void
  onReloadHistory: () => void
  onNewChat: () => void
  onClose: () => void
  /** after a click on a link of an answer to a page of the app */
  onNavigate: () => void
  /** the customer decided on an order summary under an answer (the shop's chat only) */
  onActionDone?: (actionKey: string, done: ActionDecision, followUp: ChatLine) => void
}

/**
 * The chat with the assistant (the shop's or a panel's): on bigger screens a panel at the right edge, 37.44rem wide (599
 * px: the old 24rem card made 20 % and then 30 % wider); on a phone as wide as the screen. Two heights, switched with the
 * button in the title bar and remembered (chatStorage.ts): maximized (the screen's height, the start) and minimized
 * (36rem at the bottom); the width is the same in both. It covers the chat button while it is open. The title bar and
 * the question box are here; the messages are in ChatConversation.
 * Escape closes it; Enter sends (Shift + Enter makes a new line).
 *
 * Voice (our own speech server, English): the microphone button records a question, which is sent as soon as the
 * recording stops; the answer to a spoken question is read aloud by itself, and every answer has "Listen".
 */
export function ChatPanel({ id, kind, lines, pending, error, loadingHistory, historyError, firstName, onAsk, onRetry, onReloadHistory, onNewChat, onClose, onNavigate, onActionDone }: ChatPanelProps) {
  const titleId = useId()
  const [question, setQuestion] = useState('')
  const [voiceNote, setVoiceNote] = useState<string | null>(null)
  const questionRef = useRef<HTMLTextAreaElement>(null)
  const texts = { ...ASSISTANT_TEXTS[kind], ...(firstName ? ASSISTANT_TEXTS[kind].loggedIn : undefined) }
  const [voice] = useState(canRecord)
  const readAloud = useReadAloud()
  const recorder = useVoiceRecorder(askSpoken)
  const [size, setSize] = useState(savedChatSize)

  function toggleSize() {
    const next: ChatSize = size === 'maximized' ? 'minimized' : 'maximized'
    setSize(next)
    saveChatSize(next)
  }

  function submit(event?: FormEvent) {
    event?.preventDefault()
    if (!question.trim() || pending) return
    onAsk(question)
    setQuestion('')
  }

  /** The words of a recording: sent at once, unless they are too long for one message. */
  function askSpoken(text: string) {
    if (text.length > CHAT_MESSAGE_MAX) {
      setQuestion(text.slice(0, CHAT_MESSAGE_MAX))
      setVoiceNote('Your question is long. Please shorten it, then send it.')
      questionRef.current?.focus()
      return
    }
    onAsk(text, true)
  }

  function startRecording() {
    readAloud.stop()
    setVoiceNote(null)
    void recorder.start()
  }

  // The answer to a spoken question is read aloud as soon as it arrives (once)
  const lastLine = lines.at(-1)
  const readByItself = useRef<ChatLine | null>(null)
  useEffect(() => {
    if (lastLine?.role === 'ASSISTANT' && lastLine.spoken && readByItself.current !== lastLine) {
      readByItself.current = lastLine
      void readAloud.read(lines.length - 1, lastLine.text)
    }
  })

  const recording = recorder.state === 'recording'
  const transcribing = recorder.state === 'transcribing'
  const voiceStatus = recording
    ? `Listening… ${clock(recorder.seconds)} / ${clock(MAX_RECORDING_SECONDS)}. Press stop when you are done.`
    : transcribing
      ? 'Writing down your question…'
      : null
  const voiceProblem = recorder.error ?? voiceNote

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submit()
    }
  }

  return (
    <section
      id={id}
      role="dialog"
      aria-labelledby={titleId}
      data-size={size}
      className={cn(
        // The width is the same in both sizes: the whole screen on a phone, 37.44rem at the right edge from sm. Frosted
        // glass over the page; it slides in from the bottom on a phone and from the right on bigger screens. isolate: the
        // coloured glows below stay behind the messages
        'fixed inset-x-0 bottom-0 isolate z-50 flex flex-col bg-card/85 backdrop-blur-2xl sm:left-auto sm:w-[37.44rem] sm:border-l sm:border-line sm:shadow-2xl',
        'motion-safe:animate-chat-up sm:motion-safe:animate-chat-in',
        // Maximized: from the top of the screen to the bottom. Minimized: 36rem high (at most 85 % of the screen) at the
        // bottom, with rounded top corners (only the left one at the right edge)
        size === 'maximized' ? 'top-0' : 'h-[min(36rem,85dvh)] overflow-hidden rounded-t-2xl border-t border-line shadow-2xl sm:rounded-tr-none',
      )}
    >
      {/* Three soft coloured glows (green, blue, violet) that drift slowly behind the messages. Decoration only. */}
      <span aria-hidden="true" className="pointer-events-none absolute top-24 -left-16 -z-10 size-64 rounded-full bg-glow-1 opacity-70 blur-3xl motion-safe:animate-blob" />
      <span
        aria-hidden="true"
        className="pointer-events-none absolute top-1/2 -right-20 -z-10 size-72 rounded-full bg-glow-2 opacity-70 blur-3xl motion-safe:animate-blob"
        style={{ animationDelay: '-5s' }}
      />
      <span
        aria-hidden="true"
        className="pointer-events-none absolute bottom-24 left-1/4 -z-10 size-60 rounded-full bg-glow-4 opacity-70 blur-3xl motion-safe:animate-blob"
        style={{ animationDelay: '-10s' }}
      />

      {/* The title bar: a green-blue-violet gradient that drifts slowly, with white text and round icon buttons (their
          names are also tooltips) */}
      <header className="relative flex items-center gap-3 overflow-hidden bg-linear-to-r from-brand via-deep-blue to-deep-violet bg-[length:200%_100%] px-4 py-3.5 text-white shadow-md motion-safe:animate-gradient-drift">
        {/* Two faint light circles for depth */}
        <span aria-hidden="true" className="pointer-events-none absolute -top-16 -right-10 size-40 rounded-full bg-white/10" />
        <span aria-hidden="true" className="pointer-events-none absolute -bottom-20 left-1/3 size-36 rounded-full bg-white/5" />
        {/* The assistant's picture: a sparkle (AI) with a green "online" dot */}
        <span className="relative grid size-10 shrink-0 place-items-center rounded-2xl bg-white text-brand shadow-md" aria-hidden="true">
          <Sparkles className="size-5" />
          <span className="absolute -right-0.5 -bottom-0.5 flex size-3">
            <span className="absolute inline-flex size-full rounded-full bg-ink-brand opacity-75 motion-safe:animate-ping" />
            <span className="relative inline-flex size-3 rounded-full border-2 border-white bg-ink-brand" />
          </span>
        </span>
        <div className="relative min-w-0 flex-1">
          <h2 id={titleId} className="truncate font-heading text-base font-bold text-white">
            {texts.title}
          </h2>
          <p className="truncate text-xs text-white">{texts.topics}</p>
        </div>
        <div className="relative flex shrink-0 items-center gap-0.5">
          <button
            type="button"
            onClick={() => {
              readAloud.stop()
              onNewChat()
            }}
            disabled={pending}
            aria-label="New chat"
            title="Start a new chat"
            className={HEADER_BUTTON}
          >
            <MessageSquarePlus className="size-5" aria-hidden="true" />
          </button>
          {/* The name says what a click does: shrink to the compact chat, or grow to the screen's height */}
          <button
            type="button"
            onClick={toggleSize}
            aria-label={size === 'maximized' ? 'Minimize the chat' : 'Maximize the chat'}
            title={size === 'maximized' ? 'Minimize' : 'Maximize'}
            className={HEADER_BUTTON}
          >
            {size === 'maximized' ? <Minimize2 className="size-5" aria-hidden="true" /> : <Maximize2 className="size-5" aria-hidden="true" />}
          </button>
          <button type="button" onClick={onClose} aria-label="Close the assistant" title="Close" className={HEADER_BUTTON}>
            <X className="size-5" aria-hidden="true" />
          </button>
        </div>
      </header>

      <ChatConversation
        texts={texts}
        kind={kind}
        onNavigate={onNavigate}
        size={size}
        firstName={firstName}
        lines={lines}
        pending={pending}
        error={error}
        loadingHistory={loadingHistory}
        historyError={historyError}
        onAsk={onAsk}
        onRetry={() => {
          onRetry()
          // The button disappears with the error: the focus goes back to the question box
          questionRef.current?.focus()
        }}
        onReloadHistory={onReloadHistory}
        reading={readAloud.reading}
        onListen={(index, text) => void readAloud.read(index, text)}
        onStopReading={readAloud.stop}
        onActionDone={onActionDone}
      />

      {/* The question box: one rounded box with the text field, the microphone and the send button. The whole box shows
          the focus (a green border and halo), and a red border while recording */}
      <form onSubmit={submit} className="border-t border-line/70 bg-card/60 px-3 pt-3 pb-2">
        <label htmlFor={`${id}-question`} className="sr-only">
          Your question
        </label>
        <div
          className={cn(
            // A glass box that glows green while it has the focus (red while recording)
            'flex items-end gap-1.5 rounded-2xl border bg-card/90 p-1.5 shadow-sm transition duration-200',
            recording
              ? 'border-hot ring-4 ring-hot/10'
              : 'border-line focus-within:border-brand focus-within:bg-card focus-within:shadow-lg focus-within:shadow-brand/10 focus-within:ring-4 focus-within:ring-brand/15',
          )}
        >
          <textarea
            ref={questionRef}
            id={`${id}-question`}
            // The chat opens with the cursor in the question box
            autoFocus
            rows={1}
            maxLength={CHAT_MESSAGE_MAX}
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={texts.placeholder}
            // Grows with the text (up to 9rem) where the browser can (field-sizing); scrolls after that
            className="field-sizing-content max-h-36 min-h-10 flex-1 resize-none bg-transparent px-2.5 py-2 text-sm text-heading outline-none"
          />
          {voice && (
            // One button for both (the focus stays on it): "Ask by voice", and while recording "Stop recording"
            <button
              type="button"
              onClick={recording ? recorder.stop : startRecording}
              disabled={transcribing || (pending && !recording)}
              aria-label={recording ? 'Stop recording' : 'Ask by voice'}
              title={recording ? 'Stop recording and send' : 'Ask by voice'}
              className={cn(
                'inline-flex h-10 min-w-10 shrink-0 items-center justify-center gap-1.5 rounded-xl px-2.5 transition-colors disabled:cursor-not-allowed disabled:opacity-40',
                recording ? 'bg-hot/10 text-hot-text' : 'text-body hover:bg-violet-light hover:text-violet-text',
              )}
            >
              {transcribing ? (
                <Spinner size="sm" />
              ) : recording ? (
                <>
                  <Square className="size-3.5 fill-current motion-safe:animate-pulse" aria-hidden="true" />
                  <span className="font-mono text-xs" aria-hidden="true">
                    {clock(recorder.seconds)}
                  </span>
                </>
              ) : (
                <Mic className="size-5" aria-hidden="true" />
              )}
            </button>
          )}
          <button
            type="submit"
            disabled={!question.trim() || pending || recording || transcribing}
            aria-label="Send"
            title="Send"
            // A green-to-blue gradient circle: it grows a little on hover (its arrow moves on), presses in on a click
            className="group grid size-10 shrink-0 place-items-center rounded-full bg-linear-to-br from-brand via-deep-blue to-deep-violet text-white shadow-md shadow-sale/30 transition duration-200 enabled:hover:scale-105 enabled:hover:shadow-lg enabled:active:scale-95 disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none"
          >
            <SendHorizontal className="size-4 transition-transform duration-200 group-enabled:group-hover:translate-x-0.5" aria-hidden="true" />
          </button>
        </div>
        {voiceProblem ? (
          <p role="alert" className="mt-1.5 px-1 text-xs text-hot-text">
            {voiceProblem}
          </p>
        ) : (
          <div className="mt-1.5 flex items-center justify-between gap-3 px-1 text-xs">
            <p className="hidden sm:block">Enter to send, Shift + Enter for a new line</p>
            <p className="ml-auto text-right" aria-live="polite">
              {voiceStatus ?? (CHAT_MESSAGE_MAX - question.length < 100 ? `${CHAT_MESSAGE_MAX - question.length} characters left` : ' ')}
            </p>
          </div>
        )}
      </form>
    </section>
  )
}
