package com.hippo.ehviewer.ui.scene.gallery.list;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.subscription.LocalRefreshJobStore;
import com.hippo.ehviewer.subscription.LocalRefreshStatusFormatter;
import com.hippo.ehviewer.subscription.LocalUpdateService;

/** Consistent, explicit start confirmation dialogs for long update checks. */
final class LocalUpdateStartDialog {
    interface Starter {
        void start(String method);
    }

    private LocalUpdateStartDialog() {}

    static void showFollow(Context context, boolean recommendGlobal, long lastSuccess,
                           Starter starter) {
        View content = LayoutInflater.from(context).inflate(
                R.layout.dialog_local_follow_update, null, false);
        TextView history = content.findViewById(R.id.local_update_history);
        RadioGroup methods = content.findViewById(R.id.local_update_methods);
        RadioButton global = content.findViewById(R.id.local_update_method_global);
        RadioButton tags = content.findViewById(R.id.local_update_method_tags);

        history.setText(lastSuccess <= 0
                ? context.getString(R.string.local_update_no_full_success)
                : context.getString(R.string.local_update_last_full_success,
                LocalRefreshStatusFormatter.formatTime(
                        lastSuccess, System.currentTimeMillis())));
        setMethodText(context, global, recommendGlobal
                ? R.string.local_update_method_global_recommended
                : R.string.local_update_method_global);
        setMethodText(context, tags, recommendGlobal
                ? R.string.local_update_method_tags
                : R.string.local_update_method_tags_recommended);
        methods.check(recommendGlobal
                ? R.id.local_update_method_global : R.id.local_update_method_tags);

        new AlertDialog.Builder(context)
                .setTitle(R.string.local_follow_check_updates)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.local_update_start, (dialog, which) ->
                        starter.start(methods.getCheckedRadioButtonId()
                                == R.id.local_update_method_global
                                ? LocalUpdateService.METHOD_GLOBAL
                                : LocalUpdateService.METHOD_TAGS))
                .show();
    }

    private static void setMethodText(Context context, RadioButton button, int stringId) {
        String value = context.getString(stringId);
        SpannableString styled = new SpannableString(value);
        int separator = value.indexOf('\n');
        int titleEnd = separator < 0 ? value.length() : separator;
        styled.setSpan(new StyleSpan(Typeface.BOLD), 0, titleEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (separator >= 0 && separator + 1 < value.length()) {
            TypedArray colors = context.obtainStyledAttributes(
                    new int[]{android.R.attr.textColorSecondary});
            int secondary = colors.getColor(0, button.getCurrentTextColor());
            colors.recycle();
            styled.setSpan(new ForegroundColorSpan(secondary), separator + 1, value.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new RelativeSizeSpan(0.87f), separator + 1, value.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        button.setText(styled);
        button.setLineSpacing(4f * context.getResources().getDisplayMetrics().density, 1f);
    }

    static void showBookmarks(Context context, int count, boolean recommendGlobal,
                              long lastSuccess, Starter starter) {
        View content = LayoutInflater.from(context).inflate(
                R.layout.dialog_local_follow_update, null, false);
        TextView history = content.findViewById(R.id.local_update_history);
        RadioGroup methods = content.findViewById(R.id.local_update_methods);
        RadioButton global = content.findViewById(R.id.local_update_method_global);
        RadioButton bookmarks = content.findViewById(R.id.local_update_method_tags);

        String last = lastSuccess <= 0
                ? context.getString(R.string.local_update_no_full_success)
                : context.getString(R.string.local_update_last_full_success,
                LocalRefreshStatusFormatter.formatTime(
                        lastSuccess, System.currentTimeMillis()));
        history.setText(context.getString(R.string.bookmark_update_history, count, last));
        setMethodText(context, global, recommendGlobal
                ? R.string.bookmark_update_method_global_recommended
                : R.string.bookmark_update_method_global);
        setMethodText(context, bookmarks, recommendGlobal
                ? R.string.bookmark_update_method_each
                : R.string.bookmark_update_method_each_recommended);
        methods.check(recommendGlobal
                ? R.id.local_update_method_global : R.id.local_update_method_tags);

        new AlertDialog.Builder(context)
                .setTitle(R.string.bookmark_check_updates)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.local_update_start, (dialog, which) ->
                        starter.start(methods.getCheckedRadioButtonId()
                                == R.id.local_update_method_global
                                ? LocalUpdateService.METHOD_GLOBAL
                                : LocalUpdateService.METHOD_FIRST_PAGE))
                .show();
    }
}
