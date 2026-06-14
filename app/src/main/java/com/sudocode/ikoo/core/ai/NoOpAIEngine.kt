package com.sudocode.ikoo.core.ai

class NoOpAIEngine : AIEngine {
    override val name: String = "No AI engine loaded"

    override fun isReady(): Boolean = false

    override suspend fun initialize(): Boolean = false

    override suspend fun generate(prompt: String, maxTokens: Int): String? = null
}
