package com.github.iusmac.sevensim.inject;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
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

    /** Do not initialize. */
    private SevenSimModule() {}
}
