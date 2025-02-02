/*
  Pebble_sd - a simple accelerometer based seizure detector that runs on a
  Pebble smart watch (http://getpebble.com).

  See http://openseizuredetector.org for more information.

  Copyright Graham Jones, 2015.

  This file is part of pebble_sd.

  Pebble_sd is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.
  
  Pebble_sd is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.
  
  You should have received a copy of the GNU General Public License
  along with pebble_sd.  If not, see <http://www.gnu.org/licenses/>.

*/

package uk.org.openseizuredetector;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

//MPAndroidChart
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.utils.ValueFormatter;
import com.rohitss.uceh.UCEHandler;

public class MainActivity extends AppCompatActivity {
    static final String TAG = "MainActivity";
    private int okColour = Color.BLUE;
    private int warnColour = Color.MAGENTA;
    private int alarmColour = Color.RED;
    private int okTextColour = Color.WHITE;
    private int warnTextColour = Color.WHITE;
    private int alarmTextColour = Color.BLACK;
    private OsdUtil mUtil;
    private SdServiceConnection mConnection;
    private Menu mOptionsMenu;

    private Intent sdServerIntent;

    final Handler serverStatusHandler = new Handler();
    Messenger messenger = new Messenger(new ResponseHandler());
    Timer mUiTimer;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG,"onCreate()");

        // Set our custom uncaught exception handler to report issues.
        //Thread.setDefaultUncaughtExceptionHandler(new OsdUncaughtExceptionHandler(MainActivity.this));
        new UCEHandler.Builder(this)
                .addCommaSeparatedEmailAddresses("crashreports@openseizuredetector.org.uk,")
                .build();

        //int i = 5/0;  // Force exception to test handler.
        mUtil = new OsdUtil(this,serverStatusHandler);
        mConnection = new SdServiceConnection(this);
        mUtil.writeToSysLogFile("");
        mUtil.writeToSysLogFile("* MainActivity Started     *");
        mUtil.writeToSysLogFile("MainActivity.onCreate()");

        // Initialise the User Interface
        setContentView(R.layout.main);

	/* Force display of overflow menu - from stackoverflow
     * "how to force use of..."
	 */
        try {
            Log.v(TAG, "trying menubar fiddle...");
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField =
                    ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                Log.v(TAG, "menuKeyField is not null - configuring....");
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            } else {
                Log.v(TAG, "menuKeyField is null - doing nothing...");
            }
        } catch (Exception e) {
            Log.v(TAG, "menubar fiddle exception: " + e.toString());
        }

        // Force the screen to stay on when the app is running
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Deal with the 'AcceptAlarm Button'
        Button button = (Button) findViewById(R.id.acceptAlarmButton);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.v(TAG, "acceptAlarmButton.onClick()");
                if (mConnection.mBound) {
                    if ((mConnection.mSdServer.mSmsTimer != null)
                            && (mConnection.mSdServer.mSmsTimer.mTimeLeft > 0 )){
                        Log.v(TAG, "acceptAlarmButton.onClick() - Stopping SMS Timer");
                        mUtil.showToast(getString(R.string.SMSAlarmCancelledMsg));
                        mConnection.mSdServer.stopSmsTimer();
                    }
                    else {
                        Log.v(TAG, "acceptAlarmButton.onClick() - Accepting Alarm");
                        mConnection.mSdServer.acceptAlarm();
                        }
                }
            }
        });

        // Deal with the 'Cancel Audible Button'
        button = (Button) findViewById(R.id.cancelAudibleButton);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.v(TAG, "cancelAudibleButton.onClick()");
                if (mConnection.mBound) {
                    mConnection.mSdServer.cancelAudible();
                }
            }
        });


    }

    /**
     * Create Action Bar
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "onCreateOptionsMenu()");
        getMenuInflater().inflate(R.menu.main_activity_actions, menu);
        //mOptionsMenu = menu;
        //if (mConnection.mSdServer.mSdDataSourceName != "Pebble") {
        //    Log.v(TAG,"Disabling Pebble Specific Menu Items");
        //    menu.findItem(R.id.action_instal_watch_app).setEnabled(false);
        //    menu.findItem(R.id.action_launch_pebble_app).setEnabled(false);
        //}
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "onOptionsItemSelected() :  " + item.getItemId() + " selected");
        switch (item.getItemId()) {
            /*case R.id.action_launch_pebble_app:
                Log.i(TAG, "action_launch_pebble_app");
                mConnection.mSdServer.mSdDataSource.startPebbleApp();
                return true;
                */
            case R.id.action_install_watch_app:
                Log.i(TAG, "action_install_watch_app");
                mConnection.mSdServer.mSdDataSource.installWatchApp();
                return true;

        case R.id.action_accept_alarm:
                Log.i(TAG, "action_accept_alarm");
                if (mConnection.mBound) {
                    mConnection.mSdServer.acceptAlarm();
                }
                return true;
            case R.id.action_start_stop:
                // Respond to the start/stop server menu item.
                Log.i(TAG, "action_sart_stop");
                if (mConnection.mBound) {
                    Log.i(TAG, "Stopping Server");
                    mUtil.unbindFromServer(this, mConnection);
                    stopServer();
                } else {
                    Log.i(TAG, "Starting Server");
                    startServer();
                    // and bind to it so we can see its data
                    Log.i(TAG, "Binding to Server");
                    mUtil.bindToServer(this, mConnection);
                }
                return true;
            /* fault beep test does not work with fault timer, so disable test option.
            case R.id.action_test_fault_beep:
                Log.i(TAG, "action_test_fault_beep");
                if (mConnection.mBound) {
                    mConnection.mSdServer.faultWarningBeep();
                }
                return true;
                */
            case R.id.action_test_alarm_beep:
                Log.i(TAG, "action_test_alarm_beep");
                if (mConnection.mBound) {
                    mConnection.mSdServer.alarmBeep();
                }
                return true;
            case R.id.action_test_warning_beep:
                Log.i(TAG, "action_test_warning_beep");
                if (mConnection.mBound) {
                    mConnection.mSdServer.warningBeep();
                }
                return true;
            case R.id.action_test_sms_alarm:
                Log.i(TAG, "action_test_sms_alarm");
                if (mConnection.mBound) {
                    mConnection.mSdServer.sendSMSAlarm();
                }
                return true;
            case R.id.action_test_phone_alarm:
                Log.i(TAG, "action_test_phone_alarm");
                if (mConnection.mBound) {
                    mConnection.mSdServer.sendPhoneAlarm();
                }
                return true;
            case R.id.action_export:
                Log.i(TAG, "action_export");
                try {
                    Intent i = new Intent(
                            MainActivity.this,
                            DBQueryActivity.class);
                    this.startActivity(i);
                } catch (Exception ex) {
                    Log.i(TAG, "exception starting export activity " + ex.toString());
                }
                return true;
            case R.id.action_logs:
                Log.i(TAG, "action_logs");
                try {
                    String url = "http://"
                            + mUtil.getLocalIpAddress()
                            + ":8080/logfiles.html";
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                    //Intent prefsIntent = new Intent(
                    //        MainActivity.this,
                    //        LogManagerActivity.class);
                    //this.startActivity(prefsIntent);
                } catch (Exception ex) {
                    Log.i(TAG, "exception starting log manager activity " + ex.toString());
                }
                return true;
            case R.id.action_logmanager:
                Log.i(TAG, "action_logmanager");
                try {
                    Intent intent = new Intent(
                            MainActivity.this,
                            LogManagerActivity.class);
                    this.startActivity(intent);
                } catch (Exception ex) {
                    Log.i(TAG, "exception starting log manager activity " + ex.toString());
                }
                return true;
            case R.id.action_settings:
                Log.i(TAG, "action_settings");
                try {
                    Intent prefsIntent = new Intent(
                            MainActivity.this,
                            PrefActivity.class);
                    this.startActivity(prefsIntent);
                } catch (Exception ex) {
                    Log.i(TAG, "exception starting settings activity " + ex.toString());
                }
                return true;
            case R.id.action_about:
                Log.i(TAG, "action_about");
                showAbout();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG,"onStart()");
        mUtil.writeToSysLogFile("MainActivity.onStart()");
        SharedPreferences SP = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());
        boolean audibleAlarm = SP.getBoolean("AudibleAlarm", true);
        Log.v(TAG, "onStart - auidbleAlarm = " + audibleAlarm);

        TextView tv;
        tv = (TextView) findViewById(R.id.versionTv);
        String versionName = mUtil.getAppVersionName();
        tv.setText(getString(R.string.AppTitleText) + " " + versionName);

        if (mUtil.isServerRunning()) {
            mUtil.writeToSysLogFile("MainActivity.onStart - Binding to Server");
            mUtil.bindToServer(this, mConnection);
        } else {
            Log.i(TAG,"onStart() - Server Not Running");
            mUtil.writeToSysLogFile("MainActivity.onStart - Server Not Running");
        }
        // start timer to refresh user interface every second.
        mUiTimer = new Timer();
        mUiTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateServerStatus();
            }
        }, 0, 1000);


    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG,"onStop() - unbinding from server");
        mUtil.writeToSysLogFile("MainActivity.onStop()");
        mUtil.unbindFromServer(this, mConnection);
        mUiTimer.cancel();
    }


    private void startServer() {
        mUtil.writeToSysLogFile("MainActivity.startServer()");
        Log.i(TAG, "startServer(): starting Server...");
        mUtil.startServer();
        // Change the action bar icon to show the option to stop the service.
        if (mOptionsMenu != null) {
            Log.v(TAG, "Changing menu icons");
            MenuItem menuItem = mOptionsMenu.findItem(R.id.action_start_stop);
            menuItem.setIcon(R.drawable.stop_server);
            menuItem.setTitle(R.string.StopServerTitle);
        } else {
            Log.v(TAG, "mOptionsMenu is null - not changing icons!");
        }
    }

    private void stopServer() {
        mUtil.writeToSysLogFile("MainActivity.stopServer()");
        Log.i(TAG, "stopServer(): stopping Server...");
        mUtil.stopServer();
        // Change the action bar icon to show the option to start the service.
        if (mOptionsMenu != null) {
            Log.v(TAG, "Changing action bar icons");
            mOptionsMenu.findItem(R.id.action_start_stop).setIcon(R.drawable.start_server);
            mOptionsMenu.findItem(R.id.action_start_stop).setTitle(R.string.StartServerTitle);
        } else {
            Log.v(TAG, "mOptionsMenu is null, not changing icons!");
        }
    }


    /*
     * updateServerStatus - called by the mUiTimer timer periodically.
     * requests the ui to be updated by calling serverStatusRunnable.
     */
    private void updateServerStatus() {
        serverStatusHandler.post(serverStatusRunnable);
    }

    /*
     * serverStatusRunnable - called by serverStatus - updates the
     * user interface to reflect the current status received from the server.
     */
    final Runnable serverStatusRunnable = new Runnable() {
        public void run() {
            Log.v(TAG, "serverStatusRunnable()");

            TextView tv;
            if (mUtil.isServerRunning()) {
                tv = (TextView) findViewById(R.id.serverStatusTv);
                if (mConnection.mBound)
                    tv.setText(getString(R.string.ServerRunningOK) + getString(R.string.DataSource) + " = " + mConnection.mSdServer.mSdDataSourceName);
                tv.setBackgroundColor(okColour);
                tv.setTextColor(okTextColour);
                tv = (TextView) findViewById(R.id.serverIpTv);
                tv.setText(getString(R.string.AccessServerAt)+" http://"
                        + mUtil.getLocalIpAddress()
                        + ":8080");
                tv.setBackgroundColor(okColour);
                tv.setTextColor(okTextColour);
            } else {
                tv = (TextView) findViewById(R.id.serverStatusTv);
                tv.setText(R.string.ServerStopped);
                tv.setBackgroundColor(warnColour);
                tv.setTextColor(warnTextColour);
                tv = (TextView) findViewById(R.id.serverIpTv);
                tv.setText("--");
                tv.setBackgroundColor(warnColour);
                tv.setTextColor(warnTextColour);
            }


            try {
                if (mConnection.mBound) {
                    tv = (TextView) findViewById(R.id.alarmTv);
                    if ((mConnection.mSdServer.mSdData.alarmState == 0)
                            && !mConnection.mSdServer.mSdData.alarmStanding
                            && !mConnection.mSdServer.mSdData.fallAlarmStanding) {
                        tv.setText(getString(R.string.okBtnTxt));
                        tv.setBackgroundColor(okColour);
                        tv.setTextColor(okTextColour);
                    }
                    if ((mConnection.mSdServer.mSdData.alarmState == 1)
                            && !mConnection.mSdServer.mSdData.alarmStanding
                            && !mConnection.mSdServer.mSdData.fallAlarmStanding) {
                        tv.setText(R.string.Warning);
                        tv.setBackgroundColor(warnColour);
                        tv.setTextColor(warnTextColour);
                    }
                    if (mConnection.mSdServer.mSdData.alarmState == 6) {
                        tv.setText(R.string.Mute);
                        tv.setBackgroundColor(warnColour);
                        tv.setTextColor(warnTextColour);
                    }
                    if (mConnection.mSdServer.mSdData.alarmStanding) {
                        tv.setText(R.string.Alarm);
                        tv.setBackgroundColor(alarmColour);
                        tv.setTextColor(alarmTextColour);
                    }
                    if (mConnection.mSdServer.mSdData.fallAlarmStanding) {
                        tv.setText(R.string.Fall);
                        tv.setBackgroundColor(alarmColour);
                        tv.setTextColor(alarmTextColour);
                    }

                    tv = (TextView) findViewById(R.id.pebTimeTv);
                    tv.setText(mConnection.mSdServer.mSdData.dataTime.format("%H:%M:%S"));
                    tv.setBackgroundColor(okColour);
                    tv.setTextColor(okTextColour);

                    // Pebble Connected Phrase - use for HR if active instead.
                    tv = (TextView) findViewById(R.id.pebbleTv);
                    if (mConnection.mSdServer.mSdData.mHRAlarmActive) {
                        tv.setText(getString(R.string.HR_Equals) + mConnection.mSdServer.mSdData.mHR);
                        if (mConnection.mSdServer.mSdData.mHRAlarmStanding) {
                            tv.setBackgroundColor(alarmColour);
                            tv.setTextColor(alarmTextColour);
                        } else if (mConnection.mSdServer.mSdData.mHRFaultStanding) {
                            tv.setBackgroundColor(warnColour);
                            tv.setTextColor(warnTextColour);
                        } else {
                            tv.setBackgroundColor(okColour);
                            tv.setTextColor(okTextColour);
                        }
                    } else {
                        if (mConnection.mSdServer.mSdData.watchConnected) {
                            tv.setText(R.string.HRAlarmOff);
                            tv.setBackgroundColor(okColour);
                            tv.setTextColor(okTextColour);

                        } else {
                            tv.setText(getString(R.string.WatchNotConnected));
                            tv.setBackgroundColor(warnColour);
                            tv.setTextColor(warnTextColour);
                        }
                    }
                    tv = (TextView) findViewById(R.id.appTv);
                    if (mConnection.mSdServer.mSdData.watchAppRunning) {
                        tv.setText(R.string.WatchAppOK);
                        tv.setBackgroundColor(okColour);
                        tv.setTextColor(okTextColour);
                    } else {
                        tv.setText(R.string.WatchAppNotRunning);
                        tv.setBackgroundColor(warnColour);
                        tv.setTextColor(warnTextColour);
                    }
                    tv = (TextView) findViewById(R.id.battTv);
                    tv.setText(getString(R.string.WatchBatteryEquals) + String.valueOf(mConnection.mSdServer.mSdData.batteryPc) + "%");
                    if (mConnection.mSdServer.mSdData.batteryPc <= 10) {
                        tv.setBackgroundColor(alarmColour);
                        tv.setTextColor(alarmTextColour);
                    }
                    if (mConnection.mSdServer.mSdData.batteryPc > 10) {
                        tv.setBackgroundColor(warnColour);
                        tv.setTextColor(warnTextColour);
                    }
                    if (mConnection.mSdServer.mSdData.batteryPc >= 20) {
                        tv.setBackgroundColor(okColour);
                        tv.setTextColor(okTextColour);
                    }
                    // Set ProgressBars to show margin to alarm.
                    long powerPc;
                    if (mConnection.mSdServer.mSdData.alarmThresh != 0)
                        powerPc = mConnection.mSdServer.mSdData.roiPower * 100 /
                                mConnection.mSdServer.mSdData.alarmThresh;
                    else
                        powerPc = 0;

                    long specPc;
                    if (mConnection.mSdServer.mSdData.specPower != 0 &&
                            mConnection.mSdServer.mSdData.alarmRatioThresh != 0)
                        specPc = 100 * (mConnection.mSdServer.mSdData.roiPower * 10 /
                                mConnection.mSdServer.mSdData.specPower) /
                                mConnection.mSdServer.mSdData.alarmRatioThresh;
                    else
                        specPc = 0;

                    long specRatio;
                    if (mConnection.mSdServer.mSdData.specPower != 0) {
                        specRatio = 10 * mConnection.mSdServer.mSdData.roiPower /
                                mConnection.mSdServer.mSdData.specPower;
                    } else
                        specRatio = 0;

                    ((TextView) findViewById(R.id.powerTv)).setText(getString(R.string.PowerEquals) + mConnection.mSdServer.mSdData.roiPower +
                            " ("+ getString(R.string.Threshold) + "=" + mConnection.mSdServer.mSdData.alarmThresh + ")");
                    ((TextView) findViewById(R.id.spectrumTv)).setText(getString(R.string.SpectrumRatioEquals) + specRatio +
                            " ("+ getString(R.string.Threshold) + "=" + mConnection.mSdServer.mSdData.alarmRatioThresh + ")");

                    ProgressBar pb;
                    Drawable pbDrawable;
                    pb = ((ProgressBar) findViewById(R.id.powerProgressBar));
                    pb.setMax(100);
                    pb.setProgress((int) powerPc);
                    pbDrawable = getResources().getDrawable(R.drawable.progress_bar_blue);
                    if (powerPc > 75)
                        pbDrawable = getResources().getDrawable(R.drawable.progress_bar_yellow);
                    if (powerPc > 100)
                        pbDrawable = getResources().getDrawable(R.drawable.progress_bar_red);

                    //pb.getProgressDrawable().setColorFilter(colour, PorterDuff.Mode.SRC_IN);

                    pb.setProgressDrawable(pbDrawable);

                    pb = ((ProgressBar) findViewById(R.id.spectrumProgressBar));
                    pb.setMax(100);
                    pb.setProgress((int) specPc);
                    pbDrawable = getResources().getDrawable(R.drawable.progress_bar_blue);
                    if (specPc > 75)
                        pbDrawable = getResources().getDrawable(R.drawable.progress_bar_yellow);
                    if (specPc > 100)
                        pbDrawable = getResources().getDrawable(R.drawable.progress_bar_red);
                    //pb.getProgressDrawable().setColorFilter(colour, PorterDuff.Mode.SRC_IN);
                    pb.setProgressDrawable(pbDrawable);


                    // Fault Conditions - We override the values in the UI because we do not know
                    // if the stored ones are correct or not with a fault present.
                    if ((mConnection.mSdServer.mSdData.alarmState == 4) ||
                            (mConnection.mSdServer.mSdData.alarmState == 7)) {
                        tv = (TextView) findViewById(R.id.alarmTv);
                        if (mConnection.mSdServer.mSdData.alarmState == 4) {
                            tv.setText(R.string.Fault);
                            tv.setBackgroundColor(warnColour);
                            tv.setTextColor(warnTextColour);
                        }
                        if (mConnection.mSdServer.mSdData.alarmState == 7) {
                            tv.setText(R.string.NetFault);
                            tv.setBackgroundColor(warnColour);
                            tv.setTextColor(warnTextColour);
                        }
                        tv = (TextView) findViewById(R.id.pebTimeTv);
                        tv.setText(mConnection.mSdServer.mSdData.dataTime.format("%H:%M:%S"));
                        tv.setBackgroundColor(okColour);
                        tv.setTextColor(okTextColour);

                        tv = (TextView) findViewById(R.id.pebTimeTv);
                        tv.setText("--:--:--");
                        tv.setBackgroundColor(warnColour);
                        tv.setTextColor(warnTextColour);

                        tv = (TextView) findViewById(R.id.pebbleTv);
                        tv.setText(getString(R.string.HR_Equals)+"---");
                        tv.setBackgroundColor(warnColour);
                        tv.setTextColor(warnTextColour);

                        tv = (TextView) findViewById(R.id.appTv);
                        tv.setText(getString(R.string.WatchApp)+" ----");
                        tv.setBackgroundColor(warnColour);
                        tv.setTextColor(warnTextColour);

                        tv = (TextView) findViewById(R.id.battTv);
                        tv.setText(getString(R.string.WatchBatteryEquals)+" ---%");
                        tv.setBackgroundColor(warnColour);
                        tv.setTextColor(warnTextColour);
                    }

                } else {   // Not bound to server
                    tv = (TextView) findViewById(R.id.alarmTv);
                    tv.setText(R.string.Dashes);
                    tv.setBackgroundColor(warnColour);
                    tv.setTextColor(warnTextColour);
                    tv = (TextView) findViewById(R.id.pebTimeTv);
                    tv.setText(mConnection.mSdServer.mSdData.dataTime.format("%H:%M:%S"));
                    tv.setBackgroundColor(okColour);
                    tv.setTextColor(okTextColour);

                    tv = (TextView) findViewById(R.id.pebTimeTv);
                    tv.setText("--:--:--");
                    tv.setBackgroundColor(warnColour);
                    tv.setTextColor(warnTextColour);

                    tv = (TextView) findViewById(R.id.pebbleTv);
                    tv.setText(getString(R.string.HR_Equals)+"---");
                    tv.setBackgroundColor(warnColour);
                    tv.setTextColor(warnTextColour);

                    tv = (TextView) findViewById(R.id.appTv);
                    tv.setText(getString(R.string.WatchApp)+" -----");
                    tv.setBackgroundColor(warnColour);
                    tv.setTextColor(warnTextColour);

                    tv = (TextView) findViewById(R.id.battTv);
                    tv.setText(getString(R.string.WatchBatteryEquals)+" ---%");
                    tv.setBackgroundColor(warnColour);
                    tv.setTextColor(warnTextColour);
                }
            } catch (Exception e) {
                Log.e(TAG, "ServerStatusRunnable: Exception - ");
                e.printStackTrace();
            }

            // deal with latch alarms button
            Button acceptAlarmButton = (Button) findViewById(R.id.acceptAlarmButton);

            if (mConnection.mBound) {
                if ((mConnection.mSdServer.mSmsTimer != null)
                        && (mConnection.mSdServer.mSmsTimer.mTimeLeft > 0)) {
                    acceptAlarmButton.setText(getString(R.string.SMSWillBeSentIn) + " " +
                            mConnection.mSdServer.mSmsTimer.mTimeLeft / 1000 +
                            " s - "+getString(R.string.Cancel));
                    acceptAlarmButton.setBackgroundColor(alarmColour);
                    acceptAlarmButton.setEnabled(true);
                } else {
                    acceptAlarmButton.setText(R.string.AcceptAlarm);
                    acceptAlarmButton.setBackgroundColor(Color.DKGRAY);
                    if (mConnection.mBound)
                        if ((mConnection.mSdServer.isLatchAlarms())
                                || mConnection.mSdServer.mSdData.mFallActive) {
                            acceptAlarmButton.setEnabled(true);
                        } else {
                            acceptAlarmButton.setEnabled(false);
                        }
                }
            } else {
                acceptAlarmButton.setText(getString(R.string.AcceptAlarm));
                acceptAlarmButton.setBackgroundColor(Color.DKGRAY);
                acceptAlarmButton.setEnabled(false);
            }

            // Deal with Cancel Audible button
            Button cancelAudibleButton =
                    (Button) findViewById(R.id.cancelAudibleButton);
            if (mConnection.mBound)
                if (mConnection.mSdServer.isAudibleCancelled()) {
                    cancelAudibleButton.setText(getString(R.string.AudibleAlarmsCancelledFor)
                            + mConnection.mSdServer.
                            cancelAudibleTimeRemaining()
                            + " sec."
                            + getString(R.string.PressToReEnable));
                } else {
                    if (mConnection.mSdServer.mAudibleAlarm) {
                        cancelAudibleButton.setText(R.string.CancelAudibleAlarms);
                    } else {
                        cancelAudibleButton.setText(R.string.AudibleAlarmsOff);
                    }
                }

            ////////////////////////////////////////////////////////////
            // Produce graph
            BarChart mChart = (BarChart) findViewById(R.id.chart1);
            mChart.setDrawBarShadow(false);
            mChart.setNoDataTextDescription("You need to provide data for the chart.");
            mChart.setDescription("");

            // X and Y Values
            ArrayList<String> xVals = new ArrayList<String>();
            ArrayList<BarEntry> yBarVals = new ArrayList<BarEntry>();
            for (int i = 0; i < 10; i++) {
                xVals.add(i+"-"+(i+1)+" Hz");
                if (mConnection.mSdServer != null) {
                    yBarVals.add(new BarEntry(mConnection.mSdServer.mSdData.simpleSpec[i], i));
                }
                else {
                    yBarVals.add(new BarEntry(i,i));
                }
            }

            // create a dataset and give it a type
            BarDataSet barDataSet = new BarDataSet(yBarVals,"Spectrum");
            try {
                int[] barColours = new int[10];
                for (int i = 0; i < 10; i++) {
                    if ((i < mConnection.mSdServer.mSdData.alarmFreqMin) ||
                            (i > mConnection.mSdServer.mSdData.alarmFreqMax)) {
                        barColours[i] = Color.GRAY;
                    } else {
                        barColours[i] = Color.RED;
                    }
                }
                barDataSet.setColors(barColours);
            } catch (NullPointerException e){
                Log.e(TAG,"Null pointer exception setting bar colours");
            }
            barDataSet.setBarSpacePercent(20f);
            barDataSet.setBarShadowColor(Color.WHITE);
            BarData barData = new BarData(xVals,barDataSet);
            barData.setValueFormatter(new ValueFormatter() {
                                          @Override
                                          public String getFormattedValue(float v) {
                                              DecimalFormat format = new DecimalFormat("####");
                                              return format.format(v);
                                          }
                                      });
                    mChart.setData(barData);

            // format the axes
            XAxis xAxis = mChart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setTextSize(10f);
            xAxis.setDrawAxisLine(true);
            xAxis.setDrawLabels(true);
            // Note:  the default text colour is BLACK, so does not show up on black background!!!
            //  This took a lot of finding....
            xAxis.setTextColor(Color.WHITE);
            xAxis.setDrawGridLines(false);

            YAxis yAxis = mChart.getAxisLeft();
            yAxis.setAxisMinValue(0f);
            yAxis.setAxisMaxValue(3000f);
            yAxis.setDrawGridLines(true);
            yAxis.setDrawLabels(true);
            yAxis.setTextColor(Color.WHITE);
            yAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float v) {
                    DecimalFormat format = new DecimalFormat("#####");
                    return format.format(v);
                }
            });

            YAxis yAxis2 = mChart.getAxisRight();
            yAxis2.setDrawGridLines(false);

            try {
                mChart.getLegend().setEnabled(false);
            } catch (NullPointerException e) {
                Log.e(TAG,"Null Pointer Exception setting legend");
            }

            mChart.invalidate();
        }
    };


    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG,"onPause()");
        mUtil.writeToSysLogFile("MainActivity.onPause()");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG,"onResume()");
        mUtil.writeToSysLogFile("MainActivity.onResume()");
    }


    private void showAbout() {
        mUtil.writeToSysLogFile("MainActivity.showAbout()");
        View aboutView = getLayoutInflater().inflate(R.layout.about_layout, null, false);
        String versionName = mUtil.getAppVersionName();
        Log.i(TAG, "showAbout() - version name = " + versionName);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.icon_24x24);
        builder.setTitle("OpenSeizureDetector V" + versionName);
        builder.setView(aboutView);
        builder.create();
        builder.show();
    }

    static class ResponseHandler extends Handler {
        @Override
        public void handleMessage(Message message) {
            Log.i(TAG, "Message=" + message.toString());
        }
     }

}
