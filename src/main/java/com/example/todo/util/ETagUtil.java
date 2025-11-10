package com.example.todo.util;

public class ETagUtil {

    public static String formatWeak(Integer version) {
        if (version == null) return null;
        return "W/\"" + version + "\"";
    }

    public static Integer parseIfMatch(String ifMatch) {
        if (ifMatch == null) return null;
        String s = ifMatch.trim();
        if (s.startsWith("W/")) {
            int i1 = s.indexOf('"');
            int i2 = s.lastIndexOf('"');
            if (i1 >= 0 && i2 > i1) {
                s = s.substring(i1 + 1, i2);
            } else {
                throw new IllegalArgumentException("Invalid ETag format");
            }
        } else if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length()-1);
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid ETag version");
        }
    }
}
