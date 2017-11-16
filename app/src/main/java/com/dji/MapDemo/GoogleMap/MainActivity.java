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
import dji.common.mission.waypoint.WaypointDownloadProgress;
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

   private boolean isAdd = false;

    private double droneLocationLat = 181;
    private double droneLocationLng = 181;
    //
   private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
   private Marker droneMarker = null;
    private float altitude = 100.0f;
    private float mSpeed = 10.0f;

    private List<Waypoint> waypointList = new ArrayList<>();

    public  static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;

    @Override
    protected  void onResume()
    {
        super.onResume();
        initFlightController();
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
        removeListener();
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

         addListener();
    }

    private void addListener()
    {
        if(getWaypointMissionOperator() != null)
        {
            getWaypointMissionOperator().addListener(eventNotificationListener);
        }
    }

    private void removeListener()
    {
        if(getWaypointMissionOperator()!= null)
        {
            getWaypointMissionOperator().removeListener(eventNotificationListener);
        }

    }

    private WaypointMissionOperatorListener eventNotificationListener = new WaypointMissionOperatorListener()
    {
        @Override
        public void onDownloadUpdate(@NonNull WaypointMissionDownloadEvent downloadEvent)
        {

        }

        @Override
        public void onUploadUpdate(@NonNull WaypointMissionUploadEvent uploadEvent)
        {

        }

        @Override
        public void onExecutionUpdate(@NonNull WaypointMissionExecutionEvent executionEvent)
        {

        }

        @Override
        public void onExecutionStart()
        {

        }

        @Override
        public void onExecutionFinish(@Nullable DJIError djiError)
        {
            setResultToToast("Execution Finished" + (djiError == null ? "Success!" : djiError.getDescription()));
        }
    };
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
    }

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
               if(checkID == R.id.lowSpeed)
               {
                   mSpeed = 3.0f;
               }
               else if (checkID == R.id.MidSpeed)
               {
                   mSpeed = 5.0f;
               }
               else if (checkID == R.id.HighSpeed)
               {
                   mSpeed = 10.0f;
               }
            }
        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkID)
            {
                Log.d(TAG, "Select Action. Action");
                if(checkID == R.id.finishNone)
                {
                    mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;

                }
                else if (checkID == R.id.finishGoHome)
                {
                    mFinishedAction = WaypointMissionFinishedAction.GO_HOME;
                }

                else if (checkID == R.id.finishAutoLanding)
                {
                    mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                }

                else if(checkID == R.id.finishToFirst)
                {
                    mFinishedAction = WaypointMissionFinishedAction.GO_FIRST_WAYPOINT;
                }


            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkID)
            {
                Log.d(TAG, "Select HEading Finish");

                if(checkID == R.id.headingNext)
                {
                    mHeadingMode = WaypointMissionHeadingMode.AUTO;
                }

                else if (checkID == R.id.headingInitDirec)
                {
                    mHeadingMode = WaypointMissionHeadingMode.AUTO.USING_INITIAL_DIRECTION;
                }

                else if (checkID == R.id.headingRC)
                {
                    mHeadingMode = WaypointMissionHeadingMode.AUTO.CONTROL_BY_REMOTE_CONTROLLER;
                }

                else if (checkID == R.id.headingWP)
                {
                    mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                }
            }
        });

        new AlertDialog.Builder(this).setTitle("").setView(wayPointSettings)
                .setPositiveButton("Finish", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int id)
                    {
                        String altitudeString = wpAltitude_TV.getText().toString();
                        altitude = Integer.parseInt(nulltoIntegerDefault(altitudeString));
                        Log.e(TAG,"altitude "+altitude);
                        Log.e(TAG,"speed "+mSpeed);
                        Log.e(TAG, "mFinishedAction "+mFinishedAction);
                        Log.e(TAG, "mHeadingMode "+mHeadingMode);
                        configWayPointMission();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id)
                    {
                        dialog.cancel();
                    }
                }).create().show();

    }

    String nulltoIntegerDefault(String value)
    {
        if(!isIntValue(value)) value = "0";
        return value;
    }

    boolean isIntValue(String val)
    {
        try
        {
            val = val.replace(" ", "");
            Integer.parseInt(val);

        }
        catch (Exception e)
        {
            return false;
        }
        return true;
    }

   private void configWayPointMission()
   {
       if(waypointMissionBuilder == null)
       {
           waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                   .headingMode(mHeadingMode).autoFlightSpeed(mSpeed).maxFlightSpeed(mSpeed).flightPathMode(WaypointMissionFlightPathMode.NORMAL);
       }

       else
       {
           waypointMissionBuilder.finishedAction(mFinishedAction).headingMode(mHeadingMode).autoFlightSpeed(mSpeed).maxFlightSpeed(mSpeed)
                   .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
       }

       if(waypointMissionBuilder.getWaypointList().size() > 0)
       {
           for(int i = 0; i < waypointMissionBuilder.getWaypointList().size(); i++)
           {
               waypointMissionBuilder.getWaypointList().get(i).altitude = altitude;
           }

           setResultToToast("Set WayPoint Attitude Success");
       }

       DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
       if(error == null)
       {
           setResultToToast("loadWaypoint succeeded");
       }
       else
       {
           setResultToToast("loadWayPoint Failed" + error.getDescription());
       }

   }

   public WaypointMissionOperator getWaypointMissionOperator()
   {
       if (instance == null)
       {
           instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
       }

       return instance;
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
                     updateDroneLocation();
                }
            });
        }
    }

    private  void updateDroneLocation()
    {
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);

        //Create MArker Options Object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if(droneMarker != null)
                {
                    droneMarker.remove();
                }

                if(checkGpsCoordination(droneLocationLat, droneLocationLng))
                {
                    droneMarker = gMap.addMarker(markerOptions);
                }
            }
        });
    }

    private void cameraUpdate()
    {
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomLevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomLevel);
        gMap.moveCamera(cu);


    }

    public static  boolean checkGpsCoordination(double latitude, double longitude)
    {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude!=0f && longitude != 0f);
    }

    @Override
    public void onMapClick(LatLng point)
    {
        if(isAdd == true)
        {
            markWaypoint(point);
            Waypoint mWaypoint = new Waypoint(point.latitude, point.longitude, altitude);
            //Add waypoint to waypoint arraylist
            if(waypointMissionBuilder != null)
            {
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }

            else
            {
                waypointMissionBuilder = new WaypointMission.Builder();
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }

        }

        else
        {
            setResultToToast("Cannot Add Waypoint");

        }

    }

    private void markWaypoint(LatLng point)
    {
        //Create Marker Options
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));
        Marker marker = gMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);

    }

    private void uploadWayPointMission()
    {
        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError)
            {
               if (djiError == null)
               {
                   setResultToToast("Mission Uploaded Successfully");
               }
               else
               {
                   setResultToToast("Mission upload failed, error:" + djiError.getDescription() + "retrying......");
                   getWaypointMissionOperator().retryUploadMission(null);
               }
            }
        });

    }

    private void startWaypointMission()
    {
        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError)
            {
                setResultToToast("Mission Start: " + (djiError == null ? "Successfully" : djiError.getDescription()));

            }
        });
    }

    private void  stopWaypointMission()
    {
        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError)
            {
                setResultToToast("Mission Stop: " + (djiError == null ? "Successfully" : djiError.getDescription()));

            }
        });
    }

    private void enableDisableAdd()
    {
        if (isAdd == false) {
            isAdd = true;
            add.setText("Exit");
        }else{
            isAdd = false;
            add.setText("Add");
        }
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

            case R.id.locate:
            {
                updateDroneLocation();
                cameraUpdate(); // locate drone position
                break;
            }
            case R.id.add:
            {
                enableDisableAdd();
                break;
            }

            case R.id.clear:
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        gMap.clear();
                    }
                });

                waypointList.clear();
                waypointMissionBuilder.waypointList(waypointList);
                updateDroneLocation();
                break;
            }
            case R.id.upload:
            {
                uploadWayPointMission();
                break;
            }
            case R.id.start:
            {
                startWaypointMission();
                break;
            }
            case R.id.stop:
            {
                stopWaypointMission();
                break;

            }
            default:
                break;
        }
    }

}
