package com.liskovsoft.smartyoutubetv2.common.app.presenters.ajpanel;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * AJPanel main page. The rows and their order are the ones of the plugin main screen:
 * File Manager, IPTV, Movies Browser, Services/Channels, Bouquet Editor, PIcons, EPG, Terminal, SoftCam, Plugins,
 * Backup &amp; Restore, Date/Time, Network. The four colour buttons become rows: Settings (green), Dev. Info. (yellow).
 * <p>
 * The rows that work on the files of an Enigma2 receiver (services, bouquets, PIcons, EPG, terminal, SoftCam, plugins)
 * are listed too, but they tell the truth: they are not available in the Android version.
 */
public final class AjPanelMenu {
    private AjPanelMenu() {
    }

    private static void later(Runnable r) {
        new Handler(Looper.getMainLooper()).postDelayed(r, 150);
    }

    /** Rows for the sidebar section (the section is a settings-style grid). */
    public static List<SettingsItem> sectionItems(Context c) {
        AjPanelI18n.init(c);
        List<SettingsItem> out = new ArrayList<>();
        out.add(new SettingsItem(AjPanelI18n.tr("File Manager"), () -> AjPanelFileManager.open(c)));
        out.add(new SettingsItem("IPTV", () -> AjPanelIptv.open(c)));
        out.add(new SettingsItem(AjPanelI18n.tr("Movies Browser"), () -> AjPanelFileManager.openAt(c, AjPanelConfig.moviesDir(c))));
        out.add(new SettingsItem(AjPanelI18n.tr("Services/Channels"), () -> notOnAndroid(c, AjPanelI18n.tr("Services/Channels"))));
        out.add(new SettingsItem(AjPanelI18n.tr("Bouquet Editor"), () -> notOnAndroid(c, AjPanelI18n.tr("Bouquet Editor"))));
        out.add(new SettingsItem("PIcons", () -> notOnAndroid(c, "PIcons")));
        out.add(new SettingsItem(AjPanelI18n.tr("EPG"), () -> notOnAndroid(c, AjPanelI18n.tr("EPG"))));
        out.add(new SettingsItem(AjPanelI18n.tr("Terminal"), () -> notOnAndroid(c, AjPanelI18n.tr("Terminal"))));
        out.add(new SettingsItem("SoftCam", () -> notOnAndroid(c, "SoftCam")));
        out.add(new SettingsItem(AjPanelI18n.tr("Plugins"), () -> notOnAndroid(c, AjPanelI18n.tr("Plugins"))));
        out.add(new SettingsItem(AjPanelI18n.tr("Backup & Restore"), () -> backupRestore(c)));
        out.add(new SettingsItem(AjPanelI18n.tr("Date/Time"), () -> dateTime(c)));
        out.add(new SettingsItem(AjPanelI18n.tr("Network"), () -> network(c)));
        out.add(new SettingsItem(AjPanelI18n.tr("Settings"), () -> AjPanelSettingsPresenter.show(c)));
        out.add(new SettingsItem(AjPanelI18n.tr("Dev. Info."), () -> deviceInfo(c)));
        return out;
    }

    private static void notOnAndroid(Context c, String name) {
        AppDialogPresenter d = AppDialogPresenter.instance(c);
        d.appendLongTextCategory(name, UiOptionItem.from(
                name + AjPanelI18n.tr(" works on the files and services of an Enigma2 receiver (bouquets, services lists, EPG database, ")
                        + AjPanelI18n.tr("softcam keys, plugin packages, shell).\n\nThis device runs Android, so there is nothing local for it to act on. ")
                        + AjPanelI18n.tr("It is not available in this version.")));
        d.showDialog(AjPanelConfig.NAME + " - " + name);
    }

    // ------------------------------------------------------------------ Dev. Info. (yellow)

