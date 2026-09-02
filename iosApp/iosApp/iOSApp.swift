import SwiftUI
import BackgroundTasks
import Shared

@main
struct iOSApp: App {
    init() {
        // The BGTask handler must be registered before anything submits a request,
        // and initKoin() submits the first one — so register first.
        registerBackgroundSync()
        KoinInitKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }

    private func registerBackgroundSync() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: IosSyncBridgeKt.IOS_SYNC_TASK_ID,
            using: nil
        ) { task in
            let cancel = IosSyncBridgeKt.startBackgroundSync { success in
                task.setTaskCompleted(success: success.boolValue)
            }
            task.expirationHandler = { cancel() }
        }
    }
}
