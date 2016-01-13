package za.co.rosstapson.anywall;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseQuery;
import com.parse.ParseQueryAdapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements LocationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 92;
    private static final int MILLISECONDS_PER_SECOND = 1000;
    private static final int UPDATE_INTERVAL_IN_SECONDS = 5;
    private static final int FAST_CEILING_IN_SECONDS = 1;
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;
    private static final long FAST_INTERVAL_CEILING_IN_MILLISECONDS = MILLISECONDS_PER_SECOND * FAST_CEILING_IN_SECONDS;
    private static final float METERS_PER_FEET = 0.3048f;
    private static final int METERS_PER_KILOMETER = 1000;
    private static final double OFFSET_CALCULATION_INIT_DIFF = 1.0;
    private static final float OFFSET_CALCULATION_ACCURACY = 0.01f;
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 123;


    private Location currentLocation;
    private Location lastLocation;
    private int MAX_POST_SEARCH_DISTANCE = 100;
    private int MAX_POST_SEARCH_RESULTS = 20;
    private SupportMapFragment mapFragment;
    private Circle mapCircle;
    private float radius;
    private float lastRadius;
    private final Map<String, Marker> mapMarkers = new HashMap<>();
    private int mostRecentMapUpdate;
    private boolean hasSetupInitialLocation;
    private String selectedPostObjectId;
    private LocationRequest locationRequest;
    private GoogleApiClient locationClient;
    private ParseQueryAdapter<AnywallPost> postsQueryAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        radius = Application.getSearchDistance();
        lastRadius = radius;
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar( toolbar);

        if (Application.APPDEBUG) {
            Log.d(Application.APPTAG, "ZOMG. about to check gps enabled.");
        }
        checkGPSenabled();

        // API 23 and up: request permissions at runtime
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_LOCATION);

        }
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        locationRequest.setFastestInterval(FAST_INTERVAL_CEILING_IN_MILLISECONDS);
        locationClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        ParseQueryAdapter.QueryFactory<AnywallPost> factory = new ParseQueryAdapter.QueryFactory<AnywallPost>() {
            @Override
            public ParseQuery<AnywallPost> create() {
                Location myLoc = (currentLocation == null) ? lastLocation : currentLocation;
                ParseQuery<AnywallPost> query = AnywallPost.getQuery();
                query.include("user");
                query.orderByDescending("createdAt");
                query.whereWithinKilometers("location", getPointFromLocation(myLoc), radius * METERS_PER_FEET / METERS_PER_KILOMETER);
                query.setLimit(MAX_POST_SEARCH_RESULTS);
                return query;
            }
        };
        postsQueryAdapter = new ParseQueryAdapter<AnywallPost>(this, factory) {
            public View getItemView(AnywallPost post, View view, ViewGroup parent) {
                if (view == null) {
                    view = View.inflate(getContext(), R.layout.anywall_post_item, null);
                }
                TextView contentView = (TextView) view.findViewById(R.id.content_view);
                TextView usernameView = (TextView) view.findViewById(R.id.username_view);
                contentView.setText(post.getText());
                usernameView.setText(post.getUser().getUsername());
                return view;
            }
        };
        //disable automatic loading when the adapter is attached to a view
        postsQueryAdapter.setAutoload(false);
        //disable pagination, we'll manage the query limit ourselves
        postsQueryAdapter.setPaginationEnabled(false);
        ListView postListView = (ListView) findViewById(R.id.posts_listview);
        postListView.setAdapter(postsQueryAdapter);

        postListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final AnywallPost item = postsQueryAdapter.getItem(position);
                selectedPostObjectId = item.getObjectId();
                mapFragment.getMapAsync(new OnMapReadyCallback() {
                    @Override
                    public void onMapReady(GoogleMap googleMap) {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(item.getLocation().getLatitude(), item.getLocation().getLongitude())), new GoogleMap.CancelableCallback() {
                            @Override
                            public void onFinish() {
                                Marker marker = mapMarkers.get(item.getObjectId());
                                if (marker != null) {
                                    marker.showInfoWindow();
                                }
                            }

                            @Override
                            public void onCancel() {

                            }
                        });
                    }
                });
                Marker marker = mapMarkers.get(item.getObjectId());
                if (marker != null) {
                    marker.showInfoWindow();
                }
            }
        });


        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        //enable the current location "blue dot"
        // if google play services aren't available, here's the point at which our app will throw its
        // toys right out the cot. try to handle it gracefully.
        try {

            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    // recheck permissions as per API 23
                    if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions

                        return;
                    }
                    googleMap.setMyLocationEnabled(true);
                    googleMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
                        @Override
                        public void onCameraChange(CameraPosition cameraPosition) {
                            doMapQuery();
                        }
                    });
                }
            });
        } catch (Throwable e) {
            if (Application.APPDEBUG) {
                Log.d(Application.APPTAG, "Google services unavailable.");
            }
            if (!servicesConnected()) {
                new AlertDialog.Builder(this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("Balls.")
                        .setMessage("Google Services are not available.")
                        .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finishAffinity();
                            }
                        })
                        .show();
            }
        }

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Location myLoc = (currentLocation == null) ? lastLocation : currentLocation;
                if (myLoc == null) {
                    Snackbar.make(findViewById(R.id.coordinator_layout), "Please try again after your location appears on the map", Snackbar.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(MainActivity.this, PostActivity.class);
                intent.putExtra(Application.INTENT_EXTRA_LOCATION, myLoc);
                startActivity(intent);
            }
        });
    }
    //helper method for callbacks.
    public Context getContext() {
        return (Context)this;
    }

    private void doMapQuery() {
        final int myUpdateNumber = ++mostRecentMapUpdate;

        Location myLoc = (currentLocation == null) ? lastLocation : currentLocation;
        if (myLoc == null) {
            cleanUpMarkers(new HashSet<String>());
            return;
        }
        final ParseGeoPoint myPoint = getPointFromLocation(myLoc);
        ParseQuery<AnywallPost> mapQuery = AnywallPost.getQuery();
        mapQuery.whereWithinKilometers("location", myPoint, MAX_POST_SEARCH_DISTANCE);
        mapQuery.include("user");
        mapQuery.orderByDescending("createdAt");
        mapQuery.setLimit(MAX_POST_SEARCH_RESULTS);
        mapQuery.findInBackground(new FindCallback<AnywallPost>() {
            @Override
            public void done(List<AnywallPost> objects, ParseException e) {
                if (e != null) {
                    if (Application.APPDEBUG) {
                        Log.d(Application.APPTAG, "An error occurred while querying for map posts", e);
                    }
                    return;
                }
                //make sure we're processing results from the most recent update
                //as there may be more than one in process

                if (myUpdateNumber != mostRecentMapUpdate) {
                    return;
                }
                //posts to show on the map
                Set<String> toKeep = new HashSet<>();
                for (AnywallPost post : objects) {
                    toKeep.add(post.getObjectId());
                    Marker oldMarker = mapMarkers.get(post.getObjectId());
                    MarkerOptions markerOptions = new MarkerOptions().position(new LatLng(post.getLocation().getLatitude(), post.getLocation().getLongitude()));
                    //set up the marker properties based on if it is in the search radius
                    if (post.getLocation().distanceInKilometersTo(myPoint) > radius * METERS_PER_FEET / METERS_PER_KILOMETER) {
                        //check for an existing out-of-range marker
                        if (oldMarker != null) {
                            if (oldMarker.getSnippet() == null) {
                                continue;
                            } else {
                                oldMarker.remove();
                            }
                        }
                        //display a red marker with a predefined title and no snippiet
                        markerOptions = markerOptions.title(getResources().getString(R.string.post_out_of_range))
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                    } else {
                        //check for an existing in-range marker
                        if (oldMarker != null) {
                            if (oldMarker.getSnippet() != null) {
                                continue;
                            } else {
                                oldMarker.remove();
                            }
                        }
                        //display a green marker with the post information
                        markerOptions = markerOptions.title(post.getText())
                                .snippet(post.getUser().getUsername())
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                    }
                    // add a new marker
                    // how to user getMapAsync() here?
                    // problem is we're called getMapAsync() already in too many contexts to have
                    // one easy callback method, and here markerOptions would need
                    // to be declared "final".
                    Marker marker = mapFragment.getMap().addMarker(markerOptions);
                    mapMarkers.put(post.getObjectId(), marker);
                    if (post.getObjectId().equals(selectedPostObjectId)) {
                        marker.showInfoWindow();
                        selectedPostObjectId = null;
                    }
                }
                cleanUpMarkers(toKeep);
            }
        });
    }

    private ParseGeoPoint getPointFromLocation(Location loc) {
        return new ParseGeoPoint(loc.getLatitude(), loc.getLongitude());
    }

    private void cleanUpMarkers(Set<String> markersToKeep) {
        for (String objId : new HashSet<>(mapMarkers.keySet())) {
            if (!markersToKeep.contains(objId)) {
                Marker marker = mapMarkers.get(objId);
                marker.remove();
                mapMarkers.get(objId).remove();
                mapMarkers.remove(objId);
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length == 0
                        || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                     Snackbar.make(findViewById(R.id.coordinator_layout), "Location permission not granted", Snackbar.LENGTH_SHORT).show();

                }
            }
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        menu.findItem(R.id.action_settings).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return true;
            }
        });
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
    public void onConnected(Bundle bundle) {
        if (Application.APPDEBUG) {
            Log.d(Application.APPTAG, "Connected to Google Play services. ZOMG.");
        }
        currentLocation = getLocation();
        startPeriodicUpdates();
    }

    public void onDisconnected() {
        if (Application.APPDEBUG) {
            Log.d(Application.APPTAG, "Disconnected from location services");
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(Application.APPTAG, "GoogleApiClient has been suspended.");
    }

    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;
        if (lastLocation != null && getPointFromLocation(location).distanceInKilometersTo(getPointFromLocation(lastLocation)) < 0.01) {
            // location hasn't changed more than 10 meters, so ignore.
            return;
        }
        lastLocation = location;
        LatLng myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        if (!hasSetupInitialLocation) {
            //zoom to current location
            updateZoom(myLatLng);
            hasSetupInitialLocation = true;
        }
        //update map radius indicator
        updateCircle(myLatLng);
        doMapQuery();
        doListQuery();
    }

    private void updateCircle(LatLng myLatLng) {
        if (mapCircle == null) {
            mapCircle = mapFragment.getMap()
                    .addCircle(new CircleOptions()
                            .center(myLatLng)
                            .radius(radius * METERS_PER_FEET));
            int baseColour = Color.DKGRAY;
            mapCircle.setStrokeColor(baseColour);
            mapCircle.setStrokeWidth(2);
            mapCircle.setFillColor(Color.argb(50, Color.red(baseColour), Color.green(baseColour), Color.blue(baseColour)));
        }
        mapCircle.setCenter(myLatLng);
        mapCircle.setRadius(radius * METERS_PER_FEET);
    }

    private void updateZoom(LatLng myLatLng) {
        LatLngBounds bounds = calculateBoundsWithCenter(myLatLng);
        mapFragment.getMap().animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 5));
    }

    private double calculateLatLngOffset(LatLng myLatLng, boolean bLatOffset) {
        // the return offset, initialised to the default difference
        double latLngOffset = OFFSET_CALCULATION_INIT_DIFF;
        float desiredOffsetInMeters = radius * METERS_PER_FEET;
        float[] distance = new float[1];
        boolean foundMax = false;
        double foundMinDiff = 0;

        do {
            //calculate the distance between the point of interest
            // and the current offset in the lat or long direction
            if (bLatOffset) {
                Location.distanceBetween(myLatLng.latitude, myLatLng.longitude, myLatLng.latitude + latLngOffset, myLatLng.longitude, distance);
            } else {
                Location.distanceBetween(myLatLng.latitude, myLatLng.longitude, myLatLng.latitude,
                        myLatLng.longitude + latLngOffset, distance);
            }
            float distanceDiff = distance[0] - desiredOffsetInMeters;
            if (distanceDiff < 0) {
                if (!foundMax) {
                    foundMinDiff = latLngOffset;
                    latLngOffset *= 2;
                } else {
                    double tmp = latLngOffset;
                    // increase the calculated offset at a slower pace
                    latLngOffset += (latLngOffset - foundMinDiff) / 2;
                    foundMinDiff = tmp;
                }
            } else {
                // overshot
                latLngOffset -= (latLngOffset - foundMinDiff) / 2;
                foundMax = true;
            }
        } while (Math.abs(distance[0] - desiredOffsetInMeters) > OFFSET_CALCULATION_ACCURACY);
        return latLngOffset;
    }

    /*
   * Helper method to calculate the bounds for map zooming
   */
    LatLngBounds calculateBoundsWithCenter(LatLng myLatLng) {
        // Create a bounds
        LatLngBounds.Builder builder = LatLngBounds.builder();

        // Calculate east/west points that should to be included
        // in the bounds
        double lngDifference = calculateLatLngOffset(myLatLng, false);
        LatLng east = new LatLng(myLatLng.latitude, myLatLng.longitude + lngDifference);
        builder.include(east);
        LatLng west = new LatLng(myLatLng.latitude, myLatLng.longitude - lngDifference);
        builder.include(west);

        // Calculate north/south points that should to be included
        // in the bounds
        double latDifference = calculateLatLngOffset(myLatLng, true);
        LatLng north = new LatLng(myLatLng.latitude + latDifference, myLatLng.longitude);
        builder.include(north);
        LatLng south = new LatLng(myLatLng.latitude - latDifference, myLatLng.longitude);
        builder.include(south);

        return builder.build();
    }

    private void startPeriodicUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Application.APPDEBUG) {
                Log.d(Application.APPTAG, "startPeriodicUpdates(), permissions not granted");
            }
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(locationClient, locationRequest, this);
    }

    private void stopPeriodicUpdates() {
        locationClient.disconnect();
    }

    private Location getLocation() {
        Location location = null;
        // recheck google services available
        if (servicesConnected()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions

                Snackbar.make(findViewById(R.id.coordinator_layout), "Location permission not granted", Snackbar.LENGTH_SHORT).show();
                return null;
            }
            // eh???
            try {
                location = LocationServices.FusedLocationApi.getLastLocation(locationClient);
            }
            catch (Exception e) {
                if (Application.APPDEBUG) {
                    Log.d(Application.APPTAG, e.getMessage());
                }
            }
        }
        return location;
    }

    private void doListQuery() {
        Location myLoc = (currentLocation == null) ? lastLocation: currentLocation;
        if (myLoc != null){
            postsQueryAdapter.loadObjects();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // google play can resolve some errors it detects. if the error has a resolution,
        // try sending an intent to start a google play services activity that can resolve the error
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            }
            catch (IntentSender.SendIntentException e) {
                if (Application.APPDEBUG) {
                    Log.d(Application.APPTAG, "An error occurred when connecting to location services. ZOMG", e);
                }
            }
        }
        else {
            showErrorDialog(connectionResult.getErrorCode());
        }
    }
    /*
  * Show a dialog returned by Google Play services for the connection error code
  */
    private void showErrorDialog(int errorCode) {
        // Get the error dialog from Google Play services
        Dialog errorDialog =
                GoogleApiAvailability.getInstance().getErrorDialog(this, errorCode,
                        CONNECTION_FAILURE_RESOLUTION_REQUEST);

        // If Google Play services can provide an error dialog
        if (errorDialog != null) {
            ErrorDialogFragment errorFragment = new ErrorDialogFragment();
            errorFragment.setDialog(errorDialog);
            errorFragment.show(getSupportFragmentManager(), Application.APPTAG);
        }
    }
    public void onStop() {
        if (locationClient.isConnected()) {
            stopPeriodicUpdates();
        }
        locationClient.disconnect();
        super.onStop();
    }
    public void onStart() {
        super.onStart();
        locationClient.connect();
    }
    @Override
    public void onResume() {
        super.onResume();
        Application.getConfigHelper().fetchConfigIfNeeded();
        radius = Application.getSearchDistance();
        //check the last saved location to show cached data if it's available
        if (lastLocation != null) {
            LatLng myLatLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
            //if the search distance preference has been changed, move map to new bounds
            if (lastRadius != radius){
                updateZoom(myLatLng);

            }
            updateCircle(myLatLng);

        }
        lastRadius = radius;
        doMapQuery();
        doListQuery();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case CONNECTION_FAILURE_RESOLUTION_REQUEST:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        if (Application.APPDEBUG) {
                            Log.d(Application.APPTAG, "Connected to Google Play services. ZOMG.");
                        }
                        break;
                    default:
                        if (Application.APPDEBUG) {
                            Log.d(Application.APPTAG, "Could not resolve connection issues. ZOMG dammit.");
                        }
                        break;
                }
            default:
                if (Application.APPDEBUG) {
                    Log.d(Application.APPTAG, "Unknown request code received for the activity. Oh ZOMG.");
                }
                break;
        }
    }
    //verify that Google Play services is available before making a request.
    private boolean servicesConnected() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (ConnectionResult.SUCCESS == resultCode) {
            if (Application.APPDEBUG) {
                Log.d(Application.APPTAG, "Google Play services available. ZOMG.");
            }
            return true;
        }
        else {
            Dialog dialog = apiAvailability.getErrorDialog(this, resultCode, 0);
            if (dialog != null) {
                ErrorDialogFragment errorFragment = new ErrorDialogFragment();
                errorFragment.setDialog(dialog);
                errorFragment.show(getSupportFragmentManager(), Application.APPTAG);
            }
            return false;
        }
    }

    //define our own errordialogfragment
    public static class ErrorDialogFragment extends DialogFragment {
        private Dialog mDialog;

        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }
    private void checkGPSenabled() {
        LocationManager locationManager;
        boolean isEnabled = false;
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            isEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        }
        catch (Exception e) {
            if (Application.APPDEBUG) {
                Log.d(Application.APPTAG, "zomg: " + e.getMessage());
            }
        }

        if(!isEnabled) {
            if (Application.APPDEBUG) {
                Log.d(Application.APPTAG, "GPS not enabled.");
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.gps_not_found_title);  // GPS not found
            builder.setMessage(R.string.gps_not_found_message); // Want to enable?
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogInterface, int i) {
                    startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                }
            });
            builder.setNegativeButton(R.string.no, null);
            ErrorDialogFragment errorFragment = new ErrorDialogFragment();
            errorFragment.setDialog(builder.create());
            errorFragment.show(getSupportFragmentManager(), Application.APPTAG);
            return;
        }
        else {
            if (Application.APPDEBUG) {
                Log.d(Application.APPTAG, "GPS reads as enabled.");
            }
        }
    }
}
