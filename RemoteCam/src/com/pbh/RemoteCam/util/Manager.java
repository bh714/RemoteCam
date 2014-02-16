package com.pbh.RemoteCam.util;

import java.util.ArrayList;

import com.pbh.RemoteCam.BT.BluetoothService;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

/**
 *  app mannager
 *  write, read, toast, ui command, communication
 *
 */
public class Manager extends Service {
	// Debugging
	private static final String TAG = "MM";
	private static final boolean D = true;
	
    /** Regi 된 client list */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    /** client cnt*/
    int mCcnt = -1;
    
    // file save path
    public static final String savePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
    
    private String mConnectedDeviceName = null;
    
    // Key names received from the Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String DEVICE_ADRESS = "device_adress";
    public static final String SEND_IMAGE = "send image";
    public static final String TOAST = "toast";
    
    public static final String STR_FILE = "f";
    public static final String STR_INFO = "i";
    public static final String STR_EVENT = "e";
    
    // MSG 선언부
    // msg.what
    /** msg.what */
    public static final int MSG_REGISTER_CLIENT = 1;
    /** msg.what */
    public static final int MSG_UNREGISTER_CLIENT = 2;
    /** msg.what */
    public static final int MSG_NOTIFY = 11;
    
    // 통신관련
    /** msg.what */
    public static final int MSG_STATE_CHANGE = 3;
    /** msg.what */
    public static final int MSG_READ = 4;
    /** msg.what */
    public static final int MSG_WRITE = 5;
    /** msg.what */
    public static final int MSG_DEVICE_NAME = 6;
    /** msg.what */
    public static final int MSG_DEVICE_ADDRESS = 7; // 왜 있는거지? 이거 뭐지?
    /** msg.what */
    public static final int MSG_DEVICE_CONNECT = 8;
    /** msg.what */
    public static final int MSG_BT_SART = 9;
    /** msg.what */
    public static final int MSG_TOAST = 10;
    
    
    // msg.arg1
    /** msg.arg1 */
    public static final int IMAGE = 1;
    /** msg.arg1 */
    public static final int TAKEPIC = 2;
    /** msg.arg1 */
    public static final int INFO = 3;
    
    // Messenger
    private Messenger mMessenger;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothService mBTService;
    
    int state=0;
    
    /**
     * Handler of incoming messages from clients.
     */
	class IncomingHandler extends Handler {
		
		@Override
		public void handleMessage(Message msg) {
			if(D) Log.i(TAG,"MSG what("+msg.what+")");
			
			String tempstr="";
			
			switch (msg.what) {
			
			// Service
			//--------------------------------------------------------------------------------------
			case MSG_REGISTER_CLIENT:
				mClients.add(msg.replyTo);
				mCcnt++;
				Log.i(TAG,"MSG_REGISTER_CLIENT: "+mCcnt);
				sendToClient(msg,mCcnt);
				break;
			case MSG_UNREGISTER_CLIENT:
				mClients.remove(msg.replyTo);
				mCcnt--;
				Log.i(TAG,"MSG_UNREGISTER_CLIENT: "+mCcnt);
				break;
			case MSG_NOTIFY :
				
				break;
			
			// Bluetooth
			//--------------------------------------------------------------------------------------
			case MSG_STATE_CHANGE:
                switch (msg.arg1) {
                case BluetoothService.STATE_CONNECTED:
                	break;
                case BluetoothService.STATE_CONNECTING:
                	break;
                case BluetoothService.STATE_LISTEN:
                	break;
                case BluetoothService.STATE_NONE:
                	break;
                case BluetoothService.CLIENT:
                	break;
                case BluetoothService.SERVER:
                	break;
                }
                sendToClient(msg, mCcnt);
                break;
                
			case MSG_BT_SART :
				if (mBTService != null)
					if (mBTService.getState() == BluetoothService.STATE_NONE)
						mBTService.start();
				break;
				
			case MSG_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), mConnectedDeviceName+" 에 연결되었습니다", Toast.LENGTH_SHORT)
                	.show();
                break;
                
			case MSG_DEVICE_CONNECT :
				tempstr = msg.getData().getString(DEVICE_ADRESS);
				
		        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(tempstr);
		        mBTService.connect(device, true, BluetoothService.CLIENT);
				break;	
				
			// read write
    		//--------------------------------------------------------------------------------------
			case MSG_READ:
				switch (msg.arg1) {
				case INFO :
					break;
				case IMAGE :
					sendToClient(msg, mCcnt);
					break;
				case TAKEPIC :
					if(D) Log.i(TAG,"<-----------------------------------takepic!!");
					sendToClient(msg, mCcnt);
					break;
				}
				break;
			case MSG_WRITE:
				byte[] wwriteBuf = null;

				switch (msg.arg1) {
				// client to server
				case TAKEPIC:
					if(state == BluetoothService.STATE_CONNECTED){
						tempstr = STR_EVENT;	// take pic
						mBTService.write(tempstr,"");
					}else{
						Toast.makeText(getApplicationContext(), "Bluetooth Not Connected", Toast.LENGTH_SHORT)
	                	.show();
					}
					break;
	/*
				case INFO:
					// temp = INFO+sPreview.getCamParam();
					// wwriteBuf = temp.getBytes();
					break;
						*/
				case IMAGE:
					tempstr = STR_FILE;
					if((wwriteBuf = msg.getData().getByteArray(SEND_IMAGE)) != null){
						mBTService.write(tempstr, wwriteBuf);
					}else{
						if(D) Log.e(TAG,"data is null!!");
					}
					break;
				}
				break;
				
			// etc
			//--------------------------------------------------------------------------------------
            case MSG_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
			default:
				super.handleMessage(msg);
			} // switch
		} // handler
	} // end class
	
	/**
	 * regi 된 client 에게 msg 날림 last target : mClients.size() - 1
	 * @param msg
	 * @param target
	 */
	private void sendToClient(Message msg, int target){
		int size=0;
		
		size = mClients.size() - 1;
		
		if(size != -1 && target != -1 && target <= size){
			try {
				mClients.get(target).send(msg);
			} catch (RemoteException e) { // client 죽었으니 list 에서 뺌
				mClients.remove(target);
			}	
		}
	}
	
	@Override
	public IBinder onBind(Intent Intent) {
		// TODO Auto-generated method stub
		return mMessenger.getBinder();
	}
	
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		if(D) Log.i(TAG, "++ OnCreate() ++");
		Handler handler = new IncomingHandler();
		mMessenger = new Messenger(handler);
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		mBTService = new BluetoothService(this, handler);
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		if (mBTService != null) mBTService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
	}

	
}
