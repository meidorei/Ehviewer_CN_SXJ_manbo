package com.hippo.ehviewer.subscription;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

import androidx.core.content.ContextCompat;

import com.hippo.ehviewer.R;

/** Keeps "never checked" distinct from a successfully established zero baseline. */
public final class UpdateBadgeFormatter {
    private static final String SEPARATOR = "·";
    private static final String NEW_CONTENT_INDICATOR = "●";

    private UpdateBadgeFormatter() {}

    public static String format(String name, String badge) {
        String safeName = name == null ? "" : name;
        if (badge == null) return safeName;
        String separator = hasNewContent(badge) ? NEW_CONTENT_INDICATOR : SEPARATOR;
        return badge + "  " + separator + "  " + safeName;
    }

    public static CharSequence format(Context context, String name, String badge) {
        String formatted = format(name, badge);
        if (!hasNewContent(badge)) return formatted;

        int indicatorStart = formatted.indexOf(NEW_CONTENT_INDICATOR);
        SpannableString result = new SpannableString(formatted);
        result.setSpan(new ForegroundColorSpan(
                        ContextCompat.getColor(context, R.color.deep_green_600)),
                indicatorStart, indicatorStart + NEW_CONTENT_INDICATOR.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return result;
    }

    static boolean hasNewContent(String badge) {
        if ("20+".equals(badge)) return true;
        try {
            return Integer.parseInt(badge) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
