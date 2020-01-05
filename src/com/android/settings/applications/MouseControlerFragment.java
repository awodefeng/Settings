/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.settings.applications;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.TextPaint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.applications.ApplicationsState;
import com.android.settings.applications.ApplicationsState.AppEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class MouseControlerFragment extends SettingsPreferenceFragment{

    static final String TAG = "MouseControlerFragment";
    static final boolean DEBUG = true;

    public static LayoutInflater sInflater;
    public ApplicationsAdapter mApplications;

    private ApplicationsState mApplicationsState;
    private ViewGroup mRelative;
    private CheckBox mSelectAllBox;
    private TextView mSelectText;
    private View mRootView;
    private View mListContainer;
    private ListView mListView;
    private Context mContext;
    private AppOpsManager mAppOps;
    private HashMap<String, Boolean> mMap;
    private ContentResolver mResolver;
    private TextPaint mTextPaint;
    private Toast mToast;

    class ApplicationsAdapter extends BaseAdapter implements
            ApplicationsState.Callbacks, AbsListView.RecyclerListener {
        private final ApplicationsState mState;
        private final ApplicationsState.Session mSession;
        private final ArrayList<View> mActive = new ArrayList<View>();
        private ArrayList<AppEntry> mEntries;
        private boolean mResumed;
        private LayoutInflater inflater = sInflater;
        MouseControlViewHolder mHolder;

        public ApplicationsAdapter(ApplicationsState state) {
            mState = state;
            mSession = state.newSession(this);
        }

        public void setChecked(String pkgnmae, boolean checked) {
            if (checked && !mMap.containsKey(pkgnmae)) {
                mMap.put(pkgnmae, checked);
            } else if (!checked && mMap.containsKey(pkgnmae)){
                mMap.remove(pkgnmae);
            } else {
            }
        }

        public boolean isChecked(int position) {
            String pkg = getAppEntry(position).info.packageName;
            return mMap.containsKey(pkg);
        }

        public int getCheckedCount() {
            return mMap.size();//need change
        }

        public void resume() {
            if (DEBUG)
                Log.i(TAG, "Resume!  mResumed=" + mResumed);
            if (!mResumed) {
                mResumed = true;
                mSession.resume();
                rebuild(true);
            }
        }

        public void pause() {
            if (mResumed) {
                mResumed = false;
                mSession.pause();
            }
        }

        public void release() {
            mSession.release();
        }

        private final String[] mOtherSystemInMouse = new String[] {
            "com.facebook.lite.stub",
            "com.facebook.lite",
            "com.facebook.katana",
            "com.whatsapp",
            "com.android.browser",
            "com.android.email"
        };

        public void rebuild(boolean eraseold) {
            if (DEBUG)
                Log.i(TAG, "Rebuilding app list...");
            ApplicationsState.AppFilter filterObj = ApplicationsState.THIRD_PARTY_FILTER;
            Comparator<AppEntry> comparatorObj = ApplicationsState.ALPHA_COMPARATOR;
            ArrayList<AppEntry> entries = mSession.rebuild(
                    filterObj, comparatorObj);
            /* SPRD:add other system app to list @{ */
            ArrayList<AppEntry> entriesSystem = getOtherSystemEntry();
            if (entriesSystem != null) {
                if (entries == null) {
                    entries = new ArrayList<AppEntry>();
                }
                Iterator<AppEntry> it = entriesSystem.iterator();
                while (it.hasNext()) {
                    AppEntry entrysys = it.next();
                    if (!entries.contains(entrysys)) {
                        entries.add(entrysys);
                    }
                }
             }
            /* @}*/
            if (entries != null) {
                mEntries = entries;
            } else {
                mEntries = null;
            }
            Log.i(TAG, "Rebuilding app list...mEntries==" + mEntries);
            /* SPRD: bug fix 565036 the startup manager shows wrong information @{ */
            if (mEntries != null) {
                Iterator<AppEntry> it = mEntries.iterator();
                while (it.hasNext()) {
                    AppEntry entry = it.next();
                    if ((entry.info.flags&ApplicationInfo.FLAG_INSTALLED) == 0) {
                        it.remove();
                    }
                }
            }
            /* @}*/

            /*for (int i = 0; i < getCount(); i++) {
                boolean enabled = mMap.containsKey(mEntries.get(i).info.packageName);
                setChecked(mEntries.get(i).info.packageName, enabled);
            }
            updateViews();*/
            notifyDataSetChanged();

            if (entries == null) {
                mListContainer.setVisibility(View.INVISIBLE);
            } else {
                mListContainer.setVisibility(View.VISIBLE);
            }
        }

        //SPRD:add for bug 628486 get browser entry
        private ArrayList<AppEntry> getOtherSystemEntry() {
            ArrayList<AppEntry> systemApps = new ArrayList<AppEntry>();
            if (mState != null) {
		for(int i=0;i<mOtherSystemInMouse.length;i++){
                    AppEntry entry = mState.getEntry(mOtherSystemInMouse[i]);
                    if(entry != null){
                        systemApps.add(entry);
                    }
                }
                return systemApps;
            } else {
                return null;
            }
        }

        @Override
        public void onRunningStateChanged(boolean running) {
        }

        @Override
        public void onRebuildComplete(ArrayList<AppEntry> apps) {
            if (DEBUG)
                Log.i(TAG, "onRebuildComplete...mEntries==" + apps);
            mListContainer.setVisibility(View.VISIBLE);
            Log.i(TAG, "onRebuildComplete...mEntries==" + apps);

            /* SPRD: Add for bug708013. @{ */
            ArrayList<AppEntry> tempEntries = apps;
            ArrayList<AppEntry> systemEntries = getOtherSystemEntry();
            if (systemEntries != null) {
                if (tempEntries == null) {
                    tempEntries = new ArrayList<AppEntry>();
                }
                Iterator<AppEntry> it = systemEntries.iterator();
                while (it.hasNext()) {
                    AppEntry entrysys = it.next();
                    tempEntries.add(entrysys);
                }
            }

            if (tempEntries != null) {
                mEntries = tempEntries;
            } else {
                mEntries = null;
            }
            // mEntries = apps;
            /* @} */

            for (int i = 0; i < getCount(); i++) {
                boolean enabled = mMap.containsKey(mEntries.get(i).info.packageName);
                setChecked(mEntries.get(i).info.packageName, enabled);
            }
            //updateViews();
            notifyDataSetChanged();
        }

        @Override
        public void onPackageListChanged() {
            if (DEBUG)
                Log.i(TAG, "onPackageListChanged...");
            rebuild(false);
        }

        @Override
        public void onPackageIconChanged() {

        }

        @Override
        public void onPackageSizeChanged(String packageName) {
        }

        @Override
        public void onAllSizesComputed() {

        }

        public int getCount() {
            return mEntries != null ? mEntries.size() : 0;
        }

        public Object getItem(int position) {
            return mEntries.get(position);
        }

        public AppEntry getAppEntry(int position) {
            return mEntries.get(position);
        }

        public long getItemId(int position) {
            return mEntries.get(position).id;
        }

        public View getView(final int position, View convertView,
                ViewGroup parent) {
            mHolder = MouseControlViewHolder.createOrRecycle(sInflater, convertView);
            convertView = mHolder.mRootView;
            AppEntry entry = mEntries.get(position);
            synchronized (entry) {
                mHolder.mEntry = entry;
                if (entry.label != null) {
                    mHolder.mAppName.setText(entry.label);
                }
                /* mState.ensureIcon(entry);
                if (entry.icon != null) {
                    mHolder.mAppIcon.setImageDrawable(entry.icon);
                } */
                mHolder.mCheckBox.setChecked(isChecked(position));
            }
            mActive.remove(convertView);
            mActive.add(convertView);
            return convertView;
        }

        @Override
        public void onMovedToScrapHeap(View view) {
            mActive.remove(view);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();
        ((Activity) mContext).getActionBar().setDisplayHomeAsUpEnabled(false);
        ((Activity) mContext).getActionBar().setHomeButtonEnabled(false);
        setHasOptionsMenu(true);
        mResolver = mContext.getContentResolver();
        mApplicationsState = ApplicationsState.getInstance(getActivity()
                .getApplication());
        mApplications = new ApplicationsAdapter(mApplicationsState);
        mAppOps = (AppOpsManager) getActivity().getSystemService(
                Context.APP_OPS_SERVICE);
        initData();
    }

    private void initData() {
        String str = Settings.Global.getString(mResolver, Settings.Global.MOUSE_SUPPORT_LIST);
        Log.i(TAG, "init str=" + str);
        if (str != null) {
            mMap = splitStringToMap(str);
        } else {
            mMap = new HashMap<String, Boolean>();
        }
    }
    private void saveData() {
        StringBuffer sb = new StringBuffer();;
        for(HashMap.Entry<String, Boolean> entry:mMap.entrySet()){
            sb.append(entry.getKey().toString() + ",");
        }
        Log.i(TAG, "packs=" + sb.toString());
        Settings.Global.putString(mResolver, Settings.Global.MOUSE_SUPPORT_LIST, sb.toString());
    }

    public static HashMap<String,Boolean> splitStringToMap(String str) {
        HashMap<String, Boolean> map =  new HashMap<String, Boolean>();
        String[] temp = str.split(",");
        for (int i = 0; i < temp.length; i++) {
            map.put(temp[i], true);
        }
        return map;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        sInflater = inflater;
        mRootView = sInflater.inflate(R.layout.autorun_multi_select, container,
                false);
        initialViews(container);
        return mRootView;
    }

    @Override
    public void onDetach() {
        saveData();
        super.onDetach();
    }
    @Override
    public void onConfigurationChanged(
            Configuration newConfig) {
        ActionBar actionBar = getActivity().getActionBar();
        if (actionBar != null && getResources() != null) {
            actionBar.setTitle(setActionText(getResources(), actionBar, newConfig));
        }
        super.onConfigurationChanged(newConfig);
    }

    public static SpannableString setActionText(Resources res, ActionBar actionBar,
            Configuration newConfig) {
        final SpannableString text = new SpannableString(actionBar.getTitle());
        if (newConfig.toString().contains("land")) {
            text.setSpan(new AbsoluteSizeSpan(res.getDimensionPixelSize(
                    R.dimen.startup_land_actionbar_title_size)), 0,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            text.setSpan(new AbsoluteSizeSpan(res.getDimensionPixelSize(
                    R.dimen.startup_port_actionbar_title_size)), 0,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    public void onBackPressed() {
        getActivity().finish();
    }

    private void showAlertDialog() {
        AlertDialogFragment alertdialogFragment = new AlertDialogFragment();
        alertdialogFragment.show(getFragmentManager(), "first");
    }

    class AlertDialogFragment extends DialogFragment implements DialogInterface.OnClickListener {
        private String mPlaylistName;
        private int mPlaylistId;

        public AlertDialogFragment() {

        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext, AlertDialog.THEME_HOLO_LIGHT);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setTitle(android.R.string.dialog_alert_title);
            String message = getActivity().getString(R.string.mouse_control_exit,
                    mPlaylistName);
            builder.setMessage(message);
            builder.setNegativeButton(android.R.string.cancel, this);
            builder.setPositiveButton(android.R.string.ok, this);
            return builder.create();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    saveData();
                    getActivity().finish();
                    break;
                default:
                    getActivity().finish();
                    break;
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mApplications != null) {
            mApplications.resume();
        }
        //updateViews();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mApplications != null) {
            mApplications.pause();
        }
    }

    @Override
    public void onStop() {
        //SPRD bug fix 623539 can not save the selected data.
        Log.d(TAG, "---onStop---: do save data");
        saveData();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mApplications != null) {
            mApplications.release();
        }
        mRootView = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void initialViews(ViewGroup contentParent) {
        if (mRootView == null) {
            Log.d(TAG, "mRootView == null ");
            return;
        }
        mRelative = (ViewGroup) mRootView.findViewById(R.id.select);
        mSelectAllBox = (CheckBox) mRootView.findViewById(R.id.select_all);
        mSelectText = (TextView) mRootView.findViewById(R.id.select_text);
        mSelectText.setText(R.string.mouse_control_title);
        mTextPaint = mSelectText.getPaint();
        mTextPaint.setFakeBoldText(true);
        mTextPaint.setColor(R.color.black_custom);
        mListContainer = mRootView.findViewById(R.id.list_container);
        mSelectAllBox.setVisibility(View.GONE);

        if (mListContainer != null) {
            View emptyView = mListContainer
                    .findViewById(com.android.internal.R.id.empty);
            ListView lv = (ListView) mListContainer
                    .findViewById(android.R.id.list);
            if (emptyView != null) {
                lv.setEmptyView(emptyView);
            }
            lv.setSaveEnabled(true);
            lv.setItemsCanFocus(true);
            //SPRD bug 623486 this will enable input method,we should not call input method
//            lv.setTextFilterEnabled(true);
            mListView = lv;
            mListView.setAdapter(mApplications);
            mListView.setRecyclerListener(mApplications);
            mListView.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> arg0, View view,
                        int position, long arg3) {
                    MouseControlViewHolder holder = (MouseControlViewHolder) view.getTag();
                    holder.mCheckBox.setChecked(!holder.mCheckBox.isChecked());
                    mApplications.setChecked(mApplications.getAppEntry(position).info.packageName,
                            holder.mCheckBox.isChecked());
                    mApplications.notifyDataSetChanged();
                    //updateViews();
                }
            });
        }
    }

    /* SPRD: Add for bug718257. @{ */
    private void showToast(int resid) {
        if (mToast == null) {
            mToast = Toast.makeText(mContext, resid, Toast.LENGTH_SHORT);
        } else {
            mToast.setText(resid);
        }
        mToast.show();
    }
    /* @} */

    private void updateViews() {
        if (mApplications.mEntries != null
                && mApplications.mEntries.size() == 0) {
            mRelative.setVisibility(View.GONE);
        } else {
            mRelative.setVisibility(View.VISIBLE);
        }

        int selectNum = mApplications.getCheckedCount();
        int allNum = mApplications.getCount();
        if (selectNum == allNum && allNum != 0) {
            mSelectAllBox.setChecked(true);
            mSelectText.setText(R.string.cancle_select_all);
        } else {
            mSelectAllBox.setChecked(false);
            mSelectText.setText(R.string.select_all);
        }

        mSelectAllBox.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                int allNum = mApplications.getCount();
                for (int i = 0; i < allNum; i++) {
                    mApplications.setChecked(mApplications.getAppEntry(i).info.packageName, mSelectAllBox.isChecked());
                }
                if (mSelectAllBox.isChecked()) {
                    mSelectText.setText(R.string.cancle_select_all);
                } else {
                    mSelectText.setText(R.string.select_all);
                }
                mApplications.notifyDataSetChanged();
            }
        });
        mApplications.notifyDataSetChanged();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
