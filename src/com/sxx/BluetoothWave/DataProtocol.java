package com.sxx.BluetoothWave;

import java.io.*;
import android.os.Parcel;
import android.util.Log;
import android.os.Bundle;

//Use Pacel for all data exchange 


class DataProtocol
{
	private static final String TAG = "DataProtocol";
	private static final int MAX_DATA_SIZE = 4096;
	
	public static final int DS_REQUEST_FAILED	= 0xdeaddead;
	public static final int DS_REQUEST_OK = 0x55AA55AA;
	public static final int DS_REQUEST_GET_CURRENT = 0x95270000;
	public static final int DS_REQUEST_GET_SETTINGS = 0x95270001;
	public static final int DS_REQUEST_SET_SETTINGS = 0x95270002;
	public static final int DS_REQUEST_GET_STATISTICS = 0x95270003;
	public static final int DS_REQUEST_GET_SLEEP_POINTS_BY_MONTH = 0x95270004;
	public static final int DS_REQUEST_SET_WIFI = 0x95270005;
	
	private byte[] mDataLength;
	private byte[] mBuffer = new byte[MAX_DATA_SIZE];

	public class RealTimeValues
	{
		public byte	mRaw;
		public byte mBreath;
		public byte mHeartBeat;
		public byte mBreathRate;
		public byte mHeartBeatRate;
		public RealTimeValues()
		{
		}
	};
	public class SystemSettings
	{
		public int mBreathHighThrehold;
		public int mBreathHighThreholdWarning;
		public int mBreathLowThrehold;
		public int mBreathLowThreholdWarning;
		public int mBreathStopCondition;
		public int mBreathStopWarning;
		public int mHeartBeatHighThrehold;
		public int mHeartBeatHighThreholdWarning;
		public int mHeartBeatLowThrehold;
		public int mHeartBeatLowThreholdWarning;
		public int mHeartBeatStopCondition;
		public int mHeartBeatStopWarning;
		public SystemSettings()
		{
		}
	};
	
	public class SleepDepth
	{
		public int mStartTime;
		public int mInterval;
		public int mCount;
		public int[] mValues;
		public SleepDepth()
		{
			
		}
	};
	
	public class Statistics
	{
		public static final int SLEEP_DEPTH_DISTRIBUTE_NUM = 4;
		public int mSleepPoints;
		public float[] mSleepDepthDistribute;
		public int mSleepStart;
		public int mSleepEnd;
		public SleepDepth mSleepDepth;
		public Statistics()
		{
		}
	};
	
	public class SleepPointsByMonth
	{
		public static final int DATA_COUNT = 31;
		public int[] mValues;	
	};
	
	
	public DataProtocol()
	{
		mBuffer = new byte[MAX_DATA_SIZE];
		mDataLength = new byte[4];
	}
	public int readOneDataFrame(InputStream is, byte[] buffer)
		throws IOException
	{
		int countRead;
		int offset;
		int remaining;
		int dataLength;
		offset = 0;
		remaining = 4;
		do {
			countRead = is.read(buffer, offset, remaining);

			if (countRead < 0 ) {
				Log.e(TAG, "Hit EOS reading message length");
				return -1;
			}

			offset += countRead;
			remaining -= countRead;
		} while (remaining > 0);

		dataLength = ((buffer[0] & 0xff) << 24)
			| ((buffer[1] & 0xff) << 16)
			| ((buffer[2] & 0xff) << 8)
			| (buffer[3] & 0xff);

		offset = 0;
		remaining = dataLength;

		do {
			countRead = is.read(buffer, offset, remaining);

			if (countRead < 0 ) {
				Log.e(TAG, "Hit EOS reading data.  dataLength=" + dataLength
						+ " remaining=" + remaining);
				return -1;
			}

			offset += countRead;
			remaining -= countRead;
		} while (remaining > 0); 

		return dataLength; 		
	}

