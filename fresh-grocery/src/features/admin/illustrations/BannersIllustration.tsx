/** A browser window showing a home-page slide (sun, hills, a title) with its dots: the Banners page. Decoration only. */
export function BannersIllustration({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 240 180" className={className} aria-hidden="true" focusable="false">
      <circle cx="120" cy="92" r="82" fill="#fff" opacity="0.55" />

      {/* The window */}
      <rect x="34" y="40" width="180" height="120" rx="12" fill="#000" opacity="0.07" />
      <rect x="28" y="32" width="180" height="120" rx="12" fill="#fff" />
      <path d="M28 44a12 12 0 0 1 12-12h156a12 12 0 0 1 12 12v6H28Z" fill="#f4f6fa" />
      <circle cx="42" cy="41" r="3.5" className="fill-hot" />
      <circle cx="53" cy="41" r="3.5" className="fill-accent" />
      <circle cx="64" cy="41" r="3.5" className="fill-brand" />

      {/* The slide: a sun that drifts, two hills and the title */}
      <rect x="40" y="58" width="156" height="70" rx="8" className="fill-brand-light" />
      <g className="motion-safe:animate-float">
        <circle cx="166" cy="78" r="10" className="fill-accent" />
      </g>
      <path d="M40 128l40-34 30 22 24-18 42 30Z" className="fill-brand" />
      <path d="M40 128l40-34 22 18-26 16Z" className="fill-brand-dark" opacity="0.6" />
      <rect x="52" y="68" width="62" height="8" rx="4" fill="#253d4e" />
      <rect x="52" y="81" width="40" height="6" rx="3" fill="#fff" />

      {/* The slider's dots, the first one current */}
      <rect x="98" y="137" width="16" height="6" rx="3" className="fill-brand" />
      <circle cx="122" cy="140" r="3" fill="#d5dbe5" />
      <circle cx="132" cy="140" r="3" fill="#d5dbe5" />
      <path d="M206 20l3 7 7 3-7 3-3 7-3-7-7-3 7-3Z" className="fill-accent" />
    </svg>
  )
}
