/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.deviceinfo;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityThread;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.text.format.Formatter;

import com.android.settings.R;
import com.android.settings.deviceinfo.StorageMeasurement.MeasurementDetails;
import com.android.settings.deviceinfo.StorageMeasurement.MeasurementReceiver;
import com.google.android.collect.Lists;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import android.util.Log;

public class StorageVolumePreferenceCategory extends PreferenceCategory {
    public static final String KEY_CACHE = "cache";

    // SPRD：ADD for Settings porting from 4.1 to 4.3
    public static String HIDE_BOTTOM_BUTTON = "hide_bottom_button";

    private static final int ORDER_USAGE_BAR = -2;
    private static final int ORDER_STORAGE_LOW = -1;

    /** Physical volume being measured, or {@code null} for internal. */
    private final StorageVolume mVolume;
    private final StorageMeasurement mMeasure;

    private final Resources mResources;
    private final StorageManager mStorageManager;
    private final UserManager mUserManager;

    private UsageBarPreference mUsageBarPreference;
    private Preference mMountTogglePreference;
    private Preference mFormatPreference;
    private Preference mStorageLow;

    private StorageItemPreference mItemTotal;
    private StorageItemPreference mItemAvailable;
    private StorageItemPreference mItemApps;
    private StorageItemPreference mItemDcim;
    private StorageItemPreference mItemMusic;
    private StorageItemPreference mItemDownloads;
    private StorageItemPreference mItemCache;
    private StorageItemPreference mItemMisc;
    // SPRD:ADD the video prefence.
    private StorageItemPreference mItemVideo;
    // SPRD:ADD for others prefence.
    private StorageItemPreference mItemOthers;
    private boolean mIsCaculateTatal = false;
    private List<StorageItemPreference> mItemUsers = Lists.newArrayList();

    private boolean mUsbConnected;
    private String mUsbFunction;

    // SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917
    private boolean mIsInternalStorage = false;
    // SPRD: ADD the compression ratio for the items in internal-T.
    private float mCompressionRatio = 1f;

    private long mTotalSize;

    private static final int MSG_UI_UPDATE_APPROXIMATE = 1;
    private static final int MSG_UI_UPDATE_DETAILS = 2;

