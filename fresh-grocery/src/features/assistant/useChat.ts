import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { deleteChatConversation, getChatConversation, recordChatApproval, sendChatMessage } from '../../api/chat'
import { isApiError } from '../../api/client'
import { queryKeys } from '../../api/queryKeys'
import type { ChatAction, ChatApproval, ChatApprovalStatus, ChatConversation, ChatLink, ConfirmedCard } from '../../api/types'
import { useAuth } from '../auth/authContext'
import { useWishlist } from '../wishlist/useWishlist'
import { forgetConversationId, saveConversationId, savedConversationId } from './chatStorage'

/** What the customer decided on a card: CONFIRMED (ordered, or cancelled the order) or DISMISSED (left it as it was). */
export type ActionDecision = 'CONFIRMED' | 'DISMISSED'

/** One message of the chat. */
export interface ChatLine {
  role: 'USER' | 'ASSISTANT'
  text: string
  /** an answer from the shop's live data that passed the fact check (unknown for messages loaded after a reload) */
  grounded?: boolean
  /** the answer to a spoken question: the chat reads it aloud by itself */
  spoken?: boolean
  /** pages that fit the answer (none for messages loaded after a reload) */
  links?: ChatLink[]
  /** a summary to confirm with a button (the order card); none for messages loaded after a reload */
  action?: ChatAction
  /** made once for the card: every click on its Confirm sends it, so the card can never place two orders */
  actionKey?: string
  /** when the card arrived (Date.now()): it can be confirmed for 10 minutes */
  actionReceivedAt?: number
  /** what the customer decided on the card */
  actionDone?: ActionDecision
  /** on the line after a click on Confirm ("Order #207 placed ..."): the next question tells the assistant about it */
  confirmed?: ConfirmedCard
  /**
   * Only on the line after an ONLINE order was placed: where to pay it, exactly as order-service sent it
   * (Order.paymentUrl). The line shows "Pay Now" for it, or a message when it is missing or not a page of the shop.
   */
  paymentUrl?: string | null
  /** the card's human approval as the backend keeps it (PENDING, APPROVED or REJECTED, and when); the card shows it */
  approval?: ChatApproval
}

/** The time now, written like the backend's times ("2026-11-01T10:01:30.000Z") */
function nowAsIso(): string {
  return new Date().toISOString()
}

/** The backend's approval status as the card's decision (none while it is pending) */
function decisionOf(status: ChatApprovalStatus): ActionDecision | undefined {
  if (status === 'APPROVED') return 'CONFIRMED'
  if (status === 'REJECTED') return 'DISMISSED'
  return undefined
}

/**
 * A message of a saved conversation as a line of the chat. An answer that had a card gets it back, with its key, the
 * time it was shown (it can be confirmed for 10 minutes) and its human approval, so "✓ Approved" shows again.
 */
function historyLine(message: ChatConversation['messages'][number]): ChatLine {
  if (!message.action || !message.approval) return { role: message.role, text: message.text }
  return {
    role: message.role,
    text: message.text,
    action: message.action,
    actionKey: message.approval.id,
    actionReceivedAt: Date.parse(message.approval.createdAt),
    actionDone: decisionOf(message.approval.status),
    approval: message.approval,
  }
}

/** A question that failed, for "Try again" (a spoken one is answered aloud again). */
interface FailedQuestion {
  text: string
  spoken: boolean
}

/** "guest" or the user id: whose conversation it is. */
export function useChatOwner(): string {
  const { user } = useAuth()
  return user ? String(user.id) : 'guest'
}

/**
 * The conversation with the shopping assistant: the messages so far, asking a question, trying a failed one again and
 * starting a new chat. The conversation id lives in sessionStorage (chatStorage.ts); the messages of a saved
 * conversation are loaded once, when the chat is first opened.
 * `shop`: the shop's chat sends the wishlist's product names with every question (the wishlist is kept in this browser,
 * so the backend cannot look it up); the panels' chats do not. The shop's assistant can also change a customer's cart
 * ("add 2 kg tomatoes"): after every answer, and after every error (a change may have happened before it), the cart is
 * loaded again, so the cart badge and the cart page show what is really in it.
 */
