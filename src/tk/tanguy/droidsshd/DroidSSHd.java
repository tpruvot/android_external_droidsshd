/**
 *  This file is part of DroidSSHd.
 *  http://code.google.com/p/droidsshd
 *  
 *  DroidSSHd is open source software: you can redistribute it and/or modify
 *  it under the terms of the Apache License 2.0
 *  
 *  DroidSSHd is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 

 *  @author Augusto Bott (mestre) <augusto@bott.com.br>
 *  @author Tanguy Pruvot <tanguy.pruvot@gmail.com>

 */

package tk.tanguy.droidsshd;

import tk.tanguy.droidsshd.system.*;
import tk.tanguy.droidsshd.tools.*;

import java.util.Iterator;
import android.util.Log;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
//import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
//import android.widget.ScrollView;

public class DroidSSHd extends Activity { 
	private static final String TAG = "DroidSSHd";

// http://developer.android.com/reference/java/lang/Thread.html#setDaemon%28boolean%29

//	http://stackoverflow.com/questions/3011604/how-do-i-get-preferences-to-work-in-android
//	http://stackoverflow.com/questions/2535132/how-do-you-validate-the-format-and-values-of-edittextpreference-entered-in-androi
	private Button btnStartStop;
	private EditText status_content;
	private EditText status_ip_address;
	private EditText status_username;
	private EditText status_tcp_port;
	
	private Button preferences_button;

	private ReplicantThread mMonitorDaemon;

	private Intent mDropbearDaemonHandlerService;
	
	private DroidSSHdService mBoundDaemonHandlerService;
	private boolean mDaemonHandlerIsBound;

	private long mUpdateUIdelay = 500;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Base.initialize(getBaseContext());
		Base.setDropbearDaemonStatus(Base.DAEMON_STATUS_UNKNOWN);

		setContentView(R.layout.act_main);
		setUpUiListeners();

		if ((!Util.validateHostKeys() || (!Util.checkPathToBinaries()))) {
			startInitialSetupActivity();
		}
		
		Base.setDropbearDaemonStatus(Base.DAEMON_STATUS_STOPPED);
		Base.setManualServiceStart(true);

		mDropbearDaemonHandlerService = new Intent(this, tk.tanguy.droidsshd.system.DroidSSHdService.class);
		mHandler.postDelayed(mUpdateUI, 150);
	}

	@Override
	protected void onStart() {
		super.onStart();
		doBindDaemonHandlerService(mDropbearDaemonHandlerService);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		Base.refresh();
		mHandler.postDelayed(mUpdateUI, mUpdateUIdelay);
		if(Base.debug) {
			Log.d(TAG, "onResume() called");
		}
	}

	@Override
	protected void onDestroy() {
		doUnbindDaemonHandlerService(mDropbearDaemonHandlerService);
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (Base.debug) {
			if (data!=null) {
				Log.v(TAG, "onActivityResult(" + requestCode + ", " + resultCode + ", " + data.toString() + ") called");
			} else {
				Log.v(TAG, "onActivityResult(" + requestCode + ", " + resultCode + ", null) called");
			}
		}
		if(resultCode==RESULT_CANCELED && requestCode==R.string.activity_initial_setup){
			Util.showMsg("DroidSSHd setup canceled");
			this.finish();
		}
	}

	protected void startInitialSetupActivity() {
		Util.showMsg("Initial/basic setup required");
		Intent setup = new Intent(this, tk.tanguy.droidsshd.activity.InitialSetup.class);
		setup.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
//		setup.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		startActivityForResult(setup, R.string.activity_initial_setup);
	}

	protected void setUpUiListeners() {
		status_content = (EditText) findViewById(R.id.status_content);
		status_ip_address = (EditText) findViewById(R.id.status_ip_address); 
		status_username = (EditText) findViewById(R.id.status_username);
		status_tcp_port = (EditText) findViewById(R.id.status_tcp_port);
		
		btnStartStop = (Button) findViewById(R.id.status_button);
		btnStartStop.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				btnStartStop.setEnabled(false);
				btnStartStop.setFocusable(false);
				//btnStartStop.setText(R.string.act_main_busy);
				if (Util.isDropbearDaemonRunning()) {
						if(Base.debug) {
							Log.v(TAG, "btnStartStop pressed: stopping");
						}
						stopDropbear();
						mHandler.postDelayed(mUpdateUI, 2000);
				} else {
						if(Base.debug) {
							Log.v(TAG, "btnStartStop pressed: starting");
						}
						startDropbear();
						mHandler.postDelayed(mUpdateUI, 1000);
				}
			}

		});

		
