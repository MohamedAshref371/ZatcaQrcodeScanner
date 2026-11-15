import Foundation
import InvoiceUI
import UIKit

/// Stages a complete CSV before handing its destination to the Files document picker.
final class InvoiceCsvDocuments: NSObject, UIDocumentPickerDelegate {
    private let queue = DispatchQueue(label: "com.poetscorner.invoiceqr.csv", qos: .userInitiated)
    private weak var presenter: UIViewController?
    private var observer: InvoiceCsvObserver?
    private var exportFolder: URL?

    deinit {
        if let exportFolder { try? FileManager.default.removeItem(at: exportFolder) }
    }

    func exportHistory(from presenter: UIViewController?, query: InvoiceHistoryQuery, observer: InvoiceCsvObserver) {
        export(from: presenter, name: "zatca-history.csv", observer: observer) { file in
            try SQLiteInvoiceHistory().writeHistoryCsv(filter: query, to: file)
        }
    }

    func exportStatistics(from presenter: UIViewController?, snapshot: InvoiceStatisticsSnapshot,
                          groupingLabel: String, net: Bool, observer: InvoiceCsvObserver) {
        export(from: presenter, name: "invoice-statistics.csv", observer: observer) { file in
            let document = InvoiceStatisticsCsvDocument(snapshot: snapshot, groupingLabel: groupingLabel, net: net)
            try file.write(contentsOf: Data(document.header().utf8))
            for index in 0..<document.groupCount {
                try file.write(contentsOf: Data(document.group(index: index).utf8))
            }
            try file.write(contentsOf: Data(document.summary().utf8))
        }
    }

    private func export(from presenter: UIViewController?, name: String, observer: InvoiceCsvObserver,
                        write: @escaping (FileHandle) throws -> Void) {
        guard self.observer == nil, let presenter, presenter.presentedViewController == nil else {
            observer.complete(status: .failed)
            return
        }
        self.presenter = presenter
        self.observer = observer
        queue.async {
            do {
                let folder = FileManager.default.temporaryDirectory
                    .appendingPathComponent("InvoiceCsv-\(UUID().uuidString)", isDirectory: true)
                try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: false)
                do {
                    let url = folder.appendingPathComponent(name)
                    guard FileManager.default.createFile(atPath: url.path, contents: nil) else {
                        throw CocoaError(.fileWriteUnknown)
                    }
                    let file = try FileHandle(forWritingTo: url)
                    defer { try? file.close() }
                    try write(file)
                    try file.synchronize()
                    DispatchQueue.main.async {
                        self.exportFolder = folder
                        let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
                        picker.delegate = self
                        picker.modalPresentationStyle = .fullScreen
                        guard let presenter = self.presenter, presenter.presentedViewController == nil else {
                            self.finish(.failed)
                            return
                        }
                        presenter.present(picker, animated: true)
                    }
                } catch {
                    try? FileManager.default.removeItem(at: folder)
                    throw error
                }
            } catch {
                DispatchQueue.main.async { self.finish(.failed) }
            }
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        finish(.cancelled)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        finish(urls.isEmpty ? .cancelled : .exported)
    }

    private func finish(_ status: InvoiceCsvStatus) {
        if let exportFolder { try? FileManager.default.removeItem(at: exportFolder) }
        exportFolder = nil
        let callback = observer
        observer = nil
        callback?.complete(status: status)
    }
}
