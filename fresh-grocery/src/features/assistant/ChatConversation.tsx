import {
  ArrowRight,
  BadgeCheck,
  BadgePercent,
  Bell,
  Sparkles,
  Check,
  CircleAlert,
  Copy,
  Package,
  PackageSearch,
  RotateCcw,
  Search,
  Square,
  TrendingUp,
  Truck,
  Volume2,
  Wallet,
  Wifi,
  type LucideIcon,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ErrorState'
import { Spinner } from '../../components/ui/Spinner'
import { cn } from '../../utils/cn'
import type { AssistantKind, AssistantTexts } from './assistantTexts'
import { ChatActionCard } from './ChatActionCard'
import type { ChatSize } from './chatStorage'
import { ChatLinks, ChatMessageText } from './ChatMessageText'
import { ChatPaymentStatus } from './ChatPaymentStatus'
import type { ActionDecision, ChatLine } from './useChat'

interface ChatConversationProps {
  /** the texts of this assistant (the shop's or a panel's) */
  texts: AssistantTexts
  /** which chat it is: decides which pages the answers' links may open */
  kind: AssistantKind
  /** after a click on a link to a page of the app */
  onNavigate: () => void
  /** the chat's height: after a change the newest message is brought into view again */
  size: ChatSize
  /** the user's first name, or undefined for a guest */
  firstName: string | undefined
  lines: ChatLine[]
  pending: boolean
  error: Error | null
  loadingHistory: boolean
  historyError: Error | null
  onAsk: (question: string) => void
  onRetry: () => void
  onReloadHistory: () => void
  /** the index of the message being read aloud, or null */
  reading: number | null
  onListen: (index: number, text: string) => void
  onStopReading: () => void
  /** the customer decided on the card of an answer (an order summary); without it the cards are not shown */
  onActionDone?: (actionKey: string, done: ActionDecision, followUp: ChatLine) => void
}

/**
 * The look of the assistant's bubbles (answers, typing, errors): almost solid white over the chat's gradient, a soft
 * shadow, the "tail" corner at the top left.
 */
const ASSISTANT_BUBBLE = 'rounded-2xl rounded-tl-sm bg-card/95 px-4 py-3 text-sm leading-relaxed text-heading shadow-md ring-1 shadow-heading/5 ring-line/70'

/** The small round buttons under an answer (Copy, Listen): an icon, the name for screen readers and as a tooltip. */
const ACTION_BUTTON = 'grid size-8 place-items-center rounded-full text-body transition-colors hover:bg-card hover:text-heading hover:shadow-sm'

/**
 * The assistant's picture, a sparkle (AI) on a green-blue-violet gradient: next to its answers, and bigger on the
 * welcome screen, where it floats with a soft halo.
 */
function AssistantAvatar({ large = false }: { large?: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'grid shrink-0 place-items-center bg-linear-to-br from-brand via-deep-blue to-deep-violet text-white',
        large ? 'size-16 rounded-2xl shadow-lg ring-8 shadow-violet/30 ring-violet/10 motion-safe:animate-float' : 'size-8 rounded-full shadow-md shadow-sale/25',
      )}
    >
      <Sparkles className={large ? 'size-8' : 'size-4'} />
    </span>
  )
}

/** Copies an answer; the button says "Copied" for two seconds. */
function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error('Could not copy the answer')
    }
  }

  const label = copied ? 'Copied' : 'Copy'
  return (
    <button type="button" onClick={() => void copy()} aria-label={label} title={label} className={ACTION_BUTTON}>
      {copied ? <Check className="size-4 text-brand-text" aria-hidden="true" /> : <Copy className="size-4" aria-hidden="true" />}
    </button>
  )
}

/** Reads an answer aloud ("Listen"); while it is read, "Stop reading". */
function ListenButton({ listening, onListen, onStop }: { listening: boolean; onListen: () => void; onStop: () => void }) {
  const label = listening ? 'Stop reading' : 'Listen'
  return (
    <button type="button" onClick={listening ? onStop : onListen} aria-label={label} title={label} className={ACTION_BUTTON}>
      {listening ? <Square className="size-3.5 fill-current text-brand-text motion-safe:animate-pulse" aria-hidden="true" /> : <Volume2 className="size-4" aria-hidden="true" />}
    </button>
  )
}

interface MessageProps {
  line: ChatLine
  checked: string
  kind: AssistantKind
  onNavigate: () => void
  listening: boolean
  onListen: () => void
  onStopReading: () => void
  onActionDone?: ChatConversationProps['onActionDone']
}

/**
 * One message. The user's: a green bubble on the right. The assistant's: its picture and a white bubble with the text
 * (its links work, see ChatMessageText), the payment and "Pay Now" after an online order (ChatPaymentStatus), the pages that fit the
 * answer, an order summary to confirm (ChatActionCard),
 * the "checked" note (when the backend checked the answer against live data), and Copy and Listen. Always plain text,
 * never HTML.
 */