//		mStdOut = (EditText) findViewById(R.id.stdout);
//		mLogView = (ScrollView) findViewById(R.id.stdout_scrollview);
		
/*		filechooser_button = (Button) findViewById(R.id.filechooser_button);
		filechooser_button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent p = new Intent(v.getContext(), com.h3r3t1c.filechooser.FileChooser.class);
				startActivityForResult(p, R.string.activity_file_chooser);
			}
		});*/

		preferences_button = (Button) findViewById(R.id.preferences_button);
		preferences_button.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent p = new Intent(v.getContext(), tk.tanguy.droidsshd.activity.Preferences.class);
				startActivity(p);
			}
		});
	}

	public void updateStatus() {
		String tmp = "";
		Iterator<String> ipAddr = Util.getLocalIpAddress();
		try {
			while(ipAddr.hasNext()) {
				tmp = tmp + ipAddr.next() + " ";
				if (ipAddr.hasNext()) {
					tmp = tmp + ", ";
				}
			}
		} catch (Exception e) {
			Log.w(TAG, "updateStatus() exception in IpAddress Iterator");
		}
		status_ip_address.setText(tmp);
		status_username.setText(Base.getUsername());
		status_tcp_port.setText(String.valueOf(Base.getDaemonPort()));
		
		if (Util.isDropbearDaemonRunning()) {
			Base.setDropbearDaemonStatus(Base.DAEMON_STATUS_STARTED);
		}
		switch (Base.getDropbearDaemonStatus()) {
		case Base.DAEMON_STATUS_STOPPING:
			btnStartStop.setEnabled(false);
			btnStartStop.setFocusable(false);
			btnStartStop.setText(R.string.act_main_busy);
			status_content.setText(R.string.act_main_stopping);
			break;

		case Base.DAEMON_STATUS_STARTING:
			btnStartStop.setEnabled(false);
			btnStartStop.setFocusable(false);
			btnStartStop.setText(R.string.act_main_busy);
			status_content.setText(R.string.act_main_starting);
			break;

		case Base.DAEMON_STATUS_STARTED:
			btnStartStop.setEnabled(true);
			btnStartStop.setFocusable(true);
			btnStartStop.setText(R.string.act_main_stop);
			status_content.setText(R.string.act_main_running);
			break;

		case Base.DAEMON_STATUS_STOPPED:
			btnStartStop.setEnabled(true);
			btnStartStop.setFocusable(true);
			btnStartStop.setText(R.string.act_main_start);
			status_content.setText(R.string.act_main_stopped);
			break;

		case Base.DAEMON_STATUS_UNKNOWN:
			btnStartStop.setEnabled(true);
			btnStartStop.setFocusable(true);
			btnStartStop.setText(R.string.act_main_start);
			status_content.setText(R.string.act_main_unknown);
			break;
		default:
			break;
		}
	}

	public void startDropbear() {

		doBindDaemonHandlerService(mDropbearDaemonHandlerService);
		if (!Util.checkPathToBinaries()) {
			if(Base.debug) {
				Log.v(TAG, "startDropbear bailing out: status was " + Base.getDropbearDaemonStatus() + ", changed to STOPPED(" + ")" );
			}
			Base.setDropbearDaemonStatus(Base.DAEMON_STATUS_STOPPED);
			mHandler.postDelayed(mUpdateUI, mUpdateUIdelay);
			Util.showMsg("Can't find dropbear binaries");
			return;
		}
		if (!Util.validateHostKeys()) {
			if(Base.debug) {
				Log.v(TAG, "Host keys not found");
			}
			Base.setDropbearDaemonStatus(Base.DAEMON_STATUS_STOPPED);
			mHandler.postDelayed(mUpdateUI, mUpdateUIdelay);
			Util.showMsg("Host keys not found");
			return;
		}
		if(Base.getDropbearDaemonStatus() <= Base.DAEMON_STATUS_STOPPED) {
			Base.setDropbearDaemonStatus(Base.DAEMON_STATUS_STARTING);
			if (Base.debug) {
				Log.d(TAG, "Status was STOPPED, now it's STARTING");
			}
			startService(mDropbearDaemonHandlerService);
			startLongRunningOperation();
		}
	}

	public void stopDropbear() {
		if (Base.debug) {
			Log.v(TAG, "stopDropbear() called. Current status = " + Base.getDropbearDaemonStatus() );
		}
		if ((Base.getDropbearDaemonStatus()==Base.DAEMON_STATUS_STARTED) ||
				Base.getDropbearDaemonStatus()==Base.DAEMON_STATUS_STARTING) {
			Base.setDropbearDaemonStatus(Base.DAEMON_STATUS_STOPPING);
			int pid = Util.getDropbearPidFromPidFile(Base.getDropbearPidFilePath());
			if(Base.debug) {
				Log.d(TAG, "stopDropbear() killing pid " + pid);
				Log.d(TAG, "dropbearDaemonStatus = Base.DAEMON_STATUS_STOPPING");
			}
			String cmd = "kill -2 " + pid;
//			Util.doRun(cmd, Base.runDaemonAsRoot(), mLogviewHandler);
			Util.doRun(cmd, Base.runDaemonAsRoot(), null);
			stopService(mDropbearDaemonHandlerService);
		}
		startLongRunningOperation();
		Util.releaseWakeLock();
		Util.releaseWifiLock();
	}

	@Override 
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override 
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.menu_settings:
			Intent p = new Intent(this, tk.tanguy.droidsshd.activity.Preferences.class);
			startActivity(p);
			return true;
		case R.id.menu_quit:
			Util.showMsg("Good bye...");
			this.finish();
			return true;
		case R.id.menu_about:
			Intent i = new Intent(this, tk.tanguy.droidsshd.activity.About.class);
			startActivity(i);
			return true;
		/*
		case R.id.menu_refreshui:
			if(Base.getDropbearDaemonStatus()==Base.DAEMON_STATUS_STARTING) { 
				Base.setDropbearDaemonStatus(Base.DAEMON_STATUS_STARTED);
			}
			if(Base.getDropbearDaemonStatus()==Base.DAEMON_STATUS_STOPPING) {
				Base.setDropbearDaemonStatus(Base.DAEMON_STATUS_STOPPED);
			}
			updateStatus();
			return true;
		*/
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	final Handler mHandler = new Handler();
	final Runnable mUpdateUI = new Runnable() {
		public void run() {
			updateStatus();
		}
	};

//	TODO - http://developer.android.com/resources/articles/painless-threading.html

	protected void startLongRunningOperation() {
		synchronized(this) {
			if (Base.debug) {
				Log.d(TAG, "startLongRunningOperation called");
			}
			
//			TODO - http://developer.android.com/resources/articles/timed-ui-updates.html
//			Runnable mUpdateTimeTask = new Runnable() {
//				public void run() {
//					final long start = mStartTime;
//					long millis = SystemClock.uptimeMillis() - start;
//					mHandler.postAtTime(this, start + (((minutes * 60) + seconds + 1) * 1000));
//			}
//			mHandler.removeCallbacks(mUpdateTimeTask);

			if(mMonitorDaemon!=null) {
				if(mMonitorDaemon.isAlive()) {
					mMonitorDaemon.extendLifetimeForAnother(System.currentTimeMillis()+2000);
				}
			} else {
				ReplicantThread mMonitorDaemon = new ReplicantThread(TAG, (System.currentTimeMillis()+2000), 600, mHandler, mUpdateUI, Base.debug);
				mMonitorDaemon.start();
			} 
		}
	}
	
/*	
 	final protected Handler mLogviewHandler = new Handler() {
		@Override
		public void handleMessage(Message msg){
//			mStdOut.append(msg.getData().getString("line"));
//			mLogView.postDelayed(new Runnable() {
//				public void run() {
//					mLogView.fullScroll(ScrollView.FOCUS_DOWN);
//				}
//			}, 200); 
		}
	};
*/

	// DAEMON HANDLER
	private ServiceConnection mDaemonHandlerConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			mBoundDaemonHandlerService = ((tk.tanguy.droidsshd.system.DroidSSHdService.DropbearDaemonHandlerBinder)service).getService();
			if(Base.debug) {
				Log.d(TAG, "onServiceConnected DroidSSHdService called");
				if (mBoundDaemonHandlerService==null){
					Log.d(TAG, "Failed to bind to DroidSSHdService (mBoundDaemonHandlerService is NULL)");
				} else {
					Log.d(TAG, "mBoundDaemonHandlerService = " + mBoundDaemonHandlerService.toString());
				}
			}
		}
		@Override
		public void onServiceDisconnected(ComponentName className) {
			mBoundDaemonHandlerService = null;
			if(Base.debug) {
				Log.d(TAG, "onServiceDisconnected called (mBoundDaemonHandlerService set to NULL)");
			}
		}
	};

	private void doBindDaemonHandlerService(Intent intent) {
		Base.setManualServiceStart(true);
		mDaemonHandlerIsBound = bindService(intent, mDaemonHandlerConnection, Context.BIND_AUTO_CREATE);
	}

	private void doUnbindDaemonHandlerService(Intent intent) {
		if (mDaemonHandlerIsBound) {
			unbindService(mDaemonHandlerConnection);
			mDaemonHandlerIsBound = false;
		}
	}

}


