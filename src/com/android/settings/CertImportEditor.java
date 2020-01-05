/** Create by Spreadst **/
package com.android.settings;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.io.IOException;
import android.app.AlertDialog;
import android.app.ActionBar;
import android.app.ProgressDialog;
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnCancelListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.provider.Settings;
import android.provider.MediaStore;
import android.widget.Toast;
import android.content.Context;
import android.security.KeyChain;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.content.ContentResolver;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.view.WindowManager;
import android.view.LayoutInflater;
import android.widget.EditText;;

public class CertImportEditor extends ListActivity {

    /* SPRD: dual T policy porting@{ */
   // String externalStoragePath = Environment.getExternalStoragePath().getAbsolutePath();
    String externalStoragePath = "/storage/";//Environment.getInternalStoragePath().getAbsolutePath();
    private File mCurrentDir = new File(externalStoragePath);

    private CertFileListAdapter mOnlyAdapter;

    // fix bug 221530 Unread field:
    // com.android.settings.CertImportEditor.fileDisplay on 20130924 begin
    // boolean fileDisplay = true;
    // fix bug 221530 Unread field:
    // com.android.settings.CertImportEditor.fileDisplay on 20130924 end
    // fix bug 219896 Unread field:
    // com.android.settings.CertImportEditor.backActivity on 20130924 begin
    // boolean backActivity = true;
    // fix bug 219896 Unread field:
    // com.android.settings.CertImportEditor.backActivity on 20130924 end
//    TextView mTitleText;
    ActionBar mActionBar ;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.path_file_manager);

        mActionBar = getActionBar();
        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        mActionBar.setDisplayShowTitleEnabled(true);
//      actionBar.setCustomView(R.layout.custom_title);
        mActionBar.setDisplayHomeAsUpEnabled(true);
//      mTitleText = (TextView) actionBar.getCustomView().findViewById(R.id.custom_title);

        // SPRD: dual T policy porting
        //externalStoragePath = Environment.getExternalStoragePath().getAbsolutePath();;//"/storage";//StorageUtils.getExternalStoragePathStr();
        externalStoragePath = "/storage/";Environment.getInternalStoragePath().getAbsolutePath();
        mCurrentDir = new File(externalStoragePath);
        mOnlyAdapter = new CertFileListAdapter(this);
        mOnlyAdapter.sortImpl( new File(externalStoragePath));
        setListAdapter(mOnlyAdapter);

        updateTitle();
    }

    public void updateListView()
    {
        mOnlyAdapter.sortImpl(mCurrentDir);
        updateTitle();
    }

    private static final int MAX_FILENAME_LEN = 60;
    public void updateTitle(){
        if(mCurrentDir == null){
            mActionBar.setTitle(getString(R.string.settings_label_launcher));
            return;
        }
        mActionBar.setTitle(mCurrentDir.getPath());
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        super.onListItemClick(l, v, position, id);

        String itemStr = ((CertFileListAdapter.ViewHolder)v.getTag()).filename.getText().toString();
        final File file = new File(mCurrentDir, itemStr);

        if(file.isDirectory() && file.canRead())
        {
            // Open the file folder
            mCurrentDir = new File(mCurrentDir.getPath() + "/" + itemStr);
            updateListView();
            getListView().setSelection(0);
        } else if (file.isFile()){
            Intent intent = new Intent("android.credentials.INSTALL");
            intent.putExtra("Settings_isFromSettings", true);
            intent.putExtra("Settings_CertFilePath", file.getAbsolutePath());
            startActivity(intent);
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        switch(keyCode)
        {
            case KeyEvent.KEYCODE_BACK:
            {
                if(mCurrentDir == null)
                    break;
                String str = mCurrentDir.getParentFile().getPath();
                String currentFileName = mCurrentDir.getName();
                if(!str.equals("/"))
                {
                    mCurrentDir = new File(str);
                    updateListView();

                    int index = ((CertFileListAdapter)getListAdapter()).getItemIndex(currentFileName);
                    getListView().setSelection(index);

                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public static String getFileExtendName(String filename)
    {
        int index = filename.lastIndexOf('.');
        return index == -1? null : filename.substring(index + 1);
    }

}
