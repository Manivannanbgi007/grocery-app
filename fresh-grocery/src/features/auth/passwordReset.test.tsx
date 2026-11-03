import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { allUsers, createResetToken } from '../../mocks/mockAuth'
import { renderApp } from '../../test/render'

describe('Forgot password', () => {
  it('is linked from the login page', async () => {
    const { user, router } = renderApp('/login')
    await user.click(await screen.findByRole('link', { name: 'Forgot your password?' }))
    expect(await screen.findByRole('heading', { name: 'Forgot your password?' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/forgot-password')
  })

  it('sends a code, and the new password then works for logging in', async () => {
    const { user } = renderApp('/forgot-password')
    await user.click(await screen.findByRole('button', { name: 'Send me a code' }))
    expect(screen.getByText('Please enter your email address.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Email'), 'customer1@grocery.com')
    await user.click(screen.getByRole('button', { name: 'Send me a code' }))
    expect(await screen.findByRole('heading', { name: 'Check your email' })).toBeInTheDocument()
    // While developing, the local backend returns the code too, and the link carries it to the next page
    expect(screen.getByText(/The local backend also returned the code/)).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Enter the code' }))
    expect(await screen.findByLabelText('Code from the email')).not.toHaveValue('')
    await user.type(screen.getByLabelText('New password'), 'BrandNew@789')
    await user.click(screen.getByRole('button', { name: 'Save new password' }))
    const done = await screen.findByRole('heading', { name: 'Your password is changed' })

    await user.click(within(done.parentElement!).getByRole('link', { name: 'Log in' }))
    await user.type(await screen.findByLabelText('Email'), 'customer1@grocery.com')
    await user.type(screen.getByLabelText('Password', { exact: true }), 'BrandNew@789')
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    expect(await screen.findByText('Welcome back, Priya!')).toBeInTheDocument()
  })

  it('answers the same for an email without an account (nobody can find out who is registered)', async () => {
    const { user } = renderApp('/forgot-password')
    await user.type(await screen.findByLabelText('Email'), 'nobody@example.com')
    await user.click(screen.getByRole('button', { name: 'Send me a code' }))
    expect(await screen.findByRole('heading', { name: 'Check your email' })).toBeInTheDocument()
    expect(screen.queryByText(/The local backend also returned the code/)).not.toBeInTheDocument()
  })

  it("accepts only the newest email's code, like auth-service", async () => {
    const older = createResetToken(allUsers().find((candidate) => candidate.email === 'customer1@grocery.com')!).resetToken
    const { user } = renderApp('/forgot-password')
    await user.type(await screen.findByLabelText('Email'), 'customer1@grocery.com')
    await user.click(screen.getByRole('button', { name: 'Send me a code' }))
    expect(await screen.findByRole('heading', { name: 'Check your email' })).toBeInTheDocument()

    // The code of the earlier email no longer works
    await user.click(screen.getByRole('link', { name: 'Enter the code' }))
    const code = await screen.findByLabelText('Code from the email')
    await user.clear(code)
    await user.type(code, older)
    await user.type(screen.getByLabelText('New password'), 'BrandNew@789')
    await user.click(screen.getByRole('button', { name: 'Save new password' }))
    expect(await screen.findByText('This code is wrong or has expired. Ask for a new one.')).toBeInTheDocument()
  })

  it('says when the code is wrong', async () => {
    const { user } = renderApp('/reset-password?code=not-a-real-code')
    await user.type(await screen.findByLabelText('New password'), 'BrandNew@789')
    await user.click(screen.getByRole('button', { name: 'Save new password' }))
    expect(await screen.findByText('This code is wrong or has expired. Ask for a new one.')).toBeInTheDocument()
  })
})
