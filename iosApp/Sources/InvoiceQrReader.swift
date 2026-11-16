import CoreGraphics
import Foundation
import ImageIO
import InvoiceUI

#if !targetEnvironment(simulator)
import MLKitBarcodeScanning
import MLKitVision
import UIKit
#endif

/// Create one reader per scan queue. Both engines receive upright pixels from the same input paths.
final class InvoiceQrReader {
    static var mlKitAvailable: Bool {
        #if !targetEnvironment(simulator)
        true
        #else
        false
        #endif
    }

    static var selected: InvoiceQrEngine {
        mlKitAvailable && UserDefaults.standard.string(forKey: "invoiceScanEngine") == "ml-kit" ? .mlKit : .zxing
    }

    static func save(_ engine: InvoiceQrEngine) {
        UserDefaults.standard.set(mlKitAvailable && engine == .mlKit ? "ml-kit" : "zxing", forKey: "invoiceScanEngine")
    }

    static var zxingSettings: InvoiceZxingSettings {
        let values = UserDefaults.standard.dictionary(forKey: "invoiceZxingSettings") ?? [:]
        return InvoiceZxingSettings(
            harder: values["harder"] as? Bool ?? true, rotate: values["rotate"] as? Bool ?? true,
            inverted: values["inverted"] as? Bool ?? false, binarizer: values["binarizer"] as? String ?? "LOCAL_AVERAGE",
            multiFrameRecovery: values["multiFrameRecovery"] as? Bool ?? false)
    }

    static func save(_ settings: InvoiceZxingSettings) {
        let values: [String: Any] = [
            "harder": settings.harder, "rotate": settings.rotate, "inverted": settings.inverted,
            "binarizer": settings.binarizer, "multiFrameRecovery": settings.multiFrameRecovery,
        ]
        UserDefaults.standard.set(values, forKey: "invoiceZxingSettings")
    }

    let engine: InvoiceQrEngine
    let zxing: InvoiceZxingSettings

    #if !targetEnvironment(simulator)
    private lazy var mlKit = BarcodeScanner.barcodeScanner(options: BarcodeScannerOptions(formats: .qrCode))
    #endif

    init(engine: InvoiceQrEngine, zxing: InvoiceZxingSettings = InvoiceQrReader.zxingSettings) {
        self.engine = engine
        self.zxing = zxing
    }

    func read(_ source: CGImageSource) throws -> [String] {
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: 4096
        ] as CFDictionary) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        return read(image)
    }

    func read(_ image: CGImage, maxSymbols: Int = 255) -> [String] {
        if engine == .mlKit {
            #if !targetEnvironment(simulator)
            let input = VisionImage(image: UIImage(cgImage: image))
            input.orientation = .up
            return (try? mlKit.results(in: input))?.compactMap(\.rawValue).prefix(maxSymbols).map { $0 } ?? []
            #else
            return []
            #endif
        }
        let binarizer: Int
        switch zxing.binarizer {
        case "GLOBAL_HISTOGRAM": binarizer = 1
        case "FIXED_THRESHOLD": binarizer = 2
        case "BOOL_CAST": binarizer = 3
        default: binarizer = 0
        }
        return InvoiceZXingDecoder.read(image: image, maxSymbols: maxSymbols, harder: zxing.harder,
            rotate: zxing.rotate, inverted: zxing.inverted, binarizer: binarizer)
    }
}
