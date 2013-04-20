package com.ecahack.fanburst;

import java.net.URI;

import org.json.JSONException;
import org.json.JSONObject;

import com.ecahack.fanburst.ShakeDetector.OnShakeListener;
import com.ecahack.fanburst.socket.*;
import com.ecahack.fanburst.socket.WebSocketClient.Listener;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.provider.Settings;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ViewFlipper;

public class MainActivity extends Activity implements OnClickListener {

	private ShakeDetector mShakeDetector;
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private TimeSyncService mTimeSync = new TimeSyncService();
	private Button mRegisterButton;
	private ViewFlipper flipper;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		flipper = (ViewFlipper) findViewById(R.id.flipper);

	    LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	    int layouts[] = new int[]{ R.layout.registation, R.layout.flash };
	    for (int layout : layouts)
	        flipper.addView(inflater.inflate(layout, null));
	    
	    flipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
        flipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left));
		
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
					//runBrightnessFlash();
				}
			}
		});
		
		this.findViewById(android.R.id.content).setOnClickListener(this);
		
		mRegisterButton = (Button)this.findViewById(R.id.registerButton);
		mRegisterButton.setOnClickListener(this);
		
		client.connect();
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

    private void updateTimeSync(JSONObject data_content) {
		// TODO Auto-generated method stub
		
	}

	private void updateStats(JSONObject data_content) {
		// TODO Auto-generated method stub
		
	}
	
	WebSocketClient client = new WebSocketClient(URI.create("ws://178.79.139.131:9000/api"), new Listener() {
	    @Override
	    public void onConnect() {
	        Log.d(TAG, "Connected!");
	    }

	    @Override
	    public void onMessage(String message) {
	        Log.d(TAG, String.format("Got string message! %s", message));
	        try {
				JSONObject data = new JSONObject(message);
				String data_type = data.getString("type");
				JSONObject data_content = data.getJSONObject("data");
				if(data_type == "stats") {
					updateStats(data_content);
				} else if (data_type == "timesync") {
					updateTimeSync(data_content);
				} else {
					Log.d(TAG, String.format("Unknown data type %s", message));
				}
			} catch (JSONException e) {
				Log.d(TAG, String.format("Can't decode json %s %s", message, e.getMessage()));
			}
	    }

		@Override
	    public void onMessage(byte[] data) {
	        //Log.d(TAG, String.format("Got binary message! %s", toHexString(data)));
	    }

	    @Override
	    public void onDisconnect(int code, String reason) {
	        Log.d(TAG, String.format("Disconnected! Code: %d Reason: %s", code, reason));
	    }

	    @Override
	    public void onError(Exception error) {
	        Log.e(TAG, "Error!", error);
	    }
	}, null);

	
	private static final String TAG = "FanBurst";

	@Override
	public void onClick(View v) {
		if (v == mRegisterButton)
			sendRegisterInfo();
		resetBrightness();
	}
	
	private void sendRegisterInfo() {
		String deviceId = getDeviceId();
		String sector = getUserSector();
		String row = getUserRow();
		String place = getUserPlace();
		if (validateUserRegisterData(sector, row, place)) {
			JSONObject object = new JSONObject();
	        JSONObject info = new JSONObject();
	        try {
	        	info.put("mobile_id", deviceId);
	        	info.put("sector",sector);
	        	info.put("row", row);
	        	info.put("place", place);
	          object.put("command", "register");
	          object.put("data", info );
	        } catch (JSONException e) {
	          e.printStackTrace();
	        }
	        System.out.println(object);
	        client.send(object.toString());
	        
	        flipper.showNext();
		}
	}
	
	private boolean validateUserRegisterData(String sector, String row, String place) {
		return (Integer.parseInt(sector) > 0 && Integer.parseInt(row) > 0 && Integer.parseInt(place) > 0);
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
