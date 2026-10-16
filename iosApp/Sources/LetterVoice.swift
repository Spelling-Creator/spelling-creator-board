import AVFoundation
import Foundation

/// A system voice the partner can pick in settings.
struct VoiceOption: Identifiable, Hashable {
    let id: String
    let name: String
    let quality: String
    let language: String
}

/// Says letters with as little delay as possible.
///
/// `AVSpeechSynthesizer` takes long enough to start speaking that a speller presses the letter
/// again. So each short phrase is rendered once into a PCM buffer and played through an
/// `AVAudioEngine` on later presses, which starts within a few milliseconds. Until a clip is
/// ready, the live synthesizer is used so nothing is ever silent.
///
/// The voice matters for single letters: compact voices blur Z and C, B and P. With no voice
/// chosen, the best installed voice for the current language is used.
final class LetterVoice {
    private let synthesizer = AVSpeechSynthesizer()
    private let renderer = AVSpeechSynthesizer()
    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private var clips: [String: AVAudioPCMBuffer] = [:]
    private var rendering: Set<String> = []
    private var queue: [String] = []
    private var phrases: [String] = []
    private var connectedFormat: AVAudioFormat?
    private var voice: AVSpeechSynthesisVoice? = LetterVoice.bestVoice()

    init() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try? session.setActive(true)
        engine.attach(player)
    }

    /// Installed voices for the current language, best first. Falls back to every voice if none match.
    static func availableVoices() -> [VoiceOption] {
        rankedVoices().map { voice in
            VoiceOption(
                id: voice.identifier, name: voice.name, quality: qualityLabel(voice.quality), language: voice.language)
        }
    }

    /// Switches voice, or back to the best installed one with nil, and re-renders the clips.
    func select(voiceId: String?) {
        let chosen = voiceId.flatMap(AVSpeechSynthesisVoice.init(identifier:)) ?? Self.bestVoice()
        guard chosen?.identifier != voice?.identifier else { return }
        voice = chosen
        clips.removeAll()
        queue.removeAll()
        prepare(phrases: phrases)
    }

    /// Renders clips for these phrases in the background, one at a time.
    func prepare(phrases: [String]) {
        self.phrases = phrases
        for phrase in phrases where clips[phrase] == nil && !rendering.contains(phrase) && !queue.contains(phrase) {
            queue.append(phrase)
        }
        renderNext()
    }

    func say(_ text: String, interrupt: Bool) {
        if let clip = clips[text], start(with: clip.format) {
            if interrupt {
                player.stop()
                synthesizer.stopSpeaking(at: .immediate)
            }
            player.scheduleBuffer(clip, at: nil, options: interrupt ? .interrupts : [])
            player.play()
            return
        }
        if interrupt {
            player.stop()
            synthesizer.stopSpeaking(at: .immediate)
        }
        let live = utterance(for: text)
        // A moment of silence first wakes the audio output, so the first consonant is not lost.
        live.preUtteranceDelay = Self.onsetSilenceSeconds
        synthesizer.speak(live)
    }

    /// Silence at the start of every sound. The audio output wakes up over the first few tens of
    /// milliseconds, and anything spoken in that window is lost: "boo" arrives as "oo".
    private static let onsetSilenceSeconds: TimeInterval = 0.06

    /// A single character is spoken as a letter name, a little slower than running speech, so
    /// pairs like Z and C keep their difference. Words and sentences are spoken as given.
    private func utterance(for text: String) -> AVSpeechUtterance {
        let utterance: AVSpeechUtterance
        if text.count == 1, let letter = AVSpeechUtterance(ssmlRepresentation: Self.letterSsml(text)) {
            letter.rate = AVSpeechUtteranceDefaultSpeechRate * 0.8
            utterance = letter
        } else {
            utterance = AVSpeechUtterance(string: text)
            utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        }
        utterance.voice = voice
        return utterance
    }

    private static func letterSsml(_ character: String) -> String {
        let escaped =
            character
            .replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
        return "<speak><say-as interpret-as=\"characters\">\(escaped)</say-as></speak>"
    }

    /// The automatic choice: the best natural voice. Novelty and Eloquence voices are never picked
    /// unless they are all there is, since Albert or Grandpa reading letters is not what anyone wants.
    private static func bestVoice() -> AVSpeechSynthesisVoice? {
        let ranked = rankedVoices()
        return ranked.first { isNatural($0) } ?? ranked.first
    }

    /// Voices for the current language: natural voices before novelty ones, premium before
    /// enhanced before compact, an exact region match (en-US over en-GB on a US device) ahead of
    /// the rest, and the system's own default voice ahead of its equals.
    private static func rankedVoices() -> [AVSpeechSynthesisVoice] {
        let current = Locale.current.identifier.replacingOccurrences(of: "_", with: "-")
        let language = current.split(separator: "-").first.map(String.init) ?? current
        let all = AVSpeechSynthesisVoice.speechVoices()
        let matching = all.filter { $0.language.hasPrefix(language) }
        let candidates = matching.isEmpty ? all : matching
        let systemDefault = AVSpeechSynthesisVoice(language: current)?.identifier
        func score(_ voice: AVSpeechSynthesisVoice) -> Int {
            (isNatural(voice) ? 16 : 0)
                + qualityRank(voice.quality) * 4
                + (voice.language == current ? 2 : 0)
                + (voice.identifier == systemDefault ? 1 : 0)
        }
        return candidates.sorted { lhs, rhs in
            let lhsScore = score(lhs)
            let rhsScore = score(rhs)
            if lhsScore != rhsScore { return lhsScore > rhsScore }
            return lhs.name < rhs.name
        }
    }

    /// Apple's regular voices live under `com.apple.voice` and `com.apple.ttsbundle`. The novelty
    /// voices (Albert, Bells, Zarvox) are `com.apple.speech.synthesis.voice`, and the Eloquence
    /// voices (Eddy, Grandma, Rocko) are `com.apple.eloquence`.
    private static func isNatural(_ voice: AVSpeechSynthesisVoice) -> Bool {
        let id = voice.identifier
        return !id.hasPrefix("com.apple.speech.synthesis.voice.") && !id.hasPrefix("com.apple.eloquence.")
    }

    private static func qualityRank(_ quality: AVSpeechSynthesisVoiceQuality) -> Int {
        switch quality {
        case .premium: return 2
        case .enhanced: return 1
        default: return 0
        }
    }

    private static func qualityLabel(_ quality: AVSpeechSynthesisVoiceQuality) -> String {
        switch quality {
        case .premium: return "Premium"
        case .enhanced: return "Enhanced"
        default: return "Compact"
        }
    }

    private func renderNext() {
        guard rendering.isEmpty, let phrase = queue.first else { return }
        queue.removeFirst()
        rendering.insert(phrase)
        let renderingVoice = voice?.identifier
        var chunks: [AVAudioPCMBuffer] = []
        renderer.write(utterance(for: phrase)) { [weak self] buffer in
            guard let self else { return }
            guard let pcm = buffer as? AVAudioPCMBuffer else { return }
            if pcm.frameLength > 0 {
                chunks.append(pcm)
                return
            }
            // A zero-length buffer marks the end of the utterance. Drop it if the voice changed meanwhile.
            if renderingVoice == self.voice?.identifier, let joined = Self.join(chunks) {
                self.clips[phrase] = joined
            }
            self.rendering.remove(phrase)
            self.renderNext()
        }
    }

    private func start(with format: AVAudioFormat) -> Bool {
        if connectedFormat != format {
            engine.stop()
            engine.disconnectNodeOutput(player)
            engine.connect(player, to: engine.mainMixerNode, format: format)
            connectedFormat = format
        }
        if !engine.isRunning {
            do {
                try engine.start()
            } catch {
                return false
            }
        }
        return true
    }

    /// Joins the synthesizer's chunks into one clip, with onset silence in front.
    private static func join(_ chunks: [AVAudioPCMBuffer]) -> AVAudioPCMBuffer? {
        guard let first = chunks.first else { return nil }
        let lead = AVAudioFrameCount(first.format.sampleRate * onsetSilenceSeconds)
        let total = chunks.reduce(lead) { $0 + $1.frameLength }
        guard let joined = AVAudioPCMBuffer(pcmFormat: first.format, frameCapacity: total) else { return nil }
        let channels = Int(first.format.channelCount)
        let bytesPerFrame = Int(first.format.streamDescription.pointee.mBytesPerFrame)
        for channel in 0..<channels {
            if let target = joined.floatChannelData {
                target[channel].update(repeating: 0, count: Int(lead))
            } else if let target = joined.int16ChannelData {
                target[channel].update(repeating: 0, count: Int(lead))
            } else if let target = joined.int32ChannelData {
                target[channel].update(repeating: 0, count: Int(lead))
            } else if bytesPerFrame > 0 {
                memset(joined.audioBufferList.pointee.mBuffers.mData, 0, Int(lead) * bytesPerFrame)
            }
        }
        var offset = Int(lead)
        for chunk in chunks {
            let frames = Int(chunk.frameLength)
            for channel in 0..<channels {
                if let source = chunk.floatChannelData, let target = joined.floatChannelData {
                    target[channel].advanced(by: offset).update(from: source[channel], count: frames)
                } else if let source = chunk.int16ChannelData, let target = joined.int16ChannelData {
                    target[channel].advanced(by: offset).update(from: source[channel], count: frames)
                } else if let source = chunk.int32ChannelData, let target = joined.int32ChannelData {
                    target[channel].advanced(by: offset).update(from: source[channel], count: frames)
                }
            }
            offset += frames
        }
        joined.frameLength = total
        return joined
    }
}
