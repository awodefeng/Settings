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

package com.android.settings;

import java.util.Locale;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;
import android.preference.CheckBoxPreference;

public class VoiceBroadcastSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceClickListener{

    private static final String TAG ="VoiceBroadcastSettings";
    private static final String KEY_VOICE_MENU = "voice_menu";
    private static final String KEY_VOICE_DATE = "voice_date";
    private static final String KEY_VOICE_CALL = "voice_call";
    private static final String KEY_VOICE_CONTACTS = "voice_contacts";
    private static final String KEY_VOICE_INCALL = "voice_incall";
    private static final String KEY_VOICE_MESSAGE = "voice_message";

    private CheckBoxPreference mVoiceMenu;
    private CheckBoxPreference mVoiceDate;
    private CheckBoxPreference mVoiceCall;
    private CheckBoxPreference mVoiceContacts;
    private CheckBoxPreference mVoiceInCall;
    private CheckBoxPreference mVoiceMessage;

    private ContentResolver mResolver;
    private static final int IS_OPEN = 1;
    private static final int IS_CLOSE = 0;

    // SPRD: Add for bug678643.
    private TextToSpeech mTts = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Activity activity = getActivity();
        mResolver = activity.getContentResolver();

        addPreferencesFromResource(R.xml.voice_boardcast_settings);

        mVoiceMenu = (CheckBoxPreference) findPreference(KEY_VOICE_MENU);
        mVoiceDate = (CheckBoxPreference) findPreference(KEY_VOICE_DATE);
        mVoiceCall = (CheckBoxPreference) findPreference(KEY_VOICE_CALL);
        mVoiceContacts = (CheckBoxPreference) findPreference(KEY_VOICE_CONTACTS);
        mVoiceInCall = (CheckBoxPreference) findPreference(KEY_VOICE_INCALL);
        mVoiceMessage = (CheckBoxPreference) findPreference(KEY_VOICE_MESSAGE);
        mVoiceMenu.setOnPreferenceClickListener(this);
        mVoiceDate.setOnPreferenceClickListener(this);
        mVoiceCall.setOnPreferenceClickListener(this);
        mVoiceContacts.setOnPreferenceClickListener(this);
        mVoiceInCall.setOnPreferenceClickListener(this);
        mVoiceMessage.setOnPreferenceClickListener(this);

        // SPRD: Add for bug678643.
        mTts = new TextToSpeech(getActivity(), mInitListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateState();
    }

    /* SPRD: Add for bug701938. @{ */
    @Override
    public void onDestroy() {
        if (mTts != null) {
            mTts.shutdown();
        }
        super.onDestroy();
    }
    /* @} */

    private void updateState() {
        try{
        Log.i(TAG,"menu:"+Settings.System.getInt(mResolver,
                Settings.System.VOICE_FOR_MENU));
        }catch(Exception e){
            Log.i(TAG,e+"");
        }
        Log.i(TAG,"date:"+Settings.System.getInt(mResolver,
                Settings.System.VOICE_FOR_DATE, IS_CLOSE));
        Log.i(TAG,"call:"+Settings.System.getInt(mResolver,
                Settings.System.VOICE_FOR_CALL, IS_CLOSE));
        Log.i(TAG,"contacts:"+Settings.System.getInt(mResolver,
                Settings.System.VOICE_FOR_CONTACTS, IS_CLOSE));
        Log.i(TAG,"incall:"+Settings.System.getInt(mResolver,
                Settings.System.VOICE_FOR_INCALL, IS_CLOSE));
        Log.i(TAG,"message:"+Settings.System.getInt(mResolver,
                Settings.System.VOICE_FOR_MESSAGE, IS_CLOSE));
        mVoiceMenu.setChecked(
                Settings.System.getInt(mResolver,
                        Settings.System.VOICE_FOR_MENU, IS_CLOSE) != IS_CLOSE);
        mVoiceDate.setChecked(
                Settings.System.getInt(mResolver,
                        Settings.System.VOICE_FOR_DATE, IS_CLOSE) != IS_CLOSE);
        mVoiceCall.setChecked(
                Settings.System.getInt(mResolver,
                        Settings.System.VOICE_FOR_CALL, IS_CLOSE) != IS_CLOSE);
        mVoiceContacts.setChecked(
                Settings.System.getInt(mResolver,
                        Settings.System.VOICE_FOR_CONTACTS, IS_CLOSE) != IS_CLOSE);
        mVoiceInCall.setChecked(
                Settings.System.getInt(mResolver,
                        Settings.System.VOICE_FOR_INCALL, IS_CLOSE) != IS_CLOSE);
        mVoiceMessage.setChecked(
                Settings.System.getInt(mResolver,
                        Settings.System.VOICE_FOR_MESSAGE, IS_CLOSE) != IS_CLOSE);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        final String key = preference.getKey();
        CheckBoxPreference pref = (CheckBoxPreference) preference;
        boolean isChecked = pref.isChecked();
        Log.i(TAG,key+":\t"+isChecked);
        if(KEY_VOICE_MENU.equals(key)){
            Settings.System.putInt(mResolver,
                         Settings.System.VOICE_FOR_MENU, isChecked ? IS_OPEN : IS_CLOSE);
        }else if(KEY_VOICE_DATE.equals(key)){
            Settings.System.putInt(mResolver,
                         Settings.System.VOICE_FOR_DATE, isChecked ? IS_OPEN : IS_CLOSE);
      	}else if(KEY_VOICE_CALL.equals(key)){
            Settings.System.putInt(mResolver,
                         Settings.System.VOICE_FOR_CALL, isChecked ? IS_OPEN : IS_CLOSE);
      	}else if(KEY_VOICE_CONTACTS.equals(key)){
            Settings.System.putInt(mResolver,
                         Settings.System.VOICE_FOR_CONTACTS, isChecked ? IS_OPEN : IS_CLOSE);
      	}else if(KEY_VOICE_INCALL.equals(key)){
            Settings.System.putInt(mResolver,
                         Settings.System.VOICE_FOR_INCALL, isChecked ? IS_OPEN : IS_CLOSE);
      	}else if(KEY_VOICE_MESSAGE.equals(key)){
            Settings.System.putInt(mResolver,
                         Settings.System.VOICE_FOR_MESSAGE, isChecked ? IS_OPEN : IS_CLOSE);
      	}
        return true;
    }
    /*
    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.DISPLAY;
    }*/

    /* SPRD: Add for bug678643. @{ */
    private final TextToSpeech.OnInitListener mInitListener = new TextToSpeech.OnInitListener() {
        @Override
        public void onInit(int status) {
            /* SPRD: Modify for bug701938. @{ */
            try {
                onInitEngine(status);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "onInit(): TextToSpeech throws an IllegalArgumentException!");
                e.printStackTrace();
            }
            /* @} */
        }
    };

    public void onInitEngine(int status) {
        int isAvailable = -1;
        Locale loc = null;

        if (mTts != null) {
            loc = mTts.getDefaultLanguage();
        }

        if (loc == null) {
            loc = Locale.getDefault();
        }

        if (mTts != null && loc != null) {
            isAvailable = mTts.isLanguageAvailable(loc);
        }

        // SPRD: Modify for bug689327.
        if (((isAvailable < TextToSpeech.LANG_AVAILABLE) || (status != TextToSpeech.SUCCESS)) && isAdded()) {
            Toast.makeText(getActivity(), getString(R.string.tts_status_not_supported, loc.getDisplayName()),
                    Toast.LENGTH_LONG).show();
        }
    }
    /* @} */
}
