package com.android.settings.netdisk;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.util.List;

public class MediaDataUtil {

    private static final String TAG = MediaDataUtil.class.getSimpleName();
    private static final Uri VIDEO_URI = Uri.parse("content://media/external/video/media");

    private static String sortOrder() {
        return "date_modified" + " DESC";
    }

    public static void getVideoData(Context context, List<MediaBean> listData) {

        final String[] VIDEO_PROJECTION_DATA = new String[]{
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATE_MODIFIED};

        Cursor cursor = MediaStore.Images.Media.query(context.getContentResolver(),
                VIDEO_URI, VIDEO_PROJECTION_DATA,
                null, null, sortOrder());
        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                getDataFromCursor(cursor, listData, "video");
            }
        }
    }

    public static void getImageData(Context context, List<MediaBean> listData) {

        ContentResolver cr = context.getContentResolver();
        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_MODIFIED};

        Cursor cursor = cr.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                sortOrder());

        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                getDataFromCursor(cursor, listData, "image");
            }
            cursor.close();
        }

    }

    private static void getDataFromCursor(Cursor cursor, List<MediaBean> listData, String type) {
        String displayName = cursor.getString(2);
        if (displayName.equals("Camera")) {
            String id = cursor.getString(0);
            String path = cursor.getString(1);
            MediaBean bean = new MediaBean(id, type, path, displayName);
            listData.add(bean);
        } else {
            Log.e(TAG, "getDataFromCursor >> sName = " + displayName + " skip!");
        }
    }

    public static void getMediaData(Context context, List<MediaBean> listData) {
        getImageData(context, listData);
        getVideoData(context, listData);
    }
}
