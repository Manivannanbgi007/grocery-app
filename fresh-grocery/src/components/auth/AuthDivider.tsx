/** A line with words in the middle, e.g. "New to Fresh Grocery?" above the sign-up button. The lines fade out at the ends. */
export function AuthDivider({ label }: { label: string }) {
  return (
    <div className="my-6 flex items-center gap-3 text-sm">
      <span className="h-px flex-1 bg-linear-to-r from-transparent to-line" aria-hidden="true" />
      <span className="shrink-0">{label}</span>
      <span className="h-px flex-1 bg-linear-to-l from-transparent to-line" aria-hidden="true" />
    </div>
  )
}
