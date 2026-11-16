import AVFoundation
import CoreImage
import InvoiceUI
import ImageIO
import os
import PhotosUI
import UIKit
import UniformTypeIdentifiers

final class NativeInvoiceScanner: NSObject, InvoiceScannerHost, PHPickerViewControllerDelegate {
    weak var presenter: UIViewController?
    private var photoDetection: InvoiceDetection?
    private let backupDocuments = InvoiceBackupDocuments()
    private let csvDocuments = InvoiceCsvDocuments()
    private let fileDocuments = InvoiceFileDocuments()
    private let statisticsQueue = DispatchQueue(label: "com.poetscorner.invoiceqr.statistics", qos: .userInitiated)
    private static let photoLog = Logger(subsystem: "com.poetscorner.invoiceqr.preview", category: "photo-scan")

    func languagePreference() -> InvoiceLanguagePreference {
        switch InvoiceLanguagePreferences.choice {
        case .device: return .device
        case .arabic: return .arabic
        case .english: return .english
        }
    }

    func deviceLanguage() -> InvoiceLanguage {
        InvoiceLanguagePreferences.deviceUsesArabic ? .arabic : .english
    }

    func saveLanguage(preference: InvoiceLanguagePreference) {
        if preference == .arabic {
            InvoiceLanguagePreferences.save(.arabic)
        } else if preference == .english {
            InvoiceLanguagePreferences.save(.english)
        } else {
            InvoiceLanguagePreferences.save(.device)
        }
    }

    func selectedEngine() -> InvoiceQrEngine { InvoiceQrReader.selected }
    func mlKitAvailable() -> Bool { InvoiceQrReader.mlKitAvailable }
    func setEngine(engine: InvoiceQrEngine) { InvoiceQrReader.save(engine) }
    func zxingSettings() -> InvoiceZxingSettings { InvoiceQrReader.zxingSettings }
    func setZxingSettings(settings: InvoiceZxingSettings) { InvoiceQrReader.save(settings) }
    func scanResolution() -> String { UserDefaults.standard.string(forKey: "invoiceScanResolution") ?? "HD" }
    func setScanResolution(resolution: String) { UserDefaults.standard.set(resolution, forKey: "invoiceScanResolution") }
    func scanSpeed() -> String { UserDefaults.standard.string(forKey: "invoiceScanSpeed") ?? "NORMAL" }
    func setScanSpeed(speed: String) { UserDefaults.standard.set(speed, forKey: "invoiceScanSpeed") }
    func appearanceMode() -> String { UserDefaults.standard.string(forKey: "invoiceAppearanceMode") ?? "SYSTEM" }
    func setAppearanceMode(mode: String) { UserDefaults.standard.set(mode, forKey: "invoiceAppearanceMode") }
    func appearanceColor() -> String { UserDefaults.standard.string(forKey: "invoiceAppearanceColor") ?? "DEFAULT" }
    func setAppearanceColor(color: String) { UserDefaults.standard.set(color, forKey: "invoiceAppearanceColor") }
    func cropEnabled() -> Bool {
        UserDefaults.standard.object(forKey: "invoiceScanCrop") as? Bool ?? true
    }
    func setCropEnabled(enabled: Bool) { UserDefaults.standard.set(enabled, forKey: "invoiceScanCrop") }
    func cameraAvailable() -> Bool { Self.backCamera != nil }
    func torchAvailable() -> Bool { Self.backCamera?.hasTorch == true }

