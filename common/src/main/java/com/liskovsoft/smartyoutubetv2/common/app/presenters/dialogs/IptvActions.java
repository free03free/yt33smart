package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.IptvChecker;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.IptvStore;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import arte.programar.materialfile.MaterialFilePicker;
import arte.programar.materialfile.ui.FilePickerActivity;
import arte.programar.materialfile.utils.FileUtils;

/**
 * Everything the user does with IPTV in the playlist library: add playlists, open them, manage channels.
 */
public final class IptvActions {
    private static final String TAG = IptvActions.class.getSimpleName();
    private static final int REQ_PICK_PLAYLIST = 7761;
    private static WeakReference<Activity> sResultListenerOwner;

    private IptvActions() {
    }

    public static boolean isIptvChannel(Video item) {
        return item != null && IptvStore.isIptvId(item.videoId);
    }

    public static boolean isIptvPlaylistCard(Video item) {
        return item != null && item.reloadPageKey != null && item.reloadPageKey.startsWith(IptvStore.MARK_OPEN);
    }

    /**
     * @return true when the card belongs to IPTV and has been handled
     */
    public static boolean handleMarker(Context context, Video item) {
        String key = item != null ? item.reloadPageKey : null;

        if (!IptvStore.isMarker(key)) {
            return false;
        }

        if (key.startsWith(IptvStore.MARK_OPEN)) {
            IptvStore.setOpen(key.substring(IptvStore.MARK_OPEN.length()));
            refreshBrowse(context);
        } else if (key.equals(IptvStore.MARK_BACK)) {
            IptvStore.back();
            refreshBrowse(context);
        } else if (key.equals(IptvStore.MARK_ADD)) {
            showAddDialog(context);
        } else if (key.equals(IptvStore.MARK_REFRESH)) {
            updatePlaylist(context, IptvStore.getOpen());
        } else if (key.equals(IptvStore.MARK_SEARCH)) {
            SimpleEditDialog.show(context, s(context, R.string.iptv_search), s(context, R.string.iptv_search_hint), "", value -> {
                if (value == null || value.trim().isEmpty()) {
                    return false;
                }
                IptvStore.setSearch(value);
                refreshBrowse(context);
                return true;
            });
        } else if (key.equals(IptvStore.MARK_MENU)) {
            showPlaylistMenu(context, IptvStore.getOpen());
        }

        return true;
    }

    /**
     * Long press on a channel card or on a playlist card.
     *
     * @return true when the card belongs to IPTV and the menu is shown
     */
    public static boolean showMenu(Context context, Video item) {
        if (isIptvChannel(item)) {
            showChannelMenu(context, item);
            return true;
        } else if (isIptvPlaylistCard(item)) {
            showPlaylistMenu(context, item.reloadPageKey.substring(IptvStore.MARK_OPEN.length()));
            return true;
        } else if (IptvStore.isMarker(item != null ? item.reloadPageKey : null)) {
            return true; // other IPTV cards have no menu
        }

        return false;
    }

    // ------------------------------------------------------------------ add

    public static void showAddDialog(Context context) {
        AppDialogPresenter presenter = AppDialogPresenter.instance(context);

        presenter.appendSingleButton(UiOptionItem.from("\uD83D\uDD17 " + s(context, R.string.iptv_add_from_link), option -> {
            presenter.closeDialog();
            later(() -> askLink(context, false));
        }));
        presenter.appendSingleButton(UiOptionItem.from("\uD83D\uDCC1 " + s(context, R.string.iptv_add_from_file), option -> {
            presenter.closeDialog();
            later(() -> pickFile(context));
        }));
        presenter.appendSingleButton(UiOptionItem.from("\u25B6 " + s(context, R.string.iptv_add_single), option -> {
            presenter.closeDialog();
            later(() -> askLink(context, true));
        }));

        presenter.appendSingleButton(UiOptionItem.from("\uD83E\uDDE9 " + s(context, R.string.iptv_create_merged), option -> {
            presenter.closeDialog();
            later(() -> showMergedPicker(context, null));
        }));

        presenter.showDialog(s(context, R.string.iptv_add));
    }

