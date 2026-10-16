# Getting started

## Tools

| Tool                         | Why                                                                                               |
| ---------------------------- | ------------------------------------------------------------------------------------------------- |
| JDK 17 or newer              | Gradle and the Kotlin compiler. The project is built with Temurin 25 through `mise`.               |
| Android SDK                  | Platform 37 and build tools. Managed with the `android` CLI, not Android Studio.                   |
| `android` CLI                | Google's command line tool for the SDK, emulators, installing and screenshots.                    |
| Xcode 27                     | Swift compiler, iOS SDK and simulators. Simulators are opened from Device Hub, not Simulator.app.  |
| `xcodegen` (Homebrew)        | Generates `iosApp/SpellingBoard.xcodeproj` from `iosApp/project.yml`. The project file is not committed. |

Point Gradle at the SDK once:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

## Shared module

```bash
./gradlew :shared:testAndroidHostTest    # commonTest run on the JVM
```

The tests cover geometry and hit testing, the selection engine's rules, and the session end to end.

## Android

```bash
./gradlew :androidApp:assembleDebug
android emulator start medium_phone
android install --apks androidApp/build/outputs/apk/debug/androidApp-debug.apk
android screen capture --output /tmp/board.png
android emulator stop medium_phone
```

`android run --apks <apk>` builds, installs and launches in one step when a device is connected.

## iOS

```bash
cd iosApp && xcodegen generate && cd ..
xcodebuild -project iosApp/SpellingBoard.xcodeproj -scheme SpellingBoard \
  -destination 'platform=iOS Simulator,name=iPad Pro 11 (Board)' \
  -derivedDataPath iosApp/build/DerivedData CODE_SIGNING_ALLOWED=NO build
xcrun simctl boot "iPad Pro 11 (Board)"
xcrun simctl install "iPad Pro 11 (Board)" iosApp/build/DerivedData/Build/Products/Debug-iphonesimulator/SpellingBoard.app
xcrun simctl launch "iPad Pro 11 (Board)" org.spellingcreator.board
xcrun simctl io "iPad Pro 11 (Board)" screenshot /tmp/board.png
```

Create the simulator first if it does not exist:

```bash
xcrun simctl create "iPad Pro 11 (Board)" \
  com.apple.CoreSimulator.SimDeviceType.iPad-Pro-11-inch-M5-12GB \
  com.apple.CoreSimulator.SimRuntime.iOS-27-0
```

The Xcode target has a pre-build script that runs
`./gradlew :shared:embedAndSignAppleFrameworkForXcode`, so the Kotlin framework is rebuilt whenever
Xcode builds. The script sources `mise` to find the JDK; if you use another JDK manager, set
`JAVA_HOME` in the script in `iosApp/project.yml`.

Run only one emulator or simulator at a time; two together use a lot of memory.

## App icon

The icon is the Lucide "letters" glyphs on a near-black square. The design source is
`Icon/letters-outlined.svg`: the Lucide strokes converted to filled outlines, because Icon
Composer and Android's vector importer both fill open paths and ignore strokes. To outline another
stroked SVG, replace `currentColor` with a real color first, then:

```bash
/Applications/Inkscape.app/Contents/MacOS/inkscape in.svg \
  --actions="select-all;object-stroke-to-path;select-all;path-union;export-plain-svg;export-filename:out.svg;export-do"
```

- **iOS:** `iosApp/Resources/AppIcon.icon` is the Icon Composer package, listed in `project.yml` as
  a single file in the resources phase. Xcode compiles it into the app icon, and
  `ASSETCATALOG_COMPILER_APPICON_NAME` must stay equal to its name, `AppIcon`. Edit it by opening it
  in Icon Composer.
- **Android:** an adaptive icon. `ic_launcher_foreground.xml` holds the same path data scaled into
  the 66 dp safe zone, `ic_launcher_background.xml` is the matching near-black, and the foreground
  doubles as the monochrome layer for themed icons.

## Lint and format

```bash
./gradlew ktlintFormat ktlintCheck          # Kotlin, via the ktlint Gradle plugin and .editorconfig
swift format --in-place --recursive iosApp/Sources
swift format lint --recursive iosApp/Sources
```

`swift format` ships with Xcode. Its settings are in `iosApp/.swift-format`.

## Versions

Dependency versions live in `gradle/libs.versions.toml`. Kotlin 2.4.21, AGP 9.4.0, Compose BOM
2026.09.00, kotlinx.serialization 1.11.0, Gradle 9.7.0. SKIE is not used yet because its latest release
supports Kotlin 2.4.20 and not 2.4.21; the shared API is designed so Swift does not need it (see
[architecture](architecture.md)).
