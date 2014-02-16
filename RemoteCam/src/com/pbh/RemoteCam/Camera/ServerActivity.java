package com.pbh.RemoteCam.Camera;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;


import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.Bitmap.CompressFormat;

import com.pbh.RemoteCam.util.Binding;
import com.pbh.RemoteCam.util.Manager;
import com.pbh.RemoteCam.R;

public class ServerActivity extends Binding {
	// debuging
	private static final boolean D = true;
	private static final String TAG = "PreviewActivity";
	
	//private byte[] mSendImageData = null;
	private ServerPreview mPreview;
    private Camera mCamera;
    private incom mHandler;
    int numberOfCameras;
    int cameraCurrentlyLocked;
    
    // The first rear facing camera
    int defaultCameraId;
    
    /**
     *  검색 public int screenOrientation
     *  http://cafe.naver.com/cocos2dxusers/272 -> cocos2D-x
     *  http://developer.android.com/intl/ko/reference/android/R.attr.html#screenOrientation
     */
    public static final int ANDROID_BUILD_GINGERBREAD = 9;
    public static final int SCREEN_ORIENTATION_SENSOR_LANDSCAPE = 6;
       
    @Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
		// activity handler 등록
        setHandler(mHandler = new incom());
        // Mannager 에 bind 함
        doBindService(ServerActivity.this, Manager.class);
	}

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hide the window title, fullscreen, set orientation
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if(Build.VERSION.SDK_INT >= ANDROID_BUILD_GINGERBREAD)
        {
        	setRequestedOrientation(SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        
        // 활용 가능한 camera number 를 get
        numberOfCameras = Camera.getNumberOfCameras();

		// default camera ID 를 얻어옴
		CameraInfo cameraInfo = new CameraInfo();
		for (int i = 0; i < numberOfCameras; i++) {
			Camera.getCameraInfo(i, cameraInfo);
			if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
				defaultCameraId = i;
			}
		}
		
		setupActivity();
    }
	
	private void setupActivity(){
        LayoutInflater inflater = getLayoutInflater(); 
        View view = inflater.inflate(R.layout.activity_camera , null /*ROOT_GROUP*/);
        
        FrameLayout fl = (FrameLayout)view.findViewById(R.id.FrameLayout);
        
        // camera preview
        mPreview = new ServerPreview(view.getContext());
        FrameLayout.LayoutParams pparams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        mPreview.setLayoutParams(pparams);
        /*
        // button
        Button bt_takepic = (Button)view.findViewById(R.id.bt_capture);
        FrameLayout.LayoutParams bparams = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, 
        																LayoutParams.WRAP_CONTENT,
        																Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        bt_takepic.setLayoutParams(bparams);
        bt_takepic.setOnClickListener(btListner);
        */
        fl.addView(mPreview);
        //bt_takepic.bringToFront();
        
        setContentView(view);
	}
    
    public View.OnClickListener btListner = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
        	//Message msg = Message.obtain(null,Manager.MSG_WRITE,Manager.TAKEPIC,-1,null);
			//send(TAG,msg);
			
			Bundle bundle = new Bundle();
   			bundle.putByteArray(Manager.SEND_IMAGE, mPreview.mSendImagebuffer);
   			Message imgmsg = Message.obtain(null, Manager.MSG_WRITE, Manager.IMAGE, -1, null);
   			imgmsg.setData(bundle);
   			send(TAG, imgmsg);
        }
    };
    
    @Override
    protected void onResume() {
        super.onResume();
        // Open the default i.e. the first rear facing camera.
        mCamera = Camera.open();
        cameraCurrentlyLocked = defaultCameraId;
        mPreview.setCamera(mCamera);
        mPreview.setHandler(mHandler);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Because the Camera object is a shared resource, it's very
        // important to release it when the activity is paused.
        if (mCamera != null) {
        	mPreview.setCamera(null);
            mCamera.release();
            mCamera = null;
        }
    }

    public class incom extends Handler {
        @Override
        public void handleMessage(Message msg) {
        	switch (msg.what) {
        	case Manager.MSG_REGISTER_CLIENT:
        		mIsBound = true;
        		break;
        	case Manager.MSG_TOAST :
        		send(TAG,msg);
        		break;
        	case Manager.MSG_READ :
        		switch(msg.arg1){
        		case Manager.TAKEPIC : 
        			if(D) Log.i(TAG,"takepic");
        			mPreview.takePicture();
        			break;
        		}
        		break;
        	case Manager.MSG_WRITE :
        		if(mPreview.mSendImageStart && mIsBound){
		   			Bundle bundle = new Bundle();
		   			bundle.putByteArray(Manager.SEND_IMAGE, mPreview.getSendImageBuffer());
		   			Message imgmsg = Message.obtain(null, Manager.MSG_WRITE, Manager.IMAGE, -1, null);
		   			imgmsg.setData(bundle);
		   			send(TAG, imgmsg);
		   			
	        		//long dtime = 1000/ mPreview.FRAMES_PER_SEC;
	        		this.sendEmptyMessageDelayed(Manager.MSG_WRITE, 500);
        		}
        		break;
            }
        }
    };
}


