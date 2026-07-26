package com.hippo.ehviewer.subscription;

import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.ehviewer.dao.QuickSearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Conservative local evaluator for bookmark queries during a shared global Chinese scan.
 * Any expression that cannot be reproduced exactly from GalleryInfo is rejected for fallback.
 */
public final class BookmarkGlobalMatcher {
    public static Result compile(QuickSearch search) {
        if (search == null) return Result.fallback("invalid bookmark");
        List<String> positiveTags = new ArrayList<>();
        List<String> negativeTags = new ArrayList<>();
        List<String> uploaders = new ArrayList<>();

        if (search.mode == ListUrlBuilder.MODE_TAG) {
            String tag = normalizeTag(search.keyword);
            if (tag == null) return Result.fallback("tag query is not exact");
            positiveTags.add(tag);
        } else if (search.mode == ListUrlBuilder.MODE_UPLOADER) {
            String uploader = cleanValue(search.keyword);
            if (uploader.isEmpty()) return Result.fallback("empty uploader");
            uploaders.add(uploader);
        } else if (search.mode == ListUrlBuilder.MODE_NORMAL
                || search.mode == ListUrlBuilder.MODE_FILTER) {
            for (String token : tokenize(search.keyword)) {
                boolean negative = token.startsWith("-");
                if (negative) token = token.substring(1);
                if (token.isEmpty() || token.startsWith("~")
                        || "or".equalsIgnoreCase(token)) {
                    return Result.fallback("complex search operator");
                }
                int colon = token.indexOf(':');
                if (colon <= 0 || colon == token.length() - 1) {
                    return Result.fallback("full-text keyword");
                }
                String namespace = token.substring(0, colon).toLowerCase(Locale.ROOT);
                String value = cleanValue(token.substring(colon + 1));
                if (value.isEmpty() || value.indexOf('*') >= 0 || value.indexOf('?') >= 0
                        || value.indexOf('~') >= 0) {
                    return Result.fallback("fuzzy search expression");
                }
                if ("uploader".equals(namespace)) {
                    if (negative) return Result.fallback("negative uploader");
                    uploaders.add(value);
                    continue;
                }
                if ("l".equals(namespace)) namespace = "language";
                String tag = normalizeTag(namespace + ':' + value);
                if (tag == null) return Result.fallback("unsupported search field");
                (negative ? negativeTags : positiveTags).add(tag);
            }
        } else {
            return Result.fallback("unsupported mode");
        }

        return Result.exact(new BookmarkGlobalMatcher(
                search.category, search.minRating, search.pageFrom, search.pageTo,
                positiveTags, negativeTags, uploaders));
    }

    private final int categories;
    private final int minRating;
    private final int pageFrom;
    private final int pageTo;
    private final List<String> positiveTags;
    private final List<String> negativeTags;
    private final List<String> uploaders;

    private BookmarkGlobalMatcher(int categories, int minRating, int pageFrom, int pageTo,
                                  List<String> positiveTags, List<String> negativeTags,
                                  List<String> uploaders) {
        this.categories = categories;
        this.minRating = minRating;
        this.pageFrom = pageFrom;
        this.pageTo = pageTo;
        this.positiveTags = Collections.unmodifiableList(new ArrayList<>(positiveTags));
        this.negativeTags = Collections.unmodifiableList(new ArrayList<>(negativeTags));
        this.uploaders = Collections.unmodifiableList(new ArrayList<>(uploaders));
    }

    public boolean matches(GalleryInfo gallery) {
        if (gallery == null) return false;
        if (categories != EhUtils.NONE
                && (categories == 0 || (gallery.category & categories) == 0)) {
            return false;
        }
        if (minRating >= 0 && gallery.rating < minRating) return false;
        if (pageFrom >= 0 && gallery.pages < pageFrom) return false;
        if (pageTo >= 0 && gallery.pages > pageTo) return false;
        for (String uploader : uploaders) {
            if (gallery.uploader == null || !uploader.equalsIgnoreCase(gallery.uploader.trim())) {
                return false;
            }
        }
        if (!positiveTags.isEmpty() || !negativeTags.isEmpty()) {
            if (gallery.simpleTags == null) return false;
            Set<String> tags = new HashSet<>();
            for (String raw : gallery.simpleTags) {
                tags.add(SubscriptionRepository.normalizeTagName(raw));
            }
            if (!tags.containsAll(positiveTags)) return false;
            for (String tag : negativeTags) {
                if (tags.contains(tag)) return false;
            }
        }
        return true;
    }

    private static String normalizeTag(String raw) {
        if (raw == null) return null;
        int colon = raw.indexOf(':');
        if (colon <= 0 || colon == raw.length() - 1) return null;
        String tag = SubscriptionRepository.normalizeTagName(
                raw.substring(0, colon) + ':' + cleanValue(raw.substring(colon + 1)));
        return LocalFollowJson.isValidStandardTag(tag) ? tag : null;
    }

    private static String cleanValue(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.endsWith("$")) value = value.substring(0, value.length() - 1).trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
            if (value.endsWith("$")) value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }

    private static List<String> tokenize(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                current.append(c);
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (quoted) return Collections.singletonList("");
        if (current.length() > 0) result.add(current.toString());
        return result;
    }

    public static final class Result {
        public final boolean exact;
        public final String fallbackReason;
        public final BookmarkGlobalMatcher matcher;

        private Result(boolean exact, String reason, BookmarkGlobalMatcher matcher) {
            this.exact = exact;
            this.fallbackReason = reason;
            this.matcher = matcher;
        }

        private static Result exact(BookmarkGlobalMatcher matcher) {
            return new Result(true, "", matcher);
        }

        private static Result fallback(String reason) {
            return new Result(false, reason, null);
        }
    }
}
