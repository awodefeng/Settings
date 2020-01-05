/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.wifi;

import static android.net.wifi.WifiConfiguration.INVALID_NETWORK_ID;
import static android.os.UserManager.DISALLOW_CONFIG_WIFI;

import com.android.settings.R;
import com.android.settings.RestrictedSettingsFragment;
import com.android.settings.wifi.p2p.WifiP2pSettings;
import java.util.Comparator;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.*;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

//yanbing add WifiSetings 20190308 start
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.util.DisplayMetrics;
import android.graphics.Color;
import java.util.Timer;
import java.util.TimerTask;
import android.graphics.drawable.Drawable;
//yanbing add WifiSetings 20190308 end

/* SPRD: modified for UUI empty view text color and size @{ */
import android.os.SystemProperties;
import android.content.res.Resources;
/* @} */
/**
 * Two types of UI are provided here.
 *
 * The first is for "usual Settings", appearing as any other Setup fragment.
 *
 * The second is for Setup Wizard, with a simplified interface that hides the action bar
 * and menus.
 */
public class WifiSettings extends RestrictedSettingsFragment
        implements WifiDialog.WifiDialogListener,WifiOptionDialog.WifiOptionDialogListener  {
    private static final String TAG = "WifiSettings";
    private static final int MENU_ID_WPS_PBC = Menu.FIRST;
    private static final int MENU_ID_WPS_PIN = Menu.FIRST + 1;
    private static final int MENU_ID_P2P = Menu.FIRST + 2;
    private static final int MENU_ID_ADD_NETWORK = Menu.FIRST + 3;
    private static final int MENU_ID_ADVANCED = Menu.FIRST + 4;
    private static final int MENU_ID_SCAN = Menu.FIRST + 5;
    private static final int MENU_ID_CONNECT = Menu.FIRST + 6;
    private static final int MENU_ID_FORGET = Menu.FIRST + 7;
    private static final int MENU_ID_MODIFY = Menu.FIRST + 8;
    //add by spreadst_lc for cmcc wifi feature start
    private static final int MENU_ID_TRUSTED_AP = Menu.FIRST + 9;
    private boolean supportCMCC = false;
    //add by spreadst_lc for cmcc wifi feature end


    private boolean mShouldShowWAPICertLostError = false;

    private static final int WIFI_OPTION_DIALOG = 6;
    private static final int WIFI_DIALOG_ID = 1;
    private static final int WPS_PBC_DIALOG_ID = 2;
    private static final int WPS_PIN_DIALOG_ID = 3;
    private static final int WIFI_SKIPPED_DIALOG_ID = 4;
    private static final int WIFI_AND_MOBILE_SKIPPED_DIALOG_ID = 5;

    // Combo scans can take 5-6s to complete - set to 10s.
    private static final int WIFI_RESCAN_INTERVAL_MS = 10 * 1000;

    // Instance state keys
    private static final String SAVE_DIALOG_EDIT_MODE = "edit_mode";
    private static final String SAVE_DIALOG_ACCESS_POINT_STATE = "wifi_ap_state";

    // Activity result when pressing the Skip button
    private static final int RESULT_SKIP = Activity.RESULT_FIRST_USER;

    private final IntentFilter mFilter;
    private final BroadcastReceiver mReceiver;
    private final Scanner mScanner;

    private WifiManager mWifiManager;
    private WifiManager.ActionListener mConnectListener;
    private WifiManager.ActionListener mSaveListener;
    private WifiManager.ActionListener mForgetListener;
    private boolean mP2pSupported;

    private WifiEnabler mWifiEnabler;
    // An access point being editted is stored here.
    private AccessPoint mSelectedAccessPoint;

    private DetailedState mLastState;
    private WifiInfo mLastInfo;

    private final AtomicBoolean mConnected = new AtomicBoolean(false);

    private WifiDialog mDialog;
    private WifiOptionDialog mWifiOptionDialog;

    private TextView mEmptyView;

    /* Used in Wifi Setup context */

    // this boolean extra specifies whether to disable the Next button when not connected
    private static final String EXTRA_ENABLE_NEXT_ON_CONNECT = "wifi_enable_next_on_connect";

    // this boolean extra specifies whether to auto finish when connection is established
    private static final String EXTRA_AUTO_FINISH_ON_CONNECT = "wifi_auto_finish_on_connect";

    // this boolean extra shows a custom button that we can control
    protected static final String EXTRA_SHOW_CUSTOM_BUTTON = "wifi_show_custom_button";

    // show a text regarding data charges when wifi connection is required during setup wizard
    protected static final String EXTRA_SHOW_WIFI_REQUIRED_INFO = "wifi_show_wifi_required_info";

    // this boolean extra is set if we are being invoked by the Setup Wizard
    private static final String EXTRA_IS_FIRST_RUN = "firstRun";

    // should Next button only be enabled when we have a connection?
    private boolean mEnableNextOnConnection;

    // should activity finish once we have a connection?
    private boolean mAutoFinishOnConnection;

    // Save the dialog details
    private boolean mDlgEdit;
    private AccessPoint mDlgAccessPoint;
    private Bundle mAccessPointSavedState;

    // the action bar uses a different set of controls for Setup Wizard
    private boolean mSetupWizardMode;

    /* End of "used in Wifi Setup context" */
    /* SPRD: CMCC feature default ap start */
    private PreferenceGroup mDefinedApsCategory;
    private PreferenceGroup mOtherApsCategory;
    /* SPRD: CMCC feature default ap end */

    /* SPRD: modified for UUI empty view text color and size @{ */
    private static boolean UNIVERSEUI_SUPPORT = SystemProperties.getBoolean("universe_ui_support",false);
    private static final float DEFAULT_FONT_SIZE = 20f;
    /* @} */
    private SwitchPreference mEnablerSwitchPreference;
    private Preference mEmptyPreference;
    private int isDownload = 0;

    public WifiSettings() {
        super(DISALLOW_CONFIG_WIFI);
        supportCMCC = SystemProperties.get("ro.operator").equals("cmcc");

        mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
        mFilter.addAction(WifiManager.LINK_CONFIGURATION_CHANGED_ACTION);
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);

        // Broadcom, WAPI
        mFilter.addAction(WifiManager.SUPPLICANT_WAPI_EVENT);
        // Broadcom, WAPI

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleEvent(context, intent);
            }
        };

        mScanner = new Scanner();
    }

    @Override
    public void onCreate(Bundle icicle) {

        mWifiManager= (WifiManager) getSystemService(Context.WIFI_SERVICE);
        //yanbing add WifiSetings 20190308
        if(!mWifiManager.isWifiEnabled()){
            if (!mWifiManager.setWifiEnabled(true)) {
                // Error
//            mEnablerSwitchPreference.setEnabled(true);
                Toast.makeText(getActivity(), R.string.wifi_error, Toast.LENGTH_SHORT).show();
            }
            //yanbing add WifiSetings 20190308
        }

        // Set this flag early, as it's needed by getHelpResource(), which is called by super
        mSetupWizardMode = getActivity().getIntent().getBooleanExtra(EXTRA_IS_FIRST_RUN, false);

        Intent intent = getActivity().getIntent();
        isDownload = intent.getIntExtra("isDownload",0);
        Log.d(TAG,"isDownload="+isDownload);

        try {
            Log.d("yanbing2","he");
            throw new Exception();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Resources res = getResources();
        Drawable drawable = res.getDrawable(R.drawable.bkcolor);
        getActivity().getWindow().setBackgroundDrawable(drawable);
//        showMyToast(toastFullScreen(),12000);

        super.onCreate(icicle);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if (mSetupWizardMode) {
            View view = inflater.inflate(R.layout.setup_preference, container, false);
            View other = view.findViewById(R.id.other_network);
            other.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mWifiManager.isWifiEnabled()) {
                        onAddNetworkPressed();
                    }
                }
            });
            final ImageButton b = (ImageButton) view.findViewById(R.id.more);
            if (b != null) {
                b.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mWifiManager.isWifiEnabled()) {
                            PopupMenu pm = new PopupMenu(inflater.getContext(), b);
                            pm.inflate(R.menu.wifi_setup);
                            pm.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                                @Override
                                public boolean onMenuItemClick(MenuItem item) {
                                    if (R.id.wifi_wps == item.getItemId()) {
                                        showDialog(WPS_PBC_DIALOG_ID);
                                        return true;
                                    }
                                    return false;
                                }
                            });
                            pm.show();
                        }
                    }
                });
            }

            Intent intent = getActivity().getIntent();
            if (intent.getBooleanExtra(EXTRA_SHOW_CUSTOM_BUTTON, false)) {
                view.findViewById(R.id.button_bar).setVisibility(View.VISIBLE);
                view.findViewById(R.id.back_button).setVisibility(View.INVISIBLE);
                view.findViewById(R.id.skip_button).setVisibility(View.INVISIBLE);
                view.findViewById(R.id.next_button).setVisibility(View.INVISIBLE);

                Button customButton = (Button) view.findViewById(R.id.custom_button);
                customButton.setVisibility(View.VISIBLE);
                customButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean isConnected = false;
                        Activity activity = getActivity();
                        final ConnectivityManager connectivity = (ConnectivityManager)
                                activity.getSystemService(Context.CONNECTIVITY_SERVICE);
                        if (connectivity != null) {
                            final NetworkInfo info = connectivity.getActiveNetworkInfo();
                            isConnected = (info != null) && info.isConnected();
                        }
                        if (isConnected) {
                            // Warn of possible data charges
                            showDialog(WIFI_SKIPPED_DIALOG_ID);
                        } else {
                            // Warn of lack of updates
                            showDialog(WIFI_AND_MOBILE_SKIPPED_DIALOG_ID);
                        }
                    }
                });
            }

            if (intent.getBooleanExtra(EXTRA_SHOW_WIFI_REQUIRED_INFO, false)) {
                view.findViewById(R.id.wifi_required_info).setVisibility(View.VISIBLE);
            }

            return view;
        } else {
            return super.onCreateView(inflater, container, savedInstanceState);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mP2pSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
        if(mWifiManager ==null){
            mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        }


        mConnectListener = new WifiManager.ActionListener() {
                                   @Override
                                   public void onSuccess() {
                                   }
                                   @Override
                                   public void onFailure(int reason) {
                                       Activity activity = getActivity();
                                       if (activity != null) {
                                           Toast.makeText(activity,
                                                R.string.wifi_failed_connect_message,
                                                Toast.LENGTH_SHORT).show();
                                       }
                                   }
                               };

        mSaveListener = new WifiManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                }
                                @Override
                                public void onFailure(int reason) {
                                    Activity activity = getActivity();
                                    if (activity != null) {
                                        Toast.makeText(activity,
                                            R.string.wifi_failed_save_message,
                                            Toast.LENGTH_SHORT).show();
                                    }
                                }
                            };

        mForgetListener = new WifiManager.ActionListener() {
                                   @Override
                                   public void onSuccess() {
                                   }
                                   @Override
                                   public void onFailure(int reason) {
                                       Activity activity = getActivity();
                                       if (activity != null) {
                                           Toast.makeText(activity,
                                               R.string.wifi_failed_forget_message,
                                               Toast.LENGTH_SHORT).show();
                                       }
                                   }
                               };

        if (savedInstanceState != null
                && savedInstanceState.containsKey(SAVE_DIALOG_ACCESS_POINT_STATE)) {
            mDlgEdit = savedInstanceState.getBoolean(SAVE_DIALOG_EDIT_MODE);
            mAccessPointSavedState = savedInstanceState.getBundle(SAVE_DIALOG_ACCESS_POINT_STATE);
        }

        final Activity activity = getActivity();
        final Intent intent = activity.getIntent();

        // first if we're supposed to finish once we have a connection
        mAutoFinishOnConnection = intent.getBooleanExtra(EXTRA_AUTO_FINISH_ON_CONNECT, false);

        if (mAutoFinishOnConnection) {
            // Hide the next button
            if (hasNextButton()) {
                getNextButton().setVisibility(View.GONE);
            }

            final ConnectivityManager connectivity = (ConnectivityManager)
                    activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null
                    && connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
                activity.setResult(Activity.RESULT_OK);
                activity.finish();
                return;
            }
        }

        // if we're supposed to enable/disable the Next button based on our current connection
        // state, start it off in the right state
        mEnableNextOnConnection = intent.getBooleanExtra(EXTRA_ENABLE_NEXT_ON_CONNECT, false);

        if (mEnableNextOnConnection) {
            if (hasNextButton()) {
                final ConnectivityManager connectivity = (ConnectivityManager)
                        activity.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (connectivity != null) {
                    NetworkInfo info = connectivity.getNetworkInfo(
                            ConnectivityManager.TYPE_WIFI);
                    changeNextButtonState(info.isConnected());
                }
            }
        }
        addPreferencesFromResource(R.xml.wifi_settings);

        if (mSetupWizardMode) {
            getView().setSystemUiVisibility(
//                    View.STATUS_BAR_DISABLE_BACK |
                    View.STATUS_BAR_DISABLE_HOME |
                    View.STATUS_BAR_DISABLE_RECENT |
                    View.STATUS_BAR_DISABLE_NOTIFICATION_ALERTS |
                    View.STATUS_BAR_DISABLE_CLOCK);
        }

        // On/off switch is hidden for Setup Wizard
        /*
        if (!mSetupWizardMode) {
            Switch actionBarSwitch = new Switch(activity);

            if (activity instanceof PreferenceActivity) {
                PreferenceActivity preferenceActivity = (PreferenceActivity) activity;
                if (preferenceActivity.onIsHidingHeaders() || !preferenceActivity.onIsMultiPane()) {
                    final int padding = activity.getResources().getDimensionPixelSize(
                            R.dimen.action_bar_switch_padding);
                    actionBarSwitch.setPaddingRelative(0, 0, padding, 0);
                    activity.getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                            ActionBar.DISPLAY_SHOW_CUSTOM);
                    activity.getActionBar().setCustomView(actionBarSwitch, new ActionBar.LayoutParams(
                            ActionBar.LayoutParams.WRAP_CONTENT,
                            ActionBar.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER_VERTICAL | Gravity.END));
                    actionBarSwitch.requestFocus();
                }
            }

            mWifiEnabler = new WifiEnabler(activity, actionBarSwitch);
        }*/

        mEnablerSwitchPreference = new WifiEnablerSwitchPreference(getActivity());
        mEnablerSwitchPreference.setEnabled(true);
        mEnablerSwitchPreference.setSwitchTextOff("");
        mEnablerSwitchPreference.setSwitchTextOn("");
        mEnablerSwitchPreference.setSummaryOn(R.string.accessibility_feature_state_on);
        mEnablerSwitchPreference.setSummaryOff(R.string.accessibility_feature_state_off);
        mWifiEnabler = new WifiEnabler(activity, mEnablerSwitchPreference);

        mEmptyPreference  = new Preference(getActivity());
        mEmptyPreference.setSelectable(false);

        mEmptyView = (TextView) getView().findViewById(android.R.id.empty);
        // SPRD: Add for bug678513.
        mEmptyView.setMovementMethod(new ScrollingMovementMethod());
        /* SPRD: Modify Bug 322640 text size change after different opreating @{ */
        if (UNIVERSEUI_SUPPORT) {
            Resources res = getResources();
            mEmptyView.setTextColor(res.getColor(R.color.text_Empty_color_newui));
            mEmptyView.setTextSize(120f);
        }
        Resources res = getResources();
        mEmptyView.setTextColor(res.getColor(R.color.text_Empty_color_newui));
        mEmptyView.setTextSize(32f);
        /* @} */
        getListView().setEmptyView(mEmptyView);
