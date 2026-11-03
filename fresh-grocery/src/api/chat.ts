import { apiAudio, apiRequest } from './client'
import type { ChatApproval, ChatApprovalStatus, ChatConversation, ChatReply, ConfirmedCard } from './types'

/*
 * The AI assistant (mcp-client-service), open to everybody: the shopping assistant for guests and customers, the work
 * assistant for vendors, delivery partners and admins (the backend picks the tools and rules from the login). An answer
 * takes from 2 to 60 seconds (the language model runs on a CPU locally; the gateway waits up to 180 s), at most 10
 * questions a minute (429). A conversation belongs to whoever started it (a guest or one user): anyone else gets 404.
 */

/** At most this many characters per question (ChatMessageRequest). */
export const CHAT_MESSAGE_MAX = 500

/**
 * One question. Without a conversationId a new conversation starts; its id comes with the answer.
 * `wishlist`: the product names on the wishlist, which is kept in this browser (the backend has none), so the shop's
 * assistant can answer "what is on my wishlist?". The panels leave it out.
 * `confirmed`: the cards the customer confirmed since their last question (left out when there are none).
 */
export function sendChatMessage(message: string, conversationId: string | null, wishlist?: string[], confirmed?: ConfirmedCard[]): Promise<ChatReply> {
  const body = { conversationId, message, wishlist, confirmed: confirmed?.length ? confirmed : undefined }
  return apiRequest<ChatReply>('/api/v1/chat/messages', { method: 'POST', body })
}

/** The messages of a conversation, e.g. to show it again after a reload. */
export function getChatConversation(conversationId: string, signal?: AbortSignal): Promise<ChatConversation> {
  return apiRequest<ChatConversation>(`/api/v1/chat/conversations/${conversationId}`, { signal })
}

/**
 * The customer's decision on a card (human approval), right after the click: APPROVED once Confirm did the order or cart
 * change, REJECTED after Cancel or Keep it. Only the decision is stored, for the chat history; a decision is final.
 */
export function recordChatApproval(approvalId: string, decision: Exclude<ChatApprovalStatus, 'PENDING'>): Promise<ChatApproval> {
  return apiRequest<ChatApproval>(`/api/v1/chat/approvals/${approvalId}`, { method: 'POST', body: { decision } })
}

/** Deletes all messages of a conversation ("New chat"). */
export function deleteChatConversation(conversationId: string): Promise<void> {
  return apiRequest<void>(`/api/v1/chat/conversations/${conversationId}`, { method: 'DELETE' })
}

/*
 * Voice, on our own speech server (English only; no audio leaves our machines). A spoken question takes three calls:
 * transcribeQuestion (the recording -> its words), sendChatMessage (the words, exactly like a typed question) and
 * speakText (the answer -> mp3). At most 20 voice calls a minute (429); 503 when the speech server is not available.
 */

/** At most this many characters can be read aloud in one call (SpeechRequest). */
export const SPEECH_TEXT_MAX = 2000

/** The words of a recorded question (webm, ogg or mp4; at most 2 MB, about 30 s). */
export async function transcribeQuestion(audio: Blob): Promise<string> {
  const form = new FormData()
  // The file name only tells the speech server the format: "question.webm", "question.ogg" or "question.mp4"
  const extension = /ogg/.test(audio.type) ? 'ogg' : /mp4|aac/.test(audio.type) ? 'mp4' : 'webm'
  form.append('audio', audio, `question.${extension}`)
  const result = await apiRequest<{ text: string }>('/api/v1/chat/transcriptions', { method: 'POST', body: form })
  return result.text
}

/** A text read aloud, as mp3. */
export function speakText(text: string, signal?: AbortSignal): Promise<Blob> {
  return apiAudio('/api/v1/chat/speech', { text: text.slice(0, SPEECH_TEXT_MAX) }, signal)
}
