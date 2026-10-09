import SwiftUI
import composeApp

@main
struct iosAppApp: App {
    init() {
        let defaults = UserDefaults.standard
        if let stored = defaults.string(forKey: "foodcheck.device_key"), stored.count >= 16 {
            BackendConfig.shared.deviceKey = stored
        }
        KoinInitKt.doInitKoin()
        defaults.set(BackendConfig.shared.ensureDeviceKey(), forKey: "foodcheck.device_key")
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
