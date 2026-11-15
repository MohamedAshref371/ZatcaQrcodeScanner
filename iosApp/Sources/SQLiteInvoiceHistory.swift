import CryptoKit
import Foundation
import InvoiceUI
import SQLite3

final class SQLiteInvoiceHistory: InvoiceHistoryStore {
    private let storageFolder: URL?
    private var database: OpaquePointer?

    init(folder: URL? = nil) {
        storageFolder = folder
    }

    deinit {
        if let database { sqlite3_close(database) }
    }

    func save(raw: String, normalizedPayload: String, seller: String, vat: String, total: String,
              invoiceDay: String, checkedAt: Int64) -> InvoiceHistoryEntry? {
        let key = payloadKey(normalizedPayload)
        return try? withDatabase { db in
            let row = InvoiceBackupRow(raw: raw, checkedAt: checkedAt, invoiceNumber: "", category: 0, note: "")
            let inserted = try insert(row, key: key, seller: seller, vat: vat, total: total, invoiceDay: invoiceDay, into: db)
            if inserted {
                return InvoiceHistoryEntry(raw: raw, checkedAt: checkedAt, alreadySaved: false)
            }
            return try statement(db, "SELECT raw, checked_at FROM invoices WHERE payload_key = ?") { query in
                try bind(key, to: query, at: 1)
                return try readEntry(query, alreadySaved: true)
            }
        }
    }

