import XCTest

/// MB-031 / TC-048 on the iOS simulator against the local identity provider and API, synthetic users only:
/// 1. customer-a signs in through the system browser (ASWebAuthenticationSession, PKCE); the session survives a restart.
/// 2. sign-out leaves nothing: after a restart the app is signed out.
/// 3. the next sign-in shows the login form again (no silent re-login as A); customer-b signs in; A's name is gone.
/// 4. "Switch account" also demands a fresh login; cancelling leaves the app signed out with B's data gone.
final class SignInRoundTripTests: XCTestCase {
    let app = XCUIApplication(bundleIdentifier: "com.matbakhy.customer.dev")
    let authUI = XCUIApplication(bundleIdentifier: "com.apple.SafariViewService")
    var password: String { ProcessInfo.processInfo.environment["MB031_USER_PASSWORD"] ?? "" }

    override func setUp() {
        continueAfterFailure = false
        XCTAssertFalse(password.isEmpty, "MB031_USER_PASSWORD not passed to the test runner")
    }

    private func label(containing text: String) -> XCUIElement {
        app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    private func button(_ title: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", title)).firstMatch
    }

    private func shot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// The provider's page is hosted by the system; look in the app first, then in the system web-auth service.
    private func field(secure: Bool, timeout: TimeInterval = 90) -> XCUIElement? {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            for root in [app, authUI] {
                let candidate = (secure ? root.secureTextFields : root.textFields).firstMatch
                if candidate.exists { return candidate }
            }
            Thread.sleep(forTimeInterval: 1)
        }
        return nil
    }

    private func loginFormAppears() -> Bool { field(secure: false) != nil }

    private func login(_ username: String) {
        guard let user = field(secure: false) else { return XCTFail("login form did not appear") }
        shot("login-form-\(username)")
        type(username, into: user)
        dismissKeyboard()
        guard let secret = field(secure: true, timeout: 10) else { return XCTFail("password field missing") }
        type(password + "\n", into: secret)
        // customer-a signs in as "Customer SyntheticA", customer-b as "Customer SyntheticB".
        dismissSavePassword(until: label(containing: "Synthetic" + username.suffix(1).uppercased()))
    }

    /// iOS may offer "Save Password?" after the provider's form is submitted; while it is up the redirect waits and the
    /// authorization code can expire (native run 38). Dismiss it when it appears.
    /// Run 43: the prompt appeared after a sequential 3 × 4 s look had ended, so all three places are now checked
    /// together, every half second, for up to 30 s; the wait ends early once the app shows the signed-in account.
    /// Run 44: the prompt can come after a 30 s look, while the redirect waits behind it. The wait now lasts as long as the
    /// sign-in itself (90 s), ends when the signed-in element appears, and keeps looking 3 s more in case the prompt lands
    /// on top of the signed-in screen.
    private func dismissSavePassword(until done: XCUIElement? = nil) {
        let roots = [app, authUI, XCUIApplication(bundleIdentifier: "com.apple.springboard")]
        var deadline = Date().addingTimeInterval(90)
        var settled = false
        while Date() < deadline {
            for root in roots {
                let notNow = root.buttons["Not Now"].firstMatch
                if notNow.exists && notNow.isHittable {
                    // Run 46: XCTest's own interruption handling may dismiss the prompt first, and tapping the vanished
                    // element fails the test. A tap on the screen position cannot fail; if the prompt is already gone it
                    // lands on empty space below the account actions.
                    let f = notNow.frame
                    app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0)).withOffset(CGVector(dx: f.midX, dy: f.midY)).tap()
                    print("MB031-SAVE-PASSWORD-DISMISSED")
                }
            }
            if !settled, let done, done.exists {
                settled = true
                deadline = Date().addingTimeInterval(3)
            }
            Thread.sleep(forTimeInterval: 0.5)
        }
    }

    /// With the software keyboard up, its form-assistant bar can cover the password field (native run 18): tap the page
    /// heading to blur the username field and let the keyboard go down before the next field is tapped.
    private func dismissKeyboard() {
        for root in [app, authUI] {
            let heading = root.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Sign in to")).firstMatch
            if heading.exists {
                heading.tap()
                break
            }
        }
        Thread.sleep(forTimeInterval: 1.5)
    }

    /// Web fields in the system sign-in sheet do not report keyboard focus to XCUITest; tap, let the keyboard settle,
    /// then type (native runs 16-17).
    private func type(_ text: String, into element: XCUIElement) {
        element.tap()
        Thread.sleep(forTimeInterval: 1.5)
        element.typeText(text)
        Thread.sleep(forTimeInterval: 0.5)
    }

    /// Waits for an element; on timeout prints what the app shows (marker for the CI script) and fails.
    private func expect(_ element: XCUIElement, _ message: String, timeout: TimeInterval = 90) {
        if element.waitForExistence(timeout: timeout) { return }
        print("MB031-HIERARCHY-BEGIN (\(message))")
        print(app.debugDescription)
        print("MB031-HIERARCHY-END")
        XCTFail(message)
    }

    /// The app opens on Discover (MB-032); sign-in, greeting, switch and sign-out live on the Account tab.
    private func openAccount() {
        let byId = app.buttons["tab-account"]
        let tab = byId.waitForExistence(timeout: 30) ? byId : app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Account")).firstMatch
        expect(tab, "Account tab missing", timeout: 30)
        tab.tap()
    }

    private func relaunch() {
        app.terminate()
        app.launch()
        openAccount()
    }

    func testSignInSignOutAndAccountSwitchLeaveNoPreviousUserSession() {
        app.launch()
        openAccount()

        // 1
        expect(button("Sign in"), "sign-in button missing", timeout: 60)
        button("Sign in").tap()
        login("customer-a")
        expect(label(containing: "SyntheticA"), "customer-a not signed in", timeout: 90)
        shot("signed-in-a")
        relaunch()
        expect(label(containing: "SyntheticA"), "session not restored after restart", timeout: 60)

        // 2
        button("Sign out").tap()
        expect(button("Sign in"), "not signed out", timeout: 60)
        relaunch()
        expect(button("Sign in"), "not signed out after restart", timeout: 60)
        XCTAssertFalse(label(containing: "SyntheticA").exists, "customer-a still visible after sign-out")
        shot("signed-out-after-restart")

        // 3
        button("Sign in").tap()
        XCTAssertTrue(loginFormAppears(), "login form not shown again after sign-out")
        login("customer-b")
        expect(label(containing: "SyntheticB"), "customer-b not signed in", timeout: 90)
        XCTAssertFalse(label(containing: "SyntheticA").exists, "customer-a visible to customer-b")
        shot("signed-in-b")

        // 4
        button("Switch account").tap()
        XCTAssertTrue(loginFormAppears(), "switch account did not show the login form")
        shot("switch-login-form")
        let cancel = [app, authUI].map { $0.buttons["Cancel"] }.first { $0.exists }
        XCTAssertNotNil(cancel, "no Cancel button on the sign-in sheet")
        cancel?.tap()
        expect(button("Sign in"), "not signed out after cancelled switch", timeout: 60)
        XCTAssertFalse(label(containing: "SyntheticB").exists, "customer-b still visible after cancelled switch")
        relaunch()
        expect(button("Sign in"), "sign-in button missing", timeout: 60)
        XCTAssertFalse(label(containing: "SyntheticB").exists)
        shot("cancelled-switch")
    }
}
