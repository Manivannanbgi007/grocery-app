/** A bank card in front of a paid receipt, and coins that drift: the Payments page. Decoration only. */
export function PaymentsIllustration({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 240 180" className={className} aria-hidden="true" focusable="false">
      <circle cx="120" cy="92" r="82" fill="#fff" opacity="0.55" />

      {/* The receipt, a little tilted, with a green "paid" check */}
      <g transform="rotate(8 166 82)">
        <rect x="130" y="30" width="72" height="100" rx="8" fill="#fff" />
        {[48, 60, 72, 84].map((y, index) => (
          <rect key={y} x="142" y={y} width={index % 2 ? 34 : 48} height="6" rx="3" fill="#d5dbe5" />
        ))}
        <circle cx="166" cy="110" r="11" className="fill-brand" />
        <path d="M160 110l4 4 8-8" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" fill="none" />
      </g>

      {/* The card */}
      <rect x="40" y="68" width="22" height="8" rx="4" fill="#000" opacity="0.08" />
      <rect x="30" y="60" width="122" height="78" rx="12" className="fill-brand" />
      <rect x="30" y="76" width="122" height="13" className="fill-brand-dark" />
      <rect x="44" y="98" width="22" height="16" rx="3" className="fill-accent" />
      {[44, 70, 96, 122].map((x) => (
        <rect key={x} x={x} y="122" width="20" height="5" rx="2.5" fill="#fff" opacity="0.8" />
      ))}

      {/* Coins that drift */}
      <g className="motion-safe:animate-float" style={{ animationDelay: '-2s' }}>
        <circle cx="196" cy="140" r="17" className="fill-accent" />
        <circle cx="196" cy="140" r="12" fill="none" stroke="#fff" strokeWidth="2" opacity="0.6" />
        <text x="196" y="146" textAnchor="middle" fontSize="16" fontWeight="700" fill="#8a5a00">
          ₹
        </text>
      </g>
      <g className="motion-safe:animate-float" style={{ animationDelay: '-5s' }}>
        <circle cx="168" cy="160" r="11" className="fill-accent" />
        <text x="168" y="164" textAnchor="middle" fontSize="11" fontWeight="700" fill="#8a5a00">
          ₹
        </text>
      </g>
      <path d="M40 34l3 7 7 3-7 3-3 7-3-7-7-3 7-3Z" className="fill-accent" />
    </svg>
  )
}
