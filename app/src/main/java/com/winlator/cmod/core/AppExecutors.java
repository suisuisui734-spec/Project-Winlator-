package com.winlator.cmod.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared app executors with idle worker expiry. */
public final class AppExecutors {
    private static final ExecutorService BACKGROUND = new ThreadPoolExecutor(
            0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(), namedFactory("Winlator-Background"));

    private static final ThreadPoolExecutor SERIAL_IO = new ThreadPoolExecutor(
            0, 1, 30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), namedFactory("Winlator-ContainerIO"));

    private static final ThreadPoolExecutor ASSET_IO = new ThreadPoolExecutor(
            4, 4, 30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), namedFactory("Winlator-Assets"));

    static {
        SERIAL_IO.allowCoreThreadTimeOut(true);
        ASSET_IO.allowCoreThreadTimeOut(true);
    }

    private AppExecutors() {}

    public static void executeBackground(Runnable task) {
        if (task != null) BACKGROUND.execute(task);
    }

    /** Serialize create, duplicate, and remove operations on container directories. */
    public static void executeSerialIo(Runnable task) {
        if (task != null) SERIAL_IO.execute(task);
    }

    /** Shared bounded concurrency for shortcut artwork loading. */
    public static ExecutorService assetExecutor() {
        return ASSET_IO;
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger index = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + index.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
