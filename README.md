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
- NES NROM (mapper 0) strict container parser for 16/32 KiB PRG, CHR-ROM/CHR-RAM, trainer and mirroring metadata.
- NMOS 6502 decoder for the 151 documented opcodes, plus conservative reachable-code/control-flow discovery from the NROM RESET vector.
- NES analysis UI uses that preflight and explicitly reports indirect jumps, unknown opcodes and unsupported mappers rather than claiming a native port exists.
- Asynchronous analysis with validated, invalid, inconclusive, and special-handling outcomes.
- Explicit distinction between 3DS cartridge images, 3DSX homebrew executables, and CIA packages.
- Functional export-mode selection UI (standalone APK / multi-game library).
- Local ROM library with persistent document references and open/remove actions.
- In-app project information dialog that clearly explains current backend and APK-export limitations.
- Source-only Android project ZIP generator scaffold with backend/source-path validation; the original ROM is not copied into the generated project.
- Generated project input scaffold: multi-touch on-screen D-pad, A/B/X/Y, L/R, Start/Select controls plus keyboard/gamepad shortcuts. Both feed one GameAction interface.
- Owner-keystore signing script using Android apksigner; final in-app build/export orchestration is not yet implemented.
- **No complete native game-port backend is implemented yet.** The NES NROM parser and 6502 control-flow analyzer are analysis foundations, not a recompilation engine; they do not implement PPU, APU, CPU timing, interrupts, DMA, mapper behavior or game rendering. The generated scaffold is not a playable game APK.
- Accessible labels, empty states, and a responsive single-screen layout.

## Build

Open this repository in a recent Android Studio version with JDK 17 and Android SDK 36 installed, then sync Gradle and run the `app` configuration.

Command-line build (after installing the Android SDK and Gradle):
```bash
gradle :app:assembleDebug
```

The first build requires network access to resolve Gradle and Maven dependencies.

GitHub Actions also builds the current ROM2APK debug app and publishes it as the
`rom2apk-debug-apk` workflow artifact (14-day retention). This is the ROM2APK
utility app, **not** a playable game port.

## Planned milestones

1. Android foundation and ROM import.
2. Content inspection, checksums, format-specific validation, and diagnostics.
3. Complete a NES NROM static recompilation backend, including 6502 semantics, native graphics/audio/input and equivalence tests.
4. Validate a complete NROM title end-to-end before enabling APK export for that profile.
5. Add further mappers and console families only when translated source reproduces behavior without an emulator runtime.
6. Integrate project generation, build, signing and APK verification into the Android app using a user-owned keystore.
7. Implement multi-game export, save data, per-game metadata, regression tests and real-device compatibility tests.

## Legal and security notes

Only import game files and system components that you are legally entitled to use. Native porting backends and any source-generation dependencies have their own licenses. ROM2APK will not bundle copyrighted game files, proprietary firmware, decryption keys, or third-party emulator binaries without permission. The project must preserve all dependency notices and license terms.

The final APK builder must keep signing keys under the user's control. Any optional remote build must clearly disclose what is uploaded, use encrypted transport, and provide deletion controls.

## License

The ROM2APK project is licensed under the MIT License. This does not change the licenses of third-party engines or dependencies.
