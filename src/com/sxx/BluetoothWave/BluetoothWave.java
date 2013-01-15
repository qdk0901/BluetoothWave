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

package com.sxx.BluetoothWave;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import android.content.Intent;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.ComponentName; 
import android.os.Binder;  
import android.os.IBinder;


/**
 * This is the main Activity that displays the current chat session.
 */
public class BluetoothWave extends Activity {
	// Debugging
	private static final String TAG = "BluetoothWave";
	private static final boolean D = true;

	// Message types sent from the DataClientService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_DEVICE_NAME = 2;
	public static final int MESSAGE_TOAST = 3;
	public static final int MESSAGE_UPDATE_CURRENT = 4;

	// Key names received from the DataClientService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
	private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
	private static final int REQUEST_ENABLE_BT = 3;

	// Layout Views
	private ListView mConversationView;

	// Name of the connected device
	private String mConnectedDeviceName = null;
	// Array adapter for the conversation thread
	private ArrayAdapter<String> mConversationArrayAdapter;

	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the chat services
	private DataClientService mDataClientService = null;

	private WaveViewer mWaveViwer = null;
	private ServiceConnection mSC = null;
	private boolean mIsServer = false;

	@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			if(D) Log.e(TAG, "+++ ON CREATE +++");

			// Set up the window layout
			setContentView(R.layout.main);

			mWaveViwer = (WaveViewer) findViewById(R.id.wave_viewer);
		
			if (DataServerService.shouldRunAsServer()) {
				startDataServerService();
				mIsServer = true;
				Log.d(TAG, "+++++++++++++++++++  run as server ++++++++++");
			} else {
				startDataClientService();
				mIsServer = false;
				Log.d(TAG, "+++++++++++++++++++  run as client ++++++++++");
			}

			// Get local Bluetooth adapter
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

