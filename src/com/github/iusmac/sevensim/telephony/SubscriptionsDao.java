package com.github.iusmac.sevensim.telephony;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.Optional;

@Dao
public interface SubscriptionsDao {
    @Upsert
    void upsert(final Subscription subscription);

    @Query("SELECT * FROM subscriptions WHERE id = :subId")
    Optional<Subscription> findBySubscriptionId(int subId);
}
