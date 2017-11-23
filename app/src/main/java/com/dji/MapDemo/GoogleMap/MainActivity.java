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
        import android.support.annotation.Nullable;
        import android.support.v4.app.ActivityCompat;
        import android.support.v4.app.FragmentActivity;
        import android.os.Bundle;
        import android.util.Log;
        import android.view.View;
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
        import dji.common.useraccount.UserAccountState;
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
        import dji.sdk.useraccount.UserAccountManager;
        import dji.thirdparty.afinal.utils.Utils;

public class MainActivity extends FragmentActivity implements View.OnClickListener, GoogleMap.OnMapClickListener, OnMapReadyCallback {

    protected static final String TAG = "GSDemoActivity";
    private static final double ONE_METER_OFFSET = 0.00000899322;

    private GoogleMap gMap;

    private Button locate, add, clear;
    private Button config, upload, start, stop;
    private Button polygon;

    private boolean isAdd = false;

    private double droneLocationLat = 181, droneLocationLng = 181;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;

    private float altitude = 100.0f;
    private float mSpeed = 10.0f;

    Polygon shape;
    private int POLYGON_POINTS = 4;

    // Home point for the Drone
    private LatLng homeBase;

    private List<Waypoint> waypointList = new ArrayList<>();

    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;
    private FusedLocationProviderClient mFusedLocationClient;

    @Override
    protected void onResume(){
        super.onResume();
        initFlightController();
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onDestroy(){
        unregisterReceiver(mReceiver);
        removeListener();
        super.onDestroy();
    }

    /**
     * @Description : RETURN Button RESPONSE FUNCTION
     */
    public void onReturn(View view){
        Log.d(TAG, "onReturn");
        this.finish();
    }

    private void setResultToToast(final String string){
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI() {

        locate = (Button) findViewById(R.id.locate);
        add = (Button) findViewById(R.id.add);
        clear = (Button) findViewById(R.id.clear);
        config = (Button) findViewById(R.id.config);
        upload = (Button) findViewById(R.id.upload);
        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);
        polygon = (Button) findViewById(R.id.polygon);

        locate.setOnClickListener(this);
        add.setOnClickListener(this);
        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        upload.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
        polygon.setOnClickListener(this);

    }

    // Converts degrees to radians and calculates a 1 metre offset for the latitude.
    /* This is based on the following logic
       Earth Radius = 6371km
       Perimeter = 2 * pi * 6371km
       1 degree Latitude = Perimeter / 360
       Therefore 1 metre offset = 360 / Perimeter
     */
    public static  double calcLongitudeOffset(double latitude)
    {
        return ONE_METER_OFFSET / Math.cos(latitude * Math.PI/ 180.0f);
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

    private void onProductConnectionChange()
    {
        initFlightController();

    }


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
                    updateDroneLocation();
                }
            });
        }
    }

    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (getWaypointMissionOperator() != null){
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

    private void setUpMap() {
        gMap.setOnMapClickListener(this);// add the listener for click for amap object

    }

    @Override
    public void onMapClick(LatLng point) {
        if (isAdd == true){
            markWaypoint(point);
            Waypoint mWaypoint = new Waypoint(point.latitude, point.longitude, altitude);
            //Add Waypoints to Waypoint arraylist;
            if (waypointMissionBuilder != null) {
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }else
            {
                waypointMissionBuilder = new WaypointMission.Builder();
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }
        }else{
            setResultToToast("Cannot Add Waypoint");
        }
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation(){

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

                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                    droneMarker = gMap.addMarker(markerOptions);
                }
            }
        });
    }

    private void markWaypoint(LatLng point){
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = gMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);


    }

    @Override
    public void onClick(View v) {

        String dronelat = Double.toString(droneLocationLat);
        switch (v.getId()) {
            case R.id.locate:{
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place
                Toast.makeText(getApplicationContext(), dronelat, Toast.LENGTH_LONG).show();
                break;
            }
            case R.id.add:{
                enableDisableAdd();
                break;
            }
            case R.id.clear:{
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();
                    }

                });
                waypointList.clear();
                removePolyElements();
                waypointMissionBuilder.waypointList(waypointList);
                updateDroneLocation();
                break;
            }
            case R.id.config:{
                showSettingDialog();
                break;
            }

            case R.id.polygon:
            {
                coordinateWaypointMission();
                break;
            }
            case R.id.upload:{
                uploadWayPointMission();
                break;
            }
            case R.id.start:{
                startWaypointMission();
                break;
            }
            case R.id.stop:{
                stopWaypointMission();
                break;
            }
            default:
                break;
        }
    }

    private void cameraUpdate(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        gMap.moveCamera(cu);

    }

    private void enableDisableAdd(){
        if (isAdd == false) {
            isAdd = true;
            add.setText("Exit");
        }else{
            isAdd = false;
            add.setText("Add");
        }
    }

    private void showSettingDialog(){
        LinearLayout wayPointSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed){
                    mSpeed = 3.0f;
                } else if (checkedId == R.id.MidSpeed){
                    mSpeed = 5.0f;
                } else if (checkedId == R.id.HighSpeed){
                    mSpeed = 10.0f;
                }
            }

        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select finish action");
                if (checkedId == R.id.finishNone){
                    mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
                } else if (checkedId == R.id.finishGoHome){
                    mFinishedAction = WaypointMissionFinishedAction.GO_HOME;
                } else if (checkedId == R.id.finishAutoLanding){
                    mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                } else if (checkedId == R.id.finishToFirst){
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
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {

                        String altitudeString = wpAltitude_TV.getText().toString();
                        altitude = Integer.parseInt(nulltoIntegerDefalt(altitudeString));
                        Log.e(TAG,"altitude "+altitude);
                        Log.e(TAG,"speed "+mSpeed);
                        Log.e(TAG, "mFinishedAction "+mFinishedAction);
                        Log.e(TAG, "mHeadingMode "+mHeadingMode);
                        configWayPointMission();
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

    String nulltoIntegerDefalt(String value){
        if(!isIntValue(value)) value="0";
        return value;
    }

    boolean isIntValue(String val)
    {
        try {
            val=val.replace(" ","");
            Integer.parseInt(val);
        } catch (Exception e) {return false;}
        return true;
    }

    private void coordinateWaypointMission()
    {
        List<Waypoint> waypoints = new LinkedList<>();
         // Use to add waypoint markers to the Map...
        LatLng northPoint = new LatLng(homeBase.latitude + 10 * ONE_METER_OFFSET, homeBase.longitude);
        LatLng southPoint = new LatLng(homeBase.latitude -10 * ONE_METER_OFFSET, homeBase.longitude);
        LatLng westPoint = new LatLng(homeBase.latitude, homeBase.longitude - 10 * (calcLongitudeOffset(homeBase.latitude)));
        LatLng eastPoint  =   new LatLng(homeBase.latitude, homeBase.longitude + 10 * (calcLongitudeOffset(homeBase.latitude)));

        // Calculate Waypoint Markers
        Waypoint northWaypoint = new Waypoint(homeBase.latitude + 10 * ONE_METER_OFFSET, homeBase.longitude, 10f  );
        Waypoint southWaypoint = new Waypoint(homeBase.latitude -10 * ONE_METER_OFFSET, homeBase.longitude, 10f);
        Waypoint eastWaypoint =  new Waypoint(homeBase.latitude, homeBase.longitude + 10 * (calcLongitudeOffset(homeBase.latitude)), 10f );
        Waypoint westWaypoint = new Waypoint(homeBase.latitude, homeBase.longitude - 10 * (calcLongitudeOffset(homeBase.latitude)), 10f );

        // Optimise later with for/foreach loop
        markWaypoint(northPoint);
        markWaypoint(eastPoint);
        markWaypoint(southPoint);
        markWaypoint(westPoint);

        waypoints.add(northWaypoint);
        waypoints.add(southWaypoint);
        waypoints.add(westWaypoint);
        waypoints.add(eastWaypoint);

        waypointMissionBuilder.waypointList(waypoints).waypointCount(waypoints.size());

        // Add Actions at each waypoint later..






    }

    private void configWayPointMission()
    {

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

            // Set Altitude of final waypoint to 10 metres;
            waypointMissionBuilder.getWaypointList().get(waypointMissionBuilder.getWaypointList().size() - 1).altitude = 10;
            waypointMissionBuilder.getWaypointList().get(waypointMissionBuilder.getWaypointList().size() - 1).addAction(new WaypointAction(WaypointActionType.GIMBAL_PITCH, -60));

           // Return number of Polygon Points;
            POLYGON_POINTS = waypointMissionBuilder.getWaypointList().size();

           // String Altitude_Value =  String.valueOf(waypointMissionBuilder.getWaypointList().get(waypointMissionBuilder.getWaypointList().size() - 1).altitude);

            setResultToToast("Altitudes set succesfully" );
        }

        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        if (error == null) {
            setResultToToast("loadWaypoint succeeded");
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
                    drawPolygon();
                    setResultToToast("Mission upload successfully!");
                } else {
                    setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(null);
                }
            }
        });

    }

