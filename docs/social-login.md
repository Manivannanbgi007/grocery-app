# Social login: Google, Microsoft, Facebook and Apple

"Continue with Google" (Microsoft, Facebook, Apple) next to the email and password login. The person logs in at the
provider; the shop then gives them **its own** login, exactly as after `POST /api/v1/auth/login`: a 15-minute access
token in memory and the rotating refresh cookie ([authentication.md](authentication.md)). The provider's tokens are
never stored and never reach the React app.

| Provider | Protocol | Who the person is | Email counts as verified (auto-link) |
|---|---|---|---|
| Google | OpenID Connect | the ID token's `sub` | `email_verified` and a `@gmail.com` address or a Workspace account (`hd`) |
| Microsoft | OpenID Connect | the ID token's `sub` | only with the optional claim `xms_edov` |
| Facebook | OAuth 2.0 + Graph API | the Graph API's `id` | never |
| Apple | OpenID Connect (form post) | the ID token's `sub` | `email_verified` (also the relay address of "Hide My Email") |
| Test provider (local fake, `mock-oauth2-server`) | OpenID Connect | `sub` | `email_verified` as typed in; local development and tests only |

## Switching it on

Social login is **off** until you switch it on, in auth-service's environment:

```powershell
$env:SOCIAL_LOGIN_ENABLED = 'true'
```

