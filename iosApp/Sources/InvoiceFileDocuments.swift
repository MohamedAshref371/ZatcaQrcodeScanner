import CoreGraphics
import Foundation
import ImageIO
import InvoiceUI
import UIKit
import UniformTypeIdentifiers

/// Reads images and every PDF page, then commits each file as one SQLite transaction.
final class InvoiceFileDocuments: NSObject, UIDocumentPickerDelegate {
    private let queue = DispatchQueue(label: "com.poetscorner.invoiceqr.file-import", qos: .userInitiated)
    private let cancelLock = NSLock()
    private var cancelled = false
    private var observer: InvoiceFilesObserver?

    func open(from presenter: UIViewController?, observer: InvoiceFilesObserver) {
        guard self.observer == nil, let presenter, presenter.presentedViewController == nil else {
            observer.complete(result_: result(saveFailed: 1))
            return
        }
        begin(observer)
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.image, .pdf], asCopy: false)
        picker.allowsMultipleSelection = true
        picker.delegate = self
        picker.modalPresentationStyle = .fullScreen
        presenter.present(picker, animated: true)
    }

    func importSharedFiles(observer: InvoiceFilesObserver) -> Bool {
        guard self.observer == nil else { return false }
        let files = SharedInvoiceInbox.pendingFiles()
        guard !files.isEmpty else { return false }
        begin(observer)
        process(files, fromInbox: true)
        return true
    }

    private func begin(_ observer: InvoiceFilesObserver) {
        cancelLock.lock()
        cancelled = false
        cancelLock.unlock()
        self.observer = observer
    }

    func cancel() {
        cancelLock.lock()
        cancelled = true
        cancelLock.unlock()
    }

    private var isCancelled: Bool {
        cancelLock.lock()
        defer { cancelLock.unlock() }
        return cancelled
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        finish(result(cancelled: true))
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard !urls.isEmpty else { return finish(result(cancelled: true)) }
        process(urls, fromInbox: false)
    }

    private func process(_ urls: [URL], fromInbox: Bool) {
        let engine = InvoiceQrReader.selected
        let zxing = InvoiceQrReader.zxingSettings
        queue.async { [self] in
            let reader = InvoiceQrReader(engine: engine, zxing: zxing)
            var added: Int32 = 0
            var duplicates: Int32 = 0
            var needsReview: Int32 = 0
            var invalidQr: Int32 = 0
            var unreadable: Int32 = 0
            var saveFailed: Int32 = 0
            var processed: Int32 = 0
            var seen = Set<String>()
            let history = SQLiteInvoiceHistory()

            for (index, source) in urls.enumerated() {
                if isCancelled { break }
                let staged = fromInbox ? source : FileManager.default.temporaryDirectory
                    .appendingPathComponent("InvoiceFile-\(UUID().uuidString)")
                var removeInboxFile = false
                defer {
                    if !fromInbox {
                        try? FileManager.default.removeItem(at: staged)
                    } else if removeInboxFile {
                        SharedInvoiceInbox.removeProcessedFile(source)
                    }
                }
                do {
                    notify(file: Int32(index + 1), total: Int32(urls.count), page: 0, pages: 0)
                    if !fromInbox { try stage(source, at: staged) }
                    let declared = (try? source.resourceValues(forKeys: [.contentTypeKey]))?.contentType
                    let extensionType = UTType(filenameExtension: source.pathExtension)
                    guard let type = [declared, extensionType].compactMap({ $0 }).first(where: {
                        $0.conforms(to: .image) || $0.conforms(to: .pdf)
                    }) else {
                        throw ImportError.unreadable
                    }
                    var foundValid = false
                    var candidates: [InvoiceImportCandidate] = []
                    var fileKeys = Set<String>()
                    let accept: (String) -> Void = { raw in
                        let candidate = InvoiceImportCandidate(raw: raw)
                        if candidate.valid { foundValid = true }
                        guard candidate.valid, !seen.contains(candidate.normalizedPayload),
                              fileKeys.insert(candidate.normalizedPayload).inserted else { return }
                        candidates.append(candidate)
                    }
                    if type.conforms(to: .pdf) {
                        try scanPdf(staged, reader: reader, file: Int32(index + 1), total: Int32(urls.count), accept: accept)
                    } else {
                        try scanImage(staged, reader: reader, accept: accept)
                    }
                    if isCancelled { throw CancellationError() }
                    removeInboxFile = true
                    if candidates.isEmpty {
                        if !foundValid { invalidQr += 1 }
                    } else {
                        do {
                            let counts = try history.importCandidates(candidates, cancelled: { self.isCancelled })
                            added += Int32(counts.added)
                            duplicates += Int32(counts.duplicates)
                            needsReview += Int32(counts.needsReview)
                            seen.formUnion(fileKeys)
                        } catch is CancellationError {
                            removeInboxFile = false
                            break
                        } catch {
                            removeInboxFile = false
                            saveFailed += Int32(candidates.count)
                        }
                    }
                    processed += 1
                } catch is CancellationError {
                    break
                } catch {
                    removeInboxFile = true
                    unreadable += 1
                    processed += 1
                }
            }
            let summary = InvoiceFilesResult(added: added, duplicates: duplicates, needsReview: needsReview,
                invalidQr: invalidQr, unreadable: unreadable, saveFailed: saveFailed,
                processedFiles: processed, totalFiles: Int32(urls.count),
                cancelled: isCancelled && processed < Int32(urls.count))
            DispatchQueue.main.async { self.finish(summary) }
        }
    }

    private func result(saveFailed: Int32 = 0, cancelled: Bool = false) -> InvoiceFilesResult {
        InvoiceFilesResult(added: 0, duplicates: 0, needsReview: 0, invalidQr: 0, unreadable: 0,
            saveFailed: saveFailed, processedFiles: 0, totalFiles: 0, cancelled: cancelled)
    }

    private func finish(_ result: InvoiceFilesResult) {
        let callback = observer
        observer = nil
        callback?.complete(result_: result)
    }

    private func notify(file: Int32, total: Int32, page: Int32, pages: Int32) {
        DispatchQueue.main.async { [weak self] in
            self?.observer?.progress(file: file, total: total, page: page, pages: pages)
        }
    }

    private enum ImportError: Error { case tooLarge, unreadable }

    private func stage(_ source: URL, at destination: URL) throws {
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        var readError: Error?
        var coordinationError: NSError?
        NSFileCoordinator(filePresenter: nil).coordinate(readingItemAt: source, options: [], error: &coordinationError) { url in
            do {
                let input = try FileHandle(forReadingFrom: url)
                defer { try? input.close() }
                guard FileManager.default.createFile(atPath: destination.path, contents: nil) else {
                    throw ImportError.unreadable
                }
                let output = try FileHandle(forWritingTo: destination)
                defer { try? output.close() }
                var total: Int64 = 0
                while let chunk = try input.read(upToCount: 64 * 1024), !chunk.isEmpty {
                    if isCancelled { throw CancellationError() }
                    total += Int64(chunk.count)
                    if total > SharedInvoiceInbox.maxFileBytes { throw ImportError.tooLarge }
                    try output.write(contentsOf: chunk)
                }
            } catch { readError = error }
        }
        if let readError { throw readError }
        if let coordinationError { throw coordinationError }
    }

    private func scanImage(_ url: URL, reader: InvoiceQrReader, accept: (String) -> Void) throws {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { throw ImportError.unreadable }
        for raw in try reader.read(source) { accept(raw) }
    }

    private func scanPdf(_ url: URL, reader: InvoiceQrReader, file: Int32, total: Int32, accept: (String) -> Void) throws {
        guard let document = CGPDFDocument(url as CFURL), document.numberOfPages > 0 else {
            throw ImportError.unreadable
        }
        for index in 1...document.numberOfPages {
            if isCancelled { throw CancellationError() }
            notify(file: file, total: total, page: Int32(index), pages: Int32(document.numberOfPages))
            guard let page = document.page(at: index) else { throw ImportError.unreadable }
            let bounds = page.getBoxRect(.mediaBox)
            guard bounds.width > 0, bounds.height > 0, bounds.width.isFinite, bounds.height.isFinite else {
                throw ImportError.unreadable
            }
            for edge in [1800, 3200] {
                if isCancelled { throw CancellationError() }
                let scale = CGFloat(edge) / max(bounds.width, bounds.height)
                let width = max(1, Int((bounds.width * scale).rounded()))
                let height = max(1, Int((bounds.height * scale).rounded()))
                guard let context = CGContext(data: nil, width: width, height: height,
                    bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(),
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { throw ImportError.unreadable }
                let target = CGRect(x: 0, y: 0, width: width, height: height)
                context.setFillColor(UIColor.white.cgColor)
                context.fill(target)
                context.concatenate(page.getDrawingTransform(.mediaBox, rect: target, rotate: 0, preserveAspectRatio: true))
                context.drawPDFPage(page)
                guard let image = context.makeImage() else { throw ImportError.unreadable }
                for raw in reader.read(image) { accept(raw) }
            }
        }
    }
}
