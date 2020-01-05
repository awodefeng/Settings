/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.settings.sim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SimSelectNotification extends BroadcastReceiver {
    private static final String TAG = "SimSelectNotification";
    // SPRD: add for selecting primary card after boot with sim card changed
    private static final String PRIMARY_CARD_SELECTION_DIALOG = "android.intent.action.SHOW_SELECT_PRIMARY_CARD_DIALOG";
    @Override
    public void onReceive(Context context, Intent intent) {
        /* SPRD: [Bug512963] Add for selecting primary card after boot with SIM card changed. @{ */
        if (PRIMARY_CARD_SELECTION_DIALOG.equals(intent.getAction())) {
            Log.d(TAG, "receive broadcast : SHOW_SELECT_PRIMARY_CARD_DIALOG");
            Intent targetIntent = new Intent(context, SimDialogActivity.class);
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            targetIntent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY, SimDialogActivity.PRIMARY_PICK);
            targetIntent.putExtra(SimDialogActivity.PRIMARYCARD_PICK_CANCELABLE, true);
            context.startActivity(targetIntent);
        }
        /* @} */
    }
}
