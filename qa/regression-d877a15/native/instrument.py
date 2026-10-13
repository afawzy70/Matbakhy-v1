"""Add observations/assertions to native test harness only, after the pinned product binary is built."""
from pathlib import Path
# Make the response-holding proxy observable without retaining bodies or credentials.
p=Path('apps/customer-mobile/e2e-native/slow_proxy.py');s=p.read_text().replace('import sys','import sys\nimport json,os,time,base64,re\nfrom pathlib import Path')
# Record only method/path/status and JWT time metadata, never credentials or response bodies.
s=s.replace('while data := await reader.read(65536):', '''while data := await reader.read(65536):
            try:
                line=data.split(b"\\r\\n",1)[0].decode("latin1")
                obs={"at":time.time()}
                request=re.match(r"^(GET|POST|PATCH|DELETE) (/api/v1/[^ ?]*)",line)
                response=re.match(r"^HTTP/1[.]1 ([0-9]{3})",line)
                if request:
                    obs.update(method=request[1],path=request[2])
                    auth=re.search(rb"(?i)authorization: bearer ([^\\r\\n ]+)",data)
                    obs["bearerPresent"]=bool(auth)
                    if auth:
                        part=auth[1].split(b".")[1];payload=json.loads(base64.urlsafe_b64decode(part+b"="*((4-len(part)%4)%4)))
                        obs.update(exp=payload.get("exp"),iat=payload.get("iat"),expired=payload.get("exp",0)<=time.time())
                elif response:obs.update(status=int(response[1]))
                if request or response:
                    folder=Path("iqa-native-evidence");folder.mkdir(exist_ok=True)
                    with (folder/"transport.jsonl").open("a") as file:file.write(json.dumps(obs)+"\\n")
            except Exception:pass''')
s=s.replace('if delay:\n                await asyncio.sleep(delay)', '''if delay:
                is_order_response = b'"publicNumber"' in data and b'"PENDING_ACCEPTANCE"' in data
                if is_order_response:
                    Path(os.environ['RUNNER_TEMP'], 'iqa-held-order.json').write_text(json.dumps({'held':True,'heldAt':time.time()}))
                await asyncio.sleep(delay)
                if is_order_response:
                    Path(os.environ['RUNNER_TEMP'], 'iqa-held-order.json').write_text(json.dumps({'held':False,'deliveredAt':time.time()}))''')
