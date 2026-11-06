# ROM2APK

ROM2APK is an Android application project for packaging supported game files into standalone Android apps or multi-game library apps.

> **Development status:** early foundation. The current code provides the Android shell, a clean Compose interface, document import, and format identification, and preliminary structural analysis with diagnostics. It does not yet build or run game APKs.

## Product goals

- Two export targets: one APK per game, or one APK containing a library.
- Import: `.nes`, `.sfc`, `.smc`, `.gb`, `.gbc`, `.gba`, `.ds`, `.3ds`, `.3dsx`, and `.cia`.
- System-specific native porting backends that translate game behavior into equivalent Android source code; generated games must not run inside a console emulator.
- Offline project generation and compilation after the required Android toolchain and port backend are available.
- Per-game metadata, icons, settings, and save data.
- Clear validation and diagnostics instead of claiming unsupported files will work.

## Current implementation

- Native Android app using Kotlin and Jetpack Compose.
- Android Storage Access Framework document picker (no broad storage permission).
- Extension identification for the ten requested suffixes.
- Conservative header/content checks for supported formats.
- Asynchronous analysis with validated, invalid, inconclusive, and special-handling outcomes.
- Explicit distinction between 3DS cartridge images, 3DSX homebrew executables, and CIA packages.
- Functional export-mode selection UI (standalone APK / multi-game library).
- Source-only Android project ZIP generator scaffold with backend/source-path validation; the original ROM is not copied into the generated project.
- Generated project input scaffold: multi-touch on-screen D-pad, A/B/X/Y, L/R, Start/Select controls plus keyboard/gamepad shortcuts. Both feed one GameAction interface.
- Owner-keystore signing script using Android apksigner; final in-app build/export orchestration is not yet implemented.
- A complete game-specific port backend is not yet implemented, so the generated project scaffold is not yet a playable game APK.
- Accessible labels, empty states, and a responsive single-screen layout.

## Build

Open this repository in a recent Android Studio version with JDK 17 and Android SDK 36 installed, then sync Gradle and run the `app` configuration.

Command-line build (after installing the Android SDK and Gradle):
```bash
gradle :app:assembleDebug
```

The first build requires network access to resolve Gradle and Maven dependencies.

## Planned milestones

1. Android foundation and ROM import.
2. Content inspection, checksums, format-specific validation, and diagnostics.
3. Native port backend architecture, source translation and equivalence tests per console family.
4. Implement and validate the first complete native port backend (start with a tractable 8-bit system).
5. Add further system backends only when translated source can reproduce game behavior without an emulator runtime.
6. Compile generated projects, sign with a keystore controlled by the user, and verify APK signatures.
7. Integrate build/export into the app; add multi-game export, regression tests and real-device compatibility tests.

## Legal and security notes

Only import game files and system components that you are legally entitled to use. Native porting backends and any source-generation dependencies have their own licenses. ROM2APK will not bundle copyrighted game files, proprietary firmware, decryption keys, or third-party emulator binaries without permission. The project must preserve all dependency notices and license terms.

The final APK builder must keep signing keys under the user's control. Any optional remote build must clearly disclose what is uploaded, use encrypted transport, and provide deletion controls.

## License

The ROM2APK project is licensed under the MIT License. This does not change the licenses of third-party engines or dependencies.
