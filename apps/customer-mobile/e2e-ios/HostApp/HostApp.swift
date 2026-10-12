import SwiftUI

/// Host for the MB-031 UI-test bundle only; the tests drive the customer app by bundle identifier.
@main
struct HostApp: App {
    var body: some Scene {
        WindowGroup { Text("MB-031 UI test host") }
    }
}
