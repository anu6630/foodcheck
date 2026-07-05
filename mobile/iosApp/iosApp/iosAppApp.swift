import SwiftUI
import composeApp

@main
struct iosAppApp: App {
    init() {
        KoinInitKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
