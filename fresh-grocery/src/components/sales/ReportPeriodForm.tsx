import { useState, type FormEvent, type ReactNode } from 'react'
import { indiaDay } from '../../utils/dates'
import { Button } from '../ui/Button'
import { DateField } from '../ui/DateField'
import { periodProblem } from './reportPeriod'

interface ReportPeriodFormProps {
  from: string
  to: string
  /** called with a valid period, e.g. to put it into the address */
  onShow: (from: string, to: string) => void
  /** more filters on the same row, e.g. a store drop-down */
  children?: ReactNode
}

/** "From [date] To [date] Show": a report period, checked before it is shown. */
export function ReportPeriodForm({ from, to, onShow, children }: ReportPeriodFormProps) {
  const today = indiaDay()
  const [fromBox, setFromBox] = useState(from)
  const [toBox, setToBox] = useState(to)
  const [problem, setProblem] = useState<string | null>(null)

  function show(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = periodProblem(fromBox, toBox)
    setProblem(found)
    if (!found) onShow(fromBox, toBox)
  }

  return (
    <form onSubmit={show} noValidate className="mb-6 flex flex-wrap items-end gap-3">
      <DateField label="From" size="sm" value={fromBox} max={today} onChange={setFromBox} className="w-44" />
      <DateField label="To" size="sm" value={toBox} max={today} onChange={setToBox} className="w-44" />
      <Button type="submit" variant="light">
        Show
      </Button>
      {children}
      {problem && (
        <p role="alert" className="w-full text-sm text-hot">
          {problem}
        </p>
      )}
    </form>
  )
}