function Message({ line, checked, kind, onNavigate, listening, onListen, onStopReading, onActionDone }: MessageProps) {
  if (line.role === 'USER') {
    return (
      // The customer's question: a green-to-blue gradient, coming in from the right
      <li className="flex justify-end motion-safe:animate-bubble-mine">
        <span className="sr-only">You:</span>
        <p className="max-w-[80%] rounded-2xl rounded-br-sm bg-linear-to-br from-brand to-deep-blue px-4 py-2.5 text-sm leading-relaxed break-words whitespace-pre-line text-white shadow-md shadow-sale/25">
          {line.text}
        </p>
      </li>
    )
  }

  return (
    // The assistant's answer: coming in from the left
    <li className="flex items-start gap-3 motion-safe:animate-bubble-theirs">
      <AssistantAvatar />
      <div className="flex max-w-[85%] min-w-0 flex-col items-start gap-2">
        <span className="sr-only">Assistant:</span>
        <p className={cn(ASSISTANT_BUBBLE, 'break-words whitespace-pre-line')}>
          <ChatMessageText text={line.text} kind={kind} onNavigate={onNavigate} />
        </p>
        {/* Only the line of an online order that waits for its payment has paymentUrl (null too: then it says why there is
            no button): the payment's status from payment-service, Pay Now, and in the end "Payment ... received" */}
        {line.paymentUrl !== undefined && (
          <ChatPaymentStatus orderId={line.confirmed?.orderId} paymentUrl={line.paymentUrl} kind={kind} onNavigate={onNavigate} />
        )}
        {line.links && <ChatLinks links={line.links} kind={kind} onNavigate={onNavigate} />}
        {line.action && line.actionKey && line.actionReceivedAt !== undefined && onActionDone && (
          <ChatActionCard
            action={line.action}
            actionKey={line.actionKey}
            receivedAt={line.actionReceivedAt}
            done={line.actionDone}
            approval={line.approval}
            onDone={(done, followUp) => onActionDone(line.actionKey!, done, followUp)}
          />
        )}
        <div className="flex flex-wrap items-center gap-1">
          {line.grounded && (
            <span className="mr-1 inline-flex items-center gap-1 rounded-full bg-brand-light px-2.5 py-1 text-xs font-bold text-brand-text">
              <BadgeCheck className="size-3.5" aria-hidden="true" />
              {checked}
            </span>
          )}
          {/* Copy and Listen stay together when the row wraps (a narrow phone) */}
          <span className="flex items-center gap-1">
            <CopyButton text={line.text} />
            <ListenButton listening={listening} onListen={onListen} onStop={onStopReading} />
          </span>
        </div>
      </div>
    </li>
  )
}

/**
 * The example questions take turns in these colours (only the look): a light wash of the colour from the left, the
 * icon's tile, and the border under the mouse. Tailwind needs the whole class names here.
 */
const EXAMPLE_LOOKS = [
  { wash: 'from-brand-light', tile: 'bg-brand text-white shadow-brand/30', hover: 'hover:border-brand/50' },
  { wash: 'from-sale-light', tile: 'bg-sale text-white shadow-sale/30', hover: 'hover:border-sale/60' },
  { wash: 'from-new-light', tile: 'bg-new text-white shadow-new/30', hover: 'hover:border-new/60' },
  { wash: 'from-violet-light', tile: 'bg-violet text-white shadow-violet/30', hover: 'hover:border-violet/50' },
  { wash: 'from-hot/10', tile: 'bg-hot text-white shadow-hot/30', hover: 'hover:border-hot/50' },
]

/** An icon for an example question, by its topic (the questions are in assistantTexts.ts); a product question by default. */
const EXAMPLE_ICONS: Array<[RegExp, LucideIcon]> = [
  [/offer|coupon/i, BadgePercent],
  [/deliver/i, Truck],
  [/order/i, Package],
  [/stock/i, PackageSearch],
  [/sell|sold|sales/i, TrendingUp],
  [/earn/i, Wallet],
  [/online/i, Wifi],
  [/attention/i, Bell],
]

function exampleIcon(question: string): LucideIcon {
  return EXAMPLE_ICONS.find(([topic]) => topic.test(question))?.[1] ?? Search
}

