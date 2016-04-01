package fr.jeremiegarcia.metaosc;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import com.illposed.osc.OSCMessage;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.module.Led;

import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements ServiceConnection {


    //Meta Wear
    protected MetaWearBleService.LocalBinder serviceBinder;
    private final static int REQUEST_ENABLE_BT=1;

    private final String MW_MAC_ADDRESS1 = "DB:94:69:9C:46:73";
    private MetaWearBoard mwBoard1;

    private final String MW_MAC_ADDRESS2= "D5:72:4F:06:B3:51";
    private MetaWearBoard mwBoard2;


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
// Typecast the binder to the service's LocalBinder class
        Log.i("LOG", "Service Connected");
        serviceBinder = (MetaWearBleService.LocalBinder) service;

        blinkingText(findViewById(R.id.meta1Led),true);
        blinkingText(findViewById(R.id.meta1Battery),true);

        blinkingText(findViewById(R.id.meta2Led),true);
        blinkingText(findViewById(R.id.meta2Battery), true);

        retrieveBoard();
        connectBoard();
        Log.i("LOG", "Init Interface");
        initOSCFields();
        initInterface();
    }

    public void retrieveBoard() {
        Log.i("LOG", "Retrieving boards");
        final BluetoothManager btManager=
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice1=
                btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS1);
        final BluetoothDevice remoteDevice2=
                btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS2);

        // Create a MetaWear board object for the Bluetooth Device
        mwBoard1= serviceBinder.getMetaWearBoard(remoteDevice1);
        mwBoard2= serviceBinder.getMetaWearBoard(remoteDevice2);
    }


    public void connectBoard() {
        Log.i("LOG", "Connecting boards");
        mwBoard1.setConnectionStateHandler(new Sensor2OSCConnectionStateHandler(mwBoard1, 1, this));
        mwBoard1.connect();

        mwBoard2.setConnectionStateHandler(new Sensor2OSCConnectionStateHandler(mwBoard2, 2, this));
        mwBoard2.connect();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("LOG", "On Create");
        super.onCreate(savedInstanceState);

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter!=null)
        {
            if(!mBluetoothAdapter.isEnabled())
            {
                Log.e("LOG", "Bluetooth not enabled");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }else{
            Log.e("LOG", "Bluetooth not available");
            new AlertDialog.Builder(this)
                    .setTitle("Bluetooth not supported")
                    .setMessage("Your device does not support bluetooth...")
                    .setIcon(android.R.drawable.stat_notify_error)
                    .show();

        }


        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        loadPreferences();

        Log.i("LOG", "BIND Service");
        // Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class),
                this, Context.BIND_AUTO_CREATE);

    }

    private void initInterface() {
        CheckBox ledChkBx1 = (CheckBox) findViewById( R.id.meta1Led );
        CheckBox ledChkBx2 = (CheckBox) findViewById( R.id.meta2Led );

        ledChkBx1.setOnCheckedChangeListener(new LedOnCkeckedChangeListener(mwBoard1, Led.ColorChannel.GREEN));
        ledChkBx2.setOnCheckedChangeListener(new LedOnCkeckedChangeListener(mwBoard2, Led.ColorChannel.BLUE));

    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        savePreferences();

        // Unbind the service when the activity is destroyed
        if(mwBoard1!=null && mwBoard1.isConnected()){
            Log.i("LOG", "Disconnecting board 1");
            mwBoard1.disconnect();
        }
        if(mwBoard2!=null && mwBoard2.isConnected()){
            Log.i("LOG", "Disconnecting board 2");
            mwBoard2.disconnect();
        }
        getApplicationContext().unbindService(this);
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.i("LOG", "On Pause");
        savePreferences();
    }



    @Override
    protected void onResume() {
        super.onResume();
        Log.i("LOG", "On Resume");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private void blinkingText(View box, boolean startStop) {
        if(startStop){
            Animation anim = new AlphaAnimation(0.1f, 1.0f);
            anim.setDuration(1000); //You can manage the time of the blink with this parameter
            anim.setStartOffset(20);
            anim.setRepeatMode(Animation.REVERSE);
            anim.setRepeatCount(Animation.INFINITE);
            box.startAnimation(anim);
        }else{
            box.clearAnimation();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.i("LOG DISCONECTED", name.toString());
    }

    public void updateBoardStatus(int id, boolean isConnected){
        CheckBox box = null;
        TextView txt = null;
        if(id==1){
            box = (CheckBox) findViewById(R.id.meta1Led);
            txt = (TextView) findViewById(R.id.meta1Battery);
        } else {
            box = (CheckBox) findViewById(R.id.meta2Led);
            txt = (TextView) findViewById(R.id.meta2Battery);
        }

        box.setClickable(isConnected);
        blinkingText(box,!isConnected);
        blinkingText(txt,!isConnected);

    }

    public void updateBoardBattery(int id, int percentage){
        TextView txt = null;
        if(id==1){
            txt = (TextView) findViewById(R.id.meta1Battery);
        } else {
            txt = (TextView) findViewById(R.id.meta2Battery);
        }

        if (txt != null) {
            txt.setText(percentage + "%");
        }
    }

    private static final Pattern PARTIAl_IP_ADDRESS =
            Pattern.compile("^((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9])\\.){0,3}" +
                    "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9])){0,1}$");

    private void initOSCFields() {

        final EditText ipEditText = (EditText) findViewById(R.id.oscIp);
        if (ipEditText != null) {
            ipEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {

                private String mPreviousText = "";

                @Override
                public void onFocusChange(View v, boolean hasFocus) {

                    if (!hasFocus) {
                        Editable s = ipEditText.getText();
                        if (PARTIAl_IP_ADDRESS.matcher(s).matches()) {
                            mPreviousText = s.toString();
                            OSCManager.setIp(s.toString());
                        } else {
                            s.replace(0, s.length(), mPreviousText);
                        }
                    }
                }
            });

            ipEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

                private String mPreviousText = "";
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        Editable s = ((EditText)v).getText();
                        if (PARTIAl_IP_ADDRESS.matcher(s).matches()) {
                            mPreviousText = s.toString();
                            OSCManager.setIp(s.toString());
                        } else {
                            s.replace(0, s.length(), mPreviousText);
                        }
                    }
                    return false;
                }
            });
        }

        final EditText portEditText = (EditText) findViewById(R.id.oscPort);
        if (portEditText != null) {
            portEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {

                @Override
                public void onFocusChange(View v, boolean hasFocus) {

                    if (!hasFocus) {
                        Editable s = portEditText.getText();
                        int port = Integer.parseInt(s.toString());
                        OSCManager.setPort(port);
                    }
                }
            });

            portEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        Editable s = ((EditText) v).getText();

                        int port = Integer.parseInt(s.toString());
                        OSCManager.setPort(port);
                    }

                return false;
            }
        });
        }

        Button helloOsc = (Button) findViewById(R.id.oscHello);
        if(helloOsc!=null){
            helloOsc.setOnClickListener(new View.OnClickListener() {
                OSCMessage mess = new OSCMessage("/hello");

                @Override
                public void onClick(View v) {
                    OSCManager.sendOscMessage(mess);
                }
            });
        }
    }

    private void savePreferences() {
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("IP",OSCManager.getIp());
        editor.putInt("PORT", OSCManager.getPort());
        editor.commit();
    }

    private void loadPreferences() {
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        String ip = sharedPref.getString("IP", OSCManager.getIp());
        int port = sharedPref.getInt("PORT", OSCManager.getPort());

        Log.i("LOG LOAD", ip + " " + port );

        ((EditText) findViewById(R.id.oscIp)).setText(ip);
        OSCManager.setIp(ip);

        ((EditText) findViewById(R.id.oscPort)).setText(Integer.toString(port));
        OSCManager.setPort(port);
    }

}
