import AppKit
import CoreImage
import CoreImage.CIFilterBuiltins
import Foundation
import Vision

let fields = ["متجر صور الاختبار", "310123456789013", "2026-10-20T10:30:00", "115.00", "15.00"]
var tlv = Data()
for (index, field) in fields.enumerated() {
    let bytes = Data(field.utf8)
    tlv.append(contentsOf: [UInt8(index + 1), UInt8(bytes.count)])
    tlv.append(bytes)
}

let payload = tlv.base64EncodedString()
let filter = CIFilter.qrCodeGenerator()
filter.message = Data(payload.utf8)
filter.correctionLevel = "M"
let qr = filter.outputImage!
let code = CIContext().createCGImage(qr, from: qr.extent)!
let scale = 12
let padding = 6 * scale
let width = code.width * scale
let height = code.height * scale
let context = CGContext(data: nil, width: width + 2 * padding, height: height + 2 * padding,
                        bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(),
                        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
context.setFillColor(CGColor(gray: 1, alpha: 1))
context.fill(CGRect(x: 0, y: 0, width: context.width, height: context.height))
context.interpolationQuality = .none
context.draw(code, in: CGRect(x: padding, y: padding, width: width, height: height))
let png = NSBitmapImageRep(cgImage: context.makeImage()!).representation(using: .png, properties: [:])!

let request = VNDetectBarcodesRequest()
request.symbologies = [.qr]
try VNImageRequestHandler(data: png, options: [:]).perform([request])
guard request.results?.contains(where: { $0.payloadStringValue == payload }) == true else {
    fatalError("Vision could not read the generated invoice QR fixture")
}
try png.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
