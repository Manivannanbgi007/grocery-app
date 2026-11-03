import { ArrowRight, ExternalLink } from 'lucide-react'
import { Link } from 'react-router'
import type { ChatLink } from '../../api/types'
import { cn } from '../../utils/cn'
import type { AssistantKind } from './assistantTexts'
import { parseChatText, safeExternalUrl, safeInternalPath } from './chatLinks'

interface ChatMessageTextProps {
  text: string
  /** which chat it is: decides which pages its links may open (chatLinks.ts) */
  kind: AssistantKind
  /** after a click on a link to a page of the app (the chat may close on small screens) */
  onNavigate: () => void
}

const TEXT_LINK = 'font-bold text-brand-text underline underline-offset-2 hover:text-brand-dark'

/**
 * An answer's text, still plain text (never HTML), with its links working: a Markdown link or an address to a page
 * of the app opens it with React Router (no page reload); an http(s) address of another site opens in a new tab; a
 * link to anything else shows only its label.
 */
export function ChatMessageText({ text, kind, onNavigate }: ChatMessageTextProps) {
  return (
    <>
      {parseChatText(text).map((part, index) => {
        if (part.kind === 'text') return part.text
        const path = safeInternalPath(part.target, kind)
        if (path) {
          return (
            <Link key={index} to={path} onClick={onNavigate} className={TEXT_LINK}>
              {part.label}
            </Link>
          )
        }
        const url = safeExternalUrl(part.target)
        if (url) {
          return (
            <a key={index} href={url} target="_blank" rel="noopener noreferrer" className={`${TEXT_LINK} break-all`}>
              {part.label} <ExternalLink className="inline size-3.5 align-[-0.125em]" aria-hidden="true" />
              <span className="sr-only">(opens in a new tab)</span>
            </a>
          )
        }
        return part.label
      })}
    </>
  )
}

/**
 * The page buttons under an answer take turns in these colours (only the look): green, blue, orange, violet. Each one
 * fills with a light wash of its colour under the mouse. Tailwind needs the whole class names here.
 */
const LINK_LOOKS = [
  'border-brand-soft text-brand-text hover:border-brand hover:bg-brand-light',
  'border-sale/40 text-sale-text hover:border-sale hover:bg-sale-light',
  'border-new/40 text-warn-text hover:border-new hover:bg-new-light',
  'border-violet/40 text-violet-text hover:border-violet hover:bg-violet-light',
]

interface ChatLinksProps {
  /** the reply's links (from the backend); each one is checked again here */
  links: ChatLink[]
  kind: AssistantKind
  onNavigate: () => void
}

/**
 * The pages that fit an answer, as round buttons under it ("Your wishlist →"). A labelled group of links, not a list:
 * the message itself is a list item of the chat, and the e2e flows count the messages by list item.
 */
export function ChatLinks({ links, kind, onNavigate }: ChatLinksProps) {
  const safeLinks = links.flatMap((link) => {
    const path = safeInternalPath(link.path, kind)
    return path ? [{ label: link.label, path }] : []
  })
  if (safeLinks.length === 0) return null

  return (
    <div role="group" aria-label="Pages for this answer" className="flex flex-wrap gap-2">
      {safeLinks.map((link, index) => (
        <Link
          key={link.path}
          to={link.path}
          onClick={onNavigate}
          className={cn(
            'group inline-flex items-center gap-1.5 rounded-full border bg-card px-3 py-1.5 font-heading text-xs font-bold shadow-sm transition duration-200 hover:-translate-y-0.5',
            LINK_LOOKS[index % LINK_LOOKS.length],
          )}
        >
          {link.label}
          <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
        </Link>
      ))}
    </div>
  )
}
