import Foundation

enum InvoiceLanguagePreferences {
    private static let key = "invoiceAppLanguage"

    enum Choice: String {
        case device, arabic = "ar", english = "en"
    }

    static var choice: Choice {
        let defaults = Bundle.main.bundleURL.pathExtension == "appex" ? sharedDefaults : UserDefaults.standard
        return defaults?.string(forKey: key).flatMap(Choice.init(rawValue:)) ?? .device
    }

    static var deviceUsesArabic: Bool {
        let supported = Locale.preferredLanguages.lazy
            .compactMap { $0.split(whereSeparator: { $0 == "-" || $0 == "_" }).first?.lowercased() }
            .first { $0 == "ar" || $0 == "en" }
        return supported == "ar"
    }

    static var usesArabic: Bool {
        switch choice {
        case .device: return deviceUsesArabic
        case .arabic: return true
        case .english: return false
        }
    }

    static func save(_ choice: Choice) {
        UserDefaults.standard.set(choice.rawValue, forKey: key)
        sharedDefaults?.set(choice.rawValue, forKey: key)
    }

    static func text(_ arabic: String, _ english: String) -> String {
        usesArabic ? arabic : english
    }

    private static var sharedDefaults: UserDefaults? {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "InvoiceAppGroup") as? String,
              FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) != nil else {
            return nil
        }
        return UserDefaults(suiteName: group)
    }
}
