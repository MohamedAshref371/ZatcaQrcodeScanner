import AVFoundation
import CoreImage
import InvoiceUI
import ImageIO
import os
import PhotosUI
import UIKit
import UniformTypeIdentifiers
import Vision

final class NativeInvoiceScanner: NSObject, InvoiceScannerHost, PHPickerViewControllerDelegate {
    weak var presenter: UIViewController?
    private var photoDetection: InvoiceDetection?
    private let backupDocuments = InvoiceBackupDocuments()
    private static let photoLog = Logger(subsystem: "com.poetscorner.invoiceqr.preview", category: "photo-scan")

    func makeCamera(detection: InvoiceDetection) -> UIViewController {
        InvoiceCameraViewController(detection: detection)
    }

    func releaseCamera(controller: UIViewController) {
        (controller as? InvoiceCameraViewController)?.stopCamera()
    }

    func exportBackup(observer: InvoiceBackupObserver) {
        backupDocuments.exportBackup(from: presenter, observer: observer)
    }

    func importBackup(observer: InvoiceBackupObserver) {
        backupDocuments.importBackup(from: presenter, observer: observer)
    }

    func shareInvoice(summary: String) -> Bool {
        guard let presenter, presenter.presentedViewController == nil, let view = presenter.viewIfLoaded else {
            return false
        }
        let shareSheet = UIActivityViewController(activityItems: [summary], applicationActivities: nil)
        if let popover = shareSheet.popoverPresentationController {
            popover.sourceView = view
            popover.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1)
            popover.permittedArrowDirections = []
        }
        presenter.present(shareSheet, animated: true)
        return true
    }

    func selectPhoto(detection: InvoiceDetection) {
        guard let presenter, presenter.presentedViewController == nil, photoDetection == nil else { return }
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = 1
        configuration.preferredAssetRepresentationMode = .current
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self
        picker.modalPresentationStyle = .fullScreen
        photoDetection = detection
        presenter.present(picker, animated: true)
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        guard let detection = photoDetection, let provider = results.first?.itemProvider else {
            Self.photoLog.info("Picker returned without a photo.")
            photoDetection = nil
            picker.dismiss(animated: true)
            return
        }
        let imageType = provider.registeredTypeIdentifiers.first { UTType($0)?.conforms(to: .image) == true }
            ?? UTType.image.identifier
        provider.loadDataRepresentation(forTypeIdentifier: imageType) { [weak self, weak picker] data, _ in
            let candidates = data.map(Self.readQRs) ?? []
            Self.photoLog.info("Photo bytes: \(data?.count ?? 0), QR codes: \(candidates.count).")
            DispatchQueue.main.async {
                guard let self else { return }
                self.photoDetection = nil
                picker?.dismiss(animated: true)
                if !candidates.contains(where: { detection.accept(raw: $0) != .invalid }) {
                    detection.reportPhotoIssue(foundQr: !candidates.isEmpty)
                }
            }
        }
    }

    private static func readQRs(data: Data) -> [String] {
        let request = VNDetectBarcodesRequest()
        request.symbologies = [.qr]
        #if targetEnvironment(simulator)
        request.usesCPUOnly = true
        #endif
        let source = CGImageSourceCreateWithData(data as CFData, nil)
        let properties = source.flatMap { CGImageSourceCopyPropertiesAtIndex($0, 0, nil) } as? [CFString: Any]
        let orientation = (properties?[kCGImagePropertyOrientation] as? NSNumber)
            .flatMap { CGImagePropertyOrientation(rawValue: $0.uint32Value) } ?? .up
        let handler = VNImageRequestHandler(data: data, orientation: orientation, options: [:])
        do {
            try handler.perform([request])
            let codes = request.results?.compactMap(\.payloadStringValue) ?? []
            if !codes.isEmpty { return codes }
        } catch {
            let failure = error as NSError
            Self.photoLog.info("Vision QR detection failed: \(failure.domain, privacy: .public) \(failure.code).")
        }

        // Use the software QR detector when Vision cannot decode an image.
        guard let image = CIImage(data: data, options: [.applyOrientationProperty: true]),
              let detector = CIDetector(
                ofType: CIDetectorTypeQRCode,
                context: CIContext(options: [.useSoftwareRenderer: true]),
                options: [CIDetectorAccuracy: CIDetectorAccuracyHigh]
              ) else { return [] }
        let codes = detector.features(in: image).compactMap { ($0 as? CIQRCodeFeature)?.messageString }
        Self.photoLog.info("Core Image fallback QR codes: \(codes.count).")
        return codes
    }
}

