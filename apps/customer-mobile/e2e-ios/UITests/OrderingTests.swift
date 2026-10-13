import XCTest

/// MB-032 on the iOS simulator against the local identity provider and API with synthetic data (e2e-native/seed.sql).
/// The script runs the phases in order and changes the network between them (XCUITest cannot):
///   testA  basket replacement asks first; keep leaves the basket, replace replaces it (TC-148); sign in; place the order
///          and terminate the app while the response is held back by a slow proxy (TC-049).
///   testB  (API unreachable) reopened, the cart says the order is unconfirmed and that the app is offline.
///   testC  (API back) reopening shows the one order with the server's total, by itself or after "Check and retry" (TC-049, TC-132).
/// MB-033 follow-up (the script lowers the synthetic policy limit from 10 to 5 between D and E):
///   testD  the quantity control stops at min(policy limit 10, 15 available portions) and the ten are added to the basket.
///   testE  the saved basket is flagged and cannot be quoted; reducing to 5 restores the quote; a delivery order to the
///          saved address shows the zone fee and the server's total.
///   testF  the order link (matbakhy-dev://orders/<id>) opens that order while the app runs (warm) and after termination (cold).
///   testG  same session: with 6 in the basket the script lowers the limit to 5 while the app keeps running (the test waits on
///          the public configuration); reopening the cart flags the line.
///   testI  MB-033 dynamic text (the script sets the largest accessibility text size first): every customer area in English,
///          then in Arabic, shows its content and reaches its main action; screenshots are kept for visual review.
///   testJ  MB-033 notification permission: not asked on launch; Allow shows the system prompt; Don't Allow leaves the
///          app usable with a Settings path and the in-app notifications.
///   testH  D13: after a quote the script raises Native Dish One to 130.00 (the test waits on the public meal); placing shows
///          the kitchen's change and the old and new totals; the new quote is placed only after explicit acceptance.
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
        // Tap the sign-in sheet's own heading to dismiss the keyboard. The app's tabs behind the sheet also contain
        // "Sign in to see your orders…", which exists but is not hittable (native run 23), so require hittable.
        let headings = [authUI, app].flatMap {
            $0.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Sign in to")).allElementsBoundByIndex
        }
        if let heading = headings.first(where: { $0.exists && $0.isHittable }) {
            heading.tap()
        }
        Thread.sleep(forTimeInterval: 1.5)
        guard let secret = webField(secure: true) else { return XCTFail("password field missing") }
        secret.tap()
        Thread.sleep(forTimeInterval: 1.5)
        secret.typeText(password + "\n")
        dismissSavePassword()
    }

    /// iOS may offer "Save Password?" after the provider's form is submitted; while it is up the redirect waits and the
    /// authorization code can expire (native run 38). Dismiss it when it appears.
    private func dismissSavePassword() {
        for root in [app, authUI, XCUIApplication(bundleIdentifier: "com.apple.springboard")] {
            let notNow = root.buttons["Not Now"].firstMatch
            if notNow.waitForExistence(timeout: 4) {
                notNow.tap()
                return
            }
        }
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
        // Relaunched online and signed in, the cart asks the server at once with the stored request id and shows the
        // order (native run 24); "Check and retry" is needed only when that first check cannot finish.
        let number = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "MB-")).firstMatch
        if !number.waitForExistence(timeout: 30) {
            expect(element("retry-order"), "neither the recovered order nor a retry was shown")
            element("retry-order").tap()
        }
        expect(number, "recovered order not shown")
        print("MB032-ORDER-NUMBER \(number.label)")
        let total = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Total: ")).firstMatch
        if total.waitForExistence(timeout: 10) {
            print("MB032-ORDER-TOTAL \(total.label)")
        }
        shot("recovered-order")
    }

    func testD_QuantityStopsAtThePolicyLimit() {
        app.launch()
        let meal = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Native Dish One")).firstMatch
        expect(meal, "Native Dish One not listed", timeout: 90)
        meal.tap()
        expect(element("slot-0"), "no slot")
        element("slot-0").tap()
        let increase = app.buttons["Increase"]
        expect(increase, "no increase control")
        for _ in 0..<14 where increase.isEnabled {
            increase.tap()
        }
        expect(app.staticTexts["10"], "quantity did not stop at the policy limit of 10", timeout: 10)
        XCTAssertFalse(increase.isEnabled, "increase still enabled at the limit")
        print("MB033-QUANTITY-STOPPED-AT 10")
        shot("quantity-at-limit")
        tapScrolled("add-to-cart")
        expect(text("Added to cart."), "ten portions not added")
        app.terminate()
    }

    func testE_LoweredLimitBlocksQuoteThenDeliveryOrder() {
        app.launch()
        expect(element("open-cart"), "cart shortcut missing", timeout: 90)
        element("open-cart").tap()
        expect(text("At most 5 per meal can be ordered now"), "over-limit basket line not flagged")
        let quote = element("get-quote")
        expect(quote, "no quote button")
        XCTAssertFalse(quote.isEnabled, "quote still possible with an over-limit quantity")
        print("MB033-OVER-LIMIT-BLOCKED")
        shot("over-limit-blocked")
        let decrease = element("qty-dec-00000000-0000-4000-8000-00000000a0b1")
        expect(decrease, "no decrease control on the basket line")
        for _ in 0..<5 {
            decrease.tap()
        }
        expect(text("5 × Native Dish One"), "basket line not reduced to 5", timeout: 10)
        XCTAssertFalse(text("At most 5 per meal").exists, "over-limit notice still shown after the correction")

        tapScrolled("mode-DELIVERY")
        // The saved address is a choice (a button on iOS), preselected as the first address.
        expect(app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "Home · Synthetic street 1")).firstMatch,
               "saved delivery address not offered")
        tapScrolled("get-quote")
        let total = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Total: ")).firstMatch
        expect(total, "no delivery quote")
        print("MB033-DELIVERY-QUOTE-TOTAL \(total.label)")
        let fee = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Delivery: ")).firstMatch
        expect(fee, "zone fee not shown", timeout: 5)
        print("MB033-DELIVERY-FEE \(fee.label)")
        shot("delivery-quote")
        tapScrolled("accept-terms")
        tapScrolled("place-order")
        let number = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "MB-")).firstMatch
        expect(number, "delivery order not confirmed")
        print("MB033-DELIVERY-ORDER-NUMBER \(number.label)")
        shot("delivery-order")
    }

    func testF_OrderDeepLinkWarmAndCold() {
        let env = ProcessInfo.processInfo.environment
        // XCUIApplication.open(_:) needs iOS 16.4; the CI simulator runtime is newer. An older runtime fails, never skips.
        guard #available(iOS 16.4, *) else {
            return XCTFail("deep-link check needs an iOS 16.4 or newer simulator")
        }
        guard let id = env["MB033_ORDER_ID"], let number = env["MB033_ORDER_NUMBER"], let url = URL(string: "matbakhy-dev://orders/\(id)") else {
            return XCTFail("order id and number not passed by the script")
        }
        app.launch()
        expect(element("tab-discover"), "Discover not shown before the warm link")
        app.open(url)
        expect(app.staticTexts[number], "warm deep link did not open \(number)")
        shot("deep-link-warm")
        app.terminate()
        app.open(url)
        expect(app.staticTexts[number], "cold deep link did not open \(number)", timeout: 90)
        print("MB033-DEEP-LINK-OK \(number)")
        shot("deep-link-cold")
    }

    /// Waits (polling the local API the app uses) until `condition` holds for the JSON at `path`; the script makes the change.
    private func waitForServer(_ path: String, timeout: TimeInterval = 120, _ condition: @escaping ([String: Any]) -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            let done = XCTestExpectation(description: path)
            var ok = false
            URLSession.shared.dataTask(with: URL(string: "http://localhost:8080\(path)")!) { data, _, _ in
                if let data, let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    ok = condition(object)
                }
                done.fulfill()
            }.resume()
            _ = XCTWaiter.wait(for: [done], timeout: 10)
            if ok { return true }
            Thread.sleep(forTimeInterval: 2)
        }
        return false
    }

    func testG_LimitLoweredInTheSameSession() {
        app.launch()
        let meal = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Native Dish One")).firstMatch
        expect(meal, "Native Dish One not listed", timeout: 90)
        meal.tap()
        expect(element("slot-0"), "no slot")
        element("slot-0").tap()
        let increase = app.buttons["Increase"]
        for _ in 0..<5 { increase.tap() }
        expect(app.staticTexts["6"], "quantity 6 not reached", timeout: 10)
        tapScrolled("add-to-cart")
        expect(text("Added to cart."), "six portions not added")
        tapScrolled("go-to-cart")
        expect(text("6 × Native Dish One"), "basket line 6 not shown")
        XCTAssertFalse(text("per meal can be ordered now").exists)
        print("MB033-G-READY")
        XCTAssertTrue(waitForServer("/api/v1/configuration/public") { ($0["orderLimits"] as? [String: Any])?["maxQuantityPerLine"] as? Int == 5 },
                      "the script did not lower the limit")
        XCTAssertEqual(app.state, .runningForeground, "the app must still be the same running session")
        app.navigationBars.buttons.firstMatch.tap()
        tapScrolled("go-to-cart")
        expect(text("At most 5 per meal can be ordered now"), "lowered limit not applied in the same session")
        print("MB033-SAME-SESSION-LIMIT-OK")
        shot("same-session-limit")
        let decrease = element("qty-dec-00000000-0000-4000-8000-00000000a0b1")
        for _ in 0..<5 { decrease.tap() }
        expect(text("1 × Native Dish One"), "basket line not reduced to 1", timeout: 10)
    }

    func testH_KitchenPriceChangeNeedsReconfirmation() {
        app.launch()
        expect(element("open-cart"), "cart shortcut missing", timeout: 90)
        element("open-cart").tap()
        // Native run 36: this relaunch, about ten minutes after phase A's sign-in, showed the cart signed out because
        // the app never refreshed its 5-minute access token. Fixed in MB-031 (token refresh); the relaunch must now
        // still be signed in, which makes this phase the native check of the refresh.
        if !element("mode-PICKUP").waitForExistence(timeout: 20) && app.buttons["Sign in"].firstMatch.exists {
            shot("signed-out-before-price-change")
            XCTFail("signed out after relaunch: the expired access token was not refreshed")
            return
        }
        print("MB049-STILL-SIGNED-IN")
        tapScrolled("mode-PICKUP")
        tapScrolled("get-quote")
        let total = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Total: ")).firstMatch
        expect(total, "no quote")
        XCTAssertTrue(total.label.contains("120.00"), "quote not at the old price: \(total.label)")
        tapScrolled("accept-terms")
        print("MB049-H-READY")
        XCTAssertTrue(waitForServer("/api/v1/meals/00000000-0000-4000-8000-00000000a0b1") { ($0["price"] as? [String: Any])?["amountMinor"] as? Int == 13000 },
                      "the script did not change the price")
        tapScrolled("place-order")
        let kitchen = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "The kitchen updated the price of")).firstMatch
        expect(kitchen, "kitchen price change not explained")
        XCTAssertTrue(kitchen.label.contains("Native Dish One") && kitchen.label.contains("120.00") && kitchen.label.contains("130.00"), kitchen.label)
        print("MB049-NOTICE \(kitchen.label)")
        let totals = element("price-change-total")
        expect(totals, "old and new total not shown")
        print("MB049-TOTALS \(totals.label)")
        shot("price-change-notice")
        XCTAssertFalse(element("place-order").isEnabled, "placing possible before accepting the new total")
        tapScrolled("accept-new-total")
        tapScrolled("accept-terms")
        tapScrolled("place-order")
        let number = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "MB-")).firstMatch
        expect(number, "repriced order not confirmed")
        print("MB049-ORDER-NUMBER \(number.label)")
        shot("repriced-order")
    }

    /// Scrolls until a button with this label is hittable, then taps it (buttons without an identifier).
    private func tapLabel(_ label: String) {
        let target = app.buttons[label].firstMatch
        expect(target, "\(label) missing")
        var tries = 0
        while !target.isHittable && tries < 8 {
            app.swipeUp()
            tries += 1
        }
        target.tap()
    }

    /// Scrolls until the element is hittable (reachable on screen) without tapping it.
    private func reach(_ id: String, _ message: String) {
        let target = element(id)
        expect(target, message)
        var tries = 0
        while !target.isHittable && tries < 10 {
            app.swipeUp()
            tries += 1
        }
        XCTAssertTrue(target.isHittable, message)
    }

    private func dynamicTextPass(_ lang: String, order number: String, notifications: String, support: String) {
        element("tab-discover").tap()
        let meal = element("meal-00000000-0000-4000-8000-00000000a0b1")
        expect(meal, "[\(lang)] Discover: meal not listed at large text", timeout: 90)
        shot("dt-\(lang)-discover")
        meal.tap()
        expect(element("slot-0"), "[\(lang)] Meal: no slot")
        element("slot-0").tap()
        reach("add-to-cart", "[\(lang)] Meal: add to cart not reachable at large text")
        shot("dt-\(lang)-meal")
        tapScrolled("add-to-cart")
        tapScrolled("go-to-cart")
        expect(element("cart-lines"), "[\(lang)] Cart: basket lines missing")
        shot("dt-\(lang)-cart")
        // Nothing may extend past the screen edge (native run 39: the Pickup choice did at the largest size).
        let width = app.windows.firstMatch.frame.width
        for id in ["mode-DELIVERY", "mode-PICKUP", "get-quote"] where element(id).exists {
            let frame = element(id).frame
            XCTAssertTrue(frame.minX >= 0 && frame.maxX <= width + 0.5, "[\(lang)] \(id) runs past the screen edge: \(frame) in width \(width)")
        }
        // Place order appears only with a quote: calculate the total first.
        tapScrolled("get-quote")
        expect(element("quote"), "[\(lang)] Checkout: no quote at large text")
        reach("place-order", "[\(lang)] Checkout: place order not reachable at large text")
        shot("dt-\(lang)-checkout")
        app.navigationBars.buttons.firstMatch.tap()
        app.navigationBars.buttons.firstMatch.tap()
        element("tab-orders").tap()
        let order = element("order-\(number)")
        expect(order, "[\(lang)] Orders: \(number) not listed")
        shot("dt-\(lang)-orders")
        order.tap()
        expect(element("order-items"), "[\(lang)] Order: items missing")
        shot("dt-\(lang)-order")
        app.navigationBars.buttons.firstMatch.tap()
        element("tab-account").tap()
        tapLabel(notifications)
        expect(element("notifications"), "[\(lang)] Notifications screen missing")
        shot("dt-\(lang)-notifications")
        app.navigationBars.buttons.firstMatch.tap()
        tapLabel(support)
        expect(element("support"), "[\(lang)] Support screen missing")
        shot("dt-\(lang)-support")
        app.navigationBars.buttons.firstMatch.tap()
        print("MB033-DYNAMIC-TEXT-OK \(lang)")
    }

    func testI_DynamicTextEnglishAndArabic() {
        guard let number = ProcessInfo.processInfo.environment["MB033_DT_ORDER"], !number.isEmpty else {
            return XCTFail("order number not passed by the script")
        }
        app.launch()
        dynamicTextPass("en", order: number, notifications: "Notifications", support: "Support")
        // The language button names the other language; the choice is not persisted, so Arabic continues in this process.
        element("tab-account").tap()
        tapLabel("العربية")
        dynamicTextPass("ar", order: number, notifications: "الإشعارات", support: "الدعم")
    }

    func testJ_NotificationRefusalIsSafe() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        app.launch()
        element("tab-account").tap()
        let undetermined = element("phone-alerts-undetermined")
        expect(undetermined, "permission not shown as not yet asked (the app must not ask on launch)")
        XCTAssertFalse(springboard.alerts.firstMatch.exists, "a system prompt appeared without the customer asking")
        tapScrolled("allow-alerts")
        let prompt = springboard.alerts.firstMatch
        expect(prompt, "no system notification prompt", timeout: 20)
        let deny = prompt.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Don")).firstMatch
        XCTAssertTrue(deny.waitForExistence(timeout: 10), "no Don't Allow button")
        deny.tap()
        expect(element("phone-alerts-denied"), "refusal not explained")
        expect(element("open-alert-settings"), "no path to Settings after a refusal", timeout: 10)
        XCTAssertFalse(element("allow-alerts").exists, "still offering the system prompt after a refusal")
        print("MB033-ALERTS-DENIED-SAFE")
        shot("alerts-denied")
        tapLabel("Notifications")
        expect(element("notifications"), "in-app notifications unavailable after the refusal")
        shot("notifications-after-refusal")
    }
}
