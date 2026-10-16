import Shared
import SwiftUI

struct BoardScreen: View {
    @EnvironmentObject private var model: BoardModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var showSettings = false
    @State private var showHistory = false
    @State private var showLessons = false
    @State private var copied = false

    var body: some View {
        NavigationStack {
            // The board gets as much room as possible. In landscape the chrome moves into a side
            // column so the slab can use the full height; in portrait it stacks around the board.
            GeometryReader { proxy in
                let lesson = model.state.lesson
                if proxy.size.width > proxy.size.height {
                    HStack(spacing: 16) {
                        VStack(spacing: 12) {
                            layoutPicker
                            SentenceStrip(maxLines: 8)
                            if let lesson {
                                LessonPanel(view: lesson)
                            }
                            Spacer(minLength: 0)
                            ControlBar(grid: true)
                        }
                        .frame(width: proxy.size.width * (lesson == nil ? 0.28 : 0.36))
                        BoardView()
                    }
                } else {
                    VStack(spacing: 12) {
                        layoutPicker
                        if let lesson {
                            LessonPanel(view: lesson, maxBodyHeight: proxy.size.height * 0.22)
                        }
                        SentenceStrip(maxLines: 2)
                        BoardView()
                        ControlBar(grid: false)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 12)
            .navigationTitle("Spelling Board")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button {
                        UIPasteboard.general.string = sentence
                        model.recordSentence()
                        copied = true
                    } label: {
                        Label("Copy", systemImage: copied ? "checkmark" : "doc.on.doc")
                    }
                    .disabled(sentence.isEmpty)
                    ShareLink(item: sentence) {
                        Label("Share", systemImage: "square.and.arrow.up")
                    }
                    .disabled(sentence.isEmpty)
                    .simultaneousGesture(TapGesture().onEnded { model.recordSentence() })
                    Button {
                        showLessons = true
                    } label: {
                        Label("Lessons", systemImage: "book")
                    }
                    Button {
                        showHistory = true
                    } label: {
                        Label("History", systemImage: "clock")
                    }
                    Button {
                        showSettings = true
                    } label: {
                        Label("Settings", systemImage: "gearshape")
                    }
                }
            }
            .sheet(isPresented: $showSettings) {
                SettingsView()
            }
            .sheet(isPresented: $showHistory) {
                HistoryView()
            }
            .sheet(isPresented: $showLessons) {
                LessonsView()
            }
            .onChange(of: copied) { _, isCopied in
                guard isCopied else { return }
                Task {
                    try? await Task.sleep(for: .seconds(1.5))
                    copied = false
                }
            }
            // A tablet dimming mid-sentence is a real problem with slow spellers, so the board keeps the screen on.
            .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
            .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
            // Leaving the app stops a long read-aloud rather than letting it run on.
            .onChange(of: scenePhase) { _, phase in
                if phase != .active { model.stopSpeaking() }
            }
        }
    }

    private var sentence: String {
        model.state.text.trimmingCharacters(in: .whitespaces)
    }

    private var layoutPicker: some View {
        Picker("Board", selection: layoutBinding) {
            ForEach(model.layouts, id: \.id) { layout in
                Text(layout.name).tag(layout.id)
            }
        }
        .pickerStyle(.segmented)
    }

    private var layoutBinding: Binding<String> {
        Binding(
            get: { model.state.layout.id },
            set: { model.selectLayout($0) }
        )
    }
}

private struct SentenceStrip: View {
    @EnvironmentObject private var model: BoardModel
    let maxLines: Int

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            if model.state.text.isEmpty {
                Text("Touch a letter to start spelling")
                    .font(.title2)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Text(model.state.text)
                    .font(.system(.largeTitle, design: .rounded, weight: .semibold))
                    .tracking(2)
                    .lineLimit(maxLines)
                    .minimumScaleFactor(0.5)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            ZStack {
                if let popped = model.popped {
                    Text(popped)
                        .font(.system(size: 44, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.accentColor)
                        .id(model.popCount)
                        .transition(.scale(scale: 0.4).combined(with: .opacity))
                }
            }
            .frame(width: 64, height: 64)
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: model.popCount)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, minHeight: 88)
        .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// Delete, Space, Speak and Clear: one row of four, or two rows of two beside the board.
private struct ControlBar: View {
    @EnvironmentObject private var model: BoardModel
    let grid: Bool

    var body: some View {
        if grid {
            VStack(spacing: 12) {
                HStack(spacing: 12) {
                    control("Delete", systemImage: "delete.left", action: model.backspace)
                    control("Space", systemImage: "space", action: model.space)
                }
                HStack(spacing: 12) {
                    speakOrStop
                    control("Clear", systemImage: "trash", action: model.clear)
                }
            }
        } else {
            HStack(spacing: 12) {
                control("Delete", systemImage: "delete.left", action: model.backspace)
                control("Space", systemImage: "space", action: model.space)
                speakOrStop
                control("Clear", systemImage: "trash", action: model.clear)
            }
        }
    }

    /// Speak becomes Stop while something is being read, so there is always a way to cut it off.
    @ViewBuilder
    private var speakOrStop: some View {
        if model.speaking {
            control("Stop", systemImage: "stop.fill", action: model.stopSpeaking)
        } else {
            control("Speak", systemImage: "speaker.wave.2", action: model.speak)
        }
    }

    private func control(_ title: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: systemImage)
                    .font(.title2)
                Text(title)
                    .font(.caption)
            }
            .frame(maxWidth: .infinity, minHeight: 56)
        }
        .buttonStyle(.bordered)
        .controlSize(.large)
    }
}