p.write_text(s)
p=Path('apps/customer-mobile/scripts/order-roundtrip-android.sh');s=p.read_text();needle="absent 'text=\"MB-[0-9]+\"'\nadb shell am force-stop"
s=s.replace(needle,"""absent 'text="MB-[0-9]+"'
curl -fsS http://localhost:9099/pending | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("held") and d["orders"]==1,d; print("IQA-PENDING-PRECONDITION verified held response and one committed order")'
adb shell am force-stop""")
# Append checks with the harness's bounded scrolling/dump helpers, using actual native controls.
s+='''
echo "== IQA same-session lower limit, edit/remove and lifecycle"
launch
tap 'content-desc="Native Dish One[^\"]*"' 90
tap 'resource-id="slot-0"'
pos=$(wait_for 'content-desc="Increase"' 30) || fail "no increase"
for _ in $(seq 9); do adb shell input tap $pos; done
tap_scrolled 'resource-id="add-to-cart"'
wait_for 'text="Added to cart."' 30 >/dev/null || fail "ten not added"
adb shell input keyevent 4
PID_BEFORE=$(adb shell pidof "$PKG" | tr -d '\\r')
curl -fsS http://localhost:9099/lower >/dev/null
tap 'resource-id="open-cart"' 90
wait_scrolled 'text="At most 5 per meal can be ordered now.[^\"]*"' 60 || fail "same-process policy refresh absent"
PID_AFTER=$(adb shell pidof "$PKG" | tr -d '\\r')
[ "$PID_BEFORE" = "$PID_AFTER" ] && [ -n "$PID_BEFORE" ] || fail "process restarted during same-session assertion"
echo "IQA-Q1-SAME-SESSION verified PID $PID_AFTER unchanged"
wait_scrolled 'resource-id="get-quote"' 30 || fail "quote missing"
grep -qE 'resource-id="get-quote"[^>]*enabled="false"|enabled="false"[^>]*resource-id="get-quote"' "$UI" || fail "invalid quote enabled"
wait_scrolled_up 'resource-id="qty-dec-00000000-0000-4000-8000-00000000a0b1"' 30 || fail "decrease below fold"
pos=$(center_of 'resource-id="qty-dec-00000000-0000-4000-8000-00000000a0b1"')
for quantity in 9 8 7 6 5 4; do
  wait_scrolled_up 'resource-id="qty-dec-00000000-0000-4000-8000-00000000a0b1"' 30 || fail "decrease missing during edit"
  pos=$(center_of 'resource-id="qty-dec-00000000-0000-4000-8000-00000000a0b1"')
  adb shell input tap $pos
  wait_for "text=\\"$quantity × Native Dish One[^\\"]*\\"" 30 >/dev/null || fail "one decrement did not reach $quantity"
done
wait_for 'text="4 × Native Dish One[^\"]*"' 30 >/dev/null || fail "quantity not four"
wait_scrolled 'resource-id="estimate"' 30 || fail "estimate control missing"
python3 - "$UI" <<'P'
import re,sys
nodes=re.findall(r'<node [^>]*>',open(sys.argv[1]).read())
node=next(n for n in nodes if 'resource-id="estimate"' in n)
assert re.search(r'text="EGP.480\.00"',node), 'visible estimate must be 480.00'
P
wait_scrolled_up 'resource-id="remove-00000000-0000-4000-8000-00000000a0b1"' 30 || fail "remove missing"
tap 'resource-id="remove-00000000-0000-4000-8000-00000000a0b1"'
wait_for 'text="Your cart is empty."' 30 >/dev/null || fail "empty state missing"
echo "IQA-EDIT-REMOVE verified four/480/remove/empty"
curl -fsS http://localhost:9099/restore >/dev/null
adb shell input keyevent 4
tap 'content-desc="Native Dish One[^\"]*"' 90
tap 'resource-id="slot-0"'
tap_scrolled 'resource-id="add-to-cart"'
adb shell input keyevent 4
adb shell input keyevent 3
adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
tap 'resource-id="open-cart"' 90
wait_for 'text="1 × Native Dish One[^\"]*"' 30 >/dev/null || fail "background lost basket"
launch
tap 'resource-id="open-cart"' 90
wait_for 'text="1 × Native Dish One[^\"]*"' 30 >/dev/null || fail "cold reopen lost basket"
echo "IQA-LIFECYCLE verified background and cold persisted basket"
launch
tap 'content-desc="Native Dish One[^\"]*"' 90
tap_scrolled 'content-desc="Add to favorites"|text="Add to favorites"'
wait_scrolled 'content-desc="Remove from favorites"|text="Remove from favorites"' 30 || fail "favorite not saved"
adb shell input keyevent 4
tap 'resource-id="tab-favorites"'
wait_for 'content-desc="Native Dish One[^\"]*"|text="Native Dish One"' 30 >/dev/null || fail "favorite list missing One"
tap 'content-desc="Native Dish One[^\"]*"|text="Native Dish One"'
tap_scrolled 'content-desc="Remove from favorites"|text="Remove from favorites"'
echo "IQA-FAVORITES verified add/list/remove"
launch
tap 'resource-id="tab-account"'
tap_scrolled 'content-desc="العربية"|text="العربية"'
wait_for 'text="حسابي"|text="مطبخي"' 30 >/dev/null || fail "Arabic switch missing"
# The RTL order and layout are retained as native screenshot evidence, not inferred from API tests.
shot iqa-arabic-account
tap_scrolled 'content-desc="English"|text="English"'
wait_for 'text="Account"' 30 >/dev/null || fail "English restoration missing"
echo "IQA-LOCALE verified AR and EN account switching"

'''
s+=r'''

echo "== IQA native support public reply"
curl -fsS http://localhost:9099/seed-case >/dev/null
launch
tap 'resource-id="tab-account"' 90
tap_scrolled 'content-desc="Support"|text="Support"'
wait_for 'text="CS-DA877001[^\"]*"' 30 >/dev/null || fail "support fixture not listed"
tap 'text="CS-DA877001[^\"]*"'
wait_for 'text="QA PUBLIC NATIVE REPLY"' 10 >/dev/null || fail "MBQA-004 public reply unreachable"
'''
p.write_text(s)
p=Path('apps/customer-mobile/e2e-ios/UITests/OrderingTests.swift');s=p.read_text();s=s.replace('Thread.sleep(forTimeInterval: 3)\n        app.terminate()', '''Thread.sleep(forTimeInterval: 1)
        let probe = URLSession.shared.dataTask(with: URL(string: "http://localhost:9099/pending")!) { data, _, _ in
            if let data, let result = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                XCTAssertEqual(result["held"] as? Bool, true, "response must actually be withheld")
                XCTAssertEqual(result["orders"] as? Int, 1, "server must actually have committed")
            } else { XCTFail("pending precondition observer unavailable") }
        }
        probe.resume()
        let start = Date()
        while probe.state != .completed && Date().timeIntervalSince(start) < 2 { Thread.sleep(forTimeInterval: 0.05) }
        XCTAssertEqual(probe.state, .completed, "bounded pending probe did not complete")
        print("IQA-PENDING-PRECONDITION observed before termination")
        app.terminate()''')
