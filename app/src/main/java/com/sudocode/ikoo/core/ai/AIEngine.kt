package com.sudocode.ikoo.core.ai

import android.content.Context

/**
 * Abstraction over an on-device LLM used for natural-language understanding
 * across iKoo (gallery search, screen understanding, notification intent,
 * semantic search, intent detection).
 *
 * Concrete engines (Qwen, Gemma, Phi, Llama, DeepSeek, ...) implement this
 * interface so the rest of the app never depends on a specific model runtime.
 */
interface AIEngine {

    /** Human readable engine name, e.g. "Qwen2.5-0.5B-Instruct". */
    val name: String

    /** Whether a model is currently loaded and ready for inference. */
    fun isReady(): Boolean

    /**
     * Loads/installs the bundled or downloaded model. Safe to call multiple
     * times; implementations should no-op if already loaded.
     */
    suspend fun initialize(): Boolean

    /**
     * Runs a single-turn prompt and returns raw text output. Used for
     * structured-JSON extraction (gallery search, event extraction, etc).
     *
     * Implementations should be defensive: on failure they return null so
     * callers can fall back to the offline regex/heuristic parsers that
     * already exist in [com.sudocode.ikoo.gallery_ai] and
     * [com.sudocode.ikoo.intent].
     */
    suspend fun generate(prompt: String, maxTokens: Int = 384): String?
}

/**
 * Simple registry so the app can pick an [AIEngine] implementation at
 * startup without hard-coding it everywhere. Uses [NoOpAIEngine] when no
 * bundled text model is available.
 *
 * TODO: register additional engines here as they're implemented:
 *  - GemmaEngine (Gemma 3)
 *  - PhiEngine (Phi-4)
 *  - LlamaEngine
 *  - DeepSeekEngine
 */
object AIEngineRegistry {
    @Volatile
    private var active: AIEngine? = null

    fun active(): AIEngine? = active

    fun setActive(engine: AIEngine) {
        active = engine
    }

    suspend fun initialize(context: Context): Boolean {
        val engine = active ?: synchronized(this) {
            active ?: createDefaultEngine(context).also { active = it }
        }
        return engine.initialize()
    }

    private fun createDefaultEngine(context: Context): AIEngine {
        val hasQwenModel = runCatching {
            context.assets.openFd("models/qwen2_5_0_5b_instruct.task").close()
            true
        }.getOrDefault(false)
        val hasGemmaModel = runCatching {
            context.assets.openFd("models/gemma_3n_e2b_it_int4/gemma-3n-E2B-it-int4.litertlm").close()
            true
        }.getOrDefault(false)
        return when {
            hasQwenModel -> QwenEngine(context)
            hasGemmaModel -> GemmaLiteRtTextEngine(context)
            else -> NoOpAIEngine()
        }
    }
}
