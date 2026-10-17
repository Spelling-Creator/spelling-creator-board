import Shared
import SwiftUI

/// The open lesson's current step: a section's material, or one question with the board as its
/// answer box. The partner steps through it; the speller answers on the board and taps Done.
struct LessonPanel: View {
    @EnvironmentObject private var model: BoardModel
    let view: LessonView
    var maxBodyHeight: CGFloat? = nil
    @State private var showAnswers = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(view.title).font(.headline)
                    Text(subtitle).font(.caption).foregroundStyle(.secondary)
                    if model.linkState.isActive, let status = model.linkStatusText {
                        Text(status).font(.caption.weight(.semibold)).foregroundStyle(Color.accentColor)
                    }
                }
                Spacer()
                if model.speaking {
                    Button {
                        model.stopSpeaking()
                    } label: {
                        Label("Stop", systemImage: "stop.fill")
                    }
                } else {
                    Button {
                        model.speakStep()
                    } label: {
                        Label("Read aloud", systemImage: "speaker.wave.2")
                    }
                }
                Button {
                    model.endLesson()
                } label: {
                    Label("End lesson", systemImage: "xmark")
                }
            }
            .labelStyle(.iconOnly)

            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    if view.kind == LessonStepKind.question, let question = view.question {
                        questionBody(question)
                    } else {
                        contentBody
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(maxHeight: maxBodyHeight)

            HStack(spacing: 8) {
                Button {
                    model.previousStep()
                } label: {
                    Label("Previous step", systemImage: "arrow.left")
                }
                .disabled(!view.hasPrevious)
                Button {
                    model.nextStep()
                } label: {
                    Label("Next step", systemImage: "arrow.right")
                }
                .disabled(!view.hasNext)
                if view.kind == LessonStepKind.question {
                    Button {
                        model.submitAnswer()
                    } label: {
                        Label("Done", systemImage: "checkmark")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(model.state.text.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .labelStyle(.iconOnly)
        }
        .padding(16)
        .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .onChange(of: view.stepIndex) { showAnswers = false }
    }

    private var subtitle: String {
        let step = "Step \(view.stepIndex + 1) of \(view.stepCount)"
        return view.sectionName.isEmpty ? step : "\(step), \(view.sectionName)"
    }

    @ViewBuilder
    private func questionBody(_ question: LessonQuestion) -> some View {
        HStack(spacing: 8) {
            Text("\(question.tag) \(question.label)")
                .font(.caption.weight(.semibold))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.accentColor.opacity(0.15), in: Capsule())
            Text("Question \(view.questionNumber)").font(.caption)
        }
        Text(question.prompt).font(.title3)
        if let answer = view.answer {
            Text("Answered: \(answer)").font(.body.weight(.semibold))
        }
        if !question.answers.isEmpty {
            if showAnswers {
                Text((question.suggested ? "Examples: " : "Answer: ") + question.answers.joined(separator: ", "))
                    .foregroundStyle(Color.accentColor)
            } else {
                Button("Show answer") { showAnswers = true }
            }
        }
    }

    @ViewBuilder
    private var contentBody: some View {
        ForEach(Array(view.blocks.enumerated()), id: \.offset) { _, block in
            switch block.kind {
            case BlockKind.heading:
                ForEach(block.lines, id: \.self) { Text($0).font(.headline) }
            case BlockKind.image:
                ForEach(block.lines, id: \.self) { Text($0).font(.footnote).italic() }
            case BlockKind.spelling:
                Text(block.lines.joined(separator: "   ")).font(.title2.weight(.bold))
            case BlockKind.vakt:
                VStack(alignment: .leading, spacing: 4) {
                    Text("VAKT activity").font(.caption.weight(.semibold)).foregroundStyle(.red)
                    ForEach(block.lines, id: \.self) { Text($0) }
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.red, lineWidth: 2))
            default:
                ForEach(block.lines, id: \.self) { Text($0).font(.body) }
            }
        }
    }
}
