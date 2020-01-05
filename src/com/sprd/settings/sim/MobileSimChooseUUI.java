package com.sprd.settings.sim;

import android.app.ActionBar;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings;
import android.sim.Sim;
import android.sim.SimListAdapter;
import android.sim.SimManager;
import android.util.Log;
import android.view.View;
import android.telephony.TelephonyManager;
import android.view.MenuItem;
import android.widget.ListView;

import com.android.settings.R;

public class MobileSimChooseUUI extends ListActivity {

    public static boolean PIKEL_UI_SUPPORT = SystemProperties.getBoolean("pikel_ui_support",true);
    public static String PACKAGE_NAME = "package_name";
    public static String CLASS_NAME = "class_name";
    public static String CLASS_NAME_OTHER = "class_name_other";
    public static String ACTIVITY_TITLE = "activity_title";
    public static final String SUB_ID = "sub_id";
    private Sim[] mSims;

    protected void onCreate(Bundle savedInstanceState) {

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        actionBar.setDisplayHomeAsUpEnabled(true);
        /* SPRD: modify for bug273421 @{ */
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), true,
                mAirplaneModeObserver);
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.RADIO_OPERATION), true,
                mRadioBusyObserver);
        /* @} */
        super.onCreate(savedInstanceState);
    }

    protected void onResume() {
        super.onResume();
        SimManager sm = SimManager.get(this);
        Sim[] sims = sm.getActiveSims();
        Intent intent = getIntent();
        String title = intent.getStringExtra(ACTIVITY_TITLE);
        if (title != null) {
            setTitle(title);
        }
        if (intent.getStringExtra(MobileSimChooseUUI.CLASS_NAME_OTHER) != null && !PIKEL_UI_SUPPORT) {
            String other = this.getResources().getString(R.string.mobile_network_settings_other);
            Sim sim = new Sim(-1, "", other, -1,"",0);
            int len = sims.length + 1;
            mSims = new Sim[len];
            for (int i = 0; i < len - 1; i++) {
                mSims[i] = sims[i];
            }
            mSims[len - 1] = sim;
        } else {
            mSims = sims;
        }
        /* SPRD: modify for bug273421 @{ */
        boolean enabled = !isAirplaneModeOn() && !isRadioBusy();
        if (enabled) {
            getListView().setEnabled(true);
        } else {
            getListView().setEnabled(false);
        }

        SimListAdapter adapter = new SimListAdapter(this, mSims, null,
                com.android.internal.R.layout.select_sim, enabled);
        /* @} */
        setListAdapter(adapter);
    }

    /* SPRD: modify for bug273421 @{ */
    private ContentObserver mRadioBusyObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if(!isAirplaneModeOn() && !isRadioBusy()){
                ((SimListAdapter)mList.getAdapter()).notifyDataSetChanged(true);
                getListView().setEnabled(true);
            }
        }
    };

    private ContentObserver mAirplaneModeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if(isAirplaneModeOn()){
                ((SimListAdapter)mList.getAdapter()).notifyDataSetChanged(false);
                getListView().setEnabled(false);
            }
        }
    };

    private boolean isAirplaneModeOn() {
        return Settings.Global.getInt(this.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    public boolean isRadioBusy() {
        return Settings.Secure.getInt(this.getContentResolver(),
                Settings.Secure.RADIO_OPERATION, 0) == 1;
    }
    /* @} */

    public void onListItemClick(ListView l, View v, int position, long id) {

        int phoneId = mSims[position].getPhoneId();
        String pkg = getIntent().getStringExtra(PACKAGE_NAME);
        String cls = getIntent().getStringExtra(CLASS_NAME);
        String otherCls = getIntent().getStringExtra(CLASS_NAME_OTHER);
        pkg = "com.android.settings";
        cls = "com.android.settings.deviceinfo.StatusSim";

        if (null == pkg || cls == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName(pkg, cls));
        if (otherCls != null && (l.getCount() - 1) == position) {
            intent.setComponent(new ComponentName(pkg, otherCls));
        } else {
            intent.putExtra(SUB_ID, phoneId);
        }
        TelephonyManager tm = (TelephonyManager)this.getSystemService(TelephonyManager.getServiceName(
                Context.TELEPHONY_SERVICE, phoneId));
        /* SPRD: modify for bug295260 @{ */
        if (tm != null && tm.getSimState() == TelephonyManager.SIM_STATE_PUK_REQUIRED) {
            Intent intentPUK = new Intent(Intent.ACTION_PUK_UNLOCK_REQUESTED);
            intentPUK.putExtra("phone_id", phoneId);
            sendBroadcast(intentPUK);
            return;
        }
        /* @} */
        startActivity(intent);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        /* SPRD: modify for bug273421 @{ */
        getContentResolver().unregisterContentObserver(mAirplaneModeObserver);
        getContentResolver().unregisterContentObserver(mRadioBusyObserver);
        /* @} */
        super.onDestroy();
    }
}
