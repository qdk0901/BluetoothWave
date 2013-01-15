package com.sxx.BluetoothWave;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class BootCompletedReceiver extends BroadcastReceiver {
  	@Override 
  	public void onReceive(Context context, Intent intent) {
	    if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
	    	
		}
	}
}