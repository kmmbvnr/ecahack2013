package com.ecahack.fanburst;

import java.net.URI;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.ecahack.fanburst.socket.WebSocketClient;
import com.ecahack.fanburst.socket.WebSocketClient.Listener;

public class WSClient {
	
	public WSClient(WSClientListener listener) {
		mListener = listener;
	}
	
	public void connect() {
		mClient.connect();
	}
	
	public boolean sendRegisterInfo(String deviceId, String sector, String row, String place) {
		
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
			
			mClient.send(object.toString());
			return true;
		}
		return false;
	}

	public void sentTimesyncRequest(long currentTimestamp) {
		JSONObject request = new JSONObject();
		try {		
			JSONObject data = new JSONObject();
			data.put("sent_time", currentTimestamp);	

			request.put("command", "timesync");
			request.put("data", data);
			mClient.send(request.toString());
		} catch (JSONException e) {
			Log.e(TAG, "Error, when creating timesync request", e);
		}
	}

	public void sendDeactivateRequest() {
		JSONObject request = new JSONObject();
		try {		
			request.put("command", "deactivate");
			mClient.send(request.toString());
		} catch (JSONException e) {
			Log.e(TAG, "Error, when creating deactivate request", e);
		}
	}

	public void sendActivateRequest() {
		JSONObject request = new JSONObject();
		try {		
			request.put("command", "activate");
			mClient.send(request.toString());
		} catch (JSONException e) {
			Log.e(TAG, "Error, when creating deactivate request", e);
		}
	}


	WebSocketClient mClient = new WebSocketClient(URI.create("ws://178.79.139.131:9000/api"), new Listener() {
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
					mListener.updateStats(data_content.getLong("active"), data_content.getLong("users"));
				} 
				else if (data_type.equals("timesync")) {
					mListener.updateTimeSync(data_content.getLong("sent_time"), data_content.getLong("server_time"));
				} 
				else if (data_type.equals("pattern")){
					JSONArray array = data_content.getJSONArray("pattern");
					ArrayList<Integer> list = new ArrayList<Integer>();     
					for (int i=0; i<array.length(); i++) 
						list.add(Integer.parseInt(array.get(i).toString()));
					mListener.showPattern(data_content.getString("pattern_name"), data_content.getLong("start_at"), data_content.getLong("interval"), list);
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
	
	private boolean validateUserRegisterData(String sector, String row, String place) {
		return (sector.length()>0 && Integer.parseInt(sector) > 0 && 
				row.length()>0 && Integer.parseInt(row) > 0 && 
				place.length()>0 && Integer.parseInt(place) > 0);
	}

	
	private static final String TAG = "WSClient";
	private WSClientListener mListener;
	
	public interface WSClientListener {
    	public void updateStats(long active, long users);
    	public void updateTimeSync(long sentTime, long serverTime);
    	public void showPattern(String name, long startAt, long interval, ArrayList<Integer> pattern);
    }
}

