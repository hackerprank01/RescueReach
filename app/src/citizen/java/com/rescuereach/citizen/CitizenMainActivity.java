package com.rescuereach.citizen;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuereach.R;
import com.rescuereach.citizen.fragments.PlaceholderFragment;
import com.rescuereach.citizen.fragments.ProfileFragment;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.util.LocationManager;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CitizenMainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, OnMapReadyCallback {

    private static final String TAG = "CitizenMainActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String MAPVIEW_BUNDLE_KEY = "MapViewBundleKey";

    private boolean emergencyServicesLoadedToastShown = false;

    private DrawerLayout drawer;
    private UserSessionManager sessionManager;
    private int currentFragmentId = R.id.nav_home;

    // Map and location related
    private MapView mapView;
    private GoogleMap googleMap;
    private LocationManager locationManager;
    private Location currentLocation;
    private ImageView centerLocationButton;

    private PlacesClient placesClient;
    private final int SEARCH_RADIUS = 3000; // 3 km radius
    private final int MAX_RESULTS = 5; // Max 5 results per type

    private final Map<String, List<String>> placeTypeKeywords = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Counter to track Place API requests
    private AtomicInteger placeRequestsCounter = new AtomicInteger(0);
    private AtomicInteger placeRequestsCompleted = new AtomicInteger(0);

    private boolean isSearchingPlaces = false;
    private Toast currentToast = null;
    private boolean placesLoadingToastShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_citizen_main);

        // Initialize services
        sessionManager = UserSessionManager.getInstance(this);
        locationManager = new LocationManager(this);

        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        }
        placesClient = Places.createClient(this);
        setupPlaceTypeKeywords();
        
        setupToolbar();
        setupNavigationDrawer();
        setupSosButton();
        updateNavigationHeader();
        initMapView(savedInstanceState);

        // Initialize with Home fragment if this is a fresh start
        if (savedInstanceState == null) {
            navigateToFragment(R.id.nav_home);
        }

        // Request location permissions if not granted
        requestLocationPermissions();
    }



    private void setupPlaceTypeKeywords() {
        // Setup keywords for each emergency service type
        placeTypeKeywords.put("hospital", Arrays.asList(
                "hospital", "emergency room", "medical center", "trauma center"));

        placeTypeKeywords.put("police", Arrays.asList(
                "police station", "police department", "law enforcement"));

        placeTypeKeywords.put("fire_station", Arrays.asList(
                "fire station", "fire department", "fire and rescue"));
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void setupNavigationDrawer() {
        drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, findViewById(R.id.toolbar),
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);

        // Check volunteer status and update menu visibility
        updateNavigationMenuBasedOnVolunteerStatus(navigationView);
    }

    /**
     * Updates navigation menu items based on the user's volunteer status
     * This method handles both volunteer-specific menu items and their section headers
     */
    private void updateNavigationMenuBasedOnVolunteerStatus(NavigationView navigationView) {
        if (navigationView == null) return;

        try {
            // Get current volunteer status from session manager
            boolean isVolunteer = sessionManager.isVolunteer();
            Log.d(TAG, "Updating navigation menu - volunteer status: " + isVolunteer);

            Menu navMenu = navigationView.getMenu();

            // Update volunteer-specific menu items
            MenuItem volunteerAlertsItem = navMenu.findItem(R.id.nav_volunteer_alerts);
            if (volunteerAlertsItem != null) {
                volunteerAlertsItem.setVisible(isVolunteer);
            }

            // Handle other volunteer-specific items if any
            // Add additional items here as needed

            // Handle section headers - find the volunteer section header if it exists
            MenuItem volunteerSectionHeader = navMenu.findItem(R.id.nav_volunteer_alerts);
            if (volunteerSectionHeader != null) {
                // Hide section header if no volunteer items are visible
                boolean hasVisibleItems = hasVisibleItems(navMenu, R.id.nav_volunteer_alerts);
                volunteerSectionHeader.setVisible(hasVisibleItems);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating navigation based on volunteer status", e);
        }
    }

    private boolean hasVisibleItems(Menu menu, int sectionId) {
        // Get the position of the section header
        int position = -1;
        for (int i = 0; i < menu.size(); i++) {
            if (menu.getItem(i).getItemId() == sectionId) {
                position = i;
                break;
            }
        }

        if (position == -1) return false;

        // Check all items after the section header until we hit another section header
        for (int i = position + 1; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            // If we hit another section (items with no action), stop checking
            if (item.hasSubMenu() || !item.isCheckable()) {
                break;
            }
            // If any item is visible, return true
            if (item.isVisible()) {
                return true;
            }
        }

        return false;
    }

    private void setupSosButton() {
        FloatingActionButton fab = findViewById(R.id.fab_sos);
        fab.setOnClickListener(view -> {
            Toast.makeText(this, "SOS Button Pressed - Emergency Reporting will be implemented soon",
                    Toast.LENGTH_SHORT).show();
            // SOS functionality will be implemented in a future step
        });
    }

    private void updateNavigationHeader() {
        NavigationView navigationView = findViewById(R.id.nav_view);
        View headerView = navigationView.getHeaderView(0);

    }

    private void initMapView(Bundle savedInstanceState) {
        NavigationView navigationView = findViewById(R.id.nav_view);
        View headerView = navigationView.getHeaderView(0);

        mapView = headerView.findViewById(R.id.nav_header_map_view);
        centerLocationButton = headerView.findViewById(R.id.btn_center_location);
        ImageView zoomInButton = headerView.findViewById(R.id.btn_zoom_in);
        ImageView zoomOutButton = headerView.findViewById(R.id.btn_zoom_out);

        TextView locationAddressText = headerView.findViewById(R.id.text_location_address);

        // Create bundle for MapView
        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }

        // Initialize MapView
        mapView.onCreate(mapViewBundle);
        mapView.getMapAsync(this);

        // Set up location centering button
        centerLocationButton.setOnClickListener(v -> centerMapOnCurrentLocation());

        // Set up zoom controls
        zoomInButton.setOnClickListener(v -> {
            if (googleMap != null) {
                googleMap.animateCamera(CameraUpdateFactory.zoomIn());
            }
        });

        zoomOutButton.setOnClickListener(v -> {
            if (googleMap != null) {
                googleMap.animateCamera(CameraUpdateFactory.zoomOut());
            }
        });

        // Set up geocoding for address display
        locationManager.setLocationUpdateListener(new LocationManager.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(Location location) {
                currentLocation = location;
                updateMapWithLocation();

                // Update address text using Geocoder
                updateAddressFromLocation(location, locationAddressText);
            }

            @Override
            public void onLocationError(String message) {
                Toast.makeText(CitizenMainActivity.this,
                        "Location error: " + message, Toast.LENGTH_SHORT).show();
                locationAddressText.setText(R.string.awaiting_location);
            }
        });
    }

    private void updateAddressFromLocation(Location location, TextView addressTextView) {
        // Use Geocoder to get address from location coordinates
        android.location.Geocoder geocoder = new android.location.Geocoder(this,
                java.util.Locale.getDefault());

        try {
            List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(), 1);

            if (addresses != null && !addresses.isEmpty()) {
                android.location.Address address = addresses.get(0);

                // Format the address for display
                StringBuilder sb = new StringBuilder();

                // Add thoroughfare (street name) if available
                if (address.getThoroughfare() != null) {
                    sb.append(address.getThoroughfare());
                    if (address.getSubThoroughfare() != null) {
                        sb.append(", ").append(address.getSubThoroughfare());
                    }
                    sb.append(", ");
                }

                // Add locality (city)
                if (address.getLocality() != null) {
                    sb.append(address.getLocality());
                }

                // Set the formatted address to the TextView
                String addressText = sb.toString();
                if (!addressText.isEmpty()) {
                    addressTextView.setText(addressText);
                } else {
                    addressTextView.setText(R.string.awaiting_location);
                }
            } else {
                addressTextView.setText(R.string.awaiting_location);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting address from location", e);
            addressTextView.setText(R.string.awaiting_location);
        }
    }


    
    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        } else {
            // Permissions already granted, start location updates
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        locationManager.setLocationUpdateListener(new LocationManager.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(Location location) {
                currentLocation = location;
                updateMapWithLocation();
            }

            @Override
            public void onLocationError(String message) {
                Toast.makeText(CitizenMainActivity.this,
                        "Location error: " + message, Toast.LENGTH_SHORT).show();
            }
        });

        locationManager.startLocationUpdates();
    }

    private void updateMapWithLocation() {
        if (googleMap != null && currentLocation != null) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

            // Clear previous markers
            googleMap.clear();

            // Add marker at current location
            googleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.current_location))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

            // Only start a new search if not already searching
            if (!isSearchingPlaces) {
                searchNearbyEmergencyServices(latLng);
            }

            // Animate camera to show current location
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f));
        }
    }

    private void showToast(String message, int duration) {
        runOnUiThread(() -> {
            // Cancel any existing toast
            if (currentToast != null) {
                currentToast.cancel();
            }

            // Create and show new toast
            currentToast = Toast.makeText(CitizenMainActivity.this, message, duration);
            currentToast.show();
        });
    }


    private void searchNearbyEmergencyServices(LatLng location) {
        // Mark as searching
        isSearchingPlaces = true;

        // Show loading message only once per app session
        if (!placesLoadingToastShown) {
            showToast(getString(R.string.loading_places), Toast.LENGTH_SHORT);
            placesLoadingToastShown = true;
        }

        // Reset the completion counter
        placeRequestsCompleted.set(0);

        // Counter to track completion of all searches
        AtomicInteger searchCounter = new AtomicInteger(0);
        final int TOTAL_SEARCHES = 3; // hospital, police, fire_station

        // Search for each emergency service type
        searchNearbyPlaces(location, "hospital", BitmapDescriptorFactory.HUE_RED, searchCounter, TOTAL_SEARCHES);
        searchNearbyPlaces(location, "police", BitmapDescriptorFactory.HUE_BLUE, searchCounter, TOTAL_SEARCHES);
        searchNearbyPlaces(location, "fire_station", BitmapDescriptorFactory.HUE_ORANGE, searchCounter, TOTAL_SEARCHES);
    }

    private void searchNearbyPlaces(LatLng location, String placeType, float markerColor,
                                    AtomicInteger counter, int totalSearches) {
        // Get the keywords for this place type
        List<String> keywords = placeTypeKeywords.get(placeType);
        if (keywords == null || keywords.isEmpty()) {
            // If no keywords, mark this search as complete
            checkSearchCompletion(counter, totalSearches);
            return;
        }

        // Calculate bounds for the search (approximately within SEARCH_RADIUS)
        double latRadiusDegrees = SEARCH_RADIUS / 111000.0; // approximate degrees per meter
        double lngRadiusDegrees = latRadiusDegrees / Math.cos(Math.toRadians(location.latitude));

        RectangularBounds bounds = RectangularBounds.newInstance(
                new LatLng(location.latitude - latRadiusDegrees, location.longitude - lngRadiusDegrees),
                new LatLng(location.latitude + latRadiusDegrees, location.longitude + lngRadiusDegrees));

        // Counter for found places
        AtomicInteger foundPlacesCounter = new AtomicInteger(0);
        AtomicInteger keywordRequestsCounter = new AtomicInteger(0);
        final int totalKeywords = keywords.size();

        // Search for places using each keyword
        for (String keyword : keywords) {
            if (foundPlacesCounter.get() >= MAX_RESULTS) {
                // Already found enough places
                if (keywordRequestsCounter.incrementAndGet() >= totalKeywords) {
                    checkSearchCompletion(counter, totalSearches);
                }
                continue;
            }

            // Create the autocomplete request
            FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                    .setLocationBias(bounds)
                    .setOrigin(location)
                    .setQuery(keyword)
                    .build();

            // Execute the request
            placesClient.findAutocompletePredictions(request)
                    .addOnSuccessListener(response -> {
                        // Process predictions (places found)
                        for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                            // Skip if we have enough places
                            if (foundPlacesCounter.get() >= MAX_RESULTS) {
                                continue;
                            }

                            // Create a fetch place request to get place details
                            List<Place.Field> placeFields = Arrays.asList(
                                    Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG);

                            FetchPlaceRequest fetchRequest = FetchPlaceRequest.builder(
                                    prediction.getPlaceId(), placeFields).build();

                            // Fetch the place details
                            placesClient.fetchPlace(fetchRequest)
                                    .addOnSuccessListener(fetchResponse -> {
                                        Place place = fetchResponse.getPlace();

                                        // Skip if no valid location
                                        if (place.getLatLng() == null) return;

                                        // Calculate distance from user
                                        float[] results = new float[1];
                                        Location.distanceBetween(
                                                location.latitude, location.longitude,
                                                place.getLatLng().latitude, place.getLatLng().longitude,
                                                results);

                                        float distanceKm = results[0] / 1000; // Convert meters to km

                                        // Add marker to map
                                        runOnUiThread(() -> {
                                            googleMap.addMarker(new MarkerOptions()
                                                    .position(place.getLatLng())
                                                    .title(place.getName())
                                                    .snippet(String.format(Locale.getDefault(),
                                                            getString(R.string.place_distance), distanceKm))
                                                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));

                                            // Increment counter only for unique places
                                            foundPlacesCounter.incrementAndGet();
                                        });
                                    })
                                    .addOnCompleteListener(task -> {
                                        // Whether successful or not, count this request as completed
                                        if (keywordRequestsCounter.incrementAndGet() >= totalKeywords) {
                                            checkSearchCompletion(counter, totalSearches);
                                        }
                                    });
                        }

                        // If no predictions found, we still need to track completion
                        if (response.getAutocompletePredictions().isEmpty() &&
                                keywordRequestsCounter.incrementAndGet() >= totalKeywords) {
                            checkSearchCompletion(counter, totalSearches);
                        }
                    })
                    .addOnFailureListener(exception -> {
                        Log.e(TAG, "Place search failed: " + exception.getMessage());

                        // Count this as completed even on failure
                        if (keywordRequestsCounter.incrementAndGet() >= totalKeywords) {
                            checkSearchCompletion(counter, totalSearches);
                        }
                    });
        }
    }
    private void checkSearchCompletion(AtomicInteger counter, int totalSearches) {
        if (counter.incrementAndGet() >= totalSearches) {
            // All searches completed
            isSearchingPlaces = false;
            // Show "places loaded" toast only once
            if (!emergencyServicesLoadedToastShown) {
                showToast(getString(R.string.places_loaded), Toast.LENGTH_SHORT);
                emergencyServicesLoadedToastShown = true;
            }
        }
    }

    private void checkRequestCompletion() {
        int completed = placeRequestsCompleted.incrementAndGet();
        int total = placeRequestsCounter.get();

        if (completed >= total) {
            // All searches are complete, reset the toast flag
            mainHandler.post(() -> {
                placesLoadingToastShown = false;
                Toast.makeText(CitizenMainActivity.this,
                        getString(R.string.places_loaded), Toast.LENGTH_SHORT).show();
            });
        }
    }


    private void centerMapOnCurrentLocation() {
        if (googleMap != null && currentLocation != null) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
        } else {
            Toast.makeText(this, R.string.waiting_for_location, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;

        // Configure the Google Map with explicit interaction settings
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        // Explicitly enable all gestures for user interaction
        googleMap.getUiSettings().setAllGesturesEnabled(true);
        googleMap.getUiSettings().setZoomGesturesEnabled(true);
        googleMap.getUiSettings().setScrollGesturesEnabled(true);
        googleMap.getUiSettings().setRotateGesturesEnabled(true);
        googleMap.getUiSettings().setTiltGesturesEnabled(true);

        // Disable unnecessary controls
        googleMap.getUiSettings().setCompassEnabled(false);
        googleMap.getUiSettings().setMapToolbarEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setZoomControlsEnabled(false);

        // Apply map style based on night mode
        boolean isNightMode = getResources().getBoolean(R.bool.is_night_mode);
        if (isNightMode) {
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_night));
        } else {
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_day));
        }

        // Try to enable my location layer if permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        }

        // If we already have a location, update the map
        if (currentLocation != null) {
            updateMapWithLocation();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                startLocationUpdates();

                // Enable my location layer on map if it's ready
                if (googleMap != null) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        googleMap.setMyLocationEnabled(true);
                    }
                }
            } else {
                // Permission denied
                Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    // MapView lifecycle methods
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle);
        }

        if (mapView != null) {
            mapView.onSaveInstanceState(mapViewBundle);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        if (locationManager != null) {
            startLocationUpdates();
        }
        // Refresh navigation drawer in case volunteer status changed
        NavigationView navigationView = findViewById(R.id.nav_view);
        updateNavigationMenuBasedOnVolunteerStatus(navigationView);

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) {
            mapView.onStart();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mapView != null) {
            mapView.onStop();
        }
    }

    @Override
    protected void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        if (locationManager != null) {
            locationManager.stopLocationUpdates();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mapView != null) {
            mapView.onDestroy();
        }
        if (locationManager != null) {
            locationManager.stopLocationUpdates();
        }
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_logout) {
            // Check if we should skip confirmation
            boolean skipConfirmation = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .getBoolean("skip_logout_confirmation", false);

            if (skipConfirmation) {
                performLogout();
            } else {
                showLogoutConfirmationDialog();
            }
        } else {
            navigateToFragment(id);
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void navigateToFragment(int fragmentId) {
        Fragment fragment;
        String title;

        if (fragmentId == R.id.nav_home) {
            title = getString(R.string.menu_home);
            fragment = PlaceholderFragment.newInstance(
                    title,
                    getString(R.string.placeholder_home_description),
                    R.drawable.ic_home);
        } else if (fragmentId == R.id.nav_my_reports) {
            // Check if user is volunteer before allowing navigation
            if (!sessionManager.isVolunteer()) {
                // If not a volunteer, redirect to home
                Toast.makeText(this, R.string.volunteer_access_denied,
                        Toast.LENGTH_SHORT).show();
                navigateToFragment(R.id.nav_home);
                return;
            }

            title = getString(R.string.menu_volunteer_alerts);
            fragment = PlaceholderFragment.newInstance(
                    title,
                    getString(R.string.placeholder_volunteer_description),
                    R.drawable.ic_alerts);
        } else if (fragmentId == R.id.nav_volunteer_alerts) {
            title = getString(R.string.menu_volunteer_alerts);
            fragment = PlaceholderFragment.newInstance(
                    title,
                    getString(R.string.placeholder_volunteer_description),
                    R.drawable.ic_alerts);
        } else if (fragmentId == R.id.nav_safety_features) {
            title = getString(R.string.menu_safety_features);
            fragment = PlaceholderFragment.newInstance(
                    title,
                    getString(R.string.placeholder_safety_description),
                    R.drawable.ic_safety);
        } else if (fragmentId == R.id.nav_help_support) {
            title = getString(R.string.menu_help_support);
            fragment = PlaceholderFragment.newInstance(
                    title,
                    getString(R.string.placeholder_help_description),
                    R.drawable.ic_help);
        } else if (fragmentId == R.id.nav_profile) {
            title = getString(R.string.menu_profile);
            fragment = new ProfileFragment();
        } else {
            title = getString(R.string.menu_home);
            fragment = PlaceholderFragment.newInstance(
                    title,
                    getString(R.string.placeholder_home_description),
                    R.drawable.ic_home);
            fragmentId = R.id.nav_home;
        }

        currentFragmentId = fragmentId;
        setTitle(title);

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.replace(R.id.nav_host_fragment, fragment);
        ft.commit();

        // Update selected item in navigation drawer
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setCheckedItem(fragmentId);
    }

    private void showLogoutConfirmationDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_logout_confirmation, null);
        CheckBox doNotShowAgain = dialogView.findViewById(R.id.checkbox_do_not_show_again);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.logout_dialog_title)
                .setView(dialogView)
                .setPositiveButton(R.string.logout, (dialogInterface, i) -> {
                    // Save preference if "Do not show again" is checked
                    if (doNotShowAgain.isChecked()) {
                        getSharedPreferences("app_prefs", MODE_PRIVATE)
                                .edit()
                                .putBoolean("skip_logout_confirmation", true)
                                .apply();
                    }

                    // Perform logout
                    performLogout();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            // Style the positive button as an accent/warning color
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setTextColor(getResources().getColor(R.color.emergency_red, null));
        });

        dialog.show();
    }


    private void performLogout() {
        // Show loading dialog
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.logging_out));
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Create a task to perform logout operations
        new Thread(() -> {
            try {
                // Sign out from Firebase Auth
                FirebaseAuth.getInstance().signOut();

                // Enhanced session clearing
                UserSessionManager userSession = UserSessionManager.getInstance(this);

                // Clear all session preferences before clearing session
                userSession.clearAllPreferences(); // Add this method to UserSessionManager

                // Clear user session data
                userSession.clearSession();

                // Clear application cache and WebView data if any
                clearApplicationData();

                // Wait briefly to ensure operations complete
                Thread.sleep(500);

                // Update UI on main thread
                runOnUiThread(() -> {
                    // Dismiss progress dialog
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }

                    // Show success message
                    Toast.makeText(this, R.string.logout_success, Toast.LENGTH_SHORT).show();

                    // Navigate to authentication screen
                    navigateToAuthScreen();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error during logout: " + e.getMessage());

                // Update UI on main thread
                runOnUiThread(() -> {
                    // Dismiss progress dialog
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }

                    // Show error message
                    Toast.makeText(this,
                            getString(R.string.logout_error, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // Add this method to clear application cache
    private void clearApplicationData() {
        try {
            // Clear app cache
            File cache = getCacheDir();
            File appDir = new File(cache.getParent());
            if (appDir.exists()) {
                String[] children = appDir.list();
                if (children != null) {
                    for (String s : children) {
                        if (!s.equals("lib")) {
                            deleteDir(new File(appDir, s));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing application data", e);
        }
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    private void navigateToAuthScreen() {
        // Create intent for PhoneAuthActivity
        Intent intent = new Intent(this, PhoneAuthActivity.class);

        // Clear the activity stack so the user can't go back
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // Start the activity
        startActivity(intent);

        // Apply a transition animation
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);

        // Finish the current activity
        finish();
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (currentFragmentId != R.id.nav_home) {
            // If not on home screen, navigate back to home first
            navigateToFragment(R.id.nav_home);
        } else {
            // If on home screen, show exit confirmation
            new AlertDialog.Builder(this)
                    .setTitle(R.string.exit_dialog_title)
                    .setMessage(R.string.exit_dialog_message)
                    .setPositiveButton(R.string.yes, (dialog, which) -> super.onBackPressed())
                    .setNegativeButton(R.string.no, null)
                    .show();
        }
    }

    public void refreshNavigationDrawer() {
        try {
            // Get reference to navigation view
            NavigationView navigationView = findViewById(R.id.nav_view);

            // Get current volunteer status directly from session
            UserSessionManager sessionManager = UserSessionManager.getInstance(this);
            boolean isVolunteer = sessionManager.isVolunteer();

            Log.d(TAG, "Refreshing drawer with volunteer status: " + isVolunteer);

            // Completely recreate the menu to fix visibility issues
            Menu navMenu = navigationView.getMenu();
            navMenu.clear();
            navigationView.inflateMenu(R.menu.drawer_menu);

            // Now set the visibility of volunteer items
            MenuItem volunteerAlertsItem = navMenu.findItem(R.id.nav_volunteer_alerts);
            if (volunteerAlertsItem != null) {
                volunteerAlertsItem.setVisible(isVolunteer);
                Log.d(TAG, "Set volunteer alerts item visibility to " + isVolunteer);
            }

            // If in volunteer section and no longer a volunteer, switch to home
            if (!isVolunteer && currentFragmentId == R.id.nav_volunteer_alerts) {
                navigateToFragment(R.id.nav_home);
            }

            // Update header info
            updateNavigationHeader();

            // Mark the current item as selected
            navigationView.setCheckedItem(currentFragmentId);
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing navigation drawer", e);
        }
    }
}
