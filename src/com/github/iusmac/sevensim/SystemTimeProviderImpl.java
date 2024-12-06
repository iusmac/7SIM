package com.github.iusmac.sevensim;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Time source implementation representing the current system time.
 */
public final class SystemTimeProviderImpl implements SystemTimeProvider {
    /** Return the {@link LocalDateTime#now}. */
    @Override
    public @NonNull LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }
}
