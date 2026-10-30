package com.liskovsoft.smartyoutubetv2.common.app.presenters.ajpanel;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "AJPanel Settings" screen. Same rows, same order, same titles as the plugin.
 * <p>
 * Like the plugin: the changes are kept until "Save" (green button of the plugin) is chosen;
 * leaving the screen with Back is "Cancel".
 */
public class AjPanelSettingsPresenter {
    private final Context mContext;
    private final Map<String, String> mPending = new LinkedHashMap<>();
    private boolean mSaved;
    private boolean mKeep; // the screen is closed only to edit a path: keep the pending changes

    private AjPanelSettingsPresenter(Context context) {
        mContext = context;
    }

    public static void show(Context context) {
        AjPanelI18n.init(context);
        new AjPanelSettingsPresenter(context).showScreen();
    }

    private String value(AjPanelConfig.Entry e) {
        String v = mPending.get(e.key);
        return v != null ? v : AjPanelConfig.get(mContext, e.key);
    }

    private void later(Runnable r) {
        new Handler(Looper.getMainLooper()).postDelayed(r, 150);
    }

    private void showScreen() {
        AppDialogPresenter dialog = AppDialogPresenter.instance(mContext);
        mSaved = false;
        mKeep = false;

        dialog.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\uD83D\uDCBE Save"), o -> {
            mSaved = true;
            AjPanelConfig.setAll(mContext, mPending);
            mPending.clear();
            dialog.closeDialog();
            MessageHelpers.showMessage(mContext, AjPanelI18n.tr("AJPanel: settings saved"));
        }));

        for (AjPanelConfig.Entry e : AjPanelConfig.ENTRIES) {
            if (e.isSeparator()) {
                continue;
            }
            switch (e.type) {
                case AjPanelConfig.YESNO:
                    dialog.appendRadioCategory(title(e), options(e, new String[][]{{"true", AjPanelI18n.tr("ON")}, {"false", AjPanelI18n.tr("OFF")}}));
                    break;
                case AjPanelConfig.SELECT:
                    dialog.appendRadioCategory(title(e), options(e, e.choices));
                    break;
                case AjPanelConfig.NUMBER:
                    String[][] nums = new String[e.max - e.min + 1][2];
                    for (int i = e.min; i <= e.max; i++) {
                        nums[i - e.min] = new String[]{String.valueOf(i), String.valueOf(i)};
                    }
                    dialog.appendRadioCategory(title(e), options(e, nums));
                    break;
                case AjPanelConfig.PATH:
                    appendPath(dialog, e);
                    break;
            }
        }

        dialog.appendSingleButton(UiOptionItem.from(AjPanelI18n.tr("\u267B\uFE0F Reset all to default"), o -> {
            mSaved = true;
            AjPanelConfig.resetAll(mContext);
            mPending.clear();
            dialog.closeDialog();
            MessageHelpers.showMessage(mContext, AjPanelI18n.tr("AJPanel: defaults restored"));
        }));

        dialog.showDialog(AjPanelConfig.NAME + AjPanelI18n.tr(" Settings"), () -> {
            if (!mSaved && !mKeep && !mPending.isEmpty()) {
                mPending.clear(); // Cancel
            }
        });
    }

    private String title(AjPanelConfig.Entry e) {
        return AjPanelI18n.tr(e.title) + (e.e2Only ? AjPanelI18n.tr("  (Enigma2 only)") : "");
    }

    private List<OptionItem> options(AjPanelConfig.Entry e, String[][] choices) {
        String current = value(e);
        List<OptionItem> items = new ArrayList<>();
        for (String[] c : choices) {
            final String val = c[0];
            String label = AjPanelI18n.tr(c[1]) + (val.equals(e.def) ? AjPanelI18n.tr("  (Default)") : "");
            items.add(UiOptionItem.from(label, o -> mPending.put(e.key, val), val.equals(current)));
        }
        return items;
    }

    private void appendPath(AppDialogPresenter dialog, AjPanelConfig.Entry e) {
        if ("iptvHostsMode".equals(e.key)) {
            // The plugin asks: Auto Find / Custom Path
            String cur = value(e);
            boolean auto = AjPanelConfig.AUTO_FIND.equals(cur) || cur.isEmpty();
            List<OptionItem> items = new ArrayList<>();
            items.add(UiOptionItem.from(AjPanelI18n.tr("Auto Find"), o -> mPending.put(e.key, AjPanelConfig.AUTO_FIND), auto));
            items.add(UiOptionItem.from(auto ? AjPanelI18n.tr("Custom Path") : AjPanelI18n.tr("Custom Path:  ") + cur, o -> {
                mKeep = true;
                dialog.closeDialog();
                later(() -> editPath(e));
            }, !auto));
            dialog.appendRadioCategory(title(e), items);
            return;
        }

        String shown = value(e).isEmpty() ? defaultOf(e) + AjPanelI18n.tr("  (Default)") : value(e);
        dialog.appendSingleButton(UiOptionItem.from(title(e) + ":  " + shown, o -> {
            mKeep = true;
            dialog.closeDialog();
            later(() -> editPath(e));
        }));
    }

    private String defaultOf(AjPanelConfig.Entry e) {
        switch (e.key) {
            case "MovieDownloadPath": return AjPanelConfig.moviesDir(mContext).getAbsolutePath();
            case "PIconsPath": return AjPanelConfig.piconsDir(mContext).getAbsolutePath();
            case "customMenuPath": return AjPanelConfig.customMenuDir(mContext).getAbsolutePath();
            case "backupPath": return AjPanelConfig.workingDir(mContext).getAbsolutePath();
            default: return e.def;
        }
    }

    private void editPath(AjPanelConfig.Entry e) {
        String cur = value(e);
        SimpleEditDialog.show(mContext, AjPanelI18n.tr(e.title), AjPanelI18n.tr("Change Path"), cur.isEmpty() || AjPanelConfig.AUTO_FIND.equals(cur) ? defaultOf(e) : cur, newValue -> {
            String v = newValue == null ? "" : newValue.trim();
            if (!v.isEmpty() && !v.startsWith("/")) {
                MessageHelpers.showMessage(mContext, AjPanelI18n.tr("Path must start with /"));
                return false;
            }
            mPending.put(e.key, v);
            return true;
        }, this::showScreen);
    }
}
