import UIKit
import UniformTypeIdentifiers

final class InvoiceShareViewController: UIViewController {
    private let statusLabel = UILabel()
    private let button = UIButton(type: .system)
    private let spinner = UIActivityIndicatorView(style: .medium)
    private var task: Task<Void, Never>?
    private var started = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        view.semanticContentAttribute = InvoiceLanguagePreferences.usesArabic ? .forceRightToLeft : .forceLeftToRight
        statusLabel.textAlignment = .center
        statusLabel.font = .preferredFont(forTextStyle: .body)
        statusLabel.adjustsFontForContentSizeCategory = true
        statusLabel.numberOfLines = 0
        statusLabel.text = InvoiceLanguagePreferences.text("جارٍ تجهيز الفواتير…", "Preparing invoices…")
        button.setTitle(InvoiceLanguagePreferences.text("إلغاء", "Cancel"), for: .normal)
        button.addTarget(self, action: #selector(close), for: .touchUpInside)
        spinner.startAnimating()

        let stack = UIStackView(arrangedSubviews: [spinner, statusLabel, button])
        stack.axis = .vertical
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.layoutMarginsGuide.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: view.layoutMarginsGuide.trailingAnchor),
            stack.centerYAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerYAnchor)
        ])
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !started else { return }
        started = true
        let items = extensionContext?.inputItems.compactMap { $0 as? NSExtensionItem } ?? []
        let providers = items.flatMap { $0.attachments ?? [] }
        task = Task { await receive(providers) }
    }

    @objc private func close() {
        if let task {
            task.cancel()
            button.isEnabled = false
            statusLabel.text = InvoiceLanguagePreferences.text("جارٍ الإلغاء…", "Cancelling…")
        } else {
            extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
        }
    }

    private func receive(_ providers: [NSItemProvider]) async {
        defer {
            task = nil
            spinner.stopAnimating()
            button.setTitle(InvoiceLanguagePreferences.text("تم", "Done"), for: .normal)
            button.isEnabled = true
        }

        guard !providers.isEmpty, providers.count <= SharedInvoiceInbox.maxAttachments else {
            statusLabel.text = InvoiceLanguagePreferences.text("اختر من ملف واحد إلى ٢٠ صورة أو ملف PDF.", "Choose up to 20 images or PDF files.")
            return
        }

        do {
            let folder = try SharedInvoiceInbox.folder()
            let id = UUID().uuidString
            let staging = folder.appendingPathComponent(".Staging-\(id)", isDirectory: true)
            let ready = folder.appendingPathComponent("Ready-\(id)", isDirectory: true)
            try FileManager.default.createDirectory(at: staging, withIntermediateDirectories: false)
            defer { try? FileManager.default.removeItem(at: staging) }

            var received = 0
            for (index, provider) in providers.enumerated() {
                try Task.checkCancellation()
                let types = provider.registeredTypeIdentifiers.compactMap { UTType($0) }
                guard let type = types.first(where: { $0.conforms(to: .pdf) }) ??
                    types.first(where: { $0.conforms(to: .image) }) else { continue }
                statusLabel.text = InvoiceLanguagePreferences.text("جارٍ تجهيز الملف \(index + 1) من \(providers.count)…", "Preparing file \(index + 1) of \(providers.count)…")
                let suffix = type.conforms(to: .pdf) ? "pdf" : (type.preferredFilenameExtension ?? "jpg")
                let destination = staging.appendingPathComponent(String(format: "%02d", index))
                    .appendingPathExtension(suffix)
                do {
                    try await copy(provider, type: type, to: destination)
                    received += 1
                } catch {
                    try? FileManager.default.removeItem(at: destination)
                }
            }
            try Task.checkCancellation()
            guard received > 0 else {
                statusLabel.text = InvoiceLanguagePreferences.text("تعذّر تجهيز الملفات. اختر صورًا أو ملفات PDF بحجم لا يتجاوز ٦٤ ميغابايت لكل ملف.", "Could not prepare the files. Choose images or PDFs up to 64 MiB each.")
                return
            }
            try FileManager.default.moveItem(at: staging, to: ready)
            let skipped = providers.count - received
            statusLabel.text = InvoiceLanguagePreferences.text("تم تجهيز \(received) ملف للاستيراد. افتح قارئ فاتورة لمعالجتها.", "Files ready to import: \(received). Open Invoice QR Reader to process them.")
            if skipped > 0 { statusLabel.text?.append(InvoiceLanguagePreferences.text("\nتعذّر تجهيز \(skipped) ملف.", "\nFiles that could not be prepared: \(skipped).")) }
        } catch is CancellationError {
            extensionContext?.cancelRequest(withError: CocoaError(.userCancelled))
        } catch SharedInvoiceInbox.InboxError.unavailable {
            statusLabel.text = InvoiceLanguagePreferences.text("المشاركة غير متاحة في هذه النسخة. افتح قارئ فاتورة واختر الملف من داخل التطبيق.", "Sharing is unavailable in this build. Open Invoice QR Reader and choose the files there.")
        } catch {
            statusLabel.text = InvoiceLanguagePreferences.text("تعذّر تجهيز الملفات للاستيراد.", "Could not prepare the files for import.")
        }
    }

    private func copy(_ provider: NSItemProvider, type: UTType, to destination: URL) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            _ = provider.loadFileRepresentation(forTypeIdentifier: type.identifier) { url, error in
                do {
                    guard let url else { throw error ?? CocoaError(.fileReadUnknown) }
                    try SharedInvoiceInbox.copyAttachment(from: url, to: destination)
                    continuation.resume()
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }
}
