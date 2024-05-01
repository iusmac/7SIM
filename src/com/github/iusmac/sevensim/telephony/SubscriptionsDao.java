package com.github.iusmac.sevensim.telephony;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import java.util.Optional;

@Dao
public interface SubscriptionsDao {
    @Insert
    long insert(Subscription subscription);

    @Update
    int update(Subscription subscription);

    @Upsert
    void upsert(final Subscription subscription);

    @Delete
    void delete(Subscription subscription);

    @Query("SELECT * FROM subscriptions WHERE id = :subId")
    Optional<Subscription> findBySubscriptionId(int subId);
}
