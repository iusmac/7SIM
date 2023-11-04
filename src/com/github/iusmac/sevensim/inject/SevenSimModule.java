package com.github.iusmac.sevensim.inject;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.github.iusmac.sevensim.SevenSimApplication;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

import javax.inject.Named;
import javax.inject.Singleton;

import com.github.iusmac.sevensim.SysProp;

/** Application level module. */
@InstallIn(SingletonComponent.class)
@Module
public final class SevenSimModule {
    @Named("Debug")
    @Singleton
    @Provides
    static boolean provideDebugState() {
        return new SysProp("debug", /*isPersistent=*/ false).isTrue() ||
            new SysProp("debug", /*isPersistent=*/ true).isTrue();
    }

    @Provides
    static SevenSimApplication provideApplicationInstance(
            final @ApplicationContext Context context) {

        return (SevenSimApplication) context;
    }

    @Singleton
    @Provides
    static SharedPreferences provideSharedPreferences(final @ApplicationContext Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /** Do not initialize. */
    private SevenSimModule() {}
}
