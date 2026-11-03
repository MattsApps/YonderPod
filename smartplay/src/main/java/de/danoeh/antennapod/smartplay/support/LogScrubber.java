package de.danoeh.antennapod.smartplay.support;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogScrubber {
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b([a-z][a-z0-9+.-]*)://([^\\s\"'<>/?#]*)[^\\s\"'<>]*");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|token|auth|access_token|api_?key|key|secret|username|user)"
                    + "(\\s*[=:]\\s*)([^\\s,;&\"'}]+)");
    private static final String PATH_START = "(?<![A-Za-z0-9])/(?:storage|sdcard|data|mnt|Android|system/media)/";
    private static final Pattern PATH = Pattern.compile(
            PATH_START + "[^\\n\"<>]*?\\.[A-Za-z0-9]{1,5}\\b|" + PATH_START + "[^\\n\"<>]*");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private LogScrubber() {
    }

    public static String scrub(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String result = scrubUrls(text);
        result = SECRET.matcher(result).replaceAll("$1$2<hidden>");
        result = PATH.matcher(result).replaceAll("<file>");
        return EMAIL.matcher(result).replaceAll("<email>");
    }

    private static String scrubUrls(String text) {
        Matcher matcher = URL.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String scheme = matcher.group(1).toLowerCase(Locale.ROOT);
            String authority = matcher.group(2);
            int at = authority.lastIndexOf('@');
            String host = at >= 0 ? authority.substring(at + 1) : authority;
            int colon = host.indexOf(':');
            if (colon >= 0) {
                host = host.substring(0, colon);
            }
            String replacement;
            if (scheme.equals("http") || scheme.equals("https")) {
                replacement = scheme + "://" + host + "/…";
            } else {
                replacement = scheme + "://…";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
