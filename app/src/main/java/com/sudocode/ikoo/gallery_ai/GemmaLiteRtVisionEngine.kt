package com.sudocode.ikoo.gallery_ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.util.Size
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.sudocode.ikoo.core.vision.VisionEngine
import com.sudocode.ikoo.core.vision.VisionInput
import com.sudocode.ikoo.core.vision.VisionResult
import com.sudocode.ikoo.core.vision.searchTokens
import java.io.File
import java.util.Locale

/**
 * Gemma 3n E2B INT4 LiteRT-LM model integration point.
 *
 * The downloaded model is bundled at:
 * models/gemma_3n_e2b_it_int4/gemma-3n-E2B-it-int4.litertlm
 *
 * This is the preferred offline multimodal model for iKoo. It uses the
 * MediaPipe GenAI image APIs available in the current project dependency:
 * LlmInferenceOptions.setMaxNumImages(...) and LlmInferenceSession.addImage(...).
 */
class GemmaLiteRtVisionEngine(
    private val fallback: VisionEngine = MlKitVisionEngine
) : VisionEngine {

    companion object {
        private const val ASSET_DIR = "models/gemma_3n_e2b_it_int4"
        private const val MODEL_FILE = "gemma-3n-E2B-it-int4.litertlm"
        private const val MANIFEST_FILE = "model_manifest.json"
        private const val TAG = "GemmaLiteRtVisionEngine"
    }

    override val name: String = "Gemma 3n E2B INT4 LiteRT-LM"

    @Volatile
    private var inference: LlmInference? = null

    override fun describeImage(context: Context, input: VisionInput): VisionResult {
        val fallbackResult = fallback.describeImage(context, input)
        if (!isModelPackAvailable(context)) return fallbackResult

        val caption = runGemmaCaption(context, input) ?: return fallbackResult.copy(
            engineName = "$name unavailable; ${fallbackResult.engineName} extraction"
        )
        val gemmaTokens = caption.searchTokens().toSet()
        val mergedTags = buildSet {
            addAll(fallbackResult.tags)
            addAll(gemmaTokens)
            addAll(normalizedVisualSynonyms(gemmaTokens))
        }

        return fallbackResult.copy(
            engineName = name,
            tags = mergedTags,
            extractedText = listOf(fallbackResult.extractedText, caption)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        )
    }

    fun isModelPackAvailable(context: Context): Boolean {
        return listOf(MODEL_FILE, MANIFEST_FILE).all { fileName ->
            runCatching {
                context.assets.open("$ASSET_DIR/$fileName").use { true }
            }.getOrDefault(false)
        }
    }

    @Synchronized
    private fun runGemmaCaption(context: Context, input: VisionInput): String? {
        val bitmap = loadBitmapForGemma(context, input) ?: return null
        val model = inference ?: initialize(context) ?: return null
        return runCatching {
            val image = BitmapImageBuilder(bitmap).build()
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.1f)
                .setTopK(20)
                .setTopP(0.9f)
                .build()

            LlmInferenceSession.createFromOptions(model, sessionOptions).use { session ->
                session.addImage(image)
                session.addQueryChunk(
                    """
                    Describe this gallery photo for local search.
                    Return only comma-separated concise tags and one short phrase.
                    Include exact visible object nouns and visible text/logos.
                    Prefer searchable words such as laptop, phone, hand, bottle,
                    water bottle, flask, cable, charger, paper, document, sign,
                    logo, QR code, barcode, desk, table, book.
                    """.trimIndent()
                )
                session.generateResponse().trim()
            }
        }.onFailure { error ->
            Log.w(TAG, "Gemma image inference failed; using fallback extractor", error)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    @Synchronized
    private fun initialize(context: Context): LlmInference? {
        inference?.let { return it }
        return runCatching {
            val modelFile = copyModelToPrivateStorage(context)
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(256)
                .setMaxNumImages(1)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            LlmInference.createFromOptions(context.applicationContext, options)
                .also { inference = it }
        }.onFailure { error ->
            Log.e(TAG, "Gemma LiteRT-LM initialization failed", error)
            inference = null
        }.getOrNull()
    }

    private fun copyModelToPrivateStorage(context: Context): File {
        val modelDir = File(context.filesDir, "models/gemma_3n_e2b_it_int4").apply { mkdirs() }
        val destination = File(modelDir, MODEL_FILE)
        val assetPath = "$ASSET_DIR/$MODEL_FILE"
        val assetLength = context.assets.openFd(assetPath).length

        if (destination.exists() && destination.length() == assetLength) return destination

        val temporary = File(modelDir, "$MODEL_FILE.tmp")
        context.assets.open(assetPath).use { input ->
            temporary.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }
        check(temporary.length() == assetLength) { "Gemma model copy is incomplete" }
        if (destination.exists()) destination.delete()
        check(temporary.renameTo(destination)) { "Could not install Gemma model" }
        return destination
    }

    private fun loadBitmapForGemma(context: Context, input: VisionInput): Bitmap? {
        return runCatching {
            context.contentResolver.openInputStream(input.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { source ->
                    val maxSide = maxOf(source.width, source.height)
                    if (maxSide <= 896) {
                        source
                    } else {
                        val scale = 896f / maxSide
                        Bitmap.createScaledBitmap(
                            source,
                            (source.width * scale).toInt().coerceAtLeast(1),
                            (source.height * scale).toInt().coerceAtLeast(1),
                            true
                        )
                    }
                }
            } ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(input.uri, Size(896, 896), null)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun normalizedVisualSynonyms(tokens: Set<String>): Set<String> {
        val joined = tokens.joinToString(" ").lowercase(Locale.US)
        return buildSet {
            if ("laptop" in tokens || "keyboard" in tokens || "computer" in tokens || "notebook" in tokens) {
                addAll(listOf("laptop", "computer", "notebook", "keyboard", "macbook"))
            }
            if ("phone" in tokens || "iphone" in tokens || "smartphone" in tokens || "mobile" in tokens) {
                addAll(listOf("phone", "iphone", "smartphone", "mobile", "device"))
            }
            if ("bottle" in tokens || "flask" in tokens || "thermos" in tokens ||
                "drink" in tokens || "beverage" in tokens || joined.contains("water bottle")
            ) {
                addAll(listOf("bottle", "water bottle", "flask", "thermos", "drink bottle"))
            }
            if ("hand" in tokens || "finger" in tokens || "palm" in tokens) {
                addAll(listOf("hand", "palm", "finger", "fingers", "arm"))
            }
            if ("cable" in tokens || "wire" in tokens || "charger" in tokens || "usb" in tokens) {
                addAll(listOf("charger", "cable", "wire", "usb", "charging cable"))
            }
            if ("desk" in tokens || "table" in tokens) {
                addAll(listOf("table", "desk", "workbench"))
            }
            if ("qr" in tokens || "barcode" in tokens || joined.contains("qr code")) {
                addAll(listOf("qr", "qr code", "barcode", "scan", "code"))
            }
            if ("sign" in tokens || "logo" in tokens || "poster" in tokens || "banner" in tokens) {
                addAll(listOf("sign", "signboard", "logo", "poster", "banner", "board"))
            }
            if ("scaler" in tokens || "scalar" in tokens) {
                addAll(listOf("scaler", "scalar", "school", "technology", "logo", "sign"))
            }
        }
    }
}
