import Foundation
import InvoiceUI
import XCTest
@testable import InvoicePreview

final class InvoiceBackupIntegrationTests: XCTestCase {
    func testRestoresAndroidBackupAndRoundTripsAnnotations() throws {
        let folder = try temporaryFolder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let first = SQLiteInvoiceHistory(folder: folder.appendingPathComponent("first"))
        let second = SQLiteInvoiceHistory(folder: folder.appendingPathComponent("second"))
        let raw = payload("متجر")
        let note = "نص فيه \"اقتباس\" وسطر\nوتعبير 🧾"
        let row: [String: Any] = ["raw": raw, "checkedAt": 123456789, "invoiceNumber": "A-12", "category": 3, "note": note]
        let input = folder.appendingPathComponent("android.json")
        try JSONSerialization.data(withJSONObject: [
            "format": "invoiceqr-history-backup", "version": 2, "invoices": [row, row]
        ]).write(to: input)
        let imported = try restore(from: input, into: first)
        XCTAssertEqual(imported.added, 1)
        XCTAssertEqual(imported.duplicates, 1)
        XCTAssertEqual(first.entry(id: 1)?.checkedAt, 123456789)

        let exported = folder.appendingPathComponent("iphone.json")
        try Data().write(to: exported)
        let output = try FileHandle(forWritingTo: exported)
        try first.writeBackup(to: output)
        try output.close()
        let envelope = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: exported)) as? [String: Any])
        XCTAssertEqual(envelope["version"] as? Int, 2)
        let rows = try XCTUnwrap(envelope["invoices"] as? [[String: Any]])
        XCTAssertEqual(rows.count, 1)
        XCTAssertEqual(rows.first?["raw"] as? String, raw)
        XCTAssertEqual(rows.first?["invoiceNumber"] as? String, "A-12")
        XCTAssertEqual(rows.first?["category"] as? Int, 3)
        XCTAssertEqual(rows.first?["note"] as? String, note)
        let roundTrip = try restore(from: exported, into: second)
        XCTAssertEqual(roundTrip.added, 1)
        XCTAssertEqual(second.entry(id: 1)?.raw, raw)
    }

    func testTruncatedBackupRollsBackRecordsAlreadyRead() throws {
        let folder = try temporaryFolder()
        defer { try? FileManager.default.removeItem(at: folder) }
        let history = SQLiteInvoiceHistory(folder: folder.appendingPathComponent("db"))
        let url = folder.appendingPathComponent("truncated.json")
        let data = """
            {"format":"invoiceqr-history-backup","version":1,"invoices":[{"raw":"\(payload("Seller"))","checkedAt":50}
            """
        try Data(data.utf8).write(to: url)
        XCTAssertThrowsError(try restore(from: url, into: history))
        XCTAssertNil(history.entry(id: 1))
    }

    private func restore(from url: URL, into history: SQLiteInvoiceHistory) throws -> InvoiceBackupResult {
        let file = try FileHandle(forReadingFrom: url)
        defer { try? file.close() }
        return try history.readBackup(from: file)
    }

    private func temporaryFolder() throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: false)
        return folder
    }

    private func payload(_ seller: String) -> String {
        var bytes = Data()
        for (i, field) in [seller, "300000000000003", "2026-10-20T10:30:00", "23.00", "3.00"].enumerated() {
            bytes.append(contentsOf: [UInt8(i + 1), UInt8(field.utf8.count)])
            bytes.append(contentsOf: field.utf8)
        }
        return bytes.base64EncodedString()
    }
}
