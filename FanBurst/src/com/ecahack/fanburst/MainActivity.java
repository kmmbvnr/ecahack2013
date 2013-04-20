package com.ecahack.fanburst;

import com.ecahack.fanburst.ShakeDetector.OnShakeListener;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.provider.Settings;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {

	private ShakeDetector mShakeDetector;
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		//this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		
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
}
