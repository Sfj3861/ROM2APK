# ROM2APK

ROM2APK is an Android application project for packaging supported game files into standalone Android apps or multi-game library apps.

> **Development status:** early foundation. The current code provides the Android shell, a clean Compose interface, document import, and format identification, and preliminary structural analysis with diagnostics. It does not yet build or run game APKs.

## Product goals

- Two export targets: one APK per game, or one APK containing a library.
- Import: `.nes`, `.sfc`, `.smc`, `.gb`, `.gbc`, `.gba`, `.ds`, `.3ds`, `.3dsx`, and `.cia`.
- Downloadable, versioned emulator engines and updates.
- Offline operation after required components have been downloaded.
- Per-game metadata, icons, settings, and save data.
- Clear validation and diagnostics instead of claiming unsupported files will work.

## Current implementation

- Native Android app using Kotlin and Jetpack Compose.
- Android Storage Access Framework document picker (no broad storage permission).
- Extension identification for the ten requested suffixes.\n- Conservative header/content checks for supported formats.\n- Asynchronous analysis with validated, invalid, inconclusive, and special-handling outcomes.
- Explicit distinction between 3DS cartridge images, 3DSX homebrew executables, and CIA packages.
- Export mode selection UI (standalone APK / multi-game library).
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
3. Emulator engine catalog, license review, downloads, and integrity verification.
4. Runtime integration for 8/16-bit and handheld systems.
5. DS and 3DS-family support, subject to each engine's technical requirements.
6. APK packaging, signing, and verification.
7. Multi-game library export, automated tests, and device compatibility testing.

## Legal and security notes

Only import game files and system components that you are legally entitled to use. Emulator engines and firmware have separate licenses and distribution requirements. ROM2APK will not bundle copyrighted game files, proprietary firmware, decryption keys, or third-party emulator binaries without permission. The project must preserve each dependency's notices and license terms.

The final APK builder must keep signing keys under the user's control. Any optional remote build must clearly disclose what is uploaded, use encrypted transport, and provide deletion controls.

## License

The ROM2APK project is licensed under the MIT License. This does not change the licenses of third-party engines or dependencies.
