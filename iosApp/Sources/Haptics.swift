import Shared
import UIKit

/// Keeps the Taptic Engine warmed up so the tap lands on the same frame as the touch.
@MainActor
final class Haptics {
    private let confirm = UIImpactFeedbackGenerator(style: .medium)
    private let reject = UINotificationFeedbackGenerator()

    init() {
        confirm.prepare()
        reject.prepare()
    }

    func play(_ kind: HapticKind) {
        switch kind {
        case HapticKind.confirm:
            confirm.impactOccurred()
            confirm.prepare()
        case HapticKind.reject:
            reject.notificationOccurred(.warning)
            reject.prepare()
        default:
            break
        }
    }
}
