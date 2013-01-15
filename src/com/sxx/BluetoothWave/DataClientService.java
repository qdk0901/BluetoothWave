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

import java.io.*;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Parcel;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class DataClientService {
	// Debugging
	private static final String TAG = "DataClientService";
	private static final boolean D = true;

	// Name for the SDP record when creating server socket
	private static final String NAME_SECURE = "BluetoothWaveSecure";
	private static final String NAME_INSECURE = "BluetoothWaveInsecure";

	// Unique UUID for this application
	private static final UUID MY_UUID_SECURE =
		UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
	private static final UUID MY_UUID_INSECURE =
		UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

	// Member fields
	private final BluetoothAdapter mAdapter;
	private final Handler mHandler;
	private ConnectThread mConnectThread;
	private DataRequester mDataRequester;
	private int mState;

	// Constants that indicate the current connection state
	public static final int STATE_NONE = 0;       // we're doing nothing
	public static final int STATE_LISTEN = 1;     // now listening for incoming connections
	public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
	public static final int STATE_CONNECTED = 3;  // now connected to a remote device

	/**
	 * Constructor. Prepares a new BluetoothWave session.
	 * @param context  The UI Activity Context
	 * @param handler  A Handler to send messages back to the UI Activity
	 */
	public DataClientService(Context context, Handler handler) {
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
		mHandler.obtainMessage(BluetoothWave.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
	}

	/**
	 * Return the current connection state. */
	public synchronized int getState() {
		return mState;
	}

	/**
	 * Start the chat service. Specifically start AcceptThread to begin a
	 * session in listening (server) mode. Called by the Activity onResume() */
	public synchronized void start() {
		if (D) Log.d(TAG, "start");

		// Cancel any thread attempting to make a connection
		if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

		if (mDataRequester != null) {
			mDataRequester.cancel();
			mDataRequester = null;        		
		}

		setState(STATE_LISTEN);
	}

	/**
	 * Start the ConnectThread to initiate a connection to a remote device.
	 * @param device  The BluetoothDevice to connect
	 * @param secure Socket Security type - Secure (true) , Insecure (false)
	 */
	public synchronized void connect(BluetoothDevice device, boolean secure) {
		if (D) Log.d(TAG, "connect to: " + device);

		// Cancel any thread attempting to make a connection
		if (mState == STATE_CONNECTING) {
			if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
		}

		if (mDataRequester != null) {
			mDataRequester.cancel();
			mDataRequester = null;        		
		}

		// Start the thread to connect with the given device
		mConnectThread = new ConnectThread(device, secure);
		mConnectThread.start();
		setState(STATE_CONNECTING);
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

		if (mDataRequester != null) {
			mDataRequester.cancel();
			mDataRequester = null;        		
		}

		mDataRequester = new DataRequester();
		mDataRequester.setupBluetoothSocket(socket);
		mDataRequester.start();


		// Send the name of the connected device back to the UI Activity
		Message msg = mHandler.obtainMessage(BluetoothWave.MESSAGE_DEVICE_NAME);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothWave.DEVICE_NAME, device.getName());
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

		if (mDataRequester != null) {
			mDataRequester.cancel();
			mDataRequester = null;        		
		}
		
		setState(STATE_NONE);
	}

	/**
	 * Indicate that the connection attempt failed and notify the UI Activity.
	 */
	private void connectionFailed() {
		// Send a failure message back to the Activity
		Message msg = mHandler.obtainMessage(BluetoothWave.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothWave.TOAST, "Unable to connect device");
		msg.setData(bundle);
		mHandler.sendMessage(msg);

		// Start the service over to restart listening mode
		DataClientService.this.start();
	}

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
	private void connectionLost() {
		// Send a failure message back to the Activity
		Message msg = mHandler.obtainMessage(BluetoothWave.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothWave.TOAST, "Device connection was lost");
		msg.setData(bundle);
		mHandler.sendMessage(msg);

		// Start the service over to restart listening mode
		DataClientService.this.start();
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
					tmp = device.createRfcommSocketToServiceRecord(
							MY_UUID_SECURE);
				} else {
					tmp = device.createInsecureRfcommSocketToServiceRecord(
							MY_UUID_INSECURE);
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
					Log.e(TAG, "unable to close() " + mSocketType +
							" socket during connection failure", e2);
				}
				connectionFailed();
				return;
			}

			// Reset the ConnectThread because we're done
			synchronized (DataClientService.this) {
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


	private class DataRequester extends Thread {
		private BluetoothSocket mBluetoothSocket;
		private InputStream mBlueInputStream;
		private OutputStream mBlueOutputStream;
		private DataProtocol mProtocol;
		private Boolean mShouldRunning;
		private float[] mValues = new float[4];

		private static final int DATA_POLL_RATE = 128;

		public DataRequester() {
			mBluetoothSocket = null;
			mBlueInputStream = null;
			mBlueOutputStream = null;
			mShouldRunning = false;
			mProtocol = new DataProtocol();
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
				Log.e(TAG, "temp sockets not created", e);
			}

			if (mBluetoothSocket == null)
				return false;

			mShouldRunning = true;
			return true;           	
		}

		public void run() {
			Log.i(TAG, "BEGIN DataRequester");



			// Keep listening to the InputStream while connected
			while (mShouldRunning) {
				try {

					DataProtocol.RealTimeValues result = mProtocol.new RealTimeValues();
					mProtocol.requestGetCurrent(mBlueInputStream, mBlueOutputStream, result);

					mValues[0] = result.mFullScale;
					mValues[1] = result.mRaw;
					mValues[2] = result.mBreath;
					mValues[3] = result.mHeartBeat;
								
					// Send the obtained value to the UI Activity
					mHandler.obtainMessage(BluetoothWave.MESSAGE_UPDATE_CURRENT, mValues)
						.sendToTarget();
				} catch (Exception e) {
					Log.e(TAG, "disconnected", e);
					connectionLost();
					// Start the service over to restart listening mode
					DataClientService.this.start();
				}

				try {
					Thread.sleep(1000/DATA_POLL_RATE);
				} catch (InterruptedException er) {
				}
			}
		}

		public void cancel() {
			mShouldRunning = false;
			try {
				if(mBluetoothSocket != null)
					mBluetoothSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "###################", e);
			}
		}

	}
}
