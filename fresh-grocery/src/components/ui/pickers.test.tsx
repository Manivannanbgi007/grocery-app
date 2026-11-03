import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { pickDate } from '../../test/pickers'
import { Calendar } from './Calendar'
import { DateField } from './DateField'
import { DateTimeField } from './DateTimeField'
import { Select } from './Select'
import { TimeField } from './TimeField'

const FRUITS = [
  { value: 'apple', label: 'Apple' },
  { value: 'banana', label: 'Banana' },
  { value: 'blueberry', label: 'Blueberry', disabled: true },
  { value: 'cherry', label: 'Cherry' },
  { value: 'grape', label: 'Grape' },
]

function FruitSelect() {
  const [fruit, setFruit] = useState('')
  return <Select aria-label="Fruit" placeholder="Choose a fruit" value={fruit} onChange={setFruit} options={FRUITS} />
}

/** The text of the option that is highlighted (aria-activedescendant). */
function highlighted(combobox: HTMLElement): string | null {
  const id = combobox.getAttribute('aria-activedescendant')
  return id ? document.getElementById(id)!.textContent : null
}

describe('Select', () => {
  it('opens with a click, marks the chosen option, and closes on a click outside', async () => {
    const user = userEvent.setup()
    render(<FruitSelect />)
    const fruit = screen.getByRole('combobox', { name: 'Fruit' })
    expect(fruit).toHaveTextContent('Choose a fruit')

    await user.click(fruit)
    expect(fruit).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('option', { name: 'Blueberry' })).toHaveAttribute('aria-disabled', 'true')
    await user.click(screen.getByRole('option', { name: 'Banana' }))
    expect(fruit).toHaveTextContent('Banana')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()

    await user.click(fruit)
    expect(screen.getByRole('option', { name: 'Banana' })).toHaveAttribute('aria-selected', 'true')
    await user.click(document.body)
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })

  it('works with the keyboard like a native drop-down', async () => {
    const user = userEvent.setup()
    render(<FruitSelect />)
    const fruit = screen.getByRole('combobox', { name: 'Fruit' })
    await user.tab()
    expect(fruit).toHaveFocus()

    await user.keyboard('{ArrowDown}')
    expect(highlighted(fruit)).toBe('Apple')
    await user.keyboard('{ArrowDown}{ArrowDown}') // Blueberry cannot be chosen: skipped
    expect(highlighted(fruit)).toBe('Cherry')
    await user.keyboard('{End}')
    expect(highlighted(fruit)).toBe('Grape')
    await user.keyboard('b') // typing jumps to the first option with that letter
    expect(highlighted(fruit)).toBe('Banana')
    await user.keyboard('{Enter}')
    expect(fruit).toHaveTextContent('Banana')
    expect(fruit).toHaveFocus()

    await user.keyboard(' ')
    await user.keyboard('{ArrowDown}{Escape}') // Escape closes without choosing
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(fruit).toHaveTextContent('Banana')

    await user.keyboard('g') // typing on the closed field opens it on the match; Tab chooses it and moves on
    await user.tab()
    expect(fruit).toHaveTextContent('Grape')
    expect(fruit).not.toHaveFocus()
  })

  it('opens upwards when there is no room under the field', async () => {
    const user = userEvent.setup()
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({ top: 700, bottom: 740, left: 10, right: 200, width: 190, height: 40 } as DOMRect)
    vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockReturnValue(200)
    render(<FruitSelect />)
    await user.click(screen.getByRole('combobox', { name: 'Fruit' }))
    expect(screen.getByRole('listbox')).toHaveClass('bottom-full')
    vi.restoreAllMocks()
  })
})

