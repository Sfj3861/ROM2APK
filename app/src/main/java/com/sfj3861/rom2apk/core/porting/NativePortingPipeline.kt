package com.sfj3861.rom2apk.core.porting

import com.sfj3861.rom2apk.core.rom.RomFormat

sealed interface PortingOutcome {
    data class Ready(
        val inspection: PortInspection,
        val translation: PortTranslation
    ) : PortingOutcome

    data class Unsupported(
        val format: RomFormat?,
        val reason: String
    ) : PortingOutcome

    data class Rejected(
        val backendId: String,
        val inspection: PortInspection
    ) : PortingOutcome
}

/**
 * Resolves one format-specific static/native port backend and asks it to
 * translate the supplied image into source files. No emulator fallback exists.
 */
class NativePortingPipeline(backends: Collection<NativePortBackend>) {
    private val backendByFormat: Map<RomFormat, NativePortBackend>

    init {
        require(backends.map { it.id }.distinct().size == backends.size) {
            "Los identificadores de backend deben ser únicos."
        }
        require(backends.all { it.id.isNotBlank() && it.displayName.isNotBlank() }) {
            "Cada backend necesita un identificador y nombre."
        }
        val duplicateFormats = backends.flatMap { backend ->
            backend.supportedFormats.map { format -> format to backend.id }
        }.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
        require(duplicateFormats.isEmpty()) {
            "Hay varios backends asignados al mismo formato: ${duplicateFormats.keys.joinToString()}"
        }
        backendByFormat = backends.flatMap { backend ->
            backend.supportedFormats.map { format -> format to backend }
        }.toMap()
    }

    fun supportedFormats(): Set<RomFormat> = backendByFormat.keys

    fun inspect(fileName: String, bytes: ByteArray): PortingOutcome {
        val format = RomFormat.fromFileName(fileName)
            ?: return PortingOutcome.Unsupported(null, "La extensión del archivo no está reconocida.")
        val backend = backendByFormat[format]
            ?: return PortingOutcome.Unsupported(
                format,
                "No existe todavía un porteador nativo para ${format.description}. No se utilizará un emulador como alternativa."
            )
        val inspection = backend.inspect(bytes, fileName)
        return if (inspection.supported) {
            // Inspection alone never claims that a complete port has been built.
            PortingOutcome.Rejected(backend.id, inspection)
        } else {
            PortingOutcome.Rejected(backend.id, inspection)
        }
    }

    fun translate(
        fileName: String,
        bytes: ByteArray,
        request: PortRequest
    ): PortingOutcome {
        val format = RomFormat.fromFileName(fileName)
            ?: return PortingOutcome.Unsupported(null, "La extensión del archivo no está reconocida.")
        val backend = backendByFormat[format]
            ?: return PortingOutcome.Unsupported(
                format,
                "No existe todavía un porteador nativo para ${format.description}. No se utilizará un emulador como alternativa."
            )
        val inspection = backend.inspect(bytes, fileName)
        if (!inspection.supported) return PortingOutcome.Rejected(backend.id, inspection)

        val translation = backend.translate(bytes, request)
        require(translation.backendId == backend.id) {
            "El resultado declara un backend distinto al que realizó la traducción."
        }
        require(translation.gameTitle.isNotBlank()) {
            "El backend no identificó el título del juego."
        }
        if (translation.success) {
            require(translation.sourceFiles.isNotEmpty()) {
                "El backend declaró éxito, pero no generó código fuente."
            }
            require(translation.sourceFiles.all { it.content.isNotBlank() }) {
                "El backend generó un archivo de código vacío."
            }
        }
        return PortingOutcome.Ready(inspection, translation)
    }
}
