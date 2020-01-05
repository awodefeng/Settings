package com.android.settings.sim;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.sim.Sim;
import android.sim.SimManager;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.sprd.internal.telephony.TeleUtils;
import com.android.settings.R;


/**
 * Created  on 15-11-14.
 */
public class SimFragmentDialog extends DialogFragment {
    private Context mContext;
    private Sim mSubInfoRecord;
    private int mSlotId;
    private int[] mTintArr;
    private String[] mColorStrings;
    private int mTintSelectorPos;
    private SimManager mSimManager;
    AlertDialog.Builder mBuilder;
    View mDialogLayout;
    private final String SIM_NAME = "sim_name";
    private final String TINT_POS = "tint_pos";
    private final String DISPLAY_NUMBER = "display_number";
    // SPRD: add for log tag
    private final String TAG = "SimPreferenceDialog";

    private final String SLOT_ID  = "slot_id";

    /* SPRD: modify for bug496697 @{ */
    private LinearLayout mSimEditLayout;
    private static final String KEY_FOCUS_LOCATION = "focus_location";
    private static final String KEY_FOCUS_ID = "focus_id";
    /* }@ */

    // SPRD: modify for bug505956
    private Dialog mDialog;
    private TelephonyManager mTelephonyManager[];
    private static SimFragmentDialog mSimFragmentDialog;
    public static void show (SimSettings parent,int slotId){
        if(!parent.isAdded()){
            return;
        }
        if(mSimFragmentDialog != null && mSimFragmentDialog.isAdded()){
            return;
        }
        mSimFragmentDialog = new SimFragmentDialog();
        mSimFragmentDialog.mSlotId = slotId;
        mSimFragmentDialog.setTargetFragment(parent,0);
        mSimFragmentDialog.show(parent.getFragmentManager(),"SimFragmentDialog");
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mContext = getActivity();
        mSimManager = SimManager.get(getActivity());
        if (bundle != null) {
            mSlotId = bundle.getInt(SLOT_ID);
        }
        int mNumSlots = TelephonyManager.getPhoneCount();
        mTelephonyManager = new TelephonyManager[mNumSlots];
        for (int i = 0; i < mNumSlots; i++) {
            mTelephonyManager[i] = (TelephonyManager) mContext.getSystemService(TelephonyManager.getServiceName(
                    Context.TELEPHONY_SERVICE, i));
        }
        mSubInfoRecord = mSimManager.getSimById(mSlotId);
        /* SPRD: Add for SignalCluster colorful  @{ */
//        boolean isColorfulSignal = false;
//        CarrierConfigManager configManager = (CarrierConfigManager)mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
//        PersistableBundle persistableBundle = configManager.getConfigForPhoneId(mSlotId);
//        if (persistableBundle != null) {
//            isColorfulSignal = persistableBundle
//                    .getBoolean(CarrierConfigManager.KEY_SYSTEMUI_CARRIER_LABEL_WITH_SIMCOLOR_BOOL);
//        }
//        if (!isColorfulSignal) {
//            mTintArr = mContext.getResources().getIntArray(com.android.internal.R.array.sim_colors);
//            mColorStrings = mContext.getResources().getStringArray(R.array.color_picker);
//        } else {
//            mTintArr = mContext.getResources().getIntArray(com.android.internal.R.array.sim_colors_light);
//            mColorStrings = mContext.getResources().getStringArray(R.array.light_color_picker);
//        }
//        /* @} */
//        mTintSelectorPos = 0;

        mBuilder = new AlertDialog.Builder(mContext);
        LayoutInflater inflater = (LayoutInflater)mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mDialogLayout = inflater.inflate(R.layout.multi_sim_dialog,null);
        mSimEditLayout = (LinearLayout)mDialogLayout.findViewById(R.id.sim_edit_layout);
        mBuilder.setView(mDialogLayout);
    }

