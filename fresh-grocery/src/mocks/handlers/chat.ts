import { delay, http, HttpResponse, type HttpResponseResolver } from 'msw'
import { MockApiError } from '../mockApiError'
import { userFromRequest } from '../mockAuth'
import { chatAnswer, chatConversation, decideChatApproval, deleteChatConversation } from '../mockChat'
import { MOCK_HEARD_QUESTION, silentWav } from '../mockVoice'
import { fail, ok } from './respond'

/* Mock of the AI shopping assistant (mcp-client-service): open to guests; a token, if sent, says who asks. */

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** Runs an action for a guest or a logged-in user; a MockApiError becomes the matching error response. */
function asAnyone(action: (request: Request, params: Record<string, string | readonly string[] | undefined>) => Promise<{ data: unknown; message: string }>): HttpResponseResolver {
  return async ({ request, params }) => {
    await delay()
    const id = params.conversationId
    if (typeof id === 'string' && !UUID.test(id)) return fail(400, `Invalid value '${id}' for parameter 'conversationId'`, request)
    try {
      const result = await action(request, params)
      return ok(result.data, result.message)
    } catch (error) {
      if (error instanceof MockApiError) return fail(error.status, error.message, request, error.details)
      throw error
    }
  }
}

export const chatHandlers = [
  http.post(
    '*/api/v1/chat/messages',
    asAnyone(async (request) => {
      const body = (await request.json()) as Parameters<typeof chatAnswer>[1]
      return { data: chatAnswer(userFromRequest(request), body), message: 'Answer ready' }
    }),
  ),

  http.get(
    '*/api/v1/chat/conversations/:conversationId',
    asAnyone(async (request, params) => ({ data: chatConversation(userFromRequest(request), String(params.conversationId)), message: 'Conversation found' })),
  ),

  http.delete(
    '*/api/v1/chat/conversations/:conversationId',
    asAnyone(async (request, params) => {
      deleteChatConversation(userFromRequest(request), String(params.conversationId))
      return { data: null, message: 'Conversation deleted' }
    }),
  ),

  // The customer's decision on a card (human approval)
  http.post(
    '*/api/v1/chat/approvals/:approvalId',
    asAnyone(async (request, params) => {
      const body = (await request.json()) as { decision?: string }
      return { data: decideChatApproval(userFromRequest(request), String(params.approvalId), body), message: 'Decision saved' }
    }),
  ),

  // Voice (mock speech server, see mockVoice.ts)
  http.post('*/api/v1/chat/transcriptions', async ({ request }) => {
    await delay()
    // Only checks that the part is there: the tests' fake browser (jsdom) cannot send a recording's bytes
    if (!(await request.text()).includes('name="audio"')) return fail(400, "Required part 'audio' is not present.", request)
    return ok({ text: MOCK_HEARD_QUESTION }, 'Question heard')
  }),

  http.post('*/api/v1/chat/speech', async ({ request }) => {
    await delay()
    const { text } = (await request.json()) as { text?: string }
    if (!text?.trim()) return fail(400, 'Please give the text to read aloud', request)
    return new HttpResponse(silentWav(text), { headers: { 'Content-Type': 'audio/wav' } })
  }),
]
