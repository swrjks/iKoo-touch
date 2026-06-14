package com.sudocode.ikoo.history

import android.content.Context
import com.sudocode.ikoo.intent.LatestIntentDetection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HistoryRepository private constructor(
    private val dao: HistoryDao
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeAll(): Flow<List<HistoryEventEntity>> = dao.observeAll()

    fun search(query: String): Flow<List<HistoryEventEntity>> {
        return if (query.isBlank()) dao.observeAll() else dao.search(query.trim())
    }

    fun search(query: String, intent: String?): Flow<List<HistoryEventEntity>> {
        val normalizedQuery = query.trim()
        val normalizedIntent = intent?.takeIf { it.isNotBlank() && it != "ALL" } ?: "ALL"
        return when {
            normalizedQuery.isNotBlank() -> dao.searchByIntent(normalizedQuery, normalizedIntent)
            normalizedIntent != "ALL" -> dao.observeByIntent(normalizedIntent)
            else -> dao.observeAll()
        }
    }

    fun saveDetection(detection: LatestIntentDetection) {
        repositoryScope.launch {
            dao.insert(detection.toHistoryEntity())
        }
    }

    fun delete(entity: HistoryEventEntity) {
        repositoryScope.launch {
            dao.delete(entity)
        }
    }

    fun deleteById(id: Long) {
        repositoryScope.launch {
            dao.deleteById(id)
        }
    }

    fun clearAll() {
        repositoryScope.launch {
            dao.clearAll()
        }
    }

    fun deleteBetween(startMillis: Long, endMillis: Long) {
        repositoryScope.launch {
            dao.deleteBetween(startMillis, endMillis)
        }
    }

    fun markLatestActionTaken(actionTaken: String) {
        repositoryScope.launch {
            dao.updateLatestActionTaken(actionTaken)
        }
    }

    companion object {
        @Volatile
        private var instance: HistoryRepository? = null

        fun getInstance(context: Context): HistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: HistoryRepository(
                    IKooDatabase.getInstance(context).historyDao()
                ).also { instance = it }
            }
        }
    }
}

private fun LatestIntentDetection.toHistoryEntity(): HistoryEventEntity {
    return HistoryEventEntity(
        0L,
        visibleText,
        packageName,
        result.type.name,
        eventData?.title,
        eventData?.datePhrase,
        eventData?.timePhrase,
        eventData?.location,
        result.confidence,
        latencyMillis,
        detectedAtMillis,
        "None"
    )
}
