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

import com.android.settings.R;

import android.content.Context;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.Preference;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Color;

//yanbing add WifiSetings 20190308 start
import android.content.res.Resources.Theme;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
//yanbing add WifiSetings 20190308 end

class AccessPoint extends Preference {
    static final String TAG = "Settings.AccessPoint";

    private static final String KEY_DETAILEDSTATE = "key_detailedstate";
    private static final String KEY_WIFIINFO = "key_wifiinfo";
    private static final String KEY_SCANRESULT = "key_scanresult";
    private static final String KEY_CONFIG = "key_config";

    private static final int[] STATE_SECURED = {
        R.attr.state_encrypted
    };
    private static final int[] STATE_NONE = {};

    /** These values are matched in string arrays -- changes must be kept in sync */
    static final int SECURITY_NONE = 0;
    static final int SECURITY_WEP = 1;
    static final int SECURITY_PSK = 2;
    static final int SECURITY_EAP = 3;
    // Broadcom, WAPI
    static final int SECURITY_WAPI_PSK = 4;
    static final int SECURITY_WAPI_CERT = 5;
    // Broadcom, WAPI

    enum PskType {
        UNKNOWN,
        WPA,
        WPA2,
        WPA_WPA2
    }

    String ssid;
    String bssid;
    int security;
    int networkId;
    boolean wpsAvailable = false;

    PskType pskType = PskType.UNKNOWN;

    private WifiConfiguration mConfig;
    /* package */ScanResult mScanResult;

    private int mRssi;
    private WifiInfo mInfo;
    private DetailedState mState;
    private TextView titleView;
    private TextView summaryView;

    static int getSecurity(WifiConfiguration config) {
        if (config.allowedKeyManagement.get(KeyMgmt.WPA_PSK)) {
            return SECURITY_PSK;
        }
        if (config.allowedKeyManagement.get(KeyMgmt.WPA_EAP) ||
                config.allowedKeyManagement.get(KeyMgmt.IEEE8021X)) {
            return SECURITY_EAP;
        }
        // Broadcom, WAPI
        if (config.allowedKeyManagement.get(KeyMgmt.WAPI_PSK)) {
            return SECURITY_WAPI_PSK;
        }
        if (config.allowedKeyManagement.get(KeyMgmt.WAPI_CERT)) {
            return SECURITY_WAPI_CERT;
        }
        // Broadcom, WAPI
        return (config.wepKeys[0] != null) ? SECURITY_WEP : SECURITY_NONE;
    }

    private static int getSecurity(ScanResult result) {
        // Broadcom, WAPI
        if (result.capabilities.contains("WAPI-PSK")) {
            return SECURITY_WAPI_PSK;
        } else if (result.capabilities.contains("WAPI-CERT")) {
            return SECURITY_WAPI_CERT;
        } else
        // Broadcom, WAPI
        if (result.capabilities.contains("WEP")) {
            return SECURITY_WEP;
        } else if (result.capabilities.contains("PSK")) {
            return SECURITY_PSK;
        } else if (result.capabilities.contains("EAP")) {
            return SECURITY_EAP;
        }
        return SECURITY_NONE;
    }

