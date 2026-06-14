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

/**
 * Real on-device Qwen engine backed by MediaPipe LLM Inference.
 *
 * The model is bundled in app assets and copied to private storage on first
 * launch because MediaPipe requires a filesystem path. All inference remains
 * offline after the APK is installed.
 */
class QwenEngine(context: Context) : AIEngine {

    companion object {
        private const val TAG = "QwenEngine"
        private const val MODEL_ASSET = "models/qwen2_5_0_5b_instruct.task"
        private const val MODEL_FILE = "qwen2_5_0_5b_instruct.task"
    }

    override val name: String = "Qwen2.5-0.5B-Instruct (offline)"

    private val appContext = context.applicationContext
    private val inferenceMutex = Mutex()

    @Volatile
    private var inference: LlmInference? = null

    override fun isReady(): Boolean = inference != null

    override suspend fun initialize(): Boolean = inferenceMutex.withLock {
        if (inference != null) return@withLock true

        withContext(Dispatchers.IO) {
            runCatching {
                val modelFile = copyBundledModelIfNeeded()
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(512)
                    .setPreferredBackend(LlmInference.Backend.CPU)
                    .build()
                inference = LlmInference.createFromOptions(appContext, options)
            }.onFailure {
                Log.e(TAG, "Failed to initialize bundled Qwen model", it)
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
                    val sessionOptions =
                        LlmInferenceSession.LlmInferenceSessionOptions.builder()
                            .setTemperature(0.1f)
                            .setTopK(20)
                            .setTopP(0.9f)
                            .build()
                    LlmInferenceSession.createFromOptions(model, sessionOptions).use { session ->
                        session.addQueryChunk(prompt)
                        session.generateResponse()
                    }
                }.onFailure {
                    Log.e(TAG, "Qwen inference failed", it)
                }.getOrNull()
            }
        }
    }

    private fun copyBundledModelIfNeeded(): File {
        val modelDir = File(appContext.filesDir, "models").apply { mkdirs() }
        val destination = File(modelDir, MODEL_FILE)
        val assetLength = appContext.assets.openFd(MODEL_ASSET).length

        if (destination.exists() && destination.length() == assetLength) {
            return destination
        }

        val temporary = File(modelDir, "$MODEL_FILE.tmp")
        appContext.assets.open(MODEL_ASSET).use { input ->
            temporary.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }

        check(temporary.length() == assetLength) {
            "Bundled Qwen model copy is incomplete"
        }
        if (destination.exists()) destination.delete()
        check(temporary.renameTo(destination)) {
            "Could not install bundled Qwen model"
        }
        return destination
    }
}
