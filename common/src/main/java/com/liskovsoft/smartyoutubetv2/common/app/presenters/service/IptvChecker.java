package com.liskovsoft.smartyoutubetv2.common.app.presenters.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Quick check of the IPTV channels: which links answer and which don't.
 * <br>
 * Only the first bytes of every stream are read, so the check is fast and light.
 * Few connections at the same time: many IPTV servers limit the connections of one account.
 */
public final class IptvChecker {
    private static final String TAG = IptvChecker.class.getSimpleName();
    private static final int THREADS = 6;
    private static final int CONNECT_TIMEOUT_MS = 6_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final int PEEK_BYTES = 2048;
    private static final int SAVE_EVERY = 250;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile boolean sRunning;
    private static volatile boolean sCancel;

    public static class Result {
        public int ok;
        public int dead;
        public int skipped; // not http links (rtmp, udp...): can't be checked here
        public boolean cancelled;
    }

    public interface Listener {
        /** Main thread. */
        void onProgress(int done, int total);

        /** Main thread. */
        void onDone(Result result);
    }

    private IptvChecker() {
    }

    public static boolean isRunning() {
        return sRunning;
    }

    public static void cancel() {
        sCancel = true;
    }

    /**
     * Checks all the channels of the playlist (a combined list too) and saves the results in the store.
     */
    public static void start(Context context, String playlistId, Listener listener) {
        if (sRunning) {
            return;
        }

        final Context app = context.getApplicationContext();
        final List<IptvStore.Channel> channels = IptvStore.getChannels(app, playlistId);

        sRunning = true;
        sCancel = false;

        Thread coordinator = new Thread(() -> run(app, channels, listener), "IptvChecker");
        coordinator.setDaemon(true);
        coordinator.start();
    }

    private static void run(Context app, List<IptvStore.Channel> channels, Listener listener) {
        final Result result = new Result();
        final int total = channels.size();
        final AtomicInteger done = new AtomicInteger();
        final AtomicInteger okCount = new AtomicInteger();
        final AtomicInteger deadCount = new AtomicInteger();
        final AtomicInteger skippedCount = new AtomicInteger();
        final Map<String, Integer> pending = new ConcurrentHashMap<>();
        final Object saveLock = new Object();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        try {
            for (IptvStore.Channel channel : channels) {
                pool.execute(() -> {
                    if (sCancel) {
                        return;
                    }

                    int status = check(app, channel);

                    if (status == IptvStore.STATUS_OK) {
                        okCount.incrementAndGet();
                    } else if (status == IptvStore.STATUS_DEAD) {
                        deadCount.incrementAndGet();
                    } else {
                        skippedCount.incrementAndGet();
                    }

                    pending.put(channel.id, status);
                    int finished = done.incrementAndGet();

                    // Save from time to time: the results are not lost when the app is closed
                    if (finished % SAVE_EVERY == 0) {
                        synchronized (saveLock) {
                            flush(app, pending);
                        }
                    }

                    if (listener != null && (finished % 10 == 0 || finished == total)) {
                        MAIN.post(() -> listener.onProgress(finished, total));
                    }
                });
            }

            pool.shutdown();

            while (!pool.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                if (sCancel) {
                    pool.shutdownNow();
                    break;
                }
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
        }

        synchronized (saveLock) {
            flush(app, pending);
        }

        result.ok = okCount.get();
        result.dead = deadCount.get();
        result.skipped = skippedCount.get();
        result.cancelled = sCancel;

        sRunning = false;
        sCancel = false;

        if (listener != null) {
            MAIN.post(() -> listener.onDone(result));
        }
    }

    private static void flush(Context app, Map<String, Integer> pending) {
        if (pending.isEmpty()) {
            return;
        }

        Map<String, Integer> copy = new HashMap<>(pending);
        pending.clear();
        IptvStore.setStatuses(app, copy);
    }

    /**
     * @return STATUS_OK, STATUS_DEAD or STATUS_UNKNOWN (the link can't be checked)
     */
    private static int check(Context app, IptvStore.Channel channel) {
        String url = channel.url;

        if (url == null) {
            return IptvStore.STATUS_DEAD;
        }

        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return IptvStore.STATUS_UNKNOWN;
        }

        Map<String, String> headers = IptvStore.headersFor(app, channel.id);
        String current = url;

        try {
            for (int redirects = 0; redirects < 5; redirects++) {
                HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();

                try {
                    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    connection.setReadTimeout(READ_TIMEOUT_MS);
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
                    connection.setRequestProperty("Range", "bytes=0-" + (PEEK_BYTES - 1));
                    for (Map.Entry<String, String> header : headers.entrySet()) { // the same headers as the player uses
                        connection.setRequestProperty(header.getKey(), header.getValue());
                    }

                    int code = connection.getResponseCode();

                    if (code >= 300 && code < 400) {
                        String location = connection.getHeaderField("Location");
                        if (location == null) {
                            return IptvStore.STATUS_DEAD;
                        }
                        current = new URL(new URL(current), location).toString();
                        continue;
                    }

                    if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                        return IptvStore.STATUS_DEAD;
                    }

                    return inspectBody(connection, current) ? IptvStore.STATUS_OK : IptvStore.STATUS_DEAD;
                } finally {
                    connection.disconnect(); // closes the stream: the server doesn't keep sending
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.d(TAG, "Channel is not available: %s (%s)", channel.name, e.getMessage());
        }

        return IptvStore.STATUS_DEAD;
    }

    private static boolean inspectBody(HttpURLConnection connection, String url) throws IOException {
        String contentType = connection.getContentType();
        contentType = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";
        boolean playlist = url.toLowerCase(Locale.ROOT).contains("m3u8") || contentType.contains("mpegurl");

        byte[] buffer = new byte[PEEK_BYTES];
        int total = 0;

        try (InputStream in = connection.getInputStream()) {
            int read;
            while (total < buffer.length && (read = in.read(buffer, total, buffer.length - total)) != -1) {
                total += read;
                if (playlist && total >= 16) {
                    break;
                }
                if (!playlist && total >= 188) { // one transport stream packet is enough to see the data comes
                    break;
                }
            }
        }

        if (total <= 0) {
            return false;
        }

        if (playlist) {
            String head = new String(buffer, 0, total, StandardCharsets.UTF_8).trim();
            return head.startsWith("#EXTM3U") || head.startsWith("#EXT");
        }

        // A web page instead of the stream: the server says the channel is gone
        if (contentType.contains("text/html")) {
            return false;
        }

        return true;
    }
}
