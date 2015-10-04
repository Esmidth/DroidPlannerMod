package com.playuav.android.maps.providers.amap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.o3dr.android.client.Drone;
import com.playuav.android.R;
import com.playuav.android.DroidPlannerApp;
import com.playuav.android.maps.DPMap;
import com.playuav.android.maps.MarkerInfo;
import com.playuav.android.maps.providers.DPMapProvider;

import com.playuav.android.utils.DroneHelper;
import com.playuav.android.utils.collection.HashBiMap;
import com.playuav.android.utils.prefs.AutoPanMode;
import com.playuav.android.utils.prefs.DroidPlannerPrefs;
import com.playuav.android.utils.GoogleApiClientManager;
import com.playuav.android.utils.GoogleApiClientManager.GoogleApiClientTask;
import android.graphics.Color;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.CameraPosition;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.Marker;
import com.amap.api.maps2d.model.MarkerOptions;
import com.amap.api.maps2d.CameraUpdate;
import com.amap.api.maps2d.CameraUpdateFactory;
import com.amap.api.maps2d.model.BitmapDescriptorFactory;
import com.amap.api.maps2d.model.LatLngBounds;
import com.amap.api.maps2d.model.Polyline;
import com.amap.api.maps2d.model.PolylineOptions;
import com.amap.api.maps2d.model.Polygon;
import com.amap.api.maps2d.model.PolygonOptions;
import android.location.Location;
import com.amap.api.maps2d.Projection;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.property.FootPrint;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.location.LocationManagerProxy;
import com.amap.api.location.LocationProviderProxy;
import com.amap.api.maps2d.LocationSource;
import com.amap.api.maps2d.model.MyLocationStyle;