// ----------------------------------------------------------------------


class ServerPreview extends ViewGroup implements SurfaceHolder.Callback {
	// debuging
	private static final String TAG = "Preview";
	private static final boolean D = true;
	
	private static final String savePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
	private static final int PREVIEW_FPS_MIN_INDEX = 0;
	private static final int PREVIEW_FPS_MAX_INDEX = 1;

	public final int FRAMES_PER_SEC = 25;
	public boolean mSendImageStart = false;
	public byte[] mSendImagebuffer=null;
	
	//private byte[] mImageDatabuffer=null;
	private SurfaceView mSurfaceView;
	private SurfaceHolder mHolder;
	private Size mPreviewSize;
	private List<Size> mSupportedPreviewSizes;
	private Camera mCamera;
	private Handler mHandler;
	private Camera.Parameters mParams;
	
    ServerPreview(Context context) {
        super(context);

        mSurfaceView = new SurfaceView(context);
        addView(mSurfaceView);
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void setCamera(Camera camera) {
        mCamera = camera;
        if (mCamera != null) {
        	// camera set
        	mParams = mCamera.getParameters();
        	// preview size set
            mSupportedPreviewSizes = mParams.getSupportedPreviewSizes();
            
            // Find closest FPS
            int closestRange[] = findClosestFpsRange(FRAMES_PER_SEC, mParams);
            mParams.setPreviewFpsRange(closestRange[PREVIEW_FPS_MIN_INDEX],
                                      closestRange[PREVIEW_FPS_MAX_INDEX]);
            mParams.setPreviewFormat(ImageFormat.NV21);
            mCamera.setParameters(mParams);
            
            requestLayout();
        }
    }

	// http://my.fit.edu/~vkepuska/ece5570/adt-bundle-windows-x86_64/sdk/sources/android-16/com/example/android/rs/sto/CameraCapture.java
    // FRAMES_PER_SEC 에 근접한 fps 를 찾아낸다.
    private int[] findClosestFpsRange(int fps, Camera.Parameters params) {
        List<int[]> supportedFpsRanges = params.getSupportedPreviewFpsRange();
        int[] closestRange = supportedFpsRanges.get(0);
        int fpsk = fps * 1000;
        int minDiff = 1000000;
        int diff=0;
        for (int[] range : supportedFpsRanges) {
            int low = range[PREVIEW_FPS_MIN_INDEX];
            int high = range[PREVIEW_FPS_MAX_INDEX];
            if (low <= fpsk && high >= fpsk) {
                diff = (fpsk - low) + (high - fpsk);
                if (diff < minDiff) {
                    closestRange = range;
                    minDiff = diff;
                }
            }
        }
        Log.i(TAG, "Found closest range: "
            + closestRange[PREVIEW_FPS_MIN_INDEX] + " - "
            + closestRange[PREVIEW_FPS_MAX_INDEX]);
        return closestRange;
    }
    
    public void setHandler(Handler handler){
    	mHandler = handler;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);

        if (mSupportedPreviewSizes != null) {
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
        }
    }

    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.5;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;
        
        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        int targetHeight = h;
        
