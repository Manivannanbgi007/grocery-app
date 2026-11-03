import { CircleAlert } from 'lucide-react'

interface CheckoutProblemsProps {
  /** what the backend says must be fixed first, e.g. "Pomegranate is out of stock" */
  problems: string[]
  title?: string
}

/** The reasons why the cart cannot be ordered yet, as the backend found them: a pink box that shakes once when it appears. */
export function CheckoutProblems({ problems, title = 'Please fix this first:' }: CheckoutProblemsProps) {
  return (
    <div role="alert" className="flex gap-4 rounded-3xl border border-hot/40 bg-linear-to-br from-hot/10 to-card p-5 shadow-sm motion-safe:animate-shake">
      <span aria-hidden="true" className="grid size-10 shrink-0 place-items-center rounded-xl bg-hot text-white shadow-md shadow-hot/30">
        <CircleAlert className="size-5" />
      </span>
      <div>
        <p className="font-heading font-bold text-heading">{title}</p>
        <ul className="mt-1 list-disc space-y-1 pl-5 text-sm">
          {problems.map((problem) => (
            <li key={problem}>{problem}</li>
          ))}
        </ul>
      </div>
    </div>
  )
}
