/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pbh.RemoteCam.BT;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.pbh.RemoteCam.util.Manager;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothService {
    // Debugging
    private static final String TAG = "BluetoothService";
    private static final boolean D = false;

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothSecure";
    //private static final String NAME_INSECURE = "BluetoothInsecure";

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
        UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    //private static final UUID MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    //private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private WriteThread mWriteThread;
    private ReadThread mReadThread;
    private int mState;
    private int mMode;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    
    public static final int CLIENT = 10;
    public static final int SERVER = 20;
    
    /**
     * Constructor. Prepares a new MainActivity session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Manager.MSG_STATE_CHANGE, state, -1, null).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }
    
    private synchronized void setMode(int mode) {
        if (D) Log.d(TAG, "setMode() " + mode);
        mMode = mode;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Manager.MSG_STATE_CHANGE, mMode, -1, null).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getMode() {
        return mMode;
    }


    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mWriteThread != null) {mWriteThread.cancel(); mWriteThread = null;}
        if (mReadThread != null) {mReadThread.cancel(); mReadThread = null;}

        setState(STATE_LISTEN);
        setMode(SERVER);
        
        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }
        /*
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread(false);
            mInsecureAcceptThread.start();
        }
        */
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     * @param mode Client(10)? Server(20)? 
     */
    public synchronized void connect(BluetoothDevice device, boolean secure, int mode) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mWriteThread != null) {mWriteThread.cancel(); mWriteThread = null;}
        if (mReadThread != null) {mReadThread.cancel(); mReadThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        setState(STATE_CONNECTING);
        setMode(mode);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        if (D) Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mWriteThread != null) {mWriteThread.cancel(); mWriteThread = null;}
        if (mReadThread != null) {mReadThread.cancel(); mReadThread = null;}

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        /*
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        */
        
        // Start the thread to manage the connection and perform transmissions
        mWriteThread = new WriteThread(socket, socketType);
        mReadThread = new ReadThread(socket, socketType);
        mReadThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Manager.MSG_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Manager.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        /*
        if (mWriteThread != null) {
        	mWriteThread.cancel();
        	mWriteThread = null;
        }
        */
        if (mReadThread != null) {
        	mReadThread.cancel();
        	mReadThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        /*
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        */
        setState(STATE_NONE);
    }

    /**
     *  file 외 전송
     * @param what  String 목적 기입 (header) i(info), e(event)
     * @param str
     */
    public void write(String what, String str) {
        // Create temporary object
    	WriteThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mWriteThread;
        }
        int strsize = str.getBytes().length;
        String len = String.valueOf(strsize);
    	// change "1234" to "0000001234", to make sure 10 size.
    	String header = what + "0000000000".substring(0, 10-len.length()) + len;
        // send header
        r.write(header.getBytes(),0,header.getBytes().length);
        // send body
        r.write(str.getBytes());
        Log.i(TAG, "outcomming header : " + header);
		Log.i(TAG, "outcomming packet : " + header.getBytes().length);
    }
    
    /**
     *  file 전송
     * @param what  String 목적 기입 (header) f(file)
     * @param data
     */
    public void write(String what, byte[] data) {
    	// Create temporary object
        WriteThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mWriteThread;
        }
        if(data != null){
        	int totalSize = 0;
	    	byte buffer[] = new byte[1024];
	    	String flen = String.valueOf(data.length);
	    	// change "1234" to "0000001234", to make sure 10 size.
	    	String header = what+"0000000000".substring(0, 10-flen.length()) + flen;
	        try {
	        	ByteArrayInputStream bais = new ByteArrayInputStream(data);
	        	// send header
	        	r.write(header.getBytes());

	        	// send body
				while (bais.available() > 0) {
					int rsize = bais.read(buffer);
					r.write(buffer, 0, rsize);
					totalSize += rsize;
				}
				Log.i(TAG, "outcomming header : " + header);
				Log.i(TAG, "outcomming packet : " + totalSize);
				bais.close();
	        } catch (IOException e) {
	            Log.e(TAG, "Exception during write", e);
	        }
        }else{
        	if(D) Log.e(TAG,"data is null!!");
        }
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Manager.MSG_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Manager.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Manager.MSG_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Manager.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothService.this.start();
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure":"Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, MY_UUID_SECURE);
                } else {
                    //tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
                	Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed");
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (D) Log.d(TAG, "Socket Type: " + mSocketType + "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothService.this) {
                        switch (mState) {
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                            connected(socket, socket.getRemoteDevice(), mSocketType);
                            break;
                        case STATE_NONE:
                        case STATE_CONNECTED:
                            // Either not ready or already connected. Terminate new socket.
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Could not close unwanted socket", e);
                            }
                            break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

        }

        public void cancel() {
            if (D) Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
                } else {
                    //tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
                	Log.e(TAG, "Socket Type: " + mSocketType + "create() failed");
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType + " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    
    private class ReadThread extends Thread {
    	private static final String TAG = "ReadThread";
    	
    	private final BluetoothSocket mmSocket;
    	private final BufferedInputStream mmInStream;
    	private boolean stop = false;
    	
    	public ReadThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ReadThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }
            mmInStream = new BufferedInputStream(tmpIn);
        }
    	
    	public void run(){
    		Log.i(TAG, "BEGIN ReadThread");
    		while (!stop) {
            	byte[] buffer= new byte[1024];
                try {
                	int bodySize = 0;
                    int readSize = 0;
                	int rsize = 0;
                	String inc_what = "";
                	String inc_size = "";
                	
            		// read header(11 bytes)
                	rsize = mmInStream.read(buffer,0,11);
                	if(rsize != -1){
                        // read waht? i, f, e...
                        inc_what = new String(buffer, 0, 1);
                        
                    	// body size
                        inc_size = new String(buffer, 1, 11);
                        bodySize = Integer.parseInt(inc_size.substring(0,inc_size.length()-1));
                        Log.i(TAG,"incomming packet what: " + inc_what + ", size: " + bodySize);
                	}
                    // what 에 따라 body 처리
                    //------------------------------------------------------------------------------------
                    // file 일 때(image)
                    if(inc_what.equals(Manager.STR_FILE)){
            		    try{
            			    //FileOutputStream fos = new FileOutputStream(tempFile);
            		    	ByteArrayOutputStream baos = new ByteArrayOutputStream(bodySize);
            			    // read body
            			    while(readSize < bodySize){
            			    	if(1024 > (bodySize - readSize)){
            			    		rsize = mmInStream.read(buffer,0,bodySize - readSize);
            			    	}else{
            			    		rsize = mmInStream.read(buffer);
            			    	}
    			    			baos.write(buffer, 0, rsize);
    			    			readSize += rsize;
            			    }
            			    
            			    Message msg = mHandler.obtainMessage(Manager.MSG_READ,Manager.IMAGE,-1,null);
                            Bundle bundle = new Bundle();
                            bundle.putByteArray(Manager.STR_FILE, baos.toByteArray());
                            msg.setData(bundle);
                            mHandler.sendMessage(msg);
            			    
            	            baos.close();
            		    } catch (IOException e) {
                            Log.e(TAG, "write error : ", e);
                        }
                    }
                    
                    //------------------------------------------------------------------------------------
                    // event 일 때 (takepic)
                    if(inc_what.equals(Manager.STR_EVENT)){
                        Message msg = mHandler.obtainMessage(Manager.MSG_READ, Manager.TAKEPIC, -1);
                        mHandler.sendMessage(msg);
                    }
                    
                    //------------------------------------------------------------------------------------
                    // camera info 등
                    if(inc_what.equals(Manager.STR_INFO)){	
                    	StringBuilder body = new StringBuilder();
                    	while(readSize < bodySize){
                    		rsize = mmInStream.read(buffer,0,buffer.length);
                    		body.append(new String(buffer,0,buffer.length));
                    		readSize+=rsize;
                    	}
                    	String info = body.toString();
                    	Message msg = mHandler.obtainMessage(Manager.MSG_READ,Manager.INFO,-1,(Object)info);
                        mHandler.sendMessage(msg);
                    }
                    
                    //------------------------------------------------------------------------------------
                    // etc...
	            } catch (IOException e) {
	                Log.e(TAG, "disconnected", e);
	                connectionLost();
	                cancel();
	                // Start the service over to restart listening mode
	                BluetoothService.this.start();
	                break;
	            }
            } // while()
    		
    		try {
				mmInStream.close();
				cancel();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	} // run()
    	
        public void cancel() {
            try {
            	mmInStream.close();
                mmSocket.close();
                stop=true;
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
    

    private class WriteThread {
    	private static final String TAG = "WriteThread";
    	
        private final BluetoothSocket mmSocket;
        private final OutputStream mmOutStream;

        public WriteThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create WriteThread: " + socketType);
            mmSocket = socket;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }
            mmOutStream = tmpOut;
        }

        /**
         * connected OutStream 으로 write 동작 수행
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }
        
        public void write(byte[] buffer, int start, int end) {
            try {
                mmOutStream.write(buffer,start,end);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }
        
        public void cancel() {
            try {
            	mmOutStream.flush();
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
