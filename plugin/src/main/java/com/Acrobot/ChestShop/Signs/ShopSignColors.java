package com.Acrobot.ChestShop.Signs;

import org.bukkit.ChatColor;

/**
 * Keeps shop sign formatting separate from the text used to validate and create a shop.
 */
public final class ShopSignColors {
    private ShopSignColors() {
    }

    /**
     * Translate Bukkit ampersand codes without modifying the submitted sign lines.
     */
    public static String[] translate(String[] lines) {
        String[] translated = lines.clone();
        for (int i = 0; i < translated.length; i++) {
            if (translated[i] != null) {
                translated[i] = ChatColor.translateAlternateColorCodes('&', translated[i]);
            }
        }
        return translated;
    }

    /**
     * Restore formatting onto canonical text, never restoring discarded visible characters.
     * Case and whitespace changes retain inline codes. Replaced text inherits its leading
     * codes; colon-separated parts (such as buy and sell prices) are handled independently.
     */
    public static String restore(String original, String replacement) {
        if (original == null || replacement == null || replacement.isEmpty()) {
            return replacement;
        }

        String plainOriginal = ChatColor.stripColor(original);
        if (original.equals(plainOriginal)) {
            return replacement;
        }

        // Keep formatting supplied by creation listeners, including the bold autofill marker.
        if (!replacement.equals(ChatColor.stripColor(replacement))) {
            return leadingColors(original) + replacement;
        }

        if (withoutWhitespace(plainOriginal).equalsIgnoreCase(withoutWhitespace(replacement))) {
            return copyColors(original, replacement);
        }

        String[] originalParts = original.split(":", -1);
        String[] replacementParts = replacement.split(":", -1);
        if (originalParts.length > 1 && originalParts.length == replacementParts.length) {
            for (int i = 0; i < replacementParts.length; i++) {
                replacementParts[i] = restore(originalParts[i], replacementParts[i]);
            }
            return String.join(":", replacementParts);
        }

        return leadingColors(original) + replacement;
    }

    private static String withoutWhitespace(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                result.append(text.charAt(i));
            }
        }
        return result.toString();
    }

    private static String leadingColors(String text) {
        StringBuilder colors = new StringBuilder();
        for (int i = 0; i < text.length();) {
            if (isColorCode(text, i)) {
                colors.append(text, i, i + 2);
                i += 2;
            } else if (Character.isWhitespace(text.charAt(i))) {
                i++;
            } else {
                break;
            }
        }
        return colors.toString();
    }

    private static String copyColors(String original, String replacement) {
        StringBuilder result = new StringBuilder();
        int source = 0;
        for (int i = 0; i < replacement.length(); i++) {
            char character = replacement.charAt(i);
            while (source < original.length()) {
                if (isColorCode(original, source)) {
                    result.append(original, source, source + 2);
                    source += 2;
                } else if (Character.isWhitespace(original.charAt(source))) {
                    source++;
                    if (Character.isWhitespace(character)) {
                        break;
                    }
                } else {
                    if (!Character.isWhitespace(character)) {
                        source++;
                    }
                    break;
                }
            }
            result.append(character);
        }

        // Preserve trailing resets without bringing back trimmed whitespace.
        while (source < original.length()) {
            if (isColorCode(original, source)) {
                result.append(original, source, source + 2);
                source += 2;
            } else {
                source++;
            }
        }
        return result.toString();
    }

    private static boolean isColorCode(String text, int index) {
        return text.charAt(index) == ChatColor.COLOR_CHAR && index + 1 < text.length()
                && ChatColor.stripColor(text.substring(index, index + 2)).isEmpty();
    }
}
