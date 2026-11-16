import CoreImage
import XCTest
@testable import InvoicePreview

final class InvoiceQrReaderTests: XCTestCase {
    func testZXingReadsQRCodeFromCameraAndFileImageFormat() throws {
        let payload = "Invoice QR reader fixture"
        let generator = try XCTUnwrap(CIFilter(name: "CIQRCodeGenerator"))
        generator.setValue(Data(payload.utf8), forKey: "inputMessage")
        let output = try XCTUnwrap(generator.outputImage)
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 8, y: 8))
        let image = try XCTUnwrap(CIContext(options: [.useSoftwareRenderer: true])
            .createCGImage(scaled, from: scaled.extent))
        let reader = InvoiceQrReader(engine: .zxing)
        XCTAssertEqual(reader.read(image, maxSymbols: 1), [payload])
        XCTAssertEqual(reader.read(image), [payload])
    }
}
