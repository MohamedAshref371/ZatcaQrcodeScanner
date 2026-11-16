import Foundation

enum SharedInvoiceInbox {
    static let maxAttachments = 20
    static let maxFileBytes: Int64 = 64 * 1024 * 1024

    enum InboxError: Error { case unavailable, tooLarge }

    static func folder() throws -> URL {
        guard let identifier = Bundle.main.object(forInfoDictionaryKey: "InvoiceAppGroup") as? String,
              let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier) else {
            throw InboxError.unavailable
        }
        let folder = container.appendingPathComponent("InvoiceInbox", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder
    }

    static func pendingFiles() -> [URL] {
        guard let root = try? folder(), let batches = try? contents(root) else { return [] }
        return batches.filter { $0.lastPathComponent.hasPrefix("Ready-") }.flatMap { batch in
            (try? contents(batch)) ?? []
        }
    }

    static func removeProcessedFile(_ file: URL) {
        let manager = FileManager.default
        guard (try? manager.removeItem(at: file)) != nil else { return }
        let batch = file.deletingLastPathComponent()
        if (try? contents(batch).isEmpty) == true {
            try? manager.removeItem(at: batch)
        }
    }

    private static func contents(_ folder: URL) throws -> [URL] {
        try FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil, options: [.skipsHiddenFiles])
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
    }

    /// Copy while the provider's temporary file is still available.
    static func copyAttachment(from source: URL, to destination: URL) throws {
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        let input = try FileHandle(forReadingFrom: source)
        defer { try? input.close() }
        guard FileManager.default.createFile(atPath: destination.path, contents: nil) else {
            throw CocoaError(.fileWriteUnknown)
        }
        let output = try FileHandle(forWritingTo: destination)
        defer { try? output.close() }
        var size: Int64 = 0
        while let chunk = try input.read(upToCount: 64 * 1024), !chunk.isEmpty {
            size += Int64(chunk.count)
            guard size <= maxFileBytes else { throw InboxError.tooLarge }
            try output.write(contentsOf: chunk)
        }
    }
}
