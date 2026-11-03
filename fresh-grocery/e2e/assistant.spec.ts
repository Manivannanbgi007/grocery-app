import { expect, test } from '@playwright/test'
import { skipSalePopup, VENDOR1 } from './helpers'

/*
 * Smoke flow 25: a guest asks the AI shopping assistant (mcp-client-service + mcp-server-service + Ollama) a real question
 * through the gateway. The language model runs on a CPU locally: an answer takes 20-60 s, so this flow may take up to
 * 3 minutes. The answer itself differs from run to run; the flow checks that one arrives, survives a reload, and that
 * "New chat" starts again.
 * Smoke flow 29: vendor1 asks the store assistant in the vendor panel; the answer must come from the store's own data
 * (a vendor tool ran and the answer passed the backend's check: "Checked against your store's live data").
 * Smoke flow 30 (asking by voice) is in voice.spec.ts: it needs its own browser, with a fake microphone.
 */

test.setTimeout(180_000)

test('a guest asks the shopping assistant, sees the chat again after a reload, and starts a new chat', async ({ page }) => {
  await skipSalePopup(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Ask our assistant' }).click()
  const chat = page.getByRole('dialog', { name: 'Shopping assistant' })
  await chat.getByRole('button', { name: 'Do you have tomatoes? How much are they?' }).click()

  const log = chat.getByRole('log', { name: 'Chat' })
  await expect(log.getByRole('listitem')).toHaveCount(1)
  await expect(chat.getByText('Looking it up…', { exact: false })).toBeVisible()
  // The question and the assistant's answer (or an error the chat shows with "Try again")
  await expect(log.getByRole('listitem').or(chat.getByRole('button', { name: 'Try again' }))).toHaveCount(2, { timeout: 150_000 })
  await expect(chat.getByRole('button', { name: 'Try again' }), 'the assistant answered (is Ollama running?)').toHaveCount(0)
  const answer = (await log.getByRole('listitem').nth(1).textContent())!.replace('Assistant:', '').trim()
  expect(answer.length).toBeGreaterThan(0)

  // The conversation id is in sessionStorage: after a reload the chat shows the same messages
  await page.reload()
  await page.getByRole('button', { name: 'Ask our assistant' }).click()
  await expect(chat.getByRole('log', { name: 'Chat' }).getByRole('listitem')).toHaveCount(2)
  await expect(chat.getByText('Do you have tomatoes? How much are they?')).toBeVisible()

  await chat.getByRole('button', { name: 'New chat' }).click()
  await expect(chat.getByRole('list', { name: 'Example questions' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(chat).toHaveCount(0)
})

test('a vendor asks the store assistant about the new orders and gets an answer from the store', async ({ page }) => {
  await skipSalePopup(page)
  await page.goto('/vendor')
  await page.getByLabel('Email').fill(VENDOR1.email)
  await page.getByLabel('Password', { exact: true }).fill(VENDOR1.password)
  await page.getByRole('button', { name: 'Log in' }).click()

  await page.getByRole('button', { name: 'Ask the assistant' }).click()
  const chat = page.getByRole('dialog', { name: 'Store assistant' })
  await chat.getByRole('button', { name: 'Do I have new orders?' }).click()

  const log = chat.getByRole('log', { name: 'Chat' })
  await expect(log.getByRole('listitem').or(chat.getByRole('button', { name: 'Try again' }))).toHaveCount(2, { timeout: 150_000 })
  await expect(chat.getByRole('button', { name: 'Try again' }), 'the assistant answered (is Ollama running?)').toHaveCount(0)
  await expect(chat.getByText("Checked against your store's live data"), 'a vendor tool answered (new mcp services running?)').toBeVisible()
})
