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

package com.android.settings.deviceinfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.storage.StorageVolume;
import android.text.format.Formatter;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.android.settings.R;
import com.android.settings.Settings;
import com.android.settings.deviceinfo.StorageMeasurement.FileInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.widget.TextView;

/**
 * This class handles the selection and removal of Misc files.
 */
public class MiscFilesHandler extends ListActivity {

    private static final String TAG = "MemorySettings";
    private String mNumSelectedFormat;
    private String mNumBytesSelectedFormat;
    private MemoryMearurementAdapter mAdapter;
    private LayoutInflater mInflater;
    private AlertDialog mWarnDeleteFile;
    /* SPRD @{ */
    private ArrayList<Object> mToRemove = null;
    private ProgressDialog mDeleteDataBar = null;
    private ActionMode mActionMode =null;
    private static final int DELETE_FILE_MESSAGE_MISC = 1;
    private static final int CANCLE_DIALOG = 2;
    private boolean mIsCancel = false;
    private SparseBooleanArray mItemsStateArray;
    private TextView textView1;
    /* @} */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFinishOnTouchOutside(true);
        setTitle(R.string.misc_files);
        mNumSelectedFormat = getString(R.string.misc_files_selected_count);
        mNumBytesSelectedFormat = getString(R.string.misc_files_selected_count_bytes);
        mAdapter = new MemoryMearurementAdapter(this);
        mInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        setContentView(R.layout.settings_storage_miscfiles_list);
        textView1 = (TextView) findViewById(R.id.textView1);//SPRD:add for bug638786
        final ListView lv = getListView();

