import { Circle, CircleCheck } from 'lucide-react'
import { cn } from '../../utils/cn'
import { PASSWORD_RULES } from './validation'

/**
 * The password rules under a new password box, each one ticked off as soon as the password meets it. Used as the box's
 * hint, so screen readers read the rules when the box gets the focus.
 */
export function PasswordRules({ password }: { password: string }) {
  return (
    <ul className="mt-1 flex flex-wrap gap-x-4 gap-y-1">
      {PASSWORD_RULES.map((rule) => {
        const met = rule.met(password)
        return (
          <li key={rule.label} className={cn('flex items-center gap-1.5 transition-colors', met ? 'font-bold text-brand-text' : 'text-body')}>
            {met ? <CircleCheck className="size-4" aria-hidden="true" /> : <Circle className="size-4" aria-hidden="true" />}
            {rule.label}
            {met && <span className="sr-only">: done</span>}
          </li>
        )
      })}
    </ul>
  )
}
