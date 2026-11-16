import Foundation
import InvoiceUI
import XCTest
@testable import InvoicePreview

final class InvoiceCsvIntegrationTests: XCTestCase {
    func testFilteredHistoryUsesTheVisibleRowsAndOptionalHashColumn() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: false)
        defer { try? FileManager.default.removeItem(at: folder) }
        let history = SQLiteInvoiceHistory(folder: folder.appendingPathComponent("db"))
        let plain = payload(["Alpha", "300000000000003", "2026-10-20T10:30:00", "23.00", "3.00"])
        let hashed = payload(["Beta", "300000000000004", "2026-10-21T10:30:00", "46.00", "6.00", "hash-value"])
        let first = try XCTUnwrap(history.save(raw: plain, normalizedPayload: plain, seller: "Alpha",
            vat: "300000000000003", total: "23.00", tax: "3.00", invoiceDay: "2026-10-20", checkedAt: 100))
        XCTAssertNotNil(history.save(raw: hashed, normalizedPayload: hashed, seller: "Beta",
            vat: "300000000000004", total: "46.00", tax: "6.00", invoiceDay: "2026-10-21", checkedAt: 200))
        XCTAssertTrue(history.updateAnnotation(id: first.id,
            annotation: InvoiceAnnotationFields(number: "INV-1", category: 0, note: "=SUM(1,2)")))

        let filtered = try export(history, to: folder.appendingPathComponent("filtered.csv"), seller: "Alpha")
        let expected = "\u{FEFF}\"رقم الفاتورة\",\"اسم البائع\",\"الرقم الضريبي\",\"تاريخ الفاتورة\",\"إجمالي الفاتورة\",\"الضريبة\",\"التصنيف\",\"ملاحظة\"\r\n" +
            "\"INV-1\",\"Alpha\",\"300000000000003\",\"2026-10-20T10:30:00\",\"23.00\",\"3.00\",\"\",\"'=SUM(1,2)\""
        XCTAssertEqual(filtered, Data(expected.utf8))

        let english = try export(history, to: folder.appendingPathComponent("english.csv"), seller: "Alpha", language: .english)
        let englishExpected = "\u{FEFF}\"Invoice number\",\"Seller name\",\"VAT number\",\"Invoice date\",\"Invoice total\",\"VAT amount\",\"Category\",\"Note\"\r\n" +
            "\"INV-1\",\"Alpha\",\"300000000000003\",\"2026-10-20T10:30:00\",\"23.00\",\"3.00\",\"\",\"'=SUM(1,2)\""
        XCTAssertEqual(english, Data(englishExpected.utf8))

        let allBytes = try export(history, to: folder.appendingPathComponent("all.csv"), seller: "")
        let all = String(decoding: allBytes, as: UTF8.self)
        XCTAssertTrue(all.components(separatedBy: "\r\n")[0].contains("\"Hash الفاتورة\""))
        XCTAssertTrue(all.contains("\"hash-value\""))
        XCTAssertEqual(all.components(separatedBy: "\r\n").count, 3)
    }

    private func export(_ history: SQLiteInvoiceHistory, to url: URL, seller: String,
                        language: InvoiceLanguage = .arabic) throws -> Data {
        XCTAssertTrue(FileManager.default.createFile(atPath: url.path, contents: nil))
        let file = try FileHandle(forWritingTo: url)
        try history.writeHistoryCsv(filter: InvoiceHistoryQuery(seller: seller, vat: seller,
            invoiceFrom: "", invoiceTo: "", checkedFrom: Int64.min, checkedBefore: Int64.max,
            category: -1, annotationTerm: ""), language: language, to: file)
        try file.close()
        return try Data(contentsOf: url)
    }

    private func payload(_ fields: [String]) -> String {
        var bytes = Data()
        for (index, field) in fields.enumerated() {
            let value = Data(field.utf8)
            bytes.append(contentsOf: [UInt8(index + 1), UInt8(value.count)])
            bytes.append(value)
        }
        return bytes.base64EncodedString()
    }
}
