package com.dji.MapDemo.GoogleMap;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewDebug;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import dji.common.battery.BatteryState;
import dji.common.flightcontroller.BatteryThresholdBehavior;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.internal.util.Util;
import dji.midware.data.model.P3.DataFlycDownloadWayPointMissionMsg;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.common.error.DJIError;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.thirdparty.afinal.utils.Utils;

public class MainActivity extends FragmentActivity implements View.OnClickListener, GoogleMap.OnMapClickListener, OnMapReadyCallback {

    protected static final String TAG = "GSDemoActivity";
    // See comments on calcLongitudeOffset function.
    private static final double ONE_METER_OFFSET = 0.00000899322;

    // Google Map Object
    private GoogleMap gMap;

    // Coordinate for the current position retrieved from the tablet
    // Coordinate is set as a home position
    private double mHomeLatitude;
    private double mHomeLongitude;

    // Buttons on the app
    private Button home, locate, add, clear;
    private Button config, upload, start, stop;
    private Button polygon;
    private Button circle;
    private Button goHome;

    // check to add waypoints or not
    private boolean isAdd = false;

    // Drone coordinates
    private double droneLocationLat = 181, droneLocationLng = 181;
    ArrayList<Marker> markers = new ArrayList<>();
    private Marker droneMarker = null;

   // Initialise speed and altitude of aircraft
    private float altitude = 100.0f;
    private float mSpeed = 10.0f;

    // Used to draw shapes on the map
    Polygon shape;

    // Home point for the Drone
    private LatLng homeBase;

    // Lists to store waypoints for missions
    private List<Waypoint> waypointList = new ArrayList<>();
    private List<Waypoint> coordinateWaypointList = new ArrayList<>();
    private List<Waypoint> circularWaypointList = new ArrayList<>();

    // DJI Mission Configuration classes
    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;
    private FusedLocationProviderClient mFusedLocationClient;

    // Fields for retrieved battery State.
    private int batteryChargeRemaining;
    private int batteryChargeInPercent;
    private int batteryVoltage;
    private int batteryCurrent;
    private float batteryTemp;

    // UI Elements for Battery State
    private TextView mBatteryChargeRemaining;
    private TextView mBatteryChargeInPercent;
    private TextView mBatteryVoltage;
    private TextView mBatteryCurrent;
    private TextView mBatteryTemp;
    private TextView droneCoordinates;

   // Low battery thresholds
    private int lowBattery;
    private int seriousLowBattery;
    final private int lowBatteryThreshold = 20;
    final private int seriousLowBatteryThreshold = 10;

    // 1 for Polygon, 2 for circular, 0 other
    private int missionType;

    // To Update Drone Location in MainActivity
   private String droneTextLat ;
   private String droneTextLng;


