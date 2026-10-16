import Shared
import SwiftUI

struct BoardScreen: View {
    @EnvironmentObject private var model: BoardModel
    @State private var showSettings = false

    var body: some View {
        NavigationStack {
            // The board gets as much room as possible. In landscape the chrome moves into a side
            // column so the slab can use the full height; in portrait it stacks around the board.
            GeometryReader { proxy in
                if proxy.size.width > proxy.size.height {
                    HStack(spacing: 16) {
                        VStack(spacing: 12) {
                            layoutPicker
                            SentenceStrip(maxLines: 8)
                            Spacer()
                            ControlBar(grid: true)
                        }
                        .frame(width: proxy.size.width * 0.28)
                        BoardView()
                    }
                } else {
                    VStack(spacing: 12) {
                        layoutPicker
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
                ToolbarItem(placement: .topBarTrailing) {
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
        }
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
                    control("Speak", systemImage: "speaker.wave.2", action: model.speak)
                    control("Clear", systemImage: "trash", action: model.clear)
                }
            }
        } else {
            HStack(spacing: 12) {
                control("Delete", systemImage: "delete.left", action: model.backspace)
                control("Space", systemImage: "space", action: model.space)
                control("Speak", systemImage: "speaker.wave.2", action: model.speak)
                control("Clear", systemImage: "trash", action: model.clear)
            }
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