    private static void deviceInfo(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append(AjPanelI18n.tr("Model:         ")).append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android:       ").append(Build.VERSION.RELEASE).append("  (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("CPU ABI:       ").append(Build.SUPPORTED_ABIS != null ? Arrays.toString(Build.SUPPORTED_ABIS) : Build.CPU_ABI).append('\n');

        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            sb.append(AjPanelI18n.tr("RAM:           ")).append(fmt(mi.totalMem - mi.availMem)).append(AjPanelI18n.tr(" used / ")).append(fmt(mi.totalMem)).append('\n');
        } catch (Exception ignored) {
        }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            sb.append(AjPanelI18n.tr("Data storage:  ")).append(fmt(fs.getAvailableBytes())).append(AjPanelI18n.tr(" free / ")).append(fmt(fs.getTotalBytes())).append('\n');
            StatFs sd = new StatFs(AjPanelConfig.storageRoot().getPath());
            sb.append(AjPanelI18n.tr("Shared storage:")).append(' ').append(fmt(sd.getAvailableBytes())).append(AjPanelI18n.tr(" free / ")).append(fmt(sd.getTotalBytes())).append('\n');
        } catch (Exception ignored) {
        }
        sb.append(AjPanelI18n.tr("Working path:  ")).append(AjPanelConfig.workingDir(c)).append('\n');
        sb.append(AjPanelI18n.tr("Movies path:   ")).append(AjPanelConfig.moviesDir(c)).append('\n');
        sb.append(AjPanelConfig.NAME).append(":       ").append(AjPanelConfig.VERSION).append(AjPanelI18n.tr(" (Android port)\n"));

