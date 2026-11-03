/** A basket full of groceries: the shop's login and sign-up screens. Decoration only (hidden from screen readers). */
export function GroceryBasketIllustration({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 400 320" className={className} aria-hidden="true" focusable="false">
      {/* Backdrop and the shadow on the ground */}
      <circle cx="200" cy="160" r="140" className="fill-card" opacity="0.65" />
      <ellipse cx="200" cy="294" rx="128" ry="10" fill="#253d4e" opacity="0.08" />

      {/* Floating leaves and sparkles */}
      <path className="fill-brand" d="M54 112c4-22 24-34 46-30-2 22-22 34-46 30Z" />
      <path className="stroke-brand-light" d="M58 108l28-18" strokeWidth="3" strokeLinecap="round" />
      <path className="fill-brand-dark" d="M316 214c18-12 40-6 50 12-18 12-40 6-50-12Z" />
      <path d="M332 62Q332 78 348 78Q332 78 332 94Q332 78 316 78Q332 78 332 62Z" fill="#fdc040" />
      <path d="M76 214Q76 222 84 222Q76 222 76 230Q76 222 68 222Q76 222 76 214Z" fill="#fdc040" />
      <circle className="fill-brand" cx="300" cy="44" r="5" opacity="0.5" />
      <circle cx="96" cy="58" r="4" fill="#f59758" opacity="0.6" />

      {/* The basket's handle, behind the groceries */}
      <path className="stroke-brand-dark" d="M128 172C128 68 272 68 272 172" fill="none" strokeWidth="12" strokeLinecap="round" />

      {/* Carrot */}
      <g transform="rotate(-20 128 130)">
        <path className="stroke-brand" d="M128 94c-3-18-14-26-22-28M128 94c0-18 6-28 14-32M128 94c4-14 14-20 24-20" fill="none" strokeWidth="6" strokeLinecap="round" />
        <path d="M114 96h28l-14 92Z" fill="#f59758" strokeLinejoin="round" />
        <path d="M119 114h8M124 134h8M122 154h6" stroke="#e07b3c" strokeWidth="3" strokeLinecap="round" />
      </g>

      {/* Bread */}
      <g transform="rotate(-14 170 120)">
        <rect x="152" y="58" width="36" height="130" rx="18" fill="#e9b872" />
        <path d="M160 84l20-10M160 108l20-10M160 132l20-10" stroke="#c98d3f" strokeWidth="4" strokeLinecap="round" />
      </g>

      {/* Milk */}
      <rect x="204" y="92" width="38" height="92" rx="12" fill="#fff" />
      <rect x="211" y="78" width="24" height="18" rx="5" fill="#67bcee" />
      <rect x="204" y="122" width="38" height="24" fill="#e6f4fc" />
      <path d="M223 128c-4 5-6 8-6 10a6 6 0 0 0 12 0c0-2-2-5-6-10Z" fill="#67bcee" />

      {/* Greens */}
      <circle className="fill-brand-dark" cx="262" cy="134" r="28" />
      <circle className="fill-brand" cx="286" cy="114" r="22" />
      <circle cx="248" cy="110" r="20" fill="#5fcf98" />
      <circle className="fill-brand" cx="270" cy="94" r="16" />

      {/* Tomato and orange at the rim */}
      <circle cx="166" cy="150" r="20" fill="#ef5a4c" />
      <path className="stroke-brand" d="M157 132l9 6 9-6M166 138v-8" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" fill="none" />
      <circle cx="160" cy="144" r="5" fill="#fff" opacity="0.35" />
      <circle cx="226" cy="154" r="18" fill="#fdc040" />
      <circle cx="232" cy="147" r="4" fill="#fff" opacity="0.55" />

      {/* The basket */}
      <path className="fill-brand" d="M100 176h200l-19 104a12 12 0 0 1-12 10H131a12 12 0 0 1-12-10Z" />
      <path className="stroke-brand-dark" d="M108 212h184M114 248h172" strokeWidth="6" />
      <path className="stroke-brand-dark" d="M150 178l6 110M200 178v112M250 178l-6 110" strokeWidth="5" />
      <rect className="fill-brand-dark" x="88" y="162" width="224" height="24" rx="12" />
      {/* The shop's leaf mark on a label */}
      <circle cx="200" cy="230" r="24" fill="#fff" />
      <path className="fill-brand" d="M188 242c-3-14 6-26 26-27 1 18-9 27-26 27Z" />
      <path d="M190 240c5-7 10-12 17-16" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" fill="none" />
    </svg>
  )
}
