package com.github.iusmac.sevensim.test;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * A {@link Handler} implementing an {@link Executor} that posts all tasks onto a separate thread.
 */
public final class AsyncTestTaskExecutor extends Handler implements Executor {
    public static final AsyncTestTaskExecutor INSTANCE;
    static {
        final var handlerThread = new HandlerThread(
                AsyncTestTaskExecutor.class.getSimpleName() + "Thread");
        handlerThread.start();
        INSTANCE = new AsyncTestTaskExecutor(handlerThread.getLooper());
    }

    private AsyncTestTaskExecutor(final Looper looper) {
        super(looper, /*callback=*/ null, /*async=*/ true);
    }

    @Override
    public void execute(final Runnable command) {
        if (!post(command)) {
            throw new RejectedExecutionException(this + " is shutting down");
        }
    }
}