    public String getSecurityString(boolean concise) {
        Context context = getContext();
        switch(security) {
            case SECURITY_EAP:
                return concise ? context.getString(R.string.wifi_security_short_eap) :
                    context.getString(R.string.wifi_security_eap);
            case SECURITY_PSK:
                switch (pskType) {
                    case WPA:
                        return concise ? context.getString(R.string.wifi_security_short_wpa) :
                            context.getString(R.string.wifi_security_wpa);
                    case WPA2:
                        return concise ? context.getString(R.string.wifi_security_short_wpa2) :
                            context.getString(R.string.wifi_security_wpa2);
                    case WPA_WPA2:
                        return concise ? context.getString(R.string.wifi_security_short_wpa_wpa2) :
                            context.getString(R.string.wifi_security_wpa_wpa2);
                    case UNKNOWN:
                    default:
                        return concise ? context.getString(R.string.wifi_security_short_psk_generic)
                                : context.getString(R.string.wifi_security_psk_generic);
                }
            case SECURITY_WEP:
                return concise ? context.getString(R.string.wifi_security_short_wep) :
                    context.getString(R.string.wifi_security_wep);
            // Broadcom, WAPI
            case SECURITY_WAPI_PSK:
                return context.getString(R.string.wifi_security_wapi_psk);
            case SECURITY_WAPI_CERT:
                return context.getString(R.string.wifi_security_wapi_cert);
            // Broadcom, WAPI
            case SECURITY_NONE:
            default:
                return concise ? "" : context.getString(R.string.wifi_security_none);
        }
    }

    private static PskType getPskType(ScanResult result) {
        boolean wpa = result.capabilities.contains("WPA-PSK");
        boolean wpa2 = result.capabilities.contains("WPA2-PSK");
        if (wpa2 && wpa) {
            return PskType.WPA_WPA2;
        } else if (wpa2) {
            return PskType.WPA2;
        } else if (wpa) {
            return PskType.WPA;
        } else {
            Log.w(TAG, "Received abnormal flag string: " + result.capabilities);
            return PskType.UNKNOWN;
        }
    }

    AccessPoint(Context context, WifiConfiguration config) {
        super(context);
        loadConfig(config);
        refresh();
    }

    AccessPoint(Context context, ScanResult result) {
        super(context);
        loadResult(result);
        refresh();
    }

    AccessPoint(Context context, Bundle savedState) {
        super(context);

        mConfig = savedState.getParcelable(KEY_CONFIG);
        if (mConfig != null) {
            loadConfig(mConfig);
        }
        mScanResult = (ScanResult) savedState.getParcelable(KEY_SCANRESULT);
        if (mScanResult != null) {
            loadResult(mScanResult);
        }
        mInfo = (WifiInfo) savedState.getParcelable(KEY_WIFIINFO);
        if (savedState.containsKey(KEY_DETAILEDSTATE)) {
            mState = DetailedState.valueOf(savedState.getString(KEY_DETAILEDSTATE));
        }
        update(mInfo, mState);
    }

    public void saveWifiState(Bundle savedState) {
        savedState.putParcelable(KEY_CONFIG, mConfig);
        savedState.putParcelable(KEY_SCANRESULT, mScanResult);
        savedState.putParcelable(KEY_WIFIINFO, mInfo);
        if (mState != null) {
            savedState.putString(KEY_DETAILEDSTATE, mState.toString());
        }
    }

    private void loadConfig(WifiConfiguration config) {
        ssid = (config.SSID == null ? "" : removeDoubleQuotes(config.SSID));
        bssid = config.BSSID;
        security = getSecurity(config);
        networkId = config.networkId;
        mRssi = Integer.MAX_VALUE;
        mConfig = config;
    }

    private void loadResult(ScanResult result) {
        ssid = result.SSID;
        bssid = result.BSSID;
        security = getSecurity(result);
        wpsAvailable = security != SECURITY_EAP && result.capabilities.contains("WPS");
        if (security == SECURITY_PSK)
            pskType = getPskType(result);
        networkId = -1;
        mRssi = result.level;
        mScanResult = result;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        titleView = (TextView) view.findViewById(com.android.internal.R.id.title);
        summaryView = (TextView) view.findViewById(com.android.internal.R.id.summary);

        titleView.setSelected(true);
        //yanbing add WifiSetings 20190308 start
        updateIcon(getLevel(),mState);
        //yanbing add WifiSetings 20190308 end
    }

