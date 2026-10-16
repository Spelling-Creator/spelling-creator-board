# Spelling Creator Board

## Overview

A virtual letterboard (an AAC-style app for people who communicate by spelling) for iOS and Android.
Kotlin Multiplatform holds everything that is not a view (`shared/`): board layouts, geometry and hit
testing, the selection engine, the sentence, settings, and `BoardSession`, the one object the apps
drive. The views are native: Jetpack Compose in `androidApp/`, SwiftUI in `iosApp/`. Each app draws the
cells the shared geometry computed, forwards raw touches, and plays whatever feedback comes back.

Layout math, hit testing, timing and the sentence never go in a view. That rule is what keeps the two
apps from drifting apart. Read `docs/architecture.md` before changing the shared API and
`docs/feedback.md` before changing how a touch is answered; the feel of the board is the product.

Licensed MPL 2.0. The sibling `../spelling-creator` repo is AGPL and JavaScript; the two connect
through OSLF files and the hub's HTTP API, never shared code (`docs/integration.md`).

## Searching the Codebase

Use fast, modern tools instead of the slow defaults:

- [`rg`](https://github.com/BurntSushi/ripgrep) (ripgrep) for text search, not `grep`. It respects `.gitignore` for you. Prefer to use [`ast-grep`](https://ast-grep.github.io) when applicable as it uses the structure of the code instead.
- [`fd`](https://github.com/sharkdp/fd) for finding files by name, not `find`.
- [`ast-grep`](https://ast-grep.github.io) for structural searches and rewrites, like finding every call to a function or every component that uses a certain prop. Call it as `ast-grep`. The `sg` alias is deprecated, so don't use it.
- `jq` for reading and querying JSON.

If your harness has dedicated search or file tools that already wrap these, those are fine too.

## Building and running

Android uses the `android` CLI (Google's command line tool), not Android Studio and not the deprecated
`sdkmanager`: `android sdk install`, `android emulator start|stop`, `android install --apks`,
`android screen capture`. iOS uses `xcodegen generate` (the `.xcodeproj` is generated from
`iosApp/project.yml` and not committed), `xcodebuild`, and `xcrun simctl`. Xcode 27 has no
Simulator.app; the simulator window is Device Hub (`/Applications/Xcode.app/Contents/Applications/DeviceHub.app`).

Run only one emulator or simulator at a time. Stop one before booting the other.

Full commands are in `docs/getting-started.md`.

## Code Quality

Always lint and format:

```bash
./gradlew ktlintFormat ktlintCheck
swift format --in-place --recursive iosApp/Sources && swift format lint --recursive iosApp/Sources
```

Kotlin style is ktlint's official style via `.editorconfig`. Swift style is `swift format` (ships with
Xcode) via `iosApp/.swift-format`. Run the shared tests too: `./gradlew :shared:testAndroidHostTest`.

Kotlin enum entries are `UPPER_SNAKE`. They arrive in Swift as `lowerCamel` static members; other
spellings get mangled by the Objective-C export.

## Docs (IMPORTANT!!!)

Always update the docs if necessary when changing or adding code. If the docs already contain outdated information, expand your scope to cover that info too. The docs live in `docs/` and the `README.md`.

## Diagrams are draw.io, not ASCII art

Never draw a flow, pipeline or architecture picture out of characters in a code block. Make a draw.io
diagram instead: one `docs/diagrams/<name>.drawio.svg` per diagram, shown with a normal Markdown image
and alt text that says what it shows. Code blocks are for things that are text: commands, file trees,
URLs, output.

## Outdated packages

PLEASE, PLEASE do not use a package that is old or deprecated. EVEN IF IT IS "INDUSTRY STANDARD" THAT DOES NOT MEAN IT IS GOOD!!! USE THE BEST PACKAGE AND LATEST VERSION LIKE A NORMAL PERSON WOULD!

Versions are pinned in `gradle/libs.versions.toml`.

## Try to avoid spinners

When applicable, try to use skeletons instead of spinners.

## Keep things human

Do no use em dashes or other special symbols not normally found in writing. Do not word things in a weird way. Keep it looking human. If you need to use, for example, an arrow icon, use an SVG instead.

Replacing an em dash with a hyphen is NOT a fix. A spaced hyphen doing an em dash's job ("the limit - 4 bytes", "fast - but wrong") is the same tic with a different character. Reword the sentence instead: use a comma, a colon, parentheses, or the actual word ("minus", "to", "through"). Plain hyphens in compound words and numeric ranges like 0-1 are fine.

## Use American spelling

It's fine to be British in the chat if the person is actually in a region where its spelled like that. But if not just use American spelling. The app itself MUST use American spelling.
