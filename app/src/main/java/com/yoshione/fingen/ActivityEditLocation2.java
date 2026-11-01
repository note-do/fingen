package com.yoshione.fingen;

import com.yoshione.fingen.utils.DebouncingOnClickListener;
import com.yoshione.fingen.databinding.ActivityEditLocation2Binding;
import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.yoshione.fingen.dao.LocationsDAO;
import com.yoshione.fingen.widgets.ToolbarActivity;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

//import permissions.dispatcher.RuntimePermissions;

/**
 * Created by slv on 07.03.2016.
 * a
 */
@RuntimePermissions
public class ActivityEditLocation2 extends ToolbarActivity implements OnMapReadyCallback,
        GoogleMap.OnMapClickListener, GoogleMap.OnCameraMoveStartedListener {
    private ActivityEditLocation2Binding mBinding;
    EditText editTextName;
    Button mButtonSaveLocation;
    GoogleMap map;
    private com.yoshione.fingen.model.Location location;

    private double lat = 0;
    private double lon = 0;
    private LocationManager locationManager;
    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(android.location.Location location) {
            if (location == null)
                return;
            lat = location.getLatitude();
            lon = location.getLongitude();
            updateLocationOnMap();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_edit_location2;
    }

    @Override
    protected String getLayoutTitle() {
        if (location == null) {
            return "";
        }
        if (location.getID() < 0) {
            return getResources().getString(R.string.new_new_location);
        } else {
            return getResources().getString(R.string.ent_edit_location);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityEditLocation2Binding.bind(getContentRoot());
        editTextName = mBinding.editTextName;
        mButtonSaveLocation = mBinding.buttonSaveLocation;
        mBinding.buttonSaveLocation.setOnClickListener(new DebouncingOnClickListener(v -> onSaveClick()));

        if (savedInstanceState != null) {
            location = savedInstanceState.getParcelable("location");
        } else {
            location = getIntent().getParcelableExtra("location");
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getLayoutTitle());
        }

        editTextName.setText(location.getName());
        editTextName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                location.setName(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setOnMapClickListener(this);
        map.setOnCameraMoveStartedListener(this);
        map.getUiSettings().setMyLocationButtonEnabled(true);
        enableMyLocation();
        if (location != null && !location.isUndefined()) {
            LatLng latLng = new LatLng(location.getLat(), location.getLon());
            addMarker(latLng);
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
        }
    }

    private void enableMyLocation() {
        if (map != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.setMyLocationEnabled(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ActivityEditLocation2PermissionsDispatcher.startDetectCoordsWithPermissionCheck(this);
    }

    @Override
    protected void onPause() {
        removeUpdates();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("location", location);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        location = savedInstanceState.getParcelable("location");
    }

    public void onSaveClick() {
        if (location.getName().isEmpty()) return;
        LocationsDAO locationsDAO = LocationsDAO.getInstance(getApplicationContext());
        try {
            locationsDAO.createModel(location);
        } catch (Exception e) {
            Toast.makeText(this, R.string.msg_error_on_write_to_db, Toast.LENGTH_SHORT).show();
            return;
        }
        finish();
    }

    private void updateLocationOnMap() {
        if (location.getID() < 0) {
            final LatLng latLng = new LatLng(lat, lon);
            if (map != null) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
            }
        }
    }

    private void removeUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (locationManager != null) {
                locationManager.removeUpdates(locationListener);
            }
        }
    }

    @Override
    public void onCameraMoveStarted(int reason) {
        if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
            removeUpdates();
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        if (latLng != null) {
            removeUpdates();
            location.setLat(latLng.latitude);
            location.setLon(latLng.longitude);
            addMarker(latLng);
        }
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    private void addMarker(LatLng latLng) {
        map.clear();
        location.setLat(latLng.latitude);
        location.setLon(latLng.longitude);
        map.addMarker(new MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.icon_location_pin)));
    }

    @NeedsPermission({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void startDetectCoords() {
        enableMyLocation();
        if (location.getID() < 0) {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

            if (locationManager != null && locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER))
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
                }

            if (locationManager != null && locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER))
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                }
        }
    }

    @OnShowRationale({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void showRationaleForContact(PermissionRequest request) {
        showRationaleDialog(R.string.msg_permission_location_rationale, request);
    }

    @OnPermissionDenied({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void onLocationDenied() {
        Toast.makeText(this, R.string.msg_permission_location_denied, Toast.LENGTH_SHORT).show();
    }

    @OnNeverAskAgain({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void onLocationNeverAskAgain() {

    }

    private void showRationaleDialog(@StringRes int messageResId, final PermissionRequest request) {
        new AlertDialog.Builder(this)
                .setPositiveButton(R.string.act_next, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        request.proceed();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        request.cancel();
                    }
                })
                .setCancelable(false)
                .setMessage(messageResId)
                .show();
    }
}
