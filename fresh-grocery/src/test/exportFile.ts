import { screen, waitFor, within } from '@testing-library/react'
import type { UserEvent } from '@testing-library/user-event'
import { expect, type Mock } from 'vitest'

export interface ExportedFile {
  fileName: string
  blob: Blob
  /** the file's text (CSV only; empty for Excel and PDF) */
  text: string
}

/**
 * Exports the list on screen with its Export menu and returns the saved file. The test file mocks the download first:
 *
 *   vi.mock('../../utils/download', () => ({ saveFile: vi.fn() }))   // the path from the test file to utils/download
 *   const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')
 *
 * `within`: the part of the page with the list, when the page has more than one Export button.
 */
export async function exportList(user: UserEvent, saveFile: Mock, format: 'Excel' | 'CSV' | 'PDF' = 'CSV', within_?: HTMLElement): Promise<ExportedFile> {
  const scope = within_ ? within(within_) : screen
  const calls = saveFile.mock.calls.length
  await user.click(await scope.findByRole('button', { name: 'Export' }))
  await user.click(screen.getByRole('menuitem', { name: new RegExp(`^${format}`) }))
  await waitFor(() => expect(saveFile.mock.calls.length).toBeGreaterThan(calls), { timeout: 5000 })
  const [blob, fileName] = saveFile.mock.calls.at(-1) as [Blob, string]
  return { fileName, blob, text: format === 'CSV' ? await blob.text() : '' }
}

/** A CSV's lines without the byte order mark: [header, ...rows]. */
export function csvLines(text: string): string[] {
  return text.replace(/^﻿/, '').replace(/\r\n$/, '').split('\r\n')
}
