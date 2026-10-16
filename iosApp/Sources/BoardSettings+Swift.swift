import Shared

// Kotlin data classes are immutable, so handing them between isolation domains is safe.
extension BoardState: @unchecked @retroactive Sendable {}
extension Feedback: @unchecked @retroactive Sendable {}

/// Kotlin's `copy` loses its default arguments on the way to Swift, so this fills them in.
extension BoardSettings {
    func with(
        layoutId: String? = nil,
        style: BoardStyle? = nil,
        commitMode: CommitMode? = nil,
        refractoryMillis: Int64? = nil,
        stylusOnly: Bool? = nil,
        letterSounds: Bool? = nil,
        speakWords: Bool? = nil,
        haptics: Bool? = nil,
        keepHistory: Bool? = nil,
        hubUrl: String? = nil
    ) -> BoardSettings {
        BoardSettings(
            layoutId: layoutId ?? self.layoutId,
            style: style ?? self.style,
            commitMode: commitMode ?? self.commitMode,
            refractoryMillis: refractoryMillis ?? self.refractoryMillis,
            stylusOnly: stylusOnly ?? self.stylusOnly,
            letterSounds: letterSounds ?? self.letterSounds,
            speakWords: speakWords ?? self.speakWords,
            haptics: haptics ?? self.haptics,
            voiceId: voiceId,
            keepHistory: keepHistory ?? self.keepHistory,
            hubUrl: hubUrl ?? self.hubUrl
        )
    }

    /// The voice is optional in the model, so clearing it needs its own call.
    func withVoice(_ voiceId: String?) -> BoardSettings {
        BoardSettings(
            layoutId: layoutId,
            style: style,
            commitMode: commitMode,
            refractoryMillis: refractoryMillis,
            stylusOnly: stylusOnly,
            letterSounds: letterSounds,
            speakWords: speakWords,
            haptics: haptics,
            voiceId: voiceId,
            keepHistory: keepHistory,
            hubUrl: hubUrl
        )
    }
}
