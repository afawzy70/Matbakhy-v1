import XCTest

/// MB-032 on the iOS simulator against the local identity provider and API with synthetic data (e2e-native/seed.sql).
/// The script runs the phases in order and changes the network between them (XCUITest cannot):
///   testA  basket replacement asks first; keep leaves the basket, replace replaces it (TC-148); sign in; place the order
///          and terminate the app while the response is held back by a slow proxy (TC-049).
///   testB  (API unreachable) reopened, the cart says the order is unconfirmed and that the app is offline.
///   testC  (API back) "Check and retry" shows the one order with the server's total (TC-049, TC-132).
final class OrderingTests: XCTestCase {
    let app = XCUIApplication(bundleIdentifier: "com.matbakhy.customer.dev")
    let authUI = XCUIApplication(bundleIdentifier: "com.apple.SafariViewService")
    var password: String { ProcessInfo.processInfo.environment["MB031_USER_PASSWORD"] ?? "" }

    override func setUp() {
        continueAfterFailure = false
    }

    private func element(_ id: String) -> XCUIElement { app.descendants(matching: .any)[id] }

    private func text(_ value: String) -> XCUIElement {
        app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", value)).firstMatch
    }

    private func expect(_ element: XCUIElement, _ message: String, timeout: TimeInterval = 60) {
        if element.waitForExistence(timeout: timeout) { return }
        print("MB031-HIERARCHY-BEGIN (\(message))")
        print(app.debugDescription)
        print("MB031-HIERARCHY-END")
        XCTFail(message)
    }

    private func shot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// Scrolls until the element is hittable, then taps it.
    private func tapScrolled(_ id: String) {
        let target = element(id)
        expect(target, "\(id) missing")
        var tries = 0
        while !target.isHittable && tries < 6 {
            app.swipeUp()
            tries += 1
        }
        target.tap()
    }

    private func addFirstSlot(of mealLabel: String) {
        let meal = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", mealLabel)).firstMatch
        expect(meal, "\(mealLabel) not listed", timeout: 90)
        meal.tap()
        expect(element("slot-0"), "no slot for \(mealLabel)")
        element("slot-0").tap()
        element("add-to-cart").tap()
    }

    private func webField(secure: Bool) -> XCUIElement? {
        let deadline = Date().addingTimeInterval(90)
        while Date() < deadline {
            for root in [app, authUI] {
                let candidate = (secure ? root.secureTextFields : root.textFields).firstMatch
                if candidate.exists { return candidate }
            }
            Thread.sleep(forTimeInterval: 1)
        }
        return nil
    }

    private func login(_ username: String) {
        guard let user = webField(secure: false) else { return XCTFail("login form did not appear") }
        user.tap()
        Thread.sleep(forTimeInterval: 1.5)
        user.typeText(username)
        for root in [app, authUI] {
            let heading = root.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Sign in to")).firstMatch
            if heading.exists {
                heading.tap()
                break
            }
        }
        Thread.sleep(forTimeInterval: 1.5)
        guard let secret = webField(secure: true) else { return XCTFail("password field missing") }
        secret.tap()
        Thread.sleep(forTimeInterval: 1.5)
        secret.typeText(password + "\n")
    }

    func testA_ReplacementPromptAndSubmitThenKill() {
        app.launch()
        addFirstSlot(of: "Native Dish One")
        expect(text("Added to cart."), "first meal not added")
        app.navigationBars.buttons.firstMatch.tap()
        addFirstSlot(of: "Native Dish Two")
        let alert = app.alerts.firstMatch
        expect(alert, "no replacement prompt")
        XCTAssertTrue(alert.staticTexts["Replace your cart?"].exists)
        shot("replace-prompt")
        alert.buttons["Keep cart"].tap()
        expect(text("Your cart is unchanged."), "keep did not keep")
        element("go-to-cart").tap()
        expect(text("1 × Native Dish One"), "basket changed after keep")
        app.navigationBars.buttons.firstMatch.tap()
        element("add-to-cart").tap()
        expect(app.alerts.firstMatch, "no replacement prompt the second time")
        app.alerts.firstMatch.buttons["Replace cart"].tap()
        expect(text("Added to cart."), "replace did not add")
        element("go-to-cart").tap()
        expect(text("1 × Native Dish Two"), "basket not replaced")
        XCTAssertFalse(text("1 × Native Dish One").exists)
        shot("cart-replaced")

        app.buttons["Sign in"].firstMatch.tap()
        login("customer-a")
        expect(element("mode-PICKUP"), "checkout not shown after sign-in", timeout: 90)
        element("mode-PICKUP").tap()
        tapScrolled("get-quote")
        expect(text("Total: "), "no quote")
        tapScrolled("accept-terms")
        // The script has pointed the API at a proxy that holds responses back: the order is created, the app never hears.
        tapScrolled("place-order")
        Thread.sleep(forTimeInterval: 3)
        app.terminate()
    }

    func testB_ReopenedOfflineSaysUnconfirmed() {
        app.launch()
        expect(element("open-cart"), "cart shortcut missing")
        element("open-cart").tap()
        expect(text("couldn't confirm whether your order arrived"), "unconfirmed order not stated")
        expect(text("You're offline"), "offline not stated")
        XCTAssertFalse(element("place-order").exists)
        shot("reopened-offline")
        app.terminate()
    }

    func testC_BackOnlineRecoversTheOneOrder() {
        app.launch()
        expect(element("open-cart"), "cart shortcut missing")
        element("open-cart").tap()
        expect(element("retry-order"), "retry missing")
        element("retry-order").tap()
        let number = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "MB-")).firstMatch
        expect(number, "recovered order not shown")
        print("MB032-ORDER-NUMBER \(number.label)")
        let total = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Total: ")).firstMatch
        if total.waitForExistence(timeout: 10) {
            print("MB032-ORDER-TOTAL \(total.label)")
        }
        shot("recovered-order")
    }
}
