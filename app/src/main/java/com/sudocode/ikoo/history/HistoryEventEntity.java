package com.sudocode.ikoo.history;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "history_events")
public class HistoryEventEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String rawText;
    public String sourcePackage;
    public String detectedIntent;
    @Nullable public String extractedTitle;
    @Nullable public String datePhrase;
    @Nullable public String timePhrase;
    @Nullable public String location;
    public float confidence;
    public long latencyMillis;
    public long timestampMillis;
    public String actionTaken;

    public HistoryEventEntity(
            long id,
            String rawText,
            String sourcePackage,
            String detectedIntent,
            @Nullable String extractedTitle,
            @Nullable String datePhrase,
            @Nullable String timePhrase,
            @Nullable String location,
            float confidence,
            long latencyMillis,
            long timestampMillis,
            String actionTaken
    ) {
        this.id = id;
        this.rawText = rawText;
        this.sourcePackage = sourcePackage;
        this.detectedIntent = detectedIntent;
        this.extractedTitle = extractedTitle;
        this.datePhrase = datePhrase;
        this.timePhrase = timePhrase;
        this.location = location;
        this.confidence = confidence;
        this.latencyMillis = latencyMillis;
        this.timestampMillis = timestampMillis;
        this.actionTaken = actionTaken;
    }
}
