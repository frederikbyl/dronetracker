package com.wasp2;

import android.Manifest;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import com.parrot.arsdk.ARSDK;
import com.parrot.arsdk.arcommands.ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;
import com.wasp2.discovery.DroneDiscoverer;
import com.wasp2.drone.MiniDrone;
import com.wasp2.video.H264VideoView;
import com.wasp2.video.ImageReadyCallback;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Code based on https://github.com/Parrot-Developers/Samples/tree/master/Android/SDKSample/app
 *
 * Implemented for Parrot Mambo
 *
 * Steps:
 * 1. connect with drone
 * 2. stream camera
 * 3. bounding boxes for camera input on QR code or WASP
 * 4. make Move
 */

public class MainActivity extends AppCompatActivity implements DroneDiscoverer.Listener , ImageReadyCallback {

    private int errorX = 0;
    private int errorY = 0;
    private int previousX = 0;
    private int previousY = 0;
    private long lastDate=0;
    int incrementalErrorX = 0;
    int incrementalErrorY = 0;

    /** List of runtime permission we need. */
    private static final String[] PERMISSIONS_NEEDED = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };

    /** Code for permission request result handling. */
    private static final int REQUEST_CODE_PERMISSIONS_REQUEST = 1;

    static {
        ARSDK.loadSDKLibs();
    }

    public DroneDiscoverer mDroneDiscoverer;

    private MiniDrone mMiniDrone;
    private FloatingActionButton mStartHuntBt;
    private H264VideoView mVideoView;

    private final List<ARDiscoveryDeviceService> mDronesList = new ArrayList<>();


    //listener implementation
    @Override
    public void onDronesListUpdated(List<ARDiscoveryDeviceService> dronesList) {
        mDronesList.clear();
        mDronesList.addAll(dronesList);
        Log.i("DRONES", dronesList.toString());
        //mAdapter.notifyDataSetChanged();
        for(ARDiscoveryDeviceService service : dronesList) {
            Log.i("DRONES ",service.getName() + " on " + service.getNetworkType());
            if(mMiniDrone== null) {
                mMiniDrone = new MiniDrone(this, service);
                mMiniDrone.addListener(mMiniDroneListener);
                mMiniDrone.connect();
            }

        }

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mVideoView = (H264VideoView) findViewById(R.id.videoView);

        mVideoView.setImageDisplayCallBack(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mStartHuntBt = findViewById(R.id.fab);
        mStartHuntBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

              if(mMiniDrone!=null) {
                //  Log.i("TAKEOFF", mMiniDrone.getFlyingState().getValue());
                  switch (mMiniDrone.getFlyingState()) {
                      case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                          mMiniDrone.takeOff();
                          break;
                      case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                      case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                          mMiniDrone.land();
                          break;
                      default:
                  }
              }


            }

        });


        mDroneDiscoverer = new DroneDiscoverer(this);

        Set<String> permissionsToRequest = new HashSet<>();
        for (String permission : PERMISSIONS_NEEDED) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    Toast.makeText(this, "Please allow permission " + permission, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                } else {
                    permissionsToRequest.add(permission);
                }
            }
        }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[permissionsToRequest.size()]),
                    REQUEST_CODE_PERMISSIONS_REQUEST);
        }






    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        // setup the drone discoverer and register as listener
        mDroneDiscoverer.setup();
        mDroneDiscoverer.addListener(this);

        // start discovering
        mDroneDiscoverer.startDiscovering();
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        // clean the drone discoverer object
        mDroneDiscoverer.stopDiscovering();
        mDroneDiscoverer.cleanup();
        mDroneDiscoverer.removeListener(this);
    }


    private final MiniDrone.Listener mMiniDroneListener = new MiniDrone.Listener() {
        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state)
            {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    //mConnectionProgressDialog.dismiss();
                    break;

                case ARCONTROLLER_DEVICE_STATE_STOPPED:
                    // if the deviceController is stopped, go back to the previous activity
                    //mConnectionProgressDialog.dismiss();
                    //finish();
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onBatteryChargeChanged(int batteryPercentage) {
            //mBatteryLabel.setText(String.format("%d%%", batteryPercentage));
        }

        @Override
        public void onPilotingStateChanged(ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
            switch (state) {
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                    //mStartHuntBt.setText("Take off");
                    mStartHuntBt.setEnabled(true);
                   // mDownloadBt.setEnabled(true);
                    break;
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                    //mStartHuntBt.setText("Land");
                    mStartHuntBt.setEnabled(true);
                    //mDownloadBt.setEnabled(false);
                    break;
                default:
                    mStartHuntBt.setEnabled(false);
                    //mDownloadBt.setEnabled(false);
            }
        }

        @Override
        public void onPictureTaken(ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
            Log.i("PICTURE", "Picture has been taken");
        }

        @Override
        public void configureDecoder(ARControllerCodec codec) {
            mVideoView.configureDecoder(codec);
        }

        @Override
        public void onFrameReceived(ARFrame frame) {
            mVideoView.displayFrame(frame);
        }

        @Override
        public void onMatchingMediasFound(int nbMedias) {
            /*
            mDownloadProgressDialog.dismiss();

            mNbMaxDownload = nbMedias;
            mCurrentDownloadIndex = 1;

            if (nbMedias > 0) {
                mDownloadProgressDialog = new ProgressDialog(MiniDroneActivity.this, R.style.AppCompatAlertDialogStyle);
                mDownloadProgressDialog.setIndeterminate(false);
                mDownloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mDownloadProgressDialog.setMessage("Downloading medias");
                mDownloadProgressDialog.setMax(mNbMaxDownload * 100);
                mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);
                mDownloadProgressDialog.setProgress(0);
                mDownloadProgressDialog.setCancelable(false);
                mDownloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mMiniDrone.cancelGetLastFlightMedias();
                    }
                });
                mDownloadProgressDialog.show();
            }
            */
        }

        @Override
        public void onDownloadProgressed(String mediaName, int progress) {
            //mDownloadProgressDialog.setProgress(((mCurrentDownloadIndex - 1) * 100) + progress);
        }

        @Override
        public void onDownloadComplete(String mediaName) {
            /*
            mCurrentDownloadIndex++;
            mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);

            if (mCurrentDownloadIndex > mNbMaxDownload) {
                mDownloadProgressDialog.dismiss();
                mDownloadProgressDialog = null;
            }

             */
        }
    };

    public void drawImage(final Bitmap bitmap, final Rect rect) {


        Runnable thread = new Runnable()
        {
            public void run()
            {

                Log.i("DRAW: ", Long.toString(System.nanoTime()-lastDate));
                lastDate =System.nanoTime();
                int centerX = 640 / 2;
                int centerY = 368 / 2;


                if (rect != null) {

                    int distanceZ = rect.right - rect.left;
                    int centerRectX = (rect.right - rect.left) / 2 + rect.left;
                    int centerRectY = (rect.bottom - rect.top) / 2 + rect.top;
                    int diffX = -1 * (centerX - centerRectX);
                    int diffY = (centerY - centerRectY);
                    errorX = diffX;
                    errorY = diffY;

                    incrementalErrorX = Math.abs(previousX)-Math.abs(errorX);
                    incrementalErrorY = Math.abs(previousY)-Math.abs(errorY);

                    previousX = errorX;
                    previousY = errorY;
                    int moveX = -15;
                    int moveY = -15;

                    if(incrementalErrorX<0) {
                        moveX=-50;//incrementalErrorX/20;
                    }
                    if(incrementalErrorX<-50) {
                        moveX=-100;//incrementalErrorX/20;
                    }
                    if(incrementalErrorY<0) {
                        moveY=-30;//incrementalErrorY/20;
                    }


                    mMiniDrone.setFlag((byte) 1);
                    if (diffX < -100) {
                        Log.i("TURNING", "HARDLEFT");
                        mMiniDrone.setRoll((byte) moveX);
                        //Pause for 4 seconds


                    } else if (diffX > -100 && diffX < 0) {
                        Log.i("TURNING", "SOFTLEFT");
                        // mMiniDrone.setRoll((byte) diffX);
                        mMiniDrone.setRoll((byte) 0);

                    } else if (diffX < 100 && diffX > 0) {
                        Log.i("TURNING", "SOFTRIGHT");
                        mMiniDrone.setRoll((byte) 0);

                    } else if (diffX > 100) {
                        Log.i("TURNING", "HARDRIGHT");
                        mMiniDrone.setRoll((byte) -moveX);

                        Log.i("TURNING", "0");
                       // mMiniDrone.setRoll((byte) 0);


                    } else {
                        Log.i("TURNING", "0");
                        mMiniDrone.setRoll((byte) 0);
                    }


                    if (diffY < -30) {
                        mMiniDrone.setGaz((byte) moveY);
                    } else if (diffY < 0 && diffY > -30) {
                        mMiniDrone.setGaz((byte) diffY);
                    } else if (diffY < 30 && diffY > 0) {
                        mMiniDrone.setGaz((byte) diffY);
                    } else if (diffY > 30) {
                        mMiniDrone.setGaz((byte) -moveY);
                    } else {
                        mMiniDrone.setGaz((byte) 0);
                    }


                    if(distanceZ>100) {
                        mMiniDrone.setPitch((byte)-20);
                    } else if(distanceZ<50) {
                        mMiniDrone.setPitch((byte)30);
                    } else {
                        mMiniDrone.setPitch((byte)0);
                    }

                } else {

                    mMiniDrone.setFlag((byte) 1);
                    mMiniDrone.setRoll((byte) 0);
                    mMiniDrone.setGaz((byte) 0);
                }
            }
        };

        new Thread(thread).start();






        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                int centerX = 640 / 2;
                int centerY = 368 / 2;

                Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                //Bitmap mutableBitmap = Bitmap.createBitmap(640, 368, Bitmap.Config.ARGB_8888);

                Canvas canvas = new Canvas(mutableBitmap);
                ImageView imageview = findViewById(R.id.imageView);
                imageview.setImageBitmap(mutableBitmap);
                Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(5);


                if (rect != null) {
                    Paint paint2 = new Paint();
                    paint2.setColor(Color.GREEN);
                    paint2.setStyle(Paint.Style.STROKE);
                    paint2.setStrokeWidth(5);

                    Paint paint3 = new Paint();
                    paint3.setColor(Color.BLUE);
                    paint3.setStyle(Paint.Style.STROKE);
                    paint3.setStrokeWidth(1);
                    paint3.setTextSize(30);

                    int centerRectX = (rect.right - rect.left) / 2 + rect.left;
                    int centerRectY = (rect.bottom - rect.top) / 2 + rect.top;
                    canvas.drawCircle(centerRectX, centerRectY, 5, paint2);
                    canvas.drawCircle(centerRectX, centerRectY, 1, paint2);

                    canvas.drawRect(rect, paint2);
                    canvas.drawText(Integer.toString(incrementalErrorX), 20, 30, paint3);
                    canvas.drawText(Integer.toString(incrementalErrorY), 20, 60, paint3);


                    canvas.drawCircle(centerX, centerY, 5, paint);

                }
            }
        });


    }


}
