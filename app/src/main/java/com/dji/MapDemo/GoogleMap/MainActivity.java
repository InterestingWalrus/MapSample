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

        import java.util.ArrayList;
        import java.util.LinkedList;
        import java.util.List;
        import java.util.Map;
        import java.util.concurrent.ConcurrentHashMap;


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

    private GoogleMap gMap;

    private double mHomeLatitude;
    private double mHomeLongitude;

    // Buttons on the app
    private Button home, locate, add, clear;
    private Button config, upload, start, stop;
    private Button polygon;
    private Button circle;

    private boolean isAdd = false;

    // Drone coordinates
    private double droneLocationLat = 181, droneLocationLng = 181;
    ArrayList<Marker> markers = new ArrayList<>();
    private Marker droneMarker = null;

    private float altitude = 100.0f;
    private float mSpeed = 10.0f;

    Polygon shape;

    // Home point for the Drone
    private LatLng homeBase;

    private List<Waypoint> waypointList = new ArrayList<>();
    private List<Waypoint> coordinateWaypointList = new ArrayList<>();
    private List<Waypoint> circularWaypointList = new ArrayList<>();

    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;
    private FusedLocationProviderClient mFusedLocationClient;

    // Instantiate dialogSample class;
    DialogSample dialogSample = new DialogSample();

    private int missionType;


    @Override
    protected void onResume() {
        super.onResume();
        initFlightController();
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

    /**
     * @Description : RETURN Button RESPONSE FUNCTION
     */
    public void onReturn(View view) {
        Log.d(TAG, "onReturn");
        this.finish();
    }

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
        }
    }

    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().addListener(eventNotificationListener);
        }
    }

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
        public void onExecutionStart() {

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
            setResultToToast("Cannot Add Waypoint");
        }
    }

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

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordinates(droneLocationLat, droneLocationLng)) {
                    droneMarker = gMap.addMarker(markerOptions);

                }
            }
        });
    }

    // Set Markers
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

    private void markHomePoint(LatLng point) {
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        gMap.addMarker(markerOptions);

    }

    @Override
    public void onClick(View v) {
        // Return current drone coordinates to write on-screen
        String droneLat = Double.toString(droneLocationLat);
        String droneLon = Double.toString(droneLocationLng);
        switch (v.getId()) {

            case R.id.home: {
                // setHomeLocationCurrentLocation();
                setHomeLocation();
            }
            case R.id.locate: {

                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place
                //Toast.makeText(getApplicationContext(), droneLat + ", " + droneLon, Toast.LENGTH_LONG).show();
                getHomeLocation();
                break;
            }
            case R.id.add: {
                enableDisableAdd();
                break;
            }
            case R.id.clear: {

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
                break;
            }
            case R.id.config: {
                showSettingDialog();
                break;
            }

            case R.id.polygon:
            {
                missionType = 1;
               showSettingDialog();
               updateDroneLocation();
               cameraUpdate();

                break;
            }
            case R.id.upload: {
                uploadWayPointMission();
                break;
            }
            case R.id.start: {
                startWaypointMission();
                break;
            }
            case R.id.stop: {
                stopWaypointMission();
                break;
            }

            case R.id.circular:
             {
                missionType = 2;
                showSettingDialog();
                break;
            }
            default:
                break;
        }
    }

    private void openDialog() {

        dialogSample.show(getSupportFragmentManager(), "Flight Parameters");
        mSpeed = (float) dialogSample.returnSpeed();
        altitude = (float) dialogSample.returnAltitude();

    }

    private void cameraUpdate() {
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomLevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomLevel);
        gMap.moveCamera(cu);

    }

    private void enableDisableAdd() {
        if (isAdd == false) {
            isAdd = true;
            add.setText("Exit");
        } else {
            isAdd = false;
            add.setText("Add");
        }
    }

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

                        if(missionType ==1)
                        {
                            coordinateWaypointMission();
                        }

                        else if (missionType == 2)
                        {
                            circularMission();
                        }

                        else
                        {
                            setResultToToast("Invalid Mission Type");
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
    private void circularMission() {

        if (waypointMissionBuilder == null) {

            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        }

        else

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

    private void configWayPointMission()
    {

        String pol_points = null;

        if (waypointMissionBuilder == null){

            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        }else
        {
            waypointMissionBuilder.finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        }

        if (waypointMissionBuilder.getWaypointList().size() > 0)
        {

            for (int i=0; i< waypointMissionBuilder.getWaypointList().size(); i++)
            {

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

            setResultToToast("Altitudes set succesfully" );
        }

        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        if (error == null) {
            setResultToToast(pol_points + " Waypoints added");
        } else {
            setResultToToast("loadWaypoint failed " + error.getDescription());
        }
    }

    private void uploadWayPointMission()
    {

        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null)
                {
                    setResultToToast("Mission upload successfully!");
                } else {
                    setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(null);
                }
            }
        });

    }

    private void setHomeLocation()
    {
        if(mFlightController != null)
        {

            mFlightController.setHomeLocation(new LocationCoordinate2D(homeBase.latitude, homeBase.longitude), new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError)
                {
                    String Text1 = Double.toString(homeBase.latitude);
                    String Text2 = Double.toString(homeBase.longitude);
                    setResultToToast("Home Location set: " + Text1 + " ," + Text2 + (djiError == null ? " Successfully" : djiError.getDescription()) );
                }
            });
        }
    }

    private void setHomeLocationCurrentLocation()
    {
        if(mFlightController != null)
        {

            mFlightController.setHomeLocationUsingAircraftCurrentLocation( new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError)
                {
                    String Text1 = Double.toString(droneLocationLat);
                    String Text2 = Double.toString(droneLocationLng);
                    setResultToToast("Home Location set: " + Text1 + " ," + Text2 + (djiError == null ? " Successfully" : djiError.getDescription()) );
                }
            });
        }
    }


    private void getHomeLocation()
    {
        if (mFlightController!= null)
        {
            mFlightController.getHomeLocation(new CommonCallbacks.CompletionCallbackWith<LocationCoordinate2D>() {

                @Override
                public void onSuccess(LocationCoordinate2D t) {
                     mHomeLatitude = t.getLatitude();
                     mHomeLongitude = t.getLongitude();
                    setResultToToast( "home point latitude: " + mHomeLatitude + "\nhome point longitude: " + mHomeLongitude );
                }

                @Override
                public void onFailure(DJIError error)
                {
                    setResultToToast(" Get Home Location Error: " + error.getDescription() );
                }


            });


        }

    }

