import XCTest

/// بيلتقط الشاشات الخمس (Scanner, Result, History, Statistics, Settings) باللغة اللي في SCREENSHOT_LANG.
final class ScreenshotTests: XCTestCase {

    // الترتيب = ترتيب الـ NavigationBar في ScannerApp.kt
    private let tabs: [(file: String, en: String, ar: String)] = [
        ("01-scanner",    "Scanner",    "الماسح"),
        ("02-result",     "Result",     "النتيجة"),
        ("03-history",    "History",    "السجل"),
        ("04-statistics", "Statistics", "الإحصاءات"),
        ("05-settings",   "Settings",   "الإعدادات"),
    ]

    override func setUp() { continueAfterFailure = false }

    func testCaptureScreens() throws {
        let env = ProcessInfo.processInfo.environment
        let lang = env["SCREENSHOT_LANG"] ?? "en"
        let arabic = lang == "ar"
        let dir = env["SCREENSHOT_DIR"] ?? NSTemporaryDirectory()
        try FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)

        // تصريح الكاميرا بيطلع أول ما تفتح تاب الماسح
        addUIInterruptionMonitor(withDescription: "Permissions") { alert in
            for name in ["Allow", "OK", "Allow While Using App", "السماح", "موافق"] where alert.buttons[name].exists {
                alert.buttons[name].tap(); return true
            }
            if alert.buttons.count > 0 { alert.buttons.element(boundBy: alert.buttons.count - 1).tap(); return true }
            return false
        }

        let app = XCUIApplication()
        app.launchArguments += [
            "-AppleLanguages", "(\(lang))",
            "-AppleLocale", arabic ? "ar_SA" : "en_US",
            "-InvoiceQRLanguage", lang,
            "-seedDemoData", lang,
        ]
        app.launch()
        Thread.sleep(forTimeInterval: 4) // استنى الـ seed + أول رسم
        app.tap()                        // يفعّل interruption monitor

        for (index, tab) in tabs.enumerated() {
            tapTab(app, index: index, label: arabic ? tab.ar : tab.en, arabic: arabic)
            Thread.sleep(forTimeInterval: 2)
            app.tap() // لو فيه alert ظاهر
            let shot = app.screenshot()
            try shot.pngRepresentation.write(to: URL(fileURLWithPath: "\(dir)/\(tab.file)-\(lang).png"))
            let attachment = XCTAttachment(screenshot: shot)
            attachment.name = "\(tab.file)-\(lang)"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
    }

    /// يدوّر على عنصر الـ label (أقرب واحد للأسفل = الـ NavigationBar)، ولو ملقاهوش يضغط بالإحداثيات.
    private func tapTab(_ app: XCUIApplication, index: Int, label: String, arabic: Bool) {
        let predicate = NSPredicate(format: "label == %@ OR identifier == %@", label, label)
        let query = app.descendants(matching: .any).matching(predicate)
        if query.firstMatch.waitForExistence(timeout: 10) {
            let bottom = query.allElementsBoundByIndex
                .filter { $0.exists && $0.frame.height > 0 }
                .max(by: { $0.frame.minY < $1.frame.minY })
            if let bottom { bottom.tap(); return }
        }
        let fraction = (Double(index) + 0.5) / 5.0
        let x = arabic ? 1.0 - fraction : fraction
        app.coordinate(withNormalizedOffset: CGVector(dx: x, dy: 0.96)).tap()
    }
}
