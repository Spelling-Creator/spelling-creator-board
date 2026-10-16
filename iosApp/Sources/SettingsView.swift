import Shared
import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model: BoardModel
    @Environment(\.dismiss) private var dismiss

    private let step: Int64 = 50
    private let refractoryRange =
        Double(BoardSettings.companion.MIN_REFRACTORY_MILLIS)...Double(BoardSettings.companion.MAX_REFRACTORY_MILLIS)

    var body: some View {
        NavigationStack {
            Form {
                Section("Look") {
                    Picker("Look", selection: style) {
                        Text("Stencil board").tag(BoardStyle.stencil)
                        Text("Plain keys").tag(BoardStyle.plain)
                    }
                    .pickerStyle(.segmented)
                }

                Section("Choose a letter") {
                    Picker("Choose a letter", selection: commitMode) {
                        Text("When the finger lands").tag(CommitMode.onTouchDown)
                        Text("When the finger lifts").tag(CommitMode.onTouchUp)
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }

                Section {
                    LabeledContent("Ignore the same letter again within", value: refractoryLabel)
                    Slider(value: refractory, in: refractoryRange, step: Double(step))
                } footer: {
                    Text(
                        "A second press on the same letter inside this window is shown but not added. "
                            + "Double letters still work after it passes."
                    )
                }

                Section {
                    Toggle("Stylus only", isOn: binding(\.stylusOnly) { $0.with(stylusOnly: $1) })
                } footer: {
                    Text("Fingers are ignored, so a steadying hand cannot add letters.")
                }

                Section {
                    Picker("Voice", selection: voiceId) {
                        Text("Automatic (best installed)").tag(String?.none)
                        ForEach(model.availableVoices) { voice in
                            Text("\(voice.name) (\(voice.quality))").tag(Optional(voice.id))
                        }
                    }
                    .pickerStyle(.navigationLink)
                } header: {
                    Text("Voice")
                } footer: {
                    Text(
                        "Compact voices blur letters like Z and C. Download an Enhanced or Premium voice in "
                            + "Settings, Accessibility, Spoken Content, Voices, and it will be used automatically.")
                }

                Section {
                    Toggle("Keep a history of sentences", isOn: binding(\.keepHistory) { $0.with(keepHistory: $1) })
                } header: {
                    Text("Privacy")
                } footer: {
                    Text(
                        "Finished sentences are kept on this device only, and can be deleted one at a time or all "
                            + "at once from History. Turning this off deletes them.")
                }

                Section("Feedback") {
                    Toggle("Say each letter", isOn: binding(\.letterSounds) { $0.with(letterSounds: $1) })
                    Toggle("Read each word back after Space", isOn: binding(\.speakWords) { $0.with(speakWords: $1) })
                    Toggle("Vibrate on each letter", isOn: binding(\.haptics) { $0.with(haptics: $1) })
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private var refractoryLabel: String {
        let millis = model.settings.refractoryMillis
        return millis == 0 ? "Off" : "\(millis) ms"
    }

    private var voiceId: Binding<String?> {
        Binding(
            get: { model.settings.voiceId },
            set: { model.update(model.settings.withVoice($0)) }
        )
    }

    private var style: Binding<BoardStyle> {
        Binding(
            get: { model.settings.style },
            set: { model.update(model.settings.with(style: $0)) }
        )
    }

    private var commitMode: Binding<CommitMode> {
        Binding(
            get: { model.settings.commitMode },
            set: { model.update(model.settings.with(commitMode: $0)) }
        )
    }

    private var refractory: Binding<Double> {
        Binding(
            get: { Double(model.settings.refractoryMillis) },
            set: { model.update(model.settings.with(refractoryMillis: Int64($0))) }
        )
    }

    private func binding(
        _ keyPath: KeyPath<BoardSettings, Bool>,
        _ apply: @escaping (BoardSettings, Bool) -> BoardSettings
    ) -> Binding<Bool> {
        Binding(
            get: { model.settings[keyPath: keyPath] },
            set: { model.update(apply(model.settings, $0)) }
        )
    }
}
