package com.github.iusmac.sevensim;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;

/**
 * Time source representing the current system time. Used to inject a fake clock into unit tests.
 */
public interface SystemTimeProvider {
    /** Return the {@link LocalDateTime#now}. */
    @NonNull LocalDateTime now();
}
