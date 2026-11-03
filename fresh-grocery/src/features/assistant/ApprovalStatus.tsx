import { Check, Clock, TimerOff, X, type LucideIcon } from 'lucide-react'
import type { ChatApproval } from '../../api/types'
import { cn } from '../../utils/cn'
import { formatShortDateTime } from '../../utils/format'
import { useAuth } from '../auth/authContext'
import type { ActionDecision } from './useChat'

/** What the card's approval line shows */
type Shown = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED'

/**
 * The look of each status, in the shop's status colours (both themes): pending = warning (amber), approved = success
 * (green), rejected = danger (red), expired = neutral (grey). Tailwind needs the whole class names here.
 */
const LOOKS: Record<Shown, { label: string; icon: LucideIcon; pill: string }> = {
  PENDING: { label: 'Pending Human Approval', icon: Clock, pill: 'bg-warn-light text-warn-text ring-warn/40' },
  APPROVED: { label: 'Approved', icon: Check, pill: 'bg-brand-light text-brand-text ring-brand/40' },
  REJECTED: { label: 'Rejected', icon: X, pill: 'bg-danger-light text-danger-text ring-danger/40' },
  EXPIRED: { label: 'Expired: not approved within 10 minutes', icon: TimerOff, pill: 'bg-surface text-body ring-line' },
}

interface ApprovalStatusProps {
  /** the backend's record of the card's approval; missing from an older backend (then this browser's decision shows) */
  approval?: ChatApproval
  /** what the customer decided in this browser: Confirm (CONFIRMED) or Cancel / Keep it (DISMISSED) */
  done?: ActionDecision
  /** the card is older than 10 minutes and nobody decided: it cannot be approved any more */
  expired: boolean
}

/**
 * "Human approval: ✓ Approved · by you · 1 Nov, 3:31 pm", at the top of a card of the chat. The assistant only prepares
 * an order, a cancellation or a cart change; nothing happens until a human (the customer) approves it with Confirm. This
 * line says where that approval stands, and it stays in the chat history (the backend keeps it, useChat loads it).
 */
export function ApprovalStatus({ approval, done, expired }: ApprovalStatusProps) {
  const { user } = useAuth()
  // This browser's decision first (it is newer than the backend's answer for a moment), then the backend's record
  const shown: Shown =
    done === 'CONFIRMED' || approval?.status === 'APPROVED'
      ? 'APPROVED'
      : done === 'DISMISSED' || approval?.status === 'REJECTED'
        ? 'REJECTED'
        : expired
          ? 'EXPIRED'
          : 'PENDING'
  const { label, icon: Icon, pill } = LOOKS[shown]
  const decidedAt = approval?.decidedAt
  // Only the conversation's owner can decide: show "by you" when that is the person looking at it
  const byYou = approval?.decidedByUserId != null && approval.decidedByUserId === user?.id

  return (
    <p className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
      <span className="font-heading font-bold text-body">Human approval</span>
      {/* aria-live: a screen reader says the new status when it changes (Pending → Approved) */}
      <span aria-live="polite" className={cn('inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 font-bold ring-1 transition-colors', pill)}>
        {/* Pending: the clock pulses softly (it still waits for a click); a decision's tick or cross jumps in once */}
        <Icon
          className={cn('size-3.5', shown === 'PENDING' && 'motion-safe:animate-pulse', (shown === 'APPROVED' || shown === 'REJECTED') && 'motion-safe:animate-pop')}
          aria-hidden="true"
        />
        {label}
      </span>
      {decidedAt && (shown === 'APPROVED' || shown === 'REJECTED') && (
        <span className="text-body">
          {byYou && 'by you · '}
          <time dateTime={decidedAt}>{formatShortDateTime(decidedAt)}</time>
        </span>
      )}
    </p>
  )
}