    private func payloadKey(_ normalized: String) -> String {
        SHA256.hash(data: Data(normalized.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    private func insert(_ row: InvoiceBackupRow, key: String, seller: String, vat: String,
                        total: String, invoiceDay: String, into db: OpaquePointer) throws -> Bool {
        try statement(db, """
            INSERT INTO invoices(payload_key, raw, seller, vat, total, checked_at, seller_search, invoice_day,
                                 invoice_number, category, note)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(payload_key) DO NOTHING
            """) { query in
            try bind(key, to: query, at: 1)
            try bind(row.raw, to: query, at: 2)
            try bind(seller, to: query, at: 3)
            try bind(vat, to: query, at: 4)
            try bind(total, to: query, at: 5)
            try requireOK(sqlite3_bind_int64(query, 6, row.checkedAt))
            try bind(seller.lowercased(), to: query, at: 7)
            try bind(invoiceDay, to: query, at: 8)
            try bind(row.invoiceNumber, to: query, at: 9)
            try requireOK(sqlite3_bind_int(query, 10, row.category))
            try bind(row.note, to: query, at: 11)
            guard sqlite3_step(query) == SQLITE_DONE else { throw StorageError.sqlite }
            return sqlite3_changes(db) == 1
        }
    }

    func writeBackup(to file: FileHandle) throws {
        try withDatabase { db in
            try transaction(db, begin: "BEGIN") {
                let writer = InvoiceBackupWriter.shared
                try file.write(contentsOf: Data(writer.header().utf8))
                var cursor: Int64 = 0
                while true {
                    let rows = try statement(db, """
                        SELECT id, raw, checked_at, invoice_number, category, note
                        FROM invoices WHERE id > ? ORDER BY id LIMIT 50
                        """) { query in
                        try requireOK(sqlite3_bind_int64(query, 1, cursor))
                        var rows: [(Int64, InvoiceBackupRow)] = []
                        var status = sqlite3_step(query)
                        while status == SQLITE_ROW {
                            rows.append((sqlite3_column_int64(query, 0), InvoiceBackupRow(
                                raw: columnText(query, at: 1), checkedAt: sqlite3_column_int64(query, 2),
                                invoiceNumber: columnText(query, at: 3), category: sqlite3_column_int(query, 4),
                                note: columnText(query, at: 5)
                            )))
                            status = sqlite3_step(query)
                        }
                        guard status == SQLITE_DONE else { throw StorageError.sqlite }
                        return rows
                    }
                    if rows.isEmpty { break }
                    for (id, row) in rows {
                        try file.write(contentsOf: Data(writer.record(row: row, first: cursor == 0).utf8))
                        cursor = id
                    }
                }
                try file.write(contentsOf: Data(writer.footer().utf8))
            }
        }
    }

    func readBackup(from file: FileHandle) throws -> InvoiceBackupResult {
        try withDatabase { db in
            try transaction(db, begin: "BEGIN IMMEDIATE") {
                let input = BackupUTF8Input(file)
                let reader = InvoiceBackupReader(input: input)
                var added: Int32 = 0
                var duplicates: Int32 = 0
                while true {
                    let record: InvoiceBackupRecord?
                    do {
                        record = try reader.next()
                    } catch {
                        throw input.failure ?? InvoiceBackupFailure.invalid
                    }
                    if let failure = input.failure { throw failure }
                    guard let record else { break }
                    let inserted = try insert(record.row, key: payloadKey(record.normalizedPayload), seller: record.seller,
                                              vat: record.vat, total: record.total, invoiceDay: record.invoiceDay, into: db)
                    if inserted { added += 1 } else { duplicates += 1 }
                }
                return InvoiceBackupResult(status: .imported, added: added, duplicates: duplicates)
            }
        }
    }

    private func transaction<T>(_ db: OpaquePointer, begin: String, _ action: () throws -> T) throws -> T {
        try requireOK(sqlite3_exec(db, begin, nil, nil, nil))
        do {
            let value = try action()
            try requireOK(sqlite3_exec(db, "COMMIT", nil, nil, nil))
            return value
        } catch {
            sqlite3_exec(db, "ROLLBACK", nil, nil, nil)
            throw error
        }
    }

    func page(beforeId: Int64, limit: Int32, filter: InvoiceHistoryQuery) -> InvoiceHistoryPage? {
        return try? withDatabase { db in
            try statement(db, """
                SELECT id, seller, vat, total, checked_at FROM invoices
                WHERE id < ?1
                  AND (?3 = '' OR instr(seller_search, ?3) > 0 OR (?4 <> '' AND instr(vat, ?4) > 0))
                  AND ((?5 = '' AND ?6 = '') OR (invoice_day <> ''
                    AND (?5 = '' OR invoice_day >= ?5) AND (?6 = '' OR invoice_day <= ?6)))
                  AND checked_at >= ?7 AND checked_at < ?8
                ORDER BY id DESC LIMIT ?2
                """) { query in
                try requireOK(sqlite3_bind_int64(query, 1, beforeId))
                try requireOK(sqlite3_bind_int64(query, 2, Int64(limit) + 1))
                try bind(filter.seller.lowercased(), to: query, at: 3)
                try bind(filter.vat, to: query, at: 4)
                try bind(filter.invoiceFrom, to: query, at: 5)
                try bind(filter.invoiceTo, to: query, at: 6)
                try requireOK(sqlite3_bind_int64(query, 7, filter.checkedFrom))
                try requireOK(sqlite3_bind_int64(query, 8, filter.checkedBefore))
                var rows: [InvoiceHistorySummary] = []
                while true {
                    switch sqlite3_step(query) {
                    case SQLITE_ROW:
                        if rows.count == Int(limit) { return InvoiceHistoryPage(rows: rows, hasMore: true) }
                        rows.append(InvoiceHistorySummary(
                            id: sqlite3_column_int64(query, 0),
                            seller: columnText(query, at: 1),
                            vat: columnText(query, at: 2),
                            total: columnText(query, at: 3),
                            checkedAt: sqlite3_column_int64(query, 4)
                        ))
                    case SQLITE_DONE:
                        return InvoiceHistoryPage(rows: rows, hasMore: false)
                    default:
                        throw StorageError.sqlite
                    }
                }
            }
        }
    }

    func entry(id: Int64) -> InvoiceHistoryEntry? {
        return try? withDatabase { db in
            try statement(db, "SELECT raw, checked_at FROM invoices WHERE id = ?") { query in
                try requireOK(sqlite3_bind_int64(query, 1, id))
                return try readEntry(query, alreadySaved: false)
            }
        }
    }

    private enum StorageError: Error { case sqlite }

    private func withDatabase<T>(_ action: (OpaquePointer) throws -> T) throws -> T {
        if let database { return try action(database) }

        let manager = FileManager.default
        let support = try manager.url(for: .applicationSupportDirectory, in: .userDomainMask,
                                      appropriateFor: nil, create: true)
        var folder = support.appendingPathComponent("InvoiceHistory", isDirectory: true)
        #if targetEnvironment(simulator)
        if let testStore = ProcessInfo.processInfo.environment["INVOICE_UI_TEST_HISTORY"],
           let id = UUID(uuidString: testStore) {
            folder = manager.temporaryDirectory.appendingPathComponent("InvoiceHistory-\(id.uuidString)", isDirectory: true)
        }
        #endif
        if let storageFolder { folder = storageFolder }
        try manager.createDirectory(at: folder, withIntermediateDirectories: true)
        var resources = URLResourceValues()
        resources.isExcludedFromBackup = true
        try folder.setResourceValues(resources)

        var opened: OpaquePointer?
        let path = folder.appendingPathComponent("invoices.sqlite").path
        let status = sqlite3_open_v2(path, &opened, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE, nil)
        guard let opened else { throw StorageError.sqlite }
        do {
            try requireOK(status)
            try requireOK(sqlite3_busy_timeout(opened, 1000))
            try requireOK(sqlite3_exec(opened, """
                CREATE TABLE IF NOT EXISTS invoices (
                    id INTEGER PRIMARY KEY,
                    payload_key TEXT NOT NULL UNIQUE,
                    raw TEXT NOT NULL,
                    seller TEXT NOT NULL,
                    vat TEXT NOT NULL,
                    total TEXT NOT NULL,
                    checked_at INTEGER NOT NULL
                )
                """, nil, nil, nil))
            try migrate(opened)
        } catch {
            sqlite3_close(opened)
            throw error
        }
        database = opened
        return try action(opened)
    }

    private func migrate(_ db: OpaquePointer) throws {
        try transaction(db, begin: "BEGIN IMMEDIATE") {
            let version = try statement(db, "PRAGMA user_version") { query in
                guard sqlite3_step(query) == SQLITE_ROW else { throw StorageError.sqlite }
                return sqlite3_column_int(query, 0)
            }
            if version == 0 {
                try requireOK(sqlite3_exec(db, """
                    ALTER TABLE invoices ADD COLUMN seller_search TEXT NOT NULL DEFAULT '';
                    ALTER TABLE invoices ADD COLUMN invoice_day TEXT NOT NULL DEFAULT '';
                    """, nil, nil, nil))
                try backfillSearchFields(db)
            }
            if version < 2 {
                try requireOK(sqlite3_exec(db, """
                    ALTER TABLE invoices ADD COLUMN invoice_number TEXT NOT NULL DEFAULT '';
                    ALTER TABLE invoices ADD COLUMN category INTEGER NOT NULL DEFAULT 0;
                    ALTER TABLE invoices ADD COLUMN note TEXT NOT NULL DEFAULT '';
                    PRAGMA user_version = 2;
                    """, nil, nil, nil))
            }
        }
    }

    private func backfillSearchFields(_ db: OpaquePointer) throws {
        var afterId: Int64 = 0
        while true {
            let rows = try statement(db, """
                SELECT id, seller, raw FROM invoices WHERE id > ? ORDER BY id LIMIT 50
                """) { query in
                try requireOK(sqlite3_bind_int64(query, 1, afterId))
                var rows: [(id: Int64, seller: String, raw: String)] = []
                var status = sqlite3_step(query)
                while status == SQLITE_ROW {
                    rows.append((sqlite3_column_int64(query, 0), columnText(query, at: 1), columnText(query, at: 2)))
                    status = sqlite3_step(query)
                }
                guard status == SQLITE_DONE else { throw StorageError.sqlite }
                return rows
            }
            guard let last = rows.last else { return }
            try statement(db, "UPDATE invoices SET seller_search = ?, invoice_day = ? WHERE id = ?") { query in
                for row in rows {
                    try bind(row.seller.lowercased(), to: query, at: 1)
                    try bind(InvoiceHistoryKt.invoiceHistoryDay(raw: row.raw), to: query, at: 2)
                    try requireOK(sqlite3_bind_int64(query, 3, row.id))
                    guard sqlite3_step(query) == SQLITE_DONE else { throw StorageError.sqlite }
                    try requireOK(sqlite3_reset(query))
                }
            }
            afterId = last.id
        }
    }

    private func statement<T>(_ db: OpaquePointer, _ sql: String, _ action: (OpaquePointer) throws -> T) throws -> T {
        var query: OpaquePointer?
        try requireOK(sqlite3_prepare_v2(db, sql, -1, &query, nil))
        guard let query else { throw StorageError.sqlite }
        defer { sqlite3_finalize(query) }
        return try action(query)
    }

    private func bind(_ value: String, to query: OpaquePointer, at index: Int32) throws {
        let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
        try value.utf8CString.withUnsafeBufferPointer { bytes in
            try requireOK(sqlite3_bind_text(query, index, bytes.baseAddress, Int32(bytes.count - 1), transient))
        }
    }

    private func readEntry(_ query: OpaquePointer, alreadySaved: Bool) throws -> InvoiceHistoryEntry {
        guard sqlite3_step(query) == SQLITE_ROW else { throw StorageError.sqlite }
        return InvoiceHistoryEntry(raw: columnText(query, at: 0),
                                   checkedAt: sqlite3_column_int64(query, 1), alreadySaved: alreadySaved)
    }

    private func columnText(_ query: OpaquePointer, at index: Int32) -> String {
        let bytes = sqlite3_column_text(query, index)
        let length = Int(sqlite3_column_bytes(query, index))
        return String(decoding: UnsafeBufferPointer(start: bytes, count: length), as: UTF8.self)
    }

    private func requireOK(_ code: Int32) throws {
        guard code == SQLITE_OK else { throw StorageError.sqlite }
    }
}
