import Foundation
import InvoiceUI
import UIKit
import UniformTypeIdentifiers

enum InvoiceBackupFailure: Error {
    case invalid, tooLarge, file
}

/// Decode one UTF-8 chunk at a time, carrying a partial character across the read boundary.
final class BackupUTF8Input: InvoiceBackupInput {
    private let file: FileHandle
    private var pending = Data()
    private(set) var failure: InvoiceBackupFailure?

    init(_ file: FileHandle) {
        self.file = file
    }

    func readChunk() -> String? {
        while failure == nil {
            do {
                guard let bytes = try file.read(upToCount: 8192), !bytes.isEmpty else {
                    if !pending.isEmpty { failure = .invalid }
                    return nil
                }
                pending.append(bytes)
                if let text = decode(pending) {
                    pending.removeAll(keepingCapacity: true)
                    return text
                }
                for tail in 1...min(3, pending.count) {
                    if let text = decode(pending.dropLast(tail)) {
                        pending = Data(pending.suffix(tail))
                        if !text.isEmpty { return text }
                        break
                    }
                    if tail == min(3, pending.count) { failure = .invalid }
                }
            } catch {
                failure = .file
            }
        }
        return nil
    }

    private func decode(_ bytes: Data) -> String? {
        let text = String(decoding: bytes, as: UTF8.self)
        return text.utf8.elementsEqual(bytes) ? text : nil
    }
}

final class InvoiceBackupDocuments: NSObject, UIDocumentPickerDelegate {
    private let queue = DispatchQueue(label: "com.poetscorner.invoiceqr.backup", qos: .userInitiated)
    private weak var presenter: UIViewController?
    private var observer: InvoiceBackupObserver?
    private var exportFolder: URL?
    private var importing = false

    deinit {
        if let exportFolder { try? FileManager.default.removeItem(at: exportFolder) }
    }

    func exportBackup(from presenter: UIViewController?, observer: InvoiceBackupObserver) {
        guard begin(presenter: presenter, observer: observer) else { return }
        importing = false
        queue.async {
            do {
                let folder = try Self.makeStage()
                let stamp = Int(Date().timeIntervalSince1970)
                let url = folder.appendingPathComponent("InvoiceQR-Backup-\(stamp).json")
                do {
                    let file = try Self.openOutput(url)
                    defer { try? file.close() }
                    try SQLiteInvoiceHistory().writeBackup(to: file)
                    try file.synchronize()
                } catch {
                    try? FileManager.default.removeItem(at: folder)
                    throw error
                }
                DispatchQueue.main.async {
                    self.exportFolder = folder
                    let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
                    self.present(picker)
                }
            } catch {
                DispatchQueue.main.async { self.finish(error) }
            }
        }
    }

    func importBackup(from presenter: UIViewController?, observer: InvoiceBackupObserver) {
        guard begin(presenter: presenter, observer: observer) else { return }
        importing = true
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.json], asCopy: false)
        present(picker)
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        finish(.cancelled)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else { return finish(.cancelled) }
        guard importing else { return finish(.exported) }
        queue.async {
            do {
                let folder = try Self.makeStage()
                defer { try? FileManager.default.removeItem(at: folder) }
                let staged = folder.appendingPathComponent("import.json")
                try Self.stageInput(from: url, to: staged)
                let file = try FileHandle(forReadingFrom: staged)
                defer { try? file.close() }
                let result = try SQLiteInvoiceHistory().readBackup(from: file)
                DispatchQueue.main.async { self.finish(result) }
            } catch {
                DispatchQueue.main.async { self.finish(error) }
            }
        }
    }

    private func begin(presenter: UIViewController?, observer: InvoiceBackupObserver) -> Bool {
        guard self.observer == nil, let presenter, presenter.presentedViewController == nil else {
            observer.complete(result: InvoiceBackupResult(status: .failed, added: 0, duplicates: 0))
            return false
        }
        self.presenter = presenter
        self.observer = observer
        return true
    }

    private func present(_ picker: UIDocumentPickerViewController) {
        guard let presenter, presenter.presentedViewController == nil else { return finish(.failed) }
        picker.delegate = self
        picker.modalPresentationStyle = .fullScreen
        presenter.present(picker, animated: true)
    }

    private func finish(_ error: Error) {
        switch error as? InvoiceBackupFailure {
        case .invalid: finish(.invalid)
        case .tooLarge: finish(.oversized)
        default: finish(.failed)
        }
    }

    private func finish(_ status: InvoiceBackupStatus) {
        finish(InvoiceBackupResult(status: status, added: 0, duplicates: 0))
    }

    private func finish(_ result: InvoiceBackupResult) {
        if let exportFolder { try? FileManager.default.removeItem(at: exportFolder) }
        exportFolder = nil
        let callback = observer
        observer = nil
        callback?.complete(result: result)
    }

    private static func makeStage() throws -> URL {
        let manager = FileManager.default
        let url = manager.temporaryDirectory.appendingPathComponent("InvoiceBackup-\(UUID().uuidString)", isDirectory: true)
        try manager.createDirectory(at: url, withIntermediateDirectories: false)
        return url
    }

    private static func openOutput(_ url: URL) throws -> FileHandle {
        guard FileManager.default.createFile(atPath: url.path, contents: nil) else { throw InvoiceBackupFailure.file }
        return try FileHandle(forWritingTo: url)
    }

    private static func stageInput(from source: URL, to destination: URL) throws {
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        var ioError: Error?
        var coordinationError: NSError?
        NSFileCoordinator(filePresenter: nil).coordinate(readingItemAt: source, options: [], error: &coordinationError) { url in
            do {
                let input = try FileHandle(forReadingFrom: url)
                defer { try? input.close() }
                let output = try openOutput(destination)
                defer { try? output.close() }
                let limit = InvoiceBackupWriter.shared.maxBytes
                var total: Int64 = 0
                while let chunk = try input.read(upToCount: 8192), !chunk.isEmpty {
                    total += Int64(chunk.count)
                    guard total <= limit else { throw InvoiceBackupFailure.tooLarge }
                    try output.write(contentsOf: chunk)
                }
            } catch {
                ioError = error
            }
        }
        if let ioError { throw ioError }
        if let coordinationError { throw coordinationError }
    }
}
