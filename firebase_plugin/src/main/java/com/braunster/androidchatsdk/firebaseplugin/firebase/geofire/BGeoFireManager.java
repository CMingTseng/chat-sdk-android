package com.braunster.androidchatsdk.firebaseplugin.firebase.geofire;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import com.braunster.androidchatsdk.firebaseplugin.firebase.FirebasePaths;
import com.braunster.androidchatsdk.firebaseplugin.firebase.wrappers.BUserWrapper;
import com.braunster.chatsdk.Utils.Debug;
import com.braunster.chatsdk.dao.BUser;
import com.braunster.chatsdk.interfaces.GeoInterface;
import com.braunster.chatsdk.network.AbstractGeoFireManager;
import com.braunster.chatsdk.network.BNetworkManager;
import com.firebase.client.FirebaseError;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;

import org.jdeferred.DoneCallback;

import java.util.List;

import timber.log.Timber;

/**
 * Created by Erk on 30.03.2016.
 */
public class BGeoFireManager extends AbstractGeoFireManager implements LocationListener {

    public GeoInterface delegate;

    private static final String TAG = BGeoFireManager.class.getSimpleName();
    private static final boolean DEBUG = Debug.BGeoFireManager;

    private double bSearchRadius = 50000.0;
    private long bLocationUpdateTime = 5000;
    private float bMinDistance = 0.1f;
    private String currentProvider = "";

    private LocationManager locationManager = null;

    private boolean isUpdating;

    private static BGeoFireManager instance;
    private static Context context;

    public static BGeoFireManager sharedManager() {
        if(instance == null) {
            instance = new BGeoFireManager();
        }

        return instance;
    }

    @Override
    public void setGeoDelegate(GeoInterface geoDelegate) {
        delegate = geoDelegate;
    }

    @Override
    public void start() {
        if (currentProvider != "") {
            locationManager.requestLocationUpdates(currentProvider, bLocationUpdateTime, bMinDistance, this);
            startUpdatingUserLocation();
            findNearbyUsersWithRadius(bSearchRadius);
        }
    }

    public void init(Context ctx) {
        context = ctx;
        if (locationManager == null) {
            if(DEBUG) Timber.v("init GeoFireManager");
            locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if(DEBUG) Timber.v("gps provider");
                currentProvider = LocationManager.GPS_PROVIDER;
            } else {
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    if(DEBUG) Timber.v("network provider");
                    currentProvider = LocationManager.NETWORK_PROVIDER;
                } else {
                    if(DEBUG) Timber.v("no provider enabled");
                }
            }
        }
    }

    public void findNearbyUsersWithRadius(double radiusInMetres) {

        if(getCurrentGeoLocation() == null) return;

        GeoFire geoFire = new GeoFire(FirebasePaths.locationRef());

        GeoQuery circleQuery = geoFire.queryAtLocation(getCurrentGeoLocation(), radiusInMetres / 1000.0);

        final String userEntityID = BNetworkManager.sharedManager().getNetworkAdapter().currentUserModel().getEntityID();

        circleQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String entityID, final GeoLocation location) {
                if(!entityID.equals(userEntityID))
                {
                    BUserWrapper.initWithEntityId(entityID).once().then(new DoneCallback<BUser>() {
                        @Override
                        public void onDone(BUser bUser) {
                            BUserWrapper userWrapper = BUserWrapper.initWithModel(bUser);

                            delegate.userAdded(userWrapper.getModel(), location);
                        }
                    });
                }
            }

            @Override
            public void onKeyExited(String entityID) {
                if(!entityID.equals(userEntityID))
                {
                    BUserWrapper userWrapper = BUserWrapper.initWithEntityId(entityID);

                    delegate.userRemoved(userWrapper.getModel());
                }
            }

            @Override
            public void onKeyMoved(String entityID, GeoLocation location) {
                if(!entityID.equals(userEntityID))
                {
                    BUserWrapper userWrapper = BUserWrapper.initWithEntityId(entityID);

                    delegate.userMoved(userWrapper.getModel(), location);
                }
            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(FirebaseError error) {

            }
        });
    }

    private void startUpdatingUserLocation() {
        if(!isUpdating) {
            updateCurrentUserLocation();

            isUpdating = true;
        }
    }

    private void stopUpdatingUserLocation() {
        if(!isUpdating) return;

        isUpdating = false;
    }

    private GeoLocation getCurrentGeoLocation() {
        Location center = getCurrentUserLocation();

        if(center == null) return null;

        GeoLocation centerGeo = new GeoLocation(center.getLatitude(), center.getLongitude());

        return centerGeo;
    }

    private void updateCurrentUserLocation() {
        if(getCurrentGeoLocation() == null) return;

        BUser currentUser = BNetworkManager.sharedManager().getNetworkAdapter().currentUserModel();

        if(currentUser != null) {
            GeoFire geoFire = new GeoFire(FirebasePaths.locationRef());
            geoFire.setLocation(currentUser.getEntityID(), getCurrentGeoLocation());
        }

        delegate.setCurrentUserGeoLocation(getCurrentGeoLocation());
    }

    public Location getCurrentUserLocation() {
        List<String> providers = locationManager.getProviders(true);
        Location bestLocation = null;

        for (String provider : providers) {
            Location l = locationManager.getLastKnownLocation(provider);
            if (l == null) {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                // Found best last known location: %s", l);
                bestLocation = l;
            }
        }

        return bestLocation;
    }

    @Override
    public void onLocationChanged(Location location) {
        if(DEBUG) Timber.e("Location changed: " + location.toString());

        if(isUpdating) {
            updateCurrentUserLocation();
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if(DEBUG) Timber.v(provider + " " + status);
    }

    @Override
    public void onProviderDisabled(String provider) {
        stopUpdatingUserLocation();
    }

    @Override
    public void onProviderEnabled(String provider) {
        startUpdatingUserLocation();
    }
}
