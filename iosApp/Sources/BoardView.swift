import Shared
import SwiftUI
import UIKit

/// Draws the board and forwards raw touches to the model.
///
/// Geometry and hit testing live in the shared module. This view only paints cells from the
/// geometry and reports every touch the moment UIKit delivers it.
struct BoardView: View {
    @EnvironmentObject private var model: BoardModel
    /// Where the slab sits inside the view in stencil mode. Touches are reported relative to it.
    @State private var slabOrigin: CGPoint = .zero

    private let gap: CGFloat = 10
    private let corner: CGFloat = 14
    private let stencilAmber = Color(red: 1.0, green: 0.77, blue: 0.24)
    private let stencilOrange = Color(red: 1.0, green: 0.48, blue: 0.35)

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                Canvas { context, size in
                    switch model.settings.style {
                    case BoardStyle.stencil: drawStencil(in: &context, size: size)
                    default: drawPlain(in: &context)
                    }
                }
                TouchCaptureView { event in
                    model.handle(
                        PointerEvent(
                            pointerId: event.pointerId,
                            phase: event.phase,
                            x: event.x - Float(slabOrigin.x),
                            y: event.y - Float(slabOrigin.y),
                            timeMillis: event.timeMillis,
                            kind: event.kind
                        )
                    )
                }
            }
            .onAppear { resize(proxy.size) }
            .onChange(of: proxy.size) { _, size in resize(size) }
            .onChange(of: model.state.layout.id) { resize(proxy.size) }
            .onChange(of: model.settings.style) { resize(proxy.size) }
        }
        .accessibilityLabel("Letterboard")
    }

    private func resize(_ size: CGSize) {
        switch model.settings.style {
        case BoardStyle.stencil:
            let slab = fittedSlab(in: size)
            slabOrigin = slab.origin
            let frame = StencilFrame(width: Float(slab.width), height: Float(slab.height), hasHandle: true)
            model.resize(width: CGFloat(frame.hitWidth), height: slab.height, gap: 0, padding: CGFloat(frame.margin))
        default:
            slabOrigin = .zero
            model.resize(width: size.width, height: size.height, gap: gap, padding: 0)
        }
    }

    private func fittedSlab(in size: CGSize) -> CGRect {
        let box = StencilFrame.companion.fit(
            availableWidth: Float(size.width), availableHeight: Float(size.height),
            minAspect: StencilFrame.companion.MIN_ASPECT, maxAspect: StencilFrame.companion.MAX_ASPECT)
        return CGRect(x: CGFloat(box.left), y: CGFloat(box.top), width: CGFloat(box.width), height: CGFloat(box.height))
    }

    private func drawPlain(in context: inout GraphicsContext) {
        let cells = model.cells
        let labelSize = CGFloat(cells.first?.height ?? 0) * 0.48
        for cell in cells {
            let rect = rect(of: cell)
            let shape = Path(roundedRect: rect, cornerRadius: corner)
            let flash = model.flashes[cell.key.id]
            let isPressed = cell.key.id == model.state.pressedKeyId
            let fill: Color
            switch flash {
            case .committed: fill = .accentColor
            case .repeated, .none: fill = isPressed ? Color(.systemFill) : Color(.secondarySystemFill)
            }
            context.fill(shape, with: .color(fill))
            if flash == .repeated {
                context.stroke(shape, with: .color(.orange), lineWidth: 6)
            }
            let label = Text(cell.key.label)
                .font(.system(size: labelSize, weight: .bold, design: .rounded))
                .foregroundColor(flash == .committed ? .white : .primary)
            context.draw(label, at: CGPoint(x: rect.midX, y: rect.midY))
        }
    }

    /// The physical board: one dark slab with the handle and every letter cut out of it. The
    /// cutouts are painted in the screen's background color, which is what shows through a real
    /// stencil. Feedback lights the cutout: amber when a letter goes in, an orange ring for a repeat.
    private func drawStencil(in context: inout GraphicsContext, size: CGSize) {
        let box = fittedSlab(in: size)
        context.translateBy(x: box.minX, y: box.minY)
        let frame = StencilFrame(width: Float(box.width), height: Float(box.height), hasHandle: true)
        let slab = Path(
            roundedRect: CGRect(origin: .zero, size: box.size),
            cornerRadius: CGFloat(frame.cornerRadius),
            style: .continuous
        )
        context.fill(slab, with: .color(Color(.label)))
        let cutout = Color(.systemBackground)
        if let handle = frame.handle {
            let handleRect = CGRect(
                x: CGFloat(handle.left), y: CGFloat(handle.top),
                width: CGFloat(handle.width), height: CGFloat(handle.height))
            context.fill(Path(roundedRect: handleRect, cornerRadius: handleRect.width / 2), with: .color(cutout))
        }
        let cells = model.cells
        guard let first = cells.first else { return }
        let labelSize = min(CGFloat(first.height) * 0.7, CGFloat(first.width) * 0.8)
        let inset = min(CGFloat(first.width), CGFloat(first.height)) * 0.08
        for cell in cells {
            let rect = rect(of: cell)
            let flash = model.flashes[cell.key.id]
            let isPressed = cell.key.id == model.state.pressedKeyId
            let glowRect = rect.insetBy(dx: inset, dy: inset)
            let glow = Path(
                roundedRect: glowRect, cornerRadius: min(rect.width, rect.height) * 0.18, style: .continuous)
            if flash == .committed {
                context.fill(glow, with: .color(stencilAmber.opacity(0.28)))
            } else if flash == .repeated {
                context.stroke(glow, with: .color(stencilOrange), lineWidth: 6)
            }
            let color: Color
            switch flash {
            case .committed: color = stencilAmber
            case .repeated, .none: color = isPressed ? Color(.systemGray4) : cutout
            }
            let label = Text(cell.key.label)
                .font(.system(size: labelSize, weight: .black))
                .foregroundColor(color)
            context.draw(label, at: CGPoint(x: rect.midX, y: rect.midY))
        }
    }

    private func rect(of cell: CellRect) -> CGRect {
        CGRect(x: CGFloat(cell.left), y: CGFloat(cell.top), width: CGFloat(cell.width), height: CGFloat(cell.height))
    }
}

