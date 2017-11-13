package com.dji.MapDemo.GoogleMap;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.common.error.DJIError;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;


public class MainActivity extends FragmentActivity implements View.OnClickListener, GoogleMap.OnMapClickListener, OnMapReadyCallback
{
    protected static final String TAG ="MainActivity";

    private  GoogleMap gMap;
    private  Button locate, add, clear;
    private Button config, upload, start, stop;

//    private boolean isAdd = false;
//
    private double droneLocationLat = 181;
    private double droneLocationLng = 181;
//
//    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
//    private Marker droneMarker = null;
//    private float altitude = 100.0f;
//    private float mSpeed = 10.0f;
//
//    private List<Waypoint> waypointList = new ArrayList<>();
//
//    public  static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
//    private WaypointMissionOperator instance;
//    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
//    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;

    @Override
    protected  void onResume()
    {
        super.onResume();
       // initFlightController();
    }

    @Override
    protected  void onPause()
    {
        Log.e(TAG, "onPause");
        super.onPause();
    }


    @Override
    protected void onDestroy()
    {
       unregisterReceiver(mReceiver);
        //removeListener();
        super.onDestroy();
    }

    // Return button response Function
    public  void onReturn(View view)
    {
        Log.d(TAG, "onReturn");
        this.finish();
    }

    private void setResultToToast(final String string)
    {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run()
            {
                Toast.makeText(MainActivity.this, string, Toast.LENGTH_SHORT).show();

            }
        });
    }

    private void initUI()
    {
        locate = (Button) findViewById(R.id.locate);
        add = (Button) findViewById(R.id.add);
        clear = (Button) findViewById(R.id.clear);
        config = (Button) findViewById(R.id.config);
        upload = (Button) findViewById(R.id.upload);
        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);


        locate.setOnClickListener(this);
        add.setOnClickListener(this);
        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        upload.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }
        setContentView(R.layout.activity_main);

        //Register Broadcaster
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIDemoApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        initUI();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

       // addListener();
    }
    protected BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            onProductConnectionChange();
        }
    };

    private  void onProductConnectionChange()
    {
        initFlightController();
       // loginAccount();
    }

   /* private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        setResultToToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }*/

   private void showSettingDialog()
   {
       LinearLayout wayPointSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);
       final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
       RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
       RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
       RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);

       speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
       {
           @Override
           public void onCheckedChanged(RadioGroup group, int checkID)
           {
//               if(checkID == R.id.lowSpeed)
//               {
//                   mSpeed = 3.0f;
//               }
//               else if (checkID == R.id.MidSpeed)
//               {
//                   mSpeed = 5.0f;
//               }
//               else if (checkID == R.id.HighSpeed)
//               {
//                   mSpeed = 10.0f;
//               }
           }
       });

       actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
       {
           @Override
           public void onCheckedChanged(RadioGroup group, int checkID)
           {
               Log.d(TAG, "Select Action. Action");

           }
       });

       heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
       {
           @Override
           public void onCheckedChanged(RadioGroup group, int checkID)
           {
               Log.d(TAG, "Select HEading Finish");
           }
       });

       new AlertDialog.Builder(this).setTitle("").setView(wayPointSettings)
               .setPositiveButton("Finish", new DialogInterface.OnClickListener()
               {
                   @Override
                   public void onClick(DialogInterface dialog, int id)
                   {

                   }
               })
               .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int id)
                   {

                   }
               }).create().show();

   }

   @Override
   public void onClick(View v)
   {
       switch (v.getId())
       {
           case R.id.config:
           {
               showSettingDialog();
               break;
           }
           default:
               break;
       }
   }

   @Override
   public  void onMapReady(GoogleMap googleMap)
   {
       // Initializing Map Object
       if(gMap == null)
       {
           gMap = googleMap;

       }
       setUpMap();
       LatLng myOffice = new LatLng(52.761219, -1.247104);
       gMap.addMarker(new MarkerOptions().position(myOffice).title("Marker in Sports Tech"));
       gMap.moveCamera(CameraUpdateFactory.newLatLng(myOffice));

   }

   private void setUpMap()
   {
       gMap.setOnMapClickListener(this);
   }

   private void initFlightController()
   {
       BaseProduct product = DJIDemoApplication.getProductInstance();
       if(product !=null && product.isConnected())
       {
           if(product instanceof Aircraft)
           {
               mFlightController = ((Aircraft) product).getFlightController();
           }
       }

       if(mFlightController != null)
       {
           mFlightController.setStateCallback(new FlightControllerState.Callback()
           {
               @Override
               public void onUpdate(@NonNull FlightControllerState djiFlightControllerState)
               {
                   droneLocationLat = djiFlightControllerState.getAircraftLocation().getLatitude();
                   droneLocationLng = djiFlightControllerState.getAircraftLocation().getLongitude();
                   upadteDroneLocation();
               }
           });
       }
   }

   @Override
    public void onMapClick(LatLng point)
   {

   }

}
