Synthetic development fixture migrations (repeatable `R__*.sql`). Loaded only with the `dev`
profile in LOCAL/CI; `EnvironmentGuard` blocks them elsewhere. Fixture identities and businesses
must be obviously synthetic. Domain fixtures are added by the stories that create those tables.