    @Override
    public int compareTo(Preference preference) {
        if (!(preference instanceof AccessPoint)) {
            return 1;
        }
        AccessPoint other = (AccessPoint) preference;
        // Active one goes first.
        if (mInfo != null && other.mInfo == null) return -1;
        if (mInfo == null && other.mInfo != null) return 1;

        // Reachable one goes before unreachable one.
        if (mRssi != Integer.MAX_VALUE && other.mRssi == Integer.MAX_VALUE) return -1;
        if (mRssi == Integer.MAX_VALUE && other.mRssi != Integer.MAX_VALUE) return 1;

        // Configured one goes before unconfigured one.
        if (networkId != WifiConfiguration.INVALID_NETWORK_ID
                && other.networkId == WifiConfiguration.INVALID_NETWORK_ID) return -1;
        if (networkId == WifiConfiguration.INVALID_NETWORK_ID
                && other.networkId != WifiConfiguration.INVALID_NETWORK_ID) return 1;

        // Sort by signal strength.
        int difference = WifiManager.compareSignalLevel(other.mRssi, mRssi);
        if (difference != 0) {
            return difference;
        }
        // Sort by ssid.
        return ssid.compareToIgnoreCase(other.ssid);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AccessPoint)) return false;
        /* SPRD: Modify Bug 309533 for not update correct ap because ignore it @{ */
        if (((AccessPoint) other).networkId != networkId) {
            return false;
        }
        /* @} */
        return (this.compareTo((AccessPoint) other) == 0);
    }

    @Override
    public int hashCode() {
        int result = 0;
        if (mInfo != null) result += 13 * mInfo.hashCode();
        result += 19 * mRssi;
        result += 23 * networkId;
        result += 29 * ssid.hashCode();
        return result;
    }

    boolean update(ScanResult result) {
        if (ssid.equals(result.SSID) && security == getSecurity(result)) {
            if (WifiManager.compareSignalLevel(result.level, mRssi) > 0) {
                int oldLevel = getLevel();
                mRssi = result.level;
                if (getLevel() != oldLevel) {
                    //yanbing add WifiSetings 20190308 start
                    updateIcon(getLevel(),mState);
                    //yanbing add WifiSetings 20190308 end
                    notifyChanged();
                }
            }
            // This flag only comes from scans, is not easily saved in config
            if (security == SECURITY_PSK) {
                pskType = getPskType(result);
            }
            refresh();
            return true;
        }
        return false;
    }



    void update(WifiInfo info, DetailedState state) {
        boolean reorder = false;
        if (info != null && networkId != WifiConfiguration.INVALID_NETWORK_ID
                && networkId == info.getNetworkId()) {
            reorder = (mInfo == null);
            int oldLevel = getLevel();
            //SPRD: Modify Bug 422591
            if (info.getSupplicantState() == SupplicantState.COMPLETED) {
                mRssi = info.getRssi();
            }
            if (getLevel() != oldLevel) {
                //yanbing add WifiSetings 20190308 start
                updateIcon(getLevel(),mState);
                //yanbing add WifiSetings 20190308 end
                notifyChanged();
            }
            mInfo = info;
            mState = state;
            refresh();
        } else if (mInfo != null) {
            reorder = true;
            mInfo = null;
            mState = null;
            refresh();
        }
        if (reorder) {
            notifyHierarchyChanged();
        }
    }

    //yanbing add WifiSetings 20190308 start
    protected void updateIcon(int level,DetailedState state) {
        if (level == -1) {
            return;
        }

        Drawable drawable;

        if (mRssi == Integer.MAX_VALUE) { // Wifi out of range
            if(titleView !=null &&summaryView!=null) {
                titleView.setTextColor(Color.RED);
                summaryView.setTextColor(Color.RED);
            }
        } else if (mConfig != null && mConfig.status == WifiConfiguration.Status.DISABLED) {
        /* @} */
            switch (mConfig.disableReason) {
                // Sprd: disableReason is set to be WifiConfiguration.DISABLED_AUTH_FAILURE after AP reject 16 times.
                // So We can set the same summary for the state to WifiConfiguration.DISABLED_AUTH_FAILURE.
                case WifiConfiguration.DISABLED_ASSOCIATION_REJECT:
                case WifiConfiguration.DISABLED_AUTH_FAILURE:
                    if(titleView !=null &&summaryView!=null) {
                        titleView.setTextColor(Color.RED);
                        summaryView.setTextColor(Color.RED);
                    }
                    break;
                case WifiConfiguration.DISABLED_DHCP_FAILURE:
                case WifiConfiguration.DISABLED_DNS_FAILURE:
                    if(titleView !=null &&summaryView!=null) {
                        titleView.setTextColor(Color.RED);
                        summaryView.setTextColor(Color.RED);
                    }
                    break;
                case WifiConfiguration.DISABLED_UNKNOWN_REASON:
                    if(titleView !=null &&summaryView!=null) {
                        titleView.setTextColor(Color.RED);
                        summaryView.setTextColor(Color.RED);
                    }
            }
        } else if (mState != null) { // This is the active connection
            if(titleView !=null &&summaryView!=null) {
                titleView.setTextColor(Color.RED);
                summaryView.setTextColor(Color.RED);
            }
        } else { // In range, not disabled.
            if (mConfig != null) {
                if (titleView != null && summaryView != null) {
                    titleView.setTextColor(Color.RED);
                    summaryView.setTextColor(Color.RED);
                }
            }
        }






        if(state !=null && state.ordinal() ==5){
            drawable = getWifiIcon(level, getContext().getTheme(), 3);
            if(titleView !=null &&summaryView!=null) {
                titleView.setTextColor(Color.GREEN);
                summaryView.setTextColor(Color.GREEN);
            }
        }else {
            if (security != SECURITY_NONE) {
                drawable = getWifiIcon(level, getContext().getTheme(), 1);
            } else {
                drawable = getWifiIcon(level, getContext().getTheme(), 2);
            }
        }

        if (drawable != null) {
            setIcon(drawable);
        }
    }

    public static Drawable getWifiIcon(int signalLevel,Theme theme,int num) {
        return Resources.getSystem().getDrawable(getWifiSignalResource(signalLevel,num));
    }

    private static int getWifiSignalResource(int signalLevel,int num) {
        if(num==1){
            switch (signalLevel) {
                case 0:
                    return com.android.internal.R.drawable.wifi_0;
                case 1:
                    return com.android.internal.R.drawable.wifi_lock_1;
                case 2:
                    return com.android.internal.R.drawable.wifi_lock_2;
                case 3:
                    return com.android.internal.R.drawable.wifi_lock_3;
                case 4:
                    return com.android.internal.R.drawable.wifi_lock_4;
                default:
                    throw new IllegalArgumentException("Invalid signal level: " + signalLevel);
            }
        }else if(num ==2){
            switch (signalLevel) {
                case 0:
                    return com.android.internal.R.drawable.wifi_0;
                case 1:
                    return com.android.internal.R.drawable.wifi_1;
                case 2:
                    return com.android.internal.R.drawable.wifi_2;
                case 3:
                    return com.android.internal.R.drawable.wifi_3;
                case 4:
                    return com.android.internal.R.drawable.wifi_4;
                default:
                    throw new IllegalArgumentException("Invalid signal level: " + signalLevel);
            }
        }else if(num==3){
            switch (signalLevel) {
                case 0:
                    return com.android.internal.R.drawable.wifi_0;
                case 1:
                    return com.android.internal.R.drawable.wifi_connected_1;
                case 2:
                    return com.android.internal.R.drawable.wifi_connected_2;
                case 3:
                    return com.android.internal.R.drawable.wifi_connected_3;
                case 4:
                    return com.android.internal.R.drawable.wifi_connected_4;
                default:
                    throw new IllegalArgumentException("Invalid signal level: " + signalLevel);
            }
        }
        return com.android.internal.R.drawable.wifi_0;
    }
    //yanbing add WifiSetings 20190308 end

    int getLevel() {
        if (mRssi == Integer.MAX_VALUE) {
            return -1;
        }
        //yanbing add WifiSetings 20190308 start
        return WifiManager.calculateSignalLevel(mRssi, 5);
        //yanbing add WifiSetings 20190308 end
    }

    WifiConfiguration getConfig() {
        return mConfig;
    }

    WifiInfo getInfo() {
        return mInfo;
    }

    DetailedState getState() {
        return mState;
    }

    static String removeDoubleQuotes(String string) {
        int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"')
                && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    static String convertToQuotedString(String string) {
        return "\"" + string + "\"";
    }

    /** Updates the title and summary; may indirectly call notifyChanged()  */
    private void refresh() {
        setTitle(ssid);

        updateIcon(getLevel(),mState);
        Context context = getContext();
        /* SPRD: Modify Bug 309540 for ap info display error @{ */
        if (mRssi == Integer.MAX_VALUE) { // Wifi out of range
            setSummary(context.getString(R.string.wifi_not_in_range));
        } else if (mConfig != null && mConfig.status == WifiConfiguration.Status.DISABLED) {
        /* @} */
            switch (mConfig.disableReason) {
                // Sprd: disableReason is set to be WifiConfiguration.DISABLED_AUTH_FAILURE after AP reject 16 times.
                // So We can set the same summary for the state to WifiConfiguration.DISABLED_AUTH_FAILURE.
                case WifiConfiguration.DISABLED_ASSOCIATION_REJECT:
                case WifiConfiguration.DISABLED_AUTH_FAILURE:
                    setSummary(context.getString(R.string.wifi_disabled_password_failure));
                    break;
                case WifiConfiguration.DISABLED_DHCP_FAILURE:
                case WifiConfiguration.DISABLED_DNS_FAILURE:
                    setSummary(context.getString(R.string.wifi_disabled_network_failure));
                    break;
                case WifiConfiguration.DISABLED_UNKNOWN_REASON:
                    setSummary(context.getString(R.string.wifi_disabled_generic));
            }
        } else if (mState != null) { // This is the active connection
            setSummary(Summary.get(context, mState));
        } else { // In range, not disabled.
            StringBuilder summary = new StringBuilder();
            if (mConfig != null) { // Is saved network
                summary.append(context.getString(R.string.wifi_remembered));
            }

            if (security != SECURITY_NONE) {
                //yanbing add WifiSetings 20190308 start
//                String securityStrFormat;
//                if (summary.length() == 0) {
//                    securityStrFormat = context.getString(R.string.wifi_secured_first_item);
//                } else {
//                    securityStrFormat = context.getString(R.string.wifi_secured_second_item);
//                }
//                summary.append(String.format(securityStrFormat, getSecurityString(true)));
                //yanbing add WifiSetings 20190308 end
            }

            if (mConfig == null && wpsAvailable) { // Only list WPS available for unsaved networks
                //yanbing add WifiSetings 20190308 start
//                if (summary.length() == 0) {
//                    summary.append(context.getString(R.string.wifi_wps_available_first_item));
//                } else {
//                    summary.append(context.getString(R.string.wifi_wps_available_second_item));
//                }
                //yanbing add WifiSetings 20190308 end
            }
            setSummary(summary.toString());
        }
    }

    /**
     * Generate and save a default wifiConfiguration with common values.
     * Can only be called for unsecured networks.
     * @hide
     */
    protected void generateOpenNetworkConfig() {
        if (security != SECURITY_NONE)
            throw new IllegalStateException();
        if (mConfig != null)
            return;
        mConfig = new WifiConfiguration();
        mConfig.SSID = AccessPoint.convertToQuotedString(ssid);
        mConfig.allowedKeyManagement.set(KeyMgmt.NONE);
    }
}
