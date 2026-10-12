import XCTest

final class IndependentQATests: XCTestCase {
    let app = XCUIApplication(bundleIdentifier: "com.matbakhy.customer.dev")
    let meal = "00000000-0000-4000-8000-00000000a0b1"
    private func element(_ id: String) -> XCUIElement { app.descendants(matching: .any)[id] }
    private func tap(_ id: String) {
        let target = element(id)
        XCTAssertTrue(target.waitForExistence(timeout: 45), "IQA control missing: \(id)")
        for _ in 0..<8 { if target.isHittable { break }; app.swipeUp() }
        target.tap()
    }
    private func total() {
        let e = element("estimate")
        XCTAssertTrue(e.waitForExistence(timeout: 45))
        XCTAssertTrue(e.label.contains("240.00"), "IQA expected 2 × 120.00 EGP")
    }
    func testIndependentBasketPersistenceLanguageAndEditing() {
        continueAfterFailure = false
        app.launch()
        if !element("tab-discover").exists { app.navigationBars.buttons.firstMatch.tap() }
        tap("tab-discover")
        tap("meal-" + meal)
        tap("slot-0")
        tap("add-to-cart")
        tap("go-to-cart")
        tap("qty-inc-" + meal)
        total()
        print("IQA PASS: iOS native basket quantity and 240.00 EGP estimate")
        XCUIDevice.shared.press(.home)
        app.activate()
        total()
        print("IQA PASS: iOS background/foreground retains basket")
        app.terminate(); app.launch()
        tap("open-cart"); total()
        print("IQA PASS: iOS terminated-process basket restoration")
        app.navigationBars.buttons.firstMatch.tap()
        tap("tab-account")
        let arabic = app.buttons["العربية"]
        XCTAssertTrue(arabic.waitForExistence(timeout: 30)); arabic.tap()
        tap("tab-discover"); tap("open-cart")
        XCTAssertTrue(element("cart-lines").waitForExistence(timeout: 30))
        print("IQA PASS: iOS Arabic navigation keeps basket reachable")
        app.navigationBars.buttons.firstMatch.tap()
        tap("tab-account")
        let english = app.buttons["English"]
        XCTAssertTrue(english.waitForExistence(timeout: 30)); english.tap()
        tap("tab-discover"); tap("open-cart"); total()
        tap("remove-" + meal)
        XCTAssertTrue(app.staticTexts["Your cart is empty."].waitForExistence(timeout: 30))
        print("IQA PASS: iOS individual removal and empty state")
    }
}