    // On App Resume, re-initialise flight controller
    // Update Battery Status
    @Override
    protected void onResume() {
        super.onResume();
        initFlightController();
        upDateBatteryStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceiver);
        removeListener();
        super.onDestroy();
    }

    public void onReturn(View view) {
        Log.d(TAG, "onReturn");
        this.finish();
    }

    // Wrapper for the Android MakeText function.
    // input parameters: String to be written in Toast
    private void setResultToToast(final String string) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Function to initialise the MainActivity page and set listeners for all buttons
    private void initUI() {

        locate = (Button) findViewById(R.id.locate);
        add = (Button) findViewById(R.id.add);
        clear = (Button) findViewById(R.id.clear);
        config = (Button) findViewById(R.id.config);
        upload = (Button) findViewById(R.id.upload);
        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);
        polygon = (Button) findViewById(R.id.polygon);
        circle = (Button) findViewById(R.id.circular);
        home = (Button) findViewById(R.id.home);
        goHome = (Button) findViewById(R.id.goHome);

        mBatteryChargeInPercent = (TextView) findViewById(R.id.BatteryChargeRemaining);
        mBatteryTemp = (TextView) findViewById(R.id.BatteryTemp);
        mBatteryChargeRemaining = (TextView) findViewById(R.id.BatteryChargeRemaining);
        mBatteryCurrent = (TextView) findViewById(R.id.BatteryCurrent);
        mBatteryVoltage = (TextView) findViewById(R.id.BatteryVoltage);
        droneCoordinates = (TextView) findViewById(R.id.droneCoordinates);

        locate.setOnClickListener(this);
        add.setOnClickListener(this);
        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        upload.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
        polygon.setOnClickListener(this);
        circle.setOnClickListener(this);
        home.setOnClickListener(this);
        goHome.setOnClickListener(this);

    }

    // Converts degrees to radians and calculates a 1 metre offset for the latitude.
    /* This is based on the following logic
       Earth Radius = 6371km
       Perimeter = 2 * pi * 6371km
       1 degree Latitude = Perimeter / 360
       Therefore 1 metre offset = 360 / Perimeter
     */
    public static double calcLongitudeOffset(double latitude) {
        return ONE_METER_OFFSET / Math.cos(latitude * Math.PI / 180.0f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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


        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIDemoApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initUI();
        upDateBatteryStatus();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        addListener();

    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange() {
        initFlightController();
        initBattery();

    }


    // Update battery status every second.
    // Continuously checks if batteryCharge left is lower than threshold
    // Invokes go-home procedure
    //TODO: only call startGoHomeProcedure once
    private void upDateBatteryStatus() {

       final Handler mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run()
            {
                BaseProduct product = DJIDemoApplication.getProductInstance();
                if (product != null && product.isConnected()) {
                    if (product instanceof Aircraft) {
                        product.getBattery().setStateCallback(new BatteryState.Callback() {
                            @Override
                            public void onUpdate(BatteryState batteryState) {
                                batteryChargeInPercent = batteryState.getChargeRemainingInPercent();
                                batteryChargeRemaining = batteryState.getChargeRemaining();
                                batteryVoltage = batteryState.getVoltage();
                                batteryCurrent = batteryState.getCurrent();
                                batteryTemp = batteryState.getTemperature();
                                mBatteryTemp.setText(Float.toString(batteryTemp) + " \u00b0" + "C");
                                mBatteryCurrent.setText(batteryCurrent + " mA");
                                mBatteryChargeRemaining.setText(batteryChargeRemaining + " mAh");
                                mBatteryVoltage.setText(batteryVoltage + " mV");
                                mBatteryChargeInPercent.setText(batteryChargeInPercent + " %");

                                if(homeBase != null && batteryChargeInPercent <= lowBatteryThreshold)
                                {
                                    setResultToToast("Battery Low, Mission Aborted");
                                    startGoHomeProcedure();

                                }



                            }
                        });
                    }
                }
                 mHandler.postDelayed(this, 1000);

            }
        }, 1000);


    }

    // Sets Low and serious low battery thresholds. Called on App Launch after onProductConnectionChange
    // detects aircraft.
    private void initBattery() {
        mFlightController.setLowBatteryWarningThreshold(lowBatteryThreshold, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                setResultToToast("Low Battery Threshold: " + (djiError == null ? " Set Successfully" : djiError.getDescription()));
            }
        });

        mFlightController.setSeriousLowBatteryWarningThreshold(seriousLowBatteryThreshold, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                setResultToToast("Serious Low Battery Threshold: " + (djiError == null ? " Set Successfully" : djiError.getDescription()));
            }
        });

    }

    // Initialises the onboard flight controller and updates the drone location.
    private void initFlightController() {

        BaseProduct product = DJIDemoApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();

            }
        }

        if (mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {

                @Override
                public void onUpdate(FlightControllerState djiFlightControllerCurrentState) {
                    droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                    mHomeLatitude = djiFlightControllerCurrentState.getHomeLocation().getLatitude();
                    mHomeLongitude = djiFlightControllerCurrentState.getHomeLocation().getLongitude();
                    updateDroneLocation();
                }
            });

            mFlightController.setSmartReturnToHomeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError error) {
                    setResultToToast("Smart RTH Enabled: " + (error == null ? "Successfully" : error.getDescription()));
                }
            });
        }
    }

    // Battery State to return battery status
    // Method Deprecated
    private void batteryState() {

        mFlightController.getLowBatteryWarningThreshold(new CommonCallbacks.CompletionCallbackWith<Integer>() {
            @Override
            public void onSuccess(Integer integer) {
                lowBattery = integer;
                String Text1 = Integer.toString(lowBattery);
                setResultToToast("Battery At " + Text1 + "%");
            }

            @Override
            public void onFailure(DJIError djiError) {
                setResultToToast("couldn't get Battery Status");

            }
        });

        mFlightController.getSeriousLowBatteryWarningThreshold(new CommonCallbacks.CompletionCallbackWith<Integer>() {
            @Override
            public void onSuccess(Integer integer) {
                seriousLowBattery = integer;
                String Text1 = Integer.toString(seriousLowBattery);
                setResultToToast("Battery At " + Text1 + "%");
            }

            @Override
            public void onFailure(DJIError djiError) {

                setResultToToast("Unable to get Low Battery Status");

            }
        });
    }


    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().addListener(eventNotificationListener);
        }
    }

    //Remove Listener for WaypointMissionOperator
    private void removeListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().removeListener(eventNotificationListener);
        }
    }

    private WaypointMissionOperatorListener eventNotificationListener = new WaypointMissionOperatorListener() {
        @Override
        public void onDownloadUpdate(WaypointMissionDownloadEvent downloadEvent) {

        }

        @Override
        public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) {

        }

        @Override
        public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {

        }

        @Override
        public void onExecutionStart()
        {
            setResultToToast("Execution Starting ");
        }

        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
        }
    };

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
        }
        return instance;
    }

    // adds the listener for click for gmap object
    private void setUpMap() {
        gMap.setOnMapClickListener(this);

    }

    // Adds Waypoint to the map when map is clicked and if ADD button is pressed
    // If Add Button isn't clicked, Throws up an error message
    @Override
    public void onMapClick(LatLng point) {
        if (isAdd == true) {
            markWaypoint(point);
            Waypoint mWaypoint = new Waypoint(point.latitude, point.longitude, altitude);
            //Add Waypoints to Waypoint arraylist;
            if (waypointMissionBuilder != null) {
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            } else {
                waypointMissionBuilder = new WaypointMission.Builder();
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }
        } else {
            setResultToToast("Cannot Add Waypoint, Check is ADD Button is pressed.");
        }
    }

    // Coordinates Bound check.
    public static boolean checkGpsCoordinates(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation() {

        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
         droneTextLat = Double.toString(droneLocationLat);
         droneTextLng = Double.toString(droneLocationLng);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordinates(droneLocationLat, droneLocationLng)) {
                    droneMarker = gMap.addMarker(markerOptions);
                    // Update drone position in Lat,Lng in UI
                    droneCoordinates.setText(droneTextLat + ", "+ droneTextLng);

                }
            }
        });
    }

    // Set Markers to markwaypoint on the map.
    private void markWaypoint(LatLng point) {
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = gMap.addMarker(markerOptions);
        markers.add(marker);

        // Draw a polygon if number of markers exceeds three.
        if (markers.size() >= 3) {
            drawPolygon(markers.size());
        }

    }

    // TODO: Why did I separate this again?
    // Marks the current homepoint on the map
    // Should be called to update map after pressing clear button
    private void markHomePoint(LatLng point) {
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        gMap.addMarker(markerOptions);

    }

    // Events and functions when buttons are clicked
    @Override
    public void onClick(View v) {


        switch (v.getId()) {

            // Mark current position of tablet on Map
            // Interrupt mission and Start RTH Procedure
            // when button pressed.
            // RTH Function won't work if Aircraft isn't mid-flight/mid-mission
            //TODO: Can you get Aircraft to fly home if it's landed in a different place?
            case R.id.goHome:
            {
                markHomePoint(homeBase);
                startGoHomeProcedure();
                break;
            }

            //Sets current location as home position.
            //For redundancy really, Home Position is automatically set to current
            // position when app is opened
            case R.id.home:
            {
                setHomeLocation();
                markHomePoint(homeBase);
                break;
            }
            //Locates drone's current position and current home location
            //TODO Write current location to a UI element and return distance from current location to aircraft on the UI
            case R.id.locate:
            {

                markHomePoint(homeBase);
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place
                getHomeLocation();
                break;
            }
            // toggle adding waypoints to map
            case R.id.add:
            {
                enableDisableAdd();
                break;
            }
            // Clears all mission parameters
            // Also clears the screen
            case R.id.clear:
            {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();

                    }

                });
                circularWaypointList.clear();
                coordinateWaypointList.clear();
                waypointList.clear();
                waypointMissionBuilder.waypointList(waypointList);
                updateDroneLocation();
                removePolyElements();
                missionType = 0;
                break;
            }
            // Opens the setting dialog to set mission parameters
            //TODO: Can you add low battery behaviour to the dialog here?
            case R.id.config: {
                showSettingDialog();
                break;
            }

            // Sets a predefined mission
            // Mission adds waypoint to the N,E,S,D coordinates of the aircraft
            case R.id.polygon: {
                missionType = 1;
                showSettingDialog();
                updateDroneLocation();
                cameraUpdate();

                break;
            }
            // uploads parameters of current mission
            case R.id.upload: {
                uploadWayPointMission();
                break;
            }
            //starts the defined mission
            case R.id.start: {
                startWaypointMission();
                break;
            }
            //Stops the mission
            case R.id.stop: {
                stopWaypointMission();
                break;
            }
            // Uploads a mission of the aircraft flying in a circle with a predefined initial bearing
                //TODO Calculate number of waypoints in the map based on a given initial bearing
            case R.id.circular: {
                missionType = 2;
                showSettingDialog();
                break;
            }
            default:
                break;
        }
    }

    // Updates Camera view and zooms into current Aircraft position
    private void cameraUpdate() {
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomLevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomLevel);
        gMap.moveCamera(cu);

    }

    //Method enables/disables the Waypoint "Add" Button
    private void enableDisableAdd() {
        if (isAdd == false) {
            isAdd = true;
            add.setText("Exit");
        } else {
            isAdd = false;
            add.setText("Add");
        }
    }

    //Settings dialog showing the following:
    //The Altitude for the mission
    // The Speed for the mission
       //Three options at 5m/s, 10m/s, and 3.0m/s
    //Action after mission is over
       // Go back Home, AutoLand at last Waypoint, do nothing or fly back to first waypoint.
    //Aircraft Heading
    //TODO Figure out what the Aircraft's behaviour and orientation with heading
    private void showSettingDialog() {
        LinearLayout wayPointSettings = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed) {
                    mSpeed = 3.0f;
                } else if (checkedId == R.id.MidSpeed) {
                    mSpeed = 5.0f;
                } else if (checkedId == R.id.HighSpeed) {
                    mSpeed = 10.0f;
                }
            }

        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select finish action");
                if (checkedId == R.id.finishNone) {
                    mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
                } else if (checkedId == R.id.finishGoHome) {
                    mFinishedAction = WaypointMissionFinishedAction.GO_HOME;
                } else if (checkedId == R.id.finishAutoLanding) {
                    mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                } else if (checkedId == R.id.finishToFirst) {
                    mFinishedAction = WaypointMissionFinishedAction.GO_FIRST_WAYPOINT;
                }
            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select heading");

                if (checkedId == R.id.headingNext) {
                    mHeadingMode = WaypointMissionHeadingMode.AUTO;
                } else if (checkedId == R.id.headingInitDirec) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_INITIAL_DIRECTION;
                } else if (checkedId == R.id.headingRC) {
                    mHeadingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER;
                } else if (checkedId == R.id.headingWP) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                }
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                        String altitudeString = wpAltitude_TV.getText().toString();
                        altitude = Integer.parseInt(nulltoIntegerDefalt(altitudeString));
                        Log.e(TAG, "altitude " + altitude);
                        Log.e(TAG, "speed " + mSpeed);
                        Log.e(TAG, "mFinishedAction " + mFinishedAction);
                        Log.e(TAG, "mHeadingMode " + mHeadingMode);

                        if (missionType == 1) {
                            coordinateWaypointMission();
                        } else if (missionType == 2) {
                            circularMission();
                        } else {
                            configWayPointMission();
                        }
                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    //Method to initiate flyback home procedure.
    // Aircraft should fly back at an altitude of 30m
    //TODO is there a need for setResultToToast here..
    private void startGoHomeProcedure()
    {
      stopWaypointMission();

      mFlightController.setGoHomeHeightInMeters(30, new CommonCallbacks.CompletionCallback() {
          @Override
          public void onResult(DJIError djiError) {

              setResultToToast("RTH Height 30M set: " + (djiError == null ? "Successfully!" : djiError.getDescription()));

          }
      });
        mFlightController.startGoHome(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                setResultToToast("Returning to home: " + (djiError == null ? "Success!" : djiError.getDescription()));
            }
        });

    }

    // Currently ununsed method
    //TODO add a usecase for this.
    private void LandDrone()
    {
        mFlightController.startLanding(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {

                setResultToToast("Drone landing: " + (djiError == null ? "Success!" : djiError.getDescription()));

            }
        });
    }


    String nulltoIntegerDefalt(String value) {
        if (!isIntValue(value)) value = "0";
        return value;
    }

    boolean isIntValue(String val) {
        try {
            val = val.replace(" ", "");
            Integer.parseInt(val);
        } catch (Exception e) {
            return false;
        }
        return true;
    }


   // N,E,S,D Mission
    //TODO Add mission Actions for each waypoint.
    private void coordinateWaypointMission() {

        if (waypointMissionBuilder == null) {

            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        }

        // Add Actions at each waypoint later..
        else {
            waypointMissionBuilder.finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        }

        // Use to add waypoint markers to the Map...
        LatLng northPoint = new LatLng(homeBase.latitude + 10 * ONE_METER_OFFSET, homeBase.longitude);
        LatLng southPoint = new LatLng(homeBase.latitude - 10 * ONE_METER_OFFSET, homeBase.longitude);
        LatLng westPoint = new LatLng(homeBase.latitude, homeBase.longitude - 10 * (calcLongitudeOffset(homeBase.latitude)));
        LatLng eastPoint = new LatLng(homeBase.latitude, homeBase.longitude + 10 * (calcLongitudeOffset(homeBase.latitude)));

        // Calculate Waypoint Markers
        Waypoint northWaypoint = new Waypoint(homeBase.latitude + 10 * ONE_METER_OFFSET, homeBase.longitude, altitude);
        Waypoint southWaypoint = new Waypoint(homeBase.latitude - 10 * ONE_METER_OFFSET, homeBase.longitude, altitude);
        Waypoint eastWaypoint = new Waypoint(homeBase.latitude, homeBase.longitude + 10 * (calcLongitudeOffset(homeBase.latitude)), altitude);
        Waypoint westWaypoint = new Waypoint(homeBase.latitude, homeBase.longitude - 10 * (calcLongitudeOffset(homeBase.latitude)), altitude);

        // Optimise later with for/foreach loop
        markWaypoint(northPoint);
        markWaypoint(eastPoint);
        markWaypoint(southPoint);
        markWaypoint(westPoint);

        coordinateWaypointList.add(northWaypoint);
        coordinateWaypointList.add(southWaypoint);
        coordinateWaypointList.add(westWaypoint);
        coordinateWaypointList.add(eastWaypoint);

        waypointMissionBuilder.waypointList(coordinateWaypointList).waypointCount(coordinateWaypointList.size());

        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        if (error == null) {
            setResultToToast("Coordinate loadWaypoint succeeded");
        } else {
            setResultToToast("Coordinate loadWaypoint failed " + error.getDescription());
        }

    }

    /*
    Creates a circular mission of 10 way points with each point being 100m
    away from the home point.
     */
    //TODO Add waypoint actions later
    private void circularMission() {

        if (waypointMissionBuilder == null) {

            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        } else

        {
            waypointMissionBuilder.finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        }

        double distance = 0.1; // Distance being travelled
        double earthRadius = 6371; // Earth Radius

        double finalLat = 0;
        double finalLon = 0;

        double angularDistance = distance / earthRadius;  // angular distance
        int numPoints = 10;
        //double slice = 360/numPoints;
        double slice = (2 * Math.PI) / numPoints; // Bearing clockwise from north
        double Lat = homeBase.latitude;
        double Lon = homeBase.longitude;

        ArrayList<LatLng> centerArray = new ArrayList<>(); // Store bearing points.

        double homeLat = Math.toRadians(Lat);
        double homeLon = Math.toRadians(Lon);

        //Adapted from the Aviation Formulary
        // http://www.edwilliams.org/avform.htm
        for (int i = 0; i < numPoints; i++) {

            double bearing = slice * i;
            finalLat = Math.asin(Math.sin(homeLat) * Math.cos(angularDistance) + Math.cos(homeLat) * Math.sin(angularDistance) * Math.cos(bearing));

            finalLon = homeLon + Math.atan2(Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(homeLat), Math.cos(angularDistance) - Math.sin(homeLat) * Math.sin(finalLat));

            finalLat = Math.toDegrees(finalLat);
            finalLon = Math.toDegrees(finalLon);

            centerArray.add(new LatLng(finalLat, finalLon));

        }

        PolygonOptions polygonOptions = new PolygonOptions();
        polygonOptions.fillColor(0x330000FF);
        polygonOptions.strokeWidth(3);
        polygonOptions.strokeColor(Color.BLUE);

        for (int i = 0; i < centerArray.size(); i++) {
            // Add waypoints
            circularWaypointList.add(new Waypoint(centerArray.get(i).latitude, centerArray.get(i).longitude, altitude));
            // Add coordinates to draw a polygon
            polygonOptions.add(new LatLng(centerArray.get(i).latitude, centerArray.get(i).longitude));
            // set marker of for each coordinate
            markWaypoint(new LatLng(centerArray.get(i).latitude, centerArray.get(i).longitude));
        }

        waypointMissionBuilder.waypointList(circularWaypointList).waypointCount(circularWaypointList.size());

        shape = gMap.addPolygon(polygonOptions);

        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        if (error == null) {
            setResultToToast("Circular loadWaypoint succeeded");
        } else {
            setResultToToast("Circular loadWaypoint failed " + error.getDescription());
        }

    }

    // Configures custom waypoint mission
    private void configWayPointMission() {

        String pol_points = null;  // Number of WayPoints to be written to Toast

        if (waypointMissionBuilder == null) {

            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        } else {
            waypointMissionBuilder.finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        }

        if (waypointMissionBuilder.getWaypointList().size() > 0) {

            for (int i = 0; i < waypointMissionBuilder.getWaypointList().size(); i++) {

                waypointMissionBuilder.getWaypointList().get(i).altitude = altitude;

            }

            int numPoints = waypointMissionBuilder.getWaypointList().size();

            // Set Altitude of final waypoint to 10 metres;
            waypointMissionBuilder.getWaypointList().get(waypointMissionBuilder.getWaypointList().size() - 1).altitude = 10;
            waypointMissionBuilder.getWaypointList().get(waypointMissionBuilder.getWaypointList().size() - 1).addAction(new WaypointAction(WaypointActionType.GIMBAL_PITCH, -60));

            // Return number of Polygon Points;

            drawPolygon(numPoints);
            // Remove
            pol_points = Integer.toString(numPoints);

            // String Altitude_Value =  String.valueOf(waypointMissionBuilder.getWaypointList().get(waypointMissionBuilder.getWaypointList().size() - 1).altitude);

            setResultToToast("Altitudes set succesfully");
        }

        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        if (error == null) {
            setResultToToast(pol_points + " Waypoints added");
        } else {
            setResultToToast("loadWaypoint failed " + error.getDescription());
        }
    }

    //Uploads Waypoint Mission using DJI MissionOperator
    private void uploadWayPointMission() {

        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    setResultToToast("Mission upload successfully!");
                } else {
                    setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(null);
                }
            }
        });

    }

    // Method to set home Location
    private void setHomeLocation() {
        if (mFlightController != null) {

            mFlightController.setHomeLocation(new LocationCoordinate2D(homeBase.latitude, homeBase.longitude), new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    String Text1 = Double.toString(homeBase.latitude);
                    String Text2 = Double.toString(homeBase.longitude);
                    setResultToToast("Home Location set: " + Text1 + " ," + Text2 + (djiError == null ? " Successfully" : djiError.getDescription()));
                }
            });
        }
    }

    // Sets Home Location as Current AircraftLocation.
    // care to be taken when using this method. (Currently unused)
    private void setHomeLocationCurrentLocation() {
        if (mFlightController != null) {

            mFlightController.setHomeLocationUsingAircraftCurrentLocation(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    String Text1 = Double.toString(droneLocationLat);
                    String Text2 = Double.toString(droneLocationLng);
                    setResultToToast("Home Location set: " + Text1 + " ," + Text2 + (djiError == null ? " Successfully" : djiError.getDescription()));
                }
            });
        }
    }

    //Returns current home location in Coordinate degrees
    private void getHomeLocation() {
        if (mFlightController != null) {
            mFlightController.getHomeLocation(new CommonCallbacks.CompletionCallbackWith<LocationCoordinate2D>() {

                @Override
                public void onSuccess(LocationCoordinate2D t) {
                    mHomeLatitude = t.getLatitude();
                    mHomeLongitude = t.getLongitude();
                    setResultToToast("home point latitude: " + mHomeLatitude + "\nhome point longitude: " + mHomeLongitude);
                }

                @Override
                public void onFailure(DJIError error) {
                    setResultToToast(" Get Home Location Error: " + error.getDescription());
                }

            });
        }
    }

    // Draw Polygon of each markers currently on map
    // Google Maps API currently doesn't fill overlapping "lines/polygons"
    private void drawPolygon(int numPoints) {
        PolygonOptions polygonOptions = new PolygonOptions();
        polygonOptions.fillColor(0x330000FF);
        polygonOptions.strokeWidth(3);
        polygonOptions.strokeColor(Color.BLUE);

        for (int i = 0; i < numPoints; i++) {
            polygonOptions.add(markers.get(i).getPosition());

        }

        shape = gMap.addPolygon(polygonOptions);

    }

    // remove value type of concurrent hashmap.
    private void removePolyElements() {
        if (shape != null) {
            for (Marker marker : markers) {
                marker.remove();
            }

            markers.clear();
            shape.remove();
            shape = null;
        }
    }


    //Starts Waypoint Mission
    private void startWaypointMission() {

        //Check current state of mission update
        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.UPLOADING) {
            setResultToToast("Please wait, Mission is uploading");

        }
        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE) {
            getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError error) {
                    setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
                }
            });
        } else if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTING) {
            getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError error) {
                    setResultToToast("Mission in: " + (error == null ? "Progress" : error.getDescription()));
                }
            });

        }
    }

    //Stops WayPoint Mission
    private void stopWaypointMission() {

        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }

    // Updates Map with and marks current location of device in use
    //Make sure you check permissions if you're using the new Google Fused Location API
    // Older method for google Locations has been deprecated.
    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();

        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            }
        }
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            homeBase = new LatLng(location.getLatitude(), location.getLongitude());
                            CameraUpdate update = CameraUpdateFactory.newLatLngZoom(homeBase, 19);
                            gMap.animateCamera(update);
                            markHomePoint(homeBase);
                        } else if (location == null) {
                            Toast.makeText(getApplicationContext(), "Can't get current location", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}