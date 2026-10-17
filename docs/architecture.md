# Architecture

Kotlin Multiplatform for everything that is not a view. SwiftUI on iOS and Jetpack Compose on Android
for the views, so each app looks and behaves like it belongs on its platform. Compose Multiplatform was
ruled out for iOS because it draws Material widgets, and Flutter because the native-only features that
matter later (a keyboard extension, Siri, widgets, an Android accessibility service) all need Swift or
Kotlin anyway.

The rule that keeps the two apps from drifting apart: layout math, hit testing, timing and the
sentence never go in a view. A view draws cells from the geometry the shared module computed, forwards
raw touches, and plays whatever feedback comes back.

## Shared module (`shared/`)

Package `org.spellingcreator.board`.

| Package    | Contents                                                                                                                                       |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `layout`   | `BoardKey`, `BoardLayout`, the built-in `BoardLayouts` (alphabet, math), `BoardGeometry`, which lays a layout out in a rectangle and answers which key a point is on, and `StencilFrame`, the proportions of the physical board (slab corners, margin, handle slot) scaled to a rectangle. |
| `input`    | `PointerEvent` (one raw touch, in board coordinates, with a monotonic timestamp and whether it was a finger or stylus) and `SelectionEngine`, which turns events into selections. |
| `text`     | `SpelledText`: the sentence, the current word, space, backspace, clear.                                                                        |
| `settings` | `BoardSettings` (per-speller preferences, serializable) and `SettingsStore` (expect/actual over `SharedPreferences` and `NSUserDefaults`).      |
| `history`  | `SentenceHistory` (finished sentences, newest first, capped at 500, no consecutive duplicates), `HistoryEntry`, and `HistoryStore` with a preferences-backed actual per platform and an in-memory one for tests. |
| `lessons`  | `Lesson` and its blocks, `LessonParser` (the hub's JSON to words), `LessonSteps` (the walkthrough), `LessonRun` (position and answers), `HubLinks` (links and ids to addresses) and `HubClient` (the two public endpoints). See `docs/integration.md`. |
| `net`      | `HttpClient`, an expect/actual GET with a main-thread callback: `HttpURLConnection` on Android, `NSURLSession` on iOS. `WebSocketFactory`, the same idea for WebSockets: OkHttp on Android, `URLSessionWebSocketTask` on iOS, pings handled by the platform. |

`lessons` also holds `PracticeLink`, which keeps the board in step with a Practice page over the
hub's letterboard session (`docs/live-sync.md`). It is a second `BoardSessionListener`: the
session has a list of listeners, the view's first, and the link sends what it hears over the
socket and drives the session with what comes back. Nothing in `BoardSession` knows about
sockets.
| `session`  | `BoardSession`, the one object the apps talk to, plus `BoardState` (what to draw) and `Feedback` (what to do right now).                        |

### `BoardSession`

Constructed with settings and a `BoardSessionListener`. The app calls:

- `resize(width, height, gap, padding)` whenever the board view's size changes. Units are whatever the
  view reports touches in (pixels on Android, points on iOS).
- `handlePointer(event)` for every touch event.
- `space()`, `backspace()`, `clear()`, `speak()`, `selectLayout(id)`, `updateSettings(settings)` from
  the control buttons and settings screen.

The listener gets `onStateChanged(state)` whenever something to draw changed, and `onFeedback(feedback)`
for each thing to do: flash a cell, say a phrase, play a haptic. `Feedback` is a flat value with a
`kind` enum and optional `keyId`, `text`, `interrupt` and `haptic`, so Swift can switch on it without
casting. Nothing in the session is asynchronous; it is driven from the UI thread and answers on it.

### Geometry

Rows may have different lengths (the alphabet board has four rows of five and one of six). Each row's
keys are spread evenly across the full width, like the cutouts on a stencil. Hit testing is "nearest
key": a touch in a gap or in the padding snaps to the closest cell, so the only way to miss is to touch
outside the board rectangle.

### History

A sentence is kept when it is spoken, shared, copied or cleared, through `recordSentence()`, and
the same text is never kept twice in a row, so Speak followed by Clear records it once. History
is part of `BoardState` so both apps render it from the same list. Privacy rules: `keepHistory`
in settings stops recording and deletes what was kept, and the session exposes delete-one and
delete-all. Entries never leave the device unless the partner shares them.

### Two looks, one geometry

`BoardSettings.style` picks how the board is drawn. `PLAIN` is rounded keys with a gap between
them. `STENCIL`, the default, is the physical board: the view asks `StencilFrame.fit` for the largest
slab that fits its area, centered. The slab fills the area outright when its proportions are
between 1:1 and 3:2, and is clamped to the nearer bound otherwise, since a bigger board matters
more than exact proportions. The view then asks `StencilFrame` for that slab's corner radius,
margin and handle slot, and calls `resize(frame.hitWidth, slab.height, gap = 0, padding =
frame.margin)`. The view draws the slab at the fitted offset and subtracts the
same offset from every touch before handing it to the session. The letters are laid out in the
space left of the handle, a touch on the margin snaps to the nearest letter, and a touch on the
handle lands past `hitWidth` and is reported as off the board. The cutouts are painted in the screen's background color, which is what shows
through a real stencil, so the board follows light and dark mode without any extra colors.

### Selection engine

The rules live in `SelectionEngine` and are unit tested in `commonTest`. The feedback design doc
explains why each exists; the short list:

- Commit on touch-down by default; touch-up is a setting.
- First touch wins. A second pointer while one is down is `IGNORED`.
- The same key again inside the refractory window is `REPEATED`, not `COMMITTED`.
- Stylus-only mode ignores non-stylus pointers.
- The engine has no clock; it uses the timestamps on the events.

### Screen layout

Both apps give the board as much room as they can. In landscape (width greater than height) the
layout picker, sentence strip and control buttons move into a side column about 28% wide, with the
buttons in a two by two grid, and the board takes the full height beside them. In portrait they
stack above and below the board. The sentence strip sizes to its text in both cases; spare space in
the side column goes to a spacer, not the strip.

## Android app (`androidApp/`)

- `BoardViewModel` owns the session so the sentence survives rotation, exposes `BoardState` as Compose
  state, and hands feedback to whatever the screen registers.
- `BoardCanvas` draws cells on a `Canvas` and reads pointer changes on the initial pass with
  `awaitPointerEventScope`, so nothing above it can delay a touch. It maps `PointerType` to the shared
  `PointerKind` and uses `uptimeMillis` as the clock.
- `LetterVoice` synthesizes each phrase to a file once with `TextToSpeech.synthesizeToFile`, loads it
  into a `SoundPool`, and plays from memory on later presses. Until a clip is ready it speaks live.
- Haptics go through Compose's `LocalHapticFeedback` with `Confirm` and `Reject`.
- `SettingsSheet` is a Material 3 modal bottom sheet.

## iOS app (`iosApp/`)

- `BoardModel` is an `ObservableObject` on the main actor. It owns the session, mirrors `BoardState`,
  keeps the short-lived flashes, and plays sound and haptics. A small `Bridge` class implements the
  Kotlin listener protocol and forwards to the model.
- `BoardView` draws cells in a SwiftUI `Canvas` and layers a `UIViewRepresentable` on top whose UIKit
  view overrides `touchesBegan` and friends. SwiftUI gestures were not used because they commit on
  release and hide the touch type. `UITouch.timestamp` is the clock and `UITouch.type` says pencil or
  finger.
- `LetterVoice` renders each phrase once with `AVSpeechSynthesizer.write` into a PCM buffer and plays it
  through an `AVAudioEngine` player node. Live speech is the fallback until the clip exists.
- `Haptics` keeps a `UIImpactFeedbackGenerator` and a `UINotificationFeedbackGenerator` prepared.
- `SettingsView` is a `Form` in a sheet.

### The Swift boundary

The framework is exported through the Objective-C bridge, so a few Kotlin shapes change on the way:

- Enum entries are `UPPER_SNAKE` in Kotlin and arrive as `lowerCamel` static members, used as
  `FeedbackKind.keyCommitted` in a `switch`.
- Data class `copy` becomes `doCopy` with every argument required. `BoardSettings+Swift.swift` adds a
  `with(...)` helper with optionals for each field.
- Constructors with default arguments lose the defaults, so `SettingsStore(defaults: .standard)`.
- Kotlin objects are immutable, so `BoardState` and `Feedback` are marked `@unchecked Sendable` in Swift.

SKIE would smooth most of this over and is worth adding once it supports the Kotlin version in use.

## Build

Gradle 9.7 with the version catalog in `gradle/libs.versions.toml`. The shared module uses the
`com.android.kotlin.multiplatform.library` plugin for its Android target and builds a static
`Shared.framework` for `iosArm64` and `iosSimulatorArm64`. The Xcode project is generated by xcodegen
from `iosApp/project.yml` and its pre-build script runs the Gradle task that embeds the framework.
