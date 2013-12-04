package ar.com.zgroup.sip.service;

import org.apache.cordova.LOG;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.sip.SipAudioCall;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.net.sip.SipRegistrationListener;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;
import ar.com.zgroup.sip.R;
import ar.com.zgroup.sip.SipManagerActivity;

public class SipManagerService extends Service {
	
	private static final String TAG = SipManagerService.class.getName();
	private static final String INCOMING_CALL_ACTION = "ar.com.zgroup.sip.INCOMING_CALL";
	private static final int RUNNING_NOTIFICATION_ID = 72046;
	
	private SipManager mSipManager;
	private SipProfile mSipProfile;
	private SipAudioCall mCurrentCall;
	private SipAudioCall mIncomingCall;
	
	private IncomingCallReceiver mIncomingCallReceiver;
	
	private Notification mRunningNotification;
	
	private SipManagerListener mListener;
	
	private SipRegistrationListener mRegistrationListener = new RegistrationListener();
	private SipRegistrationListener mPostRegistrationListener = new PostRegistrationListener();
	
	@Override
	public IBinder onBind(Intent intent) {
		return new SipManagerLocalBinder();
	}
	
	private final class RegistrationListener implements SipRegistrationListener {
		public void onRegistering(String localProfileUri) {
			LOG.i(TAG, "Registering with SIP Server...");
			if (mListener != null) mListener.onConnecting();
		}
		
		public void onRegistrationDone(String localProfileUri, long expiryTime) {
			LOG.i(TAG, "Registration succeded...");
			if (mIncomingCallReceiver == null) {
				mIncomingCallReceiver = new IncomingCallReceiver();
				registerReceiver(mIncomingCallReceiver, new IntentFilter(INCOMING_CALL_ACTION));
			}
			if (mRunningNotification == null) {
				mRunningNotification = createRunningNotification(R.string.sip_active_title, R.string.sip_active_content,
						R.string.sip_active_ticker);
				startForeground(RUNNING_NOTIFICATION_ID, mRunningNotification);
			}
			
			// Listener for POST registration events (connection lost, re-registering, etc.)
			try {
				mSipManager.setRegistrationListener(mSipProfile.getUriString(), mPostRegistrationListener);
			} catch (SipException e) {
				LOG.e(TAG, "Error setting post registration listener.", e);
			}
			
			if (mListener != null) mListener.onConnectionSuccess();
		}
		
		public void onRegistrationFailed(String localProfileUri, int errorCode, String errorMessage) {
			LOG.w(TAG, "Registration failed.");
			if (mListener != null) mListener.onConnectionFailed();
		}
	}
	
	private final class PostRegistrationListener implements SipRegistrationListener {
		public void onRegistering(String localProfileUri) {
			LOG.i(TAG, "Reconnecting to SIP Server...");
		}
		
		public void onRegistrationDone(String localProfileUri, long expiryTime) {
			LOG.i(TAG, "Reconnection succeded");
			mRunningNotification = createRunningNotification(R.string.sip_active_title, R.string.sip_active_content,
					R.string.sip_active_ticker);
			startForeground(RUNNING_NOTIFICATION_ID, mRunningNotification);
		}
		
		public void onRegistrationFailed(String localProfileUri, int errorCode, String errorMessage) {
			LOG.w(TAG, "Reconnection failed");
			NotificationManager nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			mRunningNotification = createRunningNotification(R.string.sip_inactive_title, R.string.sip_inactive_content,
					R.string.sip_inactive_ticker);
			nManager.notify(RUNNING_NOTIFICATION_ID, mRunningNotification);
			startForeground(RUNNING_NOTIFICATION_ID, mRunningNotification);
		}
	}
	
	public class SipManagerLocalBinder extends Binder {
		
		public SipManagerService getService() {
			return SipManagerService.this;
		}
		
	}
	
	@Override
	public void onCreate() {
		LOG.i(TAG, "onCreate");
		mSipManager = SipManager.newInstance(this); // Create SipManager once per service instance
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY; // Just return this flag so that Android restarts service if killed. Should not be necessary if startForeground was called
	}
	