			// If the adapter is null, then Bluetooth is not supported
			if (mBluetoothAdapter == null) {
				Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
				finish();
				return;
			}
		}

	@Override
		public void onStart() {
			super.onStart();
			if(D) Log.e(TAG, "++ ON START ++");

			Intent in = new Intent(BluetoothWave.this, DataServerService.class);
			bindService(in, mSC, Context.BIND_AUTO_CREATE);
			
			/*
			// If BT is not on, request that it be enabled.
			// setupWave() will then be called during onActivityResult
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
				// Otherwise, setup the chat session
			} else {
				if (mWaveService == null) setupWave();
			}
			*/
		}
		
	private void startDataServerService() {
		mSC = new ServiceConnection() {
			@Override 
			public void onServiceConnected(ComponentName name, IBinder service) {
				DataServerService dss = ((DataServerService.DataServerBinder)service).getService();
				dss.setHandler(mHandler);
			}
			@Override 
			public void onServiceDisconnected(ComponentName name) {
				
			}
		};
			
	    Intent in = new Intent(BluetoothWave.this, DataServerService.class);
        startService(in);
    }

	@Override
		public synchronized void onResume() {
			super.onResume();
			if(D) Log.e(TAG, "+ ON RESUME +");

			if (mDataClientService != null) {
				// Only if the state is STATE_NONE, do we know that we haven't started already
				if (mDataClientService.getState() == DataClientService.STATE_NONE) {
					// Start the Bluetooth chat services
					mDataClientService.start();
				}
			}
		}

	private void startDataClientService() {
		Log.d(TAG, "setupClientService()");

		// Initialize the array adapter for the conversation thread
		mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
		mConversationView = (ListView) findViewById(R.id.in);
		mConversationView.setAdapter(mConversationArrayAdapter);

		// Initialize the DataClientService to perform bluetooth connections
		mDataClientService = new DataClientService(this, mHandler);
	}

	@Override
		public synchronized void onPause() {
			super.onPause();
			if(D) Log.e(TAG, "- ON PAUSE -");
		}

	@Override
		public void onStop() {
			super.onStop();
			unbindService(mSC);
			if(D) Log.e(TAG, "-- ON STOP --");
		}

	@Override
		public void onDestroy() {
			super.onDestroy();
			// Stop the Bluetooth chat services
			if (mDataClientService != null) mDataClientService.stop();
			if(D) Log.e(TAG, "--- ON DESTROY ---");
		}

	private void ensureDiscoverable() {
		if(D) Log.d(TAG, "ensure discoverable");
		if (mBluetoothAdapter.getScanMode() !=
				BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivity(discoverableIntent);
		}
	}

	private final void setStatus(int resId) {
		final ActionBar actionBar = getActionBar();
		actionBar.setSubtitle(resId);
	}

	private final void setStatus(CharSequence subTitle) {
		final ActionBar actionBar = getActionBar();
		actionBar.setSubtitle(subTitle);
	}

	// The Handler that gets information back from the DataClientService
	private final Handler mHandler = new Handler() {
		@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
					case MESSAGE_STATE_CHANGE:
						if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
						switch (msg.arg1) {
							case DataClientService.STATE_CONNECTED:
								setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
								mConversationArrayAdapter.clear();
								break;
							case DataClientService.STATE_CONNECTING:
								setStatus(R.string.title_connecting);
								break;
							case DataClientService.STATE_LISTEN:
							case DataClientService.STATE_NONE:
								setStatus(R.string.title_not_connected);
								break;
						}
						break;
					case MESSAGE_DEVICE_NAME:
						// save the connected device's name
						mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
						Toast.makeText(getApplicationContext(), "Connected to "
								+ mConnectedDeviceName, Toast.LENGTH_SHORT).show();
						break;
					case MESSAGE_TOAST:
						Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
								Toast.LENGTH_SHORT).show();
						break;
					case MESSAGE_UPDATE_CURRENT:
						float[] values = (float[])msg.obj;
						mWaveViwer.pushOneValue(values);
						break;
				}
			}
	};

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (mIsServer)
			return;
		if(D) Log.d(TAG, "onActivityResult " + resultCode);
		switch (requestCode) {
			case REQUEST_CONNECT_DEVICE_SECURE:
				// When DeviceListActivity returns with a device to connect
				if (resultCode == Activity.RESULT_OK) {
					connectDevice(data, true);
				}
				break;
			case REQUEST_CONNECT_DEVICE_INSECURE:
				// When DeviceListActivity returns with a device to connect
				if (resultCode == Activity.RESULT_OK) {
					connectDevice(data, false);
				}
				break;
			case REQUEST_ENABLE_BT:
				// When the request to enable Bluetooth returns
				if (resultCode == Activity.RESULT_OK) {
					// Bluetooth is now enabled, so set up a chat session
					startDataClientService();
				} else {
					// User did not enable Bluetooth or an error occurred
					Log.d(TAG, "BT not enabled");
					Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
					finish();
				}
		}
	}

	private void connectDevice(Intent data, boolean secure) {
		// Get the device MAC address
		String address = data.getExtras()
			.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
		// Get the BluetoothDevice object
		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		// Attempt to connect to the device
		mDataClientService.connect(device, secure);
	}

	@Override
		public boolean onCreateOptionsMenu(Menu menu) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.option_menu, menu);
			return true;
		}

	@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			if (mIsServer)
				return true;
			Intent serverIntent = null;
			switch (item.getItemId()) {
				case R.id.secure_connect_scan:
					// Launch the DeviceListActivity to see devices and do scan
					serverIntent = new Intent(this, DeviceListActivity.class);
					startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
					return true;
				case R.id.insecure_connect_scan:
					// Launch the DeviceListActivity to see devices and do scan
					serverIntent = new Intent(this, DeviceListActivity.class);
					startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
					return true;
				case R.id.discoverable:
					// Ensure this device is discoverable by others
					ensureDiscoverable();
					return true;
			}
			return false;
		}




}