        AppDialogPresenter d = AppDialogPresenter.instance(c);
        d.appendLongTextCategory(AjPanelI18n.tr("Device Information"), UiOptionItem.from(sb.toString()));
        d.showDialog(AjPanelI18n.tr("Device Information"));
    }

    private static String fmt(long b) {
        if (b < 1024L * 1024 * 1024) return String.format(Locale.US, "%.0f MB", b / 1048576.0);
        return String.format(Locale.US, "%.2f GB", b / 1073741824.0);
    }

    // ------------------------------------------------------------------ Date/Time and Network

    private static void dateTime(Context c) {
        Date now = new Date();
        TimeZone tz = TimeZone.getDefault();
        String text = AjPanelI18n.tr("Date:       ") + new SimpleDateFormat("EEEE, yyyy-MM-dd", Locale.getDefault()).format(now) + "\n"
                + AjPanelI18n.tr("Time:       ") + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now) + "\n"
                + AjPanelI18n.tr("Time zone:  ") + tz.getID() + "  (UTC" + new SimpleDateFormat("Z", Locale.US).format(now) + ")\n"
                + AjPanelI18n.tr("Auto time:  ") + (Settings.Global.getInt(c.getContentResolver(), Settings.Global.AUTO_TIME, 0) == 1 ? AjPanelI18n.tr("ON (network time)") : AjPanelI18n.tr("OFF")) + "\n";
        AppDialogPresenter d = AppDialogPresenter.instance(c);
        d.appendLongTextCategory(AjPanelI18n.tr("Date/Time"), UiOptionItem.from(text));
        d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u2699\uFE0F Open Android Date & time settings"), o -> openSettings(c, Settings.ACTION_DATE_SETTINGS)));
        d.showDialog(AjPanelI18n.tr("Date/Time"));
    }

    private static void network(Context c) {
        StringBuilder sb = new StringBuilder();
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            sb.append(AjPanelI18n.tr("Connection:  ")).append(ni != null && ni.isConnected() ? ni.getTypeName() + AjPanelI18n.tr(" (connected)") : AjPanelI18n.tr("not connected")).append("\n");
        } catch (Exception ignored) {
        }
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    String h = a.getHostAddress();
                    int pct = h.indexOf('%');
                    if (pct > 0) h = h.substring(0, pct);
                    sb.append(String.format(Locale.US, "%-10s", nif.getName())).append(h).append("\n");
                }
            }
        } catch (Exception e) {
            sb.append(AjPanelI18n.tr("Interfaces: ")).append(e.getMessage()).append("\n");
        }
        AppDialogPresenter d = AppDialogPresenter.instance(c);
        d.appendLongTextCategory(AjPanelI18n.tr("Network"), UiOptionItem.from(sb.toString()));
        d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u2699\uFE0F Open Android network settings"), o -> openSettings(c, Settings.ACTION_WIRELESS_SETTINGS)));
        d.showDialog(AjPanelI18n.tr("Network"));
    }

    private static void openSettings(Context c, String action) {
        try {
            Intent i = new Intent(action);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            MessageHelpers.showMessage(c, AjPanelI18n.tr("Settings screen is not available on this device"));
        }
    }

    // ------------------------------------------------------------------ Backup & Restore (settings of AJPanel)

    private static void backupRestore(Context c) {
        AppDialogPresenter d = AppDialogPresenter.instance(c);
        File dir = AjPanelConfig.workingDir(c);

        d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDCBE Backup AJPanel settings"), o -> {
            d.closeDialog();
            try {
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new Exception(AjPanelI18n.tr("Cannot create ") + dir);
                }
                JSONObject json = new JSONObject();
                for (Map.Entry<String, String> e : AjPanelConfig.snapshot(c).entrySet()) {
                    json.put(e.getKey(), e.getValue());
                }
                File f = new File(dir, "ajpanel_settings_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json");
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                MessageHelpers.showMessage(c, AjPanelI18n.tr("Saved: ") + f.getAbsolutePath());
            } catch (Exception e) {
                MessageHelpers.showMessage(c, AjPanelI18n.tr("Backup failed: ") + e.getMessage());
            }
        }));

        File[] found = dir.listFiles((p, name) -> name.startsWith("ajpanel_settings_") && name.endsWith(".json"));
        List<OptionItem> items = new ArrayList<>();
        if (found != null) {
            Arrays.sort(found, new Comparator<File>() {
                @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
            });
            for (File f : found) {
                items.add(UiOptionItem.from(f.getName(), o -> {
                    d.closeDialog();
                    restore(c, f);
                }));
            }
        }
        if (items.isEmpty()) {
            items.add(UiOptionItem.from(AjPanelI18n.tr("(no backup files in ") + dir.getAbsolutePath() + ")", o -> {}));
        }
        d.appendStringsCategory(AjPanelI18n.tr("Restore  -  ") + dir.getAbsolutePath(), items);
        d.showDialog(AjPanelI18n.tr("Backup & Restore"));
    }

    private static void restore(Context c, File f) {
        try {
            byte[] data = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                int off = 0, n;
                while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            }
            JSONObject json = new JSONObject(new String(data, StandardCharsets.UTF_8));
            Map<String, String> values = new LinkedHashMap<>();
            Map<String, String> known = AjPanelConfig.snapshot(c);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (known.containsKey(k)) { // only the keys AJPanel knows
                    values.put(k, json.getString(k));
                }
            }
            AjPanelConfig.setAll(c, values);
            MessageHelpers.showMessage(c, AjPanelI18n.tr("Restored ") + values.size() + AjPanelI18n.tr(" settings"));
        } catch (Exception e) {
            MessageHelpers.showMessage(c, AjPanelI18n.tr("Restore failed: ") + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ dialog version of the main page

    /** Opens the main page as a dialog (same rows as the section). */
    public static void showDialog(Context c) {
        AjPanelI18n.init(c);
        AppDialogPresenter d = AppDialogPresenter.instance(c);
        List<OptionItem> items = new ArrayList<>();
        for (SettingsItem s : sectionItems(c)) {
            items.add(UiOptionItem.from(s.title, o -> {
                d.closeDialog();
                later(s.onClick);
            }));
        }
        d.appendStringsCategory(AjPanelConfig.NAME + " - " + AjPanelConfig.VERSION, items);
        d.showDialog(AjPanelConfig.NAME + " - " + AjPanelConfig.VERSION);
    }
}
