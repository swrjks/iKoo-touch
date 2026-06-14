package com.sudocode.ikoo.core.vision

import android.content.Context
import android.net.Uri

data class VisionInput(
    val uri: Uri,
    val displayName: String,
    val relativePath: String,
    val width: Int,
    val height: Int
)

data class VisionResult(
    val engineName: String,
    val tags: Set<String> = emptySet(),
    val extractedText: String = "",
    val barcodes: List<String> = emptyList(),
    val labels: List<String> = emptyList()
) {
    fun searchableTokens(): Set<String> {
        return buildSet {
            addAll(tags)
            addAll(extractedText.searchTokens())
            barcodes.flatMapTo(this) { it.searchTokens() }
            labels.flatMapTo(this) { it.searchTokens() }
        }
    }
}

interface VisionEngine {
    val name: String

    /**
     * Describes an image locally for gallery indexing. Implementations should
     * fail soft and return partial or empty results instead of throwing.
     */
    fun describeImage(context: Context, input: VisionInput): VisionResult
}

object VisionEngineRegistry {
    @Volatile
    private var active: VisionEngine? = null

    fun active(): VisionEngine? = active

    fun setActive(engine: VisionEngine) {
        active = engine
    }
}

fun String.searchTokens(): List<String> {
    return lowercase(java.util.Locale.US)
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .split(Regex("\\s+"))
        .map { token ->
            when (token) {
                "search", "show", "find", "photo", "photos", "image", "images" -> ""
                else -> token
            }
        }
        .filter { it.length > 1 }
}
