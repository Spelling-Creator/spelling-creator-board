import Shared
import SwiftUI

/// The sentences the speller has finished, newest first. Everything here can be deleted at any time.
struct HistoryView: View {
    @EnvironmentObject private var model: BoardModel
    @Environment(\.dismiss) private var dismiss
    @State private var confirmDeleteAll = false

    var body: some View {
        NavigationStack {
            Group {
                if !model.settings.keepHistory {
                    ContentUnavailableView(
                        "History is off",
                        systemImage: "clock.badge.xmark",
                        description: Text("Turn it on in Settings to keep finished sentences on this device."))
                } else if model.state.history.isEmpty {
                    ContentUnavailableView(
                        "No sentences yet",
                        systemImage: "clock",
                        description: Text("Sentences are kept here when you tap Speak, Share, Copy or Clear."))
                } else {
                    List {
                        ForEach(model.state.history, id: \.id) { entry in
                            Button {
                                model.restoreHistoryEntry(entry.id)
                                dismiss()
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(entry.text)
                                        .font(.title3)
                                        .foregroundStyle(.primary)
                                    Text(Self.when(entry.timeMillis))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .swipeActions(edge: .leading) {
                                Button {
                                    model.speakHistoryEntry(entry.id)
                                } label: {
                                    Label("Speak", systemImage: "speaker.wave.2")
                                }
                                .tint(.accentColor)
                            }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    model.deleteHistoryEntry(entry.id)
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("History")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .topBarLeading) {
                    ShareLink(item: model.historyText) {
                        Label("Share all", systemImage: "square.and.arrow.up")
                    }
                    .disabled(model.state.history.isEmpty)
                    Button(role: .destructive) {
                        confirmDeleteAll = true
                    } label: {
                        Label("Delete all", systemImage: "trash")
                    }
                    .disabled(model.state.history.isEmpty)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .confirmationDialog(
                "Delete every kept sentence?", isPresented: $confirmDeleteAll, titleVisibility: .visible
            ) {
                Button("Delete all", role: .destructive) { model.clearHistory() }
            }
        }
    }

    private static func when(_ millis: Int64) -> String {
        Date(timeIntervalSince1970: TimeInterval(millis) / 1000).formatted(date: .abbreviated, time: .shortened)
    }
}
