import { useId, type CSSProperties } from 'react'
import { cn } from '../../../utils/cn'

/** The heights of the chart's columns. */
const BARS = [30, 46, 38, 60, 52, 72, 64]
/** The donut's circumference (radius 18). */
const RING = 2 * Math.PI * 18

interface DashboardIllustrationProps {
  className?: string
  /** true (the dashboard's banner): the columns grow one by one and the floating parts drift (motion-safe only) */
  animated?: boolean
}

/**
 * A dashboard window with a shield and the team: the admin panel's login and the dashboard's welcome banner.
 * Drawn for a dark background. Decoration only (hidden from screen readers).
 */
export function DashboardIllustration({ className, animated = false }: DashboardIllustrationProps) {
  // The window's rounded corners cut off its insides; the id is unique per drawing on the page
  const clipId = useId()
  // A floating part drifts up and down; each starts at another moment, so they never move together
  const drift = (delay: string): { className?: string; style?: CSSProperties } =>
    animated ? { className: 'motion-safe:animate-float', style: { animationDelay: delay } } : {}

  return (
    <svg viewBox="0 0 400 320" className={className} aria-hidden="true" focusable="false">
      <circle cx="200" cy="160" r="140" fill="#fff" opacity="0.06" />

      {/* The window */}
      <rect x="60" y="58" width="284" height="204" rx="16" fill="#000" opacity="0.2" />
      <rect x="50" y="46" width="284" height="204" rx="16" fill="#fff" />
      <clipPath id={clipId}>
        <rect x="50" y="46" width="284" height="204" rx="16" />
      </clipPath>
      <g clipPath={`url(#${clipId})`}>
        <rect x="50" y="46" width="284" height="28" fill="#f4f6fa" />
        <circle cx="68" cy="60" r="4.5" fill="#f74b81" />
        <circle cx="82" cy="60" r="4.5" fill="#fdc040" />
        <circle className="fill-brand" cx="96" cy="60" r="4.5" />

        {/* Its dark menu */}
        <rect x="50" y="74" width="62" height="176" fill="#16252f" />
        <rect className="fill-brand" x="62" y="90" width="38" height="8" rx="4" />
        {[108, 126, 144, 162].map((y) => (
          <rect key={y} x="62" y={y} width="38" height="8" rx="4" fill="#2a3d4a" />
        ))}

        {/* Three numbers, with a dot in the theme's green, yellow and blue */}
        {[
          { x: 124, dot: 'fill-brand' },
          { x: 194, dot: 'fill-accent' },
          { x: 264, dot: 'fill-sale' },
        ].map(({ x, dot }) => (
          <g key={x}>
            <rect x={x} y="86" width="60" height="40" rx="8" fill="#f4f6fa" />
            <rect x={x + 8} y="96" width="26" height="6" rx="3" fill="#d5dbe5" />
            <rect x={x + 8} y="108" width="36" height="9" rx="4" fill="#253d4e" />
            <circle cx={x + 48} cy="99" r="5" className={dot} />
          </g>
        ))}

        {/* A column chart */}
        <rect x="124" y="136" width="130" height="100" rx="8" fill="#f4f6fa" />
        {BARS.map((height, index) => (
          <rect
            key={index}
            x={134 + index * 16}
            y={226 - height}
            width="9"
            height={height}
            rx="2"
            // transform-box: the column grows from its own bottom edge, not from the drawing's
            className={cn(index === BARS.length - 2 ? 'fill-brand-dark' : 'fill-brand', animated && 'origin-bottom [transform-box:fill-box] motion-safe:animate-grow-up')}
            style={animated ? { animationDelay: `${300 + index * 90}ms` } : undefined}
          />
        ))}

        {/* A donut */}
        <rect x="264" y="136" width="60" height="100" rx="8" fill="#f4f6fa" />
        <circle cx="294" cy="176" r="18" fill="none" stroke="#e7eaf3" strokeWidth="9" />
        <circle className="stroke-brand" cx="294" cy="176" r="18" fill="none" strokeWidth="9" strokeDasharray={`${RING * 0.62} ${RING}`} transform="rotate(-90 294 176)" />
        <circle
          cx="294"
          cy="176"
          r="18"
          fill="none"
          stroke="#fdc040"
          strokeWidth="9"
          strokeDasharray={`${RING * 0.2} ${RING}`}
          strokeDashoffset={-RING * 0.64}
          transform="rotate(-90 294 176)"
        />
        <rect x="274" y="206" width="40" height="6" rx="3" fill="#d5dbe5" />
        <rect x="274" y="218" width="28" height="6" rx="3" fill="#e7eaf3" />
      </g>

      {/* Floating: a shield (approvals, safety) */}
      <g {...drift('-1s')}>
        <path className="fill-brand" d="M66 204l28 10v22c0 20-13 32-28 38-15-6-28-18-28-38v-22Z" />
        <path d="M53 238l9 9 17-18" stroke="#fff" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
      </g>

      {/* Floating: a notification */}
      <g {...drift('-4s')}>
        <rect x="296" y="26" width="96" height="46" rx="12" fill="#000" opacity="0.2" />
        <rect x="290" y="18" width="96" height="46" rx="12" fill="#fff" />
        <circle className="fill-brand-light" cx="310" cy="41" r="11" />
        <path className="stroke-brand-dark" d="M305 41l4 4 7-8" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
        <rect x="327" y="33" width="46" height="6" rx="3" fill="#253d4e" />
        <rect x="327" y="45" width="30" height="6" rx="3" fill="#d5dbe5" />
      </g>

      {/* Floating: the team */}
      <g {...drift('-6s')}>
        {['#67bcee', '#f59758', '#f74b81'].map((color, index) => (
          <g key={color}>
            <circle cx={312 + index * 24} cy="270" r="16" fill={color} stroke="#16252f" strokeWidth="3" />
            <circle cx={312 + index * 24} cy="265" r="5" fill="#fff" opacity="0.9" />
            <path d={`M${303 + index * 24} 279a9 7 0 0 1 18 0Z`} fill="#fff" opacity="0.9" />
          </g>
        ))}
      </g>
    </svg>
  )
}
