package com.ecahack.fanburst;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class TimeSyncService {
	private long mTimeshift;
	private boolean mFirstResponse = true;
	private List<Roundtrip> mRoundTrips = new LinkedList<Roundtrip>();
	
	class Roundtrip implements Comparable<Roundtrip> {
		long mSentTime;
		long mServerTime;
		long mReceiveTime;
				
		public Roundtrip(long sentTime, long serverTime, long receiveTime) {
			mSentTime = sentTime;
			mServerTime = serverTime;
			mReceiveTime = receiveTime;
		}
		
		public long latency() {
			return mReceiveTime - mSentTime;
		}
		
		public long timeshift() {
			return (mServerTime + this.latency()/2)-mReceiveTime;
		}

		@Override
		public int compareTo(Roundtrip that) {
			long thisLatency = this.latency();
			long thatLatency = that.latency();
			if (thisLatency > thatLatency) {
				return 1;
			} else if (thisLatency == thatLatency) {
				return 0;
			}
			return -1;
		}
		
		@Override
		public String toString() {
			return String.format("(%s, %s, %s)", mSentTime, mServerTime, mReceiveTime);
		}
	}
	
	public TimeSyncService() {
		mTimeshift = 0;
	}
		
	public long getCurrentTimestamp() {
		return System.currentTimeMillis()/1000L + mTimeshift;
	}	
	
	public int getSamplesLength() {
		return mRoundTrips.size();
	}

	public void collectResponse(long sent_timestamp, long server_timestamp) {
		Roundtrip roundtrip = new Roundtrip(sent_timestamp, server_timestamp, this.getCurrentTimestamp());

		if(mFirstResponse) {
			mFirstResponse = false;
			mTimeshift = roundtrip.timeshift();
		} else {
			mRoundTrips.add(roundtrip);
		}
	}
	
	public void finish() {
		Collections.sort(mRoundTrips);
			
		double latencyMean = 0;
		for(Roundtrip roundTrip : mRoundTrips) {
			latencyMean += (1.0 * roundTrip.latency()) / mRoundTrips.size();
		}

		double std_dev = 0;
		for(Roundtrip roundTrip : mRoundTrips) {
			std_dev += Math.pow(roundTrip.latency() - latencyMean, 2)/ mRoundTrips.size();	
		}	
		std_dev = Math.sqrt(std_dev);
		
		double latencyMedian = mRoundTrips.get(mRoundTrips.size()/2).latency();

		int count = 0;
		long meanTimeshift = 0;
		for(Roundtrip roundTrip : mRoundTrips) {
			if(latencyMedian - std_dev <= roundTrip.latency() && roundTrip.latency() <= latencyMedian + std_dev) {
				meanTimeshift += roundTrip.timeshift();
				count += 1;
			}
		}

		mTimeshift += (meanTimeshift/count);
	}

	public long getTimeshift() {
		return mTimeshift;
	}	
}
