package com.sxx.BluetoothWave;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.View;
import android.util.AttributeSet;
import java.util.ArrayList;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class WaveViewer extends View {
	private static final String TAG = "WaveViewer";
	
	private static final int RAW_WAVE = 0;
	private static final int BREATH_WAVE = 1;
	private static final int HEART_BEAT_WAVE = 2;
	private static final int WAVES_NUM = 3;

	private Paint   mPaint = new Paint();
	private	int mMaxNumOfPoints;

	private int	mWidth;
	private int mHeight;
	private ArrayList<Integer>[] mDataPoints;
	private ArrayList<Integer> mHeartBeat;
	private Path[] mWaves;
	private Path mGrid;
	private int[] mColors;

	private static final int MESSAGE_UPDATE_VIEW = 1;

	public void pushOneValue(float[] values)
	{
		// values[0] - full scale
		// values[1] - raw value
		// values[2] - breath
		// values[3] - heart beat
		float fs = values[0];
		
		//Log.d(TAG, "============:" + fs + "," + values[1] + "," + values[2] + "," + values[3]);
		for(int i = 0; i < WAVES_NUM; i++) {
			
			int v = (int)(values[i + 1] * mHeight / fs / 4); 
			
			if (mDataPoints[i].size() < mMaxNumOfPoints) {
				mDataPoints[i].add(v);
			} else {
				mDataPoints[i].remove(0);
				mDataPoints[i].add(v);
			}
	
			
			mWaves[i].rewind();
			mWaves[i].moveTo(0, mDataPoints[i].get(0));
			for (int j = 1; j < mDataPoints[i].size(); j++) {
				mWaves[i].lineTo(j, mDataPoints[i].get(j));
			}

		}
		mHandler.obtainMessage(MESSAGE_UPDATE_VIEW).sendToTarget();

	}

	public WaveViewer(Context context) {
		this(context, null);
	}

	public WaveViewer(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public WaveViewer(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mMaxNumOfPoints = 800;
		mWidth = 800;
		mHeight = 480;
		
		mDataPoints = new ArrayList[WAVES_NUM];
		mWaves = new Path[WAVES_NUM];
		mColors = new int[WAVES_NUM];
		
		for (int i = 0; i < WAVES_NUM; i++) {
			mDataPoints[i] = new ArrayList<Integer>();
			mWaves[i] = new Path();	
		}
		
		mColors[0] = Color.YELLOW;
		mColors[1] = Color.GREEN;
		mColors[2] = Color.RED;

		mGrid = new Path();

		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

		mPaint.setPathEffect(null);
	}

	private void makeGrid()
	{
		mGrid.moveTo(0, mHeight / 2);
		mGrid.lineTo(mWidth, mHeight / 2);	
	}
	private void drawWave(Canvas canvas)
	{
		canvas.translate(0, mHeight / 2);
		
		for (int i = 0; i < WAVES_NUM; i++) {
			mPaint.setColor(mColors[i]);
			mPaint.setStyle(Paint.Style.STROKE);
			mPaint.setStrokeWidth(3);
			canvas.drawPath(mWaves[i], mPaint);
		}

	}
	private void drawGrid(Canvas canvas)
	{
		canvas.translate(0, 0);
		mPaint.setColor(Color.YELLOW);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeWidth(1);
		canvas.drawPath(mGrid, mPaint);    	
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		mMaxNumOfPoints = right - left;
		mWidth = right - left;
		mHeight = bottom - top;
		makeGrid();
	}
	@Override protected void onDraw(Canvas canvas) {
		canvas.drawColor(Color.BLACK);
		drawGrid(canvas);
		drawWave(canvas);
	}

	private final Handler mHandler = new Handler() {
		@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
					case MESSAGE_UPDATE_VIEW:
						invalidate();
						break;
				}
			}
	};
}
