import { expect, test } from '@playwright/test'
import { fileURLToPath } from 'node:url'
import { skipSalePopup } from './helpers'

/*
 * Smoke flow 30: a guest asks the shopping assistant by voice. Edge's fake microphone "speaks"
 * fixtures/question-offers.wav (made once by our speech server, speaches); the speech server writes it down, the
 * assistant answers (20-60 s on a CPU), and the answer is read aloud by itself. Needs the speech server and the model
 * (start-infra.ps1 -Profiles ai). In a file of its own: the fake microphone is a setting of the whole browser.
 */

const question = fileURLToPath(new URL('./fixtures/question-offers.wav', import.meta.url))

test.use({
  permissions: ['microphone'],
  // "%noloop": the fake microphone says the question once, then stays silent
  launchOptions: { args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', `--use-file-for-fake-audio-capture=${question}%noloop`] },
})

test.setTimeout(240_000)

test('a guest asks by voice and hears the answer', async ({ page }) => {
  await skipSalePopup(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Ask our assistant' }).click()
  const chat = page.getByRole('dialog', { name: 'Shopping assistant' })

  await chat.getByRole('button', { name: 'Ask by voice' }).click()
  await expect(chat.getByText(/^Listening…/)).toBeVisible()
  await page.waitForTimeout(3500) // the question takes about 2 s
  await chat.getByRole('button', { name: 'Stop recording' }).click()

  // What the speech server understood is sent as the question
  const log = chat.getByRole('log', { name: 'Chat' })
  await expect(log.getByRole('listitem').first(), 'the speech server wrote the question down (is speaches running?)').toContainText(/which offers are running/i, {
    timeout: 60_000,
  })

  const speech = page.waitForResponse((response) => response.url().endsWith('/api/v1/chat/speech'), { timeout: 180_000 })
  await expect(log.getByRole('listitem').or(chat.getByRole('button', { name: 'Try again' }))).toHaveCount(2, { timeout: 150_000 })
  await expect(chat.getByRole('button', { name: 'Try again' }), 'the assistant answered (is Ollama running?)').toHaveCount(0)

  // The answer to a spoken question is read aloud by itself; afterwards "Listen" can read it again
  const audio = await speech
  expect(audio.status()).toBe(200)
  expect(audio.headers()['content-type']).toBe('audio/mpeg')
  await expect(log.getByRole('button', { name: 'Listen' })).toBeVisible({ timeout: 90_000 })
})
