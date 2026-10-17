# Spelling Creator Board

A virtual letterboard for people who communicate by spelling: pointing to letters on a board while a
communication partner reads them out. It runs on iPad, iPhone and Android, looks native on each, and
is built around one idea: every touch gets an answer, right away, on every channel at once.

It is open source under the [Mozilla Public License 2.0](LICENSE) and is a companion to
[Spelling Creator](https://spellingcreator.org), the lesson maker. The two will connect through the
hub's lessons; see [docs/integration.md](docs/integration.md).

## What it does today

- Two boards modeled on the common stencil boards: the alphabet in five rows, and a math board with
  the arithmetic signs and the digits.
- Drawn like the physical board by default: one dark slab with the handle slot and every letter cut
  out of it, so the screen background shows through. A setting switches to plain keys.
- The moment a finger lands, the cell lights up, the letter is spoken, the device taps, and the letter
  pops into the sentence strip. No waiting for the finger to lift.
- A second press on the same letter inside a short window is shown but not added, so an uncertain
  repeat gets a visible answer instead of a doubled letter.
- First touch wins: a steadying palm or the partner's hand cannot add letters. Stylus-only mode ignores
  fingers entirely.
- Letters are spoken from pre-rendered clips so there is no text to speech delay between presses.
- Space reads the word back. Speak reads the sentence. Delete and Clear say what they did.
- Share sends the sentence through the system share sheet (messages, mail, notes, anything installed),
  and Copy puts it on the clipboard. The screen stays awake while the board is showing.
- Lessons from a Spelling Creator hub: pick one from the list or paste a link, step through its
  material and questions, and answer each question on the board. Works with self-hosted hubs through
  a setting. Read only, no account, nothing sent back.
- Live sync with a Practice page on another screen: join with a code and the board follows the
  computer's step while the computer shows what is being spelled. The board side is built; the hub
  side is specified in [docs/live-sync.md](docs/live-sync.md).
- History keeps finished sentences on the device: everything that was spoken, shared, copied or
  cleared, with a time. Tap one to put it back on the board, speak it, or delete it; share or delete
  them all; or turn history off in settings, which deletes them too. Nothing leaves the device.
- Per-speller settings: commit on touch-down or touch-up, the repeat window, stylus only, sounds, haptics.

## Layout

| Path          | What it is                                                                                   |
| ------------- | -------------------------------------------------------------------------------------------- |
| `shared/`     | Kotlin Multiplatform module: board layouts, geometry and hit testing, the selection engine, the sentence, settings, and the session the apps drive. Everything that is not a view. |
| `androidApp/` | Jetpack Compose app. Draws the board on a Canvas and plays sound and haptics.                |
| `iosApp/`     | SwiftUI app, generated from `project.yml` with xcodegen. Same split: draw, listen, play.      |
| `docs/`       | How it is built and why it feels the way it does.                                            |

## Quick start

See [docs/getting-started.md](docs/getting-started.md) for the full setup. The short version:

```bash
./gradlew :shared:testAndroidHostTest     # shared logic tests
./gradlew :androidApp:assembleDebug       # Android APK
android emulator start medium_phone       # then: android install --apks androidApp/build/outputs/apk/debug/androidApp-debug.apk

cd iosApp && xcodegen generate            # once, and after adding Swift files
xcodebuild -project iosApp/SpellingBoard.xcodeproj -scheme SpellingBoard \
  -destination 'platform=iOS Simulator,name=iPad Pro 11 (Board)' build
```

## Documentation

- [Getting started](docs/getting-started.md): tools, building, running on a simulator or emulator, linting.
- [Architecture](docs/architecture.md): what lives in the shared module, what the apps do, and the Swift boundary.
- [Feedback design](docs/feedback.md): why touch-down commit, the repeat window, and pre-rendered letter sounds.
- [Spelling Creator integration](docs/integration.md): how the board and the lesson maker will connect.
