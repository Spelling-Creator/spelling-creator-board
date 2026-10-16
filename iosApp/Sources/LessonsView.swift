import Shared
import SwiftUI

/// Pick a lesson from the hub, or paste a link to one, and open it on the board.
struct LessonsView: View {
    @EnvironmentObject private var model: BoardModel
    @Environment(\.dismiss) private var dismiss
    @State private var link = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack {
                        TextField("Lesson link or id", text: $link)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)
                        Button("Open") { open(link) }
                            .disabled(link.trimmingCharacters(in: .whitespaces).isEmpty || model.lessonLoading)
                    }
                    if let error = model.lessonError {
                        Text(error).foregroundStyle(.red)
                    }
                } footer: {
                    Text("From \(model.settings.hubUrl). Nothing is sent to the hub; answers stay on this device.")
                }

                Section("On the hub") {
                    if let listing = model.lessonListing {
                        if listing.isEmpty {
                            Text("The hub has no published lessons.").foregroundStyle(.secondary)
                        }
                        ForEach(listing, id: \.id) { lesson in
                            Button {
                                open(lesson.id)
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(lesson.title).foregroundStyle(.primary)
                                    Text(subtitle(lesson)).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            .disabled(model.lessonLoading)
                        }
                    } else {
                        // Placeholder rows the shape of the ones that will replace them.
                        ForEach(0..<4, id: \.self) { _ in
                            VStack(alignment: .leading, spacing: 6) {
                                RoundedRectangle(cornerRadius: 4).fill(Color(.systemFill)).frame(width: 220, height: 16)
                                RoundedRectangle(cornerRadius: 4).fill(Color(.systemFill)).frame(width: 120, height: 12)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
            }
            .navigationTitle("Lessons")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task { model.loadLessonListing() }
        }
    }

    private func open(_ input: String) {
        model.openLesson(input) { dismiss() }
    }

    private func subtitle(_ lesson: LessonSummary) -> String {
        let sections = lesson.sectionCount == 1 ? "1 section" : "\(lesson.sectionCount) sections"
        return lesson.author.isEmpty ? sections : "\(lesson.author), \(sections)"
    }
}
