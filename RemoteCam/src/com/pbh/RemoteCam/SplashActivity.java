package com.pbh.RemoteCam;

import com.pbh.RemoteCam.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class SplashActivity extends Activity {

	@SuppressLint("HandlerLeak")
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_splash);

		Handler handler = new Handler(){	
			public void handlerMessage(Message msg){
				finish();
			}
		};
		handler.sendEmptyMessageDelayed(0,1000);
	}
}