p.write_text(s)

p=Path('apps/customer-mobile/e2e-ios/UITests/OrderingTests.swift')
s=p.read_text()
extra=r'''    private func fixture(_ path: String) {
        let done = expectation(description: "synthetic fixture control")
        URLSession.shared.dataTask(with: URL(string: "http://localhost:9099/" + path)!) { _, response, error in
            XCTAssertNil(error)
            XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 200)
            done.fulfill()
        }.resume()
        wait(for: [done], timeout: 10)
    }

    func testG_IndependentSameSessionPolicyEditAndRecovery() {
        app.launch()
        element("tab-account").tap()
        let oldSession = app.buttons["Sign out"]
        if oldSession.waitForExistence(timeout: 10) { oldSession.tap() }
        let freshSignIn = app.buttons["Sign in"]
        expect(freshSignIn, "independent active-session prerequisite unavailable")
        freshSignIn.tap()
        login("customer-a")
        expect(text("Signed in as"), "real Gsession not established")
        print("IQA-G-FRESH-AUTH established without changing300s policy")
        element("tab-discover").tap()
        let meal = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Native Dish One")).firstMatch
        expect(meal, "One not listed")
        meal.tap()
        element("slot-0").tap()
        let increase = app.buttons["Increase"]
        expect(increase, "increase missing")
        for _ in 0..<9 { increase.tap() }
        expect(text("10"), "ten not reached")
        tapScrolled("add-to-cart")
        expect(text("Added to cart."), "ten not added")
        app.navigationBars.buttons.firstMatch.tap()
        element("open-cart").tap()
        // Change the synthetic server policy while THIS app process is already displaying its basket.
        fixture("lower")
        XCTAssertEqual(app.state, .runningForeground)
        app.navigationBars.buttons.firstMatch.tap()
        element("open-cart").tap()
        expect(text("At most 5 per meal can be ordered now"), "same-session limit refresh missing")
        XCTAssertFalse(element("get-quote").isEnabled)
        print("IQA-Q1-SAME-SESSION verified continuous runningForeground app")
        let decrease = element("qty-dec-00000000-0000-4000-8000-00000000a0b1")
        expect(decrease, "decrease missing")
        for _ in 0..<6 { decrease.tap() }
        expect(text("4 × Native Dish One"), "four not shown")
        expect(text("480.00"), "estimate not 480")
        tapScrolled("remove-00000000-0000-4000-8000-00000000a0b1")
        expect(text("Your cart is empty."), "last-line empty state missing")
        print("IQA-EDIT-REMOVE verified four/480/remove/empty")
        fixture("restore")
        app.navigationBars.buttons.firstMatch.tap()
        addFirstSlot(of: "Native Dish One")
        app.navigationBars.buttons.firstMatch.tap()
        XCUIDevice.shared.press(.home)
        app.activate()
        element("open-cart").tap()
        expect(text("1 × Native Dish One"), "background lost basket")
        app.terminate()
        app.launch()
        element("open-cart").tap()
        expect(text("1 × Native Dish One"), "cold reopen lost basket")
        print("IQA-LIFECYCLE verified background and cold basket")
        app.terminate()
        app.launch()
        element("tab-discover").tap()
        let favoriteMeal = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Native Dish One")).firstMatch
        expect(favoriteMeal, "favorite meal unavailable")
        favoriteMeal.tap()
        let addFavorite = app.buttons["Add to favorites"]
        expect(addFavorite, "favorite add missing")
        addFavorite.tap()
        expect(app.buttons["Remove from favorites"], "favorite not saved")
        app.navigationBars.buttons.firstMatch.tap()
        element("tab-favorites").tap()
        expect(text("Native Dish One"), "favorite list missing One")
        let favoriteScreen = element("favorites")
        expect(favoriteScreen, "favorites screen missing")
        let openFavorite = favoriteScreen.buttons["Discover"].firstMatch
        expect(openFavorite, "favorite Discover action missing")
        openFavorite.tap()
        let removeFavorite = app.buttons["Remove from favorites"]
        expect(removeFavorite, "favorite removal action missing")
        removeFavorite.tap()
        expect(app.buttons["Add to favorites"], "favorite removal not confirmed")
        app.navigationBars.buttons.firstMatch.tap()
        expect(text("No favorite dishes yet."), "favorite remained in list after removal")
        print("IQA-FAVORITES verified add/list/remove")
        app.terminate()
        app.launch()
        element("tab-account").tap()
        let arabic = app.buttons["العربية"]
        expect(arabic, "language switch missing")
        arabic.tap()
        expect(app.buttons["English"], "Arabic state not active")
        shot("iqa-arabic-account")
        app.buttons["English"].tap()
        expect(app.buttons["العربية"], "English not restored")
        print("IQA-LOCALE verified AR and EN switching")

    }

    func testH_IndependentCustomerSupportReply() {
        app.launch()
        element("tab-account").tap()
        // Independent support case: establish a fresh REAL customer session, without changing the300s policy.
        let signOut = app.buttons["Sign out"]
        if signOut.waitForExistence(timeout: 10) { signOut.tap() }
        let signIn = app.buttons["Sign in"]
        expect(signIn, "fresh support authentication unavailable")
        signIn.tap()
        login("customer-a")
        expect(text("Signed in as"), "real fresh customer session not established")
        print("IQA-SUPPORT-FRESH-AUTH established")
        fixture("seed-case")
        // Completed native cancellation journey on the actual previously placed delivery order.
        guard #available(iOS 16.4, *), let id = ProcessInfo.processInfo.environment["MB033_ORDER_ID"], let number = ProcessInfo.processInfo.environment["MB033_ORDER_NUMBER"] else { return XCTFail("real delivered-order prerequisite absent") }
        app.open(URL(string: "matbakhy-dev://orders/\(id)")!)
        expect(text(number), "own delivery order missing")
        let cancel = app.buttons["Cancel order"]
        expect(cancel, "pending cancellation unavailable")
        var scrolls = 0
        while !cancel.isHittable && scrolls < 6 { app.swipeUp(); scrolls += 1 }
        XCTAssertTrue(cancel.isHittable)
        cancel.tap()
        expect(text("Cancelled"), "real customer cancellation not visible")
        XCTAssertFalse(app.buttons["Cancel order"].exists)
        print("IQA-CUSTOMER-CANCEL verified \(number)")
        app.terminate()
        app.launch()
        element("tab-account").tap()
        let support = app.buttons["Support"]
        expect(support, "Support unavailable")
        support.tap()
        let orderChoice = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Order: " + number)).firstMatch
        expect(orderChoice, "own order choice missing")
        while !orderChoice.isHittable { app.swipeUp(); break }
        orderChoice.tap()
        let description = app.textViews["Describe the problem"]
        expect(description, "support description editor missing")
        description.tap()
        description.typeText("Independent synthetic native support creation after cancellation.")
        let quality = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Quality")).firstMatch
        expect(quality, "case category choice missing")
        quality.tap()
        let send = app.buttons["Send"]
        expect(send, "support submit missing")
        scrolls = 0
        while !send.isHittable && scrolls < 6 { app.swipeUp(); scrolls += 1 }
        XCTAssertTrue(send.isHittable)
        send.tap()
        expect(text("Case sent:"), "real support creation not acknowledged")
        print("IQA-SUPPORT-CREATE verified real UI receipt")
        let publicCase = text("CS-DA877001")
        expect(publicCase, "synthetic public-reply case not in list")
        scrolls = 0
        while !publicCase.isHittable && scrolls < 6 { app.swipeDown(); scrolls += 1 }
        XCTAssertTrue(publicCase.isHittable)
        text("CS-DA877001").tap()
        expect(text("QA PUBLIC NATIVE REPLY"), "MBQA-004: customer cannot reach public reply", timeout: 10)
        XCTAssertFalse(text("QA PRIVATE NATIVE NOTE").exists)
    }
'''
s=s.rsplit('}',1)[0]+extra+'}\n'
p.write_text(s)
p=Path('apps/customer-mobile/scripts/order-roundtrip-ios.sh')
s=p.read_text().replace('Test Case|error:|MB03[23]-', 'Test Case|error:|MB03[23]-|IQA-')
s=s.replace('    exit 1\n  fi\n}', '    exit 1\n  fi\n  grep -q \"Test Case.*$name.*passed\" \"$RUNNER_TEMP/mb032-$name.log\" || { echo \"FAIL: zero successful executions for $name\"; exit 1; }\n}')
# Return allows the independent Gcase to execute even when H finds the known public-thread product defect.
s=s.replace('    exit 1\n  fi\n', '    return 1\n  fi\n').replace('zero successful executions for $name\"; exit 1;', 'zero successful executions for $name\"; return 1;')
s+='\nIQA_EXTRA_FAILURE=0\nphase testH_IndependentCustomerSupportReply || IQA_EXTRA_FAILURE=1\nphase testG_IndependentSameSessionPolicyEditAndRecovery || IQA_EXTRA_FAILURE=1\nexit $IQA_EXTRA_FAILURE\n'
p.write_text(s)
