/**
 * Hands a downloaded file to the browser, which saves it (or asks where to), like clicking a download link.
 * The temporary address of the file is released again right after.
 */
export function saveFile(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}