/** Before the first question: the assistant says hello, and the example questions are one click away. */
function Welcome({ texts, firstName, onAsk }: { texts: AssistantTexts; firstName: string | undefined; onAsk: (question: string) => void }) {
  return (
    <div className="relative flex min-h-full flex-col items-center justify-center py-6 text-center motion-safe:animate-message-in">
      {/* A soft green-and-violet glow behind the picture */}
      <span aria-hidden="true" className="pointer-events-none absolute top-1/2 left-1/2 size-64 -translate-x-1/2 -translate-y-[85%] rounded-full bg-linear-to-br from-brand/15 to-violet/15 blur-3xl" />
      <AssistantAvatar large />
      <h3 className="mt-6 font-heading text-2xl">
        Hi{firstName ? `, ${firstName}` : ''}!{' '}
        <span aria-hidden="true" className="inline-block origin-[70%_70%] motion-safe:animate-wave">
          👋
        </span>
      </h3>
      <p className="mt-2 max-w-sm text-sm leading-relaxed">{texts.greeting}</p>

      <p className="mt-8 flex items-center gap-1.5 self-stretch text-left font-heading text-xs font-bold tracking-wider text-body uppercase">
        <Sparkles className="size-3.5 text-violet-text" aria-hidden="true" />
        Try asking
      </p>
      <ul aria-label="Example questions" className="mt-3 grid w-full gap-2.5">
        {texts.examples.map((example, index) => {
          const Icon = exampleIcon(example)
          const look = EXAMPLE_LOOKS[index % EXAMPLE_LOOKS.length]
          return (
            // The questions rise in one after the other
            <li key={example} className="motion-safe:animate-rise" style={{ animationDelay: `${150 + index * 90}ms` }}>
              <button
                type="button"
                onClick={() => onAsk(example)}
                className={cn(
                  'group flex w-full items-center gap-3 rounded-2xl border border-line bg-card bg-linear-to-r to-card px-4 py-3 text-left text-sm font-bold text-heading shadow-sm transition duration-200 hover:-translate-y-0.5 hover:shadow-md active:scale-[0.99]',
                  look.wash,
                  look.hover,
                )}
              >
                <span className={cn('grid size-9 shrink-0 place-items-center rounded-xl shadow-md transition-transform group-hover:scale-110 group-hover:-rotate-6', look.tile)} aria-hidden="true">
                  <Icon className="size-4" />
                </span>
                <span className="flex-1">{example}</span>
                <ArrowRight className="size-4 text-muted transition group-hover:translate-x-0.5 group-hover:text-brand-text" aria-hidden="true" />
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

/** The three typing dots: green, blue and violet */
const TYPING_DOTS = ['bg-brand', 'bg-sale', 'bg-violet']

/** While the assistant works on an answer: "Thinking…" with three bouncing dots (still dots with reduced motion). */
function TypingBubble() {
  return (
    <div role="status" className="mt-6 flex items-start gap-3 motion-safe:animate-bubble-theirs">
      <AssistantAvatar />
      <div className={ASSISTANT_BUBBLE}>
        <span className="flex items-center gap-2.5">
          <span className="font-heading text-sm font-bold text-heading">Thinking…</span>
          <span className="flex h-3 items-center gap-1.5" aria-hidden="true">
            {TYPING_DOTS.map((colour, dot) => (
              <span key={colour} className={cn('size-2 rounded-full motion-safe:animate-bounce', colour)} style={{ animationDelay: `${dot * 0.15}s` }} />
            ))}
          </span>
        </span>
        <p className="mt-1.5 text-xs text-body">Looking it up… (this can take up to a minute)</p>
      </div>
    </div>
  )
}

/**
 * The conversation between the chat's title bar and its question box: the welcome screen, or the messages (role="log",
 * one list item per message), the typing bubble and an error with "Try again". It scrolls to the newest message.
 */
export function ChatConversation({
  texts,
  kind,
  onNavigate,
  size,
  firstName,
  lines,
  pending,
  error,
  loadingHistory,
  historyError,
  onAsk,
  onRetry,
  onReloadHistory,
  reading,
  onListen,
  onStopReading,
  onActionDone,
}: ChatConversationProps) {
  const scrollRef = useRef<HTMLDivElement>(null)

  // Keep the newest message in view (also when the chat gets smaller or taller)
  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight
  }, [lines.length, pending, error, size])

  return (
    // A light green-to-violet wash behind the messages, see-through enough for the panel's drifting glows (ChatPanel)
    <div ref={scrollRef} className="flex-1 overflow-y-auto bg-linear-to-b from-brand-light/70 via-card/60 to-violet-light/70 px-4 py-5 sm:px-5">
      {historyError ? (
        <ErrorState compact title="Could not load the chat" error={historyError} onRetry={onReloadHistory} />
      ) : lines.length === 0 && !loadingHistory ? (
        <Welcome texts={texts} firstName={firstName} onAsk={onAsk} />
      ) : (
        <ul role="log" aria-label="Chat" aria-live="polite" className="space-y-6">
          {lines.map((line, index) => (
            <Message
              key={index}
              line={line}
              checked={texts.checked}
              kind={kind}
              onNavigate={onNavigate}
              listening={reading === index}
              onListen={() => onListen(index, line.text)}
              onStopReading={onStopReading}
              onActionDone={onActionDone}
            />
          ))}
        </ul>
      )}

      {loadingHistory && (
        <p role="status" className="flex items-center justify-center gap-2 py-6 text-sm">
          <Spinner size="sm" className="text-brand" />
          Loading the chat…
        </p>
      )}
      {pending && <TypingBubble />}
      {error && (
        <div role="alert" className="mt-6 flex items-start gap-3">
          <span className="grid size-8 shrink-0 place-items-center rounded-full bg-hot/10 text-hot-text" aria-hidden="true">
            <CircleAlert className="size-4" />
          </span>
          <div className="max-w-[85%] rounded-2xl rounded-tl-sm border border-hot/30 bg-hot/5 px-4 py-3 text-sm text-heading">
            <p>{error.message}</p>
            <Button variant="outline" size="sm" onClick={onRetry} className="mt-2.5 rounded-full">
              <RotateCcw className="size-3.5" aria-hidden="true" />
              Try again
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