    // ------------------------------------------------------------------ combined list

    /**
     * Choose the playlists that the combined list shows together.
     *
     * @param mergedId null: create a new combined list, otherwise change the sources of the list
     */
    private static void showMergedPicker(Context context, String mergedId) {
        List<IptvStore.Playlist> candidates = new ArrayList<>();
        for (IptvStore.Playlist playlist : IptvStore.getPlaylists(context)) {
            if (playlist.type != IptvStore.TYPE_MERGED) {
                candidates.add(playlist);
            }
        }

        if (candidates.size() < (mergedId == null ? 2 : 1)) {
            MessageHelpers.showMessage(context, s(context, R.string.iptv_merged_need_two));
            return;
        }

        IptvStore.Playlist editing = mergedId != null ? IptvStore.getPlaylist(context, mergedId) : null;
        final Set<String> selected = new HashSet<>();
        if (editing != null) {
            selected.addAll(editing.sources);
        }

        List<OptionItem> items = new ArrayList<>();
        for (IptvStore.Playlist playlist : candidates) {
            items.add(UiOptionItem.from(playlist.name + "  \u2022  " + playlist.channels.size(), option -> {
                if (option.isSelected()) {
                    selected.add(playlist.id);
                } else {
                    selected.remove(playlist.id);
                }
            }, selected.contains(playlist.id)));
        }

        AppDialogPresenter presenter = AppDialogPresenter.instance(context);
        presenter.appendCheckedCategory(s(context, R.string.iptv_merged_pick), items);
        presenter.showDialog(s(context, R.string.iptv_merged_pick), () -> {
            List<String> ordered = new ArrayList<>(); // in the order of the playlists
            for (IptvStore.Playlist playlist : candidates) {
                if (selected.contains(playlist.id)) {
                    ordered.add(playlist.id);
                }
            }

            if (ordered.isEmpty() || (mergedId == null && ordered.size() < 2)) {
                MessageHelpers.showMessage(context, s(context, R.string.iptv_merged_need_two));
                return;
            }

            if (mergedId != null) {
                IptvStore.setMergedSources(context, mergedId, ordered);
                refreshBrowse(context);
            } else {
                later(() -> askName(context, s(context, R.string.iptv_merged_name_default), name -> {
                    String id = IptvStore.createMerged(context, name, ordered);
                    IptvStore.setOpen(id);
                    refreshBrowse(context);
                }));
            }
        });
    }

    // ------------------------------------------------------------------ http headers

    private interface HeadersSave {
        void save(Map<String, String> headers);
    }

    private static final String[] COMMON_HEADER_NAMES = {"User-Agent", "Referer", "Origin", "Cookie"};
    private static final int[] COMMON_HEADER_LABELS = {R.string.iptv_header_ua, R.string.iptv_header_referer,
            R.string.iptv_header_origin, R.string.iptv_header_cookie};

