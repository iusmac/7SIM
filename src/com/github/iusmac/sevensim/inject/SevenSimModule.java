package com.github.iusmac.sevensim.inject;

import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

/** Application level module. */
@InstallIn(SingletonComponent.class)
@Module
public final class SevenSimModule {
    /** Do not initialize. */
    private SevenSimModule() {}
}
