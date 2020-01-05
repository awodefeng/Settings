/** Create by Spreadst */
package com.sprd.settings.timerpower;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import com.android.settings.R;
import com.android.settings.Settings;

import com.sprd.android.support.featurebar.FeatureBarHelper;
import android.widget.AdapterView.OnItemLongClickListener;
/**
 * Power ON/OFF application.
 */
public class AlarmClock extends Activity implements OnItemClickListener,OnItemLongClickListener {

    static final String PREFERENCES = "AlarmClock";

    /** This must be false for production.  If true, turns on logging,
        test code, etc. */
    static final boolean DEBUG = true;

    private LayoutInflater mFactory;
    private ListView mAlarmsList;
    private Cursor mCursor;

    private void updateIndicatorAndAlarm(boolean enabled, ImageView bar,
            Alarm alarm) {
        Log.v("timerpower AlarmClock ========== >>>>> updateIndicatorAndAlarm "+enabled);
        Alarms.enableAlarm(this, alarm.id, enabled);
        if (enabled) {
            SetAlarm.popAlarmSetToast(this, alarm.hour, alarm.minutes,
                    alarm.daysOfWeek);
        }
    }

    private class AlarmTimeAdapter extends CursorAdapter {
        public AlarmTimeAdapter(Context context, Cursor cursor) {
            super(context, cursor);
        }

        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View ret = mFactory.inflate(R.layout.alarm_time, parent, false);
            return ret;
        }

        public void bindView(View view, Context context, Cursor cursor) {
            final Alarm alarm = new Alarm(AlarmClock.this,cursor);

            View indicator = view.findViewById(R.id.indicator);

            // Set the initial state of the clock "checkbox"
            final CheckBox clockOnOff =
                    (CheckBox) indicator.findViewById(R.id.clock_onoff);
            clockOnOff.setChecked(alarm.enabled);

            // Clicking outside the "checkbox" should also change the state.
            indicator.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        clockOnOff.toggle();
                        updateIndicatorAndAlarm(clockOnOff.isChecked(),
                                null, alarm);
                    }
            });
            Log.v("timerpower AlarmClock -------------------- >>>>>>>>>>>>>>> "+alarm.label);
            final TextView powerOnOff = (TextView)view.findViewById(R.id.poweronoff);
            if(!alarm.label.equals("") && alarm.label.equals("on"))
            {
                powerOnOff.setText(R.string.power_on);
            }else
            {
                powerOnOff.setText(R.string.power_off);
            }

        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
/*Modify for fix bug 189270 start*/
        ActionBar actionBar =  getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setHomeButtonEnabled(false);//SPRD:add for bug646928
        }
/*Modify for fix bug 189270 end*/
        mFactory = LayoutInflater.from(this);
        mCursor = Alarms.getAlarmsCursor(getContentResolver());
        Log.v("timerpower AlarmClock ============= mCursor");

        updateLayout();
    }

/*Modify for fix bug 189270 start*/
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return super.onOptionsItemSelected(item);
        }
        return super.onOptionsItemSelected(item);
    }
/*Modify for fix bug 189270 end*/

    private void updateLayout() {
        setContentView(R.layout.alarm_clock);
        //SPRD:616934 Add softKey for settings
        setSoftKey();
        mAlarmsList = (ListView) findViewById(R.id.alarms_list);
        AlarmTimeAdapter adapter = new AlarmTimeAdapter(this, mCursor);
        mAlarmsList.setAdapter(adapter);
        mAlarmsList.setOnItemClickListener(this);
        mAlarmsList.setOnItemLongClickListener(this);// SPRD add for bug622196
    }

    /* SPRD:616934 Add softKey fotr settings @{ */
    private void setSoftKey() {
        FeatureBarHelper helperBar = new FeatureBarHelper(this);
        ViewGroup vg = helperBar.getFeatureBar();
        if (vg != null) {
            View option = helperBar.getOptionsKeyView();
            vg.removeView(option);
        }
    }
    /* @} */

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ToastMaster.cancelToast();
        mCursor.close();
    }

    @Override
    public void onItemClick(AdapterView parent, View v, int pos, long id) {
        android.util.Log.d("timerpower AlarmClock", "id = " + id);
        Intent intent = new Intent(this, SetAlarm.class);
        intent.putExtra(Alarms.ALARM_ID, (int) id);
        startActivity(intent);
    }

    /* SPRD add for bug622196 start*/
    @Override
    public boolean onItemLongClick(AdapterView parent, View v, int pos, long id) {
        Log.i("onItemLongClick");

        final Alarm alarm = new Alarm(AlarmClock.this, ((CursorAdapter)parent.getAdapter()).getCursor());

        View indicator = v.findViewById(R.id.indicator);

        // Set the initial state of the clock "checkbox"
        final CheckBox clockOnOff =
                (CheckBox) indicator.findViewById(R.id.clock_onoff);
        clockOnOff.setChecked(alarm.enabled);
        clockOnOff.toggle();
        updateIndicatorAndAlarm(clockOnOff.isChecked(),
                null, alarm);
        return true;
    }
    /* SPRD add for bug622196 end*/
}