export function useChat(open: boolean, shop = false) {
  const owner = useChatOwner()
  const { user } = useAuth()
  const wishlist = useWishlist()
  const queryClient = useQueryClient()
  /** the conversation to show again after a reload (null: nothing to load) */
  const [savedId, setSavedId] = useState(() => savedConversationId(owner))
  const [lines, setLines] = useState<ChatLine[]>([])
  const [failedQuestion, setFailedQuestion] = useState<FailedQuestion | null>(null)
  /** the customer's decisions on cards (by the card's key), for the cards of this browser and of the loaded history alike */
  const [decided, setDecided] = useState<Record<string, Pick<ChatLine, 'actionDone' | 'approval'>>>({})
  /** cards confirmed since the last question: this browser placed or cancelled the order, so the assistant hears it */
  const unsentConfirmations = useRef<ConfirmedCard[]>([])

  const history = useQuery({
    queryKey: queryKeys.chatConversation(owner, savedId),
    queryFn: async ({ signal }) => {
      try {
        return (await getChatConversation(savedId!, signal)).messages.map(historyLine)
      } catch (error) {
        // Gone (deleted, or only the latest messages are kept): start a new conversation
        if (isApiError(error, 404)) {
          forgetConversationId(owner)
          return []
        }
        throw error
      }
    },
    enabled: open && savedId !== null,
    staleTime: Infinity,
    retry: false,
    // The chat shows "Loading the chat…" itself: no app-wide loading sign
    meta: { quiet: true },
  })

  const send = useMutation({
    mutationFn: ({ question, confirmed }: { question: string; confirmed: ConfirmedCard[] }) =>
      sendChatMessage(question, savedConversationId(owner), shop ? wishlist.products.map((product) => product.name) : undefined, confirmed),
    onSuccess: (reply) => saveConversationId(owner, reply.conversationId),
    onError: (error) => {
      if (isApiError(error, 404)) forgetConversationId(owner)
    },
    onSettled: () => {
      if (shop) void queryClient.invalidateQueries({ queryKey: queryKeys.allCarts })
    },
    // The chat shows "Looking it up…" itself
    meta: { quiet: true },
  })
  const remove = useMutation({ mutationFn: deleteChatConversation })

  function post(question: string, spoken: boolean) {
    setFailedQuestion(null)
    const confirmed = unsentConfirmations.current
    send.mutate({ question, confirmed }, {
      onSuccess: (reply) => {
        // Told; after an error they go again with the next try
        unsentConfirmations.current = unsentConfirmations.current.filter((card) => !confirmed.includes(card))
        // The card's key is its approval's id (the backend keeps the decision under it); without one (an older backend) a new key
        const card = reply.action
          ? { action: reply.action, actionKey: reply.approval?.id ?? crypto.randomUUID(), actionReceivedAt: Date.now(), approval: reply.approval ?? undefined }
          : {}
        // "Proceed to pay" after an online order was placed: the order's own payment address, for "Pay Now"
        const payment = reply.paymentUrl ? { paymentUrl: reply.paymentUrl } : {}
        setLines((current) => [...current, { role: 'ASSISTANT', text: reply.answer, grounded: reply.grounded, spoken, links: reply.links, ...card, ...payment }])
      },
      onError: () => setFailedQuestion({ text: question, spoken }),
    })
  }

  /**
   * The customer decided on a card (found by its key): the card shows the decision, and `followUp` (e.g. "Order #197
   * placed ...") comes after the last message. A confirmed card goes with the next question to the assistant.
   * The decision is also saved as the card's human approval (APPROVED or REJECTED), so the chat history shows it later.
   * The card shows it at once with this browser's time; the backend's answer replaces that a moment later.
   */
  function finishAction(actionKey: string, done: ActionDecision, followUp: ChatLine) {
    if (followUp.confirmed) unsentConfirmations.current = [...unsentConfirmations.current, followUp.confirmed]
    const approval = allLines.find((line) => line.actionKey === actionKey)?.approval
    const status: Exclude<ChatApprovalStatus, 'PENDING'> = done === 'CONFIRMED' ? 'APPROVED' : 'REJECTED'
    const shownNow: ChatApproval | undefined = approval && { ...approval, status, decidedAt: nowAsIso(), decidedByUserId: user?.id ?? null }
    setDecided((current) => ({ ...current, [actionKey]: { actionDone: done, approval: shownNow } }))
    setLines((current) => [...current, followUp])
    if (approval) {
      recordChatApproval(approval.id, status)
        .then((saved) => setDecided((current) => ({ ...current, [actionKey]: { actionDone: done, approval: saved } })))
        // Not saved (e.g. the connection dropped): the card still shows the decision; after a reload it may show "pending"
        .catch(() => undefined)
    }
  }

  /** The saved history, then this browser's lines; a card decided here shows that decision */
  const allLines: ChatLine[] = [...(history.data ?? []), ...lines].map((line) =>
    line.actionKey && decided[line.actionKey] ? { ...line, ...decided[line.actionKey], approval: decided[line.actionKey].approval ?? line.approval } : line,
  )

  /** Shows the question at once and asks the assistant. `spoken`: asked by voice, so the answer is read aloud. */
  function ask(question: string, spoken = false) {
    const text = question.trim()
    if (!text || send.isPending) return
    setLines((current) => [...current, { role: 'USER', text }])
    post(text, spoken)
  }

  /** Asks the question that failed again (it is already in the chat). */
  function retry() {
    if (failedQuestion) post(failedQuestion.text, failedQuestion.spoken)
  }

  /** Deletes the conversation (if there is one) and starts again. */
  function newChat() {
    const conversationId = savedConversationId(owner)
    if (conversationId) remove.mutate(conversationId)
    forgetConversationId(owner)
    setSavedId(null)
    setLines([])
    setDecided({})
    setFailedQuestion(null)
    unsentConfirmations.current = []
    send.reset()
  }

  return {
    lines: allLines,
    loadingHistory: history.isFetching,
    historyError: history.error,
    reloadHistory: () => void history.refetch(),
    pending: send.isPending,
    error: failedQuestion ? send.error : null,
    ask,
    retry,
    newChat,
    finishAction,
  }
}
