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

    let engine: InvoiceQrEngine

    #if !targetEnvironment(simulator)
    private lazy var mlKit = BarcodeScanner.barcodeScanner(options: BarcodeScannerOptions(formats: .qrCode))
    #endif

    init(engine: InvoiceQrEngine) {
        self.engine = engine
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
        return InvoiceZXingDecoder.read(image: image, maxSymbols: maxSymbols)
    }
}
