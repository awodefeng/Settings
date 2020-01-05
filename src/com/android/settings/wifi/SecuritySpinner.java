/** Create by Spreadst */

package com.android.settings.wifi;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodManager;
import android.widget.Spinner;

public class SecuritySpinner extends Spinner {
    private Context mContext;

    public SecuritySpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    public boolean performClick() {
        hideSoftKeyboard();
        return super.performClick();
    }

    private void hideSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }
}