	public int writeOneDataFrame(OutputStream os, byte[] buffer)
		throws IOException
	{
		mDataLength[0] = mDataLength[1] = 0;
		mDataLength[2] = (byte)((buffer.length >> 8) & 0xff);
		mDataLength[3] = (byte)((buffer.length) & 0xff);

		os.write(mDataLength);
		os.write(buffer);
		return buffer.length;
	}

	public boolean sendRequest(InputStream is, OutputStream os, Parcel request, Parcel response)
		throws IOException
	{
		byte[] buffer = request.marshall();
		
		writeOneDataFrame(os, buffer);

		int length = readOneDataFrame(is, mBuffer);

		if (length < 0) {
			Log.e(TAG, "End-of-stream reached");
			return false;
		}

		response.unmarshall(mBuffer, 0, length);
		response.setDataPosition(0);

		return true;
	}

	public  boolean recvRequest(InputStream is, Parcel request)
		throws IOException
	{
		int length = readOneDataFrame(is, mBuffer);

		if (length > 0) {
			request.unmarshall(mBuffer, 0, length);
			request.setDataPosition(0);
			return true;
		} else {
			return false;	
		}
	}

	public boolean sendResponse(OutputStream os, Parcel response)
		throws IOException
	{
		writeOneDataFrame(os, response.marshall());
		return true;
	}
	
	public boolean requestGetCurrent(InputStream is, OutputStream os, RealTimeValues result)
		throws IOException
	{
		boolean ret = false;
		Parcel request = Parcel.obtain();
		Parcel response = Parcel.obtain();
		
		request.writeInt(DS_REQUEST_GET_CURRENT);
		sendRequest(is, os, request, response);

		if (response.readInt() != DS_REQUEST_GET_CURRENT) {
			ret = false;
		} else {
			int pos = response.dataPosition();
			result.mRaw = response.readByte();
			response.setDataPosition(pos++);
			result.mBreath = response.readByte();
			response.setDataPosition(pos++);
			result.mHeartBeat = response.readByte();
			response.setDataPosition(pos++);
			result.mBreathRate = response.readByte();
			response.setDataPosition(pos++);
			result.mHeartBeatRate = response.readByte();
			ret = true;
		}
		
		request.recycle();
		response.recycle();
		return ret;			
	}
	
	public boolean requestGetSettings(InputStream is, OutputStream os, SystemSettings result)
		throws IOException
	{
		boolean ret = false;
		Parcel request = Parcel.obtain();
		Parcel response = Parcel.obtain();
		
		request.writeInt(DS_REQUEST_GET_SETTINGS);
		sendRequest(is, os, request, response);
		if (response.readInt() != DS_REQUEST_GET_SETTINGS) {
			ret = false;
		} else {
			result.mBreathHighThrehold = response.readInt();
			result.mBreathHighThreholdWarning = response.readInt();
			result.mBreathLowThrehold = response.readInt();
			result.mBreathLowThreholdWarning = response.readInt();
			result.mBreathStopCondition = response.readInt();
			result.mBreathStopWarning = response.readInt();
			result.mHeartBeatHighThrehold = response.readInt();
			result.mHeartBeatHighThreholdWarning = response.readInt();
			result.mHeartBeatLowThrehold = response.readInt();
			result.mHeartBeatLowThreholdWarning = response.readInt();
			result.mHeartBeatStopCondition = response.readInt();
			result.mHeartBeatStopWarning = response.readInt();
			ret = true;
		}
		
		request.recycle();
		response.recycle();
		return ret;
	}
	
