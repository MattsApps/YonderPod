#!/bin/zsh
# Checks every episode Yonder has downloaded on the phone: does the audio run as long as the file says?
# (Bug: an episode stuck a few seconds before the end.) BBC episodes are checked first.
# Double-click with the phone plugged in (wired debug build). Results go to episode-lengths.txt.
cd "$(dirname "$0")"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
LOG="$PWD/episode-lengths.txt"
if ! adb get-state >/dev/null 2>&1; then
  echo "No phone found. Plug it in, unlock it, accept the USB debugging prompt, then run this again."
  exit 1
fi
{
  echo "==== Episode lengths, $(date) ===="
  /usr/bin/python3 - <<'PYTHON'
import os
import shlex
import struct
import subprocess
import sys

PKG = "com.mattsapps.yonderpod"
MEDIA = "/sdcard/Android/data/" + PKG + "/files/media"

BITRATES = {
    (1, 1): [0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448],
    (1, 2): [0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384],
    (1, 3): [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320],
    (2, 1): [0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256],
    (2, 2): [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160],
    (2, 3): [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160],
}
RATES = {3: [44100, 48000, 32000], 2: [22050, 24000, 16000], 0: [11025, 12000, 8000]}


def frame_info(h):
    if (h >> 21) & 0x7FF != 0x7FF:
        return None
    ver = (h >> 19) & 3
    layer = 4 - ((h >> 17) & 3)
    bri = (h >> 12) & 15
    sri = (h >> 10) & 3
    pad = (h >> 9) & 1
    if ver == 1 or layer == 4 or bri in (0, 15) or sri == 3:
        return None
    v = 1 if ver == 3 else 2
    kbps = BITRATES[(v, layer)][bri]
    rate = RATES[ver][sri]
    if layer == 1:
        size = (12 * kbps * 1000 // rate + pad) * 4
        spf = 384
    else:
        spf = 1152 if (layer == 2 or v == 1) else 576
        size = spf // 8 * kbps * 1000 // rate + pad
    mono = ((h >> 6) & 3) == 3
    return size, spf, rate, kbps, v, mono


def clock(seconds):
    seconds = int(round(seconds))
    return "%d:%02d:%02d" % (seconds // 3600, seconds // 60 % 60, seconds % 60)


def check(data):
    pos = 0
    if data[:3] == b"ID3" and len(data) > 10:
        s = data[6:10]
        pos = 10 + ((s[0] << 21) | (s[1] << 14) | (s[2] << 7) | s[3])
    first = None
    while pos + 4 <= len(data):
        info = frame_info(struct.unpack(">I", data[pos:pos + 4])[0])
        if info and pos + info[0] + 4 <= len(data) and frame_info(
                struct.unpack(">I", data[pos + info[0]:pos + info[0] + 4])[0]):
            first = pos
            break
        pos += 1
    if first is None:
        return "not an MP3 (or no audio found), skipped"
    size, spf, rate, kbps, v, mono = frame_info(struct.unpack(">I", data[first:first + 4])[0])
    side = (17 if mono else 32) if v == 1 else (9 if mono else 17)
    header = None
    for off, tag in ((4 + side, (b"Xing", b"Info")), (36, (b"VBRI",))):
        t = data[first + off:first + off + 4]
        if t in tag:
            if t == b"VBRI":
                nbytes, nframes = struct.unpack(">II", data[first + off + 10:first + off + 18])
                header = ("VBRI", nframes, nbytes)
            else:
                flags = struct.unpack(">I", data[first + off + 4:first + off + 8])[0]
                p = first + off + 8
                nframes = nbytes = None
                if flags & 1:
                    nframes = struct.unpack(">I", data[p:p + 4])[0]
                    p += 4
                if flags & 2:
                    nbytes = struct.unpack(">I", data[p:p + 4])[0]
                header = (t.decode(), nframes, nbytes)
            break
    frames = 0
    pos = first + (size if header else 0)
    audio_end = pos
    junk = 0
    while pos + 4 <= len(data):
        info = frame_info(struct.unpack(">I", data[pos:pos + 4])[0])
        if info and pos + info[0] <= len(data):
            frames += 1
            pos += info[0]
            audio_end = pos
        elif info:
            junk = len(data) - pos
            break
        else:
            pos += 1
            junk += 1
    actual = frames * spf / rate
    lines = ["file %d bytes, audio from byte %d, %d kbps %d Hz" % (len(data), first, kbps, rate),
             "actual audio: %d frames = %s" % (frames, clock(actual))]
    if header and header[1]:
        said = header[1] * spf / rate
        lines.append("%s header says %d frames = %s (%+.1f s vs actual)" % (header[0], header[1], clock(said),
                                                                          said - actual))
    else:
        said = (len(data) - first) * 8 / (kbps * 1000)
        lines.append("no frame count in the file: estimated from size and bitrate %s (%+.1f s vs actual)"
                     % (clock(said), said - actual))
    if header and header[2] and header[0] != "Info":
        end = first + header[2]
        lines.append("%s header says the audio ends at byte %d; the file %s" % (
            header[0], end, "ends %d bytes sooner, so seeking near the end reads past the file"
            % (end - len(data)) if end > len(data) else "is long enough"))
    if junk > 1024:
        lines.append("%d bytes after or between the audio that aren't audio" % junk)
    verdict = "SHORTER THAN REPORTED" if said - actual > 1.0 or (header and header[2] and header[0] != "Info"
                                                                  and first + header[2] > len(data)) else "ok"
    return verdict + "\n      " + "\n      ".join(lines)


def adb(cmd):
    return subprocess.run(["adb", "exec-out", cmd], capture_output=True).stdout


def main():
    if len(sys.argv) > 1:
        for path in sys.argv[1:]:
            with open(path, "rb") as f:
                print(os.path.basename(path) + ": " + check(f.read()))
        return
    listing = adb("run-as %s find %s -type f" % (PKG, MEDIA)).decode("utf-8", "replace").split("\n")
    files = [line.strip() for line in listing if line.strip().lower().endswith((".mp3", ".m4a", ".aac"))]
    if not files:
        listing = adb("find %s -type f" % MEDIA).decode("utf-8", "replace").split("\n")
        files = [line.strip() for line in listing if line.strip().lower().endswith((".mp3", ".m4a", ".aac"))]
    if not files:
        print("No downloaded episodes found on the phone (looked in %s)." % MEDIA)
        return
    files.sort(key=lambda p: (0 if "bbc" in p.lower() or "comedy" in p.lower() else 1, p))
    print("Checking %d downloaded episodes (BBC ones first)\n" % len(files))
    for path in files:
        data = adb("run-as %s cat %s" % (PKG, shlex.quote(path)))
        if not data:
            data = adb("cat %s" % shlex.quote(path))
        print(path.replace(MEDIA + "/", "") + "\n   " + check(data) + "\n", flush=True)


main()
PYTHON
  echo
  echo "==== Yonder's episode end log (newest last) ===="
  adb exec-out run-as com.mattsapps.yonderpod cat no_backup/episode-end-log.txt
} 2>&1 | tee "$LOG"
echo "Saved episode-lengths.txt. You can close this window."
