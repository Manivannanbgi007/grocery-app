import { QueryClient, QueryClientProvider, useMutation, useQuery } from '@tanstack/react-query'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement } from 'react'
import { createMemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { LoadingIndicator } from '../layout/LoadingIndicator'
import { LoadingState } from './LoadingState'

/** The app's "Loading…" sign next to `ui`, with a fresh cache. */
function renderWithIndicator(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: '/', element: null }])
  render(
    <QueryClientProvider client={queryClient}>
      <LoadingIndicator router={router} />
      {ui}
    </QueryClientProvider>,
  )
  return { queryClient, sign: screen.getByRole('status') }
}

/** A request that only answers when the test says so. */
function controlledRequest() {
  let answer: (value: string) => void = () => {}
  const promise = new Promise<string>((resolve) => {
    answer = resolve
  })
  return { promise, answer: (value: string) => answer(value) }
}

const longerThanTheDelay = () => new Promise((resolve) => setTimeout(resolve, 700))

describe('Loading states', () => {
  it('shows the spinner and what is loading in a section', () => {
    render(<LoadingState label="Loading your deliveries…" />)
    expect(screen.getByRole('status')).toHaveTextContent('Loading your deliveries…')
  })

  it('shows "Loading…" at the top while data that is not on screen yet takes longer than 0.4 s', async () => {
    const request = controlledRequest()
    function SlowList() {
      const { data } = useQuery({ queryKey: ['slow'], queryFn: () => request.promise })
      return <p>{data ?? 'Not there yet'}</p>
    }
    const { sign } = renderWithIndicator(<SlowList />)

    expect(sign).toBeEmptyDOMElement()
    expect(await within(sign).findByText('Loading…', {}, { timeout: 2000 })).toBeInTheDocument()
    act(() => request.answer('The list'))
    expect(await screen.findByText('The list')).toBeInTheDocument()
    await waitFor(() => expect(sign).toBeEmptyDOMElement())
  })

  it('also shows it while a change is being saved', async () => {
    const request = controlledRequest()
    function SaveButton() {
      const save = useMutation({ mutationFn: () => request.promise })
      return (
        <button type="button" onClick={() => save.mutate()}>
          Save
        </button>
      )
    }
    const { sign } = renderWithIndicator(<SaveButton />)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Save' }))
    expect(await within(sign).findByText('Loading…', {}, { timeout: 2000 })).toBeInTheDocument()
    act(() => request.answer('saved'))
    await waitFor(() => expect(sign).toBeEmptyDOMElement())
  })

  it('stays away for background refreshes of data on screen and for requests that show their own progress', async () => {
    const never = () => new Promise<string>(() => {})
    function Background() {
      // On screen already, refreshed in the background (like polling)
      const known = useQuery({ queryKey: ['known'], queryFn: never, staleTime: 0 })
      // Shows its own progress (like the chat)
      const quiet = useQuery({ queryKey: ['quiet'], queryFn: never, meta: { quiet: true } })
      return (
        <p>
          {known.data} {quiet.isPending ? 'Looking it up…' : ''}
        </p>
      )
    }
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    queryClient.setQueryData(['known'], 'Shown before')
    const router = createMemoryRouter([{ path: '/', element: null }])
    render(
      <QueryClientProvider client={queryClient}>
        <LoadingIndicator router={router} />
        <Background />
      </QueryClientProvider>,
    )

    expect(await screen.findByText(/Shown before/)).toBeInTheDocument()
    expect(queryClient.isFetching()).toBe(2)
    await longerThanTheDelay()
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })
})
