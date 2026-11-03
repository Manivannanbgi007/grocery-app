import { screen, within } from '@testing-library/react'
import type { UserEvent } from '@testing-library/user-event'
import { MONTH_NAMES, partsOf } from '../utils/calendar'

/*
 * Using our drop-downs and pickers in tests, the way a user does: open, then click. (They are not native <select> or
 * <input type="date"> elements, so user.selectOptions and typing a date do not work on them.)
 */

/** Chooses an option of a drop-down (components/ui/Select), e.g. chooseOption(user, getByLabelText('Zone'), 'Indiranagar, Bengaluru'). */
export async function chooseOption(user: UserEvent, field: HTMLElement, option: string | RegExp) {
  await user.click(field)
  await user.click(screen.getByRole('option', { name: option }))
}

/** Chooses a day (YYYY-MM-DD) in a date field (components/ui/DateField): opens the calendar, goes to its month and clicks it. */
export async function pickDate(user: UserEvent, field: HTMLElement, day: string) {
  await user.click(field)
  await clickDay(user, screen.getByRole('dialog', { name: /choose a date/ }), day)
}

/** Clicks a day (YYYY-MM-DD) in an open calendar (components/ui/Calendar), after going to its month and year. */
export async function clickDay(user: UserEvent, calendar: HTMLElement, day: string) {
  const { year, month, day: date } = partsOf(day)
  await chooseOption(user, within(calendar).getByRole('combobox', { name: 'Year' }), String(year))
  await chooseOption(user, within(calendar).getByRole('combobox', { name: 'Month' }), MONTH_NAMES[month])
  await user.click(within(calendar).getByRole('button', { name: new RegExp(`, ${date} ${MONTH_NAMES[month]} ${year}$`) }))
}
