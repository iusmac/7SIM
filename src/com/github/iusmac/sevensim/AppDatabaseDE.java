package com.github.iusmac.sevensim;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.github.iusmac.sevensim.scheduler.SubscriptionScheduleEntity;
import com.github.iusmac.sevensim.scheduler.SubscriptionSchedulesDao;
import com.github.iusmac.sevensim.telephony.Subscription;
import com.github.iusmac.sevensim.telephony.SubscriptionsDao;

/**
 * Application database located in the DE (device encrypted) storage.
 */
@Database(
    entities = {Subscription.class, SubscriptionScheduleEntity.class},
    exportSchema = false,
    version = 2
)
@TypeConverters({RoomTypeConverters.class})
public abstract class AppDatabaseDE extends RoomDatabase {
    public abstract SubscriptionsDao subscriptionsDao();
    public abstract SubscriptionSchedulesDao subscriptionSchedulerDao();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(final SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE subscription_schedules"
                    + " ADD COLUMN label TEXT");
        }
    };
}