        // Try to find an size match aspect ratio and size
        //for (Size size : sizes) {
        for(int i = 0; sizes.size() > i; i++){
        	Size size = sizes.get(i);
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
                if(size.width==1280 && size.height==960){ 
            		optimalSize = size;
            	}
            }
        }
        
        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);

            final int width = r - l;
            final int height = b - t;

            int previewWidth = width;
            int previewHeight = height;
            if (mPreviewSize != null) {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;
            }

            // Center the child SurfaceView within the parent.
            if (width * previewHeight > height * previewWidth) {
                final int scaledChildWidth = previewWidth * height / previewHeight;
                child.layout((width - scaledChildWidth) / 2, 0,
                        (width + scaledChildWidth) / 2, height);
            } else {
                final int scaledChildHeight = previewHeight * width / previewWidth;
                child.layout(0, (height - scaledChildHeight) / 2,
                        width, (height + scaledChildHeight) / 2);
            }
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(holder);
            }
        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        if (mCamera != null) {
        	mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mSendImageStart = false;
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Now that the size is known, set up the camera parameters and begin
        // the preview.
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        requestLayout();

        mCamera.setPreviewCallback(mFrame);
        //int expectedBytes = mPreviewSize.width * mPreviewSize.height *
    	//		ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
        //mCamera.addCallbackBuffer(mImageDatabuffer = new byte[expectedBytes]);
        
        mCamera.setParameters(parameters);
        mCamera.startPreview();
        
    }
    
	public void takePicture(){
		mCamera.takePicture(null, null, mPicture);
	}
    
    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {
	    @Override
	    public void onPictureTaken(byte[] data, Camera camera) {
	        File pictureFile = getOutputMediaFile();
	        if (pictureFile == null){
	            if(D) Log.d(TAG, "Error creating media file");
	            return;
	        }
	        try {
	            FileOutputStream fos = new FileOutputStream(pictureFile);
	            fos.write(data);
	            fos.close();
	            if(D) Log.i(TAG, " == PIC SAVE SUCCESSED ==");
	        } catch (FileNotFoundException e) {
	            if(D) Log.d(TAG, "File not found: " + e.getMessage());
	        } catch (IOException e) {
	            if(D) Log.d(TAG, "Error accessing file: " + e.getMessage());
	        } finally{
	        	mCamera.stopPreview();
	        	mCamera.startPreview();
	            Message msg = mHandler.obtainMessage(Manager.MSG_TOAST);
	            Bundle bundle = new Bundle();
	            bundle.putString(Manager.TOAST, "사진 저장완료");
	            msg.setData(bundle);
	            mHandler.sendMessage(msg);
	        }
	    }
	};

	private static File getOutputMediaFile(){
	    File SaveDir = new File(savePath, "RemoteCamera");
	    
	    if (! SaveDir.exists()){
	        if (! SaveDir.mkdirs()){
	            if(D) Log.d(TAG, "failed to create directory");
	            return null;
	        }
	    }

	    String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA).format(new Date());
	    File mediaFile;
	    
	    mediaFile = new File(SaveDir.getPath() + File.separator +timeStamp+".jpg");
	    
	    return mediaFile;
	}
	
	private Camera.PreviewCallback mFrame = new Camera.PreviewCallback(){
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			// TODO Auto-generated method stub
			try{
				Bitmap bm = decodeNV21(data, camera);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    		bm.compress(CompressFormat.JPEG, 20, baos);
	    		setSendImageBuffer(baos.toByteArray());
				baos.close();
    		}catch(IOException e){
    			e.printStackTrace();
    		}
			if(!mSendImageStart){
				mHandler.sendEmptyMessage(Manager.MSG_WRITE);
				mSendImageStart = true;
			}
		}
	};
	
	public Bitmap decodeNV21(byte[] data, Camera camera) {
		Bitmap retimage = null;

		int w = camera.getParameters().getPreviewSize().width;
		int h = camera.getParameters().getPreviewSize().height;

		// Get the YuV image
		YuvImage yuv_image = new YuvImage(data, mParams.getPreviewFormat(), w, h, null);
		// Convert Yuv to jpeg
		Rect rect = new Rect(0, 0, w, h);
		ByteArrayOutputStream out_stream = new ByteArrayOutputStream();
		// Convert Yuv to jpeg
		yuv_image.compressToJpeg(rect, 100, out_stream);
		
		BitmapFactory.Options resizeOpts = new Options();
		resizeOpts.inSampleSize = 2;
		
		retimage = BitmapFactory.decodeByteArray(out_stream.toByteArray(), 0, out_stream.size(), resizeOpts);
		retimage = Bitmap.createScaledBitmap(retimage, 640, 480, false);
		
		try {
			out_stream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(D) if(retimage == null) Log.i(TAG, "retimage is null!");
		return retimage;
	}
		
	public void setSendImageBuffer(byte[] data){
		if(mSendImagebuffer!=null){
			synchronized(mSendImagebuffer){
				mSendImagebuffer = data;
			}
		}else{
			mSendImagebuffer = data;
		}
	}
	
	public byte[] getSendImageBuffer(){
		if(mSendImagebuffer!=null){
			synchronized(mSendImagebuffer){
				return mSendImagebuffer;
			}
		}else
			return null;
	}
}
