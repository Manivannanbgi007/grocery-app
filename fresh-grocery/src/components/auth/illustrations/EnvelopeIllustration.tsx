/** An envelope with a letter and a check: "Check your email". Decoration only (hidden from screen readers). */
export function EnvelopeIllustration({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 200 160" className={className} aria-hidden="true" focusable="false">
      <circle className="fill-brand-light" cx="100" cy="84" r="68" />
      <path d="M30 34Q30 42 38 42Q30 42 30 50Q30 42 22 42Q30 42 30 34Z" fill="#fdc040" />
      <circle className="fill-brand" cx="176" cy="116" r="4" opacity="0.6" />

      {/* The back of the envelope, the letter, then the front pocket */}
      <rect className="fill-brand-soft" x="38" y="52" width="124" height="82" rx="10" />
      <rect x="54" y="28" width="92" height="76" rx="6" fill="#fff" />
      <rect className="fill-brand" x="66" y="42" width="50" height="6" rx="3" />
      <rect x="66" y="56" width="68" height="5" rx="2.5" fill="#e7eaf3" />
      <rect x="66" y="68" width="58" height="5" rx="2.5" fill="#e7eaf3" />
      <path className="fill-brand" d="M38 70l62 40 62-40v54a10 10 0 0 1-10 10H48a10 10 0 0 1-10-10Z" />
      <path className="stroke-brand-dark" d="M40 132l46-34M160 132l-46-34" strokeWidth="3" strokeLinecap="round" />

      {/* Sent */}
      <circle cx="156" cy="42" r="18" fill="#fdc040" />
      <path d="M147 42l6 6 12-12" stroke="#253d4e" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </svg>
  )
}
