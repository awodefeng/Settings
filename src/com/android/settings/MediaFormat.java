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
import android.content.Intent;
import android.os.Bundle;
import android.os.storage.StorageVolume;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.android.internal.os.storage.ExternalStorageFormatter;
import com.sprd.android.support.featurebar.FeatureBarHelper;

/**
 * Confirm and execute a format of the sdcard.
 * Multiple confirmations are required: first, a general "are you sure
 * you want to do this?" prompt, followed by a keyguard pattern trace if the user
 * has defined one, followed by a final strongly-worded "THIS WILL ERASE EVERYTHING
 * ON THE SD CARD" prompt.  If at any time the phone is allowed to go to sleep, is
 * locked, et cetera, then the confirmation sequence is abandoned.
 */
public class MediaFormat extends Activity {

    private static final int KEYGUARD_REQUEST = 55;
    private static final int MENU_INITIATE_MEDIA_FORMAT = 0;
    private static final int MENU_EXECUTE_MEDIA_FORMAT = 1;

    private LayoutInflater mInflater;

    private View mInitialView;
    // SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917
    private TextView mInitiateText;
    private Button mInitiateButton;

    private View mFinalView;
    // SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917
    private TextView mFinalText;
    private Button mFinalButton;

    // SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917
    private boolean mIsInternal = false;
    private boolean mIsFinalView = false;

    /**
     * The user has gone through the multiple confirmation, so now we go ahead
     * and invoke the Mount Service to format the SD card.
     */
    private Button.OnClickListener mFinalClickListener = new Button.OnClickListener() {
            public void onClick(View v) {

                if (Utils.isMonkeyRunning()) {
                    return;
                }
                Intent intent = new Intent(ExternalStorageFormatter.FORMAT_ONLY);
                intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
                // Transfer the storage volume to the new intent
                final StorageVolume storageVolume = getIntent().getParcelableExtra(
                        StorageVolume.EXTRA_STORAGE_VOLUME);
                intent.putExtra(StorageVolume.EXTRA_STORAGE_VOLUME, storageVolume);
                startService(intent);
                finish();
            }
        };

    /**
     *  Keyguard validation is run using the standard {@link ConfirmLockPattern}
     * component as a subactivity
     */
    private boolean runKeyguardConfirmation(int request) {
        return new ChooseLockSettingsHelper(this)
                .launchConfirmationActivity(request,
                        getText(R.string.media_format_gesture_prompt),
                        getText(R.string.media_format_gesture_explanation));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != KEYGUARD_REQUEST) {
            return;
        }

        // If the user entered a valid keyguard trace, present the final
        // confirmation prompt; otherwise, go back to the initial state.
        if (resultCode == Activity.RESULT_OK) {
            establishFinalConfirmationState();
        } else if (resultCode == Activity.RESULT_CANCELED) {
            finish();
        } else {
            establishInitialState();
        }
    }

    /**
     * If the user clicks to begin the reset sequence, we next require a
     * keyguard confirmation if the user has currently enabled one.  If there
     * is no keyguard available, we simply go to the final confirmation prompt.
     */
    private Button.OnClickListener mInitiateListener = new Button.OnClickListener() {
            public void onClick(View v) {
                if (!runKeyguardConfirmation(KEYGUARD_REQUEST)) {
                    establishFinalConfirmationState();
                }
            }
        };

    /**
     * Configure the UI for the final confirmation interaction
     */
    private void establishFinalConfirmationState() {
        if (mFinalView == null) {
            mFinalView = mInflater.inflate(R.layout.media_format_final, null);
            mFinalButton =
                    (Button) mFinalView.findViewById(R.id.execute_media_format);
            // SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917
            mFinalText = (TextView) mFinalView.findViewById(R.id.execute_format_text);
            // mFinalButton.setOnClickListener(mFinalClickListener);
            mFinalButton.setVisibility(View.GONE);
        }

        /* SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917 @{ */
        if (mIsInternal) {
            mFinalText.setText(R.string.media_format_final_desc_internal);
            mFinalButton.setText(R.string.media_format_button_text_internal);
        }
        /* @} */

        setContentView(mFinalView);
        setSoftKey();
        mIsFinalView = true;
    }

    /**
     * In its initial state, the activity presents a button for the user to
     * click in order to initiate a confirmation sequence.  This method is
     * called from various other points in the code to reset the activity to
     * this base state.
     *
     * <p>Reinflating views from resources is expensive and prevents us from
     * caching widget pointers, so we use a single-inflate pattern:  we lazy-
     * inflate each view, caching all of the widget pointers we'll need at the
     * time, then simply reuse the inflated views directly whenever we need
     * to change contents.
     */
    private void establishInitialState() {
        if (mInitialView == null) {
            mInitialView = mInflater.inflate(R.layout.media_format_primary, null);
            mInitiateButton =
                    (Button) mInitialView.findViewById(R.id.initiate_media_format);
            // SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917
            mInitiateText = (TextView) mInitialView.findViewById(R.id.initiate_format_text);
            // mInitiateButton.setOnClickListener(mInitiateListener);
            mInitiateButton.setVisibility(View.GONE);
        }

        /* SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917 @{ */
        if (mIsInternal) {
            mInitiateText.setText(R.string.media_format_desc_internal);
            mInitiateButton.setText(R.string.media_format_button_text_internal);
        }
        /* @} */

        setContentView(mInitialView);
        setSoftKey();
        mIsFinalView = false;
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        mInitialView = null;
        mFinalView = null;
        mInflater = LayoutInflater.from(this);

        /* SPRD：ADD for Settings porting from 4.1 to 4.3 on 20130917 @{ */
        mIsInternal = getIntent().getBooleanExtra("storage_type", false);
        if (mIsInternal) {
            setTitle(R.string.media_format_title_internal);
        }
        /* @} */

        establishInitialState();
    }

    /** Abandon all progress through the confirmation sequence by returning
     * to the initial view any time the activity is interrupted (e.g. by
     * idle timeout).
     */
    @Override
    public void onPause() {
        super.onPause();

        /* SPRD: Remove for bug825206. @{
        if (!isFinishing()) {
            establishInitialState();
        }
        @} */
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_INITIATE_MEDIA_FORMAT, 0, R.string.media_format_button_text)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_EXECUTE_MEDIA_FORMAT, 0, R.string.media_format_final_button_text)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.getItem(MENU_INITIATE_MEDIA_FORMAT).setVisible(!mIsFinalView);
        menu.getItem(MENU_EXECUTE_MEDIA_FORMAT).setVisible(mIsFinalView);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
        case MENU_INITIATE_MEDIA_FORMAT:
            if (!runKeyguardConfirmation(KEYGUARD_REQUEST)) {
                establishFinalConfirmationState();
            }
            return true;
        case MENU_EXECUTE_MEDIA_FORMAT:
            if (Utils.isMonkeyRunning()) {
                return true;
            }
            Intent intent = new Intent(ExternalStorageFormatter.FORMAT_ONLY);
            intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
            final StorageVolume storageVolume = getIntent().getParcelableExtra(
                    StorageVolume.EXTRA_STORAGE_VOLUME);
            intent.putExtra(StorageVolume.EXTRA_STORAGE_VOLUME, storageVolume);
            startService(intent);
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void setSoftKey() {
        FeatureBarHelper featureBar = new FeatureBarHelper(this);
        ViewGroup vg = featureBar.getFeatureBar();
        if (vg != null) {
            TextView center = (TextView) featureBar.getCenterKeyView();
            vg.removeView(center);
        }
    }
}
