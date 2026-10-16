import Combine
import Foundation
import Shared
import SwiftUI

/// Which short-lived highlight a cell is showing.
enum FlashKind {
    case committed
    case repeated
}

/// How long a cell stays lit after it was chosen or noticed.
let flashSeconds: TimeInterval = 0.35

/// Owns the shared `BoardSession` and turns its callbacks into SwiftUI state plus sound and haptics.
@MainActor
final class BoardModel: ObservableObject {
    @Published private(set) var state: BoardState
    @Published private(set) var flashes: [String: FlashKind] = [:]
    @Published private(set) var popped: String?
    @Published private(set) var popCount = 0

    private let store = SettingsStore(defaults: .standard)
    private var session: BoardSession!
    private let voice = LetterVoice()
    private let haptics = Haptics()
    private var flashTasks: [String: Task<Void, Never>] = [:]
    private var popTask: Task<Void, Never>?

    init() {
        let settings = store.load()
        let bridge = Bridge()
        let session = BoardSession(
            settings: settings, listener: bridge, historyStore: PreferencesHistoryStore(defaults: .standard))
        self.session = session
        self.state = session.state
        bridge.model = self
        voice.select(voiceId: settings.voiceId)
        voice.prepare(phrases: session.layout.keys.map(\.spoken))
    }

    var layouts: [BoardLayout] { BoardLayouts.shared.all }

    var availableVoices: [VoiceOption] { LetterVoice.availableVoices() }

    var settings: BoardSettings { state.settings }

    func resize(width: CGFloat, height: CGFloat, gap: CGFloat, padding: CGFloat) {
        session.resize(width: Float(width), height: Float(height), gap: Float(gap), padding: Float(padding))
    }

    /// Cells for drawing, in the same coordinates the view reports touches in.
    var cells: [CellRect] { session.geometry?.cells ?? [] }

    func handle(_ event: PointerEvent) {
        session.handlePointer(event: event)
    }

    func update(_ settings: BoardSettings) {
        session.updateSettings(newSettings: settings)
        store.save(settings: settings)
        voice.select(voiceId: settings.voiceId)
        voice.prepare(phrases: session.layout.keys.map(\.spoken))
    }

    func selectLayout(_ id: String) {
        update(settings.with(layoutId: id))
    }

    func space() { session.space() }
    func backspace() { session.backspace() }
    func clear() { session.clear() }
    func speak() { session.speak() }

    func recordSentence() { session.recordSentence() }
    func speakHistoryEntry(_ id: String) { session.speakHistoryEntry(id: id) }
    func restoreHistoryEntry(_ id: String) { session.restoreHistoryEntry(id: id) }
    func deleteHistoryEntry(_ id: String) { session.deleteHistoryEntry(id: id) }
    func clearHistory() { session.clearHistory() }

    var historyText: String { session.historyText }

    fileprivate func stateChanged(_ state: BoardState) {
        self.state = state
    }

    fileprivate func feedback(_ feedback: Feedback) {
        if let haptic = feedback.haptic {
            haptics.play(haptic)
        }
        switch feedback.kind {
        case FeedbackKind.say:
            voice.say(feedback.text ?? "", interrupt: feedback.interrupt)
        case FeedbackKind.keyCommitted:
            guard let keyId = feedback.keyId else { return }
            flash(keyId, .committed)
            pop(session.layout.key(id: keyId)?.label)
        case FeedbackKind.keyRepeated:
            guard let keyId = feedback.keyId else { return }
            flash(keyId, .repeated)
        default:
            break
        }
    }

    private func flash(_ keyId: String, _ kind: FlashKind) {
        flashes[keyId] = kind
        flashTasks[keyId]?.cancel()
        flashTasks[keyId] = Task { [weak self] in
            try? await Task.sleep(for: .seconds(flashSeconds))
            guard !Task.isCancelled, let self else { return }
            if self.flashes[keyId] == kind {
                self.flashes.removeValue(forKey: keyId)
            }
        }
    }

    private func pop(_ label: String?) {
        guard let label else { return }
        popped = label
        popCount += 1
        popTask?.cancel()
        popTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(flashSeconds * 2))
            guard !Task.isCancelled else { return }
            self?.popped = nil
        }
    }
}

/// Kotlin calls back on whatever thread drove the session, which is always the main thread here.
private final class Bridge: BoardSessionListener, @unchecked Sendable {
    weak var model: BoardModel?

    func onStateChanged(state: BoardState) {
        MainActor.assumeIsolated { model?.stateChanged(state) }
    }

    func onFeedback(feedback: Feedback) {
        MainActor.assumeIsolated { model?.feedback(feedback) }
    }
}
