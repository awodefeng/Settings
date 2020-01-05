/** Created by Spreadst */
package com.android.settings;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class ApnTypeSettingActivity extends Activity {

    private ApnEditorAdapter mAdapter;
    private EditText mEditText;
    private AlertDialog confirmDialog = null;
    // SPRD: bug 854523
    private static final String TAG = "ApnTypeSettingActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initApnlist();
        ActionBar actionBar = getActionBar();
        actionBar.setTitle(R.string.apn_type_setting);
        initEdit();
    }

    public void initApnlist() {
        setContentView(R.layout.apn_type_edit);
        ListView apnListView = (ListView)findViewById(R.id.apn_type_list);
        mAdapter = new ApnEditorAdapter(ApnTypeSettingActivity.this);
        mEditText = (EditText)findViewById(R.id.user_edit_apn);
        Button okButton = (Button)findViewById(R.id.apn_ok);
        Button cancelButton = (Button)findViewById(R.id.apn_cancel);
        okButton.setOnClickListener(mButtonClickListener);
        cancelButton.setOnClickListener(mButtonClickListener);
        apnListView.setAdapter(mAdapter);
        apnListView.setItemsCanFocus(false);
        apnListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        setListViewHeightBasedOnChildren(apnListView);

        apnListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                mAdapter.setApnChecked(view, position);
            }
        });
    }

    private void initEdit() {
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        String apnType = bundle.getString("apn_type");
        String[] apnArray = apnType.split(",");
        ArrayList<String> judgeList = new ArrayList<String>();
        StringBuilder editBuilder = new StringBuilder("");
        for (String apnString : apnArray) {
            int position = mAdapter.getPositionByText(apnString);
            if (position != -1) {
                if (judgeList != null && judgeList.contains(apnString)) {
                    editBuilder.append(apnString + ",");
                }
                mAdapter.isSelected.put(position, true);
                judgeList.add(apnString);
            } else if (position == -1
                    && !apnString.equals(getResources().getString(
                            R.string.apn_not_set))) {
                editBuilder.append(apnString + ",");
            }
        }
        if (editBuilder.length() > 0) {
            String comma = editBuilder.charAt(editBuilder.length() - 1) + "";
            if (comma.equals(",")) {
                editBuilder.deleteCharAt(editBuilder.length() - 1);
            }
            mEditText.setText(editBuilder);
            // SPRD: bug 854523
            mEditText.setSelection(editBuilder.length());
        }
        mEditText.setHint(R.string.user_could_input_apn_type);
    }

    OnClickListener mButtonClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
            case R.id.apn_ok:
                String apnType = getApnString();
                if(!checkEdit(apnType)) {
                    return;
                }
                boolean defaultapnType = ((apnType.indexOf("default") != -1) || (apnType.indexOf("*") != -1));
                if (!defaultapnType) {
                    showDialog(apnType);
                } else {
                    createNewApnType(apnType);
                }
                break;
            case R.id.apn_cancel:
                setResult(RESULT_CANCELED);
                finish();
                break;
            }
        }
    };

    /* SPRD: bug 854523 @{ */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onKeyDown() keycode = " + keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                String apnType = getApnString();
                if(!checkEdit(apnType)) {
                    return true;
                }
                boolean defaultapnType = ((apnType.indexOf("default") != -1)
                        || (apnType.indexOf("*") != -1));
                if (!defaultapnType) {
                    showDialog(apnType);
                    return true;
                } else {
                    createNewApnType(apnType);
                    return true;
                }
            case KeyEvent.KEYCODE_BACK:
                setResult(RESULT_CANCELED);
                finish();
                return true;

        }
        return super.onKeyDown(keyCode, event);
    }
    /* @} */

    private void createNewApnType(String apnType) {
        Intent bindIntent = new Intent(ApnTypeSettingActivity.this, ApnEditor.class);
        Bundle bundle = new Bundle();
        bundle.putString("apn_type", apnType);
        bindIntent.putExtras(bundle);
        setResult(RESULT_OK, bindIntent);
        finish();
    }

    private void showDialog(final String apnType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.error_title);
        builder.setMessage(R.string.apn_type_message);
        builder.setPositiveButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                createNewApnType(apnType);
            }
        });
        builder.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });

        if(confirmDialog != null)
            confirmDialog.dismiss();

        confirmDialog = builder.create();
        confirmDialog.show();
    }

    private String getApnString() {
        StringBuilder builder = new StringBuilder("");
        String[] apnArray = getResources().getStringArray(
                R.array.apn_type_array);
        HashMap<String, Boolean> apnMap = mAdapter.getEntries();
        for (int i = 0; i < apnArray.length; i++) {
            if (apnMap.get(apnArray[i])) {
                builder.append(apnArray[i] + ",");
            }
        }
        if (mEditText.getText()!= null && mEditText.getText().length() > 0) {
            String editInputString = mEditText.getText().toString();
            builder.append(editInputString);
        }
        if (builder.length() > 0) {
            String builderLast = builder.charAt(builder.length() - 1) + "";
            if (builderLast.equals(",")) {
                builder.deleteCharAt(builder.length() - 1);
            }
        }
        return builder.toString();
    }

    private void showToast(int msg){
        Toast toast = Toast.makeText(ApnTypeSettingActivity.this,
                msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    private boolean checkEdit(String apnType) {
        ArrayList<String> apnOrigList = mAdapter.getOrigArray();
        ArrayList<String> apnFullList = mAdapter.getFullArray();
        String editString = mEditText.getText().toString();

        String[] editArray;

        if (editString.length() > 0) {
            editArray = editString.split(",");
            if (editArray.length == 0) {
                showToast(R.string.error_apn_type);
                return false;
            }
            for (String edit : editArray) {
                if (!apnFullList.contains(edit)) {
                    showToast(R.string.error_apn_type);
                    return false;
                } else if (apnOrigList.contains(edit)) {
                    showToast(R.string.could_not_input_the_same_apn_type);
                    return false;
                }
            }
            for (int i = 0; i < editArray.length; i++) {
                for (int j = i + 1; j < editArray.length; j++) {
                    if (editArray[i].equals(editArray[j])) {
                        showToast(R.string.same_apn_type);
                        return false;
                    }
                }
            }
        }
        if (apnType.equals("")) {
            showToast(R.string.empty_apn_type);
            return false;
        }
        return true;
    }

    /*
     * measure the height of ListView and set the value on LayoutParams
     * @param listView the ListView which should be reset the LayoutParams
     */
    private void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null) {
            return;
        }
        int totalHeight = 0;
        // get the items of the ListView
        for (int i = 0; i < listAdapter.getCount(); i++) {
            View listItem = listAdapter.getView(i, null, listView);
            // measure the item's layout
            listItem.measure(0, 0);
            totalHeight += listItem.getMeasuredHeight();
        }
        // get LayoutParams of the ListView
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        // set the measured height on ListView's params
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
        listView.setLayoutParams(params);
    }

}
