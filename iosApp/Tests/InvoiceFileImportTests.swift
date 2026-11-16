import Foundation
import InvoiceUI
import XCTest
@testable import InvoicePreview

final class InvoiceFileImportTests: XCTestCase {
    func testBatchKeepsOriginalCheckTimeAndRollsBackCancelledFile() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: false)
        defer { try? FileManager.default.removeItem(at: folder) }
        let history = SQLiteInvoiceHistory(folder: folder)
        let first = InvoiceImportCandidate(raw: payload("First"))
        let second = InvoiceImportCandidate(raw: payload("Second"))
        XCTAssertTrue(first.valid)
        XCTAssertTrue(second.valid)

        let imported = try history.importCandidates([first], cancelled: { false })
        XCTAssertEqual(imported.added, 1)
        let originalTime = try XCTUnwrap(history.entry(id: 1)).checkedAt
        let repeated = try history.importCandidates([first], cancelled: { false })
        XCTAssertEqual(repeated.duplicates, 1)
        XCTAssertEqual(history.entry(id: 1)?.checkedAt, originalTime)

        var checks = 0
        XCTAssertThrowsError(try history.importCandidates([second, first], cancelled: {
            checks += 1
            return checks == 2
        }))
        XCTAssertNil(history.entry(id: 2))
        XCTAssertEqual(history.entry(id: 1)?.checkedAt, originalTime)
    }

    private func payload(_ seller: String) -> String {
        var bytes = Data()
        for (index, field) in [seller, "300000000000003", "2026-10-20T10:30:00", "23.00", "3.00"].enumerated() {
            bytes.append(contentsOf: [UInt8(index + 1), UInt8(field.utf8.count)])
            bytes.append(contentsOf: field.utf8)
        }
        return bytes.base64EncodedString()
    }
}
