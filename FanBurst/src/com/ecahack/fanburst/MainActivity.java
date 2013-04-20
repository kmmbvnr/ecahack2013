package com.ecahack.fanburst;

import com.ecahack.fanburst.ShakeDetector.OnShakeListener;

import de.tavendo.autobahn.*;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.provider.Settings;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.EditText;

public class MainActivity extends Activity implements OnClickListener {

	private ShakeDetector mShakeDetector;
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		Settings.System.putInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

		// ShakeDetector initialization
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mShakeDetector = new ShakeDetector(new OnShakeListener() {
			@Override
			public void onShake() {
				synchronized (this) {
					runBrightnessFlash();
				}
			}
		});
		
		this.findViewById(android.R.id.content).setOnClickListener(this);
		
		startConnection();
	}


	@Override
	protected void onResume() {
		super.onResume();
		mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
	}

	@Override
	protected void onPause() {
		mSensorManager.unregisterListener(mShakeDetector);
		super.onPause();
	}   

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return false;
	}

	public void runBrightnessFlash() {
		WindowManager.LayoutParams lp = getWindow().getAttributes();

		if (lp.screenBrightness == 0) {
			lp.screenBrightness = 1;
			this.findViewById(android.R.id.content).setBackgroundColor(Color.argb(255, 255, 255, 255));
		}
		else {
			lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
			this.findViewById(android.R.id.content).setBackgroundColor(Color.argb(255, 0, 0, 0));
		}
		getWindow().setAttributes(lp);
	} 
	
	private void resetBrightness() {
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.screenBrightness = 1;
		this.findViewById(android.R.id.content).setBackgroundColor(Color.argb(255, 255, 255, 255));
		getWindow().setAttributes(lp);
	}
	
	
	private final WebSocketConnection mConnection = new WebSocketConnection();

	private void startConnection() {

		final String wsuri = "ws://178.79.139.131/9000/api";

		try {
			mConnection.connect(wsuri, new WebSocketHandler() {

				@Override
				public void onOpen() {
					Log.d(TAG, "Status: Connected to " + wsuri);
					//mConnection.sendTextMessage("Hello, world!");
				}

				@Override
				public void onTextMessage(String payload) {
					Log.d(TAG, "Got echo: " + payload);
				}

				@Override
				public void onClose(int code, String reason) {
					Log.d(TAG, "Connection lost.");
				}
			});
		} catch (WebSocketException e) {

			Log.d(TAG, e.toString());
		}
	}
	
	private static final String TAG = "FanBurst";

	@Override
	public void onClick(View v) {
		resetBrightness();
	}
	
	private String getDeviceId() {
		return  UniqueIdentifier.id(getApplicationContext());
	}
	
	private String getUserSector() {
		return getEditTextValue(R.id.sectorTextView);
	}
	
	private String getUserPlace() {
		return getEditTextValue(R.id.placeTextView);
	}
	
	private String getUserRow() {
		return getEditTextValue(R.id.rowTextView);
	}
	
	private String getEditTextValue(int id) {
		EditText editText = (EditText) this.findViewById(id);
		return editText.getText().toString();
	}

}