    private Handler mUpdateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UI_UPDATE_APPROXIMATE: {
                    final long[] size = (long[]) msg.obj;
                    updateApproximate(size[0], size[1]);
                    break;
                }
                case MSG_UI_UPDATE_DETAILS: {
                    final MeasurementDetails details = (MeasurementDetails) msg.obj;
                    updateDetails(details);
                    // set the prefences enable.
                    setPrefencesEnable(true);
                    break;
                }
            }
        }
    };

    /**
     * Build category to summarize internal storage, including any emulated
     * {@link StorageVolume}.
     */
    public static StorageVolumePreferenceCategory buildForInternal(Context context) {
        return new StorageVolumePreferenceCategory(context, null);
    }

    /**
     * Build category to summarize specific physical {@link StorageVolume}.
     */
    public static StorageVolumePreferenceCategory buildForPhysical(
            Context context, StorageVolume volume) {
        return new StorageVolumePreferenceCategory(context, volume);
    }

    private StorageVolumePreferenceCategory(Context context, StorageVolume volume) {
        super(context);

        mVolume = volume;
        mMeasure = StorageMeasurement.getInstance(context, volume);

        mResources = context.getResources();
        mStorageManager = StorageManager.from(context);
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        // SRPD: ADD if volume is null,then caculate the tatal size again.
        if (null == mVolume) {
            mIsCaculateTatal = true;
        }
        /* SPRD: REMOVE because bla bla .. @{
        setTitle(volume != null ? volume.getDescription(context)
                : context.getText(R.string.internal_storage));
        @} */

        /* SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917 @{ */
        if (volume != null) {
            setTitle(volume.getDescription(context));
            Log.i("baiyuliang","volume path = " + volume.getPath() + ",internalPath = " + Environment.getInternalStoragePath() + ".");
            if (volume.getPath().equals(Environment.getInternalStoragePath().toString())) {
                mIsInternalStorage = true;
            }
        } else {
            setTitle(context.getText(R.string.internal_storage));
        }
        /* @} */
    }

    private StorageItemPreference buildItem(int titleRes, int colorRes) {
        return new StorageItemPreference(getContext(), titleRes, colorRes);
    }

    public void init() {
        final Context context = getContext();

        removeAll();

        final UserInfo currentUser;
        try {
            currentUser = ActivityManagerNative.getDefault().getCurrentUser();
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to get current user");
        }

        final List<UserInfo> otherUsers = getUsersExcluding(currentUser);
        final boolean showUsers = mVolume == null && otherUsers.size() > 0;

        mUsageBarPreference = new UsageBarPreference(context);
        mUsageBarPreference.setOrder(ORDER_USAGE_BAR);
        addPreference(mUsageBarPreference);

        mItemTotal = buildItem(R.string.memory_size, 0);
        mItemAvailable = buildItem(R.string.memory_available, R.color.memory_avail);
        addPreference(mItemTotal);
        addPreference(mItemAvailable);

        mItemApps = buildItem(R.string.memory_apps_usage, R.color.memory_apps_usage);
        mItemDcim = buildItem(R.string.memory_dcim_usage, R.color.memory_dcim);
        // SPRD:ADD the video prefence.
        mItemVideo = buildItem(R.string.memory_video_usage, R.color.memory_video);
        mItemMusic = buildItem(R.string.memory_music_usage, R.color.memory_music);
        mItemDownloads = buildItem(R.string.memory_downloads_usage, R.color.memory_downloads);
        mItemCache = buildItem(R.string.memory_media_cache_usage, R.color.memory_cache);
        mItemMisc = buildItem(R.string.memory_media_misc_usage, R.color.memory_misc);
        // SPRD:ADD for the others prefence.
        mItemOthers = buildItem(R.string.memory_others_usage, R.color.memory_downloads);

        mItemCache.setKey(KEY_CACHE);

        if (showUsers) {
            addPreference(new PreferenceHeader(context, currentUser.name));
        }

        // SPRD: Modify for bug912340.
        // addPreference(mItemApps);
        addPreference(mItemDcim);
        // SPRD:ADD the video prefence
        addPreference(mItemVideo);
        addPreference(mItemMusic);
        addPreference(mItemDownloads);
        addPreference(mItemCache);
        addPreference(mItemMisc);
        // SPRD:ADD add the prefence in catoragy
        addPreference(mItemOthers);
        // set the prefence enable or unenable.
        setPrefencesEnable(false);

        if (showUsers) {
            addPreference(new PreferenceHeader(context,
                    R.string.storage_other_users));

            int count = 0;
            for (UserInfo info : otherUsers) {
                final int colorRes = count++ % 2 == 0 ? R.color.memory_user_light
                        : R.color.memory_user_dark;
                final StorageItemPreference userPref = new StorageItemPreference(
                        getContext(), info.name, colorRes, info.id);
                mItemUsers.add(userPref);
                addPreference(userPref);
            }
        }

        final boolean isRemovable = mVolume != null ? mVolume.isRemovable() : false;
        // Always create the preference since many code rely on it existing
        mMountTogglePreference = new Preference(context);
        if (isRemovable) {
            mMountTogglePreference.setTitle(R.string.sd_eject);
            mMountTogglePreference.setSummary(R.string.sd_eject_summary);
            addPreference(mMountTogglePreference);
        }

        // Only allow formatting of primary physical storage
        // TODO: enable for non-primary volumes once MTP is fixed
        // SPRD：REMOVE because of useless
        //final boolean allowFormat = mVolume != null ? mVolume.isPrimary() : false;
        // SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917
        final boolean allowFormat = mVolume != null;
        if (allowFormat) {
            mFormatPreference = new Preference(context);
            /* SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917 @{ */
            if (mIsInternalStorage) {
                mFormatPreference.setTitle(R.string.internal_format);
                mFormatPreference.setSummary(R.string.internal_format_summary);
            } else {
                mFormatPreference.setTitle(R.string.sd_format);
                mFormatPreference.setSummary(R.string.sd_format_summary);
            }
            /* @} */
            addPreference(mFormatPreference);
        }
        // SPRD: ADD for internal sdcard,set lowMemory prefence.
        if (null == mVolume) {
            final IPackageManager pm = ActivityThread.getPackageManager();
            try {
                if (pm.isStorageLow()) {
                    mStorageLow = new Preference(context);
                    mStorageLow.setOrder(ORDER_STORAGE_LOW);
                    /* SPRD: Modify for bug653427. @{ */
                    // mStorageLow.setTitle(R.string.storage_low_title);
                    // mStorageLow.setSummary(R.string.storage_low_summary);
                    mStorageLow.setLayoutResource(R.layout.storage_low_details_layout);
                    /* @} */
                    addPreference(mStorageLow);
                } else if (mStorageLow != null) {
                    removePreference(mStorageLow);
                    mStorageLow = null;
                }
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * set prefences enable or not.
     * @return
     */
    private void setPrefencesEnable(boolean isEanble) {
        mItemCache.setEnabled(isEanble);
        mItemDownloads.setEnabled(isEanble);
        mItemMusic.setEnabled(isEanble);
        mItemDcim.setEnabled(isEanble);
        mItemApps.setEnabled(isEanble);
        mItemMisc.setEnabled(isEanble);
        mItemOthers.setEnabled(isEanble);
        // SPRD:ADD the video prefence.
        mItemVideo.setEnabled(isEanble);
    }

    public StorageVolume getStorageVolume() {
        return mVolume;
    }

    private void updatePreferencesFromState() {
        if (ActivityManager.isUserAMonkey()) {
            Log.d("StorageVolumePreferenceCategory", "ignoring monkey's attempt to update preferences state");
            if (mMountTogglePreference != null) {
                mMountTogglePreference.setEnabled(false);
            }
            if (mFormatPreference != null) {
                mFormatPreference.setEnabled(false);
            }
            return;
        }
        // Only update for physical volumes
        if (mVolume == null) return;

        mMountTogglePreference.setEnabled(true);

        final String state = mStorageManager.getVolumeState(mVolume.getPath());

        if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            mItemAvailable.setTitle(R.string.memory_available_read_only);
            if (mFormatPreference != null) {
                // SPRD：REMOVE because of useless
                //removePreference(mFormatPreference);
                // SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917
                mFormatPreference.setEnabled(false);
            }
        } else {
            mItemAvailable.setTitle(R.string.memory_available);
        }

        if (Environment.MEDIA_MOUNTED.equals(state)
                || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)
                // SPRD:ADD for the 4.4
                || Environment.MEDIA_NOFS.equals(state)) {
            // SPRD： add for if phone is calling stat,not allow unmount sd card
            TelephonyManager mTeleMgr = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
            if (mTeleMgr.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                mMountTogglePreference.setEnabled(false);
            } else {
                mMountTogglePreference.setEnabled(true);
            }
            mMountTogglePreference.setTitle(mResources.getString(R.string.sd_eject));
            mMountTogglePreference.setSummary(mResources.getString(R.string.sd_eject_summary));
            /* SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917 @{ */
            if (mFormatPreference != null) {
                mFormatPreference.setEnabled(true);
            }
            /* @} */
        } else {
            if (Environment.MEDIA_UNMOUNTED.equals(state) || Environment.MEDIA_NOFS.equals(state)
                    || Environment.MEDIA_UNMOUNTABLE.equals(state)) {
                mMountTogglePreference.setEnabled(true);
                mMountTogglePreference.setTitle(mResources.getString(R.string.sd_mount));
                mMountTogglePreference.setSummary(mResources.getString(R.string.sd_mount_summary));
            } else {
                mMountTogglePreference.setEnabled(false);
                mMountTogglePreference.setTitle(mResources.getString(R.string.sd_mount));
                mMountTogglePreference.setSummary(mResources.getString(R.string.sd_insert_summary));
            }

            /* SPRD: REMOVE because we do not need remove them @{
            removePreference(mUsageBarPreference);
            removePreference(mItemTotal);
            removePreference(mItemAvailable);
            @} */
            if (mFormatPreference != null) {
                // SPRD：REMOVE because of useless
                //removePreference(mFormatPreference);
                // SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917
                if (Environment.MEDIA_UNMOUNTED.equals(state)) {
                    mFormatPreference.setEnabled(true);
                } else {
                    mFormatPreference.setEnabled(false);
                }
            }
        }

        if (mUsbConnected && (UsbManager.USB_FUNCTION_MTP.equals(mUsbFunction) ||
                UsbManager.USB_FUNCTION_PTP.equals(mUsbFunction))) {
            mMountTogglePreference.setEnabled(false);
            if (Environment.MEDIA_MOUNTED.equals(state)
                    || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                mMountTogglePreference.setSummary(
                        mResources.getString(R.string.mtp_ptp_mode_summary));
            }

            if (mFormatPreference != null) {
                mFormatPreference.setEnabled(false);
                mFormatPreference.setSummary(mResources.getString(R.string.mtp_ptp_mode_summary));
            }
        }/* SPRD: REMOVE because of useless @{
          else if (mFormatPreference != null) {
            mFormatPreference.setEnabled(true);
            mFormatPreference.setSummary(mResources.getString(R.string.sd_format_summary));
        }
        @} */
    }

    public void updateApproximate(long totalSize, long availSize) {
        mItemTotal.setSummary(formatSize(totalSize));
        mItemAvailable.setSummary(formatSize(availSize));

        mTotalSize = totalSize;

        final long usedSize = totalSize - availSize;

        mUsageBarPreference.clear();
        mUsageBarPreference.addEntry(0, usedSize / (float) totalSize, android.graphics.Color.GRAY);
        mUsageBarPreference.commit();
        // SPRD:ADD if the external sdcard is not mounted,remove the prefences.
        if ((null != mVolume) && (!Environment.getExternalStoragePathState().equals(
                Environment.MEDIA_MOUNTED))) {
            if ((0 == totalSize) && (0 == availSize)) {
                removePreference(mItemTotal);
                removePreference(mItemAvailable);
                removePreference(mUsageBarPreference);
            }
        }
        updatePreferencesFromState();
    }

    private static long totalValues(HashMap<String, Long> map, String... keys) {
        long total = 0;
        for (String key : keys) {
            if (map.containsKey(key)) {
                total += map.get(key);
            }
        }
        return total;
    }

    public void updateDetails(MeasurementDetails details) {

        // SPRD:ADD reCalculate the total size by now items size.
        mUsageBarPreference.clear();

        mItemTotal.setSummary(formatSize(details.totalSize));
        mItemAvailable.setSummary(formatSize(details.availSize));
        // SPRD:ADD get the other type files size and update the prefence.
        final long othersSize = getOtherTypesFilesSize(details);
        // SPRD:ADD For internal storage, the nand storage flash_type, caculate
        // the size by the ratio.
         if ((null == mVolume)
                 && (1 == SystemProperties.getInt("ro.storage.flash_type", 0))) {
             setCompressionRatio(details, othersSize);
         }
         /* SPRD: modify for Bug912340. @{ */
        updatePreference(mItemOthers, othersSize + details.appsSize);
        // updatePreference(mItemApps, details.appsSize);
        /* @} */

        // SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917
        final long dcimSize = totalValues(details.mediaSize, StorageMeasurement.DCIM);
        updatePreference(mItemDcim, dcimSize);

        // SPRD:ADD the video prefence.
        final long videoSize = totalValues(details.mediaSize, StorageMeasurement.VIDEO);
        updatePreference(mItemVideo, videoSize);

        // SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917
        final long musicSize = totalValues(details.mediaSize, StorageMeasurement.MUSIC);
        updatePreference(mItemMusic, musicSize);

        // SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917
        final long downloadsSize = totalValues(details.mediaSize, StorageMeasurement.DOWNLOAD);
        updatePreference(mItemDownloads, downloadsSize);

        updatePreference(mItemCache, details.cacheSize);
        updatePreference(mItemMisc, details.miscSize);

        for (StorageItemPreference userPref : mItemUsers) {
            final long userSize = details.usersSize.get(userPref.userHandle);
            updatePreference(userPref, userSize);
        }

        mUsageBarPreference.commit();
    }

    private void updatePreference(StorageItemPreference pref, long size) {
        // SPRD:ADD For internal storage, the nand storage flash_type, caculate
        // the size by the ratio.
        if ((null == mVolume)
                && (1 == SystemProperties.getInt("ro.storage.flash_type", 0))) {
            size *= mCompressionRatio;
        }
        if (size > 0) {
            pref.setSummary(formatSize(size));
            final int order = pref.getOrder();
            mUsageBarPreference.addEntry(order, size / (float) mTotalSize, pref.color);
        } else {
            removePreference(pref);
        }
    }

    private void measure() {
        mMeasure.invalidate();
        mMeasure.measure();
    }

    public void onResume() {
        mMeasure.setReceiver(mReceiver);
        measure();
    }

    public void onStorageStateChanged() {
        init();
        measure();
    }

    public void onUsbStateChanged(boolean isUsbConnected, String usbFunction) {
        mUsbConnected = isUsbConnected;
        mUsbFunction = usbFunction;
        measure();
    }

    public void onMediaScannerFinished() {
        measure();
    }

    public void onCacheCleared() {
        measure();
    }

    public void onPause() {
        mMeasure.cleanUp();
    }

    private String formatSize(long size) {
        return Formatter.formatFileSize(getContext(), size);
    }

    private MeasurementReceiver mReceiver = new MeasurementReceiver() {
        @Override
        public void updateApproximate(StorageMeasurement meas, long totalSize, long availSize) {
            mUpdateHandler.obtainMessage(MSG_UI_UPDATE_APPROXIMATE, new long[] {
                    totalSize, availSize }).sendToTarget();
        }

        @Override
        public void updateDetails(StorageMeasurement meas, MeasurementDetails details) {
            mUpdateHandler.obtainMessage(MSG_UI_UPDATE_DETAILS, details).sendToTarget();
        }
    };

    public boolean mountToggleClicked(Preference preference) {
        return preference == mMountTogglePreference;
    }

    /**
     * SPRD：ADD to get mFormatPreference @{
     */
    public Preference getFormatPreference(){
        return mFormatPreference;
    }
    /** @} */

    public Intent intentForClick(Preference pref) {
        Intent intent = null;

        // TODO The current "delete" story is not fully handled by the respective applications.
        // When it is done, make sure the intent types below are correct.
        // If that cannot be done, remove these intents.
        final String key = pref.getKey();
        if (pref == mFormatPreference) {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setClass(getContext(), com.android.settings.MediaFormat.class);
            intent.putExtra(StorageVolume.EXTRA_STORAGE_VOLUME, mVolume);
            // SPRD：ADD to distinguish if the storage is internal
            intent.putExtra("storage_type", mIsInternalStorage);
        } else if (pref == mItemApps) {
            intent = new Intent(Intent.ACTION_MANAGE_PACKAGE_STORAGE);
            intent.setClass(getContext(),
                    com.android.settings.Settings.ManageApplicationsActivity.class);
        } else if (pref == mItemDownloads) {
            intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).putExtra(
                    DownloadManager.INTENT_EXTRAS_SORT_BY_SIZE, true);
        } else if (pref == mItemMusic) {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/mp3");
            // SPRD：ADD for Settings porting from 4.1 to 4.3
            intent.putExtra(HIDE_BOTTOM_BUTTON, true);
        } else if (pref == mItemDcim) {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            // TODO Create a Videos category, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            intent.setData(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        // SPRD:ADD the video prefence click.
        } else if(pref == mItemVideo) {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            intent.setType("vnd.android.cursor.dir/video");
        } else if (pref == mItemMisc) {
            Context context = getContext().getApplicationContext();
            intent = new Intent(context, MiscFilesHandler.class);
            intent.putExtra(StorageVolume.EXTRA_STORAGE_VOLUME, mVolume);
        } else if (pref == mItemOthers) {
            // SPRD:ADD if click,then show the files.
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/mp3");
            intent.putExtra(HIDE_BOTTOM_BUTTON, true);
        }

        return intent;
    }

    public static class PreferenceHeader extends Preference {
        public PreferenceHeader(Context context, int titleRes) {
            super(context, null, com.android.internal.R.attr.preferenceCategoryStyle);
            setTitle(titleRes);
        }

        public PreferenceHeader(Context context, CharSequence title) {
            super(context, null, com.android.internal.R.attr.preferenceCategoryStyle);
            setTitle(title);
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    }

    /**
     * Return list of other users, excluding the current user.
     */
    private List<UserInfo> getUsersExcluding(UserInfo excluding) {
        final List<UserInfo> users = mUserManager.getUsers();
        final Iterator<UserInfo> i = users.iterator();
        while (i.hasNext()) {
            if (i.next().id == excluding.id) {
                i.remove();
            }
        }
        return users;
    }

    /**
     * SPRD:ADD get the other types files.
     */
    private long getOtherTypesFilesSize(MeasurementDetails details) {

        long othersSize = 0l;
        if (mIsCaculateTatal) {
            long dcimSize = totalValues(details.mediaSize,
                    StorageMeasurement.DCIM);
            long musicSize = totalValues(details.mediaSize,
                    StorageMeasurement.MUSIC);
            // SPRD:ADD the video size.
            long videoSize = totalValues(details.mediaSize,
                    StorageMeasurement.VIDEO);
            long downloadsSize = totalValues(details.mediaSize,
                    StorageMeasurement.DOWNLOAD);

            long userSize = 0l;
            for (StorageItemPreference userPref : mItemUsers) {
                userSize += details.usersSize.get(userPref.userHandle);
            }

            othersSize = mTotalSize - dcimSize - videoSize - musicSize
                    - downloadsSize - userSize - details.appsSize
                    - details.availSize - details.cacheSize - details.miscSize;
        }

        if (othersSize > 0) {
            return othersSize;
        } else {
            return 0l;
        }
    }

    /**
     * SPRD:ADD reset the compression ratio.
     */
    private void setCompressionRatio(MeasurementDetails details,
            final long otherTypesFilesSize) {
        if (otherTypesFilesSize > 0) {
            return;
        }
        long dcimSize = totalValues(details.mediaSize, StorageMeasurement.DCIM);
        long musicSize = totalValues(details.mediaSize,
                StorageMeasurement.MUSIC);
        long videoSize = totalValues(details.mediaSize,
                StorageMeasurement.VIDEO);
        long downloadsSize = totalValues(details.mediaSize,
                StorageMeasurement.DOWNLOAD);

        long userSize = 0l;
        for (StorageItemPreference userPref : mItemUsers) {
            userSize += details.usersSize.get(userPref.userHandle);
        }

        mCompressionRatio = ((mTotalSize - details.availSize) / (float) (details.appsSize
                + details.cacheSize
                + details.miscSize
                + otherTypesFilesSize
                + dcimSize + musicSize + videoSize + downloadsSize + userSize));
    }
}
