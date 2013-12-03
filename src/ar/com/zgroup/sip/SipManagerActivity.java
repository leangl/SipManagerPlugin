package ar.com.zgroup.sip;

import org.apache.cordova.Config;
import org.apache.cordova.CordovaActivity;

import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager.LayoutParams;

public class SipManagerActivity extends CordovaActivity
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		super.init();
		// Set by <content src="index.html" /> in config.xml
		super.loadUrl(Config.getStartUrl());
		//super.loadUrl("file:///android_asset/www/index.html")
		
		Window window = getWindow();
		window.addFlags(LayoutParams.FLAG_DISMISS_KEYGUARD | LayoutParams.FLAG_SHOW_WHEN_LOCKED);
	}
}
