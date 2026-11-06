package com.sfj3861.rom2apk.core.porting

import com.sfj3861.rom2apk.core.rom.RomFormat

/**
 * A backend must translate a supported game into platform-native source.
 * It must not embed or invoke a console emulator.
 */
interface NativePortBackend {
    val id: String
    val displayName: String
    val supportedFormats: Set<RomFormat>

    fun inspect(input: ByteArray, fileName: String): PortInspection
    fun translate(input: ByteArray, request: PortRequest): PortTranslation
}

data class PortRequest(
    val applicationId: String,
    val applicationName: String,
    val versionName: String = "1.0"
)

data class PortInspection(
    val supported: Boolean,
    val summary: String,
    val estimatedSourceFiles: Int = 0,
    val limitations: List<String> = emptyList()
)

data class PortSourceFile(
    /** Relative path inside the generated Android project. */
    val path: String,
    val content: String
)

data class PortTranslation(
    val success: Boolean,
    val backendId: String,
    val gameTitle: String,
    val sourceFiles: List<PortSourceFile>,
    val diagnostics: List<String> = emptyList()
)

/**
 * A generated game implementation is compiled into the target APK.
 * This contract deliberately contains no frame loop or emulation API.
 */
interface NativeGamePort {
    val title: String
    fun create(): NativeGameSession
}

interface NativeGameSession {
    fun onCreate()
    fun onPause()
    fun onResume()
    fun onDestroy()
}
