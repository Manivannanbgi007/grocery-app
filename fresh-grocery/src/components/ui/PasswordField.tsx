import { Eye, EyeOff, type LucideIcon } from 'lucide-react'
import { useState, type InputHTMLAttributes, type ReactNode } from 'react'
import { TextField } from './TextField'

interface PasswordFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string
  error?: string
  hint?: ReactNode
  /** a small icon inside the box on the left, e.g. a padlock */
  icon?: LucideIcon
}

/** A password box with a "Show / Hide" button, so people can check what they typed. */
export function PasswordField(props: PasswordFieldProps) {
  const [visible, setVisible] = useState(false)
  return (
    <TextField
      {...props}
      type={visible ? 'text' : 'password'}
      trailing={
        <button
          type="button"
          onClick={() => setVisible((shown) => !shown)}
          className="mr-2 rounded-md p-2 text-body hover:text-heading"
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
        >
          {visible ? <EyeOff className="size-5" aria-hidden="true" /> : <Eye className="size-5" aria-hidden="true" />}
        </button>
      }
    />
  )
}