// Draw Polygon of Marker Distance
    private void drawPolygon()
    {
        PolygonOptions polygonOptions = new PolygonOptions();
        polygonOptions.fillColor(0x330000FF);
        polygonOptions.strokeWidth(3);
        polygonOptions.strokeColor(Color.BLUE);

        for (int i = 0; i < POLYGON_POINTS; i++)
        {
            polygonOptions.add(mMarkers.get(i).getPosition());
        }

         shape = gMap.addPolygon(polygonOptions);

    }

    private void removePolyElements()
    {
        for (Marker marker : mMarkers)
        {
            marker.remove();
        }

        mMarkers.clear();
        shape.remove();
        shape = null;
    }



    private void startWaypointMission()
    {
        //Check current state of mission update
     while(getWaypointMissionOperator().getCurrentState() == WaypointMissionState.UPLOADING)
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
                            markWaypoint(homeBase);
                        } else if (location == null) {
                            Toast.makeText(getApplicationContext(), "Can't get current location", Toast.LENGTH_LONG).show();
                        }
                    }
                });
//        LatLng shenzhen = new LatLng(22.5362, 113.9454);
//        gMap.addMarker(new MarkerOptions().position(shenzhen).title("Marker in Shenzhen"));
//        gMap.moveCamera(CameraUpdateFactory.newLatLng(shenzhen));
    }


}