package com.github.iusmac.sevensim;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.github.iusmac.sevensim.telephony.PinEntity;
import com.github.iusmac.sevensim.telephony.PinStorageDao;

/**
 * Application database located in the CE (credential encrypted) storage.
 */
@Database(
    entities = {PinEntity.class},
    exportSchema = false,
    version = 1
)
public abstract class AppDatabaseCE extends RoomDatabase {
    public abstract PinStorageDao pinStorageDao();
}