    /**
     * Edit User-Agent, Referer, Origin, Cookie and other headers. Every change is saved at once.
     */
    private static void showHeadersEditor(Context context, String title, Map<String, String> current, HeadersSave saver) {
        final Map<String, String> headers = current != null ? new LinkedHashMap<>(current) : new LinkedHashMap<>();
        AppDialogPresenter presenter = AppDialogPresenter.instance(context);

        Set<String> common = new HashSet<>();
        for (int i = 0; i < COMMON_HEADER_NAMES.length; i++) {
            final String name = COMMON_HEADER_NAMES[i];
            final String label = s(context, COMMON_HEADER_LABELS[i]);
            common.add(name);
            presenter.appendSingleButton(UiOptionItem.from(label + ": " + shorten(context, headers.get(name)), option -> {
                presenter.closeDialog();
                later(() -> SimpleEditDialog.show(context, label, label, headers.get(name) != null ? headers.get(name) : "", value -> {
                    if (value == null || value.trim().isEmpty()) {
                        headers.remove(name);
                    } else {
                        headers.put(name, value.trim());
                    }
                    saver.save(headers);
                    later(() -> showHeadersEditor(context, title, headers, saver));
                    return true;
                }));
            }));
        }

        // Other headers in one line: Name: value || Name: value
        StringBuilder other = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!common.contains(entry.getKey())) {
                if (other.length() > 0) {
                    other.append(" || ");
                }
                other.append(entry.getKey()).append(": ").append(entry.getValue());
            }
        }
        final String otherText = other.toString();
        final String otherLabel = s(context, R.string.iptv_header_other);
        presenter.appendSingleButton(UiOptionItem.from(otherLabel + ": " + shorten(context, otherText), option -> {
            presenter.closeDialog();
            later(() -> SimpleEditDialog.show(context, otherLabel, s(context, R.string.iptv_header_other_hint), otherText, value -> {
                headers.keySet().retainAll(common); // only the common headers stay
                if (value != null) {
                    for (String part : value.split("\\|\\|")) {
                        int colon = part.indexOf(':');
                        if (colon > 0 && !part.substring(colon + 1).trim().isEmpty()) {
                            headers.put(part.substring(0, colon).trim(), part.substring(colon + 1).trim());
                        }
                    }
                }
                saver.save(headers);
                later(() -> showHeadersEditor(context, title, headers, saver));
                return true;
            }));
        }));

        presenter.appendSingleButton(UiOptionItem.from("\uD83E\uDDF9 " + s(context, R.string.iptv_headers_clear), option -> {
            headers.clear();
            saver.save(headers);
            presenter.closeDialog();
            MessageHelpers.showMessage(context, s(context, R.string.msg_done));
        }));

        presenter.showDialog(s(context, R.string.iptv_headers) + " - " + title);
    }

    private static String shorten(Context context, String value) {
        if (value == null || value.isEmpty()) {
            return s(context, R.string.iptv_header_not_set);
        }
        return value.length() > 40 ? value.substring(0, 40) + "..." : value;
    }

    // ------------------------------------------------------------------ check channels

    private static void startCheck(Context context, String playlistId) {
        if (IptvChecker.isRunning()) {
            MessageHelpers.showMessage(context, s(context, R.string.iptv_check_running));
            return;
        }

        MessageHelpers.showMessage(context, s(context, R.string.iptv_check_started));

        final int[] lastStep = {-1};

        IptvChecker.start(context, playlistId, new IptvChecker.Listener() {
            @Override
            public void onProgress(int done, int total) {
                int step = total > 0 ? done * 10 / total : 10; // a message every 10%
                if (step != lastStep[0] && done < total) {
                    lastStep[0] = step;
                    MessageHelpers.showMessage(context, String.format(s(context, R.string.iptv_check_progress), done, total));
                }
            }

            @Override
            public void onDone(IptvChecker.Result result) {
                try {
                    showCheckSummary(context, playlistId, result);
                } catch (Exception e) {
                    Log.e(TAG, "Can't show the check result: %s", e.getMessage());
                }
            }
        });
    }

    private static void showCheckSummary(Context context, String playlistId, IptvChecker.Result result) {
        String text = String.format(s(context, R.string.iptv_check_done), result.ok, result.dead, result.skipped)
                + (result.cancelled ? "  \u2022  " + s(context, R.string.iptv_check_stopped) : "");

        refreshBrowse(context);

        if (result.dead == 0) {
            MessageHelpers.showLongMessage(context, text);
            return;
        }

        AppDialogPresenter presenter = AppDialogPresenter.instance(context);
        presenter.appendSingleButton(UiOptionItem.from("\uD83D\uDDD1 " + String.format(s(context, R.string.iptv_delete_dead), result.dead), option -> {
            presenter.closeDialog();
            later(() -> confirmDeleteDead(context, playlistId));
        }));
        presenter.appendSingleButton(UiOptionItem.from("\uD83D\uDE48 " + String.format(s(context, R.string.iptv_hide_dead), result.dead), option -> {
            int hidden = IptvStore.hideByStatus(context, playlistId, IptvStore.STATUS_DEAD);
            presenter.closeDialog();
            MessageHelpers.showMessage(context, String.format(s(context, R.string.iptv_dead_hidden), hidden));
            refreshBrowse(context);
        }));
        presenter.appendSingleButton(UiOptionItem.from("\uD83D\uDD34 " + s(context, R.string.iptv_review_dead), option -> presenter.closeDialog()));
        presenter.showDialog(text);
    }

    private static void confirmDeleteDead(Context context, String playlistId) {
        int dead = IptvStore.countByStatus(context, playlistId, IptvStore.STATUS_DEAD);

        if (dead == 0) {
            return;
        }

        AppDialogUtil.showConfirmationDialog(context, String.format(s(context, R.string.iptv_delete_dead_confirm), dead), () -> {
            int deleted = IptvStore.deleteByStatus(context, playlistId, IptvStore.STATUS_DEAD);
            AppDialogPresenter.instance(context).closeDialog();
            MessageHelpers.showMessage(context, String.format(s(context, R.string.iptv_dead_deleted), deleted));
            refreshBrowse(context);
        });
    }

    private static void askLink(Context context, boolean single) {
        SimpleEditDialog.show(context,
                s(context, single ? R.string.iptv_add_single : R.string.iptv_add_from_link),
                s(context, single ? R.string.iptv_enter_stream_link : R.string.iptv_enter_link), "", value -> {
                    String url = value != null ? value.trim() : "";
                    if (!url.contains("://")) {
                        MessageHelpers.showMessage(context, s(context, R.string.iptv_bad_link));
                        return false;
                    }

                    later(() -> askName(context, defaultName(url), name -> {
                        if (single) {
                            IptvStore.addSingle(context, name, url);
                            MessageHelpers.showMessage(context, s(context, R.string.msg_done));
                            refreshBrowse(context);
                        } else {
                            MessageHelpers.showMessage(context, s(context, R.string.iptv_loading));
                            IptvStore.addFromUrl(context, url, name, (count, error) -> onAdded(context, count, error));
                        }
                    }));
                    return true;
                });
    }

    private interface NameCallback {
        void onName(String name);
    }

    private static void askName(Context context, String defaultName, NameCallback callback) {
        SimpleEditDialog.show(context, s(context, R.string.iptv_enter_name), s(context, R.string.iptv_enter_name), defaultName, value -> {
            callback.onName(value != null && !value.trim().isEmpty() ? value.trim() : defaultName);
            return true;
        });
    }

    private static void onAdded(Context context, Integer count, String error) {
        if (error != null) {
            showError(context, error);
            return;
        }

        MessageHelpers.showMessage(context, String.format(s(context, R.string.iptv_added), count));
        refreshBrowse(context);
    }

    private static void pickFile(Context context) {
        if (!(context instanceof MotherActivity)) {
            MessageHelpers.showMessage(context, s(context, R.string.iptv_file_error));
            return;
        }

        MotherActivity activity = (MotherActivity) context;
        registerResultListener(activity);

        if (PermissionHelpers.hasStoragePermissions(activity)) {
            runFilePicker(activity);
        } else {
            activity.addOnPermissions((requestCode, permissions, grantResults) -> {
                if (requestCode == PermissionHelpers.REQUEST_EXTERNAL_STORAGE
                        && grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    runFilePicker(activity);
                }
            });
            PermissionHelpers.verifyStoragePermissions(activity);
        }
    }

    private static void registerResultListener(MotherActivity activity) {
        // The listener lives as long as the activity. Register it once per activity
        Activity owner = sResultListenerOwner != null ? sResultListenerOwner.get() : null;
        if (owner == activity) {
            return;
        }
        sResultListenerOwner = new WeakReference<>(activity);

        activity.addOnResult((requestCode, resultCode, data) -> {
            if (requestCode != REQ_PICK_PLAYLIST || resultCode != Activity.RESULT_OK || data == null) {
                return;
            }

            String path = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
            if (path == null) {
                return;
            }

            later(() -> askName(activity, new File(path).getName(), name -> {
                MessageHelpers.showMessage(activity, s(activity, R.string.iptv_loading));
                IptvStore.addFromFile(activity, path, name, (count, error) -> onAdded(activity, count, error));
            }));
        });
    }

    private static void runFilePicker(Activity activity) {
        File root = FileUtils.getFile(activity, null);

        if (root == null) {
            Log.e(TAG, "Storage device is unavailable");
            MessageHelpers.showMessage(activity, s(activity, R.string.iptv_file_error));
            return;
        }

        new MaterialFilePicker()
                .withActivity(activity)
                .withTitle(s(activity, R.string.iptv_pick_file))
                .withRootPath(root.getAbsolutePath())
                .start(REQ_PICK_PLAYLIST);
    }

    // ------------------------------------------------------------------ playlist menu

    private static void updatePlaylist(Context context, String playlistId) {
        if (playlistId == null) {
            return;
        }

        MessageHelpers.showMessage(context, s(context, R.string.iptv_loading));
        IptvStore.refresh(context, playlistId, (count, error) -> {
            if (error != null) {
                showError(context, error);
                return;
            }
            MessageHelpers.showMessage(context, String.format(s(context, R.string.iptv_updated), count));
            refreshBrowse(context);
        });
    }

    private static void showPlaylistMenu(Context context, String playlistId) {
        IptvStore.Playlist playlist = playlistId != null ? IptvStore.getPlaylist(context, playlistId) : null;

        if (playlist == null) {
            return;
        }

        AppDialogPresenter presenter = AppDialogPresenter.instance(context);
        boolean merged = playlist.type == IptvStore.TYPE_MERGED;

        if (playlist.type != IptvStore.TYPE_SINGLE) {
            presenter.appendSingleButton(UiOptionItem.from("\uD83D\uDD04 " + s(context, merged ? R.string.iptv_refresh_all : R.string.iptv_refresh), option -> {
                presenter.closeDialog();
                updatePlaylist(context, playlistId);
            }));
        }

        if (merged) {
            presenter.appendSingleButton(UiOptionItem.from("\uD83E\uDDE9 " + s(context, R.string.iptv_merged_edit_sources), option -> {
                presenter.closeDialog();
                later(() -> showMergedPicker(context, playlistId));
            }));
        } else {
            presenter.appendSingleButton(UiOptionItem.from("\uD83E\uDDE9 " + s(context, R.string.iptv_headers), option -> {
                presenter.closeDialog();
                later(() -> showHeadersEditor(context, playlist.name, playlist.headers,
                        headers -> IptvStore.setPlaylistHeaders(context, playlistId, headers)));
            }));
        }

        // Check which channels work
        if (IptvChecker.isRunning()) {
            presenter.appendSingleButton(UiOptionItem.from("\u23F9 " + s(context, R.string.iptv_check_stop), option -> {
                IptvChecker.cancel();
                presenter.closeDialog();
            }));
        } else {
            presenter.appendSingleButton(UiOptionItem.from("\uD83D\uDD0D " + s(context, R.string.iptv_check), option -> {
                presenter.closeDialog();
                startCheck(context, playlistId);
            }));
        }

        int dead = IptvStore.countByStatus(context, playlistId, IptvStore.STATUS_DEAD);
        if (dead > 0) {
            presenter.appendSingleButton(UiOptionItem.from("\uD83D\uDDD1 " + String.format(s(context, R.string.iptv_delete_dead), dead), option -> {
                presenter.closeDialog();
                later(() -> confirmDeleteDead(context, playlistId));
            }));
            presenter.appendSingleButton(UiOptionItem.from("\uD83D\uDE48 " + String.format(s(context, R.string.iptv_hide_dead), dead), option -> {
                int hidden = IptvStore.hideByStatus(context, playlistId, IptvStore.STATUS_DEAD);
                presenter.closeDialog();
                MessageHelpers.showMessage(context, String.format(s(context, R.string.iptv_dead_hidden), hidden));
                refreshBrowse(context);
            }));
        }
        if (dead > 0 || IptvStore.countByStatus(context, playlistId, IptvStore.STATUS_OK) > 0) {
            presenter.appendSingleButton(UiOptionItem.from("\u21BA " + s(context, R.string.iptv_clear_marks), option -> {
                IptvStore.clearStatuses(context, playlistId);
                presenter.closeDialog();
                refreshBrowse(context);
            }));
        }
        presenter.appendSingleButton(UiOptionItem.from("\u270F " + s(context, R.string.iptv_rename), option -> {
            presenter.closeDialog();
            later(() -> SimpleEditDialog.show(context, s(context, R.string.iptv_rename), s(context, R.string.iptv_enter_name), playlist.name, value -> {
                IptvStore.renamePlaylist(context, playlistId, value);
                refreshBrowse(context);
                return true;
            }));
        }));
        presenter.appendSingleButton(UiOptionItem.from("\u2B05 " + s(context, R.string.iptv_move_earlier), option -> {
            IptvStore.movePlaylist(context, playlistId, -1);
            presenter.closeDialog();
            refreshBrowse(context);
        }));
        presenter.appendSingleButton(UiOptionItem.from("\u27A1 " + s(context, R.string.iptv_move_later), option -> {
            IptvStore.movePlaylist(context, playlistId, 1);
            presenter.closeDialog();
            refreshBrowse(context);
        }));
        presenter.appendSingleButton(UiOptionItem.from("\uD83D\uDDD1 " + s(context, R.string.iptv_delete), option -> {
            presenter.closeDialog();
            later(() -> AppDialogUtil.showConfirmationDialog(context,
                    String.format(s(context, R.string.iptv_delete_confirm), playlist.name), () -> {
                        IptvStore.deletePlaylist(context, playlistId);
                        AppDialogPresenter.instance(context).closeDialog();
                        refreshBrowse(context);
                    }));
        }));

        presenter.showDialog(playlist.name);
    }

    // ------------------------------------------------------------------ channel menu

    private static void showChannelMenu(Context context, Video item) {
        IptvStore.Channel channel = IptvStore.findChannel(context, item.videoId);

        if (channel == null) {
            return;
        }

        final String id = channel.id;
        AppDialogPresenter presenter = AppDialogPresenter.instance(context);

        presenter.appendSingleButton(UiOptionItem.from(
                (channel.favorite ? "\u2606 " + s(context, R.string.iptv_favorite_remove) : "\u2B50 " + s(context, R.string.iptv_favorite_add)), option -> {
                    IptvStore.setFavorite(context, id, !channel.favorite);
                    presenter.closeDialog();
                    refreshBrowse(context);
                }));
        presenter.appendSingleButton(UiOptionItem.from("\u270F " + s(context, R.string.iptv_rename), option -> {
            presenter.closeDialog();
            later(() -> SimpleEditDialog.show(context, s(context, R.string.iptv_rename), s(context, R.string.iptv_enter_name), channel.name, value -> {
                IptvStore.renameChannel(context, id, value);
                refreshBrowse(context);
                return true;
            }));
        }));
        presenter.appendSingleButton(UiOptionItem.from("\uD83D\uDD17 " + s(context, R.string.iptv_edit_link), option -> {
            presenter.closeDialog();
            later(() -> SimpleEditDialog.show(context, s(context, R.string.iptv_edit_link), s(context, R.string.iptv_enter_stream_link), channel.url, value -> {
                if (value == null || !value.contains("://")) {
                    MessageHelpers.showMessage(context, s(context, R.string.iptv_bad_link));
                    return false;
                }
                IptvStore.setChannelUrl(context, id, value);
                MessageHelpers.showMessage(context, s(context, R.string.msg_done));
                return true;
            }));
        }));
        presenter.appendSingleButton(UiOptionItem.from("\uD83E\uDDE9 " + s(context, R.string.iptv_headers), option -> {
            presenter.closeDialog();
            later(() -> showHeadersEditor(context, channel.name, channel.headers,
                    headers -> IptvStore.setChannelHeaders(context, id, headers)));
        }));
        presenter.appendSingleButton(UiOptionItem.from("\u2B05 " + s(context, R.string.iptv_move_earlier), option -> {
            IptvStore.moveChannel(context, id, -1);
            presenter.closeDialog();
            refreshBrowse(context);
        }));
        presenter.appendSingleButton(UiOptionItem.from("\u27A1 " + s(context, R.string.iptv_move_later), option -> {
            IptvStore.moveChannel(context, id, 1);
            presenter.closeDialog();
            refreshBrowse(context);
        }));
        presenter.appendSingleButton(UiOptionItem.from("\u23EE " + s(context, R.string.iptv_move_first), option -> {
            IptvStore.moveChannelToStart(context, id);
            presenter.closeDialog();
            refreshBrowse(context);
        }));
        presenter.appendSingleButton(UiOptionItem.from(
                channel.hidden ? "\uD83D\uDC41 " + s(context, R.string.iptv_unhide) : "\uD83D\uDE48 " + s(context, R.string.iptv_hide), option -> {
                    IptvStore.setHidden(context, id, !channel.hidden);
                    presenter.closeDialog();
                    refreshBrowse(context);
                }));
        presenter.appendSingleButton(UiOptionItem.from("\uD83D\uDDD1 " + s(context, R.string.iptv_delete), option -> {
            presenter.closeDialog();
            later(() -> AppDialogUtil.showConfirmationDialog(context,
                    String.format(s(context, R.string.iptv_delete_confirm), channel.name), () -> {
                        IptvStore.deleteChannel(context, id);
                        AppDialogPresenter.instance(context).closeDialog();
                        refreshBrowse(context);
                    }));
        }));
        presenter.appendSingleButton(UiOptionItem.from("\uD83D\uDDD1 " + s(context, R.string.iptv_delete_group), option -> {
            presenter.closeDialog();
            later(() -> AppDialogUtil.showConfirmationDialog(context,
                    String.format(s(context, R.string.iptv_delete_confirm), groupLabel(context, channel.group)), () -> {
                        IptvStore.deleteGroupOf(context, id);
                        AppDialogPresenter.instance(context).closeDialog();
                        refreshBrowse(context);
                    }));
        }));

        presenter.showDialog(channel.name);
    }

    // ------------------------------------------------------------------ helpers

    public static String groupLabel(Context context, String group) {
        return group == null || group.trim().isEmpty() ? s(context, R.string.iptv_ungrouped) : group;
    }

    private static String defaultName(String url) {
        try {
            String host = Uri.parse(url).getHost();
            if (host != null && !host.isEmpty()) {
                return host;
            }
        } catch (Exception e) {
            // use the default
        }
        return "IPTV";
    }

    private static void showError(Context context, String error) {
        String text;
        if ("no_channels".equals(error)) {
            text = s(context, R.string.iptv_no_channels);
        } else if ("not_found".equals(error)) {
            text = s(context, R.string.iptv_channel_not_found);
        } else {
            text = String.format(s(context, R.string.iptv_error), error);
        }
        MessageHelpers.showLongMessage(context, text);
    }

    private static void refreshBrowse(Context context) {
        BrowsePresenter.instance(context).refresh(false);
    }

    /**
     * A dialog can't be shown right in the middle of closing of another one.
     */
    private static void later(Runnable runnable) {
        Utils.postDelayed(runnable, 200);
    }

    private static String s(Context context, int id) {
        return context.getString(id);
    }
}
