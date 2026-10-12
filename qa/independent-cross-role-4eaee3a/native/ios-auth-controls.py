"""QA-only UI harness: dismiss the observed system Save Password sheet; binary/product unchanged."""
from pathlib import Path
import os
root=Path(os.environ['GITHUB_WORKSPACE'])
p=root/'apps/customer-mobile/e2e-ios/UITests/SignInRoundTripTests.swift'
s=p.read_text()
old='        if element.waitForExistence(timeout: timeout) { return }'
new='''        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            for host in [app, authUI] {
                let save = host.staticTexts["Save Password?"]
                let decline = host.buttons["Not Now"]
                if save.exists && decline.exists {
                    decline.tap()
                    print("IQA: dismissed system Save Password sheet; no credential stored")
                }
            }
            if element.exists { return }
            Thread.sleep(forTimeInterval: 1)
        }'''
assert s.count(old)==1
p.write_text(s.replace(old,new))
print('QA test controls prepared; production binary/source unchanged; same element/timeouts/assertions.')
