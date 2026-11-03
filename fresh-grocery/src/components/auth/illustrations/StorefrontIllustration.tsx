/** The awning: six stripes of two oranges, each ending in a scallop. */
const AWNING = ['#f59758', '#ffd9c2', '#f59758', '#ffd9c2', '#f59758', '#ffd9c2']
const STRIPE = 232 / AWNING.length
/** Fruit and vegetables on the window's two shelves (the green one in the theme's green; set with style={{ fill }}). */
const SHELF = ['#ef5a4c', '#fdc040', 'var(--color-brand)', '#f59758', '#ef5a4c']

/** A grocery store with a sales card: the vendor panel's login. Decoration only (hidden from screen readers). */
export function StorefrontIllustration({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 400 320" className={className} aria-hidden="true" focusable="false">
      {/* Backdrop and the shadow on the ground */}
      <circle cx="200" cy="160" r="140" className="fill-card" opacity="0.65" />
      <ellipse cx="200" cy="294" rx="150" ry="10" fill="#253d4e" opacity="0.08" />

      {/* The building and its sign */}
      <rect x="96" y="104" width="208" height="186" rx="6" fill="#fff" stroke="#f3d3bd" strokeWidth="3" />
      <rect x="84" y="62" width="232" height="46" rx="12" fill="#253d4e" />
      <circle className="fill-brand" cx="140" cy="85" r="13" />
      <path d="M133 92c-2-9 4-16 15-16 0 11-6 16-15 16Z" fill="#fff" />
      <text x="220" y="93" textAnchor="middle" fontFamily="Quicksand, sans-serif" fontSize="22" fontWeight="700" letterSpacing="3" fill="#fff">
        FRESH
      </text>

      {/* The awning */}
      {AWNING.map((color, index) => (
        <g key={index}>
          <rect x={84 + index * STRIPE} y="108" width={STRIPE} height="28" fill={color} />
          <circle cx={84 + index * STRIPE + STRIPE / 2} cy="136" r={STRIPE / 2} fill={color} />
        </g>
      ))}
      <rect x="80" y="104" width="240" height="8" rx="4" fill="#e07b3c" />

      {/* The window with its shelves */}
      <rect x="112" y="172" width="100" height="76" rx="8" fill="#e6f4fc" />
      <path d="M112 200h100M112 230h100" stroke="#b9dcf2" strokeWidth="4" />
      {SHELF.map((color, index) => (
        <circle key={`top-${index}`} cx={128 + index * 17} cy="191" r="7" style={{ fill: color }} />
      ))}
      {[...SHELF].reverse().map((color, index) => (
        <circle key={`bottom-${index}`} cx={128 + index * 17} cy="221" r="7" style={{ fill: color }} />
      ))}

      {/* The door with an "open" sign */}
      <rect className="fill-brand" x="230" y="166" width="58" height="124" rx="8" />
      <rect className="fill-brand-light" x="240" y="178" width="38" height="46" rx="5" />
      <path d="M249 196l10-9 10 9" stroke="#253d4e" strokeWidth="2" fill="none" strokeLinejoin="round" />
      <rect x="244" y="196" width="30" height="14" rx="3" fill="#fff" />
      <text className="fill-brand-dark" x="259" y="206.5" textAnchor="middle" fontFamily="Quicksand, sans-serif" fontSize="8" fontWeight="700">
        OPEN
      </text>
      <circle cx="244" cy="244" r="4" fill="#fff" />

      {/* A crate of tomatoes in front */}
      <circle cx="122" cy="258" r="11" fill="#ef5a4c" />
      <circle cx="144" cy="255" r="11" fill="#ef5a4c" />
      <circle cx="166" cy="258" r="11" fill="#f59758" />
      <rect x="104" y="258" width="80" height="32" rx="4" fill="#e9b872" />
      <path d="M104 274h80M124 258v32M164 258v32" stroke="#c98d3f" strokeWidth="3" />

      {/* A plant by the door */}
      <path className="fill-brand" d="M330 262c-14-10-16-30-8-42 12 8 14 28 8 42Z" />
      <path className="fill-brand-dark" d="M330 262c4-18 18-28 32-26-2 16-16 26-32 26Z" />
      <path d="M316 290l-6-30h40l-6 30Z" fill="#f59758" />

      {/* Floating: today's sales going up */}
      <rect x="296" y="28" width="92" height="66" rx="14" fill="#253d4e" opacity="0.08" />
      <rect x="290" y="20" width="92" height="66" rx="14" fill="#fff" />
      <rect className="fill-brand-soft" x="304" y="56" width="12" height="18" rx="3" />
      <rect className="fill-brand" x="322" y="46" width="12" height="28" rx="3" />
      <rect className="fill-brand-dark" x="340" y="34" width="12" height="40" rx="3" />
      <circle cx="366" cy="38" r="10" fill="#fdc040" />
      <path d="M366 43v-10M361 37l5-5 5 5" stroke="#253d4e" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />

      {/* Floating: a new order */}
      <rect x="22" y="158" width="80" height="44" rx="12" fill="#253d4e" opacity="0.08" />
      <rect x="16" y="150" width="80" height="44" rx="12" fill="#fff" />
      <circle className="fill-brand" cx="38" cy="172" r="11" />
      <path d="M33 172l4 4 7-8" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
      <rect x="55" y="164" width="30" height="6" rx="3" fill="#253d4e" opacity="0.7" />
      <rect x="55" y="176" width="20" height="6" rx="3" fill="#d5dbe5" />
    </svg>
  )
}
