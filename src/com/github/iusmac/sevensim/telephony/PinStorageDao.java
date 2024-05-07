package com.github.iusmac.sevensim.telephony;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.Optional;

@Dao
public interface PinStorageDao {
    @Upsert
    long upsert(PinEntity pin);

    @Delete
    void delete(PinEntity pin);

    @Query("SELECT * FROM pin_storage WHERE sub_id = :subId LIMIT 1")
    Optional<PinEntity> findBySubscriptionId(int subId);
}
