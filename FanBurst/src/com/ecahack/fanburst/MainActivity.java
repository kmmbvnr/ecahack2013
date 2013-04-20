package com.ecahack.fanburst;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.ecahack.fanburst.socket.*;
import com.ecahack.fanburst.socket.WebSocketClient.Listener;

import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.*;
import android.view.SurfaceHolder.Callback;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ToggleButton;
import android.widget.ViewFlipper;

public class MainActivity extends Activity implements OnClickListener, Callback, OnTouchListener {

	private TimeSyncService mTimeSync = new TimeSyncService();
	private Button mRegisterButton;
	private Button mFlashOnShakeButton;
	private ViewFlipper flipper;
	private View mRegistrationView;
	private View mActivationView;
	private boolean mPatternRunning;
	private boolean isFlashOn;

	Camera mCamera;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		flipper = (ViewFlipper) findViewById(R.id.flipper);

		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mRegistrationView = inflater.inflate(R.layout.registation, null);
		mActivationView = inflater.inflate(R.layout.activation, null);

		flipper.addView(mRegistrationView);
		flipper.addView(mActivationView);

		flipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
		flipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left));

		mRegisterButton = (Button)this.findViewById(R.id.registerButton);
		mRegisterButton.setOnClickListener(this);

		mFlashOnShakeButton = (Button)this.findViewById(R.id.flashOnShakeButton);
		mFlashOnShakeButton.setOnTouchListener(this);

		client.connect();

		SurfaceView preview = (SurfaceView) findViewById(R.id.surface);
		mHolder = preview.getHolder();
		mHolder.addCallback(this);
		mCamera = Camera.open();
		try {
			mCamera.setPreviewDisplay(mHolder);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}   

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return false;
	}

	private boolean checkCameraHardware(Context context) {
		if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
			// this device has a camera
			return true;
		} else {
			// no camera on this device
			return false;
		}
	}

	private void updateTimeSync(JSONObject data_content) {
		try {
			mTimeSync.collectResponse(data_content.getLong("sent_time"), data_content.getLong("server_time"));
		} catch (JSONException e) {
			Log.e(TAG, "Error when reading timesync response", e);
		}

		if(mTimeSync.getSamplesLength() < 5) {
			sentTimesyncRequest();
		} else {
			mTimeSync.finish();
			Log.d(TAG, String.format("Result timeshift %d", mTimeSync.getTimeshift()));
		}
	}

	private void updateStats(JSONObject data_content) {
		// TODO Auto-generated method stub

	}

	private void showPattern(JSONObject data_content) {
		if (mPatternRunning)
			return;
		try {
			JSONArray array = data_content.getJSONArray("pattern");
			final long interval = data_content.getLong("interval");
			Log.d(TAG, array.toString());
			final ArrayList<Integer> list = new ArrayList<Integer>();     
			int len = array.length();
			for (int i=0;i<len;i++){ 
				list.add(Integer.parseInt(array.get(i).toString()));
			} 
			MainActivity.this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mPatternRunning = true;
					runPattern(list, interval, 0);
				}
			});	

		} catch (JSONException e) {

		}

	}


	private void runPattern(final ArrayList<Integer> list, final long interval, final int i) {
		Integer brightness = list.get(i);
		if (brightness == 1)
			turnOn();
		else
			turnOff();
		if (i + 1 < list.size()) {
			final Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					MainActivity.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							runPattern(list, interval, i+1);
						}
					});	
				}
			}, interval);
		}
		else {
			mPatternRunning = false;
			turnOff();
		}

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
				if(data_type.equals("stats")) {
					updateStats(data_content);
				} else if (data_type.equals("timesync")) {
					updateTimeSync(data_content);
				} else if (data_type.equals("pattern")){
					showPattern(data_content);
				} 
				else {
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

	private SurfaceHolder mHolder;


	private static final String TAG = "FanBurst";

	@Override
	public void onClick(View v) {
		if (v == mRegisterButton) {
			sendRegisterInfo();
			sentTimesyncRequest();
		}
	}
	
	@Override
	public boolean onTouch( View button , MotionEvent theMotion ) {
		switch ( theMotion.getAction() ) {
		case MotionEvent.ACTION_DOWN: 
			turnOn();
			break;
		case MotionEvent.ACTION_UP: 
			turnOff();
			break;
		}
		return true;
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

	private void sentTimesyncRequest() {
		JSONObject request = new JSONObject();
		try {		
			JSONObject data = new JSONObject();
			data.put("sent_time", mTimeSync.getCurrentTimestamp());	

			request.put("command", "timesync");
			request.put("data", data);
			client.send(request.toString());
		} catch (JSONException e) {
			Log.e(TAG, "Error, when creating timesync request", e);
		}
	}

	private void sendDeactivateRequest() {
		JSONObject request = new JSONObject();
		try {		
			request.put("command", "deactivate");
			client.send(request.toString());
		} catch (JSONException e) {
			Log.e(TAG, "Error, when creating deactivate request", e);
		}
	}

	private void sendActivateRequest() {
		JSONObject request = new JSONObject();
		try {		
			request.put("command", "activate");
			client.send(request.toString());
		} catch (JSONException e) {
			Log.e(TAG, "Error, when creating deactivate request", e);
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


	public void onToggleClicked(View view) {
		boolean on = ((ToggleButton) view).isChecked();
		if (on) {
			sendActivateRequest();
		} else {
			sendDeactivateRequest();
		}
	}


	private void turnOn() {
		if (!isFlashOn) {
			isFlashOn = true;

			Parameters params = mCamera.getParameters();
			params.setFlashMode(Parameters.FLASH_MODE_TORCH);
			mCamera.setParameters(params);      
			mCamera.startPreview();
		}
	}

	private void turnOff() {
		if (isFlashOn) {
			isFlashOn = false;
			Parameters params = mCamera.getParameters();
			params.setFlashMode(Parameters.FLASH_MODE_OFF);
			mCamera.setParameters(params);
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {}

	public void surfaceCreated(SurfaceHolder holder) {
		mHolder = holder;
		try {
			mCamera.setPreviewDisplay(mHolder);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		mCamera.stopPreview();
		mHolder = null;
	}
}
