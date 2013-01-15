package com.sxx.BluetoothWave;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import java.util.UUID;
import android.os.Binder;  
import android.os.IBinder;

import java.io.*;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Parcel;

public class DataServerService  extends Service 
{
	private static final boolean mIsWorkAsGenerator = false;
	private static final String DATA_SERVER_SOCKET_NAME = "socket-data-server";
	private static final String TAG = "DataServer";
	private static final boolean D = true;
	
	// Name for the SDP record when creating server socket
	private static final String NAME_SECURE = "BluetoothWaveSecure";
	private static final String NAME_INSECURE = "BluetoothWaveInsecure";

	// Unique UUID for this application
	private static final UUID UUID_SECURE =
		UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
	private static final UUID UUID_INSECURE =
		UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
		
	private BluetoothAdapter mAdapter;
	private BluetoothReceiver mBluetoothReceiver;
	private Handler mHandler;
	private DataServerBinder mDataServerBinder;
	private ServerThread mServerThread;
	
	@Override
    public void onCreate() {
    	if(shouldRunAsServer()) {
	    	mDataServerBinder = new DataServerBinder();
	    	mBluetoothReceiver = new BluetoothReceiver();
	    	mAdapter = BluetoothAdapter.getDefaultAdapter();
	    	
	    	if (!mIsWorkAsGenerator) {
		    	IntentFilter bluetoothFilter = new IntentFilter();
		    	bluetoothFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
		    	bluetoothFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		    	bluetoothFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		    	
		    	registerReceiver(mBluetoothReceiver, bluetoothFilter);
		    	
		        mAdapter.setName("Health Device");
		        if (!mAdapter.isEnabled()) {
		        	mAdapter.enable();
		        }
		        if (mAdapter.getState() == BluetoothAdapter.STATE_ON) {
		        	mAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, 0);
		        	mAdapter.setDiscoverableTimeout(0);
		        }
	    	}
	        
	        mServerThread = new ServerThread();
	        mServerThread.start();   
    	}     
    }
    
    @Override
    public void onStart(Intent intent, int startId) {
    	
	}
    
    @Override
    public void onDestroy() {
        
    }
    
    @Override
    public IBinder onBind(Intent intent) {
		return mDataServerBinder;
    }
    
    public static boolean shouldRunAsServer()
    {
    	/*
    	if (mIsWorkAsGenerator)
    		return true;
    	boolean should = false;
    	LocalSocket s;
		LocalSocketAddress l;
		try {
			s = new LocalSocket();	
			l = new LocalSocketAddress(DATA_SERVER_SOCKET_NAME,
						LocalSocketAddress.Namespace.ABSTRACT);
			s.connect(l);
			s.close();
			should = true;
		} catch (IOException ex) {
		}
	*/
	return true;
    }
    
    public class DataServerBinder extends Binder {  
        DataServerService getService() {    
            return DataServerService.this;  
        }     
    }
    
	public void setHandler(Handler h)
	{
		mHandler = h;
	}
    	
	public class BluetoothReceiver extends BroadcastReceiver{
		private String mDefaultCode="0000";
		@Override
		public void onReceive(Context context, Intent intent) {
			
			if (intent.getAction().equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
				BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				int type = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);
				byte[] pinBytes;
				
				Log.d(TAG, "------------"+intent);
				switch(type)
				{
					case BluetoothDevice.PAIRING_VARIANT_PIN:
						pinBytes = BluetoothDevice.convertPinToBytes(mDefaultCode);
						bluetoothDevice.setPin(pinBytes);
					break;
            		case BluetoothDevice.PAIRING_VARIANT_PASSKEY:
            			int passkey = Integer.parseInt(mDefaultCode);
            			bluetoothDevice.setPasskey(passkey);
            		break;
            		case BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION:
            		case BluetoothDevice.PAIRING_VARIANT_CONSENT:
            		{
            			bluetoothDevice.setPairingConfirmation(true);
		                  pinBytes = BluetoothDevice.convertPinToBytes(mDefaultCode);
            			bluetoothDevice.setPin(pinBytes);
            		}
            		break;
            		case BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY:
            		case BluetoothDevice.PAIRING_VARIANT_DISPLAY_PIN:
            			int pairingKey =
                    	intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, BluetoothDevice.ERROR);
		                if (type == BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY) {
		                	bluetoothDevice.setPairingConfirmation(true);
		                } else {
		                    String pinCode = String.format("%04d", pairingKey);
		                    pinBytes = BluetoothDevice.convertPinToBytes(pinCode);
            			 bluetoothDevice.setPin(pinBytes);
		                }
            		break;
            		case BluetoothDevice.PAIRING_VARIANT_OOB_CONSENT:
                		bluetoothDevice.setRemoteOutOfBandData();
                	break;
				}
				     
			} else if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
				
				if (state == BluetoothAdapter.STATE_ON) {
					mAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, 0);
					mAdapter.setDiscoverableTimeout(0);
				}
			} else if (intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
					mServerThread.cancel();
					mServerThread.start();
				}
			}
		}
	}
	
	private class ServerThread extends Thread {
		// The local server socket
		private final BluetoothServerSocket mmServerSocket;
		private boolean mIsRunning = false;
		private DataBridger mDataBridger;
		
		public ServerThread() {
			mDataBridger = null;
			
			BluetoothServerSocket tmp = null;
			// Create a new listening server socket
			try {
				tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(
						NAME_INSECURE, UUID_INSECURE);
			} catch (IOException e) {
				Log.e(TAG, "Socket listen() failed", e);
			}
			
			mmServerSocket = tmp;
			
			if (mmServerSocket != null)
				mIsRunning = true;
		}

		public void run() {
			if (D) Log.d(TAG, "BEGIN AcceptThread");
			
			setName("AcceptThread");

			BluetoothSocket socket = null;

			// Listen to the server socket if we're not connected
			while (mIsRunning) {
				try {
					Log.d(TAG, "Listening for next client");
					
					socket = mmServerSocket.accept();
					
					if (mDataBridger != null) {
						mDataBridger.cancel();	
					}
					
					mDataBridger = new DataBridger();
					
					if (mDataBridger.setup() && mDataBridger.setupBluetoothSocket(socket)) {
						mDataBridger.start();
						Log.d(TAG, "++++++ Data Server Begin Running ++++");	
					}
				} catch (IOException e) {
					
					Log.e(TAG, "accept() failed", e);
					break;
				}
			}
		}

		public void cancel() {
			mIsRunning = false;
			try {
				mmServerSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "Server close failed", e);
			}
		}
	}
	
	private class DataBridger extends Thread {
		private LocalSocket mLocalSocket;
		private LocalSocketAddress mSocketAddress;
		private BluetoothSocket mBluetoothSocket;
		private DataProtocol mProtocol;
		private InputStream mLocalInputStream;
		private OutputStream mLocalOutputStream;
		private InputStream mBlueInputStream;
		private OutputStream mBlueOutputStream;
		private Boolean mShouldRunning;
		private int mPhase = 0;
		private float[] mValues = new float[4];

		public DataBridger() {
			mLocalSocket = null;
			mBluetoothSocket = null;
			mLocalInputStream = null;
			mLocalOutputStream = null;
			mBlueInputStream = null;
			mBlueOutputStream = null;
			mShouldRunning = false;
			mProtocol = new DataProtocol();
		}

		public Boolean setup() {
			//if work as wave data generator, do not need the local data server
			if (mIsWorkAsGenerator)
				return true;
			
			try {
				if (mLocalSocket == null) {
					mLocalSocket = new LocalSocket();	
				}

				if (mSocketAddress == null) {
					mSocketAddress = new LocalSocketAddress(DATA_SERVER_SOCKET_NAME,
							LocalSocketAddress.Namespace.ABSTRACT);
				}
				mLocalSocket.connect(mSocketAddress);

			} catch (IOException ex) {
				Log.d(TAG, "Create local socket failed", ex);
				mLocalSocket = null;
			}

			if (mLocalSocket == null)
				return false;

			try {
				mLocalInputStream = mLocalSocket.getInputStream();
				mLocalOutputStream = mLocalSocket.getOutputStream();
			} catch (IOException e) {
				mLocalSocket = null;
				mLocalInputStream = null;
				mLocalOutputStream = null;
				Log.e(TAG, "temp sockets not created", e);
			}

			if (mLocalSocket == null)
				return false;

			return true;

		}

		public Boolean setupBluetoothSocket(BluetoothSocket socket)
		{
			mBluetoothSocket = socket;
			try {
				mBlueInputStream = socket.getInputStream();
				mBlueOutputStream = socket.getOutputStream();
			} catch (IOException e) {
				mBluetoothSocket = null;
				mBlueInputStream = null;
				mBlueOutputStream = null;
				Log.e(TAG, "Sockets not created", e);
			}

			if (mBluetoothSocket == null)
				return false;
				
			mShouldRunning = true;
			return true;           

		}

		public void run() {
			Log.i(TAG, "BEGIN DataBridger");


			while(mShouldRunning){
				try{
					Parcel request = Parcel.obtain();
					Parcel response = Parcel.obtain();
					
					mProtocol.recvRequest(mBlueInputStream, request);
					
					int r = request.readInt();
					
					if (r == DataProtocol.DS_REQUEST_GET_CURRENT) {
						
						if (mIsWorkAsGenerator) {
							float value;
							float fs = 5.0f;
							
							response.writeInt(DataProtocol.DS_REQUEST_GET_CURRENT);
							
							response.writeFloat(fs);
							mValues[0] = fs;
							
							value = (float)(fs * Math.sin(mPhase * 2 * 3.14 / 240));
							response.writeFloat(value);
							mValues[1] = value;
							
							value = (float)(fs * Math.sin(0.25 * 3.14 + mPhase * 2 * 3.14 / 240));
							response.writeFloat(value);
							mValues[2] = value;
							
							value = (float)(fs * Math.sin(0.75 * 3.14 + mPhase * 2 * 3.14 / 240));
							response.writeFloat(value);
							mValues[3] = value;
							
							mPhase++;
							
						} else {
							request.setDataPosition(0);
							mProtocol.sendRequest(mLocalInputStream, mLocalOutputStream, request, response);
							
							response.readInt();
							for(int i = 0; i < 4; i++)
								mValues[i] = response.readFloat();
							
							response.setDataPosition(0);
						}
						
						mProtocol.sendResponse(mBlueOutputStream, response);

						// also draw on the server size
						//mHandler.obtainMessage(BluetoothWave.MESSAGE_UPDATE_CURRENT, mValues)
						//	.sendToTarget();

						request.recycle();
						response.recycle();
					} else {
						//otherwise direct the data request to the data server
						request.setDataPosition(0);
						mProtocol.sendRequest(mLocalInputStream, mLocalOutputStream, request, response);
						mProtocol.sendResponse(mBlueOutputStream, response);
					}
				} catch (Exception e) {
					cancel();
					Log.e(TAG, "Connecion Lost");	
					break;
				}
			}
		}

		public void cancel() {
			mShouldRunning = false;
			try {
				if(mBluetoothSocket != null) {
					mBluetoothSocket.close();
					mBluetoothSocket = null;
				}
				if(mLocalSocket != null) {
					mLocalSocket.close();
					mLocalSocket = null;
				}
			} catch (IOException e) {
				Log.e(TAG, "Stop data bridger failed", e);
			}
		}
	}	
}
