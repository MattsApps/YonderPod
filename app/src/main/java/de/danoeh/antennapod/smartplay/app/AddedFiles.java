package de.danoeh.antennapod.smartplay.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

public final class AddedFiles {
    private static final String TAG = "AddedFiles";
    private static final String FOLDER = "Added files";

    public static final class Result {
        public final FeedItem item;
        public final boolean copied;
        public final long size;

        Result(FeedItem item, boolean copied, long size) {
            this.item = item;
            this.copied = copied;
            this.size = size;
        }
    }

    private AddedFiles() {
    }

    public static Result add(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String name = displayName(resolver, uri);
        String mimeType = resolver.getType(uri);
        long originalSize = fileSize(resolver, uri);
        Feed feed = addedFilesFeed(context);
        for (FeedItem existing : feed.getItems()) {
            FeedMedia existingMedia = existing.getMedia();
            if (existingMedia == null) {
                continue;
            }
            boolean sameFile = name.equals(existing.getLink()) && originalSize > 0
                    && originalSize == existingMedia.getSize();
            if (uri.toString().equals(existingMedia.getDownloadUrl()) || sameFile) {
                return new Result(existing, false, 0);
            }
        }
        boolean persisted = false;
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            persisted = true;
        } catch (SecurityException e) {
            Log.d(TAG, "No lasting permission for " + uri + ", copying");
        }
        String localFileUrl;
        String downloadUrl;
        long size;
        if (persisted) {
            localFileUrl = uri.toString();
            downloadUrl = uri.toString();
            size = originalSize;
        } else {
            File folder = UserPreferences.getDataFolder(FOLDER);
            File destination = uniqueFile(folder, name);
            try (InputStream in = resolver.openInputStream(uri);
                    OutputStream out = new FileOutputStream(destination)) {
                if (in == null) {
                    throw new IOException("Cannot open " + uri);
                }
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            localFileUrl = destination.getAbsolutePath();
            downloadUrl = Uri.fromFile(destination).toString();
            size = destination.length();
        }

        String guid = UUID.randomUUID().toString();
        FeedItem item = new FeedItem(0, stripExtension(name), guid, name, new Date(), FeedItem.UNPLAYED, feed);
        item.disableAutoDownload();
        FeedMedia media = new FeedMedia(0, item, 0, 0, size, mimeType, localFileUrl, downloadUrl, 0, null, 0, 0);
        item.setMedia(media);
        readMetadata(context, Uri.parse(downloadUrl), item);
        feed.getItems().add(item);
        FeedDatabaseWriter.updateFeed(context, feed, false);
        FeedItem saved = DBReader.getFeedItemByGuidOrEpisodeUrl(guid, downloadUrl);
        return new Result(saved != null ? saved : item, !persisted, size);
    }

    private static Feed addedFilesFeed(Context context) {
        for (Feed feed : DBReader.getFeedList()) {
            if (LocalFolders.isAddedFiles(feed)) {
                Feed full = DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
                if (full.getItems() == null) {
                    full.setItems(new ArrayList<>());
                }
                return full;
            }
        }
        Feed feed = new Feed(LocalFolders.ADDED_FILES_URL, null, context.getString(R.string.added_files_title));
        feed.setItems(new ArrayList<>());
        feed.setSortOrder(SortOrder.DATE_NEW_OLD);
        feed.setImageUrl(Feed.PREFIX_GENERATIVE_COVER + LocalFolders.ADDED_FILES_URL);
        Feed saved = FeedDatabaseWriter.updateFeed(context, feed, false);
        FeedPreferences preferences = saved.getPreferences();
        if (preferences != null) {
            preferences.setKeepUpdated(false);
            try {
                DBWriter.setFeedPreferences(preferences).get();
            } catch (Exception e) {
                Log.e(TAG, "Could not save preferences", e);
            }
        }
        LocalFolders.setPlayOrder(context, saved.getId(), LocalFolders.PlayOrder.NEWEST);
        Feed full = DBReader.getFeed(saved.getId(), false, 0, Integer.MAX_VALUE);
        if (full.getItems() == null) {
            full.setItems(new ArrayList<>());
        }
        return full;
    }

    private static void readMetadata(Context context, Uri uri, FeedItem item) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (!TextUtils.isEmpty(title)) {
                item.setTitle(title);
            }
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration != null) {
                item.getMedia().setDuration((int) Long.parseLong(duration));
            }
            item.getMedia().setHasEmbeddedPicture(retriever.getEmbeddedPicture() != null);
        } catch (Exception e) {
            Log.d(TAG, "No metadata for " + uri);
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                Log.d(TAG, "Could not release retriever");
            }
        }
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            Log.d(TAG, "No display name for " + uri);
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "audio";
    }

    private static long fileSize(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[] {OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (Exception e) {
            Log.d(TAG, "No size for " + uri);
        }
        return 0;
    }

    private static File uniqueFile(File folder, String name) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        File file = new File(folder, safe);
        int counter = 2;
        while (file.exists()) {
            file = new File(folder, stripExtension(safe) + " (" + counter + ")" + extension(safe));
            counter++;
        }
        return file;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }
}
