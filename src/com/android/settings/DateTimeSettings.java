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

import android.app.Activity;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.text.format.DateFormat;
import android.widget.DatePicker;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
/* SPRD：ADD for Auto GPS time for bug320995 @{ */
import android.location.LocationManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.util.Log;
import android.app.ActivityManager;
import android.os.SystemProperties;
import android.view.KeyEvent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
/* @} */

public class DateTimeSettings extends SettingsPreferenceFragment
        implements OnSharedPreferenceChangeListener,
                TimePickerDialog.OnTimeSetListener, DatePickerDialog.OnDateSetListener,
                DialogInterface.OnClickListener, OnCancelListener {

    private static final String HOURS_12 = "12";
    private static final String HOURS_24 = "24";

    // Used for showing the current date format, which looks like "12/31/2010", "2010/12/13", etc.
    // The date value is dummy (independent of actual date).
    private Calendar mDummyDate;

    public static boolean ORIG_AUTOTIME_SUPPORT = SystemProperties.getBoolean("orig_autotime_support",true);
    private static final String KEY_DATE_FORMAT = "date_format";
    /* SPRD：ADD for Auto GPS time for bug320995 @{ */
    //orig
    private static final String KEY_AUTO_TIME_ORIG = "auto_time";
    private static final String TAG = "DateTimeSettings";
    public static boolean GPS_SUPPORT = !(SystemProperties.get("ro.wcn").equals("disabled"));
    private static final String KEY_AUTO_TIME = GPS_SUPPORT ? "auto_time_list":"auto_time_list_no_gps";
    /* @} */
    private static final String KEY_AUTO_TIME_ZONE = "auto_zone";

    private static final int DIALOG_DATEPICKER = 0;
    private static final int DIALOG_TIMEPICKER = 1;

    // have we been launched from the setup wizard?
    protected static final String EXTRA_IS_FIRST_RUN = "firstRun";

    /* SPRD：ADD for Auto GPS time for bug320995 @{ */
    private CheckBoxPreference mAutoTimeOrigPref;
    /* @} */
    private Preference mTimePref;
    private Preference mTime24Pref;
    private CheckBoxPreference mAutoTimeZonePref;
    private Preference mTimeZone;
    private Preference mDatePref;
    private ListPreference mDateFormat;
    // flag that need to show toast.
    private static boolean mIsSetDataTimeSucess = false;
    
    /* SPRD：ADD for Auto GPS time for bug320995 @{ */
    private ListPreference mAutoTimePref;
    private static final int DIALOG_GPS_CONFIRM = 2;
    private static final int AUTO_TIME_NETWORK_INDEX = 0;
    private static final int AUTO_TIME_GPS_INDEX = 1;
    private static final int AUTO_TIME_OFF_INDEX = GPS_SUPPORT ? 2 : 1;
    /* @} */

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.date_time_prefs);

        initUI();
    }

    private void initUI() {
        boolean autoTimeEnabled = getAutoState(Settings.Global.AUTO_TIME);
        boolean autoTimeZoneEnabled = getAutoState(Settings.Global.AUTO_TIME_ZONE);
        /* SPRD：ADD for Auto GPS time for bug320995 @{ */
        boolean autoTimeGpsEnabled = getAutoState(Settings.Global.AUTO_TIME_GPS);
        /* @} */
        Intent intent = getActivity().getIntent();
        boolean isFirstRun = intent.getBooleanExtra(EXTRA_IS_FIRST_RUN, false);

        mDummyDate = Calendar.getInstance();
        /* SPRD：ADD for Auto GPS time for bug320995 @{ */
        mAutoTimePref = (ListPreference) findPreference(KEY_AUTO_TIME);
        if (autoTimeEnabled) {
            mAutoTimePref.setValueIndex(AUTO_TIME_NETWORK_INDEX);
        } else if (GPS_SUPPORT && autoTimeGpsEnabled) {
            mAutoTimePref.setValueIndex(AUTO_TIME_GPS_INDEX);
        } else {
            mAutoTimePref.setValueIndex(AUTO_TIME_OFF_INDEX);
        }
        mAutoTimePref.setSummary(mAutoTimePref.getValue());

        //orig
        mAutoTimeOrigPref = (CheckBoxPreference) findPreference(KEY_AUTO_TIME_ORIG);
        mAutoTimeOrigPref.setChecked(autoTimeEnabled);
        /* @} */
        
        mAutoTimeZonePref = (CheckBoxPreference) findPreference(KEY_AUTO_TIME_ZONE);
        // Override auto-timezone if it's a wifi-only device or if we're still in setup wizard.
        // TODO: Remove the wifiOnly test when auto-timezone is implemented based on wifi-location.
        if (Utils.isWifiOnly(getActivity()) || isFirstRun) {
            getPreferenceScreen().removePreference(mAutoTimeZonePref);
            autoTimeZoneEnabled = false;
        }
        mAutoTimeZonePref.setChecked(autoTimeZoneEnabled);

        mTimePref = findPreference("time");
        mTime24Pref = findPreference("24 hour");
        mTimeZone = findPreference("timezone");
        mDatePref = findPreference("date");
        mDateFormat = (ListPreference) findPreference(KEY_DATE_FORMAT);
        if (isFirstRun) {
            getPreferenceScreen().removePreference(mTime24Pref);
            getPreferenceScreen().removePreference(mDateFormat);
        }
        if(null != mDateFormat){
            getPreferenceScreen().removePreference(mDateFormat);
        }
        /* SPRD: Modify Bug 207845, move data code to updateDateFormatDisplay() function @{
         * @orig
        String [] dateFormats = getResources().getStringArray(R.array.date_format_values);
        String [] formattedDates = new String[dateFormats.length];
        String currentFormat = getDateFormat();
        // Initialize if DATE_FORMAT is not set in the system settings
        // This can happen after a factory reset (or data wipe)
        if (currentFormat == null) {
            currentFormat = "";
        }

        // Prevents duplicated values on date format selector.
        mDummyDate.set(mDummyDate.get(Calendar.YEAR), mDummyDate.DECEMBER, 31, 13, 0, 0);

        for (int i = 0; i < formattedDates.length; i++) {
            String formatted =
                    DateFormat.getDateFormatForSetting(getActivity(), dateFormats[i])
                    .format(mDummyDate.getTime());

            if (dateFormats[i].length() == 0) {
                formattedDates[i] = getResources().
                    getString(R.string.normal_date_format, formatted);
            } else {
                formattedDates[i] = formatted;
            }
        }

        mDateFormat.setEntries(formattedDates);
        mDateFormat.setEntryValues(R.array.date_format_values);
        mDateFormat.setValue(currentFormat);
        */
        updateDateFormatDisplay();
        /* @} */

        /* SPRD：ADD for Auto GPS time for bug320995 @{ */
        if(ORIG_AUTOTIME_SUPPORT){
            //orig
            removePreference("auto_time_list_no_gps");
            removePreference("auto_time_list");
            mTimePref.setEnabled(!autoTimeEnabled);
            mDatePref.setEnabled(!autoTimeEnabled);
            mTimeZone.setEnabled(!autoTimeZoneEnabled);
        } else {
            boolean autoEnabled = autoTimeEnabled || autoTimeGpsEnabled;

            mTimePref.setEnabled(!autoEnabled);
            mDatePref.setEnabled(!autoEnabled);
            mTimeZone.setEnabled(!autoTimeZoneEnabled);

            if (GPS_SUPPORT) {
                removePreference("auto_time_list_no_gps");
            } else {
                removePreference("auto_time_list");
            }
            if(null != mAutoTimeOrigPref){
                getPreferenceScreen().removePreference(mAutoTimeOrigPref);
            }
        }

        /* @} */
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);

        ((CheckBoxPreference)mTime24Pref).setChecked(is24Hour());

        // Register for time ticks and other reasons for time change
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getActivity().registerReceiver(mIntentReceiver, filter, null, null);

        updateTimeAndDateDisplay(getActivity());
        
        /* SPRD：ADD for Auto GPS time for bug320995 @{ */
        updateDateFormatDisplay();
        /* @} */
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mIntentReceiver);
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    public void updateTimeAndDateDisplay(Context context) {
        java.text.DateFormat shortDateFormat = DateFormat.getDateFormat(context);
        final Calendar now = Calendar.getInstance();
        mDummyDate.setTimeZone(now.getTimeZone());
        // We use December 31st because it's unambiguous when demonstrating the date format.
        // We use 13:00 so we can demonstrate the 12/24 hour options.
        /* SPRD: Modify Bug 207845,replace format function @{
         * @orig
        mDummyDate.set(now.get(Calendar.YEAR), 11, 31, 13, 0, 0);
        */
        mDummyDate.set(now.get(Calendar.YEAR), now.get(Calendar.MONDAY),now.get(Calendar.DAY_OF_MONTH),13,0,0);
        /* @} */
        Date dummyDate = mDummyDate.getTime();
        mTimePref.setSummary(DateFormat.getTimeFormat(getActivity()).format(now.getTime()));
        mTimeZone.setSummary(getTimeZoneText(now.getTimeZone()));
        mDatePref.setSummary(shortDateFormat.format(now.getTime()));
        mDateFormat.setSummary(shortDateFormat.format(dummyDate));
        mTime24Pref.setSummary(DateFormat.getTimeFormat(getActivity()).format(dummyDate));
        // SPRD: dateFormat change ,then update dateFormatDiaplay
        updateDateFormatDisplay();
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int day) {
        final Activity activity = getActivity();
        if (activity != null) {
            setDate(activity, year, month, day);
            isNeedshowToast();
            updateTimeAndDateDisplay(activity);
            /* SPRD: Modify Bug 207845,update date format display @{ */
             updateDateFormatDisplay();
            /* @} */
        }
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        final Activity activity = getActivity();
        if (activity != null) {
            setTime(activity, hourOfDay, minute);
            isNeedshowToast();
            updateTimeAndDateDisplay(activity);
        }

        // We don't need to call timeUpdated() here because the TIME_CHANGED
        // broadcast is sent by the AlarmManager as a side effect of setting the
        // SystemClock time.
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (key.equals(KEY_DATE_FORMAT)) {
            String format = preferences.getString(key,
                    getResources().getString(R.string.default_date_format));
            Settings.System.putString(getContentResolver(),
                    Settings.System.DATE_FORMAT, format);
            updateTimeAndDateDisplay(getActivity());
        } else if (key.equals(KEY_AUTO_TIME)) {
        	/* SPRD：ADD for Auto GPS time for bug320995 @{ */
        	//boolean autoEnabled = preferences.getBoolean(key, true);
            //Settings.Global.putInt(getContentResolver(), Settings.Global.AUTO_TIME,
            //        autoEnabled ? 1 : 0);            
            String value = mAutoTimePref.getValue();
            int index = mAutoTimePref.findIndexOfValue(value);
            mAutoTimePref.setSummary(value);
            boolean autoEnabled = true;

            if (index == AUTO_TIME_NETWORK_INDEX) {
                Settings.Global.putInt(getContentResolver(),
                        Settings.Global.AUTO_TIME, 1);
                Settings.Global.putInt(getContentResolver(),
                        Settings.Global.AUTO_TIME_GPS, 0);
            } else if (GPS_SUPPORT && index == AUTO_TIME_GPS_INDEX) {
                showDialog(DIALOG_GPS_CONFIRM);
                //setOnCancelListener(this);
            } else {
                Settings.Global.putInt(getContentResolver(), Settings.Global.AUTO_TIME, 0);
                Settings.Global.putInt(getContentResolver(), Settings.Global.AUTO_TIME_GPS, 0);
                autoEnabled = false;
            }
            /* @} */
            mTimePref.setEnabled(!autoEnabled);
            mDatePref.setEnabled(!autoEnabled);
        } else if(key.equals(KEY_AUTO_TIME_ORIG)){
              boolean autoEnabled = preferences.getBoolean(key, true);
              Settings.Global.putInt(getContentResolver(), Settings.Global.AUTO_TIME,
                    autoEnabled ? 1 : 0);
              mTimePref.setEnabled(!autoEnabled);
              mDatePref.setEnabled(!autoEnabled);
        } else if (key.equals(KEY_AUTO_TIME_ZONE)) {
            boolean autoZoneEnabled = preferences.getBoolean(key, true);
            Settings.Global.putInt(
                    getContentResolver(), Settings.Global.AUTO_TIME_ZONE, autoZoneEnabled ? 1 : 0);
            mTimeZone.setEnabled(!autoZoneEnabled);
        }
    }

    @Override
    public Dialog onCreateDialog(int id) {
        final Calendar calendar = Calendar.getInstance();
        switch (id) {
        case DIALOG_DATEPICKER:
            DatePickerDialog d = new DatePickerDialog(
                    getActivity(),
                    android.R.style.Theme_Holo_Light_Dialog,
                    this,
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            configureDatePicker(d.getDatePicker());
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
             //SPRD add for bug622181
             setDateListener(d);
             return d;
        case DIALOG_TIMEPICKER:
            TimePickerDialog timePickerDialog = new TimePickerDialog(
                    getActivity(),
                    android.R.style.Theme_Holo_Light_Dialog,
                    this,
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(getActivity()));
                    timePickerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    //SPRD add for bug622181
                    setTimeListener(timePickerDialog);
                    return timePickerDialog;
        /* SPRD：ADD for Auto GPS time for bug320995 @{ */
        case DIALOG_GPS_CONFIRM: {
            int msg;
            Dialog dGps;
            
            LocationManager mLocationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
            boolean gpsEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            
            if (gpsEnabled) {
                msg = R.string.gps_time_sync_attention_gps_on;
            } else {
                msg = R.string.gps_time_sync_attention_gps_off;
            }
            dGps = new AlertDialog.Builder(getActivity()).setMessage(
                    getActivity().getResources().getString(msg)).setIcon(
                            android.R.drawable.ic_dialog_alert).setTitle(
                    R.string.proxy_error).setPositiveButton(
                    android.R.string.yes, (OnClickListener) this).setNegativeButton(
                    android.R.string.no, (OnClickListener) this)./*setOnCancelListener((OnCancelListener) this).*/create();
            // SPRD: not allowed to touch outside dismiss dialog
            dGps.setCanceledOnTouchOutside(false);
            dGps.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event)
                {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        Log.d(TAG, "setOnKeyListener KeyEvent.KEYCODE_BACK");
                        reSetAutoTimePref();
                    }
                    return false; //default return false
                }
            });
            return dGps;    
            }
        /* @} */
        default:
            throw new IllegalArgumentException();
        }
    }

    static void configureDatePicker(DatePicker datePicker) {
        // The system clock can't represent dates outside this range.
        Calendar t = Calendar.getInstance();
        t.clear();
        t.set(1970, Calendar.JANUARY, 1);
        datePicker.setMinDate(t.getTimeInMillis());
        t.clear();
        t.set(2037, Calendar.DECEMBER, 31);
        datePicker.setMaxDate(t.getTimeInMillis());
    }

    /*
    @Override
    public void onPrepareDialog(int id, Dialog d) {
        switch (id) {
        case DIALOG_DATEPICKER: {
            DatePickerDialog datePicker = (DatePickerDialog)d;
            final Calendar calendar = Calendar.getInstance();
            datePicker.updateDate(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            break;
        }
        case DIALOG_TIMEPICKER: {
            TimePickerDialog timePicker = (TimePickerDialog)d;
            final Calendar calendar = Calendar.getInstance();
            timePicker.updateTime(
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE));
            break;
        }
        default:
            break;
        }
    }
    */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mDatePref) {
            showDialog(DIALOG_DATEPICKER);
        } else if (preference == mTimePref) {
            // The 24-hour mode may have changed, so recreate the dialog
            removeDialog(DIALOG_TIMEPICKER);
            showDialog(DIALOG_TIMEPICKER);
        } else if (preference == mTime24Pref) {
            set24Hour(((CheckBoxPreference)mTime24Pref).isChecked());
            updateTimeAndDateDisplay(getActivity());
            timeUpdated();
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        updateTimeAndDateDisplay(getActivity());
    }

    private void timeUpdated() {
        Intent timeChanged = new Intent(Intent.ACTION_TIME_CHANGED);
        getActivity().sendBroadcast(timeChanged);
    }

    /*  Get & Set values from the system settings  */

    private boolean is24Hour() {
        return DateFormat.is24HourFormat(getActivity());
    }

    private void set24Hour(boolean is24Hour) {
        Settings.System.putString(getContentResolver(),
                Settings.System.TIME_12_24,
                is24Hour? HOURS_24 : HOURS_12);
    }

    private String getDateFormat() {
        return Settings.System.getString(getContentResolver(),
                Settings.System.DATE_FORMAT);
    }

    private boolean getAutoState(String name) {
        try {
            return Settings.Global.getInt(getContentResolver(), name) > 0;
        } catch (SettingNotFoundException snfe) {
            return false;
        }
    }

    /* package */ static void setDate(Context context, int year, int month, int day) {
        Calendar c = Calendar.getInstance();

        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month);
        c.set(Calendar.DAY_OF_MONTH, day);
        long when = c.getTimeInMillis();
        mIsSetDataTimeSucess = false;
        if ((when > 0) && (when / 1000 < Integer.MAX_VALUE)) {
            mIsSetDataTimeSucess = true;
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setTime(when);
        }
    }

    /* package */ static void setTime(Context context, int hourOfDay, int minute) {
        Calendar c = Calendar.getInstance();

        // SPRD: bug320576 second set to 0 when set time
        c.set(Calendar.HOUR_OF_DAY, hourOfDay);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 1);
        long when = c.getTimeInMillis();
        mIsSetDataTimeSucess = false;
        if ((when > 0) && (when / 1000 < Integer.MAX_VALUE)) {
            mIsSetDataTimeSucess = true;
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setTime(when);
        }
    }

    private static String getTimeZoneText(TimeZone tz) {
        SimpleDateFormat sdf = new SimpleDateFormat("ZZZZ, zzzz");
        sdf.setTimeZone(tz);
        return sdf.format(new Date());
    }

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Activity activity = getActivity();
            if (activity != null) {
                updateTimeAndDateDisplay(activity);
            }
        }
    };
    /**
     * SPRD:Modify Bug 207845,add date format. @{
     */
    private void updateDateFormatDisplay() {
        String[] dateFormats = getResources().getStringArray(
                R.array.date_format_values);
        String[] formattedDates = new String[dateFormats.length];
        String currentFormat = getDateFormat();
        // Initialize if DATE_FORMAT is not set in the system settings
        // This can happen after a factory reset (or data wipe)
        if (currentFormat == null) {
            currentFormat = "";
        }
        for (int i = 0; i < formattedDates.length; i++) {
            String formatted = DateFormat.getDateFormatForSetting(
                    getActivity(), dateFormats[i]).format(mDummyDate.getTime());

            if (dateFormats[i].length() == 0) {
                formattedDates[i] = getResources().getString(
                        R.string.normal_date_format, formatted);
            } else {
                formattedDates[i] = formatted;
            }
        }

        mDateFormat.setEntries(formattedDates);
        mDateFormat.setEntryValues(R.array.date_format_values);
        mDateFormat.setValue(currentFormat);
    }
    /** @} */

    /*
     * check is need show toast.
     */
    private void isNeedshowToast() {
         if (!mIsSetDataTimeSucess) {
             Toast.makeText(getActivity(), R.string.toast_datatime_error,Toast.LENGTH_SHORT).show();
         }
     }
    
    /* SPRD：ADD for Auto GPS time for bug320995 @{ */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            Log.d(TAG, "Enable GPS time sync");
            //boolean gpsEnabled = Settings.Secure.isLocationProviderEnabled(
            //        getContentResolver(), LocationManager.GPS_PROVIDER);
            LocationManager mLocationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
            boolean gpsEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if (!gpsEnabled) {
            	Log.d(TAG, "Enable GPS time sync gpsEnabled =" + gpsEnabled);
            	//mLocationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            	/*
            	Settings.Secure.setLocationProviderEnabled(
                        getContentResolver(), LocationManager.GPS_PROVIDER,
                        true);
                */
            	int currentUserId = ActivityManager.getCurrentUser();
		        Settings.Secure.putIntForUser(getContentResolver(), Settings.Secure.LOCATION_MODE, 
		        		Settings.Secure.LOCATION_MODE_SENSORS_ONLY, currentUserId);
            }
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.AUTO_TIME, 0);
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.AUTO_TIME_GPS, 1);
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            Log.d(TAG, "DialogInterface.BUTTON_NEGATIVE");
            reSetAutoTimePref();
        }
    }
    private void reSetAutoTimePref() {
        Log.d(TAG, "reset AutoTimePref as cancel the selection");
        boolean autoTimeEnabled = getAutoState(Settings.Global.AUTO_TIME);
        boolean autoTimeGpsEnabled = getAutoState(Settings.Global.AUTO_TIME_GPS);
        if (autoTimeEnabled) {
            mAutoTimePref.setValueIndex(AUTO_TIME_NETWORK_INDEX);
        } else if (GPS_SUPPORT && autoTimeGpsEnabled) {
            mAutoTimePref.setValueIndex(AUTO_TIME_GPS_INDEX);
        } else {
            mAutoTimePref.setValueIndex(AUTO_TIME_OFF_INDEX);
        }
        mAutoTimePref.setSummary(mAutoTimePref.getValue());
    }

    @Override
    public void onCancel(DialogInterface arg0) {
        Log.d(TAG, "onCancel Dialog");
        reSetAutoTimePref();
    }
    /* @} */

    /* SPRD add for bug622181 start*/
    public void setTimeListener(TimePickerDialog timePickerDialog){
        final TimePicker timePicker = timePickerDialog.getTime();
            timePickerDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
            Log.d(TAG, "time keyCode = "+keyCode+" CurrentHour = "+timePicker.getCurrentHour()+" CurrentMinute = "+timePicker.getCurrentMinute());
                if ((keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                    onTimeSet(timePicker, timePicker.getCurrentHour().intValue(), timePicker.getCurrentMinute().intValue());
                    dialog.dismiss();
                } else if((keyCode == KeyEvent.KEYCODE_BACK) && (event.getAction() == KeyEvent.ACTION_UP)) {
                    dialog.dismiss();
                }
                return false;
            }
        });
    }

    public void setDateListener(DatePickerDialog datePickerDialog){
        final DatePicker datePicker = datePickerDialog.getDatePicker();
        datePickerDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
             Log.d(TAG, "date keyCode = "+keyCode+" year = "+datePicker.getYear()+" month = "+datePicker.getMonth()+" date = "+datePicker.getDayOfMonth());
                if ((keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                    onDateSet(datePicker, datePicker.getYear(), datePicker.getMonth(), datePicker.getDayOfMonth());
                    dialog.dismiss();
                } else if((keyCode == KeyEvent.KEYCODE_BACK) && (event.getAction() == KeyEvent.ACTION_UP)) {
                    dialog.dismiss();
                }
                return false;
            }
        });
   }
    /* SPRD add for bug622181 end*/
}
