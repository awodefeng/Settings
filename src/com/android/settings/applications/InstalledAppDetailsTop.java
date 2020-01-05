package com.android.settings.applications;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.settings.ChooseLockGeneric.ChooseLockGenericFragment;
import com.android.settings.R;

import com.sprd.android.support.featurebar.FeatureBarHelper;

public class InstalledAppDetailsTop extends PreferenceActivity {

    /* SPRD: Add for bug650008. @{ */
    public FeatureBarHelper mHelperBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setSoftKey();
    }

    private void setSoftKey() {
        mHelperBar = new FeatureBarHelper(this);
        ViewGroup vg = mHelperBar.getFeatureBar();
        if (vg != null) {
            View option = mHelperBar.getOptionsKeyView();
            ((TextView) option).setText(R.string.default_feature_bar_options);
            View back = mHelperBar.getBackKeyView();
            ((TextView) back).setText(R.string.default_feature_bar_back);
            View center = mHelperBar.getCenterKeyView();
            ((TextView) center).setText(R.string.default_feature_bar_center);
        }
    }
    /* @} */

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, InstalledAppDetails.class.getName());
        modIntent.putExtra(EXTRA_NO_HEADERS, true);
        return modIntent;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (InstalledAppDetails.class.getName().equals(fragmentName)) return true;
        return false;
    }

}
