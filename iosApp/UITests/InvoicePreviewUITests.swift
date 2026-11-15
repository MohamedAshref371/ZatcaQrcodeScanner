import XCTest

final class InvoicePreviewUITests: XCTestCase {
    func testInvalidInputThenArabicInvoice() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()

        submit("invalid!", in: app)
        XCTAssertTrue(element("invoice-error", in: app).waitForExistence(timeout: 15))
        XCTAssertFalse(element("invoice-result", in: app).exists)
        attachScreenshot("invoice-invalid", of: app)

        element("invoice-clear", in: app).tap()
        XCTAssertFalse(element("invoice-error", in: app).exists)
        XCTAssertFalse(element("invoice-submit", in: app).isEnabled)

        let fields = ["متجر الاختبار", "310123456789013", "2024-02-29T14:25:00", "115.00", "15.00"]
        var tlv = Data()
        for (index, field) in fields.enumerated() {
            let bytes = Data(field.utf8)
            tlv.append(contentsOf: [UInt8(index + 1), UInt8(bytes.count)])
            tlv.append(bytes)
        }
        submit(tlv.base64EncodedString(), in: app)

        XCTAssertTrue(element("invoice-result", in: app).waitForExistence(timeout: 15))
        let status = element("invoice-status", in: app)
        XCTAssertTrue(status.waitForExistence(timeout: 5))
        XCTAssertEqual(status.label, "اجتازت الفحص المحلي")
        for (index, value) in fields.enumerated() {
            let field = element("invoice-field-\(index + 1)", in: app)
            XCTAssertTrue(field.exists, "Missing invoice field \(index + 1)")
            XCTAssertEqual(field.label, value)
        }
        attachScreenshot("invoice-valid", of: app)
    }

    private func submit(_ input: String, in app: XCUIApplication) {
        let field = element("invoice-input", in: app)
        XCTAssertTrue(field.waitForExistence(timeout: 15))
        field.tap()
        field.typeText(input)

        let submit = element("invoice-submit", in: app)
        XCTAssertTrue(submit.exists)
        XCTAssertTrue(submit.isEnabled)
        submit.tap()
    }

    private func element(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func attachScreenshot(_ name: String, of app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