//        showMyToast(toastFullScreen(),12000);

        if (!mSetupWizardMode) {
            registerForContextMenu(getListView());
        }
        setHasOptionsMenu(true);
    }

    private Toast toastFullScreen(){
        Log.d(TAG,"toastFullScreen");
        Toast toast = Toast.makeText(getActivity(), null, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        LinearLayout toastView = (LinearLayout)toast.getView();
        toastView.setBackgroundColor(Color.BLACK);

        // Get the screen size with unit pixels.
        WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(outMetrics);

        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(outMetrics.widthPixels,
                outMetrics.heightPixels);
        vlp.setMargins(0, 0, 0, 0);

        LinearLayout entity = (LinearLayout) getActivity().getLayoutInflater().inflate(R.layout.wifi_searching, null);

        GifView gifView = (GifView)entity.findViewById(R.id.search_gif);
        gifView.setMovieResource(R.raw.progress);
        entity.setLayoutParams(vlp);

        toastView.addView(entity);
        return toast;
//        toast.show();
    }

    private void showMyToast(final Toast toast,final   int cnt) {
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                toast.show();
            }
        }, 0, 3000);//每隔三秒调用一次show方法;

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                toast.cancel();
                timer.cancel();
            }
        }, cnt );//经过多长时间关闭该任务
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWifiEnabler != null) {
            mWifiEnabler.resume();
        }

        if (!mWifiManager.isWifiEnabled()) {
            if (!mWifiManager.setWifiEnabled(true)) {
                Toast.makeText(getActivity(), R.string.wifi_error, Toast.LENGTH_SHORT).show();
            }
        }


        getActivity().registerReceiver(mReceiver, mFilter);
        updateAccessPoints();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG,"onPause");
        if (mWifiEnabler != null) {
            mWifiEnabler.pause();
        }
        getActivity().unregisterReceiver(mReceiver);
        mScanner.pause();

        if (!isWiFiActive()) {
            if (mWifiManager.isWifiEnabled()) {
                mWifiManager.setWifiEnabled(false);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"onDestroy");
        if (!isWiFiActive()) {
            if (mWifiManager.isWifiEnabled()) {
                mWifiManager.setWifiEnabled(false);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // If the user is not allowed to configure wifi, do not show the menu.
        if (isRestrictedAndNotPinProtected()) return;

        final boolean wifiIsEnabled = mWifiManager.isWifiEnabled();
        TypedArray ta = getActivity().getTheme().obtainStyledAttributes(
                new int[] {R.attr.ic_menu_add, R.attr.ic_wps, R.attr.ic_tab_wifi_trusted_ap});
        if (mSetupWizardMode) {
            menu.add(Menu.NONE, MENU_ID_WPS_PBC, 0, R.string.wifi_menu_wps_pbc)
                    .setIcon(ta.getDrawable(1))
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(Menu.NONE, MENU_ID_ADD_NETWORK, 0, R.string.wifi_add_network)
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
            menu.add(Menu.NONE, MENU_ID_WPS_PBC, 0, R.string.wifi_menu_wps_pbc)
                    .setIcon(ta.getDrawable(1))
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            //add by spreadst_lc for cmcc wifi feature start
            if (supportCMCC) {
                menu.add(Menu.NONE, MENU_ID_TRUSTED_AP, 0, R.string.wifi_trusted_ap)
                        .setIcon(ta.getDrawable(2))
                        .setEnabled(wifiIsEnabled)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
            //add by spreadst_lc for cmcc wifi feature end
            menu.add(Menu.NONE, MENU_ID_ADD_NETWORK, 0, R.string.wifi_add_network)
                    .setIcon(ta.getDrawable(0))
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(Menu.NONE, MENU_ID_SCAN, 0, R.string.wifi_menu_scan)
                    //.setIcon(R.drawable.ic_menu_scan_network)
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(Menu.NONE, MENU_ID_WPS_PIN, 0, R.string.wifi_menu_wps_pin)
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            if (mP2pSupported) {
                menu.add(Menu.NONE, MENU_ID_P2P, 0, R.string.wifi_menu_p2p)
                        .setEnabled(wifiIsEnabled)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
            menu.add(Menu.NONE, MENU_ID_ADVANCED, 0, R.string.wifi_menu_advanced)
                    //.setIcon(android.R.drawable.ic_menu_manage)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        ta.recycle();
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // If the dialog is showing, save its state.
        if (mDialog != null && mDialog.isShowing()) {
            outState.putBoolean(SAVE_DIALOG_EDIT_MODE, mDlgEdit);
            if (mDlgAccessPoint != null) {
                mAccessPointSavedState = new Bundle();
                mDlgAccessPoint.saveWifiState(mAccessPointSavedState);
                outState.putBundle(SAVE_DIALOG_ACCESS_POINT_STATE, mAccessPointSavedState);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // If the user is not allowed to configure wifi, do not handle menu selections.
        if (isRestrictedAndNotPinProtected()) return false;

        switch (item.getItemId()) {
            case MENU_ID_WPS_PBC:
                removeDialog(WPS_PBC_DIALOG_ID);
                showDialog(WPS_PBC_DIALOG_ID);
                return true;
            case MENU_ID_P2P:
                if (getActivity() instanceof PreferenceActivity) {
                    ((PreferenceActivity) getActivity()).startPreferencePanel(
                            WifiP2pSettings.class.getCanonicalName(),
                            null,
                            R.string.wifi_p2p_settings_title, null,
                            this, 0);
                } else {
                    startFragment(this, WifiP2pSettings.class.getCanonicalName(), -1, null);
                }
                return true;
            case MENU_ID_WPS_PIN:
                showDialog(WPS_PIN_DIALOG_ID);
                return true;
            case MENU_ID_SCAN:
                if (mWifiManager.isWifiEnabled()) {
                    mScanner.forceScan();
                }
                return true;
            case MENU_ID_ADD_NETWORK:
                if (mWifiManager.isWifiEnabled()) {
                    onAddNetworkPressed();
                }
                return true;
            case MENU_ID_ADVANCED:
                if (getActivity() instanceof PreferenceActivity) {
                    ((PreferenceActivity) getActivity()).startPreferencePanel(
                            AdvancedWifiSettings.class.getCanonicalName(),
                            null,
                            R.string.wifi_advanced_titlebar, null,
                            this, 0);
                } else {
                    startFragment(this, AdvancedWifiSettings.class.getCanonicalName(), -1, null);
                }
                return true;
            //add by spreadst_lc for cmcc wifi feature start
            case MENU_ID_TRUSTED_AP:
                if (mWifiManager.isWifiEnabled()) {
                    Intent intent = new Intent((PreferenceActivity)getActivity(),WifiTrustedAPList.class);
                    this.startActivity(intent);
                }
                return true;
            //add by spreadst_lc for cmcc wifi feature end
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo info) {
//        if (info instanceof AdapterContextMenuInfo) {
//            Preference preference = (Preference) getListView().getItemAtPosition(
//                    ((AdapterContextMenuInfo) info).position);
//
//            if (preference instanceof AccessPoint) {
//                mSelectedAccessPoint = (AccessPoint) preference;
//                menu.setHeaderTitle(mSelectedAccessPoint.ssid);
//                if (mSelectedAccessPoint.getLevel() != -1
//                        && mSelectedAccessPoint.getState() == null) {
//                    menu.add(Menu.NONE, MENU_ID_CONNECT, 0, R.string.wifi_menu_connect);
//                }
//                if (mSelectedAccessPoint.networkId != INVALID_NETWORK_ID) {
//                    menu.add(Menu.NONE, MENU_ID_FORGET, 0, R.string.wifi_menu_forget);
//                    menu.add(Menu.NONE, MENU_ID_MODIFY, 0, R.string.wifi_menu_modify);
//                }
//            }
//        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mSelectedAccessPoint == null) {
            return super.onContextItemSelected(item);
        }
        switch (item.getItemId()) {
            case MENU_ID_CONNECT: {
                if (mSelectedAccessPoint.networkId != INVALID_NETWORK_ID) {
                    //SPRD: if already in connecting/connected state, have the same meaning as manual connect
                    if (supportCMCC == true && WifiConnectionPolicy.isWifiConnectingOrConnected() == true) {
                        WifiConnectionPolicy.setManulConnectFlags(true);
                    }
                    mWifiManager.connect(mSelectedAccessPoint.networkId,
                            mConnectListener);
                } else if (mSelectedAccessPoint.security == AccessPoint.SECURITY_NONE) {
                    /** Bypass dialog for unsecured networks */
                    mSelectedAccessPoint.generateOpenNetworkConfig();
                    //SPRD: if already in connecting/connected state, have the same meaning as manual connect
                    if (supportCMCC == true && WifiConnectionPolicy.isWifiConnectingOrConnected() == true) {
                        WifiConnectionPolicy.setManulConnectFlags(true);
                    }
                    mWifiManager.connect(mSelectedAccessPoint.getConfig(),
                            mConnectListener);
                } else {
                    showDialog(mSelectedAccessPoint, true);
                }
                mShouldShowWAPICertLostError = true;
                return true;
            }
            case MENU_ID_FORGET: {
                if (mSelectedAccessPoint.networkId == INVALID_NETWORK_ID) {
                    // Should not happen, but a monkey seems to triger it
                    Log.e(TAG, "Failed to forget invalid network " + mSelectedAccessPoint.getConfig());
                    return true;
                }
                mWifiManager.forget(mSelectedAccessPoint.networkId, mForgetListener);
                return true;
            }
            case MENU_ID_MODIFY: {
                showDialog(mSelectedAccessPoint, true);
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference instanceof AccessPoint) {
            mSelectedAccessPoint = (AccessPoint) preference;
            /** Bypass dialog for unsecured, unsaved networks */
            if (mSelectedAccessPoint.security == AccessPoint.SECURITY_NONE &&
                    mSelectedAccessPoint.networkId == INVALID_NETWORK_ID) {
                mSelectedAccessPoint.generateOpenNetworkConfig();
                //SPRD: if already in connecting/connected state, have the same meaning as manual connect
                if (supportCMCC == true && WifiConnectionPolicy.isWifiConnectingOrConnected() == true) {
                    WifiConnectionPolicy.setManulConnectFlags(true);
                }
                mWifiManager.connect(mSelectedAccessPoint.getConfig(), mConnectListener);
            } else if(mSelectedAccessPoint.networkId != INVALID_NETWORK_ID){
                showDialog(WIFI_OPTION_DIALOG);
            }else {
                showDialog(mSelectedAccessPoint, false);
            }
        } else {
            return super.onPreferenceTreeClick(screen, preference);
        }
        return true;
    }

    private void showDialog(AccessPoint accessPoint, boolean edit) {
        if (mDialog != null) {
            removeDialog(WIFI_DIALOG_ID);
            mDialog = null;
        }

        // Save the access point and edit mode
        mDlgAccessPoint = accessPoint;
        mDlgEdit = edit;

        if(mDlgAccessPoint.getState()==null) {
            if(mDlgAccessPoint.getConfig()!=null){
                if(mDlgAccessPoint.getConfig().status ==WifiConfiguration.Status.ENABLED && mDlgAccessPoint.getState()==null) {
                    if (mDlgAccessPoint.networkId != INVALID_NETWORK_ID) {
                        mWifiManager.connect(mDlgAccessPoint.networkId, mConnectListener);
                    }
                }
            }else{
                showDialog(WIFI_DIALOG_ID);
            }
        }
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case WIFI_OPTION_DIALOG:
                mWifiOptionDialog = new WifiOptionDialog(getActivity(),this,mSelectedAccessPoint);
                mWifiOptionDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                mWifiOptionDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                return mWifiOptionDialog;
            case WIFI_DIALOG_ID:
                AccessPoint ap = mDlgAccessPoint; // For manual launch
                if (ap == null) { // For re-launch from saved state
                    if (mAccessPointSavedState != null) {
                        ap = new AccessPoint(getActivity(), mAccessPointSavedState);
                        // For repeated orientation changes
                        mDlgAccessPoint = ap;
                        // Reset the saved access point data
                        mAccessPointSavedState = null;
                    }
                }
                // If it's still null, fine, it's for Add Network
                mSelectedAccessPoint = ap;
                //yanbing add WifiSetings 20190308 start
                mDialog = new WifiDialog(getActivity(), R.style.XunDialog,this, ap, mDlgEdit);
                mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                ////yanbing add WifiSetings 20190308 end
                return mDialog;
            case WPS_PBC_DIALOG_ID:
                return new WpsDialog(getActivity(), WpsInfo.PBC);
            case WPS_PIN_DIALOG_ID:
                return new WpsDialog(getActivity(), WpsInfo.DISPLAY);
            case WIFI_SKIPPED_DIALOG_ID:
                return new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.wifi_skipped_message)
                            .setCancelable(false)
                            .setNegativeButton(R.string.wifi_skip_anyway,
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    getActivity().setResult(RESULT_SKIP);
                                    getActivity().finish();
                                }
                            })
                            .setPositiveButton(R.string.wifi_dont_skip,
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                }
                            })
                            .create();
            case WIFI_AND_MOBILE_SKIPPED_DIALOG_ID:
                return new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.wifi_and_mobile_skipped_message)
                            .setCancelable(false)
                            .setNegativeButton(R.string.wifi_skip_anyway,
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    getActivity().setResult(RESULT_SKIP);
                                    getActivity().finish();
                                }
                            })
                            .setPositiveButton(R.string.wifi_dont_skip,
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                }
                            })
                            .create();

        }
        return super.onCreateDialog(dialogId);
    }

    /**
     * Shows the latest access points available with supplimental information like
     * the strength of network and the security for it.
     */
    private void updateAccessPoints() {
        // Safeguard from some delayed event handling
        if (getActivity() == null) return;

        if (isRestrictedAndNotPinProtected()) {
            addMessagePreference(R.string.wifi_empty_list_user_restricted);
            return;
        }
        final int wifiState = mWifiManager.getWifiState();

        switch (wifiState) {
            case WifiManager.WIFI_STATE_ENABLED:
                /* SPRD: CMCC feature default ap start */
                if (supportCMCC) {
                    addAccessPointsForCMCCVersion();
                } else {
                    addAccessPointForOrignalVersion();
                }
                Intent aintent = new Intent("com.xxun.WifiScan");
                Log.i(TAG, "com.xxun.WifiScan");
                getActivity().sendBroadcast(aintent);
                /* SPRD: CMCC feature default ap end */
                break;

            case WifiManager.WIFI_STATE_ENABLING:
                getPreferenceScreen().removeAll();
//                getPreferenceScreen().addPreference(mEnablerSwitchPreference);
                break;

            case WifiManager.WIFI_STATE_DISABLING:
                addMessagePreference(R.string.wifi_stopping);
//                getPreferenceScreen().addPreference(mEnablerSwitchPreference);
                break;

            case WifiManager.WIFI_STATE_DISABLED:
                setOffMessage();
                break;
        }
    }
    /* SPRD: CMCC feature default ap start */
    private void addAccessPointForOrignalVersion() {
     // AccessPoints are automatically sorted with TreeSet.
        final Collection<AccessPoint> accessPoints = constructAccessPoints();
        getPreferenceScreen().removeAll();
        if(accessPoints.size() == 0) {
            addMessagePreference(R.string.wifi_empty_list_wifi_on);
        }else {
//            getPreferenceScreen().addPreference(mEnablerSwitchPreference);
//            mEnablerSwitchPreference.setOrder(0);
        }
        for (AccessPoint accessPoint : accessPoints) {
            ////yanbing add WifiSetings 20190308 start
            accessPoint.setLayoutResource(R.layout.preference_wifi);
            getPreferenceScreen().addPreference(accessPoint);
            //yanbing add WifiSetings 20190308 end
        }
    }
    private void addAccessPointsForCMCCVersion() {
        final Collection<AccessPoint> accessPoints = constructAccessPoints();
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
//        preferenceScreen.addPreference(mEnablerSwitchPreference);
//        mEnablerSwitchPreference.setOrder(0);
        getPreferenceScreen().setOrderingAsAdded(true);

        if (mDefinedApsCategory == null) {
            mDefinedApsCategory = new PreferenceCategory(getActivity());
        } else {
            mDefinedApsCategory.removeAll();
        }
        addAccessPointCategory(mDefinedApsCategory, R.string.user_defined_accesspoints_list);

        if (mOtherApsCategory == null) {
            mOtherApsCategory = new PreferenceCategory(getActivity());
        } else {
            mOtherApsCategory.removeAll();
        }
        addAccessPointCategory(mOtherApsCategory, R.string.other_accesspoints_list);

        for (AccessPoint accessPoint : accessPoints) {
            if (accessPoint.networkId != -1) {
                mDefinedApsCategory.addPreference(accessPoint);
            } else {
                mOtherApsCategory.addPreference(accessPoint);
            }
        }

        int mDefaultDefinedApsCount = mDefinedApsCategory.getPreferenceCount();
        if (mDefaultDefinedApsCount == 0) {
            preferenceScreen.removePreference(mDefinedApsCategory);
        }
    }

    private void addAccessPointCategory(PreferenceGroup preferenceGroup, int titleId) {
        preferenceGroup.setTitle(titleId);
        getPreferenceScreen().addPreference(preferenceGroup);
        preferenceGroup.setEnabled(true);
    }
    /* SPRD: CMCC feature default ap end */
    private void setOffMessage() {
        if (mEmptyView != null) {
            mEmptyView.setText(R.string.wifi_starting);
            if (Settings.Global.getInt(getActivity().getContentResolver(),
                    Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0) == 1) {
                mEmptyView.append("\n\n");
                int resId;
                if (Settings.Secure.isLocationProviderEnabled(getActivity().getContentResolver(),
                        LocationManager.NETWORK_PROVIDER)) {
                    resId = R.string.wifi_scan_notify_text_location_on;
                } else {
                    resId = R.string.wifi_scan_notify_text_location_off;
                }
                CharSequence charSeq = getText(resId);
                mEmptyView.append(charSeq);
            }
        }
        getPreferenceScreen().removeAll();
//        getPreferenceScreen().addPreference(mEnablerSwitchPreference);
//        mEnablerSwitchPreference.setOrder(0);
        if (mEmptyView != null) {
                mEmptyPreference.setSummary(mEmptyView.getText());
                getPreferenceScreen().addPreference(mEmptyPreference);
//                mEnablerSwitchPreference.setOrder(1);
        }

    }

    private void addMessagePreference(int messageId) {
        if (mEmptyView != null) mEmptyView.setText(messageId);
        getPreferenceScreen().removeAll();

//        getPreferenceScreen().addPreference(mEnablerSwitchPreference);
//        mEnablerSwitchPreference.setOrder(0);

        if (mEmptyView != null) {
//            mEmptyPreference.setSummary(mEmptyView.getText());
//            getPreferenceScreen().addPreference(mEmptyPreference);
//            mEnablerSwitchPreference.setOrder(1);
        }
    }

    /** Returns sorted list of access points */
    private List<AccessPoint> constructAccessPoints() {
        ArrayList<AccessPoint> accessPoints = new ArrayList<AccessPoint>();
        /** Lookup table to more quickly update AccessPoints by only considering objects with the
         * correct SSID.  Maps SSID -> List of AccessPoints with the given SSID.  */
        Multimap<String, AccessPoint> apMap = new Multimap<String, AccessPoint>();

        final List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                // Ignore configured AP which ssid is null or "".
                if (config.SSID == null || config.SSID.length() == 0) {
                    continue;
                }
                String capabilities_save = null;
                for (int k = 0; k < config.allowedKeyManagement.size(); k++) {
                    if (config.allowedKeyManagement.get(k)) {
                        if (k < KeyMgmt.strings.length) {
                            capabilities_save = KeyMgmt.strings[k];
                        }
                    }
                }
                if(capabilities_save.contains("WAPI-PSK") || capabilities_save.contains("WAPI-CERT") ||capabilities_save.contains("EAP")) {
                    continue;
                }
                AccessPoint accessPoint = new AccessPoint(getActivity(), config);
                accessPoint.update(mLastInfo, mLastState);
//                accessPoints.add(accessPoint);
                apMap.put(accessPoint.ssid, accessPoint);
            }
        }

        final List<ScanResult> results = mWifiManager.getScanResults();
        if (results != null) {
            for (ScanResult result : results) {
                // Ignore hidden and ad-hoc networks.
                if (result.SSID == null || result.SSID.length() == 0 ||
                        result.capabilities.contains("[IBSS]")) {
                    continue;
                }
                if(result.capabilities.contains("WAPI-PSK") || result.capabilities.contains("WAPI-CERT") ||result.capabilities.contains("EAP")){
                    continue;
                }

                boolean found = false;
                for (AccessPoint accessPoint : apMap.getAll(result.SSID)) {
                    if (accessPoint.update(result)){
                        accessPoints.add(accessPoint);
                        found = true;
                    }
                }
                if (!found) {
                    AccessPoint accessPoint = new AccessPoint(getActivity(), result);
                    accessPoints.add(accessPoint);
                    apMap.put(accessPoint.ssid, accessPoint);
                }
            }
        }

        // Pre-sort accessPoints to speed preference insertion
        Collections.sort(accessPoints);
        return accessPoints;
    }

    /** A restricted multimap for use in constructAccessPoints */
    private class Multimap<K,V> {
        private final HashMap<K,List<V>> store = new HashMap<K,List<V>>();
        /** retrieve a non-null list of values with key K */
        List<V> getAll(K key) {
            List<V> values = store.get(key);
            return values != null ? values : Collections.<V>emptyList();
        }

        void put(K key, V val) {
            List<V> curVals = store.get(key);
            if (curVals == null) {
                curVals = new ArrayList<V>(3);
                store.put(key, curVals);
            }
            curVals.add(val);
        }
    }

    private void handleEvent(Context context, Intent intent) {
        String action = intent.getAction();
        // Broadcom, WAPI
        if (WifiManager.SUPPLICANT_WAPI_EVENT.equals(action)) {
            String wapiEventName = "wapi_string";
            int wapiGetEvent;
            wapiGetEvent = intent.getIntExtra(wapiEventName, 0);
            Log.w(TAG, "SUPPLICANT_WAPI_EVENT received: " + wapiGetEvent);
            switch(wapiGetEvent) {
            case WifiManager.WAPI_EVENT_CERT_FAIL_CODE:
                Toast.makeText(context, R.string.wifi_wapi_fail_to_init,
                    Toast.LENGTH_LONG).show();
                break;
            case WifiManager.WAPI_EVENT_AUTH_FAIL_CODE:
                Toast.makeText(context, R.string.wifi_wapi_fail_to_auth,
                    Toast.LENGTH_LONG).show();
                break;
            case WifiManager.WAPI_EVENT_CERT_LOST_CODE:
                Log.e(TAG, "WAPI_EVENT_CERT_LOST_CODE");
                if(mShouldShowWAPICertLostError == true)
                {
                   Toast.makeText(context, R.string.wifi_wapi_cert_lost,
                       Toast.LENGTH_LONG).show();
                    Log.w(TAG, "cert lost");
                    mShouldShowWAPICertLostError = false;
                }
                break;
            }
        } else
        // Broadcom, WAPI
        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
            /* SPRD: fix bug272987 remove wpsdialog when turn off wifi  */
            if (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN) != WifiManager.WIFI_STATE_ENABLED) {
                removeDialog(WPS_PBC_DIALOG_ID);
            }
            updateWifiState(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN));
        } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action) ||
                WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION.equals(action) ||
                WifiManager.LINK_CONFIGURATION_CHANGED_ACTION.equals(action)) {
                updateAccessPoints();
        } else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
            //Ignore supplicant state changes when network is connected
            //TODO: we should deprecate SUPPLICANT_STATE_CHANGED_ACTION and
            //introduce a broadcast that combines the supplicant and network
            //network state change events so the apps dont have to worry about
            //ignoring supplicant state change when network is connected
            //to get more fine grained information.
            SupplicantState state = (SupplicantState) intent.getParcelableExtra(
                    WifiManager.EXTRA_NEW_STATE);
            if (!mConnected.get() && SupplicantState.isHandshakeState(state)) {
                updateConnectionState(WifiInfo.getDetailedStateOf(state));
             } else {
                 // During a connect, we may have the supplicant
                 // state change affect the detailed network state.
                 // Make sure a lost connection is updated as well.
                 updateConnectionState(null);
             }
        } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(
                    WifiManager.EXTRA_NETWORK_INFO);
            if(info.isConnected() && (isDownload==1)){
                Log.d(TAG,"sendBroadcast isDownload="+isDownload);
                Intent aintent = new Intent("com.xxun.watch.WifiOnlyDownload");
                Log.i(TAG, "send WifiOnlyDownload broadcast");
                getActivity().sendBroadcast(aintent);
                getActivity().finish();
                return;
            }

            mConnected.set(info.isConnected());
            changeNextButtonState(info.isConnected());
            updateAccessPoints();
            updateConnectionState(info.getDetailedState());
            if (mAutoFinishOnConnection && info.isConnected()) {
                Activity activity = getActivity();
                if (activity != null) {
                    activity.setResult(Activity.RESULT_OK);
                    activity.finish();
                }
                return;
            }
        } else if (WifiManager.RSSI_CHANGED_ACTION.equals(action)) {
            updateConnectionState(null);
        }
    }

    private void updateConnectionState(DetailedState state) {
        /* sticky broadcasts can call this when wifi is disabled */
        if (!mWifiManager.isWifiEnabled()) {
            mScanner.pause();
            return;
        }

        if (state == DetailedState.OBTAINING_IPADDR) {
            mScanner.pause();
        } else {
            mScanner.resume();
        }

        mLastInfo = mWifiManager.getConnectionInfo();
        if (state != null) {
            //mLastState = state;
            // Try to get the current connection state that is more accurate when state is disconnected.
            if (state == DetailedState.DISCONNECTED) {
                DetailedState mCurrentState = DetailedState.IDLE;
                if (mLastInfo != null) {
                    mCurrentState = mLastInfo.getDetailedStateOf(mLastInfo.getSupplicantState());
                }
                if (mCurrentState.compareTo(DetailedState.SCANNING) > 0) {
                    mLastState = mCurrentState;
                } else {
                    mLastState = state;
                }
            } else {
                mLastState = state;
            }
        }
        boolean isIloggable = Log.isIloggable();
        if (isIloggable) {
            if (state == DetailedState.CONNECTED) {
                Log.stopPerfTracking("WLAN: connected.");
            }else if (state == DetailedState.DISCONNECTED) {
                Log.stopPerfTracking("WLAN: disconnected.");
            }
        }
        for (int i = getPreferenceScreen().getPreferenceCount() - 1; i >= 0; --i) {
            Preference preference = getPreferenceScreen().getPreference(i);
            if (supportCMCC) {
                if (preference instanceof PreferenceCategory) {
                    // Maybe there's a WifiConfigPreference in the subPreference of PreferenceCategory.
                    for (int j =  ((PreferenceCategory) preference).getPreferenceCount() - 1; j >= 0; --j) {
                        Preference subPreference = ((PreferenceCategory) preference).getPreference(j);
                        if (subPreference instanceof AccessPoint) {
                            final AccessPoint accessPoint = (AccessPoint) subPreference;
                            accessPoint.update(mLastInfo, mLastState);
                        }
                    }
                }
            } else {
                // Maybe there's a WifiConfigPreference
                if (preference instanceof AccessPoint) {
                    final AccessPoint accessPoint = (AccessPoint) preference;
                    accessPoint.update(mLastInfo, mLastState);
                }
            }
        }
    }

    private void updateWifiState(int state) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.invalidateOptionsMenu();
        }

        switch (state) {
            case WifiManager.WIFI_STATE_ENABLED:
                mScanner.resume();
                return; // not break, to avoid the call to pause() below

            case WifiManager.WIFI_STATE_ENABLING:
                addMessagePreference(R.string.wifi_starting);
                break;

            case WifiManager.WIFI_STATE_DISABLED:
                setOffMessage();
                break;
        }

        mLastInfo = null;
        mLastState = null;
        mScanner.pause();
    }

    private class Scanner extends Handler {
        private int mRetry = 0;

        void resume() {
            if (!hasMessages(0)) {
                sendEmptyMessage(0);
            }
        }

        void forceScan() {
            removeMessages(0);
            sendEmptyMessage(0);
        }

        void pause() {
            mRetry = 0;
            removeMessages(0);
        }

        @Override
        public void handleMessage(Message message) {
            if (mWifiManager.startScan()) {
                mRetry = 0;
            } else if (++mRetry >= 3) {
                mRetry = 0;
                Activity activity = getActivity();
                if (activity != null) {
                    Toast.makeText(activity, R.string.wifi_fail_to_scan,
                            Toast.LENGTH_LONG).show();
                }
                return;
            }
            sendEmptyMessageDelayed(0, WIFI_RESCAN_INTERVAL_MS);
        }
    }

    public boolean isWiFiActive() {
        ConnectivityManager manager = (ConnectivityManager)getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            NetworkInfo wifiInfo = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (wifiInfo != null && wifiInfo.isConnected()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Renames/replaces "Next" button when appropriate. "Next" button usually exists in
     * Wifi setup screens, not in usual wifi settings screen.
     *
     * @param connected true when the device is connected to a wifi network.
     */
    private void changeNextButtonState(boolean connected) {
        if (mEnableNextOnConnection && hasNextButton()) {
            getNextButton().setEnabled(connected);
        }
    }

    //yanbing add WifiSetings 20190308 start
    @Override
    public void onForget(WifiDialog dialog) {
        forget();
    }

    @Override
    public void onSubmit(WifiDialog dialog) {
        if (mDialog != null) {
            submit(mDialog.getController());
            mShouldShowWAPICertLostError = true;
        }
    }

    @Override
    public void connect_to_network(AccessPoint accessPoint_1,WifiOptionDialog dialog){
        if (dialog != null) {
            if (accessPoint_1.networkId != INVALID_NETWORK_ID) {
                //SPRD: if already in connecting/connected state, have the same meaning as manual connect
                if (supportCMCC == true && WifiConnectionPolicy.isWifiConnectingOrConnected() == true) {
                    WifiConnectionPolicy.setManulConnectFlags(true);
                }
                mWifiManager.connect(accessPoint_1.networkId,
                        mConnectListener);
            }else if (accessPoint_1.security == AccessPoint.SECURITY_NONE) {
                /** Bypass dialog for unsecured networks */
                accessPoint_1.generateOpenNetworkConfig();
                //SPRD: if already in connecting/connected state, have the same meaning as manual connect
                if (supportCMCC == true && WifiConnectionPolicy.isWifiConnectingOrConnected() == true) {
                    WifiConnectionPolicy.setManulConnectFlags(true);
                }
                mWifiManager.connect(accessPoint_1.getConfig(),
                        mConnectListener);
            }else {
                showDialog(accessPoint_1, true);
            }
            mShouldShowWAPICertLostError = true;
        }
    }

    @Override
    public void cancel_save_network(AccessPoint accessPoint_1,WifiOptionDialog dialog){
        Log.d("yanbing","wifiSetting cancel_save_network");
        if (dialog != null) {
            if (accessPoint_1.networkId == INVALID_NETWORK_ID) {
                // Should not happen, but a monkey seems to triger it
                Log.e(TAG, "Failed to forget invalid network " + accessPoint_1.getConfig());
            }
            mWifiManager.forget(mSelectedAccessPoint.networkId, mForgetListener);
        }
    }

    @Override
    public void disconnect_network(AccessPoint accessPoint_1,WifiOptionDialog dialog){
        if (dialog != null) {
            mWifiManager.disableNetwork(accessPoint_1.networkId);
            mWifiManager.disconnect();
        }
    }
    //yanbing add WifiSetings 20190308 end

    /* package */ void submit(WifiConfigController configController) {

        final WifiConfiguration config = configController.getConfig();
        boolean isIloggable = Log.isIloggable();
        if(config != null&&(config.preSharedKey != null)){
            if ((config.wapiPskType == WifiConfiguration.WAPI_HEX_PASSWORD)
                && (!AccessPoint.removeDoubleQuotes(config.preSharedKey).matches("[0-9A-Fa-f]*"))) {
                Toast.makeText(getActivity(),
                    R.string.lockpassword_illegal_character, Toast.LENGTH_SHORT)
                    .show();
                return ;
            }
        }

        if (config == null) {
            if (mSelectedAccessPoint != null
                    && mSelectedAccessPoint.networkId != INVALID_NETWORK_ID) {
                if (isIloggable) {
                    Log.startPerfTracking("WLAN: connect to ap.");
                }

                //SPRD: if already in connecting/connected state, have the same meaning as manual connect
                if (supportCMCC == true && WifiConnectionPolicy.isWifiConnectingOrConnected() == true) {
                    WifiConnectionPolicy.setManulConnectFlags(true);
                }
                mWifiManager.connect(mSelectedAccessPoint.networkId,
                        mConnectListener);
            }
        } else if (config.networkId != INVALID_NETWORK_ID) {
            if (mSelectedAccessPoint != null) {
                mWifiManager.save(config, mSaveListener);
            }
        } else {
            if (configController.isEdit()) {
                mWifiManager.save(config, mSaveListener);
            } else {
                if (isIloggable) {
                    Log.startPerfTracking("WLAN: connect to ap.");
                }
                //SPRD: if already in connecting/connected state, have the same meaning as manual connect
                if (supportCMCC == true && WifiConnectionPolicy.isWifiConnectingOrConnected() == true) {
                    WifiConnectionPolicy.setManulConnectFlags(true);
                }
                mWifiManager.connect(config, mConnectListener);
            }
        }

        if (mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }
        updateAccessPoints();
    }

    /* package */ void forget() {
        if (mSelectedAccessPoint.networkId == INVALID_NETWORK_ID) {
            // Should not happen, but a monkey seems to triger it
            Log.e(TAG, "Failed to forget invalid network " + mSelectedAccessPoint.getConfig());
            return;
        }

        mWifiManager.forget(mSelectedAccessPoint.networkId, mForgetListener);

        if (mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }
        updateAccessPoints();

        // We need to rename/replace "Next" button in wifi setup context.
        changeNextButtonState(false);
    }

    /**
     * Refreshes acccess points and ask Wifi module to scan networks again.
     */
    /* package */ void refreshAccessPoints() {
        if (mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }

        getPreferenceScreen().removeAll();
    }

    /**
     * Called when "add network" button is pressed.
     */
    /* package */ void onAddNetworkPressed() {
        // No exact access point is selected.
        mSelectedAccessPoint = null;
        showDialog(null, true);
    }

    /* package */ int getAccessPointsCount() {
        final boolean wifiIsEnabled = mWifiManager.isWifiEnabled();
        if (wifiIsEnabled) {
            return getPreferenceScreen().getPreferenceCount();
        } else {
            return 0;
        }
    }

    /**
     * Requests wifi module to pause wifi scan. May be ignored when the module is disabled.
     */
    /* package */ void pauseWifiScan() {
        if (mWifiManager.isWifiEnabled()) {
            mScanner.pause();
        }
    }

    /**
     * Requests wifi module to resume wifi scan. May be ignored when the module is disabled.
     */
    /* package */ void resumeWifiScan() {
        if (mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }
    }

    @Override
    protected int getHelpResource() {
        if (mSetupWizardMode) {
            return 0;
        }
        return R.string.help_url_wifi;
    }

    /**
     * Used as the outer frame of all setup wizard pages that need to adjust their margins based
     * on the total size of the available display. (e.g. side margins set to 10% of total width.)
     */
    public static class ProportionalOuterFrame extends RelativeLayout {
        public ProportionalOuterFrame(Context context) {
            super(context);
        }
        public ProportionalOuterFrame(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
        public ProportionalOuterFrame(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        /**
         * Set our margins and title area height proportionally to the available display size
         */
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
            int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
            final Resources resources = getContext().getResources();
            float titleHeight = resources.getFraction(R.dimen.setup_title_height, 1, 1);
            float sideMargin = resources.getFraction(R.dimen.setup_border_width, 1, 1);
            int bottom = resources.getDimensionPixelSize(R.dimen.setup_margin_bottom);
            setPaddingRelative(
                    (int) (parentWidth * sideMargin),
                    0,
                    (int) (parentWidth * sideMargin),
                    bottom);
            View title = findViewById(R.id.title_area);
            if (title != null) {
                title.setMinimumHeight((int) (parentHeight * titleHeight));
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
    public class WifiEnablerSwitchPreference extends SwitchPreference {

        private float fontScale=1;
        public WifiEnablerSwitchPreference(Context context) {
            super(context);
            fontScale=context.getResources().getDisplayMetrics().scaledDensity;
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);
            ViewGroup preferenceLayout = (ViewGroup) view;
            view.setPadding(0,0,8,0);
            TextView title = (TextView) ((ViewGroup) preferenceLayout.getChildAt(1)).getChildAt(0);
            TextView summary = (TextView) ((ViewGroup) preferenceLayout.getChildAt(1)).getChildAt(1);
//            title.setTextSize(30);
            //20px to sp
//            summary.setTextSize(20 / fontScale);
            title.setPadding(8,0,0,0);
            summary.setPadding(8,0,0,0);
        }
    }

}
