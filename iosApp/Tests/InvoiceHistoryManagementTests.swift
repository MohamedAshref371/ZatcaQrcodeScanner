import Foundation
import InvoiceUI
import XCTest
@testable import InvoicePreview

final class InvoiceHistoryManagementTests: XCTestCase {
    func testCategoryAndAnnotationFiltersAlsoApplyToCSV() throws {
        try withHistory { history, folder in
            let first = try save("Alpha", day: "2026-10-20", checkedAt: 100, to: history)
            let second = try save("Beta", day: "2026-10-20", checkedAt: 200, to: history)
            XCTAssertTrue(history.updateAnnotation(id: first.id,
                annotation: InvoiceAnnotationFields(number: "INV-É21", category: 1, note: "Pièce de rechange")))
            XCTAssertTrue(history.updateAnnotation(id: second.id,
                annotation: InvoiceAnnotationFields(number: "INV-É21", category: 2, note: "Pièce de rechange")))

            let byNumber = query(category: 1, annotation: "inv-é21")
            let byNote = query(category: 1, annotation: "PIÈCE")
            XCTAssertEqual(history.page(beforeId: .max, limit: 10, filter: byNumber)?.rows.map(\.id), [first.id])
            XCTAssertEqual(history.page(beforeId: .max, limit: 10, filter: byNote)?.rows.map(\.id), [first.id])
            XCTAssertEqual(history.page(beforeId: .max, limit: 10, filter: query(category: 0))?.rows.count, 0)

            let url = folder.appendingPathComponent("filtered.csv")
            try Data().write(to: url)
            let file = try FileHandle(forWritingTo: url)
            try history.writeHistoryCsv(filter: byNote, language: .english, to: file)
            try file.close()
            let csv = try String(contentsOf: url, encoding: .utf8)
            XCTAssertTrue(csv.contains("Alpha"))
            XCTAssertFalse(csv.contains("Beta"))
        }
    }

    func testDeletionPreviewRejectsChangedMembershipAndKeepsOutOfRangeRows() throws {
        try withHistory { history, _ in
            _ = try save("Outside", day: "2026-10-21", checkedAt: 300, to: history)
            let first = try save("Alpha", day: "2026-10-20", checkedAt: 100, to: history)
            let range = query(from: "2026-10-20", to: "2026-10-20")
            let preview = try XCTUnwrap(history.previewDeletion(filter: range))
            XCTAssertEqual(preview.count, 1)

            let row = try XCTUnwrap(history.page(beforeId: .max, limit: 10, filter: range)?.rows.first)
            XCTAssertTrue(history.deleteInvoice(id: first.id, identity: row.identity, checkedAt: row.checkedAt))
            let replacement = try save("Beta", day: "2026-10-20", checkedAt: 100, to: history)
            XCTAssertEqual(replacement.id, first.id)
            XCTAssertFalse(history.deleteInvoice(id: row.id, identity: row.identity, checkedAt: row.checkedAt))
            XCTAssertEqual(history.deletePeriod(filter: range, preview: preview), .changed)
            XCTAssertNil(history.previewDeletion(filter: query()))

            let refreshed = try XCTUnwrap(history.previewDeletion(filter: range))
            XCTAssertEqual(refreshed.count, 1)
            XCTAssertEqual(history.deletePeriod(filter: range, preview: refreshed), .deleted)
            XCTAssertEqual(history.page(beforeId: .max, limit: 10, filter: query())?.rows.map(\.seller), ["Outside"])
        }
    }

    func testCheckedTimeRangeIncludesStartAndExcludesEnd() throws {
        try withHistory { history, _ in
            _ = try save("Start", day: "2026-10-20", checkedAt: 100, to: history)
            _ = try save("End", day: "2026-10-20", checkedAt: 200, to: history)
            let range = query(checkedFrom: 100, checkedBefore: 200)
            let preview = try XCTUnwrap(history.previewDeletion(filter: range))
            XCTAssertEqual(preview.count, 1)
            XCTAssertEqual(history.deletePeriod(filter: range, preview: preview), .deleted)
            XCTAssertEqual(history.page(beforeId: .max, limit: 10, filter: query())?.rows.map(\.seller), ["End"])
        }
    }

