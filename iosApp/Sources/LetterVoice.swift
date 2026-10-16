import AVFoundation
import Foundation

/// Says letters with as little delay as possible.
///
/// `AVSpeechSynthesizer` takes long enough to start speaking that a speller presses the letter
/// again. So each short phrase is rendered once into a PCM buffer and played through an
/// `AVAudioEngine` on later presses, which starts within a few milliseconds. Until a clip is
/// ready, the live synthesizer is used so nothing is ever silent.
final class LetterVoice {
    private let synthesizer = AVSpeechSynthesizer()
    private let renderer = AVSpeechSynthesizer()
    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private var clips: [String: AVAudioPCMBuffer] = [:]
    private var rendering: Set<String> = []
    private var queue: [String] = []
    private var connectedFormat: AVAudioFormat?

    init() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try? session.setActive(true)
        engine.attach(player)
    }

    /// Renders clips for these phrases in the background, one at a time.
    func prepare(phrases: [String]) {
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
        synthesizer.speak(utterance(for: text))
    }

    private func utterance(for text: String) -> AVSpeechUtterance {
        let utterance = AVSpeechUtterance(string: text)
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        return utterance
    }

    private func renderNext() {
        guard rendering.isEmpty, let phrase = queue.first else { return }
        queue.removeFirst()
        rendering.insert(phrase)
        var chunks: [AVAudioPCMBuffer] = []
        renderer.write(utterance(for: phrase)) { [weak self] buffer in
            guard let self else { return }
            guard let pcm = buffer as? AVAudioPCMBuffer else { return }
            if pcm.frameLength > 0 {
                chunks.append(pcm)
                return
            }
            // A zero-length buffer marks the end of the utterance.
            if let joined = Self.join(chunks) {
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

    private static func join(_ chunks: [AVAudioPCMBuffer]) -> AVAudioPCMBuffer? {
        guard let first = chunks.first else { return nil }
        let total = chunks.reduce(AVAudioFrameCount(0)) { $0 + $1.frameLength }
        guard let joined = AVAudioPCMBuffer(pcmFormat: first.format, frameCapacity: total) else { return nil }
        let channels = Int(first.format.channelCount)
        var offset = 0
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
