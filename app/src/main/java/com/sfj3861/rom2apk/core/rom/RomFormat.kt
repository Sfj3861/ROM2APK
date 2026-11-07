package com.sfj3861.rom2apk.core.rom

import java.util.Locale

enum class ConsoleFamily(val displayName: String) {
    NES("Nintendo Entertainment System"),
    SNES("Super Nintendo"),
    GAME_BOY("Game Boy"),
    GAME_BOY_COLOR("Game Boy Color"),
    GAME_BOY_ADVANCE("Game Boy Advance"),
    NINTENDO_DS("Nintendo DS"),
    NINTENDO_3DS("Nintendo 3DS"),
    NINTENDO_3DS_HOME_BREW("Nintendo 3DS homebrew")
}

enum class RomFormat(
    val extension: String,
    val console: ConsoleFamily,
    val description: String
) {
    NES("nes", ConsoleFamily.NES, "NES cartridge image"),
    SFC("sfc", ConsoleFamily.SNES, "Super Nintendo image"),
    SMC("smc", ConsoleFamily.SNES, "Super Nintendo image"),
    GB("gb", ConsoleFamily.GAME_BOY, "Game Boy image"),
    GBC("gbc", ConsoleFamily.GAME_BOY_COLOR, "Game Boy Color image"),
    GBA("gba", ConsoleFamily.GAME_BOY_ADVANCE, "Game Boy Advance image"),
    DS("ds", ConsoleFamily.NINTENDO_DS, "Nintendo DS image"),
    THREE_DS("3ds", ConsoleFamily.NINTENDO_3DS, "Nintendo 3DS cartridge image"),
    THREE_DSX("3dsx", ConsoleFamily.NINTENDO_3DS_HOME_BREW, "3DS homebrew executable"),
    CIA("cia", ConsoleFamily.NINTENDO_3DS, "CTR Importable Archive package");

    companion object {
        fun fromFileName(fileName: String): RomFormat? {
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.ROOT)
            return entries.firstOrNull { it.extension == extension }
        }
    }
}

data class ImportedRom(
    val displayName: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val format: RomFormat
) {
    val consoleName: String get() = format.console.displayName
}
