package de.danoeh.antennapod.smartplay.radio;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HlsSegments {
    private static final Pattern MAP_URI = Pattern.compile("#EXT-X-MAP:.*URI=\"([^\"]+)\"");
    private static final Pattern KEY_METHOD = Pattern.compile("#EXT-X-KEY:.*METHOD=([A-Z0-9-]+)");
    private static final Pattern SEQUENCE = Pattern.compile("#EXT-X-MEDIA-SEQUENCE:\\s*(\\d+)");

    public static final class Segment {
        private final String uri;
        private final long sequence;
        private final long start;
        private final long durationMillis;

        Segment(String uri, long sequence, long start, long durationMillis) {
            this.uri = uri;
            this.sequence = sequence;
            this.start = start;
            this.durationMillis = durationMillis;
        }

        public String getUri() {
            return uri;
        }

        public long getSequence() {
            return sequence;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return start + durationMillis;
        }
    }

    public static final class Playlist {
        private final List<Segment> segments;
        private final String initUri;
        private final boolean encrypted;
        private final boolean timed;
        private final long targetDurationMillis;

        Playlist(List<Segment> segments, String initUri, boolean encrypted, boolean timed, long targetDurationMillis) {
            this.segments = segments;
            this.initUri = initUri;
            this.encrypted = encrypted;
            this.timed = timed;
            this.targetDurationMillis = targetDurationMillis;
        }

        public List<Segment> getSegments() {
            return segments;
        }

        public String getInitUri() {
            return initUri;
        }

        public boolean isEncrypted() {
            return encrypted;
        }

        public boolean hasClockTimes() {
            return timed;
        }

        public long getTargetDurationMillis() {
            return targetDurationMillis;
        }

        public List<Segment> between(long from, long to) {
            List<Segment> result = new ArrayList<>();
            for (Segment segment : segments) {
                if (segment.getEnd() > from && segment.getStart() < to) {
                    result.add(segment);
                }
            }
            return result;
        }

        public boolean covers(long from, long to) {
            return !segments.isEmpty() && segments.get(0).getStart() <= from
                    && segments.get(segments.size() - 1).getEnd() >= to;
        }
    }

    private HlsSegments() {
    }

    public static Playlist parse(String playlist, long fetchedAt) {
        String initUri = null;
        boolean encrypted = false;
        long sequence = 0;
        long targetMillis = 6000;
        Long pendingTime = null;
        double pendingDuration = -1;
        List<Long> times = new ArrayList<>();
        List<Long> durations = new ArrayList<>();
        List<String> uris = new ArrayList<>();
        for (String rawLine : playlist.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#EXT-X-MEDIA-SEQUENCE")) {
                Matcher matcher = SEQUENCE.matcher(line);
                if (matcher.find()) {
                    sequence = Long.parseLong(matcher.group(1));
                }
            } else if (line.startsWith("#EXT-X-TARGETDURATION:")) {
                try {
                    targetMillis = Math.round(Double.parseDouble(line.substring(22).trim()) * 1000);
                } catch (NumberFormatException ignored) {
                    targetMillis = 6000;
                }
            } else if (line.startsWith("#EXT-X-MAP")) {
                Matcher matcher = MAP_URI.matcher(line);
                if (matcher.find()) {
                    initUri = matcher.group(1);
                }
            } else if (line.startsWith("#EXT-X-KEY")) {
                Matcher matcher = KEY_METHOD.matcher(line);
                encrypted = matcher.find() && !"NONE".equals(matcher.group(1));
            } else if (line.startsWith("#EXT-X-PROGRAM-DATE-TIME:")) {
                pendingTime = parseTime(line.substring(25).trim());
            } else if (line.startsWith("#EXTINF:")) {
                String value = line.substring(8);
                int comma = value.indexOf(',');
                try {
                    pendingDuration = Double.parseDouble((comma >= 0 ? value.substring(0, comma) : value).trim());
                } catch (NumberFormatException e) {
                    pendingDuration = -1;
                }
            } else if (!line.startsWith("#") && pendingDuration >= 0) {
                uris.add(line);
                durations.add(Math.round(pendingDuration * 1000));
                times.add(pendingTime);
                pendingTime = null;
                pendingDuration = -1;
            }
        }
        boolean timed = false;
        for (Long time : times) {
            if (time != null) {
                timed = true;
                break;
            }
        }
        long[] starts = new long[uris.size()];
        if (timed) {
            Long current = null;
            for (int i = 0; i < uris.size(); i++) {
                if (times.get(i) != null) {
                    current = times.get(i);
                }
                if (current == null) {
                    long ahead = 0;
                    int j = i;
                    while (times.get(j) == null) {
                        ahead += durations.get(j);
                        j++;
                    }
                    current = times.get(j) - ahead;
                }
                starts[i] = current;
                current = current + durations.get(i);
            }
        } else {
            long end = fetchedAt;
            for (int i = uris.size() - 1; i >= 0; i--) {
                end -= durations.get(i);
                starts[i] = end;
            }
        }
        List<Segment> segments = new ArrayList<>();
        for (int i = 0; i < uris.size(); i++) {
            segments.add(new Segment(uris.get(i), sequence + i, starts[i], durations.get(i)));
        }
        return new Playlist(segments, initUri, encrypted, timed, targetMillis);
    }

    public static String chooseVariant(String master, int maxBitrate) {
        Pattern bandwidth = Pattern.compile("[:,]BANDWIDTH=(\\d+)");
        String best = null;
        long bestRate = -1;
        String lowest = null;
        long lowestRate = Long.MAX_VALUE;
        long pendingRate = -1;
        for (String rawLine : master.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                Matcher matcher = bandwidth.matcher(line);
                pendingRate = matcher.find() ? Long.parseLong(matcher.group(1)) : 0;
            } else if (pendingRate >= 0 && !line.isEmpty() && !line.startsWith("#")) {
                if (pendingRate <= maxBitrate && pendingRate > bestRate) {
                    best = line;
                    bestRate = pendingRate;
                }
                if (pendingRate < lowestRate) {
                    lowest = line;
                    lowestRate = pendingRate;
                }
                pendingRate = -1;
            }
        }
        return best != null ? best : lowest;
    }

    static Long parseTime(String value) {
        String text = value.replace("Z", "+0000");
        Matcher offset = Pattern.compile("([+-]\\d{2}):(\\d{2})$").matcher(text);
        if (offset.find()) {
            text = text.substring(0, offset.start()) + offset.group(1) + offset.group(2);
        }
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ"};
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            format.setLenient(false);
            try {
                String normalised = text;
                Matcher fraction = Pattern.compile("\\.(\\d+)").matcher(normalised);
                if (pattern.contains("SSS") && fraction.find()) {
                    String digits = (fraction.group(1) + "000").substring(0, 3);
                    normalised = normalised.substring(0, fraction.start()) + "." + digits
                            + normalised.substring(fraction.end());
                }
                return format.parse(normalised).getTime();
            } catch (ParseException ignored) {
                continue;
            }
        }
        return null;
    }
}
