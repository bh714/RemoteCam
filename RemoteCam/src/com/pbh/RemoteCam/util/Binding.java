package com.pbh.RemoteCam.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

/**
 * 
 *  public
 *  ============================================================================================
 *  mService :	mService.send(msg); 와 같이 msg 객체를 담아 보낼 수 있음
 *  doBindService(Context me, Class<?> target) : Service 를 bind 함 setHandler() 이후 호출 
 *  doUnbindService() :	Service 를 unbind 함 onDestroy() 시 호출됨
 *  ============================================================================================
*/

public class Binding extends Activity{
	/** Messenger for communicating with service. */
	public Messenger mService = null;
	/** Flag indicating whether we have called bind on the service. */
	public boolean mIsBound = false;
	
	private Messenger mMessenger=null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
	}

	public void send(String TAG, Message msg){
		try {
			msg.replyTo = mMessenger;
			mService.send(msg);
			Log.i(TAG,"send("+msg.what+")");
		} catch (RemoteException e) {
		}
	}
	
	public void setHandler(Handler handler){
		mMessenger = new Messenger(handler);
	}

	/**
	 * Class for interacting with the main interface of the service.
	 */
	public ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = new Messenger(service);
			try {
				Message msg = Message.obtain(null, Manager.MSG_REGISTER_CLIENT);
				msg.replyTo = mMessenger;
				mService.send(msg);
			} catch (RemoteException e) {
			}
		}
		public void onServiceDisconnected(ComponentName className) {
			mService = null;
		}
	};

	/**
	 * doBindService
	 * @param me Context
	 * @param target Class<?>
	 */
	public void doBindService(Context me, Class<?> target) {
		mIsBound = bindService(new Intent(me, target), mConnection, Context.BIND_AUTO_CREATE);
	}

	public void doUnbindService() {
		if (mIsBound) {
			if (mService != null) {
				try {
					Message msg = Message.obtain(null,Manager.MSG_UNREGISTER_CLIENT);
					msg.replyTo = mMessenger;
					mService.send(msg);
				} catch (RemoteException e) {
				}
			}
			unbindService(mConnection);
			mIsBound = false;
		}
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		doUnbindService();
	}
	
}
