import XCTest

final class InvoicePreviewUITests: XCTestCase {
    func testAndroidStyleResultFlow() {
        continueAfterFailure = false
        let app = isolatedApp()
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
        input.typeText(base64(fields))
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
        let app = isolatedApp()
        app.launch()
        let chooseButton = element("invoice-scanner-choose", in: app)
        XCTAssertTrue(chooseButton.waitForExistence(timeout: 15))
        attachScreenshot("invoice-scanner", of: app)
        chooseButton.tap()
        let photoButton = element("invoice-scan-photo", in: app)
        XCTAssertTrue(photoButton.waitForExistence(timeout: 15))
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
        element("invoice-history-tab", in: app).tap()
        XCTAssertTrue(element("invoice-history-row", in: app).waitForExistence(timeout: 10), app.debugDescription)
    }

    func testHistoryPersistsAndRescanKeepsOriginalCheckTime() {
        continueAfterFailure = false
        let app = isolatedApp()
        let seller = "History store"
        let payload = base64([seller, "300000000000003", "2026-10-20T10:30:00", "230.00", "30.00"])
        XCTAssertTrue(payload.hasSuffix("="))

        app.launch()
        let historyTab = element("invoice-history-tab", in: app)
        XCTAssertTrue(historyTab.waitForExistence(timeout: 15))
        historyTab.tap()
        XCTAssertTrue(element("invoice-history-empty", in: app).waitForExistence(timeout: 10))
        submit(payload, in: app)
        let checkedAt = element("invoice-checked-at", in: app)
        XCTAssertTrue(checkedAt.waitForExistence(timeout: 15))
        let originalCheckTime = checkedAt.label

        app.terminate()
        app.launch()
        // Submit a different textual encoding of the same payload after restarting.
        let unpadded = payload.trimmingCharacters(in: CharacterSet(charactersIn: "="))
        submit(String(unpadded.prefix(8)) + " " + String(unpadded.dropFirst(8)), in: app)
        XCTAssertTrue(checkedAt.waitForExistence(timeout: 15))
        XCTAssertEqual(checkedAt.label, originalCheckTime)
        XCTAssertEqual(element("invoice-field-1", in: app).label, seller)

        historyTab.tap()
        let rows = app.descendants(matching: .any).matching(identifier: "invoice-history-row")
        XCTAssertTrue(rows.firstMatch.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertEqual(rows.count, 1)
        rows.firstMatch.tap()
        XCTAssertTrue(checkedAt.waitForExistence(timeout: 15))
        XCTAssertEqual(checkedAt.label, originalCheckTime)
        XCTAssertEqual(element("invoice-field-1", in: app).label, seller)
    }

    func testHistorySearchAndInvoiceDateFilter() {
        continueAfterFailure = false
        let app = isolatedApp()
        app.launch()
        submit(base64(["Éclair", "300000000000003", "2026-10-20T10:30:00Z", "230.00", "30.00"]), in: app)
        XCTAssertTrue(element("invoice-status", in: app).waitForExistence(timeout: 15))
        element("invoice-history-tab", in: app).tap()
        let search = element("invoice-history-search", in: app)
        XCTAssertTrue(search.waitForExistence(timeout: 10))
        search.tap()
        search.typeText("CLAIR")
        let row = element("invoice-history-row", in: app)
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        search.typeText("X")
        let empty = element("invoice-history-empty", in: app)
        XCTAssertTrue(empty.waitForExistence(timeout: 10))
        search.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 6))
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        element("invoice-history-dates", in: app).tap()
        let start = element("invoice-history-from", in: app)
        XCTAssertTrue(start.waitForExistence(timeout: 10))
        start.tap()
        start.typeText("2026-10-21")
        let done = app.keyboards.buttons["Done"].firstMatch
        if done.exists { done.tap() }
        element("invoice-history-date-apply", in: app).tap()
        XCTAssertTrue(empty.waitForExistence(timeout: 10))
        XCTAssertFalse(row.exists)
    }

    func testPhaseTwoResultExposesAdvancedFieldsAndChecks() {
        continueAfterFailure = false
        let app = isolatedApp()
        app.launch()
        submit(base64(["Seller", "300000000000003", "2026-10-20T10:30:00", "230.00", "30.00",
                       "hash", "signature", "key", "stamp"]), in: app)
        XCTAssertTrue(element("invoice-status", in: app).waitForExistence(timeout: 15))
        XCTAssertTrue(element("invoice-share", in: app).exists)
        XCTAssertFalse(element("invoice-field-7", in: app).exists)
        let advanced = scrollTo("invoice-advanced", in: app)
        advanced.tap()
        XCTAssertEqual(advanced.label, "إخفاء الحقول المتقدمة")
        XCTAssertEqual(scrollTo("invoice-field-7", in: app).label, "signature")
        XCTAssertEqual(scrollTo("invoice-field-8", in: app).label, Data("key".utf8).base64EncodedString())
        XCTAssertEqual(scrollTo("invoice-field-9", in: app).label, Data("stamp".utf8).base64EncodedString())
        scrollTo("invoice-checks", in: app).tap()
        XCTAssertTrue(element("invoice-check-details", in: app).exists)
        scrollTo("invoice-checks", in: app).tap()
        scrollTo("invoice-raw", in: app).tap()
        scrollTo("invoice-copy-raw", in: app).tap()
        XCTAssertTrue(app.staticTexts["تم النسخ"].exists || element("invoice-copy-raw", in: app).label == "تم النسخ")
    }

    func testCanEditInvoiceNumber() {
        continueAfterFailure = false
        let app = isolatedApp()
        app.launch()
        submit(base64(["Seller", "300000000000003", "2026-10-20T10:30:00", "230.00", "30.00"]), in: app)
        XCTAssertTrue(element("invoice-status", in: app).waitForExistence(timeout: 15))
        scrollTo("invoice-annotation-edit", in: app).tap()
        let number = element("invoice-annotation-number", in: app)
        XCTAssertTrue(number.waitForExistence(timeout: 10))
        number.tap()
        number.typeText("INV-21")
        let done = app.keyboards.buttons["Done"].firstMatch
        if done.exists { done.tap() }
        element("invoice-annotation-save", in: app).tap()
        let saved = scrollTo("invoice-saved-number", in: app)
        XCTAssertEqual(saved.label, "رقم الفاتورة: INV-21")
    }

    func testStatisticsDisplaysZeroTaxAsAnAmount() {
        continueAfterFailure = false
        let app = isolatedApp()
        app.launch()
        submit(base64(["Seller", "300000000000003", "2026-10-20T10:30:00", "230.00", "0"]), in: app)
        XCTAssertTrue(element("invoice-status", in: app).waitForExistence(timeout: 15))
        element("invoice-statistics-tab", in: app).tap()
        XCTAssertTrue(element("invoice-statistics-summary", in: app).waitForExistence(timeout: 15))
        XCTAssertTrue(app.staticTexts["عدد الفواتير: 1"].firstMatch.exists)
        XCTAssertTrue(app.staticTexts["230.00"].firstMatch.exists)
        XCTAssertTrue(app.staticTexts["0.00"].firstMatch.exists)
    }

    private func scrollTo(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        let target = element(identifier, in: app)
        let viewport = element("invoice-result-list", in: app).frame.insetBy(dx: 0, dy: 24)
        let upper = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.38))
        let lower = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.64))
        for _ in 0..<12 {
            let frame = target.exists ? target.frame : .null
            if !frame.isNull && viewport.contains(CGPoint(x: frame.midX, y: frame.midY)) && target.isHittable {
                return target
            }
            if !frame.isNull && frame.midY < viewport.minY {
                upper.press(forDuration: 0.1, thenDragTo: lower)
            } else {
                lower.press(forDuration: 0.1, thenDragTo: upper)
            }
        }
        XCTFail("Could not scroll \(identifier) into the result viewport. \(app.debugDescription)")
        return target
    }

    private func isolatedApp() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["-invoiceAppLanguage", "ar"]
        app.launchEnvironment["INVOICE_UI_TEST_HISTORY"] = UUID().uuidString
        return app
    }

    private func submit(_ payload: String, in app: XCUIApplication) {
        let tab = element("invoice-result-tab", in: app)
        XCTAssertTrue(tab.waitForExistence(timeout: 15))
        tab.tap()
        let input = element("invoice-input", in: app)
        XCTAssertTrue(input.waitForExistence(timeout: 15))
        input.tap()
        input.typeText(payload)
        element("invoice-submit", in: app).tap()
    }

    private func base64(_ fields: [String]) -> String {
        var tlv = Data()
        for (index, field) in fields.enumerated() {
            let bytes = Data(field.utf8)
            tlv.append(contentsOf: [UInt8(index + 1), UInt8(bytes.count)])
            tlv.append(bytes)
        }
        return tlv.base64EncodedString()
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
