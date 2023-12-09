package com.github.iusmac.sevensim.telephony;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.Optional;

@Dao
public abstract class SubscriptionsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract long insert(Subscription subscription);

    @Update
    public abstract int update(Subscription subscription);

    @Transaction
    // TODO: switch to @Upsert added in Room v2.5.0-alpha03; also drop onConflict from insert()
    public void insertOrUpdate(final Subscription subscription) {
        final long id = insert(subscription);
        if (id == -1L) {
            update(subscription);
        }
    }

    @Delete
    public abstract void delete(Subscription subscription);

    @Query("SELECT * FROM subscriptions WHERE id = :subId")
    public abstract Optional<Subscription> findBySubscriptionId(int subId);
}
