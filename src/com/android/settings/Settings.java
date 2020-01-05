/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.nfc.NfcAdapter;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ListView;

import com.android.internal.util.ArrayUtils;
import com.android.settings.accounts.AuthenticatorHelper;
import com.android.settings.applications.AppOpsSummary;
import com.android.settings.applications.InstalledAppDetails;
import com.android.settings.applications.ManageApplications;
import com.android.settings.applications.MouseControlerFragment;
import com.android.settings.applications.ProcessStatsUi;
import com.android.settings.bluetooth.BluetoothEnabler;
import com.android.settings.bluetooth.BluetoothSettings;
import com.android.settings.deviceinfo.Memory;
import com.android.settings.deviceinfo.UsbSettings;
import com.android.settings.fuelgauge.BatteryHistoryDetail;
import com.android.settings.fuelgauge.PowerUsageDetail;
import com.android.settings.fuelgauge.PowerUsageSummary;
import com.android.settings.inputmethod.InputMethodAndLanguageSettings;
import com.android.settings.inputmethod.KeyboardLayoutPickerFragment;
import com.android.settings.inputmethod.SpellCheckersSettings;
import com.android.settings.inputmethod.UserDictionaryList;
import com.android.settings.location.LocationSettings;
import com.android.settings.sim.SimSettings;
import com.android.settings.tts.TextToSpeechSettings;
import com.android.settings.vpn2.VpnSettings;
import com.android.settings.wfd.WifiDisplaySettings;
import com.android.settings.wifi.AdvancedWifiSettings;
import com.android.settings.wifi.HotspotSettings;
import com.android.settings.wifi.HotspotEnabler;
import com.android.settings.wifi.WifiEnabler;
import com.android.settings.wifi.WifiSettings;
import com.android.settings.wifi.p2p.WifiP2pSettings;
import com.android.settings.deviceinfo.PhoneMemory;
import com.android.settings.deviceinfo.SdCardMemory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v4.view.PagerAdapter;

import com.sprd.android.support.featurebar.FeatureBarHelper;
/**
 * Top-level settings activity to handle single pane and double pane UI layout.
 */
