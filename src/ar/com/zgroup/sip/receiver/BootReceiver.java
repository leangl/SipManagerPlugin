package ar.com.zgroup.sip.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
	
	private static final String BOOT_COMPLETED_ACTION = "android.intent.action.BOOT_COMPLETED";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (BOOT_COMPLETED_ACTION.equals(intent.getAction())) {
			
		}
	}
}
