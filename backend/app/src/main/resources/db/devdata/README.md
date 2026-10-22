Synthetic development fixture migrations (repeatable `R__*.sql`). Loaded only with the `dev`
profile in the synthetic environments LOCAL, CI and PREVIEW (the Contabo preview selects PREVIEW with the `dev` profile); `EnvironmentGuard` blocks them in STAGING and PRODUCTION. Fixture identities and businesses
must be obviously synthetic. Domain fixtures are added by the stories that create those tables.
