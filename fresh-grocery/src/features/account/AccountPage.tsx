import { KeyRound, UserRound } from 'lucide-react'
import { Appear } from '../../components/ui/Appear'
import { ErrorState } from '../../components/ui/ErrorState'
import { Skeleton } from '../../components/ui/Skeleton'
import { ToneBar, ToneIcon } from '../../components/ui/ToneIcon'
import { toneCard } from '../../components/ui/tones'
import { RequireCustomer } from '../auth/RequireCustomer'
import { AccountLayout } from './AccountLayout'
import { ChangePasswordForm } from './ChangePasswordForm'
import { ProfileForm } from './ProfileForm'
import { useProfile } from './useAccount'

/** "My account": the profile and the password. Only for a logged-in customer. */
export function AccountPage() {
  return (
    <RequireCustomer>
      <AccountLayout title="My account" crumbs={[{ label: 'My account' }]}>
        <AccountContent />
      </AccountLayout>
    </RequireCustomer>
  )
}

function AccountContent() {
  const { data: profile, isPending, isError, error, refetch } = useProfile()

  let profileContent
  if (isPending) {
    profileContent = <Skeleton className="h-72" />
  } else if (isError) {
    // Right after registering the profile may not exist yet: the backend says so, and "Try again" helps
    profileContent = <ErrorState compact title="Could not load your profile" error={error} onRetry={() => void refetch()} />
  } else {
    profileContent = <ProfileForm key={profile.id} profile={profile} />
  }

  return (
    // Two coloured cards that rise in one after the other: the profile violet (the account), the password yellow (look here)
    <div className="space-y-8">
      <Appear>
        <section aria-labelledby="profile-title" className={toneCard('violet', 'p-6')}>
          <ToneBar tone="violet" />
          <h2 id="profile-title" className="mb-5 flex items-center gap-3 text-xl">
            <ToneIcon icon={UserRound} tone="violet" solid />
            Profile
          </h2>
          {profileContent}
        </section>
      </Appear>
      <Appear order={1}>
        <section aria-labelledby="password-title" className={toneCard('yellow', 'p-6')}>
          <ToneBar tone="yellow" />
          <h2 id="password-title" className="mb-5 flex items-center gap-3 text-xl">
            <ToneIcon icon={KeyRound} tone="yellow" solid />
            Change password
          </h2>
          <ChangePasswordForm />
        </section>
      </Appear>
    </div>
  )
}
