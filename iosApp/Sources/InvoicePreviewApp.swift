import InvoiceUI
import SwiftUI
import UIKit

@main
struct InvoicePreviewApp: App {
    var body: some Scene {
        WindowGroup {
            InvoiceScreen()
                .ignoresSafeArea()
        }
    }
}

private struct InvoiceScreen: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let host = NativeInvoiceScanner()
        let controller = InvoiceScreenControllerKt.invoiceScreenController(host: host)
        host.presenter = controller
        return controller
    }

    func updateUIViewController(_ viewController: UIViewController, context: Context) {}
}
