/*
 * The id of the open conversation with the shopping assistant, kept in sessionStorage per login ("guest" or a user id),
 * so a reload shows the conversation again while a login or logout starts a new one (a conversation belongs to whoever
 * started it). sessionStorage can be blocked (private windows); then the id is kept in memory for this page only.
 */

const KEY_PREFIX = 'fresh-grocery.chat.'
const inMemory = new Map<string, string>()

export function savedConversationId(owner: string): string | null {
  try {
    return sessionStorage.getItem(KEY_PREFIX + owner)
  } catch {
    return inMemory.get(owner) ?? null
  }
}

export function saveConversationId(owner: string, conversationId: string): void {
  inMemory.set(owner, conversationId)
  try {
    sessionStorage.setItem(KEY_PREFIX + owner, conversationId)
  } catch {
    // blocked: the in-memory copy is enough for this page
  }
}

export function forgetConversationId(owner: string): void {
  inMemory.delete(owner)
  try {
    sessionStorage.removeItem(KEY_PREFIX + owner)
  } catch {
    // blocked: nothing was saved there
  }
}

/** The chat's height: as high as the screen, or the compact chat. Remembered in this browser (localStorage). */
export type ChatSize = 'maximized' | 'minimized'

const SIZE_KEY = 'fresh-grocery.chat-size'

/** The size chosen last time; maximized when nothing was chosen (or storage is blocked). */
export function savedChatSize(): ChatSize {
  try {
    return localStorage.getItem(SIZE_KEY) === 'minimized' ? 'minimized' : 'maximized'
  } catch {
    return 'maximized'
  }
}

export function saveChatSize(size: ChatSize): void {
  try {
    localStorage.setItem(SIZE_KEY, size)
  } catch {
    // blocked: the chat keeps the size until it closes
  }
}
