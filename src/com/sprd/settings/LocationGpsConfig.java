/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.sprd.settings;

import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.text.TextUtils;
import android.util.Log;
import android.provider.Settings;

import java.io.File;
import java.io.FileOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import com.android.settings.R;
import com.android.settings.R.xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class LocationGpsConfig extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "LocationGpsConfig";
    private static final String GPS_CSR_CONFIG_FILE = "/data/cg/supl/supl.xml";
    private EditTextPreference mSuplHost;
    private EditTextPreference mSuplPort;
    private EditTextPreference mCertPath;
    private ListPreference mlocationMethod;
    private ListPreference mStartMode;
    private ListPreference mConnectionType;
    private ListPreference mSuplServer;
    private LocationManager manager;
    private ListPreference mAgpsSetting;

    private static final int UPDATE_SUMMERY = 0;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.location_gps_config);
        mSuplServer = (ListPreference) findPreference("location_suplserver");
        mSuplServer.setOnPreferenceChangeListener(this);
        mSuplHost = (EditTextPreference) findPreference("supl_host");
        mSuplHost.setEnabled(false);
        mSuplPort = (EditTextPreference) findPreference("supl_port");
        mSuplPort.setEnabled(false);
        mCertPath = (EditTextPreference) findPreference("cert_path");
        mCertPath.setOnPreferenceChangeListener(this);
        mlocationMethod = (ListPreference) findPreference("location_method");
        mlocationMethod.setOnPreferenceChangeListener(this);
        mAgpsSetting = (ListPreference) findPreference("agps_setting");
        getPreferenceScreen().removePreference(mAgpsSetting);
        manager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String tmp = null;

        /* SPRD: Modify 20140625 Spreadst of Bug 327052 mSuplServer default supl is show monternet @{ */
        tmp = getGpsConfigration(GPS_CSR_CONFIG_FILE, "SERVER-ADDRESS");
        if (tmp != null && !TextUtils.isEmpty(tmp)) {
            int index = -1;
            String summary = "";
            if ("supl.monternet.com".equals(tmp)) {
                index = 0;
                summary = "MONTERNET";
            } else if ("supl.google.com".equals(tmp)) {
                index = 1;
                summary = "GOOGLE";
            } else if ("supl.nokia.com".equals(tmp)) {
                index = 2;
                summary = "NOKIA";
            }
            Log.d(TAG, " setmSuplServerIndex is " + index);
            if (index >= 0) {
                mSuplServer.setValueIndex(index);
                mSuplServer.setSummary(summary);
            }
        }
        /* @} */

        tmp = getGpsConfigration(GPS_CSR_CONFIG_FILE, "SERVER-ADDRESS");
        mSuplHost.setText(tmp);
        mSuplHost.setSummary(tmp);

        tmp = getGpsConfigration(GPS_CSR_CONFIG_FILE, "SERVER-PORT");
        mSuplPort.setText(tmp);
        mSuplPort.setSummary(tmp);

        tmp = getGpsConfigration(GPS_CSR_CONFIG_FILE, "SUPL-CER");
        mCertPath.setText(tmp);
        mCertPath.setSummary(tmp);

        tmp = getGpsConfigration(GPS_CSR_CONFIG_FILE, "ENABLE");
        mAgpsSetting.setSummary("TRUE".equals(tmp) ? R.string.open : R.string.close);
        mAgpsSetting.setValueIndex("TRUE".equals(tmp)? 0 : 1);

        tmp = getGpsConfigration(GPS_CSR_CONFIG_FILE, "SUPL-MODE");
        if (Settings.Global.getInt(getContentResolver(), Settings.Global.ASSISTED_GPS_ENABLED, 1) != 0) {
            mlocationMethod.setEnabled(true);
        } else {
            mlocationMethod.setEnabled(false);
        }
        if (tmp != null) {
            mlocationMethod.setSummary(tmp);
        }

        /*
         * tmp = getGpsConfigration(GPS_CSR_CONFIG_FILE, "START_MODE");
         * mStartMode = (ListPreference) findPreference("start_mode");
         * mStartMode.setOnPreferenceChangeListener(this); if (tmp != null)
         * mStartMode.setValue(tmp); else mStartMode.setValue("16");
         * mStartMode.setSummary(mStartMode.getEntry()); tmp =
         * getGpsConfigration(GPS_CSR_CONFIG_FILE, "AGNSS_IS_SECURE");
         * mConnectionType = (ListPreference) findPreference("connection_type");
         * mConnectionType.setOnPreferenceChangeListener(this); if (tmp != null)
         * mConnectionType.setValue(tmp); else mConnectionType.setValue("1");
         * mConnectionType.setSummary(mConnectionType.getEntry());
         */
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        // TODO Auto-generated method stub
        if ("supl_manual_settings".equals(preference.getKey())) {
            Intent intent = new Intent(LocationGpsConfig.this, AGpsSuplSettings.class);
            intent.putExtra("SERVER-ADDRESS", mSuplHost.getSummary());
            intent.putExtra("SERVER-PORT", mSuplPort.getSummary());
            startActivityForResult(intent, 0);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        if (requestCode == 0 && resultCode == 0 && data != null) {
            Bundle bdlData = data.getExtras();
            String suplSvr = bdlData.getString("supl_svr");
            String suplPort = bdlData.getString("supl_port");
            Log.v(TAG, "suplSvr = " + suplSvr + " suplPort = " + suplPort);

            int portVal = Integer.parseInt(suplPort);
            myHandler.obtainMessage(UPDATE_SUMMERY, portVal, 0, suplSvr).sendToTarget();
            setGpsConfigration(GPS_CSR_CONFIG_FILE, "SERVER-ADDRESS", suplSvr);
            setGpsConfigration(GPS_CSR_CONFIG_FILE, "SERVER-PORT", suplPort);
            manager.setAgpsServer(LocationManager.GPS_PROVIDER, 1,
                    suplSvr, portVal);
        }
    }

    Handler myHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            switch (msg.what) {
                case UPDATE_SUMMERY:
                    int port = msg.arg1;
                    String svrPort = String.valueOf(port);
                    String svrAddr = (String) msg.obj;
                    mSuplHost.setSummary(svrAddr);
                    mSuplPort.setSummary(svrPort);
                    mSuplServer.setSummary("");
                    break;
                default:
                    break;
            }
        }
    };

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        String strNewValue = (String) newValue;
        if (key == null)
            return true;
        /*
         * if (key.equals("supl_host")) { if
         * (!setGpsConfigration(GPS_CSR_CONFIG_FILE, "acSuplServer", (String)
         * newValue)) { Log.e(TAG, "config AGNSS_ADDRESS error"); return false;
         * } mSuplHost.setSummary(strNewValue); } else if
         * (key.equals("supl_port")) { if
         * (!setGpsConfigration(GPS_CSR_CONFIG_FILE, "SuplPort", (String)
         * newValue)) { Log.e(TAG, "config AGNSS_PORT error"); return false; }
         * mSuplPort.setSummary(strNewValue); } else
         */
        if (key.equals("cert_path")) {
            setGpsConfigration(GPS_CSR_CONFIG_FILE, "SUPL-CER", (String) newValue);
            mCertPath.setSummary(strNewValue);
        } else if (key.equals("location_suplserver")) {
            if (strNewValue.equals("0")) {
                mSuplServer.setSummary("MONTERNET");
                manager.setAgpsServer(LocationManager.GPS_PROVIDER, 1,
                        "supl.monternet.com", 7275);
                setGpsConfigration(GPS_CSR_CONFIG_FILE, "SERVER-ADDRESS", "supl.monternet.com");
                setGpsConfigration(GPS_CSR_CONFIG_FILE, "SERVER-PORT", "7275");
                mSuplHost.setSummary("supl.monternet.com");
                mSuplPort.setSummary("7275");
            } else if (strNewValue.equals("1")) {
                mSuplServer.setSummary("GOOGLE");
                manager.setAgpsServer(LocationManager.GPS_PROVIDER, 1,
                        "supl.google.com", 7275);
                setGpsConfigration(GPS_CSR_CONFIG_FILE, "SERVER-ADDRESS", "supl.google.com");
                setGpsConfigration(GPS_CSR_CONFIG_FILE, "SERVER-PORT", "7275");
                mSuplHost.setSummary("supl.google.com");
                mSuplPort.setSummary("7275");
            } else if (strNewValue.equals("2")) {
                mSuplServer.setSummary("NOKIA");
                manager.setAgpsServer(LocationManager.GPS_PROVIDER, 1,
                        "supl.nokia.com", 7275);
                setGpsConfigration(GPS_CSR_CONFIG_FILE, "SERVER-ADDRESS", "supl.nokia.com");
                setGpsConfigration(GPS_CSR_CONFIG_FILE, "SERVER-PORT", "7275");
                mSuplHost.setSummary("supl.nokia.com");
                mSuplPort.setSummary("7275");
            }
        } else if (key.equals("location_method")) {
            setGpsConfigration(GPS_CSR_CONFIG_FILE, "SUPL-MODE", (String) newValue);
            if (strNewValue.equals("1")) {
                mlocationMethod.setValue("1");
                mlocationMethod.setSummary(mlocationMethod.getEntry());
                manager.setPostionMode(LocationManager.GPS_PROVIDER, 1);
            } else if (strNewValue.equals("2")) {
                mlocationMethod.setValue("2");
                mlocationMethod.setSummary(mlocationMethod.getEntry());
                manager.setPostionMode(LocationManager.GPS_PROVIDER, 2);
            }
            setGpsConfigration(GPS_CSR_CONFIG_FILE, "SUPL-MODE", mlocationMethod.getEntry()
                    .toString());
        } else if (key.equals("agps_setting")){
            if (strNewValue.equals("1")) {
                mAgpsSetting.setValue("1");
                mAgpsSetting.setSummary(mAgpsSetting.getEntry());
                setGpsConfigration(GPS_CSR_CONFIG_FILE, "ENABLE", "TRUE");
            } else if (strNewValue.equals("2")){
                mAgpsSetting.setValue("2");
                mAgpsSetting.setSummary(mAgpsSetting.getEntry());
                setGpsConfigration(GPS_CSR_CONFIG_FILE, "ENABLE", "FALSE");
            }
        }/*
          * else if (key.equals("start_mode")) { if
          * (!setGpsConfigration(GPS_CSR_CONFIG_FILE, "START_MODE", (String)
          * newValue)) { Log.e(TAG, "config START_MODE error"); return false; }
          * if (strNewValue.equals("0")) { mStartMode.setSummary("HOT"); } else
          * if (strNewValue.equals("2")) { mStartMode.setSummary("WARM"); } else
          * if (strNewValue.equals("4")) { mStartMode.setSummary("COLD"); } else
          * if (strNewValue.equals("16")) { mStartMode.setSummary("AUTO"); } }
          * else if (key.equals("connection_type")) { if
          * (!setGpsConfigration(GPS_CSR_CONFIG_FILE, "AGNSS_IS_SECURE",
          * (String) newValue)) { Log.e(TAG, "config AGNSS_IS_SECURE error");
          * return false; } if (strNewValue.equals("0")) {
          * mConnectionType.setSummary("No secure"); } else if
          * (strNewValue.equals("1")) { mConnectionType.setSummary("Secure"); }
          * }
          */

        return true;
    }

    private String getGpsConfigration(String path, String key) {
        String tmpConfig = null;
        tmpConfig = getAttrVal(path, "PROPERTY", key);
        return tmpConfig;
    }

    private boolean setGpsConfigration(String path, String key, String newValue) {
        boolean setValues = setAttrVal(path, "PROPERTY", key, newValue);
        if (!setValues) {
            Log.e(TAG, "config " + key + " error");
            return false;
        }
        return setValues;
    }

    /**
     * @param path the xml file entire path
     * @return the attribute value of the key.
     */
    private String getAttrVal(String path, String elementName, String key) {
        String val = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            File gpsconfig = new File(path);
            Document doc = db.parse(gpsconfig);

            NodeList list = doc.getElementsByTagName(elementName);
            for (int i = 0; i < list.getLength(); i++) {
                Element element = (Element) list.item(i);
                if (element.getAttribute("NAME").equals(key)) {
                    val = element.getAttribute("VALUE");
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception :", e);
        }
        return val;
    }

    /**
     * @param path the xml file entire path
     * @param newVal the new value will be set.
     * @return true if set attribute value successfully, false otherwise.
     */
    private boolean setAttrVal(String path, String elementName, String key, String newVal) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            File gpsconfig = new File(path);
            Document doc = db.parse(gpsconfig);

            NodeList list = doc.getElementsByTagName(elementName);
            for (int i = 0; i < list.getLength(); i++) {
                Element element = (Element) list.item(i);
                if (element.getAttribute("NAME").equals(key)) {
                    element.setAttribute("VALUE", newVal);
                    break;
                }
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource domSource = new DOMSource(doc);
            transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
            StreamResult result = new StreamResult(new FileOutputStream(path));
            transformer.transform(domSource, result);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Exception :", e);
            return false;
        }
    }
}
