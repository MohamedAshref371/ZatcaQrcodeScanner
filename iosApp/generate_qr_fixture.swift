import AppKit
import CoreImage
import CoreImage.CIFilterBuiltins
import Foundation

let fields = ["متجر صور الاختبار", "310123456789013", "2026-10-20T10:30:00", "115.00", "15.00"]
var tlv = Data()
for (index, field) in fields.enumerated() {
    let bytes = Data(field.utf8)
    tlv.append(contentsOf: [UInt8(index + 1), UInt8(bytes.count)])
    tlv.append(bytes)
}

let filter = CIFilter.qrCodeGenerator()
filter.message = Data(tlv.base64EncodedString().utf8)
filter.correctionLevel = "M"
let qr = filter.outputImage!
let padding: CGFloat = 6
let extent = qr.extent.insetBy(dx: -padding, dy: -padding)
let white = CIImage(color: .white).cropped(to: extent)
let image = qr.composited(over: white).transformed(by: CGAffineTransform(scaleX: 12, y: 12))
let bitmap = CIContext().createCGImage(image, from: image.extent)!
let png = NSBitmapImageRep(cgImage: bitmap).representation(using: .png, properties: [:])!
try png.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