/// A transparent UIKit view that reports touches as shared `PointerEvent`s.
///
/// SwiftUI gestures commit on release and hide the touch type, so the board uses UIKit directly:
/// `touchesBegan` fires the instant the finger lands, and each touch says whether it is a finger or
/// a pencil, which stylus-only mode needs.
struct TouchCaptureView: UIViewRepresentable {
    let onEvent: (PointerEvent) -> Void

    func makeUIView(context: Context) -> TouchView {
        let view = TouchView()
        view.onEvent = onEvent
        return view
    }

    func updateUIView(_ uiView: TouchView, context: Context) {
        uiView.onEvent = onEvent
    }

    final class TouchView: UIView {
        var onEvent: ((PointerEvent) -> Void)?

        override init(frame: CGRect) {
            super.init(frame: frame)
            isMultipleTouchEnabled = true
            backgroundColor = .clear
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) { nil }

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
            report(touches, phase: .down)
        }

        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
            report(touches, phase: .move)
        }

        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
            report(touches, phase: .up)
        }

        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
            report(touches, phase: .cancel)
        }

        private func report(_ touches: Set<UITouch>, phase: PointerPhase) {
            for touch in touches {
                let point = touch.location(in: self)
                let kind: PointerKind
                switch touch.type {
                case .pencil: kind = .stylus
                case .indirectPointer: kind = .mouse
                case .direct: kind = .finger
                default: kind = .unknown
                }
                onEvent?(
                    PointerEvent(
                        pointerId: Int64(touch.hash),
                        phase: phase,
                        x: Float(point.x),
                        y: Float(point.y),
                        timeMillis: Int64(touch.timestamp * 1000),
                        kind: kind
                    )
                )
            }
        }
    }
}
