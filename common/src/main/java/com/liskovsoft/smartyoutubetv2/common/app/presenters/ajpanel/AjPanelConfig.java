package com.liskovsoft.smartyoutubetv2.common.app.presenters.ajpanel;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings of the AJPanel section.
 * <p>
 * The list, the order, the titles, the keys, the defaults and the choices are taken one-to-one from the Enigma2 plugin
 * (AJPanel v10.8.6, the "AJPanel Settings" screen). Items that only make sense on an Enigma2 receiver are kept (same
 * screen, same values) and are marked with {@link Entry#e2Only}.
 */
public final class AjPanelConfig {
    public static final String NAME = "AJPanel";
    public static final String VERSION = "v10.8.6";
    public static final String AUTO_FIND = "AUTO FIND";
    /** Sidebar section id. Must be less than SidebarService.RESERVED_ID (100) and not clash with MediaGroup.TYPE_* (the library uses 50). */
    public static final int SECTION_ID = 51;

    public static final int YESNO = 0;
    public static final int SELECT = 1;
    public static final int NUMBER = 2;
    public static final int PATH = 3;

    /** The settings screen row. A row without key is a separator. */
    public static final class Entry {
        public final String key;
        public final String title;
        public final int type;
        public final String def;
        public final String[][] choices; // {value, label}
        public final int min, max;
        public final boolean e2Only;

        Entry(String key, String title, int type, String def, String[][] choices, int min, int max, boolean e2Only) {
            this.key = key;
            this.title = title;
            this.type = type;
            this.def = def;
            this.choices = choices;
            this.min = min;
            this.max = max;
            this.e2Only = e2Only;
        }

        public boolean isSeparator() {
            return key == null;
        }

        public String labelOf(String value) {
            if (type == YESNO) {
                return "true".equals(value) ? "ON" : "OFF";
            }
            if (choices != null) {
                for (String[] c : choices) {
                    if (c[0].equals(value)) {
                        return c[1];
                    }
                }
            }
            return value;
        }
    }

    private static final String[][] KEYBOARD = {{"v", "Virtual Keyboard"}, {"s", "System Default"}};
    private static final String[][] FM_EXIT = {{"d", "Directory Up"}, {"e", "Exit"}};
    private static final String[][] HOTKEY = {{"off", "Disable"}, {"lok", "Long-OK"}, {"lesc", "Long-Exit"}, {"lred", "Long-Red"}};
    private static final String[][] SHOT = {{"off", "Disable"}, {"jpg", "JPG"}, {"png", "PNG"}, {"bmp", "BMP"}};
    private static final String[][] ENCODINGS = {{"utf8", "utf8"}, {"cp1256", "cp1256 (Arabic - Windows)"}, {"iso-8859-6", "iso-8859-6 (Arabic)"},
            {"cp1252", "cp1252 (Western - Windows)"}, {"iso-8859-1", "iso-8859-1 (Latin-1)"}, {"cp1251", "cp1251 (Cyrillic)"},
            {"iso-8859-9", "iso-8859-9 (Turkish)"}, {"utf-16", "utf-16"}};
    private static final String[][] REF_TYPE = {{"1", "1     (DVB Stream)"}, {"4097", "4097 (ServiceMp3)"}, {"5001", "5001 (GST Player)"},
            {"5002", "5002 (Ext-3 EPlayer)"}, {"8192", "8192 (HDMI input)"}, {"8193", "8193 (eServiceUri)"}, {"8793", "8793 (ServiceUrl)"}};
    // The original default is "1", which is not in the list: Enigma2 then falls back to the first item
    private static final String[][] FROZEN = {{"off", "Off"}, {"on+", "Refresh + Notify"}, {"on", "Silent Refresh"}};
    private static final String[][] RESUME = {{"off", "Disabled"}, {"a", "All"}, {"i", "IPTV Only"}, {"l", "All (Except IPTV)"}};
    private static final String[][] HOSTS_MODE = {{AUTO_FIND, "Auto Find"}, {"cust", "Custom Path"}};

    private static Entry yn(String key, String title, boolean def, boolean e2) {
        return new Entry(key, title, YESNO, String.valueOf(def), null, 0, 0, e2);
    }

    private static Entry sel(String key, String title, String def, String[][] ch, boolean e2) {
        return new Entry(key, title, SELECT, def, ch, 0, 0, e2);
    }

    private static Entry num(String key, String title, int def, int min, int max, boolean e2) {
        return new Entry(key, title, NUMBER, String.valueOf(def), null, min, max, e2);
    }

    private static Entry path(String key, String title, String def, boolean e2) {
        return new Entry(key, title, PATH, def, null, 0, 0, e2);
    }

    private static Entry sep() {
        return new Entry(null, null, YESNO, null, null, 0, 0, false);
    }

    /** Same rows and order as the "AJPanel Settings" screen of the plugin. */
    public static final Entry[] ENTRIES = {
            yn("showInMainMenu", "Show in Main Menu", false, true),
            yn("showInExtensionMenu", "Show in Extensions Menu", true, true),
            yn("showInChannelListMenu", "Show in Channel List Context Menu", true, true),
            yn("EventsInfoMenu", "Show in Events Info Menu", true, true),
            sel("keyboard", "Input Type", "v", KEYBOARD, false),
            sel("FileManagerExit", "Exit-Button Action (in File Manager and FTP)", "d", FM_EXIT, false),
            sel("hotkey_signal", "Player Bar Hotkey", "lesc", HOTKEY, true),
            sel("screenshotFType", "Screenshot", "jpg", SHOT, true),
            sel("subtDefaultEnc", "Subtitle Files Encoding Priority", "utf8", ENCODINGS, false),
            sep(),
            sel("iptvAddToBouquetRefType", "Default IPTV Reference Type (defines the player)", "4097", REF_TYPE, true),
            sel("autoResetFrozenIptvChan", "Auto Reset Frozen IPTV Channels", "off", FROZEN, true),
            yn("hideIptvServerAdultWords", "Skip Adults Channels (from IPTV Server)", false, false),
            yn("hideIptvServerChannPrefix", "Remove IPTV Channel Name Prefix (|EN| , |AR|Drama|)", false, false),
            num("portalConnTimeout", "Portal Servers Connection Timeout (seconds)", 2, 1, 5, false),
            path("iptvHostsMode", "IPTV Hosts Files Path (Playlist, Portal, M3U)", AUTO_FIND, false),
            sep(),
            sel("resumeMovies", "Auto Resume (Movies)", "a", RESUME, false),
            path("MovieDownloadPath", "Local Movies and IPTV Download Path", "", false),
            path("PIconsPath", "PIcons Path", "", true),
            path("customMenuPath", "Custom Menu Path", "", false),
            sep(),
            path("backupPath", "Main Working Path (Backup/Restore/Screenshot/etc.)", "", false),
    };

    // Values that are not shown on the settings screen but are kept by the plugin (file manager state)
    public static final String KEY_BROWSER_START = "browserStartPath";
    public static final String KEY_BOOKMARKS = "browserBookmarks";
    public static final String KEY_SORT_MODE = "browserSortMode";
    public static final String KEY_SORT_MIX = "browserSortMix";
    public static final String KEY_LAST_FIND = "lastFileManFindPatt";
    public static final String KEY_LAST_COPY_DIR = "lastCopyMoveDir";
    public static final String KEY_IPTV_HOSTS_DIRS = "iptvHostsDirs";

    private static final String PREFS = "ajpanel_config";

    private AjPanelConfig() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Entry find(String key) {
        for (Entry e : ENTRIES) {
            if (key.equals(e.key)) {
                return e;
            }
        }
        return null;
    }

    /** Raw stored value (for the settings screen) or the default. */
    public static String get(Context c, String key) {
        Entry e = find(key);
        String def = e != null ? e.def : defOfHidden(key);
        return prefs(c).getString(key, def);
    }

    public static boolean getBool(Context c, String key) {
        return "true".equals(get(c, key));
    }

    public static int getInt(Context c, String key) {
        try {
            return Integer.parseInt(get(c, key));
        } catch (NumberFormatException e) {
            Entry en = find(key);
            return en != null ? Integer.parseInt(en.def) : 0;
        }
    }

    public static void set(Context c, String key, String value) {
        prefs(c).edit().putString(key, value).apply();
    }

    public static void setAll(Context c, Map<String, String> values) {
        SharedPreferences.Editor ed = prefs(c).edit();
        for (Map.Entry<String, String> e : values.entrySet()) {
            ed.putString(e.getKey(), e.getValue());
        }
        ed.apply();
    }

    public static void resetAll(Context c) {
        prefs(c).edit().clear().apply();
    }

    private static String defOfHidden(String key) {
        switch (key) {
            case KEY_BROWSER_START: return "";
            case KEY_BOOKMARKS: return "";
            case KEY_SORT_MODE: return "0";
            case KEY_SORT_MIX: return "false";
            default: return "";
        }
    }

    public static Map<String, String> snapshot(Context c) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Entry e : ENTRIES) {
            if (!e.isSeparator()) {
                out.put(e.key, get(c, e.key));
            }
        }
        out.put(KEY_BROWSER_START, get(c, KEY_BROWSER_START));
        out.put(KEY_BOOKMARKS, get(c, KEY_BOOKMARKS));
        out.put(KEY_SORT_MODE, get(c, KEY_SORT_MODE));
        out.put(KEY_SORT_MIX, get(c, KEY_SORT_MIX));
        return out;
    }

    // ------------------------------------------------------------------ resolved paths (Android equivalents of the E2 paths)

    /** Root of the shared storage. The Enigma2 "/" has no meaning on Android. */
    public static File storageRoot() {
        File root = Environment.getExternalStorageDirectory();
        return root != null ? root : new File("/");
    }

    /** "Main Working Path" (backup, restore, screenshots...). Empty value: Download/AJPanel. */
    public static File workingDir(Context c) {
        return resolveDir(c, get(c, "backupPath"), new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AJPanel"));
    }

    /** "Local Movies and IPTV Download Path". Empty value: Movies/AJPanel. */
    public static File moviesDir(Context c) {
        return resolveDir(c, get(c, "MovieDownloadPath"), new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "AJPanel"));
    }

    public static File customMenuDir(Context c) {
        return resolveDir(c, get(c, "customMenuPath"), new File(workingDir(c), "menu"));
    }

    public static File piconsDir(Context c) {
        return resolveDir(c, get(c, "PIconsPath"), new File(workingDir(c), "picon"));
    }

    private static File resolveDir(Context c, String value, File fallback) {
        // "/" and the old receiver paths (/media/hdd/...) mean "not set" on Android
        if (value == null || value.trim().isEmpty() || value.equals("/") || value.startsWith("/media/") || value.startsWith("/usr/")) {
            return fallback;
        }
        return new File(value);
    }

    public static List<String> bookmarks(Context c) {
        List<String> out = new ArrayList<>();
        String raw = get(c, KEY_BOOKMARKS);
        if (raw.isEmpty()) {
            out.add(storageRoot().getAbsolutePath());
            out.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
            out.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath());
            return out;
        }
        for (String s : raw.split(",")) {
            if (!s.trim().isEmpty()) {
                out.add(s.trim());
            }
        }
        return out;
    }
}
