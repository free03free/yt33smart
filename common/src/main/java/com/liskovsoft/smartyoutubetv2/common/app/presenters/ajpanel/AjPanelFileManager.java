package com.liskovsoft.smartyoutubetv2.common.app.presenters.ajpanel;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.IptvStore;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AJPanel "File Manager" for Android storage.
 * <p>
 * Plugin commands that make sense on Android: browse, parent directory, view text file (with the "Subtitle Files Encoding
 * Priority" charset), properties, rename, copy, move, delete, new directory, find, sort (6 modes + mix), bookmarks,
 * start-up directory. Menu button = "File/directory options", Yellow = "General options", Blue = "Directory Bookmarks".
 */
public class AjPanelFileManager {
    private static final int PAGE = 150;
    private static final long MAX_TEXT_BYTES = 300 * 1024;
    private static final String[] SORT_NAMES = {"Name (A-Z)", "Name (Z-A)", "Date (Old first)", "Date (New first)", "Size (Small first)", "Size (Large first)"};
    private static final Set<String> TEXT_EXT = new HashSet<>(Arrays.asList(
            "txt", "log", "conf", "cfg", "ini", "xml", "json", "sh", "py", "srt", "sub", "ass", "ssa", "vtt", "nfo", "md", "csv", "list", "bouquet", "tv", "radio", "m3u", "m3u8", "html", "htm", "js", "css", "properties"));
    private static final Set<String> MEDIA_EXT = new HashSet<>(Arrays.asList(
            "mp4", "mkv", "avi", "ts", "m2ts", "mov", "flv", "wmv", "webm", "mpg", "mpeg", "3gp", "m4v", "mp3", "aac", "flac", "ogg", "wav", "m4a", "wma", "mts"));

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private final Context mContext;
    private File mDir;
    private int mPage;
    private String mFilter = "";
    private File mClipFile;
    private boolean mClipMove;
    private File mStartOverride;

    private AjPanelFileManager(Context context, File startDir) {
        mContext = context;
        mStartOverride = startDir;
    }

    public static void open(Context context) {
        openAt(context, null);
    }

    /** Opens the manager in the given directory (null: the start-up directory of the settings). */
    public static void openAt(Context context, File startDir) {
        AjPanelI18n.init(context);
        if (PermissionHelpers.hasStoragePermissions(context)) {
            new AjPanelFileManager(context, startDir).start();
        } else if (context instanceof MotherActivity) {
            MotherActivity activity = (MotherActivity) context;
            activity.addOnPermissions((requestCode, permissions, grantResults) -> {
                if (requestCode == PermissionHelpers.REQUEST_EXTERNAL_STORAGE
                        && grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    new AjPanelFileManager(context, startDir).start();
                }
            });
            PermissionHelpers.verifyStoragePermissions(activity);
        } else {
            MessageHelpers.showMessage(context, AjPanelI18n.tr("AJPanel: storage permission is required"));
        }
    }

    private void start() {
        String start = AjPanelConfig.get(mContext, AjPanelConfig.KEY_BROWSER_START);
        File dir = mStartOverride != null ? mStartOverride : start.isEmpty() ? AjPanelConfig.storageRoot() : new File(start);
        if (mStartOverride != null && !mStartOverride.exists()) {
            //noinspection ResultOfMethodCallIgnored
            mStartOverride.mkdirs();
        }
        if (!dir.isDirectory()) {
            dir = AjPanelConfig.storageRoot();
        }
        showDir(dir, 0, "");
    }

    private void later(Runnable r) {
        new Handler(Looper.getMainLooper()).postDelayed(r, 150);
    }

    // ------------------------------------------------------------------ listing

    private List<File> list(File dir) {
        File[] files = dir.listFiles();
        List<File> out = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (mFilter.isEmpty() || f.getName().toLowerCase(Locale.ROOT).contains(mFilter.toLowerCase(Locale.ROOT))) {
                    out.add(f);
                }
            }
        }

        final int mode = AjPanelConfig.getInt(mContext, AjPanelConfig.KEY_SORT_MODE);
        final boolean mix = AjPanelConfig.getBool(mContext, AjPanelConfig.KEY_SORT_MIX);
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (!mix && a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                int r;
                switch (mode) {
                    case 2: case 3: r = Long.compare(a.lastModified(), b.lastModified()); break;
                    case 4: case 5: r = Long.compare(a.length(), b.length()); break;
                    default: r = a.getName().compareToIgnoreCase(b.getName());
                }
                return (mode == 1 || mode == 3 || mode == 5) ? -r : r;
            }
        });
        return out;
    }

    private void showDir(File dir, int page, String filter) {
        mDir = dir;
        mPage = page;
        mFilter = filter;

        List<File> all = list(dir);
        int from = Math.min(page * PAGE, all.size());
        int to = Math.min(from + PAGE, all.size());

        AppDialogPresenter dialog = AppDialogPresenter.instance(mContext);

        // Menu / Yellow / Blue buttons of the plugin
        dialog.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u2699\uFE0F General options"), o -> later(() -> generalOptions(dialog))));
        dialog.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDD16 Directory Bookmarks"), o -> {
            dialog.closeDialog();
            later(this::bookmarks);
        }));

        List<OptionItem> items = new ArrayList<>();
        File parent = dir.getParentFile();
        if (parent != null) {
            items.add(UiOptionItem.from(AjPanelI18n.tr("\u2B06\uFE0F  ..   (Parent directory)"), o -> go(parent)));
        }
        if (page > 0) {
            items.add(UiOptionItem.from(AjPanelI18n.tr("\u25C0 Previous page"), o -> go(dir, page - 1)));
        }
        for (File f : all.subList(from, to)) {
            items.add(UiOptionItem.from(label(f), o -> {
                if (f.isDirectory()) {
                    go(f);
                } else {
                    AppDialogPresenter.instance(mContext).closeDialog();
                    later(() -> fileClicked(f));
                }
            }));
        }
        if (to < all.size()) {
            items.add(UiOptionItem.from(AjPanelI18n.tr("\u25B6 Next page  (") + (all.size() - to) + AjPanelI18n.tr(" more)"), o -> go(dir, page + 1)));
        }
        if (all.isEmpty()) {
            items.add(UiOptionItem.from(filter.isEmpty() ? AjPanelI18n.tr("(empty directory)") : AjPanelI18n.tr("(nothing found for \"") + filter + "\")", o -> {}));
        }

        dialog.appendStringsCategory(dir.getAbsolutePath() + "   [" + all.size() + "]", items);
        dialog.showDialog(AjPanelI18n.tr("File Manager"));
    }

    private void go(File dir) {
        go(dir, 0);
    }

    private void go(File dir, int page) {
        AppDialogPresenter.instance(mContext).closeDialog();
        later(() -> showDir(dir, page, page == 0 ? "" : mFilter));
    }

    private String label(File f) {
        if (f.isDirectory()) {
            return "\uD83D\uDCC1 " + f.getName();
        }
        return icon(f) + " " + f.getName() + "     " + size(f.length());
    }

    private String icon(File f) {
        String ext = ext(f);
        if (MEDIA_EXT.contains(ext)) return "\uD83C\uDFAC";
        if (TEXT_EXT.contains(ext)) return "\uD83D\uDCC4";
        return "\uD83D\uDCE6";
    }

    private static String ext(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return i >= 0 ? n.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }

    static String size(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", b / 1048576.0);
        return String.format(Locale.US, "%.2f GB", b / 1073741824.0);
    }

    // ------------------------------------------------------------------ file click

    private void fileClicked(File f) {
        String ext = ext(f);
        if (TEXT_EXT.contains(ext) && !ext.equals("m3u") && !ext.equals("m3u8")) {
            viewText(f);
        } else {
            fileOptions(f);
        }
    }

    private void viewText(File f) {
        IO.execute(() -> {
            String text;
            try {
                byte[] data = readHead(f, MAX_TEXT_BYTES);
                String enc = AjPanelConfig.get(mContext, "subtDefaultEnc");
                text = decode(data, enc);
                if (f.length() > MAX_TEXT_BYTES) {
                    text += AjPanelI18n.tr("\n\n... (file is larger: only the first ") + size(MAX_TEXT_BYTES) + AjPanelI18n.tr(" are shown)");
                }
            } catch (Exception e) {
                text = AjPanelI18n.tr("Error: ") + e.getMessage();
            }
            final String shown = text;
            new Handler(Looper.getMainLooper()).post(() -> {
                AppDialogPresenter d = AppDialogPresenter.instance(mContext);
                d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u2699\uFE0F File options"), o -> later(() -> fileOptions(f))));
                d.appendLongTextCategory(f.getName(), UiOptionItem.from(shown));
                d.showDialog(f.getName());
            });
        });
    }

    private static String decode(byte[] data, String preferred) {
        // Same idea as "Subtitle Files Encoding Priority": try the chosen encoding, fall back to utf8, then to latin-1
        String[] order = {preferred, "utf8", "cp1256", "iso-8859-1"};
        for (String enc : order) {
            try {
                java.nio.charset.CharsetDecoder dec = java.nio.charset.Charset.forName(enc).newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
                return dec.decode(java.nio.ByteBuffer.wrap(data)).toString();
            } catch (Exception ignored) {
            }
        }
        return new String(data, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private static byte[] readHead(File f, long max) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while (total < max && (n = in.read(buf, 0, (int) Math.min(buf.length, max - total))) > 0) {
                out.write(buf, 0, n);
                total += n;
            }
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------ file / directory options (Menu button)

    private void fileOptions(File f) {
        AppDialogPresenter d = AppDialogPresenter.instance(mContext);
        String ext = ext(f);

        if (!f.isDirectory() && (ext.equals("m3u") || ext.equals("m3u8"))) {
            d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDCE1 Add as IPTV playlist"), o -> {
                d.closeDialog();
                MessageHelpers.showMessage(mContext, AjPanelI18n.tr("Loading..."));
                IptvStore.addFromFile(mContext, f.getAbsolutePath(), f.getName(), (count, error) ->
                        MessageHelpers.showMessage(mContext, error != null ? error : "IPTV: " + count + AjPanelI18n.tr(" channels added")));
            }));
            d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDCC4 View as text"), o -> { d.closeDialog(); later(() -> viewText(f)); }));
        } else if (!f.isDirectory() && MEDIA_EXT.contains(ext)) {
            d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u25B6 Add to IPTV (single stream)"), o -> {
                d.closeDialog();
                IptvStore.addSingle(mContext, f.getName(), "file://" + f.getAbsolutePath());
                MessageHelpers.showMessage(mContext, AjPanelI18n.tr("Added to IPTV: ") + f.getName());
            }));
        } else if (!f.isDirectory() && !TEXT_EXT.contains(ext)) {
            d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDCC4 View as text"), o -> { d.closeDialog(); later(() -> viewText(f)); }));
        }

        d.appendSingleButton(UiOptionItem.from("\u2139\uFE0F Properties", o -> { d.closeDialog(); later(() -> properties(f)); }));
        d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u270F\uFE0F Rename"), o -> { d.closeDialog(); later(() -> rename(f)); }));
        d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDCCB Copy"), o -> { mClipFile = f; mClipMove = false; d.closeDialog(); MessageHelpers.showMessage(mContext, AjPanelI18n.tr("Copy: ") + f.getName()); later(() -> showDir(mDir, mPage, mFilter)); }));
        d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u2702\uFE0F Move"), o -> { mClipFile = f; mClipMove = true; d.closeDialog(); MessageHelpers.showMessage(mContext, AjPanelI18n.tr("Move: ") + f.getName()); later(() -> showDir(mDir, mPage, mFilter)); }));
        d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDDD1\uFE0F Delete"), o -> { d.closeDialog(); later(() -> confirmDelete(f)); }));
        d.showDialog(f.getName());
    }

    private void properties(File f) {
        StringBuilder sb = new StringBuilder();
        sb.append(AjPanelI18n.tr("Name:        ")).append(f.getName()).append('\n');
        sb.append(AjPanelI18n.tr("Path:        ")).append(f.getAbsolutePath()).append('\n');
        sb.append(AjPanelI18n.tr("Type:        ")).append(f.isDirectory() ? AjPanelI18n.tr("Directory") : AjPanelI18n.tr("File")).append('\n');
        if (!f.isDirectory()) {
            sb.append(AjPanelI18n.tr("Size:        ")).append(size(f.length())).append("  (").append(f.length()).append(AjPanelI18n.tr(" bytes)\n"));
        } else {
            String[] c = f.list();
            sb.append(AjPanelI18n.tr("Items:       ")).append(c == null ? "?" : String.valueOf(c.length)).append('\n');
        }
        sb.append(AjPanelI18n.tr("Modified:    ")).append(DateFormat.getDateTimeInstance().format(new Date(f.lastModified()))).append('\n');
        sb.append(AjPanelI18n.tr("Permissions: ")).append(f.canRead() ? "r" : "-").append(f.canWrite() ? "w" : "-").append(f.canExecute() ? "x" : "-").append('\n');
        AppDialogPresenter d = AppDialogPresenter.instance(mContext);
        d.appendLongTextCategory(AjPanelI18n.tr("Properties"), UiOptionItem.from(sb.toString()));
        d.showDialog(AjPanelI18n.tr("Properties"));
    }

    private void rename(File f) {
        SimpleEditDialog.show(mContext, AjPanelI18n.tr("Rename"), AjPanelI18n.tr("Rename"), f.getName(), v -> {
            String n = v == null ? "" : v.trim();
            if (n.isEmpty() || n.contains("/")) {
                MessageHelpers.showMessage(mContext, AjPanelI18n.tr("Invalid name"));
                return false;
            }
            File target = new File(f.getParentFile(), n);
            if (target.exists()) {
                MessageHelpers.showMessage(mContext, AjPanelI18n.tr("Already exists"));
                return false;
            }
            MessageHelpers.showMessage(mContext, f.renameTo(target) ? AjPanelI18n.tr("Renamed") : AjPanelI18n.tr("Rename failed"));
            return true;
        }, () -> later(() -> showDir(mDir, mPage, mFilter)));
    }

    private void confirmDelete(File f) {
        AppDialogPresenter d = AppDialogPresenter.instance(mContext);
        d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u2705 Yes, delete ") + (f.isDirectory() ? AjPanelI18n.tr("the directory and its content") : AjPanelI18n.tr("the file")), o -> {
            d.closeDialog();
            IO.execute(() -> {
                boolean ok = deleteRec(f);
                new Handler(Looper.getMainLooper()).post(() -> {
                    MessageHelpers.showMessage(mContext, ok ? AjPanelI18n.tr("Deleted") : AjPanelI18n.tr("Delete failed"));
                    showDir(mDir, mPage, mFilter);
                });
            });
        }));
        d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u274C Cancel"), o -> { d.closeDialog(); later(() -> showDir(mDir, mPage, mFilter)); }));
        d.showDialog(AjPanelI18n.tr("Delete  ") + f.getName() + " ?");
    }

    private static boolean deleteRec(File f) {
        if (f.isDirectory()) {
            File[] c = f.listFiles();
            if (c != null) {
                for (File x : c) {
                    if (!deleteRec(x)) return false;
                }
            }
        }
        return f.delete();
    }

    private static boolean copyRec(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) return false;
            File[] c = src.listFiles();
            if (c != null) {
                for (File x : c) {
                    if (!copyRec(x, new File(dst, x.getName()))) return false;
                }
            }
            return true;
        }
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return true;
    }

    // ------------------------------------------------------------------ general options (Yellow button)

    private void generalOptions(AppDialogPresenter parent) {
        parent.closeDialog();
        later(() -> {
            AppDialogPresenter d = AppDialogPresenter.instance(mContext);

            if (mClipFile != null) {
                d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDCCC Paste here:  ") + (mClipMove ? AjPanelI18n.tr("move ") : AjPanelI18n.tr("copy ")) + mClipFile.getName(), o -> { d.closeDialog(); paste(); }));
            }
            d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDCC1 New directory"), o -> { d.closeDialog(); later(this::newDir); }));
            d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDD0D Find (name contains)"), o -> { d.closeDialog(); later(this::find); }));
            if (!mFilter.isEmpty()) {
                d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u274C Clear find"), o -> { d.closeDialog(); later(() -> showDir(mDir, 0, "")); }));
            }

            int cur = AjPanelConfig.getInt(mContext, AjPanelConfig.KEY_SORT_MODE);
            List<OptionItem> sorts = new ArrayList<>();
            for (int i = 0; i < SORT_NAMES.length; i++) {
                final int m = i;
                sorts.add(UiOptionItem.from(AjPanelI18n.tr(SORT_NAMES[i]), o -> AjPanelConfig.set(mContext, AjPanelConfig.KEY_SORT_MODE, String.valueOf(m)), i == cur));
            }
            d.appendRadioCategory(AjPanelI18n.tr("Sort"), sorts);
            d.appendSingleSwitch(UiOptionItem.from(AjPanelI18n.tr("Mix directories and files"),
                    o -> AjPanelConfig.set(mContext, AjPanelConfig.KEY_SORT_MIX, String.valueOf(o.isSelected())),
                    AjPanelConfig.getBool(mContext, AjPanelConfig.KEY_SORT_MIX)));
            d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u2B50 Set this directory as the start-up directory"), o -> {
                AjPanelConfig.set(mContext, AjPanelConfig.KEY_BROWSER_START, mDir.getAbsolutePath());
                MessageHelpers.showMessage(mContext, AjPanelI18n.tr("Start-up directory saved"));
            }));
            d.showDialog(AjPanelI18n.tr("General options"), () -> later(() -> showDir(mDir, mPage, mFilter)));
        });
    }

    private void paste() {
        if (mClipFile == null) return;
        final File src = mClipFile;
        final File dst = new File(mDir, src.getName());
        if (dst.exists()) {
            MessageHelpers.showMessage(mContext, AjPanelI18n.tr("Already exists in this directory"));
            later(() -> showDir(mDir, mPage, mFilter));
            return;
        }
        final boolean move = mClipMove;
        IO.execute(() -> {
            boolean ok;
            try {
                ok = move && src.renameTo(dst);
                if (!ok) {
                    ok = copyRec(src, dst);
                    if (ok && move) ok = deleteRec(src);
                }
            } catch (Exception e) {
                ok = false;
            }
            final boolean result = ok;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (result && move) mClipFile = null;
                MessageHelpers.showMessage(mContext, result ? AjPanelI18n.tr("Done") : AjPanelI18n.tr("Failed"));
                showDir(mDir, mPage, mFilter);
            });
        });
    }

    private void newDir() {
        SimpleEditDialog.show(mContext, AjPanelI18n.tr("New directory"), AjPanelI18n.tr("New directory"), "", v -> {
            String n = v == null ? "" : v.trim();
            if (n.isEmpty() || n.contains("/")) {
                MessageHelpers.showMessage(mContext, AjPanelI18n.tr("Invalid name"));
                return false;
            }
            MessageHelpers.showMessage(mContext, new File(mDir, n).mkdirs() ? AjPanelI18n.tr("Created") : AjPanelI18n.tr("Failed"));
            return true;
        }, () -> later(() -> showDir(mDir, mPage, mFilter)));
    }

    private void find() {
        SimpleEditDialog.show(mContext, AjPanelI18n.tr("Find"), AjPanelI18n.tr("Name contains"), AjPanelConfig.get(mContext, AjPanelConfig.KEY_LAST_FIND), v -> {
            AjPanelConfig.set(mContext, AjPanelConfig.KEY_LAST_FIND, v == null ? "" : v);
            mFilter = v == null ? "" : v.trim();
            return true;
        }, () -> later(() -> showDir(mDir, 0, mFilter)));
    }

    // ------------------------------------------------------------------ bookmarks (Blue button)

    private void bookmarks() {
        AppDialogPresenter d = AppDialogPresenter.instance(mContext);
        List<String> marks = AjPanelConfig.bookmarks(mContext);

        List<OptionItem> items = new ArrayList<>();
        for (String m : marks) {
            items.add(UiOptionItem.from(m, o -> go(new File(m))));
        }
        d.appendStringsCategory(AjPanelI18n.tr("Directory Bookmarks"), items);

        String here = mDir.getAbsolutePath();
        if (!marks.contains(here)) {
            d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u2795 Add current directory"), o -> {
                List<String> n = new ArrayList<>(marks);
                n.add(here);
                AjPanelConfig.set(mContext, AjPanelConfig.KEY_BOOKMARKS, join(n));
                d.closeDialog();
                later(this::bookmarks);
            }));
        } else {
            d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u2796 Remove current directory"), o -> {
                List<String> n = new ArrayList<>(marks);
                n.remove(here);
                AjPanelConfig.set(mContext, AjPanelConfig.KEY_BOOKMARKS, join(n));
                d.closeDialog();
                later(this::bookmarks);
            }));
        }
        d.showDialog(AjPanelI18n.tr("Directory Bookmarks"));
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        return sb.toString();
    }
}
