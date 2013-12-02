package ar.com.zgroup.sip.cordova.plugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.sip.SipException;
import android.os.IBinder;
import ar.com.zgroup.sip.service.SipManagerService;
import ar.com.zgroup.sip.service.SipManagerService.SipManagerLocalBinder;
import ar.com.zgroup.sip.service.SipManagerService.SipManagerListener;

public class SipManagerPlugin extends CordovaPlugin implements SipManagerListener {
	
	// Supported action strings
	private static final String INIT = "init";
	private static final String CONNECT = "connect";
	private static final String DISCONNECT = "disconnect";
	private static final String MAKE_CALL = "make_call";
	private static final String END_CALL = "end_call";
	private static final String TAKE_CALL = "take_call";
	private static final String REJECT_CALL = "reject_call";
	private static final String SPEAKER_MODE = "speaker_mode";
	
	private SipManagerService mSipManagerService;
	//private CallbackContext mPersistentCallbackContext;
	
	private ServiceConnection mSipManagerConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder binder) {
			mSipManagerService = ((SipManagerLocalBinder) binder).getService();
			mSipManagerService.addListener(SipManagerPlugin.this);
		}
		
		@Override
		public void onServiceDisconnected(ComponentName name) {}
	};
	
	public SipManagerPlugin() {}
	
	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		
		// Start service with intent to keep it running
		Intent intent = new Intent(cordova.getActivity(), SipManagerService.class);
		cordova.getActivity().startService(intent);
		
		// Bind service for latter communication
		cordova.getActivity().bindService(new Intent(cordova.getActivity(), SipManagerService.class), mSipManagerConnection, Context.BIND_AUTO_CREATE);
	}
	
	@Override
	public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
		if (INIT.equals(action)) {
			PluginResult result = new PluginResult(Status.OK);
			result.setKeepCallback(true);
			callbackContext.sendPluginResult(result);
		} else {
			if (mSipManagerService == null) return false; // In case service is not binded yet. init not called?
				
			if (CONNECT.equals(action)) {
				connect(args, callbackContext);
			} else if (DISCONNECT.equals(action)) {
				disconnect(callbackContext);
			} else if (MAKE_CALL.equals(action)) {
				makeCall(args, callbackContext);
			} else if (END_CALL.equals(action)) {
				endCurrentCall(callbackContext);
			} else if (TAKE_CALL.equals(action)) {
				takeIncomingCall(callbackContext);
			} else if (REJECT_CALL.equals(action)) {
				rejectIncomingCall(callbackContext);
			} else if (SPEAKER_MODE.equals(action)) {
				setSpeakerMode(args, callbackContext);
			} else {
				return false;
			}
		}
		return true;
	}
	
	public void endCurrentCall(CallbackContext callbackContext) {
		try {
			mSipManagerService.endCurrentCall();
			callbackContext.success(new JSONObject());
		} catch (SipException e) {
			callbackContext.error(new JSONObject());
		}
	}
	
	public void takeIncomingCall(CallbackContext callbackContext) {
		try {
			mSipManagerService.takeCall();
			callbackContext.success(new JSONObject());
		} catch (SipException e) {
			callbackContext.error(new JSONObject());
		}
	}
	
	public void rejectIncomingCall(CallbackContext callbackContext) {
		try {
			mSipManagerService.rejectIncomingCall();
			callbackContext.success(new JSONObject());
		} catch (SipException e) {
			callbackContext.error(new JSONObject());
		}
	}
	
	public void makeCall(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
		JSONObject options = args.optJSONObject(0);
		if (options != null) {
			String domain = options.getString("domain");
			String username = options.getString("username");
			try {
				mSipManagerService.makeCall(username, domain);
				callbackContext.success(new JSONObject());
			} catch (SipException e) {
				callbackContext.error(new JSONObject());
			}
		}
	}
	
	public void connect(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
		JSONObject options = args.optJSONObject(0);
		if (options != null) {
			String domain = options.getString("domain");
			String username = options.getString("username");
			String passwd = options.getString("password");
			try {
				mSipManagerService.connect(domain, username, passwd);
				callbackContext.success(new JSONObject());
			} catch (SipException e) {
				callbackContext.error(new JSONObject());
			}
		}
	}
	
	public void disconnect(CallbackContext callbackContext) {
		mSipManagerService.disconnect();
		callbackContext.success(new JSONObject());
	}
	
	public void setSpeakerMode(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
		JSONObject options = args.optJSONObject(0);
		if (options != null) {
			Boolean speakerMode = options.getBoolean("speakerMode");
			mSipManagerService.setSpeakerMode(speakerMode);
			callbackContext.success(new JSONObject());
		} else {
			callbackContext.error(new JSONObject());
		}
	}
	
	@Override
	public void onConnecting() {
		webView.sendJavascript("SipManagerPlugin.listener.onConnecting()");
	}
	
	@Override
	public void onConnectionSuccess() {
		webView.sendJavascript("SipManagerPlugin.listener.onConnectionSuccess()");
	}
	
	@Override
	public void onConnectionFailed() {
		webView.sendJavascript("SipManagerPlugin.listener.onConnectionFailed()");
	}
	
	@Override
	public void onCallEstablished() {
		webView.sendJavascript("SipManagerPlugin.listener.onCallEstablished()");
	}
	
	@Override
	public void onCallEnded() {
		webView.sendJavascript("SipManagerPlugin.listener.onCallEnded()");
	}
	
	@Override
	public void onIncomingCall() {
		webView.sendJavascript("SipManagerPlugin.listener.onIncomingCall()");
	}
	
}
