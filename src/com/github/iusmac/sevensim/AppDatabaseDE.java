package com.github.iusmac.sevensim;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

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
    version = 1
)
@TypeConverters({RoomTypeConverters.class})
public abstract class AppDatabaseDE extends RoomDatabase {
    public abstract SubscriptionsDao subscriptionsDao();
    public abstract SubscriptionSchedulesDao subscriptionSchedulerDao();
}
