package com.github.iusmac.sevensim;

import android.util.Log;

import androidx.annotation.VisibleForTesting;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Named;

public final class Logger {
    private static final String TAG_PREFIX = "7SIM";

    @VisibleForTesting
    static final AtomicBoolean IS_LOGCAT_CHATTY_ENABLED = new AtomicBoolean();

    private final boolean mIsDebuggable;
    private final String mTag;

    @AssistedInject
    public Logger(final Runtime javaRuntime, final @Named("Debug") boolean debug,
            final @Assisted String tag) {

        mIsDebuggable = debug;

        // Prefix all tags with app name to facilitate searching in massive log files
        mTag = TAG_PREFIX + "." + tag;

        // Allow the app to be "chatty" (send >5 logs/sec.) while debugging to avoid log suppression
        if (mIsDebuggable && IS_LOGCAT_CHATTY_ENABLED.compareAndSet(false, true)) {
            try {
                javaRuntime.exec(new String[] {
                    "logcat", "-P", "'" + android.os.Process.myPid() + "'"
                }).waitFor();
            } catch (InterruptedException | IOException e) {
                e("Failed to disable app log suppression in logcat.", e);
            }
        }
    }

    public boolean isVerboseLoggable() { return Log.isLoggable(mTag, Log.VERBOSE); }
    public boolean isDebugLoggable() { return Log.isLoggable(mTag, Log.DEBUG); }
    public boolean isInfoLoggable() { return Log.isLoggable(mTag, Log.INFO); }
    public boolean isWarnLoggable() { return Log.isLoggable(mTag, Log.WARN); }
    public boolean isErrorLoggable() { return Log.isLoggable(mTag, Log.ERROR); }
    public boolean isWtfLoggable() { return Log.isLoggable(mTag, Log.ASSERT); }

    public void v(String message, Object... args) {
        if (mIsDebuggable || isVerboseLoggable()) {
            Log.v(mTag, format(message, args));
        }
    }

    public void d(String message, Object... args) {
        if (mIsDebuggable || isDebugLoggable()) {
            Log.d(mTag, format(message, args));
        }
    }

    public void i(String message, Object... args) {
        if (mIsDebuggable || isInfoLoggable()) {
            Log.i(mTag, format(message, args));
        }
    }

    public void w(String message, Object... args) {
        if (mIsDebuggable || isWarnLoggable()) {
            Log.w(mTag, format(message, args));
        }
    }

    public void e(String message, Object... args) {
        if (mIsDebuggable || isErrorLoggable()) {
            Log.e(mTag, format(message, args));
        }
    }

    public void e(String message, Throwable e) {
        if (mIsDebuggable || isErrorLoggable()) {
            Log.e(mTag, message, e);
        }
    }

    public void wtf(String message, Object... args) {
        if (mIsDebuggable || isWtfLoggable()) {
            Log.wtf(mTag, format(message, args));
        }
    }

    public void wtf(Throwable e) {
        if (mIsDebuggable || isWtfLoggable()) {
            Log.wtf(mTag, e.getMessage(), e);
        }
    }

    /**
     * Factory to create {@link Logger} instances via the {@link AssistedInject} constructor.
     */
    @AssistedFactory
    public interface Factory {
        /** Create a {@link Logger} instance with a specific log tag. */
        Logger create(String tag);
    }

    private static String format(String message, Object... args) {
        return args == null || args.length == 0 ? message : String.format(Locale.US, message, args);
    }
}
