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

    func save(raw: String, normalizedPayload: String, seller: String, vat: String, total: String, tax: String = "",
              invoiceDay: String, checkedAt: Int64) -> InvoiceHistoryEntry? {
        let key = payloadKey(normalizedPayload)
        return try? withDatabase { db in
            let row = InvoiceBackupRow(raw: raw, checkedAt: checkedAt, invoiceNumber: "", category: 0, note: "")
            let inserted = try insert(row, key: key, seller: seller, vat: vat, total: total, tax: tax, invoiceDay: invoiceDay, into: db)
            if inserted {
                return InvoiceHistoryEntry(id: sqlite3_last_insert_rowid(db), raw: raw, checkedAt: checkedAt,
                    alreadySaved: false, annotation: InvoiceAnnotationFields(number: "", category: 0, note: ""))
            }
            return try statement(db, """
                SELECT id, raw, checked_at, invoice_number, category, note FROM invoices WHERE payload_key = ?
                """) { query in
                try bind(key, to: query, at: 1)
                return try readEntry(query, alreadySaved: true)
            }
        }
    }

    /// Commit one selected file at a time. An interrupted file rolls back all of its inserts.
    func importCandidates(_ candidates: [InvoiceImportCandidate], cancelled: () -> Bool) throws -> (added: Int, duplicates: Int, needsReview: Int) {
        try withDatabase { db in
            try transaction(db, begin: "BEGIN IMMEDIATE") {
                var added = 0
                var duplicates = 0
                var needsReview = 0
                for candidate in candidates {
                    if cancelled() { throw CancellationError() }
                    let row = InvoiceBackupRow(raw: candidate.raw,
                        checkedAt: Int64(Date().timeIntervalSince1970 * 1000),
                        invoiceNumber: "", category: 0, note: "")
                    if try insert(row, key: payloadKey(candidate.normalizedPayload), seller: candidate.seller,
                                  vat: candidate.vat, total: candidate.total, tax: candidate.tax,
                                  invoiceDay: candidate.invoiceDay, into: db) {
                        added += 1
                        if !candidate.passed { needsReview += 1 }
                    } else {
                        duplicates += 1
                    }
                }
                if cancelled() { throw CancellationError() }
                return (added, duplicates, needsReview)
            }
        }
    }

    private func payloadKey(_ normalized: String) -> String {
        SHA256.hash(data: Data(normalized.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    private func insert(_ row: InvoiceBackupRow, key: String, seller: String, vat: String,
                        total: String, tax: String, invoiceDay: String, into db: OpaquePointer) throws -> Bool {
        try statement(db, """
            INSERT INTO invoices(payload_key, raw, seller, vat, total, checked_at, seller_search, invoice_day,
                                 invoice_number, category, note, tax)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(payload_key) DO NOTHING
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
            try bind(tax, to: query, at: 12)
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
                                              vat: record.vat, total: record.total, tax: record.tax,
                                              invoiceDay: record.invoiceDay, into: db)
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

    // The accumulator groups trimmed Kotlin strings. Use the same whitespace set for drill-down.
    private static let groupingWhitespace = "\u{0009}\u{000A}\u{000B}\u{000C}\u{000D}\u{001C}\u{001D}\u{001E}\u{001F}\u{0020}\u{00A0}\u{1680}\u{2000}\u{2001}\u{2002}\u{2003}\u{2004}\u{2005}\u{2006}\u{2007}\u{2008}\u{2009}\u{200A}\u{2028}\u{2029}\u{202F}\u{205F}\u{3000}"

    private static let statisticsPredicate: String = {
        var normalizedVat = "vat"
        for digits in ["٠١٢٣٤٥٦٧٨٩", "۰۱۲۳۴۵۶۷۸۹"] {
            for (index, digit) in digits.enumerated() {
                normalizedVat = "replace(\(normalizedVat), '\(digit)', '\(index)')"
            }
        }
        return """
            (:stats_seller = '' OR instr(seller_search, :stats_seller) > 0)
            AND (:stats_vat = '' OR instr(vat, :stats_vat) > 0)
            AND (:stats_excluded_vat = '' OR instr(\(normalizedVat), :stats_excluded_vat) = 0)
            """
    }()

    private static let historyGroupPredicate = """
        (:stats_seller_key IS NULL OR trim(seller, :stats_whitespace) = :stats_seller_key)
        AND (:stats_vat_key IS NULL OR trim(vat, :stats_whitespace) = :stats_vat_key)
        AND (:stats_unknown_date = 0 OR invoice_day = '')
        """

    private static let historyPredicate = """
        (?1 = '' OR instr(seller_search, ?1) > 0 OR (?2 <> '' AND instr(vat, ?2) > 0))
        AND ((?3 = '' AND ?4 = '') OR (invoice_day <> ''
          AND (?3 = '' OR invoice_day >= ?3) AND (?4 = '' OR invoice_day <= ?4)))
        AND checked_at >= ?5 AND checked_at < ?6
        AND (?7 < 0 OR category = ?7)
        AND (?8 = '' OR invoice_contains(invoice_number, ?8) OR invoice_contains(note, ?8))
        """

    func page(beforeId: Int64, limit: Int32, filter: InvoiceHistoryQuery) -> InvoiceHistoryPage? {
        return try? withDatabase { db in
            try statement(db, """
                SELECT id, seller, vat, total, checked_at, invoice_number, category, payload_key FROM invoices
                WHERE \(Self.historyPredicate) AND \(Self.statisticsPredicate) AND \(Self.historyGroupPredicate)
                    AND id < :history_before
                ORDER BY id DESC LIMIT :history_limit
                """) { query in
                try bindHistoryPage(query, beforeId: beforeId, limit: Int64(limit) + 1, filter: filter)
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
                            checkedAt: sqlite3_column_int64(query, 4),
                            number: columnText(query, at: 5),
                            category: sqlite3_column_int(query, 6),
                            identity: columnText(query, at: 7)
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

    /// Two passes in one read transaction keep the optional hash column and rows on one snapshot.
    func writeHistoryCsv(filter: InvoiceHistoryQuery, language: InvoiceLanguage, to file: FileHandle) throws {
        try withDatabase { db in
            try transaction(db, begin: "BEGIN") {
                let detector = InvoiceHistoryCsvDocument(hasHash: false, language: language)
                var cursor = Int64.max
                var hasHash = false
                while !hasHash {
                    let rows = try historyExportPage(db, beforeId: cursor, filter: filter)
                    guard let last = rows.last else { break }
                    hasHash = rows.contains { detector.hasHash(raw: $0.raw) }
                    cursor = last.id
                }
                let document = InvoiceHistoryCsvDocument(hasHash: hasHash, language: language)
                try file.write(contentsOf: Data(document.header().utf8))
                cursor = Int64.max
                var first = true
                while true {
                    let rows = try historyExportPage(db, beforeId: cursor, filter: filter)
                    guard let last = rows.last else { break }
                    for row in rows {
                        if !first { try file.write(contentsOf: Data("\r\n".utf8)) }
                        try file.write(contentsOf: Data(document.record(raw: row.raw, number: row.number,
                            category: row.category, note: row.note).utf8))
                        first = false
                    }
                    cursor = last.id
                }
            }
        }
    }

    private typealias CsvHistoryRow = (id: Int64, raw: String, number: String, category: Int32, note: String)

    private func historyExportPage(_ db: OpaquePointer, beforeId: Int64,
                                   filter: InvoiceHistoryQuery) throws -> [CsvHistoryRow] {
        try statement(db, """
            SELECT id, raw, invoice_number, category, note FROM invoices
            WHERE \(Self.historyPredicate) AND \(Self.statisticsPredicate) AND \(Self.historyGroupPredicate)
                AND id < :history_before
            ORDER BY id DESC LIMIT :history_limit
            """) { query in
            try bindHistoryPage(query, beforeId: beforeId, limit: 200, filter: filter)
            var rows: [CsvHistoryRow] = []
            var status = sqlite3_step(query)
            while status == SQLITE_ROW {
                rows.append((id: sqlite3_column_int64(query, 0), raw: columnText(query, at: 1),
                    number: columnText(query, at: 2), category: sqlite3_column_int(query, 3),
                    note: columnText(query, at: 4)))
                status = sqlite3_step(query)
            }
            guard status == SQLITE_DONE else { throw StorageError.sqlite }
            return rows
        }
    }

    private func bindHistoryPage(_ query: OpaquePointer, beforeId: Int64, limit: Int64,
                                 filter: InvoiceHistoryQuery) throws {
        try bindGroupFilter(query, filter: filter)
        try requireOK(sqlite3_bind_int64(query, sqlite3_bind_parameter_index(query, ":history_before"), beforeId))
        try requireOK(sqlite3_bind_int64(query, sqlite3_bind_parameter_index(query, ":history_limit"), limit))
    }

    private func bindGroupFilter(_ query: OpaquePointer, filter: InvoiceHistoryQuery) throws {
        try bindHistoryFilter(query, filter: filter)
        let statistics = filter.statistics
        try bindStatisticsFilter(statistics?.rows, to: query)
        try bindOptional(statistics?.sellerKey, to: query, named: ":stats_seller_key")
        try bindOptional(statistics?.vatKey, to: query, named: ":stats_vat_key")
        try bind(Self.groupingWhitespace, to: query, at: sqlite3_bind_parameter_index(query, ":stats_whitespace"))
        let missingDate = sqlite3_bind_parameter_index(query, ":stats_unknown_date")
        try requireOK(sqlite3_bind_int(query, missingDate, statistics?.missingInvoiceDate == true ? 1 : 0))
    }

    private func bindStatisticsFilter(_ filter: InvoiceStatisticsRowFilter?, to query: OpaquePointer) throws {
        try bind(filter?.seller.lowercased() ?? "", to: query, at: sqlite3_bind_parameter_index(query, ":stats_seller"))
        try bind(filter?.vat ?? "", to: query, at: sqlite3_bind_parameter_index(query, ":stats_vat"))
        try bind(filter?.excludedVat ?? "", to: query, at: sqlite3_bind_parameter_index(query, ":stats_excluded_vat"))
    }

    private func bindOptional(_ value: String?, to query: OpaquePointer, named name: String) throws {
        let index = sqlite3_bind_parameter_index(query, name)
        if let value {
            try bind(value, to: query, at: index)
        } else {
            try requireOK(sqlite3_bind_null(query, index))
        }
    }

    private func bindHistoryFilter(_ query: OpaquePointer, filter: InvoiceHistoryQuery) throws {
        try bind(filter.seller.lowercased(), to: query, at: 1)
        try bind(filter.vat, to: query, at: 2)
        try bind(filter.invoiceFrom, to: query, at: 3)
        try bind(filter.invoiceTo, to: query, at: 4)
        try requireOK(sqlite3_bind_int64(query, 5, filter.checkedFrom))
        try requireOK(sqlite3_bind_int64(query, 6, filter.checkedBefore))
        try requireOK(sqlite3_bind_int(query, 7, filter.category))
        try bind(filter.annotationTerm.lowercased(), to: query, at: 8)
    }

    func deleteInvoice(id: Int64, identity: String, checkedAt: Int64) -> Bool {
        (try? withDatabase { db in
            try statement(db, "DELETE FROM invoices WHERE id = ? AND payload_key = ? AND checked_at = ?") { query in
                try requireOK(sqlite3_bind_int64(query, 1, id))
                try bind(identity, to: query, at: 2)
                try requireOK(sqlite3_bind_int64(query, 3, checkedAt))
                guard sqlite3_step(query) == SQLITE_DONE else { throw StorageError.sqlite }
                return sqlite3_changes(db) == 1
            }
        }) ?? false
    }

    func previewDeletion(filter: InvoiceHistoryQuery) -> InvoiceHistoryDeletionPreview? {
        guard isBoundedPeriod(filter) else { return nil }
        return try? withDatabase { try deletionPreview($0, filter: filter) }
    }

    func deletePeriod(filter: InvoiceHistoryQuery, preview: InvoiceHistoryDeletionPreview) -> InvoiceHistoryDeletionResult {
        guard isBoundedPeriod(filter), preview.count > 0 else { return .failed }
        return (try? withDatabase { db in
            try transaction(db, begin: "BEGIN IMMEDIATE") {
                let current = try deletionPreview(db, filter: filter)
                guard preview.count == current.count, preview.token == current.token else { return .changed }
                try statement(db, "DELETE FROM invoices WHERE \(Self.historyPredicate)") { query in
                    try bindHistoryFilter(query, filter: filter)
                    guard sqlite3_step(query) == SQLITE_DONE,
                          sqlite3_changes64(db) == preview.count else { throw StorageError.sqlite }
                }
                return .deleted
            }
        }) ?? .failed
    }

    private func isBoundedPeriod(_ filter: InvoiceHistoryQuery) -> Bool {
        guard filter.statistics == nil else { return false }
        if filter.invoiceFrom.isEmpty && filter.invoiceTo.isEmpty {
            return filter.checkedFrom != .min && filter.checkedBefore != .max && filter.checkedFrom < filter.checkedBefore
        }
        return !filter.invoiceFrom.isEmpty && !filter.invoiceTo.isEmpty && filter.invoiceFrom <= filter.invoiceTo
    }

    private func deletionPreview(_ db: OpaquePointer, filter: InvoiceHistoryQuery) throws -> InvoiceHistoryDeletionPreview {
        try statement(db, "SELECT id, payload_key, checked_at FROM invoices WHERE \(Self.historyPredicate) ORDER BY id") { query in
            try bindHistoryFilter(query, filter: filter)
            var count: Int64 = 0
            var hash = SHA256()
            var status = sqlite3_step(query)
            while status == SQLITE_ROW {
                let id = sqlite3_column_int64(query, 0)
                let key = columnText(query, at: 1)
                let checkedAt = sqlite3_column_int64(query, 2)
                hash.update(data: Data("\(id):\(key):\(checkedAt)\n".utf8))
                count += 1
                status = sqlite3_step(query)
            }
            guard status == SQLITE_DONE else { throw StorageError.sqlite }
            let token = Data(hash.finalize()).base64EncodedString()
            return InvoiceHistoryDeletionPreview(count: count, token: token)
        }
    }

    func entry(id: Int64) -> InvoiceHistoryEntry? {
        return try? withDatabase { db in
            try statement(db, """
                SELECT id, raw, checked_at, invoice_number, category, note FROM invoices WHERE id = ?
                """) { query in
                try requireOK(sqlite3_bind_int64(query, 1, id))
                return try readEntry(query, alreadySaved: false)
            }
        }
    }

    func updateAnnotation(id: Int64, annotation: InvoiceAnnotationFields) -> Bool {
        (try? withDatabase { db in
            try statement(db, "UPDATE invoices SET invoice_number = ?, category = ?, note = ? WHERE id = ?") { query in
                try bind(annotation.number, to: query, at: 1)
                try requireOK(sqlite3_bind_int(query, 2, annotation.category))
                try bind(annotation.note, to: query, at: 3)
                try requireOK(sqlite3_bind_int64(query, 4, id))
                guard sqlite3_step(query) == SQLITE_DONE else { throw StorageError.sqlite }
                return sqlite3_changes(db) == 1
            }
        }) ?? false
    }

    func statistics(_ query: InvoiceStatisticsQuery) throws -> InvoiceStatisticsSnapshot {
        try withDatabase { db in
            let builder = InvoiceStatisticsBuilder(query: query)
            return try statement(db, """
                SELECT seller, vat, invoice_day, total, tax, category FROM invoices
                WHERE ((?1 = '' AND ?2 = '') OR (invoice_day <> ''
                    AND (?1 = '' OR invoice_day >= ?1) AND (?2 = '' OR invoice_day <= ?2)))
                    AND \(Self.statisticsPredicate)
                ORDER BY id
                """) { statement in
                try bind(query.from, to: statement, at: 1)
                try bind(query.to, to: statement, at: 2)
                try bindStatisticsFilter(query.filter, to: statement)
                var status = sqlite3_step(statement)
                while status == SQLITE_ROW {
                    builder.add(seller: columnText(statement, at: 0), vat: columnText(statement, at: 1),
                                invoiceDay: columnText(statement, at: 2), total: columnText(statement, at: 3),
                                tax: columnText(statement, at: 4), category: sqlite3_column_int(statement, 5))
                    status = sqlite3_step(statement)
                }
                guard status == SQLITE_DONE else { throw StorageError.sqlite }
                return builder.finish()
            }
        }
    }

    func breakdown(_ query: InvoiceGroupBreakdownQuery) throws -> InvoiceGroupBreakdownDisplay {
        try withDatabase { db in
            let builder = InvoiceGroupBreakdownBuilder(query: query)
            return try statement(db, """
                SELECT total, tax, category FROM invoices
                WHERE \(Self.historyPredicate) AND \(Self.statisticsPredicate) AND \(Self.historyGroupPredicate)
                """) { statement in
                try bindGroupFilter(statement, filter: query.filter)
                var status = sqlite3_step(statement)
                while status == SQLITE_ROW {
                    builder.add(total: columnText(statement, at: 0), tax: columnText(statement, at: 1),
                                category: sqlite3_column_int(statement, 2))
                    status = sqlite3_step(statement)
                }
                guard status == SQLITE_DONE else { throw StorageError.sqlite }
                return builder.finish()
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
            try requireOK(sqlite3_create_function_v2(opened, "invoice_contains", 2,
                SQLITE_UTF8 | SQLITE_DETERMINISTIC, nil, { context, count, values in
                    guard count == 2, let values,
                          let value = sqlite3_value_text(values[0]),
                          let term = sqlite3_value_text(values[1]) else {
                        sqlite3_result_int(context, 0)
                        return
                    }
                    let matches = String(cString: value).lowercased().contains(String(cString: term))
                    sqlite3_result_int(context, matches ? 1 : 0)
                }, nil, nil, nil))
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
            }
            if version < 2 {
                try requireOK(sqlite3_exec(db, """
                    ALTER TABLE invoices ADD COLUMN invoice_number TEXT NOT NULL DEFAULT '';
                    ALTER TABLE invoices ADD COLUMN category INTEGER NOT NULL DEFAULT 0;
                    ALTER TABLE invoices ADD COLUMN note TEXT NOT NULL DEFAULT '';
                    """, nil, nil, nil))
            }
            if version < 3 {
                try requireOK(sqlite3_exec(db, "ALTER TABLE invoices ADD COLUMN tax TEXT NOT NULL DEFAULT ''", nil, nil, nil))
                try backfillDerivedFields(db)
                try requireOK(sqlite3_exec(db, "PRAGMA user_version = 3", nil, nil, nil))
            }
        }
    }

    private func backfillDerivedFields(_ db: OpaquePointer) throws {
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
            try statement(db, "UPDATE invoices SET seller_search = ?, invoice_day = ?, tax = ? WHERE id = ?") { query in
                for row in rows {
                    let derived = InvoiceHistoryKt.invoiceHistoryDerivedFields(raw: row.raw)
                    try bind(row.seller.lowercased(), to: query, at: 1)
                    try bind(derived.day, to: query, at: 2)
                    try bind(derived.tax, to: query, at: 3)
                    try requireOK(sqlite3_bind_int64(query, 4, row.id))
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
        return InvoiceHistoryEntry(id: sqlite3_column_int64(query, 0), raw: columnText(query, at: 1),
            checkedAt: sqlite3_column_int64(query, 2), alreadySaved: alreadySaved,
            annotation: InvoiceAnnotationFields(number: columnText(query, at: 3),
                category: sqlite3_column_int(query, 4), note: columnText(query, at: 5)))
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