    private static var backCamera: AVCaptureDevice? {
        AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back)
    }

    func makeCamera(detection: InvoiceDetection) -> UIViewController {
        InvoiceCameraViewController(detection: detection)
    }

    func configureCamera(controller: UIViewController, configuration: InvoiceCameraConfiguration) {
        (controller as? InvoiceCameraViewController)?.update(configuration)
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

    func loadStatistics(query: InvoiceStatisticsQuery, observer: InvoiceStatisticsObserver) {
        statisticsQueue.async {
            let result = try? SQLiteInvoiceHistory().statistics(query)
            DispatchQueue.main.async { observer.complete(snapshot: result) }
        }
    }

    func exportHistoryCsv(query: InvoiceHistoryQuery, language: InvoiceLanguage, observer: InvoiceCsvObserver) {
        csvDocuments.exportHistory(from: presenter, query: query, language: language, observer: observer)
    }

    func exportStatisticsCsv(snapshot: InvoiceStatisticsSnapshot, groupingLabel: String, net: Bool,
                             language: InvoiceLanguage, observer: InvoiceCsvObserver) {
        csvDocuments.exportStatistics(from: presenter, snapshot: snapshot, groupingLabel: groupingLabel,
                                      net: net, language: language, observer: observer)
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

    func importFiles(observer: InvoiceFilesObserver) {
        fileDocuments.open(from: presenter, observer: observer)
    }

    func importSharedFiles(observer: InvoiceFilesObserver) -> Bool {
        fileDocuments.importSharedFiles(observer: observer)
    }

    func cancelFileImport() {
        fileDocuments.cancel()
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
        let engine = InvoiceQrReader.selected
        let zxing = InvoiceQrReader.zxingSettings
        provider.loadDataRepresentation(forTypeIdentifier: imageType) { [weak self, weak picker] data, _ in
            let source = data.flatMap { CGImageSourceCreateWithData($0 as CFData, nil) }
            let candidates = source.flatMap { try? InvoiceQrReader(engine: engine, zxing: zxing).read($0) } ?? []
            Self.photoLog.info("QR codes in selected photo: \(candidates.count).")
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

}

private final class InvoiceCameraViewController: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    private let detection: InvoiceDetection
    private let session = AVCaptureSession()
    private let captureQueue = DispatchQueue(label: "com.poetscorner.invoiceqr.camera", qos: .userInitiated)
    private lazy var preview = AVCaptureVideoPreviewLayer(session: session)
    private let status = UILabel()
    private var visible = false
    private var accepted = false
    private var configuration: InvoiceCameraConfiguration?

    // Accessed exclusively on captureQueue.
    private var device: AVCaptureDevice?
    private let videoOutput = AVCaptureVideoDataOutput()
    private let imageContext = CIContext()
    private var captureConfiguration: InvoiceCameraConfiguration?
    private var viewportSize: CGSize = .zero
    private var lastFrame: TimeInterval = 0
    private var reader: InvoiceQrReader?
    private let recovery = InvoiceIosFrameRecovery()

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

        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(focus(_:))))

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
        let orientation: AVCaptureVideoOrientation
        switch view.window?.windowScene?.interfaceOrientation {
        case .landscapeLeft: orientation = .landscapeLeft
        case .landscapeRight: orientation = .landscapeRight
        case .portraitUpsideDown: orientation = .portraitUpsideDown
        default: orientation = .portrait
        }
        if let connection = preview.connection, connection.isVideoOrientationSupported {
            connection.videoOrientation = orientation
        }
        let size = view.bounds.size
        captureQueue.async { [self] in
            if viewportSize != size { recovery.reset() }
            viewportSize = size
            if let connection = videoOutput.connection(with: .video), connection.isVideoOrientationSupported,
               connection.videoOrientation != orientation {
                connection.videoOrientation = orientation
            }
        }
    }

    func update(_ configuration: InvoiceCameraConfiguration) {
        guard self.configuration != configuration else { return }
        self.configuration = configuration
        captureQueue.async { [self] in
            recovery.reset()
            if let device, captureConfiguration?.resolution != configuration.resolution {
                session.beginConfiguration()
                applyResolution(configuration.resolution, to: device)
                session.commitConfiguration()
            }
            captureConfiguration = configuration
            updateDeviceControls()
        }
    }

    @objc private func focus(_ gesture: UITapGestureRecognizer) {
        let location = gesture.location(in: view)
        let point = preview.captureDevicePointConverted(fromLayerPoint: location)
        captureQueue.async { [self] in
            guard let device, session.isRunning else { return }
            do {
                try device.lockForConfiguration()
                defer { device.unlockForConfiguration() }
                if device.isFocusPointOfInterestSupported, device.isFocusModeSupported(.autoFocus) {
                    device.focusPointOfInterest = point
                    device.focusMode = .autoFocus
                }
                if device.isExposurePointOfInterestSupported, device.isExposureModeSupported(.continuousAutoExposure) {
                    device.exposurePointOfInterest = point
                    device.exposureMode = .continuousAutoExposure
                }
            } catch {
                return
            }
        }
        let ring = UIView(frame: CGRect(x: location.x - 28, y: location.y - 28, width: 56, height: 56))
        ring.layer.cornerRadius = 28
        ring.layer.borderWidth = 2
        ring.layer.borderColor = UIColor.white.cgColor
        ring.isUserInteractionEnabled = false
        view.addSubview(ring)
        UIView.animate(withDuration: 0.4, delay: 0.6, animations: { ring.alpha = 0 }) { _ in ring.removeFromSuperview() }
    }

    private func updateDeviceControls() {
        guard let device, let configuration = captureConfiguration, session.isRunning else { return }
        do {
            try device.lockForConfiguration()
            defer { device.unlockForConfiguration() }
            let minZoom = device.minAvailableVideoZoomFactor
            let maxZoom = min(device.maxAvailableVideoZoomFactor, 10)
            let offset = min(1, max(0, CGFloat(configuration.zoom)))
            device.videoZoomFactor = minZoom + max(0, maxZoom - minZoom) * offset
            let torch: AVCaptureDevice.TorchMode = configuration.torch ? .on : .off
            if device.hasTorch, device.isTorchModeSupported(torch), device.torchMode != torch {
                device.torchMode = torch
            }
        } catch {
            return
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
            lastFrame = 0
            recovery.reset()
        }
    }

    private func beginCamera() {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            status.text = InvoiceLanguagePreferences.text("الكاميرا غير متاحة على هذا الجهاز. يمكنك اختيار صورة.", "The camera is unavailable on this device. You can choose a photo.")
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
            status.text = InvoiceLanguagePreferences.text("تعذّر تشغيل الكاميرا. يمكنك اختيار صورة.", "Could not start the camera. You can choose a photo.")
        }
    }

    private func showPermissionMessage() {
        status.text = InvoiceLanguagePreferences.text("اسمح باستخدام الكاميرا من إعدادات iPhone أو اختر صورة.", "Allow camera access in iPhone Settings or choose a photo.")
    }

    private func startCamera(_ device: AVCaptureDevice) {
        status.text = InvoiceLanguagePreferences.text("جارٍ فتح الكاميرا…", "Opening camera…")
        captureQueue.async { [weak self] in
            guard let self else { return }
            if self.device == nil && self.configureCamera(device: device) {
                self.device = device
            }
            if self.device != nil && !self.session.isRunning {
                self.session.startRunning()
                self.updateDeviceControls()
            }
            let running = self.session.isRunning
            DispatchQueue.main.async {
                guard self.visible else { return }
                self.status.text = running ? nil : InvoiceLanguagePreferences.text("تعذّر تشغيل الكاميرا. يمكنك اختيار صورة.", "Could not start the camera. You can choose a photo.")
                self.view.setNeedsLayout()
            }
        }
    }

    private func configureCamera(device: AVCaptureDevice) -> Bool {
        guard let input = try? AVCaptureDeviceInput(device: device) else { return false }
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        if session.canSetSessionPreset(.hd1280x720) { session.sessionPreset = .hd1280x720 }
        guard session.canAddInput(input) else { return false }
        session.addInput(input)
        guard session.canAddOutput(videoOutput) else {
            session.removeInput(input)
            return false
        }
        videoOutput.alwaysDiscardsLateVideoFrames = true
        videoOutput.videoSettings = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
        ]
        videoOutput.setSampleBufferDelegate(self, queue: captureQueue)
        session.addOutput(videoOutput)
        if let resolution = captureConfiguration?.resolution, resolution != .hd {
            applyResolution(resolution, to: device)
        }
        return true
    }

    private func applyResolution(_ resolution: InvoiceScanResolution, to device: AVCaptureDevice) {
        let targetPixels = Int(resolution.width) * Int(resolution.height)
        var lower: (format: AVCaptureDevice.Format, pixels: Int)?
        var higher: (format: AVCaptureDevice.Format, pixels: Int)?
        for format in device.formats {
            let description = format.formatDescription
            let pixelType = CMFormatDescriptionGetMediaSubType(description)
            guard pixelType == kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange ||
                pixelType == kCVPixelFormatType_420YpCbCr8BiPlanarFullRange else { continue }
            guard format.videoSupportedFrameRateRanges.contains(where: { $0.minFrameRate <= 30 && $0.maxFrameRate >= 24 }) else {
                continue
            }
            let dimensions = CMVideoFormatDescriptionGetDimensions(description)
            let area = Int(dimensions.width) * Int(dimensions.height)
            if area <= targetPixels {
                if area > (lower?.pixels ?? 0) { lower = (format, area) }
            } else if area < (higher?.pixels ?? Int.max) {
                higher = (format, area)
            }
        }
        guard let format = (lower ?? higher)?.format, device.activeFormat != format else { return }
        do {
            try device.lockForConfiguration()
            defer { device.unlockForConfiguration() }
            device.activeFormat = format
        } catch {
            return
        }
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer,
                       from connection: AVCaptureConnection) {
        let now = ProcessInfo.processInfo.systemUptime
        guard let configuration = captureConfiguration,
              now - lastFrame >= TimeInterval(configuration.intervalMillis) / 1_000,
              let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lastFrame = now

        let input = CIImage(cvPixelBuffer: buffer)
        var region = input.extent
        if configuration.cropEnabled {
            guard viewportSize.width > 0, viewportSize.height > 0 else { return }
            let scale = max(viewportSize.width / region.width, viewportSize.height / region.height)
            let scaledWidth = region.width * scale
            let scaledHeight = region.height * scale
            let x = (CGFloat(configuration.left) * viewportSize.width + (scaledWidth - viewportSize.width) / 2) / scale
            let y = (CGFloat(configuration.top) * viewportSize.height + (scaledHeight - viewportSize.height) / 2) / scale
            let width = CGFloat(configuration.width) * viewportSize.width / scale
            let height = CGFloat(configuration.height) * viewportSize.height / scale
            region = CGRect(x: x, y: region.height - y - height, width: width, height: height)
                .integral.intersection(input.extent)
        }
        guard !region.isEmpty, let image = imageContext.createCGImage(input, from: region) else { return }
        if reader?.engine != configuration.engine || reader?.zxing != configuration.zxing {
            reader = InvoiceQrReader(engine: configuration.engine, zxing: configuration.zxing)
        }
        guard let reader else { return }
        let nowMillis = Int64(now * 1000)
        let decoded = reader.read(image, maxSymbols: 1)
        let codes: [String]
        if !decoded.isEmpty {
            recovery.reset()
            codes = decoded
        } else if configuration.cropEnabled, configuration.engine == .zxing, configuration.zxing.multiFrameRecovery,
                  let recovered = recover(image, at: nowMillis, reader: reader) {
            codes = [recovered]
        } else {
            codes = []
        }
        DispatchQueue.main.async { [weak self] in
            guard let self, self.visible, !self.accepted, self.configuration == configuration else { return }
            self.accepted = self.detection.acceptCameraFrame(rawCodes: codes, nowMillis: nowMillis)
            if self.accepted { self.stopCamera() }
        }
    }
    private func recover(_ image: CGImage, at nowMillis: Int64, reader: InvoiceQrReader) -> String? {
        guard image.width >= 100, image.height >= 100 else { return nil }
        let side = 512
        let size = side * side
        let colorSpace = CGColorSpaceCreateDeviceGray()
        guard let context = CGContext(data: nil, width: side, height: side, bitsPerComponent: 8,
            bytesPerRow: side, space: colorSpace, bitmapInfo: CGImageAlphaInfo.none.rawValue),
            let pixels = context.data else { return nil }
        context.setFillColor(gray: 1, alpha: 1)
        context.fill(CGRect(x: 0, y: 0, width: side, height: side))
        context.draw(image, in: CGRect(x: 0, y: 0, width: side, height: side))
        return recovery.offer(pixels: Data(bytes: pixels, count: size), nowMillis: nowMillis) { binary in
            guard let provider = CGDataProvider(data: binary as CFData),
                  let thresholded = CGImage(width: side, height: side, bitsPerComponent: 8, bitsPerPixel: 8,
                      bytesPerRow: side, space: colorSpace, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue),
                      provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent) else { return nil }
            let results = reader.read(thresholded, maxSymbols: 2)
            return results.count == 1 ? results.first : nil
        }
    }

}