| Setting | Default | Effect |
|---|---|---|
| `SOCIAL_LOGIN_ENABLED` | `false` | The main switch. Off: no provider is offered, the login and register pages show no social buttons, and the social endpoints answer 404. The password login works as always |
| `<PROVIDER>_CLIENT_ID` (and its secret or key) | empty | On: a provider is offered (a button) when it has its credentials ([Set up a provider](#set-up-a-provider)) |
| `GOOGLE_LOGIN_ENABLED`, `MICROSOFT_LOGIN_ENABLED`, `FACEBOOK_LOGIN_ENABLED`, `APPLE_LOGIN_ENABLED` | `true` | `false` switches one provider off and keeps its credentials (for example while that provider has an outage) |

The React app has no switch of its own: it asks auth-service (`GET /api/v1/auth/social/providers`) and shows a button
for each provider in the answer, so the browser always shows exactly what the backend offers. At start-up, auth-service
logs what it offers: `Social login is switched on with: google, facebook`, or a warning when it is switched on but no
provider has a client id, or `Social login is switched off`.

## How it works

```text
React app                                     api-gateway ─► auth-service (Spring Security)        Google
─────────                                     ────────────────────────────────────────────        ──────
"Continue with Google" (a plain link: a full page visit)
GET /api/v1/auth/oauth2/authorization/google ──────────────► state + nonce + PKCE, saved in
                                                             social_login_flows; cookie social_flow
                     ◄── 302 to accounts.google.com ─────────
the person logs in and agrees ──────────────────────────────────────────────────────────────────► Google
GET /api/v1/auth/login/oauth2/code/google?code=...&state=... ◄────────────────────────────────────
                                                             checks state, swaps the code for the
                                                             ID token, checks its signature, issuer,
                                                             audience, expiry and nonce
                     ◄── 302 to /login/social#code=<one-time code, 60 s> (no token, no cookie yet)
/login/social: POST /api/v1/auth/social/session {code} ─────► the account rules (below)
  (same origin, sends the social_flow cookie)
                     ◄── LOGGED_IN: { accessToken, user } + refresh cookie (like POST /login)
                      or SIGNUP_REQUIRED  ──► POST /api/v1/auth/social/signup {fullName, email?, phone, role}
                      or PASSWORD_REQUIRED ──► POST /api/v1/auth/social/link {password}
```

Step by step:

1. **The button** is a link, not a `fetch`: the browser itself must go to Google. Before leaving, the app remembers
   the panel (shop, vendor, delivery), the page to open afterwards and the account type of a sign-up, in
   `sessionStorage` (`features/auth/socialLoginIntent.ts`). The backend never sees them.
2. **auth-service starts the attempt** (Spring Security's `oauth2Login`): a random `state`, a `nonce` for the ID token
   and a PKCE code verifier. It stores them in the table `social_login_flows` (as JSON), sets the cookie `social_flow`
   (HttpOnly, SameSite=Strict, only sent to `/api/v1/auth/social`) and redirects to Google.
3. **Google sends the browser back** to the callback. Spring Security checks the `state`, exchanges Google's code for
   the tokens (with the client secret and the PKCE verifier), and validates the ID token. Only the ID token is read: the
   person's id (`sub`), email, whether Google verified it, and the name. The access token is thrown away.
4. **auth-service redirects to `/login/social#code=...`**: a random one-time code, valid 60 seconds, stored as a
   SHA-256 hash. It is in the URL **fragment** (`#`), which browsers never send to a server or in a `Referer`; the page
   removes it from the address bar at once.
5. **The page exchanges the code** (`POST /social/session`). The exchange only works once, before the code expires, and
   only in the browser that started the attempt (the `social_flow` cookie). Then the account rules decide.

## Account rules

`SocialLoginServiceImpl` in auth-service:

The rules are the same for every provider; "Google" below stands for any of them.

1. **This Google account is connected already** (`social_logins`, by provider and Google's `sub`): logged in.
2. **No account with this email:** the sign-up form. Name and email come from Google, and the email is locked when
   Google verified it (the table above; Facebook's never is, and a missing email is typed in). The person adds the mobile number (required everywhere in the shop). The account type comes from
   the page: a customer from the shop, a vendor or a delivery partner from those panels or the register page. The
   sign-up runs the normal registration (`AuthService.register`: the same validation, the `UserRegistered` event, the
   welcome email), with a random password nobody knows. To use a password as well, the person uses "Forgot password".
3. **An account with the same email exists:**
   - It is connected **automatically** only when **both** sides verified the email:
     - The provider's side: the last column of the table above. For Google, `email_verified` for a `@gmail.com`
       address or a Google Workspace account (`hd` claim), as Google advises.
     - Our side: `users.email_verified_at`, set by a completed password reset or an earlier social login with that
       verified email.

     Why both: otherwise someone could register your email with us first, without owning it, and your later Google
     login would sign you into their account (pre-account takeover).
   - Otherwise the person **enters that account's password once** (`POST /social/link`); after that Google logs them in
     directly. It is the same check and message as the login page ("Invalid email or password") and the same rate
     limit.
   - Every new connection sends the email "Google was connected to your account" (the `SocialLoginLinked` event,
     notification-service), with a hint to reset the password if it was not them.
4. **Never with social login:** admin and super admin accounts (they keep the password only, and the admin login page has
   no buttons), and disabled accounts.
5. **One Google account per user:** an account already connected to another Google account cannot get a second one.

### What the person can see

| Case | Where | Message |
|---|---|---|
| Cancelled on Google's page | `/login/social#error=cancelled` | "You cancelled the login. You can try again, or log in with your email and password." |
| Google's answer was invalid (state, signature, nonce), or Google failed | `#error=failed` | "The login with your account provider did not work. Please try again." |
| The code expired, was used already, or comes from another browser | 400 | "This sign-in has expired or was already used. Please try again." |
| Admin account | 403 | "Admin accounts cannot use social login. Please log in with your password." |
| Disabled account | 403 | "Your account is disabled. Please contact support." |
| Already connected to another Google account | 409 | "This account is already connected to another Google account. Please log in with that one, or with your password." |
| Wrong password to connect | 401 | "Invalid email or password" |
| No email from the provider | sign-up form | the email field is empty and editable |
| Email or mobile number already registered at sign-up | 409 | next to the field, as on the register page |
| The account belongs to another panel (a vendor on the shop's page) | page | "This is a vendor account. Vendors log in on the vendor panel." + a link (the session the server started is ended again) |

The URL only ever carries the fixed codes `cancelled` and `failed`, never a provider's text.

## Set up a provider

Every provider sends the browser back to **one exact address**, which you register in its console:

```text
{APP_PUBLIC_URL}/api/v1/auth/login/oauth2/code/{provider}
```

`APP_PUBLIC_URL` is the address people open the shop at, and `/api` on that address must reach the gateway. Locally,
the Vite dev server forwards `/api` to the gateway; in production, your reverse proxy or ingress does.

| | Local (`npm run dev`) | Production (example) |
|---|---|---|
| Google | `http://localhost:5173/api/v1/auth/login/oauth2/code/google` | `https://shop.example.com/api/v1/auth/login/oauth2/code/google` |
| Microsoft | `http://localhost:5173/api/v1/auth/login/oauth2/code/microsoft` | `https://shop.example.com/api/v1/auth/login/oauth2/code/microsoft` |
| Facebook | none to add: Facebook allows `http://localhost` by itself in development mode | `https://shop.example.com/api/v1/auth/login/oauth2/code/facebook` |
| Apple | not possible: Apple needs HTTPS and a real domain (use a tunnel, see Apple) | `https://shop.example.com/api/v1/auth/login/oauth2/code/apple` |

The address must match **exactly** (scheme, host, port, path, no trailing slash), or the provider shows a
"redirect_uri mismatch" error. The Vite server must run on port 5173 for the local address (`npm run dev` uses it unless
it is busy).

### Google

1. Open the [Google Cloud console](https://console.cloud.google.com/) and create a project (or pick one).
2. **Google Auth Platform → Branding:** the app name ("Fresh Grocery"), a support email, and for production the home
   page, privacy policy and terms links, and your domain under *Authorized domains*.
3. **Audience:** user type **External**. While the app is in *Testing*, only the Google accounts listed under *Test
   users* can log in (add your own). For everyone, **Publish app**: with only the basic scopes below, Google does not
   review the app (it may still ask to verify the branding).
4. **Data access:** the scopes `openid`, `.../auth/userinfo.email` and `.../auth/userinfo.profile`. Nothing else is
   needed.
5. **Clients → Create client:**
   - application type **Web application**;
   - **Authorized redirect URIs:** both addresses from the table above (local and production);
   - *Authorized JavaScript origins:* leave empty. The browser never talks to Google from JavaScript.
6. Copy the **Client ID** and the **Client secret**. Copy the secret right away (or download the JSON): the console does
   not show it again later.
7. Give them to auth-service as environment variables, then restart it:

   ```powershell
   $env:GOOGLE_CLIENT_ID = '1234567890-abc.apps.googleusercontent.com'
   $env:GOOGLE_CLIENT_SECRET = 'GOCSPX-...'
   $env:SOCIAL_LOGIN_ENABLED = 'true'
   cd auth-service; .\mvnw spring-boot:run
   ```

   The login pages now show "Continue with Google" (`GET /api/v1/auth/social/providers` lists it).

### Microsoft

1. Open the [Microsoft Entra admin center](https://entra.microsoft.com/) → **App registrations → New registration**.
2. **Supported account types:**
   - *Accounts in any organizational directory and personal Microsoft accounts*: everyone (tenant `common`, our
     default);
   - *Personal Microsoft accounts only*: tenant `consumers`;
   - one organization: its tenant id.
3. **Redirect URI:** platform **Web**, the local address from the table. Add the production one later under
   **Authentication**.
4. From **Overview**, copy the **Application (client) ID**.
5. **Certificates & secrets → New client secret:** pick an expiry, then copy its **Value** (not the *Secret ID*). It is
   shown only once. Write the expiry date down: the login stops working when the secret expires (24 months at most).
6. **Token configuration → Add optional claim → ID:** `email` and `xms_edov`. `xms_edov` says whether the email's
   domain is verified; without it, auto-linking stays off for Microsoft and the password is asked once instead.
7. **API permissions:** Microsoft Graph, delegated: `openid`, `profile`, `email` (the default `User.Read` can stay).
8. Environment: `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`, `MICROSOFT_TENANT` (default `common`).

The backend identifies a Microsoft account by its `sub`, never by the email or `preferred_username`: those can be
changed by the account's organization. Microsoft's ID tokens name the person's own directory in the issuer
(`https://login.microsoftonline.com/{tid}/v2.0`), so there is no fixed issuer to compare: `MicrosoftIssuerValidator`
checks that the issuer matches the token's `tid`, and that the `tid` is one the tenant setting allows (`consumers`:
only the personal-accounts directory; `organizations`: every other; a tenant id: that one).

### Facebook

1. Open [Meta for Developers](https://developers.facebook.com/apps) → **Create app**. Use case: *Authenticate and
   request data from users with Facebook Login*.
2. **App settings → Basic:** copy the **App ID** and the **App secret**. For Live mode, also fill in the *Privacy Policy
   URL*, *User data deletion* (instructions URL), the category and the icon.
3. **Use cases → Facebook Login → Settings:**
   - *Client OAuth login* and *Web OAuth login*: on;
   - *Enforce HTTPS*: on;
   - **Valid OAuth Redirect URIs:** the production address. `http://localhost` addresses are allowed automatically
     while the app is in development mode.
4. **Permissions:** `public_profile` and `email`.
   **App settings → Advanced → Require App Secret:** on. Then Facebook answers API calls only with the
   `appsecret_proof` that auth-service adds (an HMAC of the access token with the app secret).
5. While the app is in **development mode**, only people with a role on the app (admins, developers, testers) can log
   in. Switch it to **Live** for everyone; for `email` and `public_profile`, Meta grants the access without an App
   Review.
6. Environment: `FACEBOOK_CLIENT_ID` (the App ID), `FACEBOOK_CLIENT_SECRET` (the App secret), and optionally
   `FACEBOOK_GRAPH_VERSION` (default `v23.0`; Meta supports each version for about two years, so move it forward when
   the dashboard warns).

Facebook is plain OAuth 2.0: no ID token. After the login, auth-service asks the Graph API once who it is
(`GET /me?fields=id,name,email`, with the access token and its `appsecret_proof`), and then forgets the token.
Facebook does not say whether an email is verified, and some accounts have none (phone-only, or the person declined
it). So Facebook never auto-links: the sign-up form asks for a missing email (and lets the person change the one
Facebook gave), and an existing account needs its password once.

### Apple

Apple needs a paid Apple Developer Program membership, and **HTTPS on a real domain**: no `localhost`, no IP address,
no `http`. To try it from your PC, run a tunnel (for example `ngrok http 5173`), and use its `https://...` address as
`APP_PUBLIC_URL` and in Apple's settings.

1. [Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/identifiers/list) →
   **Identifiers → + → App IDs → App:** a description, a bundle id (e.g. `com.example.freshgrocery`), and the
   capability **Sign in with Apple**.
2. **Identifiers → + → Services IDs:** a description and an identifier (e.g. `com.example.freshgrocery.web`). **This
   is the client id.** Open it, tick **Sign in with Apple → Configure**:
   - *Primary App ID*: the one from step 1;
   - *Domains and Subdomains*: `shop.example.com`;
   - *Return URLs*: `https://shop.example.com/api/v1/auth/login/oauth2/code/apple`.
3. **Keys → +:** a name, **Sign in with Apple → Configure** (the primary App ID), **Register**. **Download the `.p8`
   file**: Apple lets you download it only once. Copy the **Key ID**.
4. Your **Team ID** is shown under *Membership details* of the account.
5. **Hidden emails:** people may hide their email; then Apple gives an address `...@privaterelay.appleid.com` that
   forwards to them. Emails to it arrive only from senders you register: **Services → Sign in with Apple for Email
   Communication → Configure**, then add the domain or address notification-service sends from (its SPF must pass).
6. Environment: `APPLE_CLIENT_ID` (the Services ID), `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY` (the
   content of the `.p8` file; in one line, write the line breaks as `\n`). There is no `APPLE_CLIENT_SECRET`.

Apple has no fixed client secret: auth-service signs one itself with the key (`AppleClientSecret`: an ES256 JWT with
the Team ID as issuer, the Services ID as subject and `https://appleid.apple.com` as audience). Apple would accept one
for up to six months; this one lasts an hour and is renewed before it expires. A missing or damaged key stops
auth-service at start-up with a clear message (never the key itself).

Apple posts its answer to the callback (`response_mode=form_post`, from Apple's site: the gateway allows that POST
without a CORS check). It sends the person's name only at their **first** login, in that form (field `user`); it
pre-fills the sign-up form and is never trusted for anything else. PKCE is sent like to every provider; should Apple
ever refuse it, `APPLE_PKCE=false` switches it off for Apple only.

## Configuration

auth-service (environment variables in brackets):

| Property | Default | |
|---|---|---|
| `app.social.enabled` (`SOCIAL_LOGIN_ENABLED`) | `false` | The main switch ([Switching it on](#switching-it-on)) |
| `app.social.providers.<provider>.enabled` (`GOOGLE_LOGIN_ENABLED`, `MICROSOFT_LOGIN_ENABLED`, `FACEBOOK_LOGIN_ENABLED`, `APPLE_LOGIN_ENABLED`) | `true` | `false` switches that provider off |
| `app.social.public-url` (`APP_PUBLIC_URL`) | `http://localhost:5173` | The address people open the shop at. The redirect address of every provider is built from it, never from the request (behind the gateway the request's host would be wrong) |
| `app.social.providers.google.client-id` / `client-secret` (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) | empty: no Google button | |
| `app.social.providers.microsoft.client-id` / `client-secret` (`MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`) | empty: no Microsoft button | |
| `app.social.providers.microsoft.tenant` (`MICROSOFT_TENANT`) | `common` | `common`, `organizations`, `consumers` or one Directory (tenant) ID; anything else stops auth-service at start-up |
| `app.social.providers.facebook.client-id` / `client-secret` (`FACEBOOK_CLIENT_ID`, `FACEBOOK_CLIENT_SECRET`) | empty: no Facebook button | The App ID and the App secret |
| `app.social.providers.facebook.graph-version` (`FACEBOOK_GRAPH_VERSION`) | `v23.0` | |
| `app.social.providers.apple.client-id` (`APPLE_CLIENT_ID`) | empty: no Apple button | The Services ID |
| `app.social.providers.apple.team-id`, `key-id`, `private-key` (`APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`) | empty | Required with Apple: the client secret is signed with them |
| `app.social.providers.<provider>.pkce` (`APPLE_PKCE` for Apple) | `true` | `false` switches PKCE off for one provider |
| `app.social.providers.test.*` (`SOCIAL_TEST_CLIENT_ID`, `SOCIAL_TEST_PROVIDER_URL`) | only in `application-local.yml`; empty: no button | The fake provider. auth-service refuses to start with it outside the `local` and `test` profiles, or with an `https` public URL |
| `app.social.flow-lifetime` | `10m` | Time on the provider's login page |
| `app.social.code-lifetime` | `60s` | The one-time code of `/login/social` |
| `app.social.signup-lifetime` | `15m` | The sign-up form, or the password to connect |
| `app.housekeeping.social-login-retention` (`HOUSEKEEPING_SOCIAL_LOGIN_RETENTION`) | `1d` | Finished and expired attempts are deleted after this (`HousekeepingJob`) |

api-gateway: the rate limit `social-login` (`RATE_LIMIT_SOCIAL_LOGIN_PER_MINUTE`, default 20 per minute and IP) for
the button's redirect and the callback. The three POSTs count toward the `login` limit (10 per minute).

**Secrets:** the client secrets live only in auth-service's environment, never in the React app or in Git. Locally:
environment variables. Kubernetes: the `grocery-secrets` Secret (`GOOGLE_*`, `MICROSOFT_*`, `FACEBOOK_*`, `APPLE_*`,
empty by default) from a secret store. The client **ids** are not secret, but they are configured in the same place.

**Production checklist:**
- `SOCIAL_LOGIN_ENABLED=true`, and the start-up log names the providers you expect;
- `APP_PUBLIC_URL=https://shop.example.com`: exactly the address users open, without a trailing slash;
- `/api` on that address goes to the gateway;
- `REFRESH_COOKIE_SECURE=true` (the default outside the local profile);
- the production redirect address registered at every provider;
- the provider apps published (Google *In production*, Facebook *Live*);
- a reminder before the Microsoft client secret expires;
- Facebook's *Require App Secret* on, and Apple's email relay configured for the sender address.

## Try it locally with the fake provider

No account at Google is needed: [mock-oauth2-server](https://github.com/navikt/mock-oauth2-server) is a real OpenID
Connect provider (login page, signed ID tokens, PKCE) that accepts anyone.

```powershell
# 1. The fake provider at http://127.0.0.1:8099 (profile "tools" of the infrastructure)
docker compose -f infrastructure/docker-compose.yml --profile tools up -d oauth-test-provider

# 2. auth-service with social login and the test provider switched on
$env:SOCIAL_LOGIN_ENABLED = 'true'
$env:SOCIAL_TEST_CLIENT_ID = 'grocery-local'
cd auth-service; .\mvnw spring-boot:run

# 3. The shop: http://localhost:5173/login shows "Continue with Test provider (local only)"
cd fresh-grocery; npm run dev
```

On the fake login page:
- **username** is the provider's id of the person (`sub`): the same username is the same "Google account" next time;
- **claims** is optional JSON, for example
  `{"email": "asha@example.com", "email_verified": true, "name": "Asha Rao"}`.

To try the rules:
- an email that has no account yet: the sign-up form;
- `customer1@grocery.com`: the password form (`Password@123`);
- `admin@grocery.com`: refused;
- **Cancel**-like errors: open `http://localhost:5173/login/social#error=cancelled`.

The fake provider runs at `127.0.0.1`, another site than `localhost:5173`, so the browser treats it like a real
provider (the SameSite cookie rules apply as in production).

**`npm run dev:mock`** (no backend at all): the mock API plays the four providers inside the app. Each button goes
straight to `/login/social#code=mock-<provider>`, a new person (Facebook's email editable, Apple's a relay address).
Other mock codes are in `fresh-grocery/src/mocks/mockSocial.ts` (`mock-google-customer`, `mock-google-vendor`,
`mock-google-admin`, `mock-google-no-email`).

**The real Microsoft, Facebook and Apple** need their own credentials (see [Set up a provider](#set-up-a-provider)):
Microsoft and Facebook work with `http://localhost:5173`; Apple needs an `https` address (a tunnel).

## What was added

| Where | What |
|---|---|
| auth-service `pom.xml` | `spring-boot-starter-security-oauth2-client` (Spring Security 7.1: OAuth 2.0 client, OpenID Connect, JOSE for the ID token) |
| auth-service `V10__social_login.sql` | `users.email_verified_at`; `social_logins` (`user_id`, `provider`, `provider_user_id`, `email`, `last_used_at`; UNIQUE(`provider`, `provider_user_id`), UNIQUE(`user_id`, `provider`)); `social_login_flows` (one row per attempt: hashes of state, browser cookie and code, the stage, the provider's profile, `expires_at`) |
| auth-service `social/` | `SocialLoginSecurityConfig` (two filter chains, below; the ID token checks; Apple's form post), `SocialClientRegistrations` (the configured providers), `FlowAuthorizationRequestRepository` (the attempt in MySQL, not in an HTTP session), `SocialProfiles` (each provider's answer as one `SocialProfile`), the success and failure handlers, `SocialLoginProperties` |
| auth-service `social/`, per provider | `MicrosoftIssuerValidator` (the issuer with the person's directory), `FacebookAppSecretProof` (the Graph API request), `AppleClientSecret` (the signed client secret) |
| auth-service | `SocialLoginService` (the account rules), `SocialLoginController`, `LoginSessions` (the session start, shared with the password login) |
| auth-service, notification-service | the Avro event `SocialLoginLinked` and its email |
| api-gateway | public paths, no CORS check on the callback (Apple posts it from its own site), the `social-login` rate limit |
| infrastructure | the `oauth-test-provider` container (profile `tools`) |
| fresh-grocery | `SocialLoginButtons` on the login pages (not admin) and the register page, each in its provider's own look (Google and Microsoft light, Facebook blue, Apple black; dark versions in the dark theme; colours in `styles/index.css`), `/login/social` (`SocialLoginPage`), `api/socialAuth.ts`, the mock API |

The endpoints (all through the gateway):

| Method | Path | Answer |
|---|---|---|
| GET | `/api/v1/auth/social/providers` | the configured providers, in the order of the buttons: `[{ "id": "google", "name": "Google" }, { "id": "microsoft", ... }]` |
| GET | `/api/v1/auth/oauth2/authorization/{provider}` | 302 to the provider (handled by Spring Security) |
| GET, POST | `/api/v1/auth/login/oauth2/code/{provider}` | the provider's answer; 302 to `/login/social#code=...` or `#error=...` |
| POST | `/api/v1/auth/social/session` `{code}` | `{ status, provider, auth, fullName, email, emailLocked }`; `LOGGED_IN` also sets the refresh cookie |
| POST | `/api/v1/auth/social/signup` `{fullName, email?, phone, role}` | 201, like `/register` |
| POST | `/api/v1/auth/social/link` `{password}` | 200, like `/login` |

**Spring Security in auth-service.** Until now auth-service had no filter chain. It has two now:
- the first only covers the two OAuth paths above (`oauth2Login`, stateless, no CSRF token: the `state` parameter is
  the CSRF protection there, and Apple's form post could not carry one);
- the second covers everything else and changes nothing: permitAll, no sessions, no login form. All existing tests run
  unchanged.

## Tests

```powershell
cd auth-service; .\mvnw verify      # SocialLoginFlowIntegrationTest: MySQL + mock-oauth2-server in Testcontainers
cd api-gateway; .\mvnw verify       # the public paths, the callback without CORS
cd fresh-grocery; npx vitest run src/features/auth/socialLogin.test.tsx
```

- **`SocialLoginFlowIntegrationTest`** runs the real flow against the fake provider, which plays all four providers:
  the start, the provider's login page, the callback and the code. It covers:
  - Microsoft's directory check, and its email verified only with `xms_edov`;
  - Facebook through the Graph API, never linked automatically;
  - Apple's form post, with the name from its `user` field;
  - sign-up;
  - auto-link with both emails verified;
  - the password to connect;
  - admin and disabled accounts refused;
  - a replayed code;
  - a code from another browser;
  - cancel;
  - a forged `state`.
- **`SocialProfilesTest`** covers every provider's verified-email rule. **`SocialClientRegistrationsTest`** covers
  which providers are offered (and in which order), each provider's addresses, and the start-up refusals (a wrong
  Microsoft tenant, Apple without its key, the test provider outside local development).
- **`MicrosoftIssuerValidatorTest`** (another directory's issuer, no `tid`, the tenant settings),
  **`AppleClientSecretTest`** (the JWT's claims and ES256 signature, its renewal) and **`FacebookAppSecretProofTest`**
  (the HMAC on the Graph API request) cover what the fake provider cannot play.
- **`socialLogin.test.tsx`** covers the buttons (not on the admin login), the sign-up form, the password form, the wrong
  panel and the error messages, against the mock API.
- **`e2e/social-login.spec.ts`** runs a real browser (Edge) against the real stack and the fake provider: sign-up and
  the next login at once, a social login over another login kept after a reload, the password to connect with its email
  in Mailpit, cancel, and a replayed code. Playwright's dev server runs on port 5175, so auth-service must send the
  browser back there: start it with `SOCIAL_LOGIN_ENABLED=true`, `SOCIAL_TEST_CLIENT_ID=grocery-local` and
  `APP_PUBLIC_URL=http://localhost:5175`
  (otherwise the flows are skipped, with the reason).

## Security notes

- **The provider's tokens are not our tokens.** auth-service reads the validated ID token once and keeps nothing from
  the provider except the person's id and email (`social_logins`). The shop's own JWT and refresh cookie are issued
  exactly as for a password login.
- **Validated by Spring Security:**
  - `state` (against login CSRF);
  - PKCE (a stolen code is useless);
  - the ID token's signature (the provider's published keys), issuer, audience, expiry and `nonce` (against replay).
- **The one-time code** is random, hashed in the database, valid 60 s, usable once (an atomic `UPDATE ... WHERE stage`),
  and bound to the browser that started the attempt (`social_flow`). It travels in the URL fragment, which never reaches
  a server log or a `Referer`.
- **Login CSRF:** the three POSTs refuse `Sec-Fetch-Site: cross-site`. Without the `social_flow` cookie, which is
  `SameSite=Strict`, they do nothing.
- **One login at a time in the browser:** `/login/social` exchanges the code only after the app's check for an earlier
  login (`/refresh` after the page load) has answered. Otherwise that older login's refused refresh could remove the new
  refresh cookie. The social calls are public auth calls in the app's API client: they never carry an access token, and
  their 401 ("Invalid email or password") is an answer, never a reason to refresh and send the password again.
- **Open redirects:** the backend always redirects to the fixed `/login/social`. The page to open afterwards stays in
  the browser and is checked by `safeNextPath` (no `//`, no `\`, no control characters).
- **Account takeover:**
  - auto-linking needs a verified email on both sides (see [Account rules](#account-rules));
  - a connection by password counts toward the login rate limit;
  - every new connection is announced by email;
  - accounts are matched by the provider's `sub`, never by a changeable name.
- **Admins** cannot use social login: an admin account depends only on its password.
- **The test provider** cannot be switched on in production (start-up fails).

**Not covered (yet):**
- a page to see and remove connected accounts;
- Apple's server-to-server notifications (account deleted, email forwarding switched off) and token revocation;
- MFA.
