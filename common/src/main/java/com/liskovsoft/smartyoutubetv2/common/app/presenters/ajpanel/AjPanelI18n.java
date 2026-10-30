package com.liskovsoft.smartyoutubetv2.common.app.presenters.ajpanel;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Arabic translation of the AJPanel section. The texts of the section are written in English in the code; {@link #tr}
 * returns the Arabic text when the app (or device) language is Arabic and the same text otherwise. Leading and trailing
 * symbols/emoji/spaces of a text are kept as they are, only the words between them are translated.
 */
public final class AjPanelI18n {
    private static final Map<String, String> AR = new HashMap<>();
    private static volatile boolean sArabic = "ar".equals(Locale.getDefault().getLanguage());

    private AjPanelI18n() {
    }

    /** Detects the language of the app from the given context (call it when the section is opened). */
    public static void init(Context context) {
        try {
            if (context != null) {
                Configuration conf = context.getResources().getConfiguration();
                Locale locale = Build.VERSION.SDK_INT >= 24 ? conf.getLocales().get(0) : conf.locale;
                if (locale != null) {
                    sArabic = "ar".equals(locale.getLanguage());
                    return;
                }
            }
        } catch (Exception e) {
            // use the default locale
        }
        sArabic = "ar".equals(Locale.getDefault().getLanguage());
    }

    public static boolean isArabic() {
        return sArabic;
    }

    public static String tr(String text) {
        if (!sArabic || text == null || text.isEmpty()) {
            return text;
        }
        int start = 0;
        int end = text.length();
        while (start < end) {
            int cp = text.codePointAt(start);
            if (Character.isWhitespace(cp) || cp == 0xFE0F || (cp >= 0x2190 && !Character.isLetterOrDigit(cp))) {
                start += Character.charCount(cp);
            } else {
                break;
            }
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (start >= end) {
            return text;
        }
        String translated = AR.get(text.substring(start, end));
        if (translated == null) {
            return text;
        }
        return text.substring(0, start) + translated + text.substring(end);
    }

    private static void p(String en, String ar) {
        AR.put(en, ar);
    }

    static {
        p("File Manager", "\u0645\u062f\u064a\u0631 \u0627\u0644\u0645\u0644\u0641\u0627\u062a");
        p("Movies Browser", "\u0645\u062a\u0635\u0641\u062d \u0627\u0644\u0623\u0641\u0644\u0627\u0645");
        p("Services/Channels", "\u0627\u0644\u062e\u062f\u0645\u0627\u062a/\u0627\u0644\u0642\u0646\u0648\u0627\u062a");
        p("Bouquet Editor", "\u0645\u062d\u0631\u0631 \u0627\u0644\u0628\u0627\u0642\u0627\u062a");
        p("EPG", "\u062f\u0644\u064a\u0644 \u0627\u0644\u0628\u0631\u0627\u0645\u062c (EPG)");
        p("Terminal", "\u0627\u0644\u0637\u0631\u0641\u064a\u0629");
        p("Plugins", "\u0627\u0644\u0625\u0636\u0627\u0641\u0627\u062a");
        p("Backup & Restore", "\u0627\u0644\u0646\u0633\u062e \u0627\u0644\u0627\u062d\u062a\u064a\u0627\u0637\u064a \u0648\u0627\u0644\u0627\u0633\u062a\u0639\u0627\u062f\u0629");
        p("Date/Time", "\u0627\u0644\u062a\u0627\u0631\u064a\u062e/\u0627\u0644\u0648\u0642\u062a");
        p("Network", "\u0627\u0644\u0634\u0628\u0643\u0629");
        p("Settings", "\u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a");
        p("Dev. Info.", "\u0645\u0639\u0644\u0648\u0645\u0627\u062a \u0627\u0644\u062c\u0647\u0627\u0632");
        p("Show in Main Menu", "\u0625\u0638\u0647\u0627\u0631 \u0641\u064a \u0627\u0644\u0642\u0627\u0626\u0645\u0629 \u0627\u0644\u0631\u0626\u064a\u0633\u064a\u0629");
        p("Show in Extensions Menu", "\u0625\u0638\u0647\u0627\u0631 \u0641\u064a \u0642\u0627\u0626\u0645\u0629 \u0627\u0644\u0625\u0636\u0627\u0641\u0627\u062a");
        p("Show in Channel List Context Menu", "\u0625\u0638\u0647\u0627\u0631 \u0641\u064a \u0642\u0627\u0626\u0645\u0629 \u0633\u064a\u0627\u0642 \u0627\u0644\u0642\u0646\u0648\u0627\u062a");
        p("Show in Events Info Menu", "\u0625\u0638\u0647\u0627\u0631 \u0641\u064a \u0642\u0627\u0626\u0645\u0629 \u0645\u0639\u0644\u0648\u0645\u0627\u062a \u0627\u0644\u0623\u062d\u062f\u0627\u062b");
        p("Input Type", "\u0646\u0648\u0639 \u0627\u0644\u0625\u062f\u062e\u0627\u0644");
        p("Exit-Button Action (in File Manager and FTP)", "\u0625\u062c\u0631\u0627\u0621 \u0632\u0631 \u0627\u0644\u062e\u0631\u0648\u062c (\u0641\u064a \u0645\u062f\u064a\u0631 \u0627\u0644\u0645\u0644\u0641\u0627\u062a \u0648FTP)");
        p("Player Bar Hotkey", "\u0627\u062e\u062a\u0635\u0627\u0631 \u0634\u0631\u064a\u0637 \u0627\u0644\u0645\u0634\u063a\u0651\u0644");
        p("Screenshot", "\u0644\u0642\u0637\u0629 \u0627\u0644\u0634\u0627\u0634\u0629");
        p("Subtitle Files Encoding Priority", "\u0623\u0648\u0644\u0648\u064a\u0629 \u062a\u0631\u0645\u064a\u0632 \u0645\u0644\u0641\u0627\u062a \u0627\u0644\u062a\u0631\u062c\u0645\u0629");
        p("Default IPTV Reference Type (defines the player)", "\u0646\u0648\u0639 \u0645\u0631\u062c\u0639 IPTV \u0627\u0644\u0627\u0641\u062a\u0631\u0627\u0636\u064a (\u064a\u062d\u062f\u062f \u0627\u0644\u0645\u0634\u063a\u0651\u0644)");
        p("Auto Reset Frozen IPTV Channels", "\u0625\u0639\u0627\u062f\u0629 \u0636\u0628\u0637 \u0642\u0646\u0648\u0627\u062a IPTV \u0627\u0644\u0645\u062a\u062c\u0645\u062f\u0629 \u062a\u0644\u0642\u0627\u0626\u064a\u064b\u0627");
        p("Skip Adults Channels (from IPTV Server)", "\u062a\u062e\u0637\u064a \u0642\u0646\u0648\u0627\u062a \u0627\u0644\u0628\u0627\u0644\u063a\u064a\u0646 (\u0645\u0646 \u062e\u0627\u062f\u0645 IPTV)");
        p("Remove IPTV Channel Name Prefix (|EN| , |AR|Drama|)", "\u0625\u0632\u0627\u0644\u0629 \u0628\u0627\u062f\u0626\u0629 \u0627\u0633\u0645 \u0642\u0646\u0627\u0629 IPTV (|EN| \u060c |AR|Drama|)");
        p("Portal Servers Connection Timeout (seconds)", "\u0645\u0647\u0644\u0629 \u0627\u0644\u0627\u062a\u0635\u0627\u0644 \u0628\u062e\u0648\u0627\u062f\u0645 \u0627\u0644\u0628\u0648\u0627\u0628\u0629 (\u062b\u0648\u0627\u0646\u064d)");
        p("IPTV Hosts Files Path (Playlist, Portal, M3U)", "\u0645\u0633\u0627\u0631 \u0645\u0644\u0641\u0627\u062a \u0645\u0636\u064a\u0641\u0627\u062a IPTV (\u0642\u0627\u0626\u0645\u0629\u060c \u0628\u0648\u0627\u0628\u0629\u060c M3U)");
        p("Auto Resume (Movies)", "\u0627\u0633\u062a\u0626\u0646\u0627\u0641 \u062a\u0644\u0642\u0627\u0626\u064a (\u0627\u0644\u0623\u0641\u0644\u0627\u0645)");
        p("Local Movies and IPTV Download Path", "\u0645\u0633\u0627\u0631 \u0627\u0644\u0623\u0641\u0644\u0627\u0645 \u0627\u0644\u0645\u062d\u0644\u064a\u0629 \u0648\u062a\u0646\u0632\u064a\u0644\u0627\u062a IPTV");
        p("PIcons Path", "\u0645\u0633\u0627\u0631 PIcons");
        p("Custom Menu Path", "\u0645\u0633\u0627\u0631 \u0627\u0644\u0642\u0627\u0626\u0645\u0629 \u0627\u0644\u0645\u062e\u0635\u0635\u0629");
        p("Main Working Path (Backup/Restore/Screenshot/etc.)", "\u0645\u0633\u0627\u0631 \u0627\u0644\u0639\u0645\u0644 \u0627\u0644\u0631\u0626\u064a\u0633\u064a (\u0627\u0644\u0646\u0633\u062e \u0627\u0644\u0627\u062d\u062a\u064a\u0627\u0637\u064a/\u0627\u0644\u0627\u0633\u062a\u0639\u0627\u062f\u0629/\u0644\u0642\u0637\u0627\u062a \u0627\u0644\u0634\u0627\u0634\u0629 \u0648\u063a\u064a\u0631\u0647\u0627)");
        p("Virtual Keyboard", "\u0644\u0648\u062d\u0629 \u0645\u0641\u0627\u062a\u064a\u062d \u0627\u0641\u062a\u0631\u0627\u0636\u064a\u0629");
        p("System Default", "\u0627\u0641\u062a\u0631\u0627\u0636\u064a \u0627\u0644\u0646\u0638\u0627\u0645");
        p("Directory Up", "\u0627\u0644\u0645\u062c\u0644\u062f \u0627\u0644\u0623\u0639\u0644\u0649");
        p("Exit", "\u062e\u0631\u0648\u062c");
        p("Disable", "\u062a\u0639\u0637\u064a\u0644");
        p("Long-OK", "OK \u0645\u0637\u0648\u0651\u0644");
        p("Long-Exit", "\u062e\u0631\u0648\u062c \u0645\u0637\u0648\u0651\u0644");
        p("Long-Red", "\u0623\u062d\u0645\u0631 \u0645\u0637\u0648\u0651\u0644");
        p("cp1256 (Arabic - Windows)", "cp1256 (\u0639\u0631\u0628\u064a - \u0648\u064a\u0646\u062f\u0648\u0632)");
        p("iso-8859-6 (Arabic)", "iso-8859-6 (\u0639\u0631\u0628\u064a)");
        p("cp1252 (Western - Windows)", "cp1252 (\u063a\u0631\u0628\u064a - \u0648\u064a\u0646\u062f\u0648\u0632)");
        p("cp1251 (Cyrillic)", "cp1251 (\u0633\u064a\u0631\u064a\u0644\u064a)");
        p("iso-8859-9 (Turkish)", "iso-8859-9 (\u062a\u0631\u0643\u064a)");
        p("Off", "\u0625\u064a\u0642\u0627\u0641");
        p("Refresh + Notify", "\u062a\u062d\u062f\u064a\u062b + \u0625\u0634\u0639\u0627\u0631");
        p("Silent Refresh", "\u062a\u062d\u062f\u064a\u062b \u0635\u0627\u0645\u062a");
        p("Disabled", "\u0645\u0639\u0637\u0651\u0644");
        p("All", "\u0627\u0644\u0643\u0644");
        p("IPTV Only", "IPTV \u0641\u0642\u0637");
        p("All (Except IPTV)", "\u0627\u0644\u0643\u0644 (\u0639\u062f\u0627 IPTV)");
        p("Auto Find", "\u0628\u062d\u062b \u062a\u0644\u0642\u0627\u0626\u064a");
        p("Custom Path", "\u0645\u0633\u0627\u0631 \u0645\u062e\u0635\u0635");
        p("ON", "\u062a\u0634\u063a\u064a\u0644");
        p("OFF", "\u0625\u064a\u0642\u0627\u0641");
        p("AJPanel: storage permission is required", "AJPanel: \u0635\u0644\u0627\u062d\u064a\u0629 \u0627\u0644\u062a\u062e\u0632\u064a\u0646 \u0645\u0637\u0644\u0648\u0628\u0629");
        p("File/directory options", "\u062e\u064a\u0627\u0631\u0627\u062a \u0627\u0644\u0645\u0644\u0641/\u0627\u0644\u0645\u062c\u0644\u062f");
        p("General options", "\u062e\u064a\u0627\u0631\u0627\u062a \u0639\u0627\u0645\u0629");
        p("Directory Bookmarks", "\u0625\u0634\u0627\u0631\u0627\u062a \u0627\u0644\u0645\u062c\u0644\u062f\u0627\u062a");
        p("Name (A-Z)", "\u0627\u0644\u0627\u0633\u0645 (\u0623-\u064a)");
        p("Name (Z-A)", "\u0627\u0644\u0627\u0633\u0645 (\u064a-\u0623)");
        p("Date (Old first)", "\u0627\u0644\u062a\u0627\u0631\u064a\u062e (\u0627\u0644\u0623\u0642\u062f\u0645 \u0623\u0648\u0644\u064b\u0627)");
        p("Date (New first)", "\u0627\u0644\u062a\u0627\u0631\u064a\u062e (\u0627\u0644\u0623\u062d\u062f\u062b \u0623\u0648\u0644\u064b\u0627)");
        p("Size (Small first)", "\u0627\u0644\u062d\u062c\u0645 (\u0627\u0644\u0623\u0635\u063a\u0631 \u0623\u0648\u0644\u064b\u0627)");
        p("Size (Large first)", "\u0627\u0644\u062d\u062c\u0645 (\u0627\u0644\u0623\u0643\u0628\u0631 \u0623\u0648\u0644\u064b\u0627)");
        p("..   (Parent directory)", "..   (\u0627\u0644\u0645\u062c\u0644\u062f \u0627\u0644\u0623\u0639\u0644\u0649)");
        p("Previous page", "\u0627\u0644\u0635\u0641\u062d\u0629 \u0627\u0644\u0633\u0627\u0628\u0642\u0629");
        p("Next page  (", "\u0627\u0644\u0635\u0641\u062d\u0629 \u0627\u0644\u062a\u0627\u0644\u064a\u0629  (");
        p("more)", "\u0623\u062e\u0631\u0649)");
        p("(empty directory)", "(\u0645\u062c\u0644\u062f \u0641\u0627\u0631\u063a)");
        p("(nothing found for \"", "(\u0644\u0627 \u062a\u0648\u062c\u062f \u0646\u062a\u0627\u0626\u062c \u0644\u0640 \"");
        p("... (file is larger: only the first", "... (\u0627\u0644\u0645\u0644\u0641 \u0623\u0643\u0628\u0631: \u064a\u0638\u0647\u0631 \u0623\u0648\u0644");
        p("are shown)", "\u0641\u0642\u0637)");
        p("Error:", "\u062e\u0637\u0623:");
        p("File options", "\u062e\u064a\u0627\u0631\u0627\u062a \u0627\u0644\u0645\u0644\u0641");
        p("Add as IPTV playlist", "\u0625\u0636\u0627\u0641\u0629 \u0643\u0642\u0627\u0626\u0645\u0629 IPTV");
        p("Loading...", "\u062c\u0627\u0631\u064d \u0627\u0644\u062a\u062d\u0645\u064a\u0644...");
        p("channels added", "\u0642\u0646\u0627\u0629 \u0623\u064f\u0636\u064a\u0641\u062a");
        p("View as text", "\u0639\u0631\u0636 \u0643\u0646\u0635");
        p("Add to IPTV (single stream)", "\u0625\u0636\u0627\u0641\u0629 \u0625\u0644\u0649 IPTV (\u0628\u062b \u0645\u0641\u0631\u062f)");
        p("Added to IPTV:", "\u0623\u064f\u0636\u064a\u0641 \u0625\u0644\u0649 IPTV:");
        p("Properties", "\u0627\u0644\u062e\u0635\u0627\u0626\u0635");
        p("Rename", "\u0625\u0639\u0627\u062f\u0629 \u062a\u0633\u0645\u064a\u0629");
        p("Copy", "\u0646\u0633\u062e");
        p("Copy:", "\u0646\u0633\u062e:");
        p("Move", "\u0646\u0642\u0644");
        p("Move:", "\u0646\u0642\u0644:");
        p("Delete", "\u062d\u0630\u0641");
        p("Name:", "\u0627\u0644\u0627\u0633\u0645:");
        p("Path:", "\u0627\u0644\u0645\u0633\u0627\u0631:");
        p("Type:", "\u0627\u0644\u0646\u0648\u0639:");
        p("Directory", "\u0645\u062c\u0644\u062f");
        p("File", "\u0645\u0644\u0641");
        p("Size:", "\u0627\u0644\u062d\u062c\u0645:");
        p("bytes)", "\u0628\u0627\u064a\u062a)");
        p("Items:", "\u0627\u0644\u0639\u0646\u0627\u0635\u0631:");
        p("Modified:", "\u0622\u062e\u0631 \u062a\u0639\u062f\u064a\u0644:");
        p("Permissions:", "\u0627\u0644\u0623\u0630\u0648\u0646\u0627\u062a:");
        p("Invalid name", "\u0627\u0633\u0645 \u063a\u064a\u0631 \u0635\u0627\u0644\u062d");
        p("Already exists", "\u0645\u0648\u062c\u0648\u062f \u0645\u0633\u0628\u0642\u064b\u0627");
        p("Renamed", "\u062a\u0645\u062a \u0625\u0639\u0627\u062f\u0629 \u0627\u0644\u062a\u0633\u0645\u064a\u0629");
        p("Rename failed", "\u0641\u0634\u0644\u062a \u0625\u0639\u0627\u062f\u0629 \u0627\u0644\u062a\u0633\u0645\u064a\u0629");
        p("Yes, delete", "\u0646\u0639\u0645\u060c \u0627\u062d\u0630\u0641");
        p("the directory and its content", "\u0627\u0644\u0645\u062c\u0644\u062f \u0648\u0645\u062d\u062a\u0648\u0627\u0647");
        p("the file", "\u0627\u0644\u0645\u0644\u0641");
        p("Deleted", "\u062a\u0645 \u0627\u0644\u062d\u0630\u0641");
        p("Delete failed", "\u0641\u0634\u0644 \u0627\u0644\u062d\u0630\u0641");
        p("Cancel", "\u0625\u0644\u063a\u0627\u0621");
        p("Paste here:", "\u0627\u0644\u0635\u0642 \u0647\u0646\u0627:");
        p("move", "\u0646\u0642\u0644");
        p("copy", "\u0646\u0633\u062e");
        p("New directory", "\u0645\u062c\u0644\u062f \u062c\u062f\u064a\u062f");
        p("Find (name contains)", "\u0628\u062d\u062b (\u0627\u0644\u0627\u0633\u0645 \u064a\u062d\u062a\u0648\u064a)");
        p("Clear find", "\u0645\u0633\u062d \u0627\u0644\u0628\u062d\u062b");
        p("Sort", "\u0627\u0644\u062a\u0631\u062a\u064a\u0628");
        p("Mix directories and files", "\u062e\u0644\u0637 \u0627\u0644\u0645\u062c\u0644\u062f\u0627\u062a \u0648\u0627\u0644\u0645\u0644\u0641\u0627\u062a");
        p("Set this directory as the start-up directory", "\u062a\u0639\u064a\u064a\u0646 \u0647\u0630\u0627 \u0627\u0644\u0645\u062c\u0644\u062f \u0643\u0645\u062c\u0644\u062f \u0627\u0644\u0628\u062f\u0621");
        p("Start-up directory saved", "\u062a\u0645 \u062d\u0641\u0638 \u0645\u062c\u0644\u062f \u0627\u0644\u0628\u062f\u0621");
        p("Already exists in this directory", "\u0645\u0648\u062c\u0648\u062f \u0645\u0633\u0628\u0642\u064b\u0627 \u0641\u064a \u0647\u0630\u0627 \u0627\u0644\u0645\u062c\u0644\u062f");
        p("Done", "\u062a\u0645");
        p("Failed", "\u0641\u0634\u0644");
        p("Created", "\u062a\u0645 \u0627\u0644\u0625\u0646\u0634\u0627\u0621");
        p("Find", "\u0628\u062d\u062b");
        p("Name contains", "\u0627\u0644\u0627\u0633\u0645 \u064a\u062d\u062a\u0648\u064a");
        p("Add current directory", "\u0625\u0636\u0627\u0641\u0629 \u0627\u0644\u0645\u062c\u0644\u062f \u0627\u0644\u062d\u0627\u0644\u064a");
        p("Remove current directory", "\u0625\u0632\u0627\u0644\u0629 \u0627\u0644\u0645\u062c\u0644\u062f \u0627\u0644\u062d\u0627\u0644\u064a");
        p("Skip Adults Channels", "\u062a\u062e\u0637\u064a \u0642\u0646\u0648\u0627\u062a \u0627\u0644\u0628\u0627\u0644\u063a\u064a\u0646");
        p("Remove IPTV Channel Name Prefix", "\u0625\u0632\u0627\u0644\u0629 \u0628\u0627\u062f\u0626\u0629 \u0627\u0633\u0645 \u0627\u0644\u0642\u0646\u0627\u0629");
        p("Portal Servers Connection Timeout", "\u0645\u0647\u0644\u0629 \u0627\u0644\u0627\u062a\u0635\u0627\u0644 \u0628\u062e\u0648\u0627\u062f\u0645 \u0627\u0644\u0628\u0648\u0627\u0628\u0629");
        p("Add playlist  (M3U link / file / single stream / combined)", "\u0625\u0636\u0627\u0641\u0629 \u0642\u0627\u0626\u0645\u0629  (\u0631\u0627\u0628\u0637 M3U / \u0645\u0644\u0641 / \u0628\u062b \u0645\u0641\u0631\u062f / \u0645\u062f\u0645\u062c\u0629)");
        p("Xtream Codes / Portal server", "Xtream Codes / \u062e\u0627\u062f\u0645 \u0628\u0648\u0627\u0628\u0629");
        p("(no playlists yet)", "(\u0644\u0627 \u062a\u0648\u062c\u062f \u0642\u0648\u0627\u0626\u0645 \u0628\u0639\u062f)");
        p("Playlists", "\u0627\u0644\u0642\u0648\u0627\u0626\u0645");
        p("Server URL  (http://host:port)", "\u0639\u0646\u0648\u0627\u0646 \u0627\u0644\u062e\u0627\u062f\u0645  (http://host:port)");
        p("The address must start with http:// or https://", "\u064a\u062c\u0628 \u0623\u0646 \u064a\u0628\u062f\u0623 \u0627\u0644\u0639\u0646\u0648\u0627\u0646 \u0628\u0640 http:// \u0623\u0648 https://");
        p("Username", "\u0627\u0633\u0645 \u0627\u0644\u0645\u0633\u062a\u062e\u062f\u0645");
        p("Password", "\u0643\u0644\u0645\u0629 \u0627\u0644\u0645\u0631\u0648\u0631");
        p("(no group)", "(\u0628\u062f\u0648\u0646 \u0645\u062c\u0645\u0648\u0639\u0629)");
        p("Search channels", "\u0628\u062d\u062b \u0641\u064a \u0627\u0644\u0642\u0646\u0648\u0627\u062a");
        p("Update playlist", "\u062a\u062d\u062f\u064a\u062b \u0627\u0644\u0642\u0627\u0626\u0645\u0629");
        p("Updating...", "\u062c\u0627\u0631\u064d \u0627\u0644\u062a\u062d\u062f\u064a\u062b...");
        p("channels", "\u0642\u0646\u0627\u0629");
        p("All channels   (", "\u0643\u0644 \u0627\u0644\u0642\u0646\u0648\u0627\u062a   (");
        p("Favourites   (", "\u0627\u0644\u0645\u0641\u0636\u0644\u0629   (");
        p("..   (Groups)", "..   (\u0627\u0644\u0645\u062c\u0645\u0648\u0639\u0627\u062a)");
        p("(no channels)", "(\u0644\u0627 \u062a\u0648\u062c\u062f \u0642\u0646\u0648\u0627\u062a)");
        p("works on the files and services of an Enigma2 receiver (bouquets, services lists, EPG database,", "\u064a\u0639\u0645\u0644 \u0639\u0644\u0649 \u0645\u0644\u0641\u0627\u062a \u0648\u062e\u062f\u0645\u0627\u062a \u062c\u0647\u0627\u0632 \u0627\u0633\u062a\u0642\u0628\u0627\u0644 Enigma2 (\u0627\u0644\u0628\u0627\u0642\u0627\u062a \u0648\u0642\u0648\u0627\u0626\u0645 \u0627\u0644\u062e\u062f\u0645\u0627\u062a \u0648\u0642\u0627\u0639\u062f\u0629 \u0628\u064a\u0627\u0646\u0627\u062a EPG\u060c");
        p("softcam keys, plugin packages, shell).\n\nThis device runs Android, so there is nothing local for it to act on.", "\u0648\u0645\u0641\u0627\u062a\u064a\u062d softcam \u0648\u062d\u0632\u0645 \u0627\u0644\u0625\u0636\u0627\u0641\u0627\u062a \u0648\u0633\u0637\u0631 \u0627\u0644\u0623\u0648\u0627\u0645\u0631).\n\n\u064a\u0639\u0645\u0644 \u0647\u0630\u0627 \u0627\u0644\u062c\u0647\u0627\u0632 \u0628\u0646\u0638\u0627\u0645 \u0623\u0646\u062f\u0631\u0648\u064a\u062f\u060c \u0641\u0644\u0627 \u064a\u0648\u062c\u062f \u0645\u0627 \u064a\u0645\u0643\u0646 \u0623\u0646 \u064a\u0639\u0645\u0644 \u0639\u0644\u064a\u0647 \u0645\u062d\u0644\u064a\u064b\u0627.");
        p("It is not available in this version.", "\u063a\u064a\u0631 \u0645\u062a\u0627\u062d \u0641\u064a \u0647\u0630\u0627 \u0627\u0644\u0625\u0635\u062f\u0627\u0631.");
        p("Model:", "\u0627\u0644\u0637\u0631\u0627\u0632:");
        p("RAM:", "\u0627\u0644\u0630\u0627\u0643\u0631\u0629 (RAM):");
        p("used /", "\u0645\u0633\u062a\u062e\u062f\u0645\u0629 /");
        p("Data storage:", "\u0645\u0633\u0627\u062d\u0629 \u0627\u0644\u0628\u064a\u0627\u0646\u0627\u062a:");
        p("free /", "\u0645\u062a\u0627\u062d\u0629 /");
        p("Shared storage:", "\u0627\u0644\u062a\u062e\u0632\u064a\u0646 \u0627\u0644\u0645\u0634\u062a\u0631\u0643:");
        p("Working path:", "\u0645\u0633\u0627\u0631 \u0627\u0644\u0639\u0645\u0644:");
        p("Movies path:", "\u0645\u0633\u0627\u0631 \u0627\u0644\u0623\u0641\u0644\u0627\u0645:");
        p("(Android port)", "(\u0646\u0633\u062e\u0629 \u0623\u0646\u062f\u0631\u0648\u064a\u062f)");
        p("Device Information", "\u0645\u0639\u0644\u0648\u0645\u0627\u062a \u0627\u0644\u062c\u0647\u0627\u0632");
        p("Date:", "\u0627\u0644\u062a\u0627\u0631\u064a\u062e:");
        p("Time:", "\u0627\u0644\u0648\u0642\u062a:");
        p("Time zone:", "\u0627\u0644\u0645\u0646\u0637\u0642\u0629 \u0627\u0644\u0632\u0645\u0646\u064a\u0629:");
        p("Auto time:", "\u0627\u0644\u0648\u0642\u062a \u0627\u0644\u062a\u0644\u0642\u0627\u0626\u064a:");
        p("ON (network time)", "\u0645\u0641\u0639\u0651\u0644 (\u0648\u0642\u062a \u0627\u0644\u0634\u0628\u0643\u0629)");
        p("Open Android Date & time settings", "\u0641\u062a\u062d \u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u062a\u0627\u0631\u064a\u062e \u0648\u0627\u0644\u0648\u0642\u062a \u0641\u064a \u0623\u0646\u062f\u0631\u0648\u064a\u062f");
        p("Connection:", "\u0627\u0644\u0627\u062a\u0635\u0627\u0644:");
        p("(connected)", "(\u0645\u062a\u0635\u0644)");
        p("not connected", "\u063a\u064a\u0631 \u0645\u062a\u0635\u0644");
        p("Interfaces:", "\u0627\u0644\u0648\u0627\u062c\u0647\u0627\u062a:");
        p("Open Android network settings", "\u0641\u062a\u062d \u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u0634\u0628\u0643\u0629 \u0641\u064a \u0623\u0646\u062f\u0631\u0648\u064a\u062f");
        p("Settings screen is not available on this device", "\u0634\u0627\u0634\u0629 \u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a \u063a\u064a\u0631 \u0645\u062a\u0627\u062d\u0629 \u0639\u0644\u0649 \u0647\u0630\u0627 \u0627\u0644\u062c\u0647\u0627\u0632");
        p("Backup AJPanel settings", "\u0646\u0633\u062e \u0625\u0639\u062f\u0627\u062f\u0627\u062a AJPanel \u0627\u062d\u062a\u064a\u0627\u0637\u064a\u064b\u0627");
        p("Cannot create", "\u062a\u0639\u0630\u0631 \u0625\u0646\u0634\u0627\u0621");
        p("Saved:", "\u062a\u0645 \u0627\u0644\u062d\u0641\u0638:");
        p("Backup failed:", "\u0641\u0634\u0644 \u0627\u0644\u0646\u0633\u062e \u0627\u0644\u0627\u062d\u062a\u064a\u0627\u0637\u064a:");
        p("(no backup files in", "(\u0644\u0627 \u062a\u0648\u062c\u062f \u0645\u0644\u0641\u0627\u062a \u0646\u0633\u062e \u0627\u062d\u062a\u064a\u0627\u0637\u064a \u0641\u064a");
        p("Restore  -", "\u0627\u0633\u062a\u0639\u0627\u062f\u0629  -");
        p("Restored", "\u062a\u0645\u062a \u0627\u0633\u062a\u0639\u0627\u062f\u0629");
        p("settings", "\u0625\u0639\u062f\u0627\u062f");
        p("Restore failed:", "\u0641\u0634\u0644\u062a \u0627\u0644\u0627\u0633\u062a\u0639\u0627\u062f\u0629:");
        p("Save", "\u062d\u0641\u0638");
        p("AJPanel: settings saved", "AJPanel: \u062a\u0645 \u062d\u0641\u0638 \u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a");
        p("Reset all to default", "\u0625\u0639\u0627\u062f\u0629 \u0627\u0644\u0643\u0644 \u0625\u0644\u0649 \u0627\u0644\u0627\u0641\u062a\u0631\u0627\u0636\u064a");
        p("AJPanel: defaults restored", "AJPanel: \u062a\u0645\u062a \u0627\u0633\u062a\u0639\u0627\u062f\u0629 \u0627\u0644\u0627\u0641\u062a\u0631\u0627\u0636\u064a");
        p("(Enigma2 only)", "(Enigma2 \u0641\u0642\u0637)");
        p("(Default)", "(\u0627\u0644\u0627\u0641\u062a\u0631\u0627\u0636\u064a)");
        p("Custom Path:", "\u0627\u0644\u0645\u0633\u0627\u0631 \u0627\u0644\u0645\u062e\u0635\u0635:");
        p("Change Path", "\u062a\u063a\u064a\u064a\u0631 \u0627\u0644\u0645\u0633\u0627\u0631");
        p("Path must start with /", "\u064a\u062c\u0628 \u0623\u0646 \u064a\u0628\u062f\u0623 \u0627\u0644\u0645\u0633\u0627\u0631 \u0628\u0640 /");
    }
}
