package de.danoeh.antennapod.ui.common;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ColourOverride {
    private static final String TAG = "ColourOverride";
    private static final short RES_STRING_POOL_TYPE = 0x0001;
    private static final short RES_TABLE_TYPE = 0x0002;
    private static final short RES_TABLE_PACKAGE_TYPE = 0x0200;
    private static final short RES_TABLE_TYPE_TYPE = 0x0201;
    private static final short RES_TABLE_TYPE_SPEC_TYPE = 0x0202;
    private static final int UTF8_FLAG = 1 << 8;
    private static final byte TYPE_INT_COLOR_ARGB8 = 0x1c;
    private static final int NO_ENTRY = 0xFFFFFFFF;
    private static final int CONFIG_SIZE = 64;

    private static Object loader;
    private static Map<Integer, Integer> loaderColours;

    private ColourOverride() {
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    public static void apply(Context context, Map<Integer, Integer> colours) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || colours.isEmpty()) {
            return;
        }
        try {
            applyLoader(context, colours);
        } catch (Exception e) {
            Log.e(TAG, "Could not apply custom colours", e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private static synchronized void applyLoader(Context context, Map<Integer, Integer> colours) throws Exception {
        if (loader == null || !colours.equals(loaderColours)) {
            byte[] table = table(context.getResources(), colours);
            File file = new File(context.getCacheDir(), "custom_palette.arsc");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(table);
            }
            try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file,
                    ParcelFileDescriptor.MODE_READ_ONLY)) {
                ResourcesLoader fresh = new ResourcesLoader();
                fresh.addProvider(ResourcesProvider.loadFromTable(descriptor, null));
                loader = fresh;
                loaderColours = new TreeMap<>(colours);
            }
        }
        ResourcesLoader current = (ResourcesLoader) loader;
        if (!context.getResources().getLoaders().contains(current)) {
            context.getResources().addLoaders(current);
        }
    }

    static byte[] table(Resources resources, Map<Integer, Integer> colours) {
        TreeMap<Integer, Integer> sorted = new TreeMap<>(colours);
        int firstId = sorted.firstKey();
        int packageId = firstId >>> 24;
        int typeId = (firstId >> 16) & 0xFF;
        String packageName = resources.getResourcePackageName(firstId);
        String typeName = resources.getResourceTypeName(firstId);
        List<String> keys = new ArrayList<>();
        int entryCount = 0;
        for (int id : sorted.keySet()) {
            keys.add(resources.getResourceEntryName(id));
            entryCount = Math.max(entryCount, (id & 0xFFFF) + 1);
        }
        List<String> typeNames = new ArrayList<>();
        for (int i = 1; i < typeId; i++) {
            typeNames.add("type" + i);
        }
        typeNames.add(typeName);

        byte[] typeStrings = stringPool(typeNames);
        byte[] keyStrings = stringPool(keys);
        byte[] typeSpec = typeSpec(typeId, entryCount);
        byte[] type = type(typeId, entryCount, sorted);

        int packageHeader = 288;
        int packageSize = packageHeader + typeStrings.length + keyStrings.length + typeSpec.length + type.length;
        ByteBuffer pkg = buffer(packageSize);
        pkg.putShort(RES_TABLE_PACKAGE_TYPE).putShort((short) packageHeader).putInt(packageSize);
        pkg.putInt(packageId);
        char[] name = new char[128];
        packageName.getChars(0, Math.min(127, packageName.length()), name, 0);
        for (char c : name) {
            pkg.putChar(c);
        }
        pkg.putInt(packageHeader);
        pkg.putInt(0);
        pkg.putInt(packageHeader + typeStrings.length);
        pkg.putInt(0);
        pkg.putInt(0);
        pkg.put(typeStrings).put(keyStrings).put(typeSpec).put(type);

        byte[] globalStrings = stringPool(new ArrayList<>());
        int tableSize = 12 + globalStrings.length + packageSize;
        ByteBuffer table = buffer(tableSize);
        table.putShort(RES_TABLE_TYPE).putShort((short) 12).putInt(tableSize).putInt(1);
        table.put(globalStrings).put(pkg.array());
        return table.array();
    }

    private static byte[] typeSpec(int typeId, int entryCount) {
        int size = 16 + 4 * entryCount;
        ByteBuffer spec = buffer(size);
        spec.putShort(RES_TABLE_TYPE_SPEC_TYPE).putShort((short) 16).putInt(size);
        spec.put((byte) typeId).put((byte) 0).putShort((short) 0).putInt(entryCount);
        for (int i = 0; i < entryCount; i++) {
            spec.putInt(0);
        }
        return spec.array();
    }

    private static byte[] type(int typeId, int entryCount, TreeMap<Integer, Integer> colours) {
        int header = 20 + CONFIG_SIZE;
        int entriesStart = header + 4 * entryCount;
        int size = entriesStart + 16 * colours.size();
        ByteBuffer type = buffer(size);
        type.putShort(RES_TABLE_TYPE_TYPE).putShort((short) header).putInt(size);
        type.put((byte) typeId).put((byte) 0).putShort((short) 0).putInt(entryCount).putInt(entriesStart);
        type.putInt(CONFIG_SIZE);
        for (int i = 4; i < CONFIG_SIZE; i++) {
            type.put((byte) 0);
        }
        int[] offsets = new int[entryCount];
        for (int i = 0; i < entryCount; i++) {
            offsets[i] = NO_ENTRY;
        }
        int index = 0;
        for (int id : colours.keySet()) {
            offsets[id & 0xFFFF] = 16 * index;
            index++;
        }
        for (int offset : offsets) {
            type.putInt(offset);
        }
        int key = 0;
        for (int colour : colours.values()) {
            type.putShort((short) 8).putShort((short) 0).putInt(key);
            type.putShort((short) 8).put((byte) 0).put(TYPE_INT_COLOR_ARGB8).putInt(colour);
            key++;
        }
        return type.array();
    }

    private static byte[] stringPool(List<String> strings) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] offsets = new int[strings.size()];
        for (int i = 0; i < strings.size(); i++) {
            offsets[i] = data.size();
            byte[] utf8 = strings.get(i).getBytes(StandardCharsets.UTF_8);
            writeLength(data, strings.get(i).length());
            writeLength(data, utf8.length);
            data.write(utf8, 0, utf8.length);
            data.write(0);
        }
        while (data.size() % 4 != 0) {
            data.write(0);
        }
        int header = 28;
        int stringsStart = strings.isEmpty() ? 0 : header + 4 * strings.size();
        int size = header + 4 * strings.size() + data.size();
        ByteBuffer pool = buffer(size);
        pool.putShort(RES_STRING_POOL_TYPE).putShort((short) header).putInt(size);
        pool.putInt(strings.size()).putInt(0).putInt(UTF8_FLAG).putInt(stringsStart).putInt(0);
        for (int offset : offsets) {
            pool.putInt(offset);
        }
        pool.put(data.toByteArray());
        return pool.array();
    }

    private static void writeLength(ByteArrayOutputStream out, int length) {
        if (length > 0x7F) {
            out.write(((length >> 8) & 0x7F) | 0x80);
        }
        out.write(length & 0xFF);
    }

    private static ByteBuffer buffer(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }
}