public class Settings extends PreferenceActivity
        implements ButtonBarHandler, OnAccountsUpdateListener {

    private static final String LOG_TAG = "Settings";

    private static final String META_DATA_KEY_HEADER_ID =
        "com.android.settings.TOP_LEVEL_HEADER_ID";
    private static final String META_DATA_KEY_FRAGMENT_CLASS =
        "com.android.settings.FRAGMENT_CLASS";
    private static final String META_DATA_KEY_PARENT_TITLE =
        "com.android.settings.PARENT_FRAGMENT_TITLE";
    private static final String META_DATA_KEY_PARENT_FRAGMENT_CLASS =
        "com.android.settings.PARENT_FRAGMENT_CLASS";

    private static final String EXTRA_UI_OPTIONS = "settings:ui_options";

    private static final String SAVE_KEY_CURRENT_HEADER = "com.android.settings.CURRENT_HEADER";
    private static final String SAVE_KEY_PARENT_HEADER = "com.android.settings.PARENT_HEADER";

    // if we do not support backup,the text "backup and reset" change.
    private static final String GSETTINGS_PROVIDER = "com.google.settings";
    public static boolean UNIVERSEUI_SUPPORT = SystemProperties.getBoolean("universe_ui_support",false);
    public static final boolean CU_SUPPORT = SystemProperties.get("ro.operator").equals("cucc");

    private static final boolean WCN_DISABLED = SystemProperties.get("ro.wcn").equals("disabled");

    static final int DIALOG_ONLY_ONE_HOME = 1;
    public FeatureBarHelper mHelperBar;

    private static boolean sShowNoHomeNotice = false;
    public static boolean PIKEL_UI_SUPPORT = SystemProperties.getBoolean("pikel_ui_support",true);
    private static final boolean LOCATION_GPS_ENABLED = SystemProperties.get("persist.sys.location.gps").equals("enabled");
    private static final boolean LOCATION_NETWORK_ENABLED = SystemProperties.get("persist.sys.location.network").equals("enabled");

    private String mFragmentClass;
    private int mTopLevelHeaderId;
    private Header mFirstHeader;
    private Header mCurrentHeader;
    private Header mParentHeader;
    private boolean mInLocalHeaderSwitch;

    public static LayoutInflater mInflater;
    // Show only these settings for restricted users
    private int[] SETTINGS_FOR_RESTRICTED = {
            R.id.wireless_section,
            R.id.wifi_settings,
            R.id.bluetooth_settings,
            R.id.data_usage_settings,
            R.id.wireless_settings,
            R.id.sound_settings,
            R.id.display_settings,
            R.id.storage_settings,
            R.id.application_settings,
            R.id.battery_settings,
            R.id.personal_section,
            R.id.location_settings,
            R.id.security_settings,
            R.id.language_settings,
            R.id.system_section,
            R.id.date_time_settings,
            R.id.about_settings,
            R.id.mouse_control_settings,
           // WirelessSettingsActivity.class.getName(),
    };

    private SharedPreferences mDevelopmentPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener mDevelopmentPreferencesListener;

    // TODO: Update Call Settings based on airplane mode state.

    protected HashMap<Integer, Integer> mHeaderIndexMap = new HashMap<Integer, Integer>();

    private AuthenticatorHelper mAuthenticatorHelper;
    private Header mLastHeader;
    private boolean mListeningToAccountUpdates;

    private boolean mBatteryPresent = true;
    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                boolean batteryPresent = Utils.isBatteryPresent(intent);

                if (mBatteryPresent != batteryPresent) {
                    mBatteryPresent = batteryPresent;
                    invalidateHeaders();
                }
            }
        }
    };

    private boolean mVoiceCapable;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mVoiceCapable = getResources().getBoolean(com.android.internal.R.bool.config_voice_capable);
        if (getIntent().hasExtra(EXTRA_UI_OPTIONS)) {
            getWindow().setUiOptions(getIntent().getIntExtra(EXTRA_UI_OPTIONS, 0));
        }

        mAuthenticatorHelper = new AuthenticatorHelper();
        mAuthenticatorHelper.updateAuthDescriptions(this);
        mAuthenticatorHelper.onAccountsUpdated(this, null);

        mDevelopmentPreferences = getSharedPreferences(DevelopmentSettings.PREF_FILE,
                Context.MODE_PRIVATE);

        getMetaData();
        mInLocalHeaderSwitch = true;
        super.onCreate(savedInstanceState);
        mInLocalHeaderSwitch = false;
        /**
     * SPRD:Optimization to erase the animation on click. @{
    */
        ListView list = getListView();
        // list.setSelector(R.drawable.list_selector_holo_dark);
    /** @} */
        if (!onIsHidingHeaders() && onIsMultiPane()) {
            highlightHeader(mTopLevelHeaderId);
            // Force the title so that it doesn't get overridden by a direct launch of
            // a specific settings screen.
            setTitle(R.string.settings_label);
        }
        //SPRD:616934 Add softKey fotr settings
        final Intent intent = getIntent();
        final String initialFragmentName = intent.getStringExtra(EXTRA_SHOW_FRAGMENT);
        if (!SimSettings.class.getName().equals(initialFragmentName) && !WifiSettings.class.getName().equals(initialFragmentName)) {
            setSoftKey(initialFragmentName);
        }
        // Retrieve any saved state
        if (savedInstanceState != null) {
            mCurrentHeader = savedInstanceState.getParcelable(SAVE_KEY_CURRENT_HEADER);
            mParentHeader = savedInstanceState.getParcelable(SAVE_KEY_PARENT_HEADER);
        }

        // If the current header was saved, switch to it
        if (savedInstanceState != null && mCurrentHeader != null) {
            //switchToHeaderLocal(mCurrentHeader);
            showBreadCrumbs(mCurrentHeader.title, null);
        }
        if (mParentHeader != null) {
            setParentTitle(mParentHeader.title, null, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchToParent(mParentHeader.fragment);
                }
            });
        }

        /* SPRD: Modify for bug751354. @{ */
        getActionBar().setDisplayHomeAsUpEnabled(false);
        getActionBar().setHomeButtonEnabled(false);
        //yanbing add WifiSetting 20180903 start
        getActionBar().hide();
        //yanbing add WifiSetting 20180903 end
        /* @} */

        if (LocationSettings.class.getName().equals(initialFragmentName) && (!LOCATION_GPS_ENABLED && !LOCATION_NETWORK_ENABLED)) {
          Log.d(LOG_TAG, "finish LocationSettings because location disabled");
          finish();
        }
    }

    /* SPRD:616934 Add softKey fotr settings @{ */
    private void setSoftKey(String initialFragmentName) {
        mHelperBar = new FeatureBarHelper(this);
        ViewGroup vg = mHelperBar.getFeatureBar();
        if (vg != null) {
            View option = mHelperBar.getOptionsKeyView();
            vg.removeView(option);

            for (int i = 0; i < HAS_OPTION_FRAGMENTS.length; i++) {
                if (HAS_OPTION_FRAGMENTS[i].equals(initialFragmentName)) {
                    vg.addView(option);
                }
            }
            /* SPRD: add for bug631793 @{ */
            for (int i = 0; i < SPECIAL_HELPER_BAR_FRAGMENTS.length; i++) {
                if (SPECIAL_HELPER_BAR_FRAGMENTS[i].equals(initialFragmentName)) {
                    vg.setVisibility(View.GONE);
                }
            }
            /* @} */
        }
    }

    /* SPRD: add for bug631793 @{ */
    private static final String[] SPECIAL_HELPER_BAR_FRAGMENTS = {
        MasterClearConfirm.class.getName(),
        MasterClear.class.getName(),
        // SPRD: Modify for bug690340.
        DataUsageSummary.class.getName(),
        // SPRD: Add for bug666971.
        PowerUsageSummary.class.getName(),
        // SPRD: Add for bug673073.
        BatteryHistoryDetail.class.getName(),
        PowerUsageDetail.class.getName()
    };/* @} */

    private static final String[] HAS_OPTION_FRAGMENTS = {
        BluetoothSettings.class.getName(),
        WifiSettings.class.getName(),
        ManageApplications.class.getName(),
        InstalledAppDetails.class.getName(),
        ZonePicker.class.getName(),
        ProcessStatsUi.class.getName(),
    };
    /* @} */

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save the current fragment, if it is the same as originally launched
        if (mCurrentHeader != null) {
            outState.putParcelable(SAVE_KEY_CURRENT_HEADER, mCurrentHeader);
        }
        if (mParentHeader != null) {
            outState.putParcelable(SAVE_KEY_PARENT_HEADER, mParentHeader);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mDevelopmentPreferencesListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                invalidateHeaders();
            }
        };
        mDevelopmentPreferences.registerOnSharedPreferenceChangeListener(
                mDevelopmentPreferencesListener);
        // SPRD:ADD register receiver.
        ListAdapter listAdapter = getListAdapter();
        if (listAdapter instanceof HeaderAdapter) {
            // add for tab style
            ((HeaderAdapter) listAdapter).flushViewCache();
            // add for tab style
            ((HeaderAdapter) listAdapter).resume();
        }
        // SPRD:ADD update Header
        invalidateHeaders();

        registerReceiver(mBatteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

    }

    @Override
    public void onPause() {
        super.onPause();

        unregisterReceiver(mBatteryInfoReceiver);

        ListAdapter listAdapter = getListAdapter();
        if (listAdapter instanceof HeaderAdapter) {
            ((HeaderAdapter) listAdapter).pause();
        }

        mDevelopmentPreferences.unregisterOnSharedPreferenceChangeListener(
                mDevelopmentPreferencesListener);
        mDevelopmentPreferencesListener = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onIsMultiPane() {
        return false;
    }

    private static final String[] ENTRY_FRAGMENTS = {
        WirelessSettings.class.getName(),
        WifiSettings.class.getName(),
        AdvancedWifiSettings.class.getName(),
        BluetoothSettings.class.getName(),
        TetherSettings.class.getName(),
        HotspotSettings.class.getName(),
        WifiP2pSettings.class.getName(),
        VpnSettings.class.getName(),
        DateTimeSettings.class.getName(),
        LocalePicker.class.getName(),
        InputMethodAndLanguageSettings.class.getName(),
        SpellCheckersSettings.class.getName(),
        UserDictionaryList.class.getName(),
        UserDictionarySettings.class.getName(),
        SoundSettings.class.getName(),
        DisplaySettings.class.getName(),
        SimSettings.class.getName(),
        DeviceInfoSettings.class.getName(),
        ManageApplications.class.getName(),
        ProcessStatsUi.class.getName(),
        NotificationStation.class.getName(),
        LocationSettings.class.getName(),
        SecuritySettings.class.getName(),
        PrivacySettings.class.getName(),
        DeviceAdminSettings.class.getName(),
        TextToSpeechSettings.class.getName(),
        Memory.class.getName(),
        DevelopmentSettings.class.getName(),
        UsbSettings.class.getName(),
        WifiDisplaySettings.class.getName(),
        PowerUsageSummary.class.getName(),
        CryptKeeperSettings.class.getName(),
        DataUsageSummary.class.getName(),
        DataUsageSummaryEx.class.getName(),
        DreamSettings.class.getName(),
        NotificationAccessSettings.class.getName(),
        TrustedCredentialsSettings.class.getName(),
        KeyboardLayoutPickerFragment.class.getName(),
        MouseControlerFragment.class.getName(), //SPRD: feature  support mouse controls in Settings
        WifiCallingSettings.class.getName()
    };

    @Override
    protected boolean isValidFragment(String fragmentName) {
        // Almost all fragments are wrapped in this,
        // except for a few that have their own activities.
        for (int i = 0; i < ENTRY_FRAGMENTS.length; i++) {
            if (ENTRY_FRAGMENTS[i].equals(fragmentName)) return true;
        }
        return false;
    }

    private void switchToHeaderLocal(Header header) {
        mInLocalHeaderSwitch = true;
        switchToHeader(header);
        mInLocalHeaderSwitch = false;
    }

    @Override
    public void switchToHeader(Header header) {
        if (!mInLocalHeaderSwitch) {
            mCurrentHeader = null;
            mParentHeader = null;
        }
        super.switchToHeader(header);
    }

    /**
     * Switch to parent fragment and store the grand parent's info
     * @param className name of the activity wrapper for the parent fragment.
     */
    private void switchToParent(String className) {
        final ComponentName cn = new ComponentName(this, className);
        try {
            final PackageManager pm = getPackageManager();
            final ActivityInfo parentInfo = pm.getActivityInfo(cn, PackageManager.GET_META_DATA);

            if (parentInfo != null && parentInfo.metaData != null) {
                String fragmentClass = parentInfo.metaData.getString(META_DATA_KEY_FRAGMENT_CLASS);
                CharSequence fragmentTitle = parentInfo.loadLabel(pm);
                Header parentHeader = new Header();
                parentHeader.fragment = fragmentClass;
                parentHeader.title = fragmentTitle;
                mCurrentHeader = parentHeader;

                switchToHeaderLocal(parentHeader);
                highlightHeader(mTopLevelHeaderId);

                mParentHeader = new Header();
                mParentHeader.fragment
                        = parentInfo.metaData.getString(META_DATA_KEY_PARENT_FRAGMENT_CLASS);
                mParentHeader.title = parentInfo.metaData.getString(META_DATA_KEY_PARENT_TITLE);
            }
        } catch (NameNotFoundException nnfe) {
            Log.w(LOG_TAG, "Could not find parent activity : " + className);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // If it is not launched from history, then reset to top-level
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            if (mFirstHeader != null && !onIsHidingHeaders() && onIsMultiPane()) {
                switchToHeaderLocal(mFirstHeader);
            }
            getListView().setSelectionFromTop(0, 0);
        }
    }

    private void highlightHeader(int id) {
        if (id != 0) {
            Integer index = mHeaderIndexMap.get(id);
            if (index != null) {
                getListView().setItemChecked(index, true);
                if (isMultiPane()) {
                    getListView().smoothScrollToPosition(index);
                }
            }
        }
    }

    @Override
    public Intent getIntent() {
        Intent superIntent = super.getIntent();
        String startingFragment = getStartingFragmentClass(superIntent);
        // This is called from super.onCreate, isMultiPane() is not yet reliable
        // Do not use onIsHidingHeaders either, which relies itself on this method
        if (startingFragment != null && !onIsMultiPane()) {
            Intent modIntent = new Intent(superIntent);
            modIntent.putExtra(EXTRA_SHOW_FRAGMENT, startingFragment);
            Bundle args = superIntent.getExtras();
            if (args != null) {
                args = new Bundle(args);
            } else {
                args = new Bundle();
            }
            args.putParcelable("intent", superIntent);
            modIntent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, superIntent.getExtras());
            return modIntent;
        }
        return superIntent;
    }

    /**
     * Checks if the component name in the intent is different from the Settings class and
     * returns the class name to load as a fragment.
     */
    protected String getStartingFragmentClass(Intent intent) {
        if (mFragmentClass != null) return mFragmentClass;

        String intentClass = intent.getComponent().getClassName();
        if (intentClass.equals(getClass().getName())) return null;

        if ("com.android.settings.ManageApplications".equals(intentClass)
                || "com.android.settings.RunningServices".equals(intentClass)
                || "com.android.settings.applications.StorageUse".equals(intentClass)) {
            // Old names of manage apps.
            intentClass = com.android.settings.applications.ManageApplications.class.getName();
        }

        return intentClass;
    }

    /**
     * Override initial header when an activity-alias is causing Settings to be launched
     * for a specific fragment encoded in the android:name parameter.
     */
    @Override
    public Header onGetInitialHeader() {
        String fragmentClass = getStartingFragmentClass(super.getIntent());
        if (fragmentClass != null) {
            Header header = new Header();
            header.fragment = fragmentClass;
            header.title = getTitle();
            header.fragmentArguments = getIntent().getExtras();
            mCurrentHeader = header;
            return header;
        }

        return mFirstHeader;
    }

    @Override
    public Intent onBuildStartFragmentIntent(String fragmentName, Bundle args,
            int titleRes, int shortTitleRes) {
        Intent intent = super.onBuildStartFragmentIntent(fragmentName, args,
                titleRes, shortTitleRes);

        // Some fragments want split ActionBar; these should stay in sync with
        // uiOptions for fragments also defined as activities in manifest.
        if (WifiSettings.class.getName().equals(fragmentName) ||
                WifiP2pSettings.class.getName().equals(fragmentName) ||
                BluetoothSettings.class.getName().equals(fragmentName) ||
                DreamSettings.class.getName().equals(fragmentName) ||
                LocationSettings.class.getName().equals(fragmentName)) {
            intent.putExtra(EXTRA_UI_OPTIONS, ActivityInfo.UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW);
        }

        if (DataUsageSummary.class.getName().equals(fragmentName)) {
            intent.setClass(this, DataUsageSummaryActivity.class);
        } else if(BluetoothSettings.class.getName().equals(fragmentName)) {
            intent.setClass(this, BluetoothSettingsActivity.class);
        } else {
            intent.setClass(this, SubSettings.class);
        }
        return intent;
    }

    /**
     * Populate the activity with the top-level headers.
     */
    @Override
    public void onBuildHeaders(List<Header> headers) {
        if (!onIsHidingHeaders()) {
            /* SPRD: changed for tab style @{ */
            if (UNIVERSEUI_SUPPORT && !PIKEL_UI_SUPPORT) {
                ListAdapter listAdapter = getListAdapter();
               // loadHeadersFromResource(mHeadersCategory, headers);
                if (listAdapter instanceof HeaderAdapter) {
                    ((HeaderAdapter) listAdapter).flushViewCache();
                    ((HeaderAdapter) listAdapter).resume();
                    ((HeaderAdapter) listAdapter).notifyDataSetChanged();
                }
                /* @} */
            } else {
                loadHeadersFromResource(R.xml.settings_headers, headers);
            }
        }
        updateHeaderList(headers);
    }

    private void updateHeaderList(List<Header> target) {
        final boolean showDev = mDevelopmentPreferences.getBoolean(
                DevelopmentSettings.PREF_SHOW,
                android.os.Build.TYPE.equals("eng"));
        int i = 0;
        boolean IsSupVoice = Settings.this.getResources().getBoolean(com.android.internal.R.bool.
                config_voice_capable);
        final UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
        mHeaderIndexMap.clear();
        while (i < target.size()) {
            Header header = target.get(i);
            // Ids are integers, so downcasting
            int id = (int) header.id;
            if (id == R.id.operator_settings || id == R.id.manufacturer_settings) {
                Utils.updateHeaderToSpecificActivityFromMetaDataOrRemove(this, target, header);
            } else if (id == R.id.wifi_settings) {
                // Remove WiFi Settings if WiFi service is not available.
                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)
                        || WCN_DISABLED) {
                    target.remove(i);
                }
            } else if (id == R.id.bluetooth_settings) {
                // Remove Bluetooth Settings if Bluetooth service is not available.
                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
                        || WCN_DISABLED) {
                    target.remove(i);
                }
            } else if (id == R.id.data_usage_settings) {
                // Remove data usage when kernel module not enabled
                final INetworkManagementService netManager = INetworkManagementService.Stub
                        .asInterface(ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE));
               try {
                    /* SPRD���������ADD to delete the data usage item of settings @{ */
                boolean support_cmcc = SystemProperties.get("ro.operator").equals("cmcc");
                    if (!netManager.isBandwidthControlEnabled() || support_cmcc) {
                        target.remove(i);
                    }
                } catch (RemoteException e) {
                    // ignored
                }
            } else if (id == R.id.battery_settings) {
                // Remove battery settings when battery is not available. (e.g. TV)

                if (!mBatteryPresent) {
                    target.remove(i);
                }
                /* @} */
            } else if (id == R.id.development_settings) {
                if (!showDev) {
                    target.remove(i);
                }
            }
            /* SPRD: modified for cucc feature @{ */
            else if (id == R.id.network_preference_settings) {
                //if (!("cucc".equals(WifiManager.SUPPORT_VERSION))) {
                if (!CU_SUPPORT || PIKEL_UI_SUPPORT) { //modify for CUCC support  2013-11-22
                    target.remove(header);
                }
            }
            /* @} */
            /* SPRD: add AudioProfile  @{ */
            else if (id == R.id.sound_settings && IsSupVoice)
            {
                target.remove(header);
            }
            else if (id == R.id.audio_profiles && !IsSupVoice)
            {
                target.remove(header);
            }
            /* @} */
            /* SPRD: for multi-sim @{ */
            else if (id == R.id.sim_settings) {
                if (!TelephonyManager.isMultiSim()||(!mVoiceCapable)) {
                    target.remove(header);
                }
            } else if(id == R.id.location_settings){
                if((!LOCATION_GPS_ENABLED && !LOCATION_NETWORK_ENABLED) || WCN_DISABLED) {
                    target.remove(i);
                }
            }
            if (i < target.size() && target.get(i) == header
                    && UserHandle.MU_ENABLED && UserHandle.myUserId() != 0
                    && !ArrayUtils.contains(SETTINGS_FOR_RESTRICTED, id)) {
                target.remove(i);
            }

            // Increment if the current one wasn't removed by the Utils code.
            if (i < target.size() && target.get(i) == header) {
                // Hold on to the first header, when we need to reset to the top-level
                if (mFirstHeader == null &&
                        HeaderAdapter.getHeaderType(header) != HeaderAdapter.HEADER_TYPE_CATEGORY) {
                    mFirstHeader = header;
                }
                mHeaderIndexMap.put(id, i);
                i++;
            }
        }
    }

    private void getMetaData() {
        try {
            ActivityInfo ai = getPackageManager().getActivityInfo(getComponentName(),
                    PackageManager.GET_META_DATA);
            if (ai == null || ai.metaData == null) return;
            mTopLevelHeaderId = ai.metaData.getInt(META_DATA_KEY_HEADER_ID);
            mFragmentClass = ai.metaData.getString(META_DATA_KEY_FRAGMENT_CLASS);

            // Check if it has a parent specified and create a Header object
            final int parentHeaderTitleRes = ai.metaData.getInt(META_DATA_KEY_PARENT_TITLE);
            String parentFragmentClass = ai.metaData.getString(META_DATA_KEY_PARENT_FRAGMENT_CLASS);
            if (parentFragmentClass != null) {
                mParentHeader = new Header();
                mParentHeader.fragment = parentFragmentClass;
                if (parentHeaderTitleRes != 0) {
                    mParentHeader.title = getResources().getString(parentHeaderTitleRes);
                }
            }
        } catch (NameNotFoundException nnfe) {
            // No recovery
        }
    }

    @Override
    public boolean hasNextButton() {
        return super.hasNextButton();
    }

    @Override
    public Button getNextButton() {
        return super.getNextButton();
    }

    public static class NoHomeDialogFragment extends DialogFragment {
        public static void show(Activity parent) {
            final NoHomeDialogFragment dialog = new NoHomeDialogFragment();
            dialog.show(parent.getFragmentManager(), null);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.only_one_home_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
        }
    }

    private static class HeaderAdapter extends ArrayAdapter<Header> {
        static final int HEADER_TYPE_CATEGORY = 0;
        static final int HEADER_TYPE_NORMAL = 1;
        static final int HEADER_TYPE_SWITCH = 2;
        static final int HEADER_TYPE_BUTTON = 3;
        private static final int HEADER_TYPE_COUNT = HEADER_TYPE_BUTTON + 1;

        private final WifiEnabler mWifiEnabler;
        private final BluetoothEnabler mBluetoothEnabler;
        private final HotspotEnabler mHotspotEnabler;
        private AuthenticatorHelper mAuthHelper;
        private DevicePolicyManager mDevicePolicyManager;

        /* SPRD: add for tab style @{ */
        private View[] mViewCache;
        private int mViewCacheSize = 0;

        /* @} */
        // SPRD: for Bug258772, no instance of class of DevicePolicyManager
        private Context mContext;

        private static class HeaderViewHolder {
            ImageView icon;
            TextView title;
            TextView summary;
            Switch switch_;
            ImageButton button_;
            View divider_;
        }

        private LayoutInflater mInflater;

        static int getHeaderType(Header header) {
            if (header.fragment == null && header.intent == null) {
                return HEADER_TYPE_CATEGORY;
            } /*else if (header.id == R.id.wifi_settings || header.id == R.id.bluetooth_settings) {
                return HEADER_TYPE_SWITCH;
            } */else if (header.id == R.id.security_settings) {
                return HEADER_TYPE_NORMAL;
            } else {
                return HEADER_TYPE_NORMAL;
            }
        }

        @Override
        public int getItemViewType(int position) {
            Header header = getItem(position);
            return getHeaderType(header);
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false; // because of categories
        }

        @Override
        public boolean isEnabled(int position) {
            return getItemViewType(position) != HEADER_TYPE_CATEGORY;
        }

        @Override
        public int getViewTypeCount() {
            return HEADER_TYPE_COUNT;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        public HeaderAdapter(Context context, List<Header> objects,
                AuthenticatorHelper authenticatorHelper, DevicePolicyManager dpm) {
            super(context, 0, objects);

            mAuthHelper = authenticatorHelper;
            if (null == mInflater) {
                mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            }

            // Temp Switches provided as placeholder until the adapter replaces these with actual
            // Switches inflated from their layouts. Must be done before adapter is set in super
            //mWifiEnabler = new WifiEnabler(context, new Switch(context));
            mWifiEnabler = new WifiEnabler(context, new SwitchPreference(context));
            mBluetoothEnabler = new BluetoothEnabler(context, new SwitchPreference(context));
            mHotspotEnabler = new HotspotEnabler(context, new SwitchPreference(context));
            /* SPRD: add for tab style @{ */
            mViewCacheSize = objects.size();
            mViewCache = new View[mViewCacheSize];
            /* @} */
            // SPRD: for Bug258772, no instance of class of DevicePolicyManager
            mContext = context;
        }

        /* SPRD: add for tab style @{ */
        public boolean flushViewCache() {
            int currentCount = getCount();

            mViewCacheSize = currentCount;
            mViewCache = null;
            mViewCache = new View[mViewCacheSize];
            return true;
        }

        /* @} */

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            /* SPRD: add for tab style @{ */
            if (position >= mViewCacheSize) {
                flushViewCache();
            }
            /* @} */
            HeaderViewHolder holder;
            Header header = getItem(position);
            int headerType = getHeaderType(header);
            View view = null;
            /* SPRD: add for tab style @{ */
            convertView = mViewCache[position];
            /* @} */

            if (convertView == null) {
                holder = new HeaderViewHolder();
                switch (headerType) {
                    case HEADER_TYPE_CATEGORY:
                        // SPRD: change for Bug 364010
//                        view = new TextView(getContext(), null,
//                                android.R.attr.listSeparatorTextViewStyle);
                        view = mInflater.inflate(R.layout.preference_header_category, parent,
                                false);
                        holder.title = (TextView)
                                view.findViewById(com.android.internal.R.id.title);
                        break;

                    case HEADER_TYPE_SWITCH:
                        view = mInflater.inflate(R.layout.preference_header_switch_item, parent,
                                false);
                        holder.icon = (ImageView) view.findViewById(R.id.icon);
                        holder.title = (TextView)
                                view.findViewById(com.android.internal.R.id.title);
                        holder.summary = (TextView)
                                view.findViewById(com.android.internal.R.id.summary);
                        holder.switch_ = (Switch) view.findViewById(R.id.switchWidget);
                        break;

                    case HEADER_TYPE_BUTTON:
                        view = mInflater.inflate(R.layout.preference_header_button_item, parent,
                                false);
                        holder.icon = (ImageView) view.findViewById(R.id.icon);
                        holder.title = (TextView)
                                view.findViewById(com.android.internal.R.id.title);
                        holder.summary = (TextView)
                                view.findViewById(com.android.internal.R.id.summary);
                        holder.button_ = (ImageButton) view.findViewById(R.id.buttonWidget);
                        holder.divider_ = view.findViewById(R.id.divider);
                        break;

                    case HEADER_TYPE_NORMAL:
                        view = mInflater.inflate(
                                R.layout.preference_header_item, parent,
                                false);
                        holder.icon = (ImageView) view.findViewById(R.id.icon);
                        holder.title = (TextView)
                                view.findViewById(com.android.internal.R.id.title);
                        holder.summary = (TextView)
                                view.findViewById(com.android.internal.R.id.summary);
                        break;
                }
                view.setTag(holder);
                /* SPRD: add for tab style @{ */
                mViewCache[position] = view;
                /* @} */
            } else {
                view = convertView;
                /* SPRD: changed for tab style @{ */
                 holder = (HeaderViewHolder) view.getTag();
//                return view;
                /* @} */
            }

            // All view fields must be updated every time, because the view may be recycled
            switch (headerType) {
                case HEADER_TYPE_CATEGORY:
                    holder.title.setText(header.getTitle(getContext().getResources()));
                    break;

                case HEADER_TYPE_SWITCH:
                    // Would need a different treatment if the main menu had more switches
                    if (header.id == R.id.wifi_settings) {
                        //mWifiEnabler.setSwitch(holder.switch_);
                    } else {
                       // mBluetoothEnabler.setSwitch(holder.switch_);
                    }
                    updateCommonHeaderView(header, holder);
                    break;

                case HEADER_TYPE_BUTTON:
                    if (header.id == R.id.security_settings) {
                        boolean hasCert = DevicePolicyManager.hasAnyCaCertsInstalled();
                        if (hasCert) {
                            holder.button_.setVisibility(View.VISIBLE);
                            holder.divider_.setVisibility(View.VISIBLE);
                            /* SPRD: for Bug258772, no instance of class of DevicePolicyManager @{*/
                            if(mDevicePolicyManager==null){
                                mDevicePolicyManager = DevicePolicyManager.create(mContext, null);
                            }
                            /* @} */
                            boolean isManaged = mDevicePolicyManager.getDeviceOwner() != null;
                            if (isManaged) {
                                holder.button_.setImageResource(R.drawable.ic_settings_about);
                            } else {
                                holder.button_.setImageResource(
                                        android.R.drawable.stat_notify_error);
                            }
                            holder.button_.setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    Intent intent = new Intent(
                                            android.provider.Settings.ACTION_MONITORING_CERT_INFO);
                                    getContext().startActivity(intent);
                                }
                            });
                        } else {
                            holder.button_.setVisibility(View.GONE);
                            holder.divider_.setVisibility(View.GONE);
                        }
                    }
                    updateCommonHeaderView(header, holder);
                    break;

                case HEADER_TYPE_NORMAL:
                    updateCommonHeaderView(header, holder);
                    break;
            }

            return view;
        }

        private void updateCommonHeaderView(Header header, HeaderViewHolder holder) {
                /* SPRD: Modify for bug751354. @{ */
                // holder.icon.setImageResource(header.iconRes);
                holder.icon.setVisibility(View.GONE);
                /* @} */
                holder.title.setText(header.getTitle(getContext().getResources()));
                // if we do not support backup,the text "backup and reset" change.
                if(header.id == R.id.privacy_settings) {
                    if (getContext().getPackageManager().resolveContentProvider(GSETTINGS_PROVIDER, 0) == null) {
                        holder.title.setText(getContext().getResources().getText(R.string.privacy_settings_title));
                    }
                }
                CharSequence summary = header.getSummary(getContext().getResources());
                if (!TextUtils.isEmpty(summary)) {
                    holder.summary.setVisibility(View.VISIBLE);
                    holder.summary.setText(summary);
                } else {
                    holder.summary.setVisibility(View.GONE);
                }
            }

        private void setHeaderIcon(HeaderViewHolder holder, Drawable icon) {
            ViewGroup.LayoutParams lp = holder.icon.getLayoutParams();
            lp.width = getContext().getResources().getDimensionPixelSize(
                    R.dimen.header_icon_width);
            lp.height = lp.width;
            holder.icon.setLayoutParams(lp);
            holder.icon.setImageDrawable(icon);
        }

        public void resume() {
            mWifiEnabler.resume();
            mBluetoothEnabler.resume();
        }

        public void pause() {
            mWifiEnabler.pause();
            mBluetoothEnabler.pause();
        }
    }

    @Override
    public void onHeaderClick(Header header, int position) {
        boolean revert = false;
        super.onHeaderClick(header, position);

        if (revert && mLastHeader != null) {
            highlightHeader((int) mLastHeader.id);
        } else {
            mLastHeader = header;
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        // Override the fragment title for Wallpaper settings
        int titleRes = pref.getTitleRes();
        if (pref.getFragment().equals(WallpaperTypeSettings.class.getName())) {
            titleRes = R.string.wallpaper_settings_fragment_title;
        } else if (pref.getFragment().equals(OwnerInfoSettings.class.getName())
                && UserHandle.myUserId() != UserHandle.USER_OWNER) {
            if (UserManager.get(this).isLinkedUser()) {
                titleRes = R.string.profile_info_settings_title;
            } else {
                titleRes = R.string.user_info_settings_title;
            }
        }
        startPreferencePanel(pref.getFragment(), pref.getExtras(), titleRes, pref.getTitle(),
                null, 0);
        return true;
    }

    @Override
    public boolean shouldUpRecreateTask(Intent targetIntent) {
        return super.shouldUpRecreateTask(new Intent(this, Settings.class));
    }

    @Override
    public void setListAdapter(ListAdapter adapter) {
        if (adapter == null) {
            super.setListAdapter(null);
        } else {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            super.setListAdapter(new HeaderAdapter(this, getHeaders(), mAuthenticatorHelper, dpm));
        }
    }

    @Override
    public void onAccountsUpdated(Account[] accounts) {
        // TODO: watch for package upgrades to invalidate cache; see 7206643
        mAuthenticatorHelper.updateAuthDescriptions(this);
        mAuthenticatorHelper.onAccountsUpdated(this, accounts);
        invalidateHeaders();
    }

    public static void requestHomeNotice() {
        sShowNoHomeNotice = true;
    }

    /*
     * Settings subclasses for launching independently.
     */
    public static class BluetoothSettingsActivity extends Settings { /* empty */ }
    public static class WirelessSettingsActivity extends Settings { /* empty */ }
    public static class TetherSettingsActivity extends Settings { /* empty */ }
    public static class VpnSettingsActivity extends Settings { /* empty */ }
    public static class DateTimeSettingsActivity extends Settings { /* empty */ }
    public static class StorageSettingsActivity extends Settings { /* empty */ }
    public static class WifiSettingsActivity extends Settings { /* empty */ }
    public static class HotspotSettingsActivity extends Settings { /* empty */ }
    public static class WifiP2pSettingsActivity extends Settings { /* empty */ }
    public static class InputMethodAndLanguageSettingsActivity extends Settings { /* empty */ }
    public static class KeyboardLayoutPickerActivity extends Settings { /* empty */ }
    public static class InputMethodAndSubtypeEnablerActivity extends Settings { /* empty */ }
    public static class SpellCheckersSettingsActivity extends Settings { /* empty */ }
    public static class LocalePickerActivity extends Settings { /* empty */ }
    public static class UserDictionarySettingsActivity extends Settings { /* empty */ }
    public static class SoundSettingsActivity extends Settings { /* empty */ }
    public static class DisplaySettingsActivity extends Settings { /* empty */ }
    public static class DeviceInfoSettingsActivity extends Settings { /* empty */ }
    public static class ApplicationSettingsActivity extends Settings { /* empty */ }
    public static class ManageApplicationsActivity extends Settings { /* empty */ }
    public static class ManageAppSettingsActivity extends Settings { /* empty */ }
    public static class SimSettingsActivity extends Settings { /* empty */ }
    public static class WifiCallingSettingsActivity extends Settings { /* empty */ }
    public static class AppOpsSummaryActivity extends Settings {
        @Override
        public boolean isValidFragment(String className) {
            if (AppOpsSummary.class.getName().equals(className)) {
                return true;
            }
            return super.isValidFragment(className);
        }
    }
    public static class StorageUseActivity extends Settings { /* empty */ }
    public static class DevelopmentSettingsActivity extends Settings { /* empty */ }
    public static class CaptioningSettingsActivity extends Settings { /* empty */ }
    public static class SecuritySettingsActivity extends Settings { /* empty */ }
    public static class LocationSettingsActivity extends Settings { /* empty */ }
    public static class PrivacySettingsActivity extends Settings { /* empty */ }
    public static class RunningServicesActivity extends Settings { /* empty */ }
    public static class PowerUsageSummaryActivity extends Settings { /* empty */ }
    public static class AccountSyncSettingsActivity extends Settings { /* empty */ }
    public static class AccountSyncSettingsInAddAccountActivity extends Settings { /* empty */ }
    public static class CryptKeeperSettingsActivity extends Settings { /* empty */ }
    public static class DeviceAdminSettingsActivity extends Settings { /* empty */ }
    public static class DataUsageSummaryActivity extends Settings { /* empty */ }
    public static class DataUsageSummaryExActivity extends Settings { /* empty */ }
    public static class AdvancedWifiSettingsActivity extends Settings { /* empty */ }
    public static class TextToSpeechSettingsActivity extends Settings { /* empty */ }
    public static class WifiDisplaySettingsActivity extends Settings { /* empty */ }
    public static class DreamSettingsActivity extends Settings { /* empty */ }
    public static class NotificationStationActivity extends Settings { /* empty */ }
    public static class NotificationAccessSettingsActivity extends Settings { /* empty */ }
    public static class UsbSettingsActivity extends Settings { /* empty */ }
    public static class TrustedCredentialsSettingsActivity extends Settings { /* empty */ }
    public static class PrintSettingsActivity extends Settings { /* empty */ }
    public static class PrintJobSettingsActivity extends Settings { /* empty */ }
    /* SPRD: Modify 20140118 Spread of bug269951, TaskManager and ApplicationManager perfomance optimization @{ */
    public static class SprdManageApplicationsActivity extends ManageApplicationsActivity { /* empty */ }
    public static class SprdManageTasksActivity extends ManageApplicationsActivity { /* empty */ }
    /* @} */
    public static class PhoneStorageSettingsActivity extends Settings { /* empty */ }
    public static class SdCardStorageSettingsActivity extends Settings { /* empty */ }
    //SPRD: feature  support mouse controls in Settings
    public static class MouseControlerFragmentActivity extends Settings { /* empty */ }
}
