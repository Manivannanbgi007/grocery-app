import boldUrl from '../../assets/fonts/NotoSans-Bold.ttf?url'
import regularUrl from '../../assets/fonts/NotoSans-Regular.ttf?url'

/*
 * The PDF's font. jsPDF's built-in fonts only know Western European letters: "₹" and many names would come out as
 * garbage. Noto Sans (SIL Open Font License, assets/fonts/OFL.txt) has the rupee sign and Latin, Greek and Cyrillic
 * letters. The two files are fetched only when a PDF is made, once per visit; jsPDF puts only the letters used into
 * the PDF, so the file stays small.
 */

export interface PdfFonts {
  regular: string
  bold: string
}

let loading: Promise<PdfFonts> | null = null

async function base64Of(url: string): Promise<string> {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`Could not load the font ${url} (${response.status})`)
  const bytes = new Uint8Array(await response.arrayBuffer())
  // In pieces: String.fromCharCode with 400,000 arguments would overflow the call stack
  let binary = ''
  for (let start = 0; start < bytes.length; start += 0x8000) binary += String.fromCharCode(...bytes.subarray(start, start + 0x8000))
  return btoa(binary)
}

/** The two font files as base64 for jsPDF's addFileToVFS. A failed load is tried again next time. */
export function loadPdfFonts(): Promise<PdfFonts> {
  loading ??= Promise.all([base64Of(regularUrl), base64Of(boldUrl)])
    .then(([regular, bold]) => ({ regular, bold }))
    .catch((error: unknown) => {
      loading = null
      throw error
    })
  return loading
}
