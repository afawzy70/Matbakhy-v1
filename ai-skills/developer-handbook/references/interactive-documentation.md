# Interactive documentation (Stage D)

Purpose: readers open the real applications while reading. Provide only what can be shown to be true.

## Live environment reference page
One central page listing, per environment: URL, purpose, expected HTTP behaviour, verification status, authentication requirements, network restrictions, who should use it (ordinary developers or authorized administrators only). Link workflow pages and deployment documentation.

## Safe URL validation
Use read-only HTTPS requests (`curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' <url>`, certificate dates with `openssl s_client`, DNS with `dig`). Record the result, the date and *from where* the request was made.
- A 403 can be an intentional access policy (for example an IP allow-list), not an outage; say which layer answered (response headers and body show whether it came from the edge proxy or an upstream).
- A 302 to a login or administration host is normal; a 404 on a non-proxied management endpoint can be deliberate.
- Do not invent deep links; when no reliable deep link exists give the navigation path from the home page.
- Never include session tokens, credentials or personal data in links or examples.
- Unavailable environments: state what is needed before walkthroughs can be offered.

## Journey format
Feature, business purpose, required role, test account *name* (never the password; say where an authorized user obtains it), URL, navigation, exact steps, expected result, related API calls, implementation modules, automated test references, known limitations. Cover at least: registration and login, discovery/search, detail view, basket/cart, checkout, order creation and tracking, operator or back-office processing, administration, finance/support, and authentication including multi-factor, as far as the product has them. Use only synthetic or demo accounts. Privileged administrative operations get a security warning and no casual test instructions.

## Behaviour that deserves an explicit walkthrough
Money, stock, price changes, permissions and anything irreversible: document the implemented behaviour from code and tests, then the walkthrough, then what is *not* implemented.
