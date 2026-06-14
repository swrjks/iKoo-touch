package com.sudocode.ikoo.core.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GemmaLiteRtTextEngine(context: Context) : AIEngine {
    private val appContext = context.applicationContext
    private val inferenceMutex = Mutex()

    @Volatile
    private var inference: LlmInference? = null

    override val name: String = "Gemma 3n E2B INT4 LiteRT text"

    override fun isReady(): Boolean = inference != null

    override suspend fun initialize(): Boolean = inferenceMutex.withLock {
        if (inference != null) return@withLock true
        withContext(Dispatchers.IO) {
            runCatching {
                val modelFile = copyBundledModelIfNeeded()
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(256)
                    .setPreferredBackend(LlmInference.Backend.CPU)
                    .build()
                inference = LlmInference.createFromOptions(appContext, options)
            }.onFailure {
                Log.e(TAG, "Failed to initialize bundled Gemma text model", it)
                inference = null
            }
        }
        inference != null
    }

    override suspend fun generate(prompt: String, maxTokens: Int): String? {
        if (!isReady() && !initialize()) return null
        return inferenceMutex.withLock {
            val model = inference ?: return@withLock null
            withContext(Dispatchers.Default) {
                runCatching {
                    val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTemperature(0.0f)
                        .setTopK(1)
                        .setTopP(0.1f)
                        .build()
                    LlmInferenceSession.createFromOptions(model, sessionOptions).use { session ->
                        session.addQueryChunk(prompt.take(maxTokens * 8))
                        session.generateResponse().trim()
                    }
                }.onFailure {
                    Log.e(TAG, "Gemma text inference failed", it)
                }.getOrNull()?.takeIf { it.isNotBlank() }
            }
        }
    }

    private fun copyBundledModelIfNeeded(): File {
        val modelDir = File(appContext.filesDir, "models/gemma_3n_e2b_it_int4").apply { mkdirs() }
        val destination = File(modelDir, MODEL_FILE)
        val assetLength = appContext.assets.openFd(MODEL_ASSET).length
        if (destination.exists() && destination.length() == assetLength) return destination

        val temporary = File(modelDir, "$MODEL_FILE.tmp")
        appContext.assets.open(MODEL_ASSET).use { input ->
            temporary.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }
        check(temporary.length() == assetLength) { "Bundled Gemma model copy is incomplete" }
        if (destination.exists()) destination.delete()
        check(temporary.renameTo(destination)) { "Could not install bundled Gemma model" }
        return destination
    }

    companion object {
        private const val TAG = "GemmaLiteRtTextEngine"
        private const val MODEL_ASSET = "models/gemma_3n_e2b_it_int4/gemma-3n-E2B-it-int4.litertlm"
        private const val MODEL_FILE = "gemma-3n-E2B-it-int4.litertlm"
    }
}