    /* SPRD: modify for bug505956 @{ */
    @Override
    public void onResume() {
        super.onResume();
        final IntentFilter intentFilter = new IntentFilter(
                TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(mReceiver, intentFilter);
        /* SPRD: modify for bug508651 @{ */
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Secure.RADIO_OPERATION), true,
                mRadioBusyObserver);
        /* @} */
    }
    /* }@ */

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        if (savedInstanceState != null) {
//            int pos = savedInstanceState.getInt(TINT_POS);
//            final Spinner tintSpinner = (Spinner) mDialogLayout.findViewById(R.id.spinner);
//            tintSpinner.setSelection(pos);
//            mTintSelectorPos = pos;

            EditText nameText = (EditText)mDialogLayout.findViewById(R.id.sim_name);
            nameText.setText(savedInstanceState.getString(SIM_NAME));

            /* SPRD: modify for bug493417 @{ */
            final EditText numberText = (EditText)mDialogLayout.findViewById(R.id.display_number);
            numberText.setText(savedInstanceState.getString(DISPLAY_NUMBER));
            /* @} */

            /* SPRD: modify for bug496697 @{ */
            findFocus(mSimEditLayout, savedInstanceState);
            /* @} */
        }
     /* SPRD: modify for bug505956 @{ */
        if (mSubInfoRecord != null) {
            mDialog = createEditDialog(savedInstanceState);
            return mDialog;
        }else {
            mBuilder = new AlertDialog.Builder(mContext);
            mDialog = mBuilder.create();
            return mDialog;
        }
    }

    /* SPRD: modify for bug508651 @{ */
    private ContentObserver mRadioBusyObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
                dismissDialog();
        }
    };

    private void dismissDialog(){
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(0, InputMethodManager.RESULT_HIDDEN);
        mDialog.dismiss();
    }
    /* @} */

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                if (stateExtra != null && IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                    // SPRD: modify for bug508651
                    dismissDialog();
                    mSimFragmentDialog = null;
                }
            }
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        mContext.unregisterReceiver(mReceiver);
        // SPRD: modify for bug508651
        mContext.getContentResolver().unregisterContentObserver(mRadioBusyObserver);
    }
    /* }@ */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {

        savedInstanceState.clear();

//        savedInstanceState.putInt(TINT_POS, mTintSelectorPos);
        savedInstanceState.putInt(SLOT_ID,mSlotId);

        final EditText nameText = (EditText)mDialogLayout.findViewById(R.id.sim_name);
        savedInstanceState.putString(SIM_NAME, nameText.getText().toString());
        /* SPRD: modify for bug493417 @{ */
        final EditText numberText = (EditText)mDialogLayout.findViewById(R.id.display_number);
        savedInstanceState.putString(DISPLAY_NUMBER, numberText.getText().toString());
        /* @} */

        /* SPRD: modify for bug496697 @{ */
        findFocus(mSimEditLayout, savedInstanceState);
        /* }@ */
        super.onSaveInstanceState(savedInstanceState);
    }

    /* SPRD: modify for bug496697 @{ */
    public int findFocus(ViewGroup child,Bundle bundle){
        int focusId = 0;
        int focuslocation = 0;
        for (int i = 0;i < child.getChildCount();i++ ){
            if (child.getChildAt(i) instanceof ViewGroup){
                focusId = findFocus((ViewGroup) child.getChildAt(i), bundle);
                if (focusId > 0) {
                    return focusId;
                }
            } else {
                for (int j = 0;j < child.getChildCount();j++) {
                    if (bundle.getInt(KEY_FOCUS_ID) == 0){
                        // SPRD: modify for bug557800
                        if (child.getChildAt(j).hasFocus() && child.getChildAt(j) instanceof EditText) {
                            focusId = child.getChildAt(j).getId();
                            focuslocation = ((EditText)child.getChildAt(j)).getSelectionStart();
                            bundle.putInt(KEY_FOCUS_ID,focusId);
                            bundle.putInt(KEY_FOCUS_LOCATION,focuslocation);
                            return focusId;
                        }
                    } else {
                        if (bundle.getInt(KEY_FOCUS_ID) == child.getChildAt(j).getId()){
                            focuslocation = bundle.getInt(KEY_FOCUS_LOCATION);
                            if (focuslocation >= 0 && focuslocation <= ((EditText)child.getChildAt(j)).length()){
                                child.getChildAt(j).requestFocus();
                                ((EditText) child.getChildAt(j)).setSelection(focuslocation);
                                return 0;
                            }
                        }
                    }
                }
            }
        }
        return focusId;
    }
    /* }@ */

    private Dialog createEditDialog(Bundle bundle) {
        final Resources res = mContext.getResources();
        final EditText nameText = (EditText)mDialogLayout.findViewById(R.id.sim_name);
        nameText.setText(mSubInfoRecord.getName());
        nameText.dispatchKeyEvent(new KeyEvent(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DEL));
        nameText.requestFocus();

//        final Spinner tintSpinner = (Spinner) mDialogLayout.findViewById(R.id.spinner);
//        SelectColorAdapter adapter = new SelectColorAdapter(mContext,
//                R.layout.settings_color_picker_item, mColorStrings);
//        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//        tintSpinner.setAdapter(adapter);

//        for (int i = 0; i < mTintArr.length; i++) {
//            if (mTintArr[i] == mSubInfoRecord.getIconTint()) {
//                tintSpinner.setSelection(i);
//                mTintSelectorPos = i;
//                break;
//            }
//        }

        final InputMethodManager inputMethodManager = (InputMethodManager) mContext.getSystemService(
                Context.INPUT_METHOD_SERVICE);
//        tintSpinner.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View view, MotionEvent motionEvent) {
//                if (inputMethodManager.isActive() && motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
//                    inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(),
//                            InputMethodManager.HIDE_NOT_ALWAYS);
//                }
//                return false;
//            }
//        });
//
//        tintSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view,
//                                       int pos, long id){
//                tintSpinner.setSelection(pos);
//                mTintSelectorPos = pos;
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {
//            }
//        });

        TextView numberView = (TextView)mDialogLayout.findViewById(R.id.number);
        /* SPRD: add option edit sim card's number @{ */
        numberView.setVisibility(View.GONE);
        EditText displayNumberView = (EditText)mDialogLayout.findViewById(R.id.display_number);
        displayNumberView.setInputType(InputType.TYPE_CLASS_PHONE);
        final String rawNumber =  mTelephonyManager[mSubInfoRecord.getPhoneId()].getLine1Number();
        if (!TextUtils.isEmpty(rawNumber)) {
            displayNumberView.setText(PhoneNumberUtils.formatNumber(rawNumber));
        }
        /* @} */

        String simCarrierName = mTelephonyManager[mSubInfoRecord.getPhoneId()].getSimOperatorName();
        simCarrierName = TeleUtils.updateOperator(simCarrierName, "spn");
        TextView carrierView = (TextView)mDialogLayout.findViewById(R.id.carrier);
        carrierView.setText(!TextUtils.isEmpty(simCarrierName) ? simCarrierName :
                mContext.getString(com.android.internal.R.string.unknownName));

        mBuilder.setTitle(String.format(res.getString(R.string.sim_editor_title),
                (mSubInfoRecord.getPhoneId() + 1)));

        mBuilder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                final EditText nameText = (EditText)mDialogLayout.findViewById(R.id.sim_name);

                String displayName = nameText.getText().toString();
                /* SPRD: modify for bug492214 @{ */
                boolean isEmpty = displayName.isEmpty();
                if (isEmpty) {
                    Log.d(TAG, "user input displayname is null.");
                    displayName = "SIM" + (mSubInfoRecord.getPhoneId() + 1);
                }
                /* @} */
                int phoneId = mSubInfoRecord.getPhoneId();
                mSubInfoRecord.setName(displayName);
                mSimManager.setName(phoneId,displayName,
                        isEmpty?SimManager.NAME_SOURCE_USER_INPUT_NULL : SimManager.NAME_SOURCE_USER_INPUT);

//                final int tintSelected = tintSpinner.getSelectedItemPosition();
//                int tint = mTintArr[tintSelected];
//                mSubInfoRecord.setIconTint(tint);
//                mSimManager.setIconTint(tint, subId);

                /* SPRD: add option edit sim card's number @{ */
                final EditText numberText = (EditText)mDialogLayout.findViewById(R.id.display_number);
                String displayNumber = numberText.getText().toString();
                Log.d(TAG, "displayNumber = " + displayNumber + " phoneId = " + phoneId);
                if (displayNumber!= null) {
                    mTelephonyManager[phoneId].setLine1Number("phoneNumber", displayNumber);
                    mSubInfoRecord.setNumber(displayNumber);
                    mSimManager.setNumber(phoneId,displayNumber);
                    Sim subInfo = mSimManager.getSimById(phoneId);
                    if (subInfo != null ) {
                        subInfo.setNumber(displayNumber);
                    }
                }
                /* @} */

                dialog.dismiss();
                mSimFragmentDialog = null;
            }
        });

        mBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
                mSimFragmentDialog = null;
            }
        });
        /* SPRD: modify for AOB bug496719 @{ */
        AlertDialog dialog = mBuilder.create();

        return  dialog;
        /* @} */
    }

