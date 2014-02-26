package com.pbh.RemoteCam;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;

import com.pbh.RemoteCam.BT.BluetoothService;
import com.pbh.RemoteCam.BT.DeviceListActivity;
import com.pbh.RemoteCam.Camera.*;
import com.pbh.RemoteCam.util.*;
import com.pbh.RemoteCam.R;

/**
 * https://github.com/bh714/RemoteCamera.git
 * 
 * @author park
 * 
 */
public class MainActivity extends Binding {
	// Debugging
	private static final String TAG = "MainActivity";
	private static final boolean D = false;
	
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    //private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    private int USER = 0;
	
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    
    TextView tv;
    
	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
		
        // activity handler 등록
        setHandler(new incom());
        // Mannager 에 bind 함
        doBindService(MainActivity.this, Manager.class);
        
		// BT on 이 아니면 Intent 로 on 할 수 있도록 요청함
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }else{
        	setupMainActivity();
        }
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(D) Log.i(TAG, "++ OnCreate() ++");
		
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // mBluetoothAdapter == null 이면 BT 지원하지 않음
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
	}

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");
        
    }
	
    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth services
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }
    
    
    private void setupMainActivity(){
    	while(!mIsBound){}
    	
    	setContentView(R.layout.activity_main);

    	tv = (TextView)findViewById(R.id.tv_state);
    	tv.setText(R.string.tv_how_to_use);
    	
    	LinearLayout btClient = (LinearLayout)findViewById(R.id.bt_client);
		btClient.setOnClickListener(listener);
		
		// Discoverable 함 -> server!!!!!
		LinearLayout btEtc = (LinearLayout)findViewById(R.id.bt_server);
		btEtc.setOnClickListener(listener);
		
		LinearLayout btStartCamera = (LinearLayout)findViewById(R.id.bt_etc);
		btStartCamera.setOnClickListener(listener);
    }
    
    View.OnClickListener listener = new OnClickListener(){
		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			switch(v.getId()){
			case R.id.bt_etc :
				setupCameraActivity(USER);
				break;
			case R.id.bt_client :
				Intent serverIntent = new Intent(MainActivity.this, DeviceListActivity.class);
	            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
				break;
			case R.id.bt_server :
	            ensureDiscoverable();
				break;
			}
		}
    };
    
    private void setupCameraActivity(int user){
        if(D) Log.i(TAG, "+++ setup Camera Activity "+ USER + "(1.server 2.client) +++");
        Intent intent = null;
    	switch(user){
    	case BluetoothService.SERVER :
    		intent = new Intent(MainActivity.this, ServerActivity.class);
    		startActivity(intent);
    		break;
    	case BluetoothService.CLIENT :
    		intent = new Intent(MainActivity.this, ClientActivity.class);
    		startActivity(intent);
    		break;
    	default :
    		Toast.makeText(getApplicationContext(), "찍을건지 선택/BT 연결 후에 ^^"
	        		, Toast.LENGTH_SHORT)
	        		.show();
    	}
    	
    } // setupCameraActivity
   
	private void connectDevice(Intent data) {
        // Get the device MAC address
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Send the name of the connected device back to the UI Activity
        Message msg = Message.obtain(null, Manager.MSG_DEVICE_CONNECT);
        Bundle bundle = new Bundle();
        bundle.putString(Manager.DEVICE_ADRESS, address);
        msg.setData(bundle);
        send(TAG,msg);
    }
	
	/**
	 *  BT 가 scan mode 가 아니면 300초 동안 scan mode 가 되도록 요청한다
	 *  scan mode 일 경우, Toast 를 띄워 scan mode 임을 알린다
	 */
    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
            if(mIsBound){
	            Message rmsg = Message.obtain(null, Manager.MSG_BT_SART);
	    		send(TAG, rmsg);
            }else{
            	Toast.makeText(getApplicationContext(), "연결아직 안대써!!"
    	        		, Toast.LENGTH_SHORT)
    	        		.show();
            }
        }
        else if (mBluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE){
	        Toast.makeText(getApplicationContext(), "검색 노출 상태 입니다."
	        		, Toast.LENGTH_SHORT)
	        		.show();
        }
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE_SECURE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK)  connectDevice(data);
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
        		setupMainActivity();
            } else {
                // User did not enable Bluetooth or an error occurred
                if(D) Log.d(TAG, "BT not enabled");
                Toast.makeText(getApplicationContext(), R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    
    public class incom extends Handler {
        @Override
        public void handleMessage(Message msg) {
        	switch (msg.what) {
        	case Manager.MSG_REGISTER_CLIENT :
        		mIsBound = true;
        		break;
        	case Manager.MSG_STATE_CHANGE:
                switch (msg.arg1) {
                case BluetoothService.STATE_CONNECTED:
                	tv.setText(R.string.tv_connected);
                	break;
                case BluetoothService.STATE_CONNECTING:
                	tv.setText(R.string.tv_connecting);
                	break;
                case BluetoothService.STATE_LISTEN:
                	tv.setText(R.string.tv_listen);
                	break;
                case BluetoothService.STATE_NONE:
                	tv.setText(R.string.tv_how_to_use);
                	break;
                case BluetoothService.CLIENT:
                	USER = BluetoothService.CLIENT;
                	break;
                case BluetoothService.SERVER:
                	USER = BluetoothService.SERVER;
                	break;
                }
            }
        }
    };
}