        lv.setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView parent, View view, int position, long id) {
                if (!mAdapter.isChecked(position)) {
                    mItemsStateArray.put(position, true);
                } else {
                    mItemsStateArray.delete(position);
                }
                lv.setItemChecked(position, !mAdapter.isChecked(position));
            }
        });
        lv.setItemsCanFocus(true);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        lv.setMultiChoiceModeListener(new ModeCallback(this));
        setListAdapter(mAdapter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWarnDeleteFile != null) {
            mWarnDeleteFile.dismiss();
        }
        // SPRD: break deleting files
        if (!mIsCancel) {
            mIsCancel = true;
        }
        // SPRD: force dimiss dialog
        if (mDeleteDataBar != null) {
            mDeleteDataBar.dismiss();
        }
    }

    private class ModeCallback implements ListView.MultiChoiceModeListener {
        private int mDataCount;
        private final Context mContext;

        public ModeCallback(Context context) {
            mContext = context;
            mDataCount = mAdapter.getCount();
        }

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            final MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.misc_files_menu, menu);
            return true;
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
            case R.id.action_delete:
                // delete the files selected
                warnDeleteFileDialog(mode);
                break;
            // SPRD: settings storage, no select all button
            case R.id.action_select_all:

                int selectedSize = mItemsStateArray.size();
                boolean isAllChecked = selectedSize != mDataCount;
                if (isAllChecked) {
                    for (int i = 0; i < mDataCount; i++) {
                        mItemsStateArray.put(i, isAllChecked);
                    }
                } else {
                    mItemsStateArray.clear();
                    getListView().clearChoices();
                }
                // update the title and subtitle with number selected and numberBytes selected
                onItemCheckedStateChanged(mode, 1, 0, isAllChecked);
                if (isAllChecked) {
                    item.setTitle(R.string.cancel_select_all);
                    item.setIcon(android.R.drawable.ic_menu_close_clear_cancel);//SPRD:add for bug636864
                }
                mAdapter.notifyDataSetChanged();
                break;
            }
            return true;
        }
        /* SPRD:ADD @{ */
        private void createDeleteDialog(){
            if(mWarnDeleteFile != null){
                if(mContext != null){
                    mWarnDeleteFile.dismiss();
                    mWarnDeleteFile = null;

                    mDeleteDataBar = new ProgressDialog(mContext);
                    mDeleteDataBar.setTitle(getResources().getString(R.string.title_delete_other_file));
                    mDeleteDataBar.setMessage(getResources().getString(R.string.msg_deleteing_other_file));
                    mDeleteDataBar.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    mDeleteDataBar.setCancelable(false);
                    mDeleteDataBar.show();
                }
            }
        }

        private void deleteFiles() {
            new Thread() {
                @Override
                public void run() {

                    DeleteFileSelected();

                    Message msgCancleDialog = new Message();
                    msgCancleDialog.what = CANCLE_DIALOG;
                    mHandler.sendMessage(msgCancleDialog);
                }
            }.start();
        }

        private Handler mHandler = new Handler(){

            @Override
            public void handleMessage(Message msg) {
                if(msg != null){
                    switch(msg.what){
                    case DELETE_FILE_MESSAGE_MISC:
                        createDeleteDialog();
                        deleteFiles();
                        break;
                    case CANCLE_DIALOG:
                        if(mToRemove != null){
                            updateAdapter(mToRemove);
                        }
                        if(mActionMode != null){
                            mActionMode.finish();
                        }
                        if((mDeleteDataBar != null) && (mDeleteDataBar.isShowing())){
                            mDeleteDataBar.dismiss();
                            mDeleteDataBar = null;
                        }
                        /*SPRD:add for bug638786 @{*/
			if(mDataCount<=0){
                            textView1.setVisibility(View.VISIBLE);
			}else{
                            textView1.setVisibility(View.GONE);
			}
			/*}@*/
                        break;
                    default:
                        break;
                    }
                }
            }

        };
        /* @} */

        /* SPRD:add for fix bug191693 @{ */
        private void warnDeleteFileDialog(final ActionMode mode) {
            mWarnDeleteFile = new AlertDialog.Builder(this.mContext)
                    .setCancelable(false)
                    .setTitle(getResources().getString(R.string.error_title))
                    .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
                    .setMessage(
                            getResources().getString(
                                    R.string.delete_file_warning))
                    .setPositiveButton(android.R.string.yes,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    Message msgDeleteFile = new Message();
                                    msgDeleteFile.what = DELETE_FILE_MESSAGE_MISC;
                                    mActionMode = mode;
                                    mHandler.sendMessage(msgDeleteFile);
                                }
                            }).setNegativeButton(android.R.string.no, null)
                    .show();
        }
        /* @} */

        private void DeleteFileSelected(){

            int checkedCount = getListView().getCheckedItemCount();
            if (checkedCount > mDataCount) {
                throw new IllegalStateException(
                        "checked item counts do not match. " + "checkedCount: "
                                + checkedCount + ", dataSize: " + mDataCount);
            }
            if (mDataCount > 0) {
                // SPRD: can delete files.
                mIsCancel = false;
                mToRemove = new ArrayList<Object>();
                for (int i = 0; i < mDataCount; i++) {
                    // SPRD: force stop cancel files when activity destroy.
                    if (mIsCancel) {
                        return;
                    }
                    if (!mItemsStateArray.get(i)) {
                        // item not selected
                        continue;
                    }
                    if (StorageMeasurement.LOGV) {
                        Log.i(TAG, "deleting: " + mAdapter.getItem(i));
                    }
                    // delete the file
                    File file = new File(mAdapter.getItem(i).mFileName);
                    if (file.isDirectory()) {
                        deleteDir(file);
                    } else {
                        file.delete();
                    }
                    mItemsStateArray.delete(i);
                    mToRemove.add(mAdapter.getItem(i));
                }
                // SPRD: flag delete sucess.
                mIsCancel = true;
                /* SPRD:DELETE @{ */
                //mAdapter.removeAll(toRemove);
                //mAdapter.notifyDataSetChanged();
                //mDataCount = mAdapter.getCount();
                /* @} */
            }
        }
        /* SPRD:ADD @{ */
        private void updateAdapter(ArrayList<Object> removedList){
            if(mAdapter != null){
                mAdapter.removeAll(removedList);
                mAdapter.notifyDataSetChanged();
                mDataCount = mAdapter.getCount();
            }
        }
        /* @} */
        /*add for fix bug191693 end*/
        // Deletes all files and subdirectories under given dir.
        // Returns true if all deletions were successful.
        // If a deletion fails, the method stops attempting to delete and returns false.
        private boolean deleteDir(File dir) {
            if (dir.isDirectory()) {
                String[] children = dir.list();
                /* Modify at 2013-02-21, for Porting from 4.0 MP barnch start */
                /**
                 * for (int i = 0; i < children.length; i++) { boolean success =
                 * deleteDir(new File(dir, children[i])); if (!success) { return
                 * false; } }
                 */
                if(children != null){
                    for (int i = 0; i < children.length; i++) {
                        boolean success = deleteDir(new File(dir, children[i]));
                        if (!success) {
                            return false;
                        }
                    }
                }
                /* Modify at 2013-02-21, for Porting from 4.0 MP barnch end */
            }
            // The directory is now empty so delete it
            return dir.delete();
        }

        public void onDestroyActionMode(ActionMode mode) {
            // SPRD:ADD clear the list.
            if(null != mItemsStateArray) {
            	mItemsStateArray.clear();
            }
        }

        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                boolean checked) {

            int numChecked = mItemsStateArray.size();

            mode.setTitle(String.format(mNumSelectedFormat, numChecked, mAdapter.getCount()));

            // total the sizes of all items selected so farnumChecked
            long selectedDataSize = 0;
            if (numChecked > 0) {
                for (int i = 0; i < numChecked; i++) {
                    int itemPosition = mItemsStateArray.keyAt(i);
                    selectedDataSize += mAdapter.getItem(itemPosition).mSize;
                }
            }

            mode.setSubtitle(String.format(mNumBytesSelectedFormat,
                    Formatter.formatFileSize(mContext, selectedDataSize),
                    Formatter.formatFileSize(mContext, mAdapter.getDataSize())));
            // SPRD:fix bug 208833 settings storage, no select all button
            boolean isAllChecked = numChecked != mDataCount;
            mode.getMenu().findItem(R.id.action_select_all).setTitle(isAllChecked ? R.string.select_all : R.string.cancel_select_all);
            mode.getMenu().findItem(R.id.action_select_all).setIcon(isAllChecked ? android.R.drawable.ic_menu_add : android.R.drawable.ic_menu_close_clear_cancel);//SPRD:add for bug636864
        }
    }

    class MemoryMearurementAdapter extends BaseAdapter {
        private ArrayList<StorageMeasurement.FileInfo> mData = null;
        private long mDataSize = 0;
        private Context mContext;

        public MemoryMearurementAdapter(Activity activity) {
            mContext = activity;
            final StorageVolume storageVolume = activity.getIntent().getParcelableExtra(
                    StorageVolume.EXTRA_STORAGE_VOLUME);
            StorageMeasurement mMeasurement =
                StorageMeasurement.getInstance(activity, storageVolume);
            if (mMeasurement == null) return;
            // SPRD: add synchronized for bug 176015
            synchronized (mMeasurement.mFileInfoForMisc) {
                mData = (ArrayList<StorageMeasurement.FileInfo>) mMeasurement.mFileInfoForMisc;
                if (mData != null) {
                    mItemsStateArray = new SparseBooleanArray(mData.size());
                    for (StorageMeasurement.FileInfo info : mData) {
                        mDataSize += info.mSize;
                    }
                }
            }
        }

        public boolean isChecked(int position) {
            return mItemsStateArray.get(position);
        }

        @Override
        public int getCount() {
            return (mData == null) ? 0 : mData.size();
        }

        @Override
        public StorageMeasurement.FileInfo getItem(int position) {
            if (mData == null || mData.size() <= position) {
                return null;
            }
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            if (mData == null || mData.size() <= position) {
                return 0;
            }
            return mData.get(position).mId;
        }

        public void removeAll(List<Object> objs) {
            if (mData == null) {
                return;
            }
            for (Object o : objs) {
                mData.remove(o);
                mDataSize -= ((StorageMeasurement.FileInfo) o).mSize;
            }
        }

        public long getDataSize() {
            return mDataSize;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final FileItemInfoLayout view = (convertView == null) ?
                    (FileItemInfoLayout) mInflater.inflate(R.layout.settings_storage_miscfiles,
                            parent, false) : (FileItemInfoLayout) convertView;
            FileInfo item = getItem(position);
            view.setFileName(item.mFileName);
            view.setFileSize(Formatter.formatFileSize(mContext, item.mSize));
            final ListView listView = (ListView) parent;
            final int listPosition = position;

            if (mItemsStateArray.get(position)) {
                listView.setItemChecked(position, true);
            } else {
                listView.setItemChecked(position, false);
            }

            view.getCheckBox().setOnCheckedChangeListener(new OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        mItemsStateArray.put(listPosition, true);
                    } else {
                        mItemsStateArray.delete(listPosition);
                    }
                    listView.setItemChecked(listPosition, isChecked);
                }

            });
            view.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (listView.getCheckedItemCount() > 0) {
                        return false;
                    }
                    if (!view.isChecked()) {
                        mItemsStateArray.put(listPosition, true);
                    } else {
                        mItemsStateArray.delete(listPosition);
                    }
                    listView.setItemChecked(listPosition, !view.isChecked());
                    return true;
                }
            });
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!view.isChecked()) {
                        mItemsStateArray.put(listPosition, true);
                    } else {
                        mItemsStateArray.delete(listPosition);
                    }
                    listView.setItemChecked(listPosition, !view.isChecked());
                }
            });
            return view;
        }
    }

    @Override
    protected void onDestroy() {
      super.onDestroy();
      mItemsStateArray.clear();
    }
}