private final class InvoiceCameraViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    private let detection: InvoiceDetection
    private let session = AVCaptureSession()
    private let captureQueue = DispatchQueue(label: "com.poetscorner.invoiceqr.camera", qos: .userInitiated)
    private lazy var preview = AVCaptureVideoPreviewLayer(session: session)
    private let guide = UIView()
    private let status = UILabel()
    private var visible = false
    private var accepted = false
    private var lastRejected: (payload: String, time: TimeInterval)?

    // Accessed exclusively on captureQueue.
    private var configured = false

    init(detection: InvoiceDetection) {
        self.detection = detection
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { nil }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)

        guide.isUserInteractionEnabled = false
        guide.layer.borderWidth = 2
        guide.layer.borderColor = UIColor.white.withAlphaComponent(0.9).cgColor
        guide.layer.cornerRadius = 16
        guide.isHidden = true
        view.addSubview(guide)

        status.textColor = .white
        status.numberOfLines = 0
        status.textAlignment = .center
        status.font = .preferredFont(forTextStyle: .body)
        status.adjustsFontForContentSizeCategory = true
        status.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(status)
        NSLayoutConstraint.activate([
            status.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            status.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            status.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 24),
            status.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -24),
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview.frame = view.bounds
        let side = min(view.bounds.width * 0.72, view.bounds.height * 0.74, 310)
        guide.frame = CGRect(x: view.bounds.midX - side / 2, y: view.bounds.midY - side / 2, width: side, height: side)
        if let connection = preview.connection, connection.isVideoOrientationSupported {
            switch view.window?.windowScene?.interfaceOrientation {
            case .landscapeLeft: connection.videoOrientation = .landscapeLeft
            case .landscapeRight: connection.videoOrientation = .landscapeRight
            case .portraitUpsideDown: connection.videoOrientation = .portraitUpsideDown
            default: connection.videoOrientation = .portrait
            }
        }
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        visible = true
        beginCamera()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stopCamera()
    }

    func stopCamera() {
        guard visible else { return }
        visible = false
        captureQueue.async { [self] in
            if session.isRunning { session.stopRunning() }
        }
    }

    private func beginCamera() {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            status.text = "الكاميرا غير متاحة على هذا الجهاز. يمكنك اختيار صورة."
            return
        }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            startCamera(device)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    guard let self, self.visible else { return }
                    if granted {
                        self.startCamera(device)
                    } else {
                        self.showPermissionMessage()
                    }
                }
            }
        case .denied, .restricted:
            showPermissionMessage()
        @unknown default:
            status.text = "تعذّر تشغيل الكاميرا. يمكنك اختيار صورة."
        }
    }

    private func showPermissionMessage() {
        status.text = "اسمح باستخدام الكاميرا من إعدادات iPhone أو اختر صورة."
    }

    private func startCamera(_ device: AVCaptureDevice) {
        status.text = "جارٍ فتح الكاميرا…"
        captureQueue.async { [weak self] in
            guard let self else { return }
            if !self.configured {
                self.configured = self.configureCamera(device: device)
            }
            if self.configured && !self.session.isRunning { self.session.startRunning() }
            let running = self.session.isRunning
            DispatchQueue.main.async {
                guard self.visible else { return }
                self.status.text = running ? nil : "تعذّر تشغيل الكاميرا. يمكنك اختيار صورة."
                self.guide.isHidden = !running
                self.view.setNeedsLayout()
            }
        }
    }

    private func configureCamera(device: AVCaptureDevice) -> Bool {
        guard let input = try? AVCaptureDeviceInput(device: device) else { return false }
        let output = AVCaptureMetadataOutput()
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        guard session.canAddInput(input) else { return false }
        session.addInput(input)
        guard session.canAddOutput(output) else {
            session.removeInput(input)
            return false
        }
        session.addOutput(output)
        guard output.availableMetadataObjectTypes.contains(.qr) else {
            session.removeOutput(output)
            session.removeInput(input)
            return false
        }
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]
        return true
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        guard visible, !accepted else { return }
        for case let code as AVMetadataMachineReadableCodeObject in metadataObjects {
            guard let payload = code.stringValue else { continue }
            let now = ProcessInfo.processInfo.systemUptime
            if let lastRejected, lastRejected.payload == payload, now - lastRejected.time < 1 { continue }
            let result = detection.accept(raw: payload)
            if result == .accepted {
                accepted = true
                return
            }
            lastRejected = (payload, now)
            if result != .invalid { return }
        }
    }
}
