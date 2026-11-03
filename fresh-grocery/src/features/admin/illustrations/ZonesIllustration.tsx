/** A folded map with zones, a dashed route and two pins that drift: the Delivery zones page. Decoration only. */
export function ZonesIllustration({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 240 180" className={className} aria-hidden="true" focusable="false">
      <circle cx="120" cy="92" r="82" fill="#fff" opacity="0.55" />

      {/* The map, folded in three */}
      <path d="M36 56l52-14 60 14 56-14v104l-56 14-60-14-52 14Z" fill="#000" opacity="0.06" transform="translate(4 6)" />
      <path d="M36 56l52-14 60 14 56-14v104l-56 14-60-14-52 14Z" fill="#fff" />
      <path d="M88 42l60 14v104l-60-14Z" className="fill-sale-light" />
      {/* Two zones and a road */}
      <path d="M46 70c14-8 30-4 34 8s-8 26-22 26-20-26-12-34Z" className="fill-brand-light" />
      <path d="M150 96c16-10 40-4 42 12s-16 26-30 22-22-24-12-34Z" className="fill-brand-light" />
      <path d="M36 122l52-14 60 14 56-14" stroke="#e7eaf3" strokeWidth="6" fill="none" />
      {/* The route from the store to the customer */}
      <path d="M64 92c24 0 30 30 56 22s40-24 60-6" stroke="#075f24" strokeWidth="3" strokeDasharray="5 6" strokeLinecap="round" fill="none" />

      {/* Two pins that drift */}
      <g className="motion-safe:animate-float">
        <path d="M64 92c-10-12-14-18-14-24a14 14 0 0 1 28 0c0 6-4 12-14 24Z" className="fill-brand" />
        <circle cx="64" cy="68" r="5" fill="#fff" />
      </g>
      <g className="motion-safe:animate-float" style={{ animationDelay: '-3.5s' }}>
        <path d="M180 108c-10-12-14-18-14-24a14 14 0 0 1 28 0c0 6-4 12-14 24Z" className="fill-hot" />
        <circle cx="180" cy="84" r="5" fill="#fff" />
      </g>
      <path d="M200 34l3 7 7 3-7 3-3 7-3-7-7-3 7-3Z" className="fill-accent" />
    </svg>
  )
}