	public boolean requestSetSettings(InputStream is, OutputStream os, SystemSettings settings)
		throws IOException
	{
		boolean ret = false;
		Parcel request = Parcel.obtain();
		Parcel response = Parcel.obtain();
		
		request.writeInt(DS_REQUEST_SET_SETTINGS);
		request.writeInt(settings.mBreathHighThrehold);
		request.writeInt(settings.mBreathHighThreholdWarning);
		request.writeInt(settings.mBreathLowThrehold);
		request.writeInt(settings.mBreathLowThreholdWarning);
		request.writeInt(settings.mBreathStopCondition);
		request.writeInt(settings.mBreathStopWarning);
		request.writeInt(settings.mHeartBeatHighThrehold);
		request.writeInt(settings.mHeartBeatHighThreholdWarning);
		request.writeInt(settings.mHeartBeatLowThrehold);
		request.writeInt(settings.mHeartBeatLowThreholdWarning);
		request.writeInt(settings.mHeartBeatStopCondition);
		request.writeInt(settings.mHeartBeatStopWarning);

		sendRequest(is, os, request, response);
		
		int r = response.readInt();
		int status = response.readInt();
		if (r == DS_REQUEST_SET_SETTINGS && status == DS_REQUEST_OK) {
			ret = true;	
		} else {
			ret = false;	
		}
		
		request.recycle();
		response.recycle();
		
		return ret;			
	}
	
	public boolean requestGetStatistics(InputStream is, OutputStream os, int timeStamp, Statistics result)
		throws IOException
	{
		boolean ret = false;
		Parcel request = Parcel.obtain();
		Parcel response = Parcel.obtain();
		
		request.writeInt(DS_REQUEST_GET_STATISTICS);
		request.writeInt(timeStamp);
		sendRequest(is, os, request, response);
		
		if (response.readInt() != DS_REQUEST_GET_STATISTICS) {
			ret = false;
		} else {
			result.mSleepPoints = response.readInt();
			int count;
			result.mSleepDepthDistribute = new float[Statistics.SLEEP_DEPTH_DISTRIBUTE_NUM];
			for (int i = 0; i < Statistics.SLEEP_DEPTH_DISTRIBUTE_NUM; i++)
				result.mSleepDepthDistribute[i] = response.readFloat();
			
			result.mSleepStart = response.readInt();
			result.mSleepEnd = response.readInt();
			result.mSleepDepth.mStartTime = response.readInt();
			result.mSleepDepth.mInterval = response.readInt();
			count = result.mSleepDepth.mCount = response.readInt();
			result.mSleepDepth.mValues = new int[count];
			for (int i = 0; i < count; i++)
				result.mSleepDepth.mValues[i] = response.readInt();
			ret = true;
		}
		
		request.recycle();
		response.recycle();
		
		return ret;
	}
	
	public boolean requestSleepPointsByMonth(InputStream is, OutputStream os, int timeStamp, SleepPointsByMonth result)
		 throws IOException
	{
		boolean ret = false;
		Parcel request = Parcel.obtain();
		Parcel response = Parcel.obtain();
		request.writeInt(DS_REQUEST_GET_SLEEP_POINTS_BY_MONTH);
		request.writeInt(timeStamp);
		
		sendRequest(is, os, request, response);
		
		if (response.readInt() != DS_REQUEST_GET_SLEEP_POINTS_BY_MONTH) {
			ret = false;
		} else {
			int count = response.readInt();
			if (count > SleepPointsByMonth.DATA_COUNT) {
				ret = false;
			} else {
				result.mValues = new int[count];
				for (int i = 0; i < count; i++)
					result.mValues[i] = response.readInt();
			}
		}
		
		request.recycle();
		response.recycle();
		
		return ret;
	}
	
	public boolean requestSetWIFI(InputStream is, OutputStream os, String ssid, String user, String password)
		throws IOException
	{
		boolean ret = false;
		Parcel request = Parcel.obtain();
		Parcel response = Parcel.obtain();
		
		request.writeInt(DS_REQUEST_SET_WIFI);
		request.writeString(ssid);
		request.writeString(user);
		request.writeString(password);

		sendRequest(is, os, request, response);
		
		int r = response.readInt();
		int status = response.readInt();
		if (r == DS_REQUEST_SET_WIFI && status == DS_REQUEST_OK) {
			ret = true;	
		} else {
			ret = false;	
		}
		
		request.recycle();
		response.recycle();
		
		return ret;					
	}


};
