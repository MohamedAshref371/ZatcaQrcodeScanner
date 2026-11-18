import SwiftUI
import Shared

@main
struct InvoiceQRApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeHost().ignoresSafeArea(.all)
        }
    }
}

struct ComposeHost: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let info = Bundle.main.infoDictionary
        return MainViewControllerKt.MainViewController(
            version: info?["CFBundleShortVersionString"] as? String ?? "1.2.7",
            privacyPolicyUrl: info?["PrivacyPolicyURL"] as? String ?? "https://example.com/privacy",
            storeUrl: nil)
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
