package com.liskovsoft.smartyoutubetv2.common.app.presenters.ajpanel;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.IptvActions;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.IptvStore;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AJPanel "IPTV" page on top of the IPTV engine that yt22smart already has ({@link IptvStore}):
 * one list of playlists (M3U link / file / single stream / Xtream Codes server), groups, channels, search and playback.
 * <p>
 * The plugin settings that apply to Android are honoured: "Skip Adults Channels", "Remove IPTV Channel Name Prefix",
 * "Portal Servers Connection Timeout". They only change what AJPanel shows; the stored playlists are never modified.
 */
public class AjPanelIptv {
    private static final int PAGE = 120;
    private static final Pattern ADULT = Pattern.compile("(?i)(\\bxxx\\b|\\badult|porn|\\+18|18\\+|\\bsex|\\berotic|\\bhentai)");
    private static final Pattern PREFIX = Pattern.compile("^\\s*\\|[^|]{1,12}\\|\\s*(?:[^|]{1,30}\\|\\s*)?");

    private final Context mContext;

    private AjPanelIptv(Context context) {
        mContext = context;
    }

    public static void open(Context context) {
        AjPanelI18n.init(context);
        new AjPanelIptv(context).showMain();
    }

    private void later(Runnable r) {
        new Handler(Looper.getMainLooper()).postDelayed(r, 150);
    }

    // ------------------------------------------------------------------ main page