//    private class SelectColorAdapter extends ArrayAdapter<CharSequence> {
//        private Context mContext;
//        private int mResId;
//
//        public SelectColorAdapter(
//                Context context, int resource, String[] arr) {
//            super(context, resource, arr);
//            mContext = context;
//            mResId = resource;
//        }
//
//        @Override
//        public View getView(int position, View convertView, ViewGroup parent) {
//            LayoutInflater inflater = (LayoutInflater)
//                    mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//
//            View rowView;
//            final ViewHolder holder;
//            Resources res = mContext.getResources();
//            int iconSize = res.getDimensionPixelSize(R.dimen.color_swatch_size);
//            int strokeWidth = res.getDimensionPixelSize(R.dimen.color_swatch_stroke_width);
//
//            if (convertView == null) {
//                // Cache views for faster scrolling
//                rowView = inflater.inflate(mResId, null);
//                holder = new ViewHolder();
//                ShapeDrawable drawable = new ShapeDrawable(new OvalShape());
//                drawable.setIntrinsicHeight(iconSize);
//                drawable.setIntrinsicWidth(iconSize);
//                drawable.getPaint().setStrokeWidth(strokeWidth);
//                holder.label = (TextView) rowView.findViewById(R.id.color_text);
//                holder.icon = (ImageView) rowView.findViewById(R.id.color_icon);
//                holder.swatch = drawable;
//                rowView.setTag(holder);
//            } else {
//                rowView = convertView;
//                holder = (ViewHolder) rowView.getTag();
//            }
//
//            holder.label.setText(getItem(position));
//            holder.swatch.getPaint().setColor(mTintArr[position]);
//            holder.swatch.getPaint().setStyle(Paint.Style.FILL_AND_STROKE);
//            holder.icon.setVisibility(View.VISIBLE);
//            holder.icon.setImageDrawable(holder.swatch);
//            return rowView;
//        }
//
//        @Override
//        public View getDropDownView(int position, View convertView, ViewGroup parent) {
//            View rowView = getView(position, convertView, parent);
//            final ViewHolder holder = (ViewHolder) rowView.getTag();
//
//            if (mTintSelectorPos == position) {
//                holder.swatch.getPaint().setStyle(Paint.Style.FILL_AND_STROKE);
//            } else {
//                holder.swatch.getPaint().setStyle(Paint.Style.STROKE);
//            }
//            holder.icon.setVisibility(View.VISIBLE);
//            return rowView;
//        }
//
//        private class ViewHolder {
//            TextView label;
//            ImageView icon;
//            ShapeDrawable swatch;
//        }
//    }

}

