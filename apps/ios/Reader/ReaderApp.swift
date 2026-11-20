import ReaderShared
import SwiftUI
import UIKit

@main
struct ReaderApp: App {
    @UIApplicationDelegateAdaptor(ReaderAppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ComposeRootView()
                .ignoresSafeArea()
        }
    }
}

private final class ReaderAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        IosLibraryBackgroundTasksKt.registerLibraryBackgroundTasks()
        return true
    }
}

private struct ComposeRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
