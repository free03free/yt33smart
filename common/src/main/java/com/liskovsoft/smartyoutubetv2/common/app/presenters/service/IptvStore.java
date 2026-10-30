package com.liskovsoft.smartyoutubetv2.common.app.presenters.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IPTV playlists of the user (M3U / M3U8 link, M3U file or a single stream link) and their channels.
 * <br>
 * Everything is stored locally in one json file. The user can delete, rename, hide, reorder channels and playlists.
 * <br>
 * A channel plays as a regular video with the id {@code iptv:<channelId>} (see {@link #isIptvId(String)}).
 */
public final class IptvStore {
    private static final String TAG = IptvStore.class.getSimpleName();
    private static final String FILE_NAME = "iptv_store.json";
    private static final int MAX_DOWNLOAD_BYTES = 80 * 1024 * 1024;

    public static final String ID_PREFIX = "iptv:";

    // Markers of the library cards (Video.reloadPageKey)
    public static final String MARK_OPEN = "iptv_open:";
    public static final String MARK_BACK = "iptv_back";
    public static final String MARK_ADD = "iptv_add";
    public static final String MARK_REFRESH = "iptv_refresh";
    public static final String MARK_SEARCH = "iptv_search";
    public static final String MARK_MENU = "iptv_menu";

    public static final int TYPE_URL = 0;
    public static final int TYPE_FILE = 1;
    public static final int TYPE_SINGLE = 2;
    /** A combined list: shows the channels of several other playlists together. Has no channels of its own. */
    public static final int TYPE_MERGED = 3;

    public static final int STATUS_UNKNOWN = 0;
    public static final int STATUS_OK = 1;
    public static final int STATUS_DEAD = 2;

    public static class Channel {
        public String id;
        public String name;
        public String logo;
        public String group; // null or empty: no group
        public String url;
        public boolean hidden;
        public boolean favorite;
        public boolean renamed; // the user has changed the name: keep it on the playlist update
        public Map<String, String> headers; // http headers of this channel (User-Agent, Referer...). Null: none
        public int status; // result of the last check: STATUS_*

        Channel copy() {
            Channel c = new Channel();
            c.id = id;
            c.name = name;
            c.logo = logo;
            c.group = group;
            c.url = url;
            c.hidden = hidden;
            c.favorite = favorite;
            c.renamed = renamed;
            c.headers = headers != null ? new LinkedHashMap<>(headers) : null;
            c.status = status;
            return c;
        }
    }

    public static class Playlist {
        public String id;
        public String name;
        public int type;
        public String source; // link or file path
        public long updated;
        int counter; // for the channel ids
        public Map<String, String> headers; // http headers for the whole playlist (the download and the channels). Null: none
        public List<String> sources = new ArrayList<>(); // TYPE_MERGED: ids of the combined playlists
        public List<Channel> channels = new ArrayList<>();
    }

    public interface Callback<T> {
        /**
         * Called on the main thread. Either result or error is set.
         */
        void onResult(T result, String error);
    }

    private static final Object LOCK = new Object();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Random RANDOM = new Random();
    private static final Pattern HLS_TAGS = Pattern.compile("#EXT-X-(TARGETDURATION|STREAM-INF|MEDIA-SEQUENCE)");

    private static List<Playlist> sPlaylists; // lazy
    private static String sOpenId; // opened playlist (memory only)
    private static String sSearch; // search inside the opened playlist (memory only)

    private IptvStore() {
    }

    // ------------------------------------------------------------------ ids

    public static boolean isIptvId(String videoId) {
        return videoId != null && videoId.startsWith(ID_PREFIX);
    }

    public static boolean isMarker(String key) {
        return key != null && (key.startsWith(MARK_OPEN) || key.equals(MARK_BACK) || key.equals(MARK_ADD)
                || key.equals(MARK_REFRESH) || key.equals(MARK_SEARCH) || key.equals(MARK_MENU));
    }

    private static String channelIdOf(String videoId) {
        return isIptvId(videoId) ? videoId.substring(ID_PREFIX.length()) : videoId;
    }

    private static String playlistIdOfChannel(String channelId) {
        int dot = channelId != null ? channelId.indexOf('.') : -1;
        return dot > 0 ? channelId.substring(0, dot) : null;
    }

    // ------------------------------------------------------------------ opened playlist / search

    public static synchronized String getOpen() {
        return sOpenId;
    }

    public static synchronized void setOpen(String playlistId) {
        sOpenId = playlistId;
        sSearch = null;
    }

    public static synchronized String getSearch() {
        return sSearch;
    }

    public static synchronized void setSearch(String query) {
        sSearch = query == null || query.trim().isEmpty() ? null : query.trim();
    }

    /**
     * Back: close the search first, then the opened playlist.
     */
    public static synchronized void back() {
        if (sSearch != null) {
            sSearch = null;
        } else {
            sOpenId = null;
        }
    }

    // ------------------------------------------------------------------ read

    /**
     * @return a snapshot: safe to use from any thread
     */
    public static List<Playlist> getPlaylists(Context context) {
        synchronized (LOCK) {
            load(context);
            List<Playlist> result = new ArrayList<>();
            for (Playlist p : sPlaylists) {
                result.add(copyOf(p));
            }
            return result;
        }
    }

    public static Playlist getPlaylist(Context context, String playlistId) {
        synchronized (LOCK) {
            load(context);
            Playlist p = find(playlistId);
            return p != null ? copyOf(p) : null;
        }
    }

    public static Channel findChannel(Context context, String videoOrChannelId) {
        synchronized (LOCK) {
            load(context);
            String channelId = channelIdOf(videoOrChannelId);
            Playlist p = find(playlistIdOfChannel(channelId));
            if (p != null) {
                for (Channel c : p.channels) {
                    if (c.id.equals(channelId)) {
                        return c.copy();
                    }
                }
            }
            return null;
        }
    }

    /**
     * Channel zapping: the next or previous visible channel of the same playlist.
     *
     * @param dir 1 or -1
     */
    public static Channel neighbor(Context context, String videoId, int dir) {
        synchronized (LOCK) {
            load(context);
            String channelId = channelIdOf(videoId);
            Playlist own = find(playlistIdOfChannel(channelId));
            if (own == null) {
                return null;
            }

            // Zapping goes through the list the user has opened (it may be a combined list)
            List<Channel> source = own.channels;
            Playlist opened = find(getOpen());
            if (opened != null && opened.type == TYPE_MERGED) {
                List<Channel> merged = effectiveChannels(opened);
                for (Channel c : merged) {
                    if (c.id.equals(channelId)) {
                        source = merged;
                        break;
                    }
                }
            }

            List<Channel> visible = new ArrayList<>();
            int current = -1;
            for (Channel c : source) {
                if (c.hidden && !c.id.equals(channelId)) {
                    continue;
                }
                if (c.id.equals(channelId)) {
                    current = visible.size();
                }
                visible.add(c);
            }

            if (current < 0 || visible.size() < 2) {
                return null;
            }

            int next = (current + dir + visible.size()) % visible.size();
            return visible.get(next).copy();
        }
    }

    /**
     * Makes the card/video of the channel.
     */
    public static Video toVideo(Playlist playlist, Channel channel) {
        Video video = new Video();
        video.videoId = ID_PREFIX + channel.id;
        video.title = channel.name;
        String second = channel.group != null && !channel.group.isEmpty() ? channel.group : (playlist != null ? playlist.name : null);
        String mark = channel.status == STATUS_DEAD ? "\uD83D\uDD34 " : channel.status == STATUS_OK ? "\uD83D\uDFE2 " : "";
        video.secondTitle = mark + (second != null ? second : "");
        video.cardImageUrl = channel.logo;
        video.bgImageUrl = channel.logo;
        return video;
    }

    public static Video toVideo(Context context, Channel channel) {
        Playlist playlist = channel != null ? getPlaylist(context, playlistIdOfChannel(channel.id)) : null;
        return toVideo(playlist, channel);
    }

    // ------------------------------------------------------------------ add / update

    public static void addFromUrl(Context context, String url, String name, Callback<Integer> callback) {
        final Context app = context.getApplicationContext();
        final String[] split = splitUrlAndHeaders(url.trim());
        final String link = split[0];
        final Map<String, String> headers = parseHeaderString(split[1]);

        IO.execute(() -> {
            try {
                String text = download(link, headers);

                if (isHlsManifest(text)) { // not a list of channels but a stream
                    addSingleInt(app, name, url.trim());
                    post(callback, 1, null);
                    return;
                }

                List<Channel> channels = parseM3u(text);
                if (channels.isEmpty()) {
                    post(callback, null, "no_channels");
                    return;
                }

                Playlist added = addPlaylistInt(app, name, TYPE_URL, link, channels);
                if (!headers.isEmpty()) {
                    setPlaylistHeaders(app, added.id, headers);
                }
                post(callback, channels.size(), null);
            } catch (Exception e) {
                Log.e(TAG, "Can't load the playlist: %s", e.getMessage());
                post(callback, null, errorText(e));
            }
        });
    }

    public static void addFromFile(Context context, String path, String name, Callback<Integer> callback) {
        final Context app = context.getApplicationContext();

        IO.execute(() -> {
            try {
                String text = readFile(path);
                List<Channel> channels = parseM3u(text);
                if (channels.isEmpty()) {
                    post(callback, null, "no_channels");
                    return;
                }

                addPlaylistInt(app, name, TYPE_FILE, path, channels);
                post(callback, channels.size(), null);
            } catch (Exception e) {
                Log.e(TAG, "Can't read the playlist file: %s", e.getMessage());
                post(callback, null, errorText(e));
            }
        });
    }

    public static void addSingle(Context context, String name, String url) {
        addSingleInt(context.getApplicationContext(), name, url.trim());
    }

    /**
     * Reloads the playlist from its source. Keeps ids, order, favorites, hidden flags and the renamed channels.
     */
    public static void refresh(Context context, String playlistId, Callback<Integer> callback) {
        final Context app = context.getApplicationContext();

        Playlist playlist = getPlaylist(app, playlistId);
        if (playlist == null) {
            post(callback, null, "not_found");
            return;
        }

        if (playlist.type == TYPE_SINGLE) {
            post(callback, playlist.channels.size(), null);
            return;
        }

        IO.execute(() -> {
            try {
                if (playlist.type == TYPE_MERGED) { // update all the combined playlists one after another
                    int total = 0;
                    int failed = 0;
                    String lastError = null;
                    for (String sourceId : playlist.sources) {
                        Playlist source = getPlaylist(app, sourceId);
                        if (source == null || source.type == TYPE_SINGLE || source.type == TYPE_MERGED) {
                            continue;
                        }
                        try {
                            total += refreshBlocking(app, source);
                        } catch (Exception e) {
                            failed++;
                            lastError = errorText(e);
                        }
                    }
                    if (failed > 0 && total == 0) {
                        post(callback, null, lastError);
                    } else {
                        post(callback, total, null);
                    }
                    return;
                }

                post(callback, refreshBlocking(app, playlist), null);
            } catch (Exception e) {
                Log.e(TAG, "Can't update the playlist: %s", e.getMessage());
                post(callback, null, errorText(e));
            }
        });
    }

    private static int refreshBlocking(Context app, Playlist playlist) throws IOException {
        String text = playlist.type == TYPE_FILE ? readFile(playlist.source) : download(playlist.source, playlist.headers);
        List<Channel> fresh = parseM3u(text);
        if (fresh.isEmpty()) {
            throw new IOException("no_channels");
        }

        synchronized (LOCK) {
            load(app);
            Playlist p = find(playlist.id);
            if (p == null) {
                throw new IOException("not_found");
            }
            mergeInto(p, fresh);
            p.updated = System.currentTimeMillis();
            saveLocked(app);
        }

        return fresh.size();
    }

    private static void mergeInto(Playlist p, List<Channel> fresh) {
        List<Channel> merged = new ArrayList<>();
        boolean[] used = new boolean[fresh.size()];

        // Old channels in the old order (the order chosen by the user stays)
        for (Channel old : p.channels) {
            int index = indexByUrl(fresh, used, old.url);
            if (index < 0) {
                continue; // removed from the source
            }
            used[index] = true;
            Channel f = fresh.get(index);
            if (!old.renamed) {
                old.name = f.name;
            }
            old.logo = f.logo;
            old.group = f.group;
            if (f.headers != null) {
                old.headers = f.headers; // the source knows better
            }
            merged.add(old);
        }

        // New channels at the end
        for (int i = 0; i < fresh.size(); i++) {
            if (!used[i]) {
                Channel c = fresh.get(i);
                c.id = p.id + "." + (p.counter++);
                merged.add(c);
            }
        }

        p.channels = merged;
    }

    private static int indexByUrl(List<Channel> list, boolean[] used, String url) {
        for (int i = 0; i < list.size(); i++) {
            if (!used[i] && list.get(i).url != null && list.get(i).url.equals(url)) {
                return i;
            }
        }
        return -1;
    }

    private static void addSingleInt(Context app, String name, String urlWithHeaders) {
        String[] split = splitUrlAndHeaders(urlWithHeaders);
        Channel c = new Channel();
        c.name = name != null && !name.trim().isEmpty() ? name.trim() : fallbackName(split[0]);
        c.url = split[0];
        Map<String, String> headers = parseHeaderString(split[1]);
        c.headers = headers.isEmpty() ? null : headers;
        List<Channel> list = new ArrayList<>();
        list.add(c);
        addPlaylistInt(app, c.name, TYPE_SINGLE, split[0], list);
    }

    private static Playlist addPlaylistInt(Context app, String name, int type, String source, List<Channel> channels) {
        synchronized (LOCK) {
            load(app);

            Playlist p = new Playlist();
            p.id = "p" + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(RANDOM.nextInt(1296), 36);
            p.name = name != null && !name.trim().isEmpty() ? name.trim() : fallbackName(source);
            p.type = type;
            p.source = source;
            p.updated = System.currentTimeMillis();

            for (Channel c : channels) {
                c.id = p.id + "." + (p.counter++);
                p.channels.add(c);
            }

            sPlaylists.add(p);
            saveLocked(app);
            return p;
        }
    }

    /**
     * A combined list: shows the channels of the given playlists together (the duplicates by link are shown once).
     */
    public static String createMerged(Context context, String name, List<String> sourceIds) {
        synchronized (LOCK) {
            load(context);

            Playlist p = new Playlist();
            p.id = "p" + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(RANDOM.nextInt(1296), 36);
            p.name = name != null && !name.trim().isEmpty() ? name.trim() : "IPTV";
            p.type = TYPE_MERGED;
            p.sources = new ArrayList<>(sourceIds);
            p.updated = System.currentTimeMillis();
            sPlaylists.add(p);
            saveLocked(context);
            return p.id;
        }
    }

    public static void setMergedSources(Context context, String playlistId, List<String> sourceIds) {
        synchronized (LOCK) {
            load(context);
            Playlist p = find(playlistId);
            if (p != null && p.type == TYPE_MERGED) {
                p.sources = new ArrayList<>(sourceIds);
                saveLocked(context);
            }
        }
    }

    // ------------------------------------------------------------------ manage playlists

    public static void deletePlaylist(Context context, String playlistId) {
        synchronized (LOCK) {
            load(context);
            Playlist p = find(playlistId);
            if (p != null) {
                sPlaylists.remove(p);
                for (Playlist other : sPlaylists) { // the combined lists forget the deleted playlist
                    other.sources.remove(playlistId);
                }
                saveLocked(context);
            }
        }

        synchronized (IptvStore.class) {
            if (playlistId != null && playlistId.equals(sOpenId)) {
                sOpenId = null;
                sSearch = null;
            }
        }
    }

    public static void renamePlaylist(Context context, String playlistId, String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        synchronized (LOCK) {
            load(context);
            Playlist p = find(playlistId);
            if (p != null) {
                p.name = name.trim();
                saveLocked(context);
            }
        }
    }

    /**
     * @param dir -1: earlier, 1: later
     */
    public static void movePlaylist(Context context, String playlistId, int dir) {
        synchronized (LOCK) {
            load(context);
            Playlist p = find(playlistId);
            int i = p != null ? sPlaylists.indexOf(p) : -1;
            int j = i + dir;
            if (i >= 0 && j >= 0 && j < sPlaylists.size()) {
                sPlaylists.set(i, sPlaylists.get(j));
                sPlaylists.set(j, p);
                saveLocked(context);
            }
        }
    }

    // ------------------------------------------------------------------ manage channels

    public static void deleteChannel(Context context, String channelId) {
        synchronized (LOCK) {
            load(context);
            Playlist p = find(playlistIdOfChannel(channelIdOf(channelId)));
            if (p != null) {
                String id = channelIdOf(channelId);
                for (int i = 0; i < p.channels.size(); i++) {
                    if (p.channels.get(i).id.equals(id)) {
                        p.channels.remove(i);
                        saveLocked(context);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Deletes all the channels of the group of the given channel.
     */
    public static void deleteGroupOf(Context context, String channelId) {
        synchronized (LOCK) {
            load(context);
            String id = channelIdOf(channelId);
            Playlist p = find(playlistIdOfChannel(id));
            Channel channel = p != null ? findIn(p, id) : null;
            if (channel != null) {
                String group = normGroup(channel.group);
                for (int i = p.channels.size() - 1; i >= 0; i--) {
                    if (normGroup(p.channels.get(i).group).equals(group)) {
                        p.channels.remove(i);
                    }
                }
                saveLocked(context);
            }
        }
    }

    public static void renameChannel(Context context, String channelId, String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        synchronized (LOCK) {
            load(context);
            Channel c = findAny(channelIdOf(channelId));
            if (c != null) {
                c.name = name.trim();
                c.renamed = true;
                saveLocked(context);
            }
        }
    }

    public static void setChannelUrl(Context context, String channelId, String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        synchronized (LOCK) {
            load(context);
            Channel c = findAny(channelIdOf(channelId));
            if (c != null) {
                c.url = url.trim();
                saveLocked(context);
            }
        }
    }

    public static void setHidden(Context context, String channelId, boolean hidden) {
        synchronized (LOCK) {
            load(context);
            Channel c = findAny(channelIdOf(channelId));
            if (c != null) {
                c.hidden = hidden;
                saveLocked(context);
            }
        }
    }

    public static void setFavorite(Context context, String channelId, boolean favorite) {
        synchronized (LOCK) {
            load(context);
            Channel c = findAny(channelIdOf(channelId));
            if (c != null) {
                c.favorite = favorite;
                saveLocked(context);
            }
        }
    }

    /**
     * Moves the channel among the visible channels of its group.
     *
     * @param dir -1: earlier, 1: later
     */
    public static void moveChannel(Context context, String channelId, int dir) {
        synchronized (LOCK) {
            load(context);
            String id = channelIdOf(channelId);
            Playlist p = find(playlistIdOfChannel(id));
            int i = p != null ? indexIn(p, id) : -1;
            if (i < 0) {
                return;
            }

            String group = normGroup(p.channels.get(i).group);
            for (int j = i + dir; j >= 0 && j < p.channels.size(); j += dir) {
                Channel other = p.channels.get(j);
                if (!other.hidden && normGroup(other.group).equals(group)) {
                    Channel c = p.channels.get(i);
                    p.channels.set(i, other);
                    p.channels.set(j, c);
                    saveLocked(context);
                    return;
                }
            }
        }
    }

    public static void moveChannelToStart(Context context, String channelId) {
        synchronized (LOCK) {
            load(context);
            String id = channelIdOf(channelId);
            Playlist p = find(playlistIdOfChannel(id));
            int i = p != null ? indexIn(p, id) : -1;
            if (i < 0) {
                return;
            }

            String group = normGroup(p.channels.get(i).group);
            for (int j = 0; j < i; j++) {
                Channel other = p.channels.get(j);
                if (!other.hidden && normGroup(other.group).equals(group)) {
                    Channel c = p.channels.remove(i);
                    p.channels.add(j, c);
                    saveLocked(context);
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ channels of a playlist (also a combined one)

    /**
     * @return a snapshot of the channels (for a combined list: the channels of all its playlists)
     */
    public static List<Channel> getChannels(Context context, String playlistId) {
        synchronized (LOCK) {
            load(context);
            Playlist p = find(playlistId);
            List<Channel> result = new ArrayList<>();
            if (p != null) {
                for (Channel c : effectiveChannels(p)) {
                    result.add(c.copy());
                }
            }
            return result;
        }
    }

    // call under LOCK
    private static List<Channel> effectiveChannels(Playlist p) {
        if (p.type != TYPE_MERGED) {
            return p.channels;
        }

        List<Channel> result = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        for (String sourceId : p.sources) {
            Playlist source = find(sourceId);
            if (source == null || source.type == TYPE_MERGED) {
                continue;
            }
            for (Channel c : source.channels) {
                if (c.url != null && !seenUrls.add(c.url)) {
                    continue; // the same link in several playlists: show once
                }
                result.add(c);
            }
        }

        return result;
    }

    // ------------------------------------------------------------------ http headers

    public static void setPlaylistHeaders(Context context, String playlistId, Map<String, String> headers) {
        synchronized (LOCK) {
            load(context);
            Playlist p = find(playlistId);
            if (p != null) {
                p.headers = headers == null || headers.isEmpty() ? null : new LinkedHashMap<>(headers);
                saveLocked(context);
            }
        }
    }

    public static void setChannelHeaders(Context context, String channelId, Map<String, String> headers) {
        synchronized (LOCK) {
            load(context);
            Channel c = findAny(channelIdOf(channelId));
            if (c != null) {
                c.headers = headers == null || headers.isEmpty() ? null : new LinkedHashMap<>(headers);
                saveLocked(context);
            }
        }
    }

    /**
     * The headers to play the channel with: the headers of its playlist and over them its own headers.
     */
    public static Map<String, String> headersFor(Context context, String videoOrChannelId) {
        synchronized (LOCK) {
            load(context);
            String channelId = channelIdOf(videoOrChannelId);
            Playlist p = find(playlistIdOfChannel(channelId));
            Channel c = p != null ? findIn(p, channelId) : null;
            Map<String, String> result = new LinkedHashMap<>();
            if (p != null && p.headers != null) {
                result.putAll(p.headers);
            }
            if (c != null && c.headers != null) {
                result.putAll(c.headers);
            }
            return result;
        }
    }

    // ------------------------------------------------------------------ check results

    /**
     * @param statuses channel id -> STATUS_*
     */
    public static void setStatuses(Context context, Map<String, Integer> statuses) {
        synchronized (LOCK) {
            load(context);
            for (Map.Entry<String, Integer> entry : statuses.entrySet()) {
                Channel c = findAny(channelIdOf(entry.getKey()));
                if (c != null) {
                    c.status = entry.getValue();
                }
            }
            saveLocked(context);
        }
    }

    public static void clearStatuses(Context context, String playlistId) {
        synchronized (LOCK) {
            load(context);
            Playlist p = find(playlistId);
            if (p != null) {
                for (Channel c : effectiveChannels(p)) {
                    c.status = STATUS_UNKNOWN;
                }
                saveLocked(context);
            }
        }
    }

    public static int countByStatus(Context context, String playlistId, int status) {
        synchronized (LOCK) {
            load(context);
            Playlist p = find(playlistId);
            int count = 0;
            if (p != null) {
                for (Channel c : effectiveChannels(p)) {
                    if (c.status == status) {
                        count++;
                    }
                }
            }
            return count;
        }
    }

    /**
     * Deletes the channels with the status (for a combined list - from the playlists it shows).
     *
     * @return how many channels are deleted
     */
    public static int deleteByStatus(Context context, String playlistId, int status) {
        synchronized (LOCK) {
            load(context);
            Playlist p = find(playlistId);
            if (p == null) {
                return 0;
            }

            Set<String> ids = new HashSet<>();
            for (Channel c : effectiveChannels(p)) {
                if (c.status == status) {
                    ids.add(c.id);
                }
            }

            int deleted = 0;
            for (Playlist owner : sPlaylists) {
                for (Iterator<Channel> it = owner.channels.iterator(); it.hasNext(); ) {
                    if (ids.contains(it.next().id)) {
                        it.remove();
                        deleted++;
                    }
                }
            }

            if (deleted > 0) {
                saveLocked(context);
            }
            return deleted;
        }
    }

    public static int hideByStatus(Context context, String playlistId, int status) {
        synchronized (LOCK) {
            load(context);
            Playlist p = find(playlistId);
            int hidden = 0;
            if (p != null) {
                for (Channel c : effectiveChannels(p)) {
                    if (c.status == status && !c.hidden) {
                        c.hidden = true;
                        hidden++;
                    }
                }
                if (hidden > 0) {
                    saveLocked(context);
                }
            }
            return hidden;
        }
    }

    // ------------------------------------------------------------------ M3U

    /**
     * #EXTINF:-1 tvg-id="" tvg-logo="http://logo" group-title="News",Channel name
     * http://stream
     */
    static List<Channel> parseM3u(String text) {
        List<Channel> result = new ArrayList<>();

        if (text == null) {
            return result;
        }

        String name = null;
        String logo = null;
        String group = null;
        Map<String, String> headers = null;

        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();

            if (line.startsWith("\uFEFF")) {
                line = line.substring(1).trim();
            }

            if (line.isEmpty()) {
                continue;
            }

            if (line.regionMatches(true, 0, "#EXTINF", 0, 7)) {
                logo = attr(line, "tvg-logo");
                group = attr(line, "group-title");
                String tvgName = attr(line, "tvg-name");
                name = displayName(line);
                if (name == null || name.isEmpty()) {
                    name = tvgName;
                }
            } else if (line.regionMatches(true, 0, "#EXTGRP:", 0, 8)) {
                if (group == null || group.isEmpty()) {
                    group = line.substring(8).trim();
                }
            } else if (line.regionMatches(true, 0, "#EXTVLCOPT:", 0, 11)) {
                // #EXTVLCOPT:http-user-agent=Foo, http-referrer=..., http-origin=...
                String option = line.substring(11).trim();
                int eq = option.indexOf('=');
                if (eq > 0) {
                    String key = option.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                    String value = option.substring(eq + 1).trim();
                    String header = key.equals("http-user-agent") ? "User-Agent" : key.equals("http-referrer") || key.equals("http-referer") ? "Referer"
                            : key.equals("http-origin") ? "Origin" : key.equals("http-cookie") ? "Cookie" : null;
                    if (header != null && !value.isEmpty()) {
                        if (headers == null) headers = new LinkedHashMap<>();
                        headers.put(header, value);
                    }
                }
            } else if (line.regionMatches(true, 0, "#EXTHTTP:", 0, 9)) {
                // #EXTHTTP:{"cookie":"...","User-Agent":"..."}
                try {
                    JSONObject json = new JSONObject(line.substring(9).trim());
                    Iterator<String> keys = json.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        String value = json.optString(key, "");
                        if (!value.isEmpty()) {
                            if (headers == null) headers = new LinkedHashMap<>();
                            headers.put(canonicalHeader(key), value);
                        }
                    }
                } catch (Exception e) {
                    // not a json: ignore
                }
            } else if (line.startsWith("#")) {
                // other tags are ignored
            } else {
                String[] split = splitUrlAndHeaders(line); // kodi style: url|User-Agent=...&Referer=...
                String url = split[0];

                if (url.contains("://")) {
                    Channel c = new Channel();
                    c.name = name != null && !name.isEmpty() ? name : fallbackName(url);
                    c.logo = logo != null && !logo.isEmpty() ? logo : null;
                    c.group = group != null && !group.trim().isEmpty() ? group.trim() : null;
                    c.url = url;

                    Map<String, String> inline = parseHeaderString(split[1]);
                    if (headers != null || !inline.isEmpty()) {
                        c.headers = new LinkedHashMap<>();
                        if (headers != null) c.headers.putAll(headers);
                        c.headers.putAll(inline);
                    }

                    result.add(c);
                }

                name = null;
                logo = null;
                group = null;
                headers = null;
            }
        }

        return result;
    }

    private static final Pattern ATTR = Pattern.compile("([\\w-]+)=[\"']([^\"']*)[\"']");

    private static String attr(String line, String key) {
        Matcher m = ATTR.matcher(line);
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(key)) {
                return m.group(2).trim();
            }
        }
        return null;
    }

    /**
     * The text after the first comma that is outside of the quotes.
     */
    private static String displayName(String line) {
        boolean inQuotes = false;
        char quote = 0;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == quote) {
                    inQuotes = false;
                }
            } else if (ch == '"' || ch == '\'') {
                inQuotes = true;
                quote = ch;
            } else if (ch == ',') {
                return line.substring(i + 1).trim();
            }
        }

        return null;
    }

    /**
     * Splits "link|User-Agent=Foo&Referer=http://x" into the link and the headers part.
     */
    public static String[] splitUrlAndHeaders(String value) {
        if (value == null) {
            return new String[]{"", ""};
        }

        int pipe = value.indexOf('|');
        if (pipe <= 0) {
            return new String[]{value.trim(), ""};
        }

        return new String[]{value.substring(0, pipe).trim(), value.substring(pipe + 1).trim()};
    }

    /**
     * "User-Agent=Foo&Referer=http%3A%2F%2Fx" (the values may be url encoded).
     */
    public static Map<String, String> parseHeaderString(String text) {
        Map<String, String> result = new LinkedHashMap<>();

        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        for (String pair : text.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }

            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            try {
                value = URLDecoder.decode(value, "UTF-8");
            } catch (Exception e) {
                // keep as is
            }

            if (!key.isEmpty() && !value.isEmpty()) {
                result.put(canonicalHeader(key), value);
            }
        }

        return result;
    }

    private static String canonicalHeader(String name) {
        String lower = name.trim().toLowerCase(Locale.ROOT);
        switch (lower) {
            case "user-agent":
            case "useragent":
            case "ua":
                return "User-Agent";
            case "referer":
            case "referrer":
                return "Referer";
            case "origin":
                return "Origin";
            case "cookie":
                return "Cookie";
            case "authorization":
                return "Authorization";
            default:
                return name.trim();
        }
    }

    private static boolean isHlsManifest(String text) {
        return text != null && HLS_TAGS.matcher(text).find();
    }

    private static String fallbackName(String url) {
        if (url == null) {
            return "IPTV";
        }

        String clean = url;
        int q = clean.indexOf('?');
        if (q > 0) {
            clean = clean.substring(0, q);
        }
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        int slash = clean.lastIndexOf('/');
        String tail = slash >= 0 ? clean.substring(slash + 1) : clean;
        return tail.isEmpty() ? url : tail;
    }

    // ------------------------------------------------------------------ network / files

    private static String download(String urlString, Map<String, String> headers) throws IOException {
        String current = urlString;

        for (int redirects = 0; redirects < 6; redirects++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            try {
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(60_000);
                connection.setInstanceFollowRedirects(false); // http -> https redirects are not followed automatically
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
                if (headers != null) { // the user's headers win
                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        connection.setRequestProperty(header.getKey(), header.getValue());
                    }
                }

                int code = connection.getResponseCode();

                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        throw new IOException("HTTP " + code);
                    }
                    current = new URL(new URL(current), location).toString();
                    continue;
                }

                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + code);
                }

                try (InputStream in = connection.getInputStream()) {
                    return readAll(in);
                }
            } finally {
                connection.disconnect();
            }
        }

        throw new IOException("Too many redirects");
    }

    private static String readFile(String path) throws IOException {
        File file = new File(path);
        if (!file.isFile()) {
            throw new IOException("File not found");
        }

        try (InputStream in = new FileInputStream(file)) {
            return readAll(in);
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;

        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_DOWNLOAD_BYTES) {
                throw new IOException("The playlist is too big");
            }
            out.write(buffer, 0, read);
        }

        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String errorText(Exception e) {
        String message = e.getMessage();
        return message != null && !message.isEmpty() ? message : e.getClass().getSimpleName();
    }

    private static <T> void post(Callback<T> callback, T result, String error) {
        if (callback != null) {
            MAIN.post(() -> callback.onResult(result, error));
        }
    }

    // ------------------------------------------------------------------ storage

    private static void load(Context context) {
        if (sPlaylists != null) {
            return;
        }

        sPlaylists = new ArrayList<>();
        File file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);

        if (!file.isFile()) {
            return;
        }

        try {
            JSONObject root = new JSONObject(readFile(file.getAbsolutePath()));
            JSONArray playlists = root.optJSONArray("playlists");

            for (int i = 0; playlists != null && i < playlists.length(); i++) {
                JSONObject jp = playlists.getJSONObject(i);
                Playlist p = new Playlist();
                p.id = jp.getString("id");
                p.name = jp.optString("name");
                p.type = jp.optInt("type");
                p.source = jp.optString("source", null);
                p.updated = jp.optLong("updated");
                p.counter = jp.optInt("counter");
                p.headers = readHeaders(jp.optJSONObject("hd"));

                JSONArray sources = jp.optJSONArray("s");
                for (int k = 0; sources != null && k < sources.length(); k++) {
                    p.sources.add(sources.getString(k));
                }

                JSONArray channels = jp.optJSONArray("channels");
                for (int j = 0; channels != null && j < channels.length(); j++) {
                    JSONObject jc = channels.getJSONObject(j);
                    Channel c = new Channel();
                    c.id = jc.getString("i");
                    c.name = jc.optString("n");
                    c.logo = jc.optString("l", null);
                    c.group = jc.optString("g", null);
                    c.url = jc.optString("u", null);
                    c.hidden = jc.optBoolean("h");
                    c.favorite = jc.optBoolean("f");
                    c.renamed = jc.optBoolean("r");
                    c.headers = readHeaders(jc.optJSONObject("hd"));
                    c.status = jc.optInt("st");
                    p.channels.add(c);
                }

                sPlaylists.add(p);
            }
        } catch (Exception e) {
            Log.e(TAG, "Can't read the IPTV store: %s", e.getMessage());
        }
    }

    private static Map<String, String> readHeaders(JSONObject json) {
        if (json == null || json.length() == 0) {
            return null;
        }

        Map<String, String> result = new LinkedHashMap<>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = json.optString(key, "");
            if (!value.isEmpty()) {
                result.put(key, value);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static JSONObject writeHeaders(Map<String, String> headers) throws org.json.JSONException {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }
        return json;
    }

    private static void saveLocked(Context context) {
        final File file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
        final String json;

        try {
            JSONArray playlists = new JSONArray();

            for (Playlist p : sPlaylists) {
                JSONObject jp = new JSONObject();
                jp.put("id", p.id);
                jp.put("name", p.name);
                jp.put("type", p.type);
                jp.put("source", p.source);
                jp.put("updated", p.updated);
                jp.put("counter", p.counter);
                if (p.headers != null) jp.put("hd", writeHeaders(p.headers));
                if (!p.sources.isEmpty()) jp.put("s", new JSONArray(p.sources));

                JSONArray channels = new JSONArray();
                for (Channel c : p.channels) {
                    JSONObject jc = new JSONObject();
                    jc.put("i", c.id);
                    jc.put("n", c.name);
                    if (c.logo != null) jc.put("l", c.logo);
                    if (c.group != null) jc.put("g", c.group);
                    jc.put("u", c.url);
                    if (c.hidden) jc.put("h", true);
                    if (c.favorite) jc.put("f", true);
                    if (c.renamed) jc.put("r", true);
                    if (c.headers != null) jc.put("hd", writeHeaders(c.headers));
                    if (c.status != STATUS_UNKNOWN) jc.put("st", c.status);
                    channels.put(jc);
                }
                jp.put("channels", channels);
                playlists.put(jp);
            }

            JSONObject root = new JSONObject();
            root.put("v", 1);
            root.put("playlists", playlists);
            json = root.toString();
        } catch (Exception e) {
            Log.e(TAG, "Can't build the IPTV store: %s", e.getMessage());
            return;
        }

        // Write in the background (the file may be big). One thread: the writes keep their order.
        IO.execute(() -> {
            File tmp = new File(file.getParentFile(), FILE_NAME + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } catch (IOException e) {
                Log.e(TAG, "Can't write the IPTV store: %s", e.getMessage());
                return;
            }
            if (!tmp.renameTo(file)) {
                file.delete();
                tmp.renameTo(file);
            }
        });
    }

    // ------------------------------------------------------------------ helpers (call under LOCK)

    private static Playlist find(String playlistId) {
        if (playlistId == null || sPlaylists == null) {
            return null;
        }
        for (Playlist p : sPlaylists) {
            if (p.id.equals(playlistId)) {
                return p;
            }
        }
        return null;
    }

    private static Channel findIn(Playlist p, String channelId) {
        int i = indexIn(p, channelId);
        return i >= 0 ? p.channels.get(i) : null;
    }

    private static int indexIn(Playlist p, String channelId) {
        for (int i = 0; i < p.channels.size(); i++) {
            if (p.channels.get(i).id.equals(channelId)) {
                return i;
            }
        }
        return -1;
    }

    private static Channel findAny(String channelId) {
        Playlist p = find(playlistIdOfChannel(channelId));
        return p != null ? findIn(p, channelId) : null;
    }

    private static String normGroup(String group) {
        return group == null ? "" : group.trim().toLowerCase(Locale.ROOT);
    }

    private static Playlist copyOf(Playlist p) {
        Playlist copy = new Playlist();
        copy.id = p.id;
        copy.name = p.name;
        copy.type = p.type;
        copy.source = p.source;
        copy.updated = p.updated;
        copy.counter = p.counter;
        copy.headers = p.headers != null ? new LinkedHashMap<>(p.headers) : null;
        copy.sources = new ArrayList<>(p.sources);
        for (Channel c : effectiveChannels(p)) { // a combined list shows the channels of its playlists
            copy.channels.add(c.copy());
        }
        return copy;
    }
}
