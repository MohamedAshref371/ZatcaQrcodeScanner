import InvoiceUI
import SwiftUI
import UIKit

@main
struct InvoicePreviewApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @State private var sharedImports = InvoiceSharedImportRequests()

    var body: some Scene {
        WindowGroup {
            InvoiceScreen(sharedImports: sharedImports)
                .ignoresSafeArea()
                .onAppear { sharedImports.request() }
                .onChange(of: scenePhase) { phase in
                    if phase == .active { sharedImports.request() }
                }
        }
    }
}

private struct InvoiceScreen: UIViewControllerRepresentable {
    let sharedImports: InvoiceSharedImportRequests

    func makeUIViewController(context: Context) -> UIViewController {
        let host = NativeInvoiceScanner()
        let controller = InvoiceScreenControllerKt.invoiceScreenController(
            host: host, history: SQLiteInvoiceHistory(), sharedImports: sharedImports)
        host.presenter = controller
        return controller
    }

    func updateUIViewController(_ viewController: UIViewController, context: Context) {}
}
