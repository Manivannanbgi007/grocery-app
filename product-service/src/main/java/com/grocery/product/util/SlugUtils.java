package com.grocery.product.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Turns a name into a URL-friendly "slug": "Dairy & Eggs" -> "dairy-eggs".
 */
public final class SlugUtils {

    private SlugUtils() {
    }

    public static String toSlug(String text) {
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")   // anything that is not a letter or digit becomes a dash
                .replaceAll("(^-|-$)", "");      // no dash at the start or end
    }
}
