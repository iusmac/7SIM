package com.github.iusmac.sevensim.telephony;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;
import java.util.Optional;

@Dao
public interface PinStorageDao {
    @Upsert
    long upsert(PinEntity pin);

    @Delete
    void delete(PinEntity pin);

    @Query("SELECT * FROM pin_storage")
    List<PinEntity> loadAll();

    @Query("SELECT COUNT(*) FROM pin_storage")
    int getCount();

    @Query("SELECT * FROM pin_storage WHERE sub_id = :subId LIMIT 1")
    LiveData<Optional<PinEntity>> findObservableBySubscriptionId(int subId);
}
