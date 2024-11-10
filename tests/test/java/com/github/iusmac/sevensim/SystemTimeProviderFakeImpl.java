package com.github.iusmac.sevensim;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * This implementation allows to manually control the current system time.
 */
public final class SystemTimeProviderFakeImpl implements SystemTimeProvider {
    private final SystemTimeProviderImpl mSystemTimeProvider = new SystemTimeProviderImpl();

    private LocalDateTime mNow;

    /** Return the {@link LocalDateTime#now}. */
    @Override
    public @NonNull LocalDateTime now() {
        return mNow != null ? mNow : mSystemTimeProvider.now();
    }

    public void setNow(final LocalDateTime now) {
        mNow = now;
    }
}