    private void showMain() {
        AppDialogPresenter d = AppDialogPresenter.instance(mContext);

        d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u2795 Add playlist  (M3U link / file / single stream / combined)"), o -> {
            d.closeDialog();
            later(() -> IptvActions.showAddDialog(mContext));
        }));
        d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83C\uDF10 Xtream Codes / Portal server"), o -> {
            d.closeDialog();
            later(this::xtream);
        }));

        List<IptvStore.Playlist> playlists = IptvStore.getPlaylists(mContext);
        List<OptionItem> items = new ArrayList<>();
        for (IptvStore.Playlist p : playlists) {
            String tag = p.type == IptvStore.TYPE_MERGED ? "\uD83E\uDDE9 " : "\uD83D\uDCE1 ";
            items.add(UiOptionItem.from(tag + p.name + "   (" + p.channels.size() + ")", o -> {
                d.closeDialog();
                later(() -> showGroups(p.id));
            }));
        }
        if (items.isEmpty()) {
            items.add(UiOptionItem.from(AjPanelI18n.tr("(no playlists yet)"), o -> {}));
        }
        d.appendStringsCategory(AjPanelI18n.tr("Playlists"), items);
        d.showDialog("IPTV");
    }

    // ------------------------------------------------------------------ Xtream

    private void xtream() {
        SimpleEditDialog.show(mContext, AjPanelI18n.tr("Server URL  (http://host:port)"), "", server -> {
            final String host = server == null ? "" : server.trim().replaceAll("/+$", "");
            if (!host.startsWith("http")) {
                MessageHelpers.showMessage(mContext, AjPanelI18n.tr("The address must start with http:// or https://"));
                return false;
            }
            later(() -> SimpleEditDialog.show(mContext, AjPanelI18n.tr("Username"), "", user -> {
                if (user == null || user.trim().isEmpty()) {
                    return false;
                }
                later(() -> SimpleEditDialog.showPassword(mContext, AjPanelI18n.tr("Password"), "", pass -> {
                    if (pass == null || pass.isEmpty()) {
                        return false;
                    }
                    loadXtream(host, user.trim(), pass);
                    return true;
                }));
                return true;
            }));
            return true;
        });
    }

    private void loadXtream(String host, String user, String pass) {
        try {
            String url = host + "/get.php?username=" + URLEncoder.encode(user, "UTF-8") + "&password=" + URLEncoder.encode(pass, "UTF-8")
                    + "&type=m3u_plus&output=ts";
            MessageHelpers.showMessage(mContext, AjPanelI18n.tr("Loading..."));
            IptvStore.addFromUrl(mContext, url, "Xtream " + user, (count, error) -> {
                MessageHelpers.showMessage(mContext, error != null ? error : "IPTV: " + count + AjPanelI18n.tr(" channels added"));
                later(this::showMain);
            });
        } catch (Exception e) {
            MessageHelpers.showMessage(mContext, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ groups

    private List<IptvStore.Channel> visible(String playlistId) {
        boolean noAdult = AjPanelConfig.getBool(mContext, "hideIptvServerAdultWords");
        List<IptvStore.Channel> out = new ArrayList<>();
        List<IptvStore.Channel> all = IptvStore.getChannels(mContext, playlistId);
        if (all == null) {
            return out;
        }
        for (IptvStore.Channel c : all) {
            if (c.hidden) {
                continue;
            }
            if (noAdult && (ADULT.matcher(nz(c.name)).find() || ADULT.matcher(nz(c.group)).find())) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private String shownName(IptvStore.Channel c) {
        String n = nz(c.name);
        if (AjPanelConfig.getBool(mContext, "hideIptvServerChannPrefix")) {
            String stripped = PREFIX.matcher(n).replaceFirst("");
            if (!stripped.trim().isEmpty()) {
                n = stripped;
            }
        }
        return n.trim();
    }

    private void showGroups(String playlistId) {
        IptvStore.Playlist playlist = IptvStore.getPlaylist(mContext, playlistId);
        if (playlist == null) {
            showMain();
            return;
        }

        List<IptvStore.Channel> channels = visible(playlistId);
        Map<String, Integer> groups = new LinkedHashMap<>();
        int fav = 0;
        for (IptvStore.Channel c : channels) {
            String g = nz(c.group).isEmpty() ? AjPanelI18n.tr("(no group)") : c.group;
            Integer n = groups.get(g);
            groups.put(g, n == null ? 1 : n + 1);
            if (c.favorite) {
                fav++;
            }
        }

        AppDialogPresenter d = AppDialogPresenter.instance(mContext);
        d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDD0D Search channels"), o -> {
            d.closeDialog();
            later(() -> search(playlistId));
        }));
        d.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDD04 Update playlist"), o -> {
            MessageHelpers.showMessage(mContext, AjPanelI18n.tr("Updating..."));
            IptvStore.refresh(mContext, playlistId, (count, error) ->
                    MessageHelpers.showMessage(mContext, error != null ? error : "IPTV: " + count + AjPanelI18n.tr(" channels")));
        }));

        List<OptionItem> items = new ArrayList<>();
        items.add(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDCFA All channels   (") + channels.size() + ")", o -> openChannels(d, playlist, null, "", 0)));
        if (fav > 0) {
            items.add(UiOptionItem.from(AjPanelI18n.tr("\u2B50 Favourites   (") + fav + ")", o -> openChannels(d, playlist, "\u2605", "", 0)));
        }
        for (Map.Entry<String, Integer> g : groups.entrySet()) {
            final String name = g.getKey();
            items.add(UiOptionItem.from("\uD83D\uDCC2 " + name + "   (" + g.getValue() + ")", o -> openChannels(d, playlist, name, "", 0)));
        }
        d.appendStringsCategory(playlist.name, items);
        d.showDialog(playlist.name);
    }

    private void search(String playlistId) {
        IptvStore.Playlist playlist = IptvStore.getPlaylist(mContext, playlistId);
        SimpleEditDialog.show(mContext, AjPanelI18n.tr("Search channels"), "", q -> {
            if (q == null || q.trim().isEmpty() || playlist == null) {
                return false;
            }
            later(() -> showChannels(playlist, null, q.trim(), 0));
            return true;
        });
    }

    // ------------------------------------------------------------------ channels

    private void openChannels(AppDialogPresenter d, IptvStore.Playlist p, String group, String q, int page) {
        d.closeDialog();
        later(() -> showChannels(p, group, q, page));
    }

    private void showChannels(IptvStore.Playlist playlist, String group, String query, int page) {
        List<IptvStore.Channel> shown = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT);
        for (IptvStore.Channel c : visible(playlist.id)) {
            String g = nz(c.group).isEmpty() ? AjPanelI18n.tr("(no group)") : c.group;
            boolean inGroup = group == null || ("\u2605".equals(group) ? c.favorite : g.equals(group));
            if (inGroup && (q.isEmpty() || shownName(c).toLowerCase(Locale.ROOT).contains(q))) {
                shown.add(c);
            }
        }

        int from = Math.min(page * PAGE, shown.size());
        int to = Math.min(from + PAGE, shown.size());

        AppDialogPresenter d = AppDialogPresenter.instance(mContext);
        List<OptionItem> items = new ArrayList<>();
        items.add(UiOptionItem.from(AjPanelI18n.tr("\u2B06\uFE0F  ..   (Groups)"), o -> {
            d.closeDialog();
            later(() -> showGroups(playlist.id));
        }));
        if (page > 0) {
            items.add(UiOptionItem.from(AjPanelI18n.tr("\u25C0 Previous page"), o -> openChannels(d, playlist, group, query, page - 1)));
        }
        for (IptvStore.Channel c : shown.subList(from, to)) {
            items.add(UiOptionItem.from((c.favorite ? "\u2B50 " : "\u25B6 ") + shownName(c), o -> {
                d.closeDialog();
                PlaybackPresenter.instance(mContext).openVideo(IptvStore.toVideo(playlist, c));
            }));
        }
        if (to < shown.size()) {
            items.add(UiOptionItem.from(AjPanelI18n.tr("\u25B6 Next page  (") + (shown.size() - to) + AjPanelI18n.tr(" more)"), o -> openChannels(d, playlist, group, query, page + 1)));
        }
        if (shown.isEmpty()) {
            items.add(UiOptionItem.from(AjPanelI18n.tr("(no channels)"), o -> {}));
        }

        String title = (group == null ? playlist.name : group) + (query.isEmpty() ? "" : "  \u2022  \"" + query + "\"") + "   [" + shown.size() + "]";
        d.appendStringsCategory(title, items);
        d.showDialog("IPTV");
    }
}
