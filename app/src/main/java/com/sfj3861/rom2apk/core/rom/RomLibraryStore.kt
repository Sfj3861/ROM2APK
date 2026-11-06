package com.sfj3861.rom2apk.core.rom

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LibraryRom(
    val uri: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long?,
    val format: RomFormat
)

/**
 * Local-only library index. ROM bytes stay in the user's document provider;
 * persisted SAF URI grants let the app reopen entries without copying files.
 */
object RomLibraryStore {
    private const val PREFS = "rom2apk_library"
    private const val KEY_ENTRIES = "entries"

    fun load(context: Context): List<LibraryRom> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, "[]") ?: "[]"
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    val fileName = item.optString("fileName")
                    val format = RomFormat.fromFileName(fileName) ?: continue
                    val uri = item.optString("uri")
                    if (uri.isBlank()) continue
                    add(
                        LibraryRom(
                            uri = uri,
                            displayName = item.optString("displayName", fileName),
                            fileName = fileName,
                            sizeBytes = if (item.isNull("sizeBytes")) null else item.optLong("sizeBytes"),
                            format = format
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, entry: LibraryRom) {
        val entries = load(context).filterNot { it.uri == entry.uri } + entry
        save(context, entries)
    }

    fun remove(context: Context, uri: String) {
        save(context, load(context).filterNot { it.uri == uri })
    }

    private fun save(context: Context, entries: List<LibraryRom>) {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(
                JSONObject()
                    .put("uri", entry.uri)
                    .put("displayName", entry.displayName)
                    .put("fileName", entry.fileName)
                    .put("sizeBytes", entry.sizeBytes)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, json.toString())
            .apply()
    }
}
