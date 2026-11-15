import XCTest

final class InvoicePreviewUITests: XCTestCase {
    func testAndroidStyleResultFlow() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        let resultTab = element("invoice-result-tab", in: app)
        XCTAssertTrue(resultTab.waitForExistence(timeout: 15))
        resultTab.tap()
        let input = element("invoice-input", in: app)
        XCTAssertTrue(input.waitForExistence(timeout: 15))
        XCTAssertFalse(element("invoice-submit", in: app).isEnabled)
        attachScreenshot("invoice-entry", of: app)

        input.tap()
        input.typeText("invalid!")
        element("invoice-submit", in: app).tap()
        let error = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "رمز QR غير صالح للفاتورة")).firstMatch
        XCTAssertTrue(error.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertFalse(element("invoice-result", in: app).exists)
        attachScreenshot("invoice-invalid", of: app)

        input.tap()
        input.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 8))
        let fields = ["متجر تجريبي", "300000000000003", "2026-10-20T10:30:00", "230.00", "30.00"]
        var tlv = Data()
        for (index, field) in fields.enumerated() {
            let bytes = Data(field.utf8)
            tlv.append(contentsOf: [UInt8(index + 1), UInt8(bytes.count)])
            tlv.append(bytes)
        }
        input.typeText(tlv.base64EncodedString())
        element("invoice-submit", in: app).tap()

        let status = element("invoice-status", in: app)
        XCTAssertTrue(status.waitForExistence(timeout: 15), app.debugDescription)
        XCTAssertEqual(status.label, "اجتاز فحوص QR المحلية")
        XCTAssertFalse(element("invoice-input", in: app).exists)
        XCTAssertTrue(element("invoice-result", in: app).exists)
        XCTAssertEqual(element("invoice-field-1", in: app).label, fields[0])
        XCTAssertEqual(element("invoice-field-2", in: app).label, fields[1])
        attachScreenshot("invoice-valid", of: app)

        let fieldLabels = [3: "التاريخ: الثلاثاء 20 أكتوبر 2026", 4: fields[3], 5: fields[4]]
        for tag in 3...5 {
            let field = element("invoice-field-\(tag)", in: app)
            for _ in 0..<3 where !field.isHittable {
                app.swipeUp()
            }
            XCTAssertTrue(field.exists)
            XCTAssertEqual(field.label, fieldLabels[tag])
        }
        attachScreenshot("invoice-details", of: app)
    }

    func testScansInvoiceQRFromPhotos() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        let photoButton = element("invoice-scan-photo", in: app)
        XCTAssertTrue(photoButton.waitForExistence(timeout: 15))
        attachScreenshot("invoice-scanner", of: app)
        photoButton.tap()

        // The workflow adds a recent QR image, ahead of the simulator's sample photos.
        let photo = app.images.matching(identifier: "PXGGridLayout-Info").firstMatch
        XCTAssertTrue(photo.waitForExistence(timeout: 15), app.debugDescription)
        // Photos is hosted by a system process; tap the exposed thumbnail's center.
        let frame = photo.frame
        let origin = app.frame.origin
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: frame.midX - origin.x, dy: frame.midY - origin.y))
            .tap()

        let statusOrError = app.descendants(matching: .any).matching(NSPredicate(
            format: "identifier == %@ OR label == %@ OR label == %@",
            "invoice-status", "لم أتمكن من قراءة رمز QR من الصورة", "رمز QR غير صالح للفاتورة"
        )).firstMatch
        XCTAssertTrue(statusOrError.waitForExistence(timeout: 20), app.debugDescription)
        XCTAssertEqual(statusOrError.label, "اجتاز فحوص QR المحلية")
        XCTAssertEqual(element("invoice-field-1", in: app).label, "متجر صور الاختبار")
        XCTAssertEqual(element("invoice-field-2", in: app).label, "310123456789013")
        attachScreenshot("invoice-photo-result", of: app)
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