describe('DateField', () => {
  // Today is 27 Oct 2026 (India) in these tests; only Date is faked, so user-event's timers still run
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-27T06:30:00Z'))
  })
  afterEach(() => vi.useRealTimers())

  function BirthDay({ initial }: { initial: string }) {
    const [day, setDay] = useState(initial)
    return <DateField label="Date of birth" value={day} onChange={setDay} max="2026-10-27" clearable />
  }

  it('opens on the chosen day, moves with the keyboard and chooses with Enter', async () => {
    const user = userEvent.setup()
    render(<BirthDay initial="1994-05-17" />)
    const field = screen.getByLabelText('Date of birth')
    expect(field).toHaveTextContent('17 May 1994')

    await user.click(field)
    const calendar = screen.getByRole('dialog', { name: 'Date of birth: choose a date' })
    expect(within(calendar).getByRole('button', { name: 'Tuesday, 17 May 1994' })).toHaveFocus()
    expect(within(calendar).getByRole('combobox', { name: 'Month' })).toHaveTextContent('May')
    expect(within(calendar).getByRole('combobox', { name: 'Year' })).toHaveTextContent('1994')

    await user.keyboard('{ArrowRight}{ArrowDown}') // a day, then a week
    expect(within(calendar).getByRole('button', { name: 'Wednesday, 25 May 1994' })).toHaveFocus()
    await user.keyboard('{PageDown}') // a month
    expect(within(calendar).getByRole('button', { name: 'Saturday, 25 June 1994' })).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(field).toHaveTextContent('25 Jun 1994')
    expect(field).toHaveFocus()
  })

  it('keeps to min and max, closes with Escape, and clears an optional date', async () => {
    const user = userEvent.setup()
    render(<BirthDay initial="2026-10-20" />)
    const field = screen.getByLabelText('Date of birth')

    await user.click(field)
    const calendar = screen.getByRole('dialog')
    expect(within(calendar).getByRole('button', { name: 'Wednesday, 28 October 2026' })).toBeDisabled()
    expect(within(calendar).getByRole('button', { name: 'Next month' })).toBeDisabled()
    expect(within(calendar).getByRole('button', { name: 'Tuesday, 27 October 2026' })).toHaveAttribute('aria-current', 'date')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(field).toHaveFocus()

    await pickDate(user, field, '1999-12-31') // the month and year drop-downs jump far back
    expect(field).toHaveTextContent('31 Dec 1999')
    await user.click(field)
    await user.click(screen.getByRole('button', { name: 'Clear' }))
    expect(field).toHaveTextContent('Choose a date')
  })
})

describe('Calendar with a range', () => {
  it('marks the first and last day and the days between them as chosen', () => {
    render(<Calendar value="2026-10-18" range={{ from: '2026-10-12', to: '2026-10-18' }} onChoose={vi.fn()} today="2026-10-27" />)
    const chosen = (date: number) => screen.getByRole('button', { name: new RegExp(`, ${date} October 2026$`) }).closest('td')
    expect(chosen(12)).toHaveAttribute('aria-selected', 'true')
    expect(chosen(15)).toHaveAttribute('aria-selected', 'true')
    expect(chosen(18)).toHaveAttribute('aria-selected', 'true')
    expect(chosen(11)).toHaveAttribute('aria-selected', 'false')
    expect(chosen(19)).toHaveAttribute('aria-selected', 'false')
  })
})

describe('TimeField and DateTimeField', () => {
  it('offers the times every 15 minutes, keeps a saved time between them, and jumps when typing', async () => {
    const user = userEvent.setup()
    function Opens() {
      const [time, setTime] = useState('07:10')
      return <TimeField label="Opens at" value={time} onChange={setTime} />
    }
    render(<Opens />)
    const field = screen.getByLabelText('Opens at')
    expect(field).toHaveTextContent('7:10 am')

    await user.click(field)
    const times = screen.getAllByRole('option').map((option) => option.textContent)
    expect(times).toHaveLength(24 * 4 + 1)
    expect(times.slice(0, 2)).toEqual(['12:00 am', '12:15 am'])
    expect(times).toContain('7:10 am')
    await user.keyboard('9{Enter}')
    expect(field).toHaveTextContent('9:00 am')
  })

  it('chooses the time once there is a day, and clearing the day empties both', async () => {
    const user = userEvent.setup()
    const changes: string[] = []
    function ShownFrom() {
      const [value, setValue] = useState('')
      return (
        <DateTimeField
          label="Shown from"
          value={value}
          onChange={(next) => {
            changes.push(next)
            setValue(next)
          }}
        />
      )
    }
    render(<ShownFrom />)
    const time = screen.getByLabelText('Shown from, time')
    expect(time).toBeDisabled()

    await pickDate(user, screen.getByLabelText('Shown from, day'), '2026-12-10')
    expect(time).toHaveTextContent('12:00 am')
    await user.click(time)
    await user.click(screen.getByRole('option', { name: '9:30 am' }))
    await user.click(screen.getByLabelText('Shown from, day'))
    await user.click(screen.getByRole('button', { name: 'Clear' }))
    expect(changes).toEqual(['2026-12-10T00:00', '2026-12-10T09:30', ''])
    expect(time).toBeDisabled()
  })
})