// Draw Polygon of Marker Distance
    private void drawPolygon(int numPoints)
    {
        PolygonOptions polygonOptions = new PolygonOptions();
        polygonOptions.fillColor(0x330000FF);
        polygonOptions.strokeWidth(3);
        polygonOptions.strokeColor(Color.BLUE);

        for (int i = 0; i < numPoints; i++)
        {
            polygonOptions.add(markers.get(i).getPosition());

        }

        shape = gMap.addPolygon(polygonOptions);

    }

    // remove value type of concurrent hashmap.
    private void removePolyElements()
    {
       if(shape !=null)
       {
           for (Marker marker : markers) {
               marker.remove();
           }

           markers.clear();
           shape.remove();
           shape = null;
       }
    }



    private void startWaypointMission()
    {

        //Check current state of mission update
    if(getWaypointMissionOperator().getCurrentState() == WaypointMissionState.UPLOADING)
     {
         setResultToToast("Please wait, Mission is uploading");

     }
     if(getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE)
     {
         getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
             @Override
             public void onResult(DJIError error) {
                 setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
             }
         });
     }

     else if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTING)
     {
         getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
             @Override
             public void onResult(DJIError error) {
                 setResultToToast("Mission in: " + (error == null ? "Progress" : error.getDescription()));
             }
         });

     }
    }

    private void stopWaypointMission()
    {

        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }

    @Override
    public void onMapReady(GoogleMap googleMap)
    {
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
                .addOnSuccessListener(this, new OnSuccessListener<Location>()
                {
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
//        homeBase = new LatLng(52.761155 , -1.247651);
//        gMap.addMarker(new MarkerOptions().position(homeBase).title("Marker in Shenzhen"));
//        gMap.moveCamera(CameraUpdateFactory.newLatLng(homeBase));
    }
}