	/**
	 * Connects to the SIP server
	 * 
	 * @param domain
	 * @param username
	 * @param passwd
	 * @throws SipException
	 */
	public void connect(String domain, String username, String passwd) throws SipException {
		if (!SipManager.isApiSupported(this)) throw new SipException("Not supported.");
		
		if (isRegistered()) throw new SipException("Already registered.");
		
		try {
			
			SipProfile.Builder builder = new SipProfile.Builder(username, domain);
			builder.setPassword(passwd);
			builder.setAutoRegistration(true); // flag set to true so that SipManager handles network state?
			mSipProfile = builder.build();
			
			Intent intent = new Intent();
			intent.setAction(INCOMING_CALL_ACTION);
			PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, Intent.FILL_IN_DATA);
			
			mSipManager.open(mSipProfile, pendingIntent, null);
			
			// Listener for registration events (registration success/failure)
			mSipManager.register(mSipProfile, 30, mRegistrationListener);
		} catch (Exception e) {
			LOG.e(TAG, "Failed to start SipManagerService.", e);
			disconnect();
			throw new SipException("Failed to start SipManagerService.", e);
		}
	}
	
	/**
	 * Returns if a SIP session was established
	 * 
	 * @return
	 */
	public boolean isRegistered() {
		try {
			return mSipProfile != null && mSipManager.isRegistered(mSipProfile.getUriString());
		} catch (SipException e) {
			LOG.e(TAG, e.getMessage(), e);
			return false;
		}
	}
	
	/**
	 * Disconnects from SIP server
	 */
	public void disconnect() {
		
		try {
			endCurrentCall();
		} catch (SipException e) {}
		
		try {
			rejectIncomingCall();
		} catch (SipException e) {}
		
		if (mSipProfile != null) {
			try {
				mSipManager.close(mSipProfile.getUriString());
			} catch (SipException e) {
				LOG.e(TAG, "Failed to close local profile.", e);
			}
			try {
				mSipManager.unregister(mSipProfile, new SipRegistrationListener() {
					
					public void onRegistering(String localProfileUri) {
						LOG.i(TAG, "Disconnecting");
					}
					
					public void onRegistrationDone(String localProfileUri, long expiryTime) {
						LOG.i(TAG, "Disconnect succeded");
					}
					
					public void onRegistrationFailed(String localProfileUri, int errorCode, String errorMessage) {
						LOG.e(TAG, "Disconnect failed");
					}
				});
			} catch (SipException e) {
				LOG.e(TAG, "Failed to unregister local profile.", e);
			}
			mSipProfile = null;
		}
		
		if (mIncomingCallReceiver != null) {
			unregisterReceiver(mIncomingCallReceiver);
			mIncomingCallReceiver = null;
		}
		
		stopForeground(true);
		mRunningNotification = null;
	}
	
	/**
	 * Answers an incoming call if any
	 * 
	 * @throws SipException
	 */
	public void takeCall() throws SipException {
		if (mIncomingCall != null) {
			try {
				endCurrentCall();
			} catch (SipException e) {}
			
			try {
				mIncomingCall.answerCall(30);
				mIncomingCall.startAudio();
				if (mIncomingCall.isMuted()) {
					mIncomingCall.toggleMute();
				}
				mCurrentCall = mIncomingCall;
				mIncomingCall = null;
			} catch (SipException e) {
				LOG.e(TAG, "Error taking incoming call");
				try {
					rejectIncomingCall();
				} catch (SipException e1) {}
				throw e;
			}
		}
	}
	
	/**
	 * Rejects an incoming call if any
	 * 
	 * @throws SipException
	 */
	public void rejectIncomingCall() throws SipException {
		if (mIncomingCall != null) {
			try {
				mIncomingCall.endCall();
			} catch (SipException e) {
				LOG.e(TAG, "Error ending incoming call");
				throw e;
			} finally {
				mIncomingCall.close();
				mIncomingCall = null;
			}
		}
	}
	
	/**
	 * Ends the current call if any
	 * 
	 * @throws SipException
	 */
	public void endCurrentCall() throws SipException {
		if (mCurrentCall != null) {
			try {
				mCurrentCall.endCall();
			} catch (SipException e) {
				LOG.e(TAG, "Error ending current call");
				throw e;
			} finally {
				mCurrentCall.close();
				mCurrentCall = null;
			}
		}
	}
	
	/**
	 * Enables Speakerphone for the current call if any
	 * 
	 * @param speakerMode
	 */
	public void setSpeakerMode(boolean speakerMode) {
		if (mCurrentCall != null) {
			mCurrentCall.setSpeakerMode(speakerMode);
		}
	}
	
	/**
	 * Establishes a new audio call
	 * 
	 * @param username
	 * @param domain
	 * @throws SipException
	 */
	public void makeCall(String username, String domain) throws SipException {
		try {
			SipProfile.Builder builder = new SipProfile.Builder(username, domain);
			SipProfile otherProfile = builder.build();
			
			mSipManager.makeAudioCall(mSipProfile, otherProfile, new SipAudioCall.Listener() {
				
				@Override
				public void onCallEstablished(SipAudioCall call) {
					call.startAudio();
					
					if (call.isMuted()) {
						call.toggleMute();
					}
					mCurrentCall = call;
					if (mListener != null) mListener.onCallEstablished();
				}
				
				@Override
				public void onCallEnded(SipAudioCall call) {
					if (mListener != null) mListener.onCallEnded();
				}
			}, 30);
		} catch (Exception e) {
			LOG.e(TAG, "Error making call.", e);
			throw new SipException("Error making call.", e);
		}
		
	}
	
	/**
	 * Creates and returns the persistent notification
	 * 
	 * @return
	 */
	private Notification createRunningNotification(int titleResId, int contentResId, int tickerResId) {
		PendingIntent notificationTapIntent = PendingIntent.getActivity(this, 0, new Intent(this, SipManagerActivity.class),
				PendingIntent.FLAG_UPDATE_CURRENT);
		
		NotificationCompat.Builder nBuilder = new NotificationCompat.Builder(this);
		nBuilder.setSmallIcon(R.drawable.icon)
				.setContentIntent(notificationTapIntent)
				.setOnlyAlertOnce(true)
				.setTicker(getString(tickerResId))
				.setContentTitle(getString(titleResId))
				.setContentText(getString(contentResId));
		
		Notification runningNotification = nBuilder.build();
		runningNotification.flags |= Notification.FLAG_ONGOING_EVENT;
		return runningNotification;
	}
	
	/***
	 * Listens for incoming SIP calls.
	 */
	private class IncomingCallReceiver extends BroadcastReceiver {
		
		@Override
		public void onReceive(Context context, final Intent intent) {
			if (SipManager.isIncomingCallIntent(intent)) {
				try {
					if (isRegistered()) {
						// Acquire wakelock to turn on screen on incoming call
						PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
						final WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "TAG");
						wl.acquire();
						
						SipAudioCall.Listener listener = new SipAudioCall.Listener() {
							
							@Override
							public void onRinging(SipAudioCall call, SipProfile caller) {
								if (mListener != null) mListener.onIncomingCall(caller.getUserName());
								wl.release(); // Release wakelock
							}
							
							@Override
							public void onError(SipAudioCall call, int errorCode, String errorMessage) {
								// TODO Auto-generated method stub
								super.onError(call, errorCode, errorMessage);
							}
							
							@Override
							public void onChanged(SipAudioCall call) {
								// TODO Auto-generated method stub
								super.onChanged(call);
							}
							
						};
						
						mIncomingCall = mSipManager.takeAudioCall(intent, null);
						mIncomingCall.setListener(listener, true);
					}
				} catch (Exception e) {
					try {
						rejectIncomingCall();
					} catch (SipException e1) {}
				}
			}
		}
	}
	
	public void addListener(SipManagerListener listener) {
		mListener = listener;
	}
	
	/**
	 * Unified listener for SipManager events
	 * 
	 * @author lglossman
	 * 
	 */
	public interface SipManagerListener {
		
		void onConnectionFailed();
		
		void onConnectionSuccess();
		
		void onConnecting();
		
		void onCallEstablished();
		
		void onCallEnded();
		
		void onIncomingCall(String callerId);
		
	}
}
