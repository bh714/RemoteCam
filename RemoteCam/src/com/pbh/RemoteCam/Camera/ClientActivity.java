package com.pbh.RemoteCam.Camera;

import java.util.ArrayList;

import com.pbh.RemoteCam.util.Binding;
import com.pbh.RemoteCam.util.Manager;
import com.pbh.RemoteCam.R;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

public class ClientActivity extends Binding {
	// debuging
	private static final boolean D = true;
	private static final String TAG = "PreviewActivity";
	
    /**
     *  검색 public int screenOrientation
     *  http://cafe.naver.com/cocos2dxusers/272 -> cocos2D-x
     *  http://developer.android.com/intl/ko/reference/android/R.attr.html#screenOrientation
     */
    public static final int ANDROID_BUILD_GINGERBREAD = 9;
    public static final int SCREEN_ORIENTATION_SENSOR_LANDSCAPE = 6;
    
	private static final int QUEUSIZE = 5;
    
    private ArrayList<byte[]> mImageBuffer;
    private ArrayList<byte[]> mImagePlay;
    private ClientPreview mPreview;
    
    private static int mImageIndex = 0; 
    private boolean isDisplay = false;
	
    @Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
		// activity handler 등록
        setHandler(new incom());
        // Mannager 에 bind 함
        doBindService(ClientActivity.this, Manager.class);
	}


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		
		// Hide the window title, fullscreen, set orientation
		requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if(Build.VERSION.SDK_INT >= ANDROID_BUILD_GINGERBREAD)
        {
        	setRequestedOrientation(SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
		
        setupActivity();
        
        mImageBuffer = new ArrayList<byte[]>(QUEUSIZE);
	}
	
	private void setupActivity(){
		// RelativeLayout 객체 생성하여 Preview 화면을 가운데에 배치
        // 기타 View 객체들을 작업하려면 이 곳에서 하면 됨
        LayoutInflater inflater = getLayoutInflater(); 
        View view = inflater.inflate(R.layout.activity_camera , null /*ROOT_GROUP*/);
        
        FrameLayout fl = (FrameLayout)view.findViewById(R.id.FrameLayout);
        
        // camera preview
        mPreview = new ClientPreview(view.getContext());
        FrameLayout.LayoutParams pparams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        mPreview.setLayoutParams(pparams);
        
        // button
        Button bt_takepic = (Button)view.findViewById(R.id.bt_capture);
        FrameLayout.LayoutParams bparams = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, 
        																LayoutParams.WRAP_CONTENT,
        																Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        bt_takepic.setLayoutParams(bparams);
        bt_takepic.setOnClickListener(btListner);
        
        fl.addView(mPreview);
        bt_takepic.bringToFront();
        
        setContentView(view);
	}
	
	public View.OnClickListener btListner = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
        	Message msg = Message.obtain(null, Manager.MSG_WRITE, Manager.TAKEPIC, -1, null);
			send(TAG,msg);
        }
    };

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		mImageBuffer.clear();
		mImagePlay.clear();
	}


	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}


	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}
	

	public class incom extends Handler {
	    @Override
	    public void handleMessage(Message msg) {
	    	Log.i(TAG,"Handle message : "+msg.what);
	    	switch (msg.what) {
	    	case Manager.MSG_READ :
	    		switch(msg.arg1){
	    		case Manager.IMAGE :
	    			if(mImageBuffer.size() > QUEUSIZE-1){
	    				if(mImagePlay != null) mImagePlay.clear();
	    				mImagePlay = (ArrayList<byte[]>)mImageBuffer.clone();
	    				mImageBuffer.clear();
	    				isDisplay = true;
	    				mImageIndex=0;
	    			}
	    			mImageBuffer.add(msg.getData().getByteArray(Manager.STR_FILE));
	    			
	    			if(isDisplay){
	    				mPreview.setImage(BitmapFactory.decodeByteArray(mImagePlay.get(mImageIndex)
	    						,0
	    						,mImagePlay.get(mImageIndex).length));
	    				if(!mPreview.isDisplay) mPreview.isDisplay = true;
	    				mImageIndex++;
	    			}
	    			break;
	    		}
	    		break;
	    	case Manager.MSG_REGISTER_CLIENT:
	    		break;
	        }
	    }
	};
}


//----------------------------------------------------------------------

class ClientPreview extends SurfaceView implements SurfaceHolder.Callback {
	// debuging
	private static final String TAG = "ClientPreview";
    private static final boolean D = true;
	
    //private Size mPreviewSize;
    //private List<Size> mSupportedPreviewSizes;
    private SurfaceHolder mHolder;
    private Thread mThread;
    private boolean isRunnable=true;
    private RectF mWinSize=null;
    
    public Bitmap image=null;
    public boolean isDisplay=false;
    
    public ClientPreview(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
		
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mThread = new Thread(drawThread);
	}

	private Runnable drawThread = new Runnable(){
		@Override
		public void run() {
			// TODO Auto-generated method stub
			if(D) Log.i(TAG,"++ Start canvas ++");
			Canvas canvas = null;
			while(isRunnable){
				canvas = mHolder.lockCanvas();
				try{
					synchronized(mHolder){
						if(isDisplay){
							canvas.drawColor(Color.BLACK);
							doDraw(canvas);
						}
					}
				}finally {
					if(canvas != null){
						mHolder.unlockCanvasAndPost(canvas);
					}
				}
			} // while
		}
	};

	public void doDraw(Canvas canvas){
		Paint paint = new Paint();
		paint.setColor(Color.RED);
		paint.setTextSize(18);
		
		canvas.drawBitmap(getImage(),null,mWinSize,null);
		//canvas.drawText(getImage().toString(),mWinSize.left+50,mWinSize.top+50,paint);
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		// TODO Auto-generated method stub
		if(D) Log.e(TAG, "surfaceChanged w: "+width+", h: "+height);
		mWinSize = new RectF(0,0,width,height);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		mThread.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		pause();
	}
	
	public void pause(){
		isRunnable = false;
	}
	
	public void setImage(Bitmap i){
		if(i == null) return;
		image = i;
	}
	
	public Bitmap getImage(){
		Bitmap rtImage;
		rtImage = Bitmap.createBitmap(image);
		return rtImage;
	}
}
