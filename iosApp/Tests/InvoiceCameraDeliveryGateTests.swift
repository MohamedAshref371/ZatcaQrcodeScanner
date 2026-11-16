import InvoiceUI
import XCTest

final class InvoiceCameraDeliveryGateTests: XCTestCase {
    func testHeldNormalisedCodeDeliversOnceAndRearmsAfterAbsence() {
        let gate = InvoiceCameraDeliveryGate()
        XCTAssertEqual(gate.newlyVisible(rawCodes: ["YWJj="], nowMillis: 0), ["YWJj="])
        XCTAssertEqual(gate.newlyVisible(rawCodes: ["YW Jj"], nowMillis: 400), [])
        XCTAssertEqual(gate.newlyVisible(rawCodes: ["YWJj"], nowMillis: 1200), [])
        XCTAssertEqual(gate.newlyVisible(rawCodes: [], nowMillis: 1900), [])
        XCTAssertEqual(gate.newlyVisible(rawCodes: ["YWJj"], nowMillis: 2450), ["YWJj"])
    }

    func testTwoVisibleCodesDoNotRearmEachOther() {
        let gate = InvoiceCameraDeliveryGate()
        XCTAssertEqual(gate.newlyVisible(rawCodes: ["YWJj", "ZGVm", "YWJj"], nowMillis: 0), ["YWJj", "ZGVm"])
        XCTAssertEqual(gate.newlyVisible(rawCodes: ["ZGVm", "YWJj"], nowMillis: 1000), [])
        XCTAssertEqual(gate.newlyVisible(rawCodes: ["ZGVm", "Z2hp"], nowMillis: 2000), ["Z2hp"])
        XCTAssertEqual(gate.newlyVisible(rawCodes: ["YWJj", "ZGVm", "Z2hp"], nowMillis: 2250), ["YWJj"])
    }
}
