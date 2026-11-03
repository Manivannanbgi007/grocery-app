import { MessageCircle } from 'lucide-react'
import { useEffect, useEffectEvent, useId, useRef, useState } from 'react'
import { cn } from '../../utils/cn'
import { useAuth } from '../auth/authContext'
import { ASSISTANT_TEXTS, type AssistantKind } from './assistantTexts'
import { ChatPanel } from './ChatPanel'
import { useChat, useChatOwner } from './useChat'

/** Tailwind's lg: from here the page has room next to the open chat. */
const WIDE_SCREEN = '(min-width: 64rem)'

/**
 * "Ask our assistant": the chat button in the bottom right corner of the shop, for guests and customers. A login or
 * logout gives a fresh chat (the component starts again for another owner).
 */
export function AssistantButton() {
  const { user } = useAuth()
  const owner = useChatOwner()
  if (user && user.role !== 'CUSTOMER') return null
  return <Assistant key={owner} kind="shop" />
}

/**
 * The assistant of a panel (vendor, delivery, admin), in the same corner. The panel's layout shows it only to its own
 * logins; the backend answers from that user's data (their store, their deliveries, the platform).
 */
export function PanelAssistantButton({ kind, aboveBottomBar = false }: { kind: Exclude<AssistantKind, 'shop'>; aboveBottomBar?: boolean }) {
  const owner = useChatOwner()
  return <Assistant key={owner} kind={kind} aboveBottomBar={aboveBottomBar} />
}

interface AssistantProps {
  kind: AssistantKind
  /** true when the page has a bar at the bottom below `lg` (the delivery panel): the button sits above it */
  aboveBottomBar?: boolean
}

function Assistant({ kind, aboveBottomBar = false }: AssistantProps) {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  const chat = useChat(open, kind === 'shop')
  const panelId = useId()
  const buttonRef = useRef<HTMLButtonElement>(null)

  function close() {
    setOpen(false)
    buttonRef.current?.focus()
  }

  /**
   * After a link of an answer: from 1024 px the new page is next to the chat, so the chat stays open; on smaller
   * screens the chat covers the page, so it closes. The conversation stays either way (it lives in this component).
   */
  function afterNavigate() {
    if (!window.matchMedia?.(WIDE_SCREEN).matches) setOpen(false)
  }

  // Escape closes the chat wherever the focus is (also after a button inside it disappeared)
  const onEscape = useEffectEvent((event: KeyboardEvent) => {
    if (event.key === 'Escape') close()
  })
  useEffect(() => {
    if (!open) return
    document.addEventListener('keydown', onEscape)
    return () => document.removeEventListener('keydown', onEscape)
  }, [open])

  return (
    <>
      {open && (
        <ChatPanel
          id={panelId}
          kind={kind}
          lines={chat.lines}
          pending={chat.pending}
          error={chat.error}
          loadingHistory={chat.loadingHistory}
          historyError={chat.historyError}
          firstName={user?.fullName.split(' ')[0]}
          onAsk={chat.ask}
          onRetry={chat.retry}
          onReloadHistory={chat.reloadHistory}
          onNewChat={chat.newChat}
          onClose={close}
          onNavigate={afterNavigate}
          onActionDone={kind === 'shop' ? chat.finishAction : undefined}
        />
      )}
      <button
        ref={buttonRef}
        type="button"
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        onClick={() => (open ? close() : setOpen(true))}
        className={cn(
          // The chat's green-blue-violet gradient (like its title bar), drifting slowly, with a soft shadow; it lifts a little
          // on hover. Round on a phone (only the icon shows)
          'group fixed right-5 z-40 flex h-12 items-center gap-2 rounded-full bg-linear-to-r from-brand via-deep-blue to-deep-violet bg-[length:200%_100%] px-1.5 font-heading font-bold text-white shadow-lg shadow-sale/30 transition hover:-translate-y-0.5 hover:shadow-xl hover:shadow-violet/40 active:scale-95 motion-safe:animate-gradient-drift sm:pr-5 print:hidden',
          aboveBottomBar ? 'bottom-24 lg:bottom-5' : 'bottom-5',
        )}
      >
        {/* A soft ring that pulses three times after the page opens, then stops (decoration; not with "reduce motion") */}
        {!open && <span aria-hidden="true" className="absolute inset-0 -z-10 rounded-full bg-brand/40 motion-safe:animate-[ping_2s_cubic-bezier(0,0,0.2,1)_3]" />}
        <span className="grid size-9 place-items-center rounded-full bg-white/20 transition-transform duration-300 group-hover:scale-110 group-hover:-rotate-12" aria-hidden="true">
          <MessageCircle className="size-5" />
        </span>
        {/* The name stays the same; aria-expanded says whether the chat is open */}
        <span className="sr-only sm:not-sr-only">{ASSISTANT_TEXTS[kind].button}</span>
      </button>
    </>
  )
}
