package com.sudocode.ikoo.history;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import kotlinx.coroutines.flow.Flow;

@Dao
public interface HistoryDao {
    @Query("SELECT * FROM history_events ORDER BY timestampMillis DESC")
    Flow<List<HistoryEventEntity>> observeAll();

    @Query(
            "SELECT * FROM history_events " +
                    "WHERE rawText LIKE '%' || :query || '%' " +
                    "OR sourcePackage LIKE '%' || :query || '%' " +
                    "OR detectedIntent LIKE '%' || :query || '%' " +
                    "OR extractedTitle LIKE '%' || :query || '%' " +
                    "OR datePhrase LIKE '%' || :query || '%' " +
                    "OR timePhrase LIKE '%' || :query || '%' " +
                    "OR location LIKE '%' || :query || '%' " +
                    "OR actionTaken LIKE '%' || :query || '%' " +
                    "ORDER BY timestampMillis DESC"
    )
    Flow<List<HistoryEventEntity>> search(String query);

    @Query(
            "SELECT * FROM history_events " +
                    "WHERE (:intent = 'ALL' OR detectedIntent = :intent) " +
                    "ORDER BY timestampMillis DESC"
    )
    Flow<List<HistoryEventEntity>> observeByIntent(String intent);

    @Query(
            "SELECT * FROM history_events " +
                    "WHERE (:intent = 'ALL' OR detectedIntent = :intent) " +
                    "AND (rawText LIKE '%' || :query || '%' " +
                    "OR sourcePackage LIKE '%' || :query || '%' " +
                    "OR detectedIntent LIKE '%' || :query || '%' " +
                    "OR extractedTitle LIKE '%' || :query || '%' " +
                    "OR datePhrase LIKE '%' || :query || '%' " +
                    "OR timePhrase LIKE '%' || :query || '%' " +
                    "OR location LIKE '%' || :query || '%' " +
                    "OR actionTaken LIKE '%' || :query || '%') " +
                    "ORDER BY timestampMillis DESC"
    )
    Flow<List<HistoryEventEntity>> searchByIntent(String query, String intent);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(HistoryEventEntity entity);

    @Delete
    void delete(HistoryEventEntity entity);

    @Query("DELETE FROM history_events WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM history_events")
    void clearAll();

    @Query("DELETE FROM history_events WHERE timestampMillis >= :startMillis AND timestampMillis < :endMillis")
    void deleteBetween(long startMillis, long endMillis);

    @Query("UPDATE history_events SET actionTaken = :actionTaken WHERE id = :id")
    void updateActionTaken(long id, String actionTaken);

    @Query(
            "UPDATE history_events SET actionTaken = :actionTaken " +
                    "WHERE id = (SELECT id FROM history_events ORDER BY timestampMillis DESC LIMIT 1)"
    )
    void updateLatestActionTaken(String actionTaken);
}