public class AMapFragment extends Fragment implements DPMap,AMap.OnMapClickListener,AMap.OnMapLongClickListener,AMap.OnMarkerClickListener,AMap.OnMarkerDragListener,
        LocationListener,LocationSource,AMapLocationListener {

    private static final String TAG = AMapFragment.class.getSimpleName();
	private AMap mMap;
	private MapView mMapView;
    private OnLocationChangedListener mListener;
    private LocationManagerProxy mAMapLocationManager;

	private DPMap.OnMapClickListener mMapClickListener;
    private DPMap.OnMapLongClickListener mMapLongClickListener;
    private DPMap.OnMarkerClickListener mMarkerClickListener;
    private DPMap.OnMarkerDragListener mMarkerDragListener;
    private android.location.LocationListener mLocationListener;
    protected boolean useMarkerClickAsMapClick = false;

    private List<Polygon> polygonsPaths = new ArrayList<Polygon>();


    private final AtomicReference<AutoPanMode> mPanMode = new AtomicReference<AutoPanMode>(
            AutoPanMode.DISABLED);

    protected DroidPlannerApp dpApp;
    private Polygon footprintPoly;

	private final HashBiMap<MarkerInfo, Marker> mBiMarkersMap = new HashBiMap<MarkerInfo, Marker>();
    private DroidPlannerPrefs mAppPrefs;

    private GoogleApiClientTask mGoToMyLocationTask;
    private GoogleApiClientTask mRemoveLocationUpdateTask;
    private GoogleApiClientTask mRequestLocationUpdateTask;

    private GoogleApiClientManager mGApiClientMgr;

    private Polyline flightPath;
    private Polyline missionPath;
    private Polyline mDroneLeashPath;
    private int maxFlightPathSize;

    // TODO: update the interval based on the user's current activity.
    private static final long USER_LOCATION_UPDATE_INTERVAL = 30000; // ms
    private static final long USER_LOCATION_UPDATE_FASTEST_INTERVAL = 10000; // ms
    private static final float USER_LOCATION_UPDATE_MIN_DISPLACEMENT = 15; // m

    private static final float GO_TO_MY_LOCATION_ZOOM = 18f;

    private static final IntentFilter eventFilter = new IntentFilter(AttributeEvent.GPS_POSITION);

    private final BroadcastReceiver eventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Drone drone = getDroneApi();
            if (!drone.isConnected())
                return;

            final Gps droneGps = drone.getGps();
            if (droneGps == null)
                return;

            if (mPanMode.get() == AutoPanMode.DRONE && droneGps.isValid()) {
                final LatLong droneLocation = droneGps.getPosition();
                updateCamera(droneLocation);
            }
        }
    };

	
	private void setUpMapIfNeeded() {
		if (mMap == null) {
			mMap = mMapView.getMap();
            mMap.setOnMapClickListener(this);
            mMap.setOnMapLongClickListener(this);
            mMap.setOnMarkerClickListener(this);
            mMap.setOnMarkerDragListener(this);
            mMap.setMapType(AMap.MAP_TYPE_SATELLITE);

            MyLocationStyle myLocationStyle = new MyLocationStyle();
            myLocationStyle.myLocationIcon(BitmapDescriptorFactory
                    .fromResource(R.drawable.location_marker));// 设置小蓝点的图标
            myLocationStyle.strokeColor(Color.BLACK);// 设置圆形的边框颜色
            myLocationStyle.radiusFillColor(Color.argb(100, 0, 0, 180));// 设置圆形的填充颜色
            // myLocationStyle.anchor(int,int)//设置小蓝点的锚点
            myLocationStyle.strokeWidth(1.0f);// 设置圆形的边框粗细
            mMap.setMyLocationStyle(myLocationStyle);
            mMap.setLocationSource(this);// 设置定位监听
            mMap.setMyLocationEnabled(true);// 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false


		}
	}



    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        dpApp = (DroidPlannerApp) activity.getApplication();
    }
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        final FragmentActivity activity = getActivity();
        final Context context = activity.getApplicationContext();

        mGApiClientMgr = new GoogleApiClientManager(context, LocationServices.API);

        mGoToMyLocationTask = mGApiClientMgr.new GoogleApiClientTask() {
            @Override
            public void doRun() {
                final Location myLocation = LocationServices.FusedLocationApi
                        .getLastLocation(getGoogleApiClient());
                if (myLocation != null) {
                    updateCamera(DroneHelper.LocationToCoord(myLocation), GO_TO_MY_LOCATION_ZOOM);
                }
            }
        };

        mRemoveLocationUpdateTask = mGApiClientMgr.new GoogleApiClientTask() {
            @Override
            public void doRun() {
                LocationServices.FusedLocationApi
                        .removeLocationUpdates(getGoogleApiClient(), AMapFragment.this);
            }
        };

        mRequestLocationUpdateTask = mGApiClientMgr.new GoogleApiClientTask() {
            @Override
            public void doRun() {
                final LocationRequest locationReq = LocationRequest.create()
                        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                        .setFastestInterval(USER_LOCATION_UPDATE_FASTEST_INTERVAL)
                        .setInterval(USER_LOCATION_UPDATE_INTERVAL)
                        .setSmallestDisplacement(USER_LOCATION_UPDATE_MIN_DISPLACEMENT);
                LocationServices.FusedLocationApi.requestLocationUpdates(
                        getGoogleApiClient(), locationReq, AMapFragment.this);

            }
        };

        mAppPrefs = new DroidPlannerPrefs(context);

        final Bundle args = getArguments();
        if (args != null) {
            maxFlightPathSize = args.getInt(EXTRA_MAX_FLIGHT_PATH_SIZE);
        }



        return inflater.inflate(R.layout.fragment_amap, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		mMapView = (MapView) view.findViewById(R.id.mapbox_mapview);
		mMapView.onCreate(savedInstanceState);

	}


    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }


    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
    }

	@Override
	public void onStart() {
		super.onStart();
        mGApiClientMgr.start();
        if (mPanMode.get() == AutoPanMode.DRONE) {
            LocalBroadcastManager.getInstance(getActivity().getApplicationContext())
                    .registerReceiver(eventReceiver, eventFilter);
        }
		setUpMapIfNeeded();
		
	}



	@Override
	public void onStop() {
		super.onStop();
        LocalBroadcastManager.getInstance(getActivity().getApplicationContext())
                .unregisterReceiver(eventReceiver);
        mGApiClientMgr.stop();
		
	}

	@Override
	public void addFlightPathPoint(LatLong coord) {
        final LatLng position =  DroneHelper.CoordToGaodeLatLang(coord);

        if (maxFlightPathSize > 0) {
            if (flightPath == null) {
                PolylineOptions flightPathOptions = new PolylineOptions();
                flightPathOptions.color(FLIGHT_PATH_DEFAULT_COLOR)
                        .width(FLIGHT_PATH_DEFAULT_WIDTH).zIndex(1);
                flightPath = mMap.addPolyline(flightPathOptions);
            }

            List<LatLng> oldFlightPath = flightPath.getPoints();
            if (oldFlightPath.size() > maxFlightPathSize) {
                oldFlightPath.remove(0);
            }
            oldFlightPath.add(position);
            flightPath.setPoints(oldFlightPath);
        }
	}

    @Override
    public List<LatLong> projectPathIntoMap(List<LatLong> path) {
        List<LatLong> coords = new ArrayList<LatLong>();
        Projection projection = mMap.getProjection();

        for (LatLong point : path) {
            LatLng coord = projection.fromScreenLocation(new Point((int) point
                    .getLatitude(), (int) point.getLongitude()));
            coords.add(DroneHelper.GaodeLatLngToCoord(coord));
        }

        return coords;
    }

    private LatLngBounds getBounds(List<LatLng> pointsList) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : pointsList) {
            builder.include(point);
        }
        return builder.build();
    }

    @Override
    public void zoomToFit(List<LatLong> coords) {
        if (!coords.isEmpty()) {
            final List<LatLng> points = new ArrayList<LatLng>();
            for (LatLong coord : coords)
                points.add(DroneHelper.CoordToGaodeLatLang(coord));

            final LatLngBounds bounds = getBounds(points);
            CameraUpdate animation = CameraUpdateFactory.newLatLngBounds(bounds, 100);
            if(mMap != null)
                mMap.animateCamera(animation);

        }
    }

	@Override
	public void clearMarkers() {
        for (Marker marker : mBiMarkersMap.valueSet()) {
            marker.remove();
        }

        mBiMarkersMap.clear();
	
	}

	@Override
	public void clearFlightPath() {
        if (flightPath != null) {
            List<LatLng> oldFlightPath = flightPath.getPoints();
            oldFlightPath.clear();
            flightPath.setPoints(oldFlightPath);
        }
	}

	@Override
	public LatLong getMapCenter() {
		return DroneHelper.GaodeLatLngToCoord(mMap.getCameraPosition().target);
	}

	@Override
	public float getMapZoomLevel() {
		return mMap.getMaxZoomLevel();
	}

	@Override
	public Set<MarkerInfo> getMarkerInfoList() {
        return new HashSet<MarkerInfo>(mBiMarkersMap.keySet());
	}

	@Override
	public float getMaxZoomLevel() {
		return mMap.getMaxZoomLevel();
	}

	@Override
	public float getMinZoomLevel() {
		return mMap.getMinZoomLevel();
	}

	@Override
	public DPMapProvider getProvider() {
		return DPMapProvider.高德地图;
	}

	@Override
	public void goToDroneLocation() {
        Drone dpApi = getDroneApi();
        if (!dpApi.isConnected())
            return;

        Gps gps = dpApi.getGps();
        if (!gps.isValid()) {
            Toast.makeText(getActivity().getApplicationContext(), R.string.drone_no_location, Toast.LENGTH_SHORT).show();
            return;

        }

        final float currentZoomLevel = mMap.getCameraPosition().zoom;
        final LatLong droneLocation = gps.getPosition();
        updateCamera(droneLocation, (int) currentZoomLevel);
	}

	@Override
	public void goToMyLocation() {
        if (!mGApiClientMgr.addTask(mGoToMyLocationTask)) {
            Log.e(TAG, "Unable to add google api client task.");
        }
	}

	@Override
	public void loadCameraPosition() {
        final SharedPreferences settings = mAppPrefs.prefs;

        CameraPosition.Builder camera = new CameraPosition.Builder();
        camera.bearing(settings.getFloat(PREF_BEA, DEFAULT_BEARING));
        camera.tilt(settings.getFloat(PREF_TILT, DEFAULT_TILT));
        camera.zoom(settings.getFloat(PREF_ZOOM, DEFAULT_ZOOM_LEVEL));
        camera.target(new LatLng(settings.getFloat(PREF_LAT, DEFAULT_LATITUDE),
                settings.getFloat(PREF_LNG, DEFAULT_LONGITUDE)));

        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(camera.build()));
	}


	@Override
	public void removeMarkers(Collection<MarkerInfo> markerInfoList) {

        if (markerInfoList == null || markerInfoList.isEmpty()) {
            return;
        }

        for (MarkerInfo markerInfo : markerInfoList) {
            Marker marker = mBiMarkersMap.getValue(markerInfo);
            if (marker != null) {
                marker.remove();
                mBiMarkersMap.removeKey(markerInfo);
            }
        }
        mMap.invalidate();

		
	}

	@Override
	public void saveCameraPosition() {
        CameraPosition camera = mMap.getCameraPosition();
        mAppPrefs.prefs.edit()
                .putFloat(PREF_LAT, (float) camera.target.latitude)
                .putFloat(PREF_LNG, (float) camera.target.longitude)
                .putFloat(PREF_BEA, camera.bearing)
                .putFloat(PREF_TILT, camera.tilt)
                .putFloat(PREF_ZOOM, camera.zoom).apply();
	}

    private Drone getDroneApi() {
        return dpApp.getDrone();
    }


	@Override
	public void selectAutoPanMode(AutoPanMode target) {
        final AutoPanMode currentMode = mPanMode.get();
        if (currentMode == target)
            return;

        setAutoPanMode(currentMode, target);
	}

	private void setAutoPanMode(AutoPanMode current, AutoPanMode update) {
        if (mPanMode.compareAndSet(current, update)) {
            switch (current) {
                case DRONE:
                    LocalBroadcastManager.getInstance(getActivity().getApplicationContext())
                            .unregisterReceiver(eventReceiver);
                    break;

                case USER:
                    if (!mGApiClientMgr.addTask(mRemoveLocationUpdateTask)) {
                        Log.e(TAG, "Unable to add google api client task.");
                    }
                    break;

                case DISABLED:
                default:
                    break;
            }

            switch (update) {
                case DRONE:
                    LocalBroadcastManager.getInstance(getActivity().getApplicationContext()).registerReceiver
                            (eventReceiver, eventFilter);
                    break;

                case USER:
                    if (!mGApiClientMgr.addTask(mRequestLocationUpdateTask)) {
                        Log.e(TAG, "Unable to add google api client task.");
                    }
                    break;

                case DISABLED:
                default:
                    break;
            }
        }
		
	}

	@Override
	public void setMapPadding(int left, int top, int right, int bottom) {
       // mMap.setPadding(left, top, right, bottom);

	}

	@Override
	public void setOnMapClickListener(OnMapClickListener listener) {
		
		mMapClickListener = listener;
	}

	@Override
	public void setOnMapLongClickListener(OnMapLongClickListener listener) {
        mMapLongClickListener = listener;
	}

	@Override
	public void setOnMarkerClickListener(OnMarkerClickListener listener) {
        mMarkerClickListener = listener;
	}

	@Override
	public void setOnMarkerDragListener(OnMarkerDragListener listener) {
        mMarkerDragListener = listener;
	}

    private void updateCamera(final LatLong coord){
        if(coord != null){
            final float zoomLevel = mMap.getCameraPosition().zoom;
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(DroneHelper.CoordToGaodeLatLang(coord),
                    zoomLevel));
        }
    }

    @Override
    public void updateCamera(final LatLong coord, final float zoomLevel) {
        if (coord != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(DroneHelper.CoordToGaodeLatLang(coord),zoomLevel));
        }
    }

    @Override
    public void updateCameraBearing(float bearing){
        final CameraPosition cameraPosition = new CameraPosition(DroneHelper.CoordToGaodeLatLang(getMapCenter()) , getMapZoomLevel(), 0, bearing);
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
       
    }

	@Override
	public void updateDroneLeashPath(PathSource pathSource) {
        List<LatLong> pathCoords = pathSource.getPathPoints();
        final List<LatLng> pathPoints = new ArrayList<LatLng>(pathCoords.size());
        for (LatLong coord : pathCoords) {
            pathPoints.add(DroneHelper.CoordToGaodeLatLang(coord));
        }

        if (mDroneLeashPath == null) {
            PolylineOptions flightPath = new PolylineOptions();
            flightPath.color(DRONE_LEASH_DEFAULT_COLOR).width(
                    DroneHelper.scaleDpToPixels(DRONE_LEASH_DEFAULT_WIDTH,
                            getResources()));
            mDroneLeashPath = mMap.addPolyline(flightPath);
        }

        mDroneLeashPath.setPoints(pathPoints);
	}

	@Override
	public void updateMarker(MarkerInfo markerInfo) {
		updateMarker(markerInfo, markerInfo.isDraggable());
	}

	@Override
	public void updateMarker(MarkerInfo markerInfo, boolean isDraggable) {
		// if the drone hasn't received a gps signal yet
		// if the drone hasn't received a gps signal yet
		final LatLong coord = markerInfo.getPosition();
		if (coord == null) {
			return;
		}

		final LatLng position = DroneHelper.CoordToGaodeLatLang(coord);
		Marker marker = mBiMarkersMap.getValue(markerInfo);
		if (marker == null) {
			// Generate the marker
			generateMarker(markerInfo, position, isDraggable);
		} else {
			// Update the marker
			updateMarker(marker, markerInfo, position, isDraggable);
		}
	}
	
	private void generateMarker(MarkerInfo markerInfo, LatLng position, boolean isDraggable) {
        Log.v(TAG,"generateMarker");
        final MarkerOptions markerOptions = new MarkerOptions()
                .position(position)
                .draggable(isDraggable)
                .anchor(markerInfo.getAnchorU(), markerInfo.getAnchorV())
                .snippet(markerInfo.getSnippet()).title(markerInfo.getTitle());

        final Bitmap markerIcon = markerInfo.getIcon(getResources());
        if (markerIcon != null) {
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(markerIcon));
        }

        Marker marker = mMap.addMarker(markerOptions);
        mBiMarkersMap.put(markerInfo, marker);
	}

	private void updateMarker(Marker marker, MarkerInfo markerInfo, LatLng position,
			boolean isDraggable) {
		final Bitmap markerIcon = markerInfo.getIcon(getResources());
		if (markerIcon != null) {
			marker.setIcon(BitmapDescriptorFactory.fromBitmap(markerIcon));
		}

		
		marker.setAnchor(markerInfo.getAnchorU(), markerInfo.getAnchorV());
	
		marker.setPosition(position);
		marker.setRotateAngle(markerInfo.getRotation());
		marker.setSnippet(markerInfo.getSnippet());
		marker.setTitle(markerInfo.getTitle());
		marker.setDraggable(isDraggable);
		marker.setVisible(markerInfo.isVisible());
	}
	

	@Override
	public void updateMarkers(List<MarkerInfo> markersInfos) {
		for (MarkerInfo info : markersInfos) {
			updateMarker(info);
		}
	}

	@Override
	public void updateMarkers(List<MarkerInfo> markersInfos, boolean isDraggable) {
		for (MarkerInfo info : markersInfos) {
			updateMarker(info, isDraggable);
		}
	}

	@Override
	public void updateMissionPath(PathSource pathSource) {
        List<LatLong> pathCoords = pathSource.getPathPoints();
        final List<LatLng> pathPoints = new ArrayList<LatLng>(pathCoords.size());
        for (LatLong coord : pathCoords) {
            pathPoints.add(DroneHelper.CoordToGaodeLatLang(coord));
        }

        if (missionPath == null) {
            PolylineOptions pathOptions = new PolylineOptions();
            pathOptions.color(MISSION_PATH_DEFAULT_COLOR).width(
                    MISSION_PATH_DEFAULT_WIDTH);
            missionPath = mMap.addPolyline(pathOptions);
        }

        missionPath.setPoints(pathPoints);
	}

    @Override
    public void updateRealTimeFootprint(FootPrint footprint) {
        if (footprintPoly == null) {
            PolygonOptions pathOptions = new PolygonOptions();
            pathOptions.strokeColor(FOOTPRINT_DEFAULT_COLOR).strokeWidth(FOOTPRINT_DEFAULT_WIDTH);
            pathOptions.fillColor(FOOTPRINT_FILL_COLOR);

            for (LatLong vertex : footprint.getVertexInGlobalFrame()) {
                pathOptions.add(DroneHelper.CoordToGaodeLatLang(vertex));
            }
            footprintPoly = mMap.addPolygon(pathOptions);
        } else {
            List<LatLng> list = new ArrayList<LatLng>();
            for (LatLong vertex : footprint.getVertexInGlobalFrame()) {
                list.add(DroneHelper.CoordToGaodeLatLang(vertex));
            }
            footprintPoly.setPoints(list);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "User location changed.");
        if (mPanMode.get() == AutoPanMode.USER) {
            updateCamera(DroneHelper.LocationToCoord(location), (int) mMap.getCameraPosition().zoom);
        }

        if (mLocationListener != null) {
            mLocationListener.onLocationChanged(location);
        }
    }

    @Override
    public void skipMarkerClickEvents(boolean skip) {
        useMarkerClickAsMapClick = skip;
    }

    @Override
    public void zoomToFitMyLocation(final List<LatLong> coords) {
        mGApiClientMgr.addTask(mGApiClientMgr.new GoogleApiClientTask() {
            @Override
            protected void doRun() {
                final Location myLocation = LocationServices.FusedLocationApi.getLastLocation
                        (getGoogleApiClient());
                if (myLocation != null) {
                    final List<LatLong> updatedCoords = new ArrayList<LatLong>(coords);
                    updatedCoords.add(DroneHelper.LocationToCoord(myLocation));
                    zoomToFit(updatedCoords);
                } else {
                    zoomToFit(coords);
                }
            }
        });

    }

    @Override
    public void updatePolygonsPaths(List<List<LatLong>> paths) {
        for (Polygon poly : polygonsPaths) {
            poly.remove();
        }

        for (List<LatLong> contour : paths) {
            PolygonOptions pathOptions = new PolygonOptions();
            pathOptions.strokeColor(POLYGONS_PATH_DEFAULT_COLOR).strokeWidth(
                    POLYGONS_PATH_DEFAULT_WIDTH);
            final List<LatLng> pathPoints = new ArrayList<LatLng>(contour.size());
            for (LatLong coord : contour) {
                pathPoints.add(DroneHelper.CoordToGaodeLatLang(coord));
            }
            pathOptions.addAll(pathPoints);
            polygonsPaths.add(mMap.addPolygon(pathOptions));
        }
    }

    @Override
    public void setLocationListener(android.location.LocationListener receiver) {
        mLocationListener = receiver;
        //Update the listener with the last received location
        if (mLocationListener != null && isResumed()) {
            mGApiClientMgr.addTask(mGApiClientMgr.new GoogleApiClientTask() {
                @Override
                protected void doRun() {
                    final Location lastLocation = LocationServices.FusedLocationApi.getLastLocation
                            (getGoogleApiClient());
                    if (lastLocation != null) {
                        mLocationListener.onLocationChanged(lastLocation);
                    }
                }
            });
        }
    }

    @Override
    public void addCameraFootprint(FootPrint footprintToBeDraw) {
        PolygonOptions pathOptions = new PolygonOptions();
        pathOptions.strokeColor(FOOTPRINT_DEFAULT_COLOR).strokeWidth(FOOTPRINT_DEFAULT_WIDTH);
        pathOptions.fillColor(FOOTPRINT_FILL_COLOR);

        for (LatLong vertex : footprintToBeDraw.getVertexInGlobalFrame()) {
            pathOptions.add(DroneHelper.CoordToGaodeLatLang(vertex));
        }
        mMap.addPolygon(pathOptions);


    }

    @Override
    public void onMapClick(LatLng point) {
        Log.v("123",point.toString());

        if (mMapClickListener != null) {
            mMapClickListener.onMapClick(DroneHelper.GaodeLatLngToCoord(point));
        }
    }

    @Override
    public void onMapLongClick(LatLng point)
    {
        if (mMapLongClickListener != null) {
            mMapLongClickListener.onMapLongClick(DroneHelper.GaodeLatLngToCoord(point));
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker)
    {
        if (mMarkerDragListener != null) {
            final MarkerInfo markerInfo = mBiMarkersMap.getKey(marker);
            markerInfo.setPosition(DroneHelper.GaodeLatLngToCoord(marker.getPosition()));
            mMarkerDragListener.onMarkerDragStart(markerInfo);
        }
        return false;
    }

    @Override
    public void onMarkerDragEnd(Marker marker)
    {
        if (mMarkerDragListener != null && marker !=null ) {
            final MarkerInfo markerInfo = mBiMarkersMap.getKey(marker);
            markerInfo.setPosition(DroneHelper.GaodeLatLngToCoord(marker.getPosition()));
            mMarkerDragListener.onMarkerDragEnd(markerInfo);
        }
    }


    @Override
    public void onMarkerDragStart(Marker marker)
    {
        if (mMarkerDragListener != null) {
            final MarkerInfo markerInfo = mBiMarkersMap.getKey(marker);
            markerInfo.setPosition(DroneHelper.GaodeLatLngToCoord(marker.getPosition()));
            mMarkerDragListener.onMarkerDragStart(markerInfo);
        }
    }

    @Override
    public void onMarkerDrag(Marker marker)
    {
        if (mMarkerDragListener != null) {
            final MarkerInfo markerInfo = mBiMarkersMap.getKey(marker);
            markerInfo.setPosition(DroneHelper.GaodeLatLngToCoord(marker.getPosition()));
            mMarkerDragListener.onMarkerDrag(markerInfo);
        }
    }


    @Override
    public void onLocationChanged(AMapLocation aLocation) {
        if (mListener != null && aLocation != null) {
            Log.v("123", "onLocation" + aLocation.toString());
            mListener.onLocationChanged(aLocation);// 显示系统小蓝点
        }
    }

    /**
     * 激活定位
     */
    @Override
    public void activate(OnLocationChangedListener listener) {
        Log.v("123", "active");
        mListener = listener;
        if (mAMapLocationManager == null) {
            mAMapLocationManager = LocationManagerProxy.getInstance(getActivity().getApplicationContext());
			/*
			 * mAMapLocManager.setGpsEnable(false);
			 * 1.0.2版本新增方法，设置true表示混合定位中包含gps定位，false表示纯网络定位，默认是true Location
			 * API定位采用GPS和网络混合定位方式
			 * ，第一个参数是定位provider，第二个参数时间最短是2000毫秒，第三个参数距离间隔单位是米，第四个参数是定位监听者
			 */
            mAMapLocationManager.requestLocationData(LocationProviderProxy.AMapNetwork, 2000, 10, this);

        }
    }

    /**
     * 停止定位
     */
    @Override
    public void deactivate() {
        mListener = null;
        if (mAMapLocationManager != null) {
            mAMapLocationManager.removeUpdates(this);
            mAMapLocationManager.destory();
        }
        mAMapLocationManager = null;
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }


}
