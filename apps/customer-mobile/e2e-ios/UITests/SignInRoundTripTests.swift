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
        user.tap()
        user.typeText(username)
        guard let secret = field(secure: true, timeout: 10) else { return XCTFail("password field missing") }
        secret.tap()
        secret.typeText(password + "\n")
    }

    private func relaunch() {
        app.terminate()
        app.launch()
    }

    func testSignInSignOutAndAccountSwitchLeaveNoPreviousUserSession() {
        app.launch()

        // 1
        XCTAssertTrue(button("Sign in").waitForExistence(timeout: 60))
        button("Sign in").tap()
        login("customer-a")
        XCTAssertTrue(label(containing: "SyntheticA").waitForExistence(timeout: 90), "customer-a not signed in")
        shot("signed-in-a")
        relaunch()
        XCTAssertTrue(label(containing: "SyntheticA").waitForExistence(timeout: 60), "session not restored after restart")

        // 2
        button("Sign out").tap()
        XCTAssertTrue(button("Sign in").waitForExistence(timeout: 60), "not signed out")
        relaunch()
        XCTAssertTrue(button("Sign in").waitForExistence(timeout: 60), "not signed out after restart")
        XCTAssertFalse(label(containing: "SyntheticA").exists, "customer-a still visible after sign-out")
        shot("signed-out-after-restart")

        // 3
        button("Sign in").tap()
        XCTAssertTrue(loginFormAppears(), "login form not shown again after sign-out")
        login("customer-b")
        XCTAssertTrue(label(containing: "SyntheticB").waitForExistence(timeout: 90), "customer-b not signed in")
        XCTAssertFalse(label(containing: "SyntheticA").exists, "customer-a visible to customer-b")
        shot("signed-in-b")

        // 4
        button("Switch account").tap()
        XCTAssertTrue(loginFormAppears(), "switch account did not show the login form")
        shot("switch-login-form")
        let cancel = [app, authUI].map { $0.buttons["Cancel"] }.first { $0.exists }
        XCTAssertNotNil(cancel, "no Cancel button on the sign-in sheet")
        cancel?.tap()
        XCTAssertTrue(button("Sign in").waitForExistence(timeout: 60), "not signed out after cancelled switch")
        XCTAssertFalse(label(containing: "SyntheticB").exists, "customer-b still visible after cancelled switch")
        relaunch()
        XCTAssertTrue(button("Sign in").waitForExistence(timeout: 60))
        XCTAssertFalse(label(containing: "SyntheticB").exists)
        shot("cancelled-switch")
    }
}