    func testStatisticsGroupAppliesAllFiltersToPaginationAndExport() throws {
        try withHistory { history, folder in
            let first = try save("Alpha", day: "2026-10-20", checkedAt: 100, to: history)
            let newest = try save("Alpha", day: "2026-10-21", checkedAt: 200, to: history)
            _ = try save("Alpha Retail", day: "2026-10-20", checkedAt: 300, to: history)
            _ = try save("Alpha", day: "2026-10-19", checkedAt: 400, to: history)
            _ = try save("Alpha", day: "2026-10-22", checkedAt: 500, vat: "٣١٢٠٠٠٠٠٠٠٠٠٠٠٣", to: history)

            let rows = InvoiceStatisticsRowFilter(seller: "ALPHA", vat: "", excludedVat: "312")
            let group = InvoiceStatisticsHistoryFilter(rows: rows, sellerKey: "Alpha", vatKey: nil,
                missingInvoiceDate: false)
            let filter = InvoiceHistoryQuery(seller: "", vat: "", invoiceFrom: "2026-10-20",
                invoiceTo: "2026-10-22", checkedFrom: .min, checkedBefore: .max, category: -1,
                annotationTerm: "", statistics: group)
            let page = try XCTUnwrap(history.page(beforeId: .max, limit: 1, filter: filter))
            XCTAssertEqual(page.rows.map(\.id), [newest.id])
            XCTAssertTrue(page.hasMore)
            let last = try XCTUnwrap(history.page(beforeId: newest.id, limit: 1, filter: filter))
            XCTAssertEqual(last.rows.map(\.id), [first.id])
            XCTAssertFalse(last.hasMore)
            XCTAssertNil(history.previewDeletion(filter: filter))

            let url = folder.appendingPathComponent("group.csv")
            try Data().write(to: url)
            let file = try FileHandle(forWritingTo: url)
            try history.writeHistoryCsv(filter: filter, language: .english, to: file)
            try file.close()
            let csv = try String(contentsOf: url, encoding: .utf8)
            XCTAssertTrue(csv.contains("2026-10-20"))
            XCTAssertTrue(csv.contains("2026-10-21"))
            XCTAssertFalse(csv.contains("2026-10-19"))
            XCTAssertFalse(csv.contains("2026-10-22"))
            XCTAssertFalse(csv.contains("Alpha Retail"))
        }
    }

    private func withHistory(_ run: (SQLiteInvoiceHistory, URL) throws -> Void) throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        try run(SQLiteInvoiceHistory(folder: folder.appendingPathComponent("history")), folder)
    }

    private func query(from: String = "", to: String = "", checkedFrom: Int64 = .min, checkedBefore: Int64 = .max,
                       category: Int32 = -1, annotation: String = "") -> InvoiceHistoryQuery {
        InvoiceHistoryQuery(seller: "", vat: "", invoiceFrom: from, invoiceTo: to,
            checkedFrom: checkedFrom, checkedBefore: checkedBefore, category: category, annotationTerm: annotation)
    }

    private func save(_ seller: String, day: String, checkedAt: Int64, vat: String = "300000000000003", to history: SQLiteInvoiceHistory) throws -> InvoiceHistoryEntry {
        var bytes = Data()
        for (index, field) in [seller, vat, day + "T10:30:00", "23.00", "3.00"].enumerated() {
            let value = Data(field.utf8)
            bytes.append(contentsOf: [UInt8(index + 1), UInt8(value.count)])
            bytes.append(value)
        }
        let raw = bytes.base64EncodedString()
        return try XCTUnwrap(history.save(raw: raw, normalizedPayload: raw, seller: seller,
            vat: vat, total: "23.00", tax: "3.00", invoiceDay: day, checkedAt: checkedAt))
    }
}
