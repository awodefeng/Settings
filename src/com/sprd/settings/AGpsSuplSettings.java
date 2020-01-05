
package com.sprd.settings;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.android.settings.R;
import com.android.settings.R.xml;


public class AGpsSuplSettings extends Activity implements OnClickListener {
    /** Called when the activity is first created. */
    private String TAG = "GpsSettingsActivity";
    private EditText supl_server;
    private EditText supl_port;
    private Button supl_save;
    private Button supl_clear;
    private LocationManager loc_manager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.agps_supl_settings);

        supl_server = (EditText) findViewById(R.id.agps_supl_svr_address);
        supl_port = (EditText) findViewById(R.id.agps_supl_port_value);
        supl_save = (Button) findViewById(R.id.save_supl_info);
        supl_clear = (Button) findViewById(R.id.clear_svr_info);

        supl_save.setOnClickListener(this);
        supl_clear.setOnClickListener(this);
        loc_manager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        switch (v.getId()) {
            case R.id.save_supl_info:
                String supl_addr = supl_server.getText().toString();
                String strPort = supl_port.getText().toString();
                /*SPRD: fix bug341352 setting crash @{*/
                if (supl_addr.length() == 0 || strPort.length() == 0 || supl_addr.length() >= 20 || strPort.length() > 4) {
                /* @}*/
                    Toast.makeText(this, "please input valid supl server address and port number",
                            Toast.LENGTH_SHORT).show();
                    break;
                }
                Log.v(TAG, "supl_addr = " + supl_addr + " supl_addr = " + strPort);
                Intent retIntent = getIntent();
                retIntent.putExtra("supl_svr", supl_addr);
                retIntent.putExtra("supl_port", strPort);
                AGpsSuplSettings.this.setResult(0, retIntent);
                AGpsSuplSettings.this.finish();
            case R.id.clear_svr_info:
                supl_server.setText("");
                supl_port.setText("");
                break;
            default:
                break;
        }
    }

    @Override
    public void onBackPressed() {
        // TODO Auto-generated method stub
        AGpsSuplSettings.this.finish();
        super.onBackPressed();
    }

}
