package com.rescuereach.citizen.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.rescuereach.R;
import com.rescuereach.data.model.EmergencyService;
import com.rescuereach.util.LocationManager;

import java.util.ArrayList;
import java.util.List;

public class SafetyFeaturesFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "SafetyFeaturesFragment";
    private static final float DEFAULT_ZOOM = 14f;

    private MapView mapView;
    private GoogleMap googleMap;
    private LocationManager locationManager;

    private List<EmergencyService> emergencyServices = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationManager = new LocationManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_safety_features, container, false);

        // Initialize MapView
        mapView = rootView.findViewById(R.id.map_view_safety);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        return rootView;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.setInfoWindowAdapter(new CustomInfoWindowAdapter());
        googleMap.setOnInfoWindowClickListener(this::onInfoWindowClick);

        // Request location permissions if not granted
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissions();
            return;
        }

        googleMap.setMyLocationEnabled(true);
        fetchAndDisplayEmergencyServices();
    }

    private void requestLocationPermissions() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onMapReady(googleMap);
            } else {
                Toast.makeText(requireContext(), R.string.location_permission_required, Toast.LENGTH_SHORT).show();
                disableLocationFeatures();
            }
        }
    }

    private void disableLocationFeatures() {
        googleMap.setMyLocationEnabled(false);
    }

    private void fetchAndDisplayEmergencyServices() {
        locationManager.getCurrentLocation(location -> {
            if (location != null) {
                LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, DEFAULT_ZOOM));

                // Mock data for demonstration purposes
                emergencyServices.add(new EmergencyService("1", "Downtown Fire Station", "FIRE", new LatLng(userLatLng.latitude + 0.01, userLatLng.longitude + 0.01), "123 Main St", "101", 1.2));
                emergencyServices.add(new EmergencyService("2", "City Hospital", "MEDICAL", new LatLng(userLatLng.latitude - 0.01, userLatLng.longitude - 0.01), "456 Elm St", "108", 2.5));
                emergencyServices.add(new EmergencyService("3", "Central Police Station", "POLICE", new LatLng(userLatLng.latitude + 0.02, userLatLng.longitude - 0.02), "789 Oak St", "100", 3.0));

                for (EmergencyService service : emergencyServices) {
                    addMarker(service);
                }
            } else {
                Toast.makeText(requireContext(), R.string.location_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addMarker(EmergencyService service) {
        LatLng position = service.getLocation();
        float color;

        switch (service.getType()) {
            case "FIRE":
                color = BitmapDescriptorFactory.HUE_ORANGE;
                break;
            case "POLICE":
                color = BitmapDescriptorFactory.HUE_BLUE;
                break;
            case "MEDICAL":
                color = BitmapDescriptorFactory.HUE_RED;
                break;
            default:
                color = BitmapDescriptorFactory.HUE_GREEN;
                break;
        }

        googleMap.addMarker(new MarkerOptions()
                        .position(position)
                        .title(service.getName())
                        .snippet(service.getAddress())
                        .icon(BitmapDescriptorFactory.defaultMarker(color)))
                .setTag(service);
    }

    private void onInfoWindowClick(Marker marker) {
        EmergencyService service = (EmergencyService) marker.getTag();
        if (service != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String uri = "geo:" + service.getLocation().latitude + "," + service.getLocation().longitude +
                    "?q=" + Uri.encode(service.getName() + ", " + service.getAddress());
            intent.setData(Uri.parse(uri));
            startActivity(intent);
        }
    }

    private class CustomInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {
        private final View infoWindow;

        CustomInfoWindowAdapter() {
            infoWindow = LayoutInflater.from(requireContext()).inflate(R.layout.custom_info_window, null);
        }

        @Override
        public View getInfoWindow(Marker marker) {
            EmergencyService service = (EmergencyService) marker.getTag();
            if (service != null) {
                ((TextView) infoWindow.findViewById(R.id.text_service_name)).setText(service.getName());
                ((TextView) infoWindow.findViewById(R.id.text_service_address)).setText(service.getAddress());
                ((TextView) infoWindow.findViewById(R.id.text_service_distance)).setText(getString(R.string.distance_format, service.getDistance()));

                infoWindow.findViewById(R.id.icon_phone).setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + service.getPhoneNumber()));
                    startActivity(intent);
                });
            }
            return infoWindow;
        }

        @Override
        public View getInfoContents(Marker marker) {
            return null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroy();
    }
}