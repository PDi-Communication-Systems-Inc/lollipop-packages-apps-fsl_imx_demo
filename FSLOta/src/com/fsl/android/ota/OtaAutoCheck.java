package com.fsl.android.ota;

import android.content.Intent;
import android.app.Service;
import android.content.Context;
import android.util.Log;
import android.os.IBinder;
import android.os.Binder;
import java.net.MalformedURLException;
import android.content.BroadcastReceiver;
import java.lang.Runnable;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;

public class OtaAutoCheck extends Service implements OTAServerManager.OTAStateChangeListener {
    private OTAServerManager mOTAManager; 
    private Context mContext;
    private final String TAG = "OTA_AC";

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    /* Kick started by Settings OTA BOOT_COMPLETE ACTION */
    @Override 
    public void onCreate() {
	super.onCreate();
        this.registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_TIME_CHANGED));
	this.registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_DATE_CHANGED));
	this.registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));
	this.registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_BOOT_COMPLETED));

	/* After a reboot we need to restart any existing check */
        restoreAlarmIfNeeded();	
    }

   private void restoreAlarmIfNeeded() {
      PendingIntent pi = null;
      AlarmManager am = 
         (AlarmManager)(this.getSystemService( Context.ALARM_SERVICE )); 

      /* Create a new alarm with updated time */
      mContext = getBaseContext();
      try {
	   mOTAManager = new OTAServerManager(mContext);
	} catch (MalformedURLException e) {
	    mOTAManager = null;
	    Log.e(TAG, "meet not a mailformat URL... should not happens.");
	    e.printStackTrace();
	}

      BuildPropParser config = mOTAManager.getParser();
      String build_monthly_check = config.getProp(OTAServerConfig.monthly_tag);
      if (build_monthly_check != null) {
         long checkTime = Long.parseLong(build_monthly_check);

         Intent updInt = new Intent();
         updInt.setClassName("com.fsl.android.ota", "com.fsl.android.ota.OtaAutoCheck");
         updInt.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
         updInt.addFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);
         updInt.addFlags(Intent.FLAG_FROM_BACKGROUND);
         updInt.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
         updInt.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
         pi = PendingIntent.getService(this, 0, updInt, 0); 

         am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                         checkTime, 2592000000L, pi);

         }   
         else {
             Log.i(TAG, "No monthly check active for OTA");
         }   
   }
 
    @Override
    public void onStart(Intent i, int startId) { 
	Log.i(TAG, "Starting " + startId + " from onStart for OTA Auto Check");
	runCheckPrep(i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
       Log.i(TAG, "Starting " + startId + " from onStartCommand for OTA Auto Check");
       runCheckPrep(intent);
       // We want this service to continue running until it is explicitly
       // stopped, so return sticky.
       return START_STICKY;
    }
 
    private void runCheckPrep(Intent intent) {
        Log.i(TAG, "Starting OTA Auto Check");

        // Prepare OTA Manager
        mContext = getBaseContext();
        try {
           mOTAManager = new OTAServerManager(mContext);
        } catch (MalformedURLException e) {
            mOTAManager = null;
            Log.e(TAG, "meet not a mailformat URL... should not happens.");
            e.printStackTrace();
        }   
    
        // if setup went okay, start checking for updating
        if (mOTAManager != null) {
	   mOTAManager.setmListener(this);
           Log.i(TAG, "Spinning OTA Auto Check Update Thread");
           this.beat.run();
           Log.i(TAG, "Stopping OTA Auto Check Update Thread");
           this.stopSelf();
        }   
        else {
           Log.e(TAG, "OTA Manager failed to initialize");
        }
    }
 
    public Runnable beat = new Runnable() {
 
        public void run() {
           // Call OTA Update Mechanism
	    mOTAManager.startCheckingVersion();
        }
    };

    public void onStateOrProgress(int message, int error, Object info) {
	/* An upgrade is necessary, start the process automatically */
        if ((message == STATE_IN_CHECKED) && (error == NO_ERROR)) {
	    new Thread(new Runnable() {
			  public void run() {
			     mOTAManager.startDownloadUpgradePackage();
			  }
		       }).start();
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
       @Override
       public void onReceive(Context context, Intent intent) {
	  PendingIntent pi = null;
	  AlarmManager am = 
	     (AlarmManager)(context.getSystemService( Context.ALARM_SERVICE ));
          String action = intent.getAction();

	  /* Determine if an automated check is already 
	     scheduled */
          if ((Intent.ACTION_TIME_CHANGED.equals(action)) || 
	      (Intent.ACTION_TIMEZONE_CHANGED.equals(action)) || 
	      (Intent.ACTION_BOOT_COMPLETED.equals(action)) || 
	      (Intent.ACTION_DATE_CHANGED.equals(action))) {
             Log.d(TAG, "action: " + action);

	     pi = PendingIntent.getService(context, 0, 
		    new Intent("com.fsl.android.ota"),
		    PendingIntent.FLAG_NO_CREATE); 

	     /* if a check is already scheduled, cancel it, alarm 
		service does not expose a way to modify an alarm */
             if ((pi != null) && (am != null))
             {
                Log.d(TAG, "Alarm is already active so current alarm " + 
	  	      " will be cancelled");
		am.cancel(pi);
             }
	     else if (pi == null) {
		Log.e(TAG, "No alarm to cancel");
             }
	     else if (am != null) {
              	Log.e(TAG, "Alarm Service not available");
 	     }

	     /* Create a new alarm with updated time */
	     try {
                mOTAManager = new OTAServerManager(context);
             } catch (MalformedURLException e) {
                mOTAManager = null;
                Log.e(TAG, "meet not a mailformat URL... should not happens.");
                e.printStackTrace();
             }

	     BuildPropParser config = mOTAManager.getParser();
	     String build_monthly_check = config.getProp(OTAServerConfig.monthly_tag);
	     if (build_monthly_check != null) {
                long checkTime = Long.parseLong(build_monthly_check);

              Intent updInt = new Intent();
              updInt.setClassName("com.fsl.android.ota", "com.fsl.android.ota.OtaAutoCheck");
              updInt.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
	      updInt.addFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);
              updInt.addFlags(Intent.FLAG_FROM_BACKGROUND);
	      updInt.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
	      updInt.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
	      pi = PendingIntent.getService(context, 0, updInt, 0);

              am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                              checkTime, 2592000000L, pi);

	     }
	     else {
 	        Log.i(TAG, "No monthly check active for OTA");
             }   
          }
          else {
	     Log.e(TAG, "Failed to handle action: " + action);
          }    
       }
    };  
}
