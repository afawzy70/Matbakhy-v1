# Physical iPhone test plan for the TestFlight beta

Run after: the D18 realm migration, `register-mobile-client.sh apply`, the Preview allow-list decision, and a TestFlight install of build N.
Use synthetic accounts only. Record every result in the table; leave `NOT RUN` until it was executed on a phone. Do not paste tokens, codes or screenshots showing secrets.

| # | Check | Expected | Result (device, iOS, build, date) |
|---|---|---|---|
| A1 | Install from TestFlight, open | App named *Matbakhy Beta*, Arabic default or device language | NOT RUN |
| A2 | Reach the API: open Discover | Meals list loads over cellular and Wi-Fi (no 403 "Not available") | NOT RUN |
| B1 | Account → sign in | System browser sheet (not an embedded view); Keycloak login with a synthetic customer; returns to the app signed in | NOT RUN |
| B2 | Kill the app from the switcher and reopen | Still signed in (tokens restored from the Keychain), no new login | NOT RUN |
| B3 | Wait past the access-token lifetime (> 5 min), then open Orders | Works without login (refresh token) | NOT RUN |
| B4 | Sign out | Signed-out state; reopening shows signed out; basket and private data gone | NOT RUN |
| B5 | Switch account (sign out, sign in as another customer) | No data of the first account is visible; basket empty | NOT RUN |
| B6 | Cancel the browser sheet during sign-in | Back in the app, signed out, no error crash | NOT RUN |
| B7 | Airplane mode during an order submit, then reconnect | One order only; recovery message, no duplicate | NOT RUN |
| C1 | Customer without MFA: normal journey (browse, basket, quote, order) | No OTP asked anywhere | NOT RUN |
| C2 | In the web app (Account → Security) turn on two-step verification with the same customer, then use the phone | Phone shows the *Verify* banner on the next protected screen; no broken screens | NOT RUN |
| C3 | Tap *Verify* | System browser asks password + OTP (or enrolment on first use); returns; banner disappears; basket and account unchanged; screens work after pull-to-refresh | NOT RUN |
| C4 | Cancel the verification | Banner stays; app still signed in | NOT RUN |
| C5 | Administrator resets this customer's MFA, then use the phone | Next protected call asks for verification again; old session no longer counts | NOT RUN |
| C6 | Administrator sets CUSTOMER to REQUIRED (test only, then back to OPTIONAL) | Phone asks for verification on the next protected call; after OPTIONAL it works as C1/C2 | NOT RUN |
| D1 | Wrong redirect: not testable on device; covered by `verify-mfa-flow.py` | — | see server verification |
| D2 | Deep link to an order while signed out | Asks to sign in, requests nothing private | NOT RUN |

Failure handling: stop at the first failing check, keep the build number, file a defect with steps and the response codes (no tokens), fix in the application or realm, repeat from the failed row.
