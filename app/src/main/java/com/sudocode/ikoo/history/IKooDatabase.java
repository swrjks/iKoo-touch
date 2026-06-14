package com.sudocode.ikoo.history;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {HistoryEventEntity.class},
        version = 1,
        exportSchema = false
)
public abstract class IKooDatabase extends RoomDatabase {
    public abstract HistoryDao historyDao();

    private static volatile IKooDatabase instance;

    public static IKooDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (IKooDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            IKooDatabase.class,
                            "ikoo_local_history.db"
                    ).build();
                }
            }
        }
        return instance;
    }
}
