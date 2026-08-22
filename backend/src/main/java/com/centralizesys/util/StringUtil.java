package com.centralizesys.util;

public class StringUtil {
    private StringUtil() {
        /* This utility class should not be instantiated */
    }

    public static String safeTruncate(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) return str;
        if (Character.isHighSurrogate(str.charAt(maxLen - 1))) {
            return str.substring(0, maxLen - 1);
        }
        return str.substring(0, maxLen);
    }
}
