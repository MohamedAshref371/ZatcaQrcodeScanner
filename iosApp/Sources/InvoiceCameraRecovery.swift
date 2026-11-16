import CoreImage
import InvoiceUI
import UIKit

final class InvoiceCameraRecovery {
    private let consensus = InvoiceIosFrameRecovery()
    private var lastCorners: [CGPoint]?
    private var lastPositionAt: Int64 = 0
    private var dimensions: CGSize?
    private var usingGuide = false

    func reset() {
        consensus.reset()
        lastCorners = nil
        dimensions = nil
        usingGuide = false
    }

    func scan(_ image: CGImage, at nowMillis: Int64, allowGuide: Bool, reader: InvoiceQrReader, context: CIContext) -> [String] {
        let observed = reader.readZXing(image, maxSymbols: 3, returnErrors: true)
        if let valid = observed.compactMap(\.text).first {
            reset()
            return [valid]
        }
        let size = CGSize(width: image.width, height: image.height)
        if dimensions != size { reset() }
        let candidates = observed.map { $0.corners.map(\.cgPointValue) }
        if let first = candidates.first, candidates.dropFirst().contains(where: { !samePosition(first, $0) }) {
            reset()
            return []
        }

        if let first = observed.first, first.recoverable, !usingGuide {
            lastCorners = candidates.first
            lastPositionAt = nowMillis
        } else if !usingGuide && (lastCorners == nil || nowMillis - lastPositionAt > 1800) {
            reset()
            guard allowGuide else { return [] }
            usingGuide = true
        }
        dimensions = size
        let aligned: CGImage
        if usingGuide {
            aligned = image
        } else if let corners = lastCorners, let corrected = align(image, corners: corners, context: context) {
            aligned = corrected
        } else {
            reset()
            return []
        }
        guard let value = fuse(aligned, at: nowMillis, reader: reader) else { return [] }
        reset()
        return [value]
    }

    private func samePosition(_ first: [CGPoint], _ second: [CGPoint]) -> Bool {
        func bounds(_ points: [CGPoint]) -> CGRect {
            let xs = points.map(\.x)
            let ys = points.map(\.y)
            guard let x0 = xs.min(), let x1 = xs.max(), let y0 = ys.min(), let y1 = ys.max() else { return .null }
            return CGRect(x: x0, y: y0, width: x1 - x0, height: y1 - y0)
        }
        let a = bounds(first)
        let b = bounds(second)
        let smaller = min(a.width * a.height, b.width * b.height)
        guard smaller > 0 else { return false }
        let overlap = a.intersection(b)
        return !overlap.isNull && overlap.width * overlap.height / smaller > 0.8 &&
            max(a.width * a.height, b.width * b.height) / smaller < 1.5
    }

    private func align(_ image: CGImage, corners: [CGPoint], context: CIContext) -> CGImage? {
        guard corners.count == 4, corners.allSatisfy({
            $0.x >= -4 && $0.y >= -4 && $0.x <= CGFloat(image.width) + 4 && $0.y <= CGFloat(image.height) + 4
        }) else { return nil }
        let top = hypot(corners[1].x - corners[0].x, corners[1].y - corners[0].y)
        let left = hypot(corners[3].x - corners[0].x, corners[3].y - corners[0].y)
        guard top >= 100, left >= 100 else { return nil }
        let vectors = corners.map { CIVector(x: $0.x, y: CGFloat(image.height - 1) - $0.y) }
        let rectified = CIImage(cgImage: image).applyingFilter("CIPerspectiveCorrection", parameters: [
            "inputTopLeft": vectors[0], "inputTopRight": vectors[1],
            "inputBottomRight": vectors[2], "inputBottomLeft": vectors[3]
        ])
        guard !rectified.extent.isEmpty, !rectified.extent.isInfinite else { return nil }
        return context.createCGImage(rectified, from: rectified.extent)
    }

    private func fuse(_ image: CGImage, at nowMillis: Int64, reader: InvoiceQrReader) -> String? {
        guard image.width >= 100, image.height >= 100 else { return nil }
        let side = 512
        let border = 32
        let colorSpace = CGColorSpaceCreateDeviceGray()
        let info = CGImageAlphaInfo.none.rawValue
        guard let canvas = CGContext(data: nil, width: side, height: side, bitsPerComponent: 8,
            bytesPerRow: side, space: colorSpace, bitmapInfo: info), let pixels = canvas.data else { return nil }
        canvas.setFillColor(gray: 1, alpha: 1)
        canvas.fill(CGRect(x: 0, y: 0, width: side, height: side))
        canvas.draw(image, in: CGRect(x: 0, y: 0, width: side, height: side))
        return consensus.offer(pixels: Data(bytes: pixels, count: side * side), nowMillis: nowMillis) { binary in
            let edge = side + 2 * border
            guard let provider = CGDataProvider(data: binary as CFData),
                  let thresholded = CGImage(width: side, height: side, bitsPerComponent: 8, bitsPerPixel: 8,
                      bytesPerRow: side, space: colorSpace, bitmapInfo: CGBitmapInfo(rawValue: info),
                      provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent),
                  let padded = CGContext(data: nil, width: edge, height: edge, bitsPerComponent: 8,
                      bytesPerRow: edge, space: colorSpace, bitmapInfo: info) else { return nil }
            padded.setFillColor(gray: 1, alpha: 1)
            padded.fill(CGRect(x: 0, y: 0, width: edge, height: edge))
            padded.draw(thresholded, in: CGRect(x: border, y: border, width: side, height: side))
            guard let candidate = padded.makeImage() else { return nil }
            let results = reader.read(candidate, maxSymbols: 2)
            return results.count == 1 ? results.first : nil
        }
    }
}
