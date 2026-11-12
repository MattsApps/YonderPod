package de.danoeh.antennapod.smartplay.radio;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Writes an audio-only MP4 (.m4a) holding AAC frames, so a recording can be turned into a seekable file
 * without ever writing it out unencrypted (Android's MediaMuxer only writes to a plain file).
 *
 * <p>Two passes: tell it every frame's size and time with {@link #addSample}, then write {@link #header()}
 * followed by the frames' bytes, in the same order, to any stream (here, an encrypting one). The index
 * ("moov") goes before the audio, so the file plays and seeks from the start.
 */
public final class Mp4AudioWriter {
    private static final int CHUNK_SAMPLES = 1000;
    private static final int MOVIE_TIMESCALE = 1000;
    private static final int[] MATRIX = {0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000};

    private final int sampleRate;
    private final int channels;
    private final byte[] decoderConfig;
    private int[] sizes = new int[4096];
    private long[] times = new long[4096];
    private int count;
    private long totalBytes;
    private int largest;

    /**
     * @param sampleRate    from the track's format
     * @param channels      from the track's format
     * @param decoderConfig the AAC AudioSpecificConfig (the track's "csd-0")
     */
    public Mp4AudioWriter(int sampleRate, int channels, byte[] decoderConfig) {
        if (sampleRate <= 0 || channels <= 0 || decoderConfig == null || decoderConfig.length == 0) {
            throw new IllegalArgumentException("Not enough about the audio to write an MP4");
        }
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.decoderConfig = decoderConfig.clone();
    }

    /** One AAC frame: its size in bytes and its time in microseconds. */
    public void addSample(int size, long timeUs) {
        if (count == sizes.length) {
            sizes = Arrays.copyOf(sizes, count * 2);
            times = Arrays.copyOf(times, count * 2);
        }
        sizes[count] = size;
        times[count] = timeUs;
        count++;
        totalBytes += size;
        largest = Math.max(largest, size);
    }

    public int getSampleCount() {
        return count;
    }

    public int getSampleSize(int index) {
        return sizes[index];
    }

    public long getAudioBytes() {
        return totalBytes;
    }

    /** Everything before the audio: ftyp, moov and the mdat header. The frames follow, unchanged. */
    public byte[] header() throws IOException {
        if (count == 0) {
            throw new IOException("No audio");
        }
        if (totalBytes + 8 > 0xFFFFFFFFL) {
            throw new IOException("Too long for an MP4 recording");
        }
        int[] durations = durations();
        long mediaDuration = 0;
        for (int duration : durations) {
            mediaDuration += duration;
        }
        byte[] ftyp = ftyp();
        // The chunk offsets depend on the moov's size, which doesn't depend on the offsets' values.
        byte[] moov = moov(durations, mediaDuration, 0);
        long dataStart = ftyp.length + moov.length + 8;
        moov = moov(durations, mediaDuration, dataStart);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(ftyp.length + moov.length + 8);
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(ftyp);
        out.write(moov);
        out.writeInt((int) (totalBytes + 8));
        out.write(type("mdat"));
        out.flush();
        return bytes.toByteArray();
    }

    /**
     * Each frame's length in sample-rate units, from the gaps between the frames' times. A gap that's missing
     * or clearly wrong (a reconnect or a jump in the stream's clock) takes the usual frame length instead, so
     * the audio plays straight through.
     */
    int[] durations() {
        int typical = typicalDuration();
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            long gap = i + 1 < count ? toUnits(times[i + 1] - times[i]) : typical;
            result[i] = gap > 0 && gap <= typical * 4L ? (int) gap : typical;
        }
        return result;
    }

    private int typicalDuration() {
        int[] gaps = new int[Math.min(count - 1, 200)];
        int found = 0;
        for (int i = 0; i < gaps.length; i++) {
            long gap = toUnits(times[i + 1] - times[i]);
            if (gap > 0 && gap < sampleRate) {
                gaps[found++] = (int) gap;
            }
        }
        if (found == 0) {
            return 1024;
        }
        int[] sorted = Arrays.copyOf(gaps, found);
        Arrays.sort(sorted);
        return sorted[found / 2];
    }

    private long toUnits(long micros) {
        return Math.round(micros * (double) sampleRate / 1_000_000d);
    }

    private static byte[] ftyp() throws IOException {
        Box box = new Box("ftyp");
        box.out.write(type("M4A "));
        box.out.writeInt(0);
        box.out.write(type("M4A "));
        box.out.write(type("mp42"));
        box.out.write(type("isom"));
        return box.bytes();
    }

    private byte[] moov(int[] durations, long mediaDuration, long dataStart) throws IOException {
        long movieDuration = mediaDuration * MOVIE_TIMESCALE / sampleRate;
        Box moov = new Box("moov");

        Box mvhd = new Box("mvhd");
        mvhd.out.writeInt(0); // version and flags
        mvhd.out.writeInt(0); // created
        mvhd.out.writeInt(0); // modified
        mvhd.out.writeInt(MOVIE_TIMESCALE);
        mvhd.out.writeInt((int) movieDuration);
        mvhd.out.writeInt(0x00010000); // rate 1.0
        mvhd.out.writeShort(0x0100); // volume 1.0
        mvhd.out.write(new byte[10]);
        for (int value : MATRIX) {
            mvhd.out.writeInt(value);
        }
        mvhd.out.write(new byte[24]);
        mvhd.out.writeInt(2); // next track id
        moov.add(mvhd);

        Box trak = new Box("trak");
        Box tkhd = new Box("tkhd");
        tkhd.out.writeInt(0x00000007); // version 0, enabled, in movie, in preview
        tkhd.out.writeInt(0);
        tkhd.out.writeInt(0);
        tkhd.out.writeInt(1); // track id
        tkhd.out.writeInt(0);
        tkhd.out.writeInt((int) movieDuration);
        tkhd.out.write(new byte[8]);
        tkhd.out.writeShort(0); // layer
        tkhd.out.writeShort(1); // alternate group
        tkhd.out.writeShort(0x0100); // volume
        tkhd.out.writeShort(0);
        for (int value : MATRIX) {
            tkhd.out.writeInt(value);
        }
        tkhd.out.writeInt(0); // width
        tkhd.out.writeInt(0); // height
        trak.add(tkhd);

        Box mdia = new Box("mdia");
        Box mdhd = new Box("mdhd");
        mdhd.out.writeInt(0);
        mdhd.out.writeInt(0);
        mdhd.out.writeInt(0);
        mdhd.out.writeInt(sampleRate);
        mdhd.out.writeInt((int) mediaDuration);
        mdhd.out.writeShort(0x55C4); // "und"
        mdhd.out.writeShort(0);
        mdia.add(mdhd);

        Box hdlr = new Box("hdlr");
        hdlr.out.writeInt(0);
        hdlr.out.writeInt(0);
        hdlr.out.write(type("soun"));
        hdlr.out.write(new byte[12]);
        hdlr.out.write("SoundHandler".getBytes(StandardCharsets.US_ASCII));
        hdlr.out.writeByte(0);
        mdia.add(hdlr);

        Box minf = new Box("minf");
        Box smhd = new Box("smhd");
        smhd.out.writeInt(0);
        smhd.out.writeInt(0);
        minf.add(smhd);
        Box dinf = new Box("dinf");
        Box dref = new Box("dref");
        dref.out.writeInt(0);
        dref.out.writeInt(1);
        Box url = new Box("url ");
        url.out.writeInt(1); // self-contained
        dref.add(url);
        dinf.add(dref);
        minf.add(dinf);
        minf.add(stbl(durations, dataStart));
        mdia.add(minf);
        trak.add(mdia);
        moov.add(trak);
        return moov.bytes();
    }

    private Box stbl(int[] durations, long dataStart) throws IOException {
        Box stbl = new Box("stbl");

        Box stsd = new Box("stsd");
        stsd.out.writeInt(0);
        stsd.out.writeInt(1);
        Box mp4a = new Box("mp4a");
        mp4a.out.write(new byte[6]);
        mp4a.out.writeShort(1); // data reference index
        mp4a.out.write(new byte[8]);
        mp4a.out.writeShort(channels);
        mp4a.out.writeShort(16);
        mp4a.out.writeShort(0);
        mp4a.out.writeShort(0);
        mp4a.out.writeInt((sampleRate > 0xFFFF ? 0 : sampleRate) << 16);
        mp4a.add(esds(durations));
        stsd.add(mp4a);
        stbl.add(stsd);

        Box stts = new Box("stts");
        ByteArrayOutputStream runs = new ByteArrayOutputStream();
        DataOutputStream runOut = new DataOutputStream(runs);
        int entries = 0;
        int i = 0;
        while (i < durations.length) {
            int j = i;
            while (j < durations.length && durations[j] == durations[i]) {
                j++;
            }
            runOut.writeInt(j - i);
            runOut.writeInt(durations[i]);
            entries++;
            i = j;
        }
        stts.out.writeInt(0);
        stts.out.writeInt(entries);
        stts.out.write(runs.toByteArray());
        stbl.add(stts);

        int chunks = (count + CHUNK_SAMPLES - 1) / CHUNK_SAMPLES;
        int last = count - (chunks - 1) * CHUNK_SAMPLES;
        Box stsc = new Box("stsc");
        stsc.out.writeInt(0);
        stsc.out.writeInt(last == CHUNK_SAMPLES || chunks == 1 ? 1 : 2);
        stsc.out.writeInt(1);
        stsc.out.writeInt(chunks == 1 ? last : CHUNK_SAMPLES);
        stsc.out.writeInt(1);
        if (last != CHUNK_SAMPLES && chunks > 1) {
            stsc.out.writeInt(chunks);
            stsc.out.writeInt(last);
            stsc.out.writeInt(1);
        }
        stbl.add(stsc);

        Box stsz = new Box("stsz");
        stsz.out.writeInt(0);
        stsz.out.writeInt(0);
        stsz.out.writeInt(count);
        for (int k = 0; k < count; k++) {
            stsz.out.writeInt(sizes[k]);
        }
        stbl.add(stsz);

        Box stco = new Box("stco");
        stco.out.writeInt(0);
        stco.out.writeInt(chunks);
        long offset = dataStart;
        for (int k = 0; k < count; k++) {
            if (k % CHUNK_SAMPLES == 0) {
                stco.out.writeInt((int) offset);
            }
            offset += sizes[k];
        }
        stbl.add(stco);
        return stbl;
    }

    private Box esds(int[] durations) throws IOException {
        long units = 0;
        for (int duration : durations) {
            units += duration;
        }
        double seconds = Math.max(1d, units / (double) sampleRate);
        int bitrate = (int) Math.min(Integer.MAX_VALUE, Math.round(totalBytes * 8 / seconds));

        ByteArrayOutputStream config = new ByteArrayOutputStream();
        DataOutputStream configOut = new DataOutputStream(config);
        configOut.writeByte(0x40); // MPEG-4 audio
        configOut.writeByte(0x15); // audio stream
        configOut.writeByte((largest >> 16) & 0xFF);
        configOut.writeShort(largest & 0xFFFF);
        configOut.writeInt(bitrate);
        configOut.writeInt(bitrate);
        writeDescriptor(configOut, 0x05, decoderConfig);

        ByteArrayOutputStream es = new ByteArrayOutputStream();
        DataOutputStream esOut = new DataOutputStream(es);
        esOut.writeShort(1); // ES id
        esOut.writeByte(0);
        writeDescriptor(esOut, 0x04, config.toByteArray());
        writeDescriptor(esOut, 0x06, new byte[] {0x02});

        Box esds = new Box("esds");
        esds.out.writeInt(0);
        writeDescriptor(esds.out, 0x03, es.toByteArray());
        return esds;
    }

    private static void writeDescriptor(DataOutputStream out, int tag, byte[] body) throws IOException {
        out.writeByte(tag);
        int length = body.length;
        // Four bytes of length, seven bits each, as most writers do.
        out.writeByte(0x80 | ((length >> 21) & 0x7F));
        out.writeByte(0x80 | ((length >> 14) & 0x7F));
        out.writeByte(0x80 | ((length >> 7) & 0x7F));
        out.writeByte(length & 0x7F);
        out.write(body);
    }

    private static byte[] type(String fourCc) {
        return fourCc.getBytes(StandardCharsets.US_ASCII);
    }

    private static final class Box {
        final String type;
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final DataOutputStream out = new DataOutputStream(body);

        Box(String type) {
            this.type = type;
        }

        void add(Box child) throws IOException {
            out.write(child.bytes());
        }

        byte[] bytes() throws IOException {
            out.flush();
            ByteArrayOutputStream whole = new ByteArrayOutputStream(body.size() + 8);
            DataOutputStream wholeOut = new DataOutputStream(whole);
            wholeOut.writeInt(body.size() + 8);
            wholeOut.write(type(type));
            wholeOut.write(body.toByteArray());
            wholeOut.flush();
            return whole.toByteArray();
        }
    }
}
