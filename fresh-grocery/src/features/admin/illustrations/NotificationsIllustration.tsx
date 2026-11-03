/** A bell that swings with a red count, sound waves, and an email behind it: the Notifications page. Decoration only. */
export function NotificationsIllustration({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 240 180" className={className} aria-hidden="true" focusable="false">
      <circle cx="120" cy="92" r="82" fill="#fff" opacity="0.55" />

      {/* The email behind, drifting */}
      <g className="motion-safe:animate-float" style={{ animationDelay: '-2.5s' }}>
        <rect x="128" y="86" width="88" height="60" rx="8" fill="#fff" />
        <path d="M130 90l42 30 42-30" stroke="#d5dbe5" strokeWidth="4" strokeLinejoin="round" fill="none" />
        <circle cx="210" cy="90" r="7" className="fill-sale" />
      </g>

      {/* The bell: swings twice when the page opens (transform-box: it turns round its own top) */}
      <g className="origin-top [transform-box:fill-box] motion-safe:animate-wave">
        <circle cx="96" cy="40" r="6" className="fill-new" />
        <path d="M60 120h72l-12-18V76a24 24 0 0 0-48 0v26Z" className="fill-accent" />
        <path d="M72 102V78a24 24 0 0 1 14-22" stroke="#fff" strokeWidth="4" strokeLinecap="round" fill="none" opacity="0.6" />
        <circle cx="96" cy="128" r="9" className="fill-new" />
      </g>
      {/* The count */}
      <circle cx="126" cy="56" r="13" className="fill-hot" />
      <text x="126" y="61" textAnchor="middle" fontSize="13" fontWeight="700" fill="#fff">
        3
      </text>
      {/* Sound waves */}
      <path d="M44 66c-6 8-6 22 0 30M34 58c-10 13-10 33 0 46" className="stroke-brand" strokeWidth="4" strokeLinecap="round" fill="none" />
      <path d="M32 140l3 7 7 3-7 3-3 7-3-7-7-3 7-3Z" className="fill-accent" />
    </svg>
  )
}
