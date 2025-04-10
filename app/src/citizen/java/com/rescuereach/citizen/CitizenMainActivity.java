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
import com.rescuereach.R;
import com.rescuereach.citizen.fragments.HomeFragment;
import com.rescuereach.citizen.fragments.PlaceholderFragment;
import com.rescuereach.citizen.fragments.ProfileFragment;
import com.rescuereach.service.auth.AuthService;
import com.rescuereach.service.auth.AuthServiceProvider;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.util.LocationManager;

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

    // UI components
    private DrawerLayout drawer;
    private MapView mapView;
    private GoogleMap googleMap;
    private ImageView centerLocationButton;
    private TextView locationAddressText;

    // Service components
    private UserSessionManager sessionManager;
    private LocationManager locationManager;
    private PlacesClient placesClient;

    // State management
    private int currentFragmentId = R.id.nav_home;
    private Location currentLocation;
    private Toast currentToast = null;
    private boolean isSearchingPlaces = false;
    private boolean emergencyServicesLoadedToastShown = false;
    private boolean placesLoadingToastShown = false;

    private Handler drawerUpdateHandler;
    private static final long DRAWER_UPDATE_DELAY = 500; // Half second delay

    // Place search configuration
    private static final int SEARCH_RADIUS = 3000; // 3 km radius
    private static final int MAX_RESULTS = 5; // Max 5 results per type
    private final Map<String, List<String>> placeTypeKeywords = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_citizen_main);

        initializeServices();
        setupUI();

        sessionManager = UserSessionManager.getInstance(this);
        drawerUpdateHandler = new Handler(Looper.getMainLooper());

        // Initialize with Home fragment if this is a fresh start
        if (savedInstanceState == null) {
            navigateToFragment(R.id.nav_home);
        }

        // Initial drawer update with direct Firebase check
        updateDrawerWithFirebaseCheck();
        
        // Request location permissions if not granted
        requestLocationPermissions();
    }

    private void updateDrawerWithFirebaseCheck() {
        // Direct Firebase check - this is crucial
        try {
            sessionManager.checkVolunteerStatus(new UserSessionManager.VolunteerStatusCallback() {
                @Override
                public void onResult(boolean isVolunteer) {
                    Log.d(TAG, "Volunteer status from Firebase: " + isVolunteer);

                    // First update session manager for consistency
                    sessionManager.setVolunteer(isVolunteer);

                    // Update on main thread with slight delay to ensure drawer is ready
                    drawerUpdateHandler.postDelayed(() -> {
                        try {
                            NavigationView navigationView = findViewById(R.id.nav_view);
                            Menu menu = navigationView.getMenu();

                            MenuItem volunteerSection = menu.findItem(R.id.nav_volunteer_alerts);
                            if (volunteerSection != null) {
                                volunteerSection.setVisible(isVolunteer);
                                Log.d(TAG, "Updated drawer visibility: " + isVolunteer);
                            }

                            // Also update the volunteer section header if exists
                            if (menu.size() >= 2) {
                                MenuItem volunteerSectionHeader = menu.getItem(1);
                                if (volunteerSectionHeader != null) {
                                    volunteerSectionHeader.setVisible(isVolunteer);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating drawer", e);
                        }
                    }, DRAWER_UPDATE_DELAY);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error checking volunteer status", e);
        }
    }

    private void initializeServices() {
        // Initialize user session and location services
        sessionManager = UserSessionManager.getInstance(this);
        locationManager = new LocationManager(this);

        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        }
        placesClient = Places.createClient(this);

        // Setup place type keywords for emergency services search
        setupPlaceTypeKeywords();
    }

    private void setupUI() {
        setupToolbar();
        setupNavigationDrawer();
        setupSosButton();
        updateNavigationHeader();
        initMapView(null); // Will be properly initialized in onSaveInstanceState
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

            // Find the volunteer section header (it's the 2nd item in the root menu)
            MenuItem volunteerSectionHeader = null;
            if (navMenu.size() >= 2) {
                volunteerSectionHeader = navMenu.getItem(1); // Index 1 is the volunteer section header
            }

            // Update volunteer alerts item visibility
            MenuItem volunteerAlertsItem = navMenu.findItem(R.id.nav_volunteer_alerts);
            if (volunteerAlertsItem != null) {
                volunteerAlertsItem.setVisible(isVolunteer);
                Log.d(TAG, "Set volunteer alerts item visibility to " + isVolunteer);
            }

            // Also update the section header visibility
            if (volunteerSectionHeader != null) {
                volunteerSectionHeader.setVisible(isVolunteer);
                Log.d(TAG, "Set volunteer section header visibility to " + isVolunteer);
            }

            // If in volunteer section and no longer a volunteer, switch to home
            if (!isVolunteer && currentFragmentId == R.id.nav_volunteer_alerts) {
                navigateToFragment(R.id.nav_home);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating navigation based on volunteer status", e);
        }
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
        // Additional header customization can be added here
    }

    private void initMapView(Bundle savedInstanceState) {
        NavigationView navigationView = findViewById(R.id.nav_view);
        View headerView = navigationView.getHeaderView(0);

        mapView = headerView.findViewById(R.id.nav_header_map_view);
        centerLocationButton = headerView.findViewById(R.id.btn_center_location);
        ImageView zoomInButton = headerView.findViewById(R.id.btn_zoom_in);
        ImageView zoomOutButton = headerView.findViewById(R.id.btn_zoom_out);
        locationAddressText = headerView.findViewById(R.id.text_location_address);

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

        // Set up location listener for map updates
        setupLocationListener();
    }

    private void setupLocationListener() {
        locationManager.setLocationUpdateListener(new LocationManager.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(Location location) {
                currentLocation = location;
                updateMapWithLocation();
                updateAddressFromLocation(location);
            }

            @Override
            public void onLocationError(String message) {
                showToast("Location error: " + message, Toast.LENGTH_SHORT);
                if (locationAddressText != null) {
                    locationAddressText.setText(R.string.awaiting_location);
                }
            }
        });
    }

    private void updateAddressFromLocation(Location location) {
        if (locationAddressText == null) return;

        // Use Geocoder to get address from location coordinates
        android.location.Geocoder geocoder = new android.location.Geocoder(this, Locale.getDefault());

        try {
            List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(), 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);

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
                    locationAddressText.setText(addressText);
                } else {
                    locationAddressText.setText(R.string.awaiting_location);
                }
            } else {
                locationAddressText.setText(R.string.awaiting_location);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting address from location", e);
            locationAddressText.setText(R.string.awaiting_location);
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
            currentToast = Toast.makeText(this, message, duration);
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
                                            if (googleMap != null && !isFinishing()) {
                                                googleMap.addMarker(new MarkerOptions()
                                                        .position(place.getLatLng())
                                                        .title(place.getName())
                                                        .snippet(String.format(Locale.getDefault(),
                                                                getString(R.string.place_distance), distanceKm))
                                                        .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));

                                                // Increment counter only for unique places
                                                foundPlacesCounter.incrementAndGet();
                                            }
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

    private void centerMapOnCurrentLocation() {
        if (googleMap != null && currentLocation != null) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
        } else {
            showToast(getString(R.string.waiting_for_location), Toast.LENGTH_SHORT);
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
        enableMyLocationIfPermitted();

        // If we already have a location, update the map
        if (currentLocation != null) {
            updateMapWithLocation();
        }
    }

    private void enableMyLocationIfPermitted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (googleMap != null) {
                googleMap.setMyLocationEnabled(true);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                startLocationUpdates();
                enableMyLocationIfPermitted();
            } else {
                // Permission denied
                showToast(getString(R.string.location_permission_denied), Toast.LENGTH_LONG);
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
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_logout) {
            handleLogoutRequest();
        } else {
            navigateToFragment(id);
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void handleLogoutRequest() {
        // Check if we should skip confirmation
        boolean skipConfirmation = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("skip_logout_confirmation", false);

        if (skipConfirmation) {
            performLogout();
        } else {
            showLogoutConfirmationDialog();
        }
    }

    private void navigateToFragment(int fragmentId) {
        Fragment fragment;
        String title;

        if (fragmentId == R.id.nav_home) {
            title = getString(R.string.menu_home);
            fragment = new HomeFragment();
        } else if (fragmentId == R.id.nav_my_reports) {
            title = getString(R.string.menu_my_reports);
            fragment = PlaceholderFragment.newInstance(
                    title,
                    getString(R.string.placeholder_my_reports_description),
                    R.drawable.ic_reports);
        } else if (fragmentId == R.id.nav_volunteer_alerts) {
            // Check if user is volunteer before allowing navigation
            if (!sessionManager.isVolunteer()) {
                // If not a volunteer, redirect to home
                showToast(getString(R.string.volunteer_access_denied), Toast.LENGTH_SHORT);
                navigateToFragment(R.id.nav_home);
                return;
            }
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
            fragment = new HomeFragment();
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

        // Get auth service for logout
        AuthService authService = AuthServiceProvider.getInstance().getAuthService();

        // Prepare intent for next activity BEFORE signout
        final Intent authIntent = new Intent(this, PhoneAuthActivity.class);
        authIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // Sign out from Firebase Auth
        authService.signOut(new AuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                try {
                    // Clear session data
                    sessionManager.clearSession();

                    // Dismiss progress dialog
                    safelyDismissDialog(progressDialog);

                    // Show success message
                    showToast(getString(R.string.logout_success), Toast.LENGTH_SHORT);

                    // Wait a moment before navigating to ensure UI is ready
                    new Handler().postDelayed(() -> {
                        // Start PhoneAuthActivity
                        startActivity(authIntent);

                        // Apply transition animation
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);

                        // Finish the current activity
                        finish();
                    }, 200);
                } catch (Exception e) {
                    Log.e(TAG, "Error during logout cleanup", e);
                    handleLogoutError(progressDialog, e);
                }
            }

            @Override
            public void onError(Exception e) {
                handleLogoutError(progressDialog, e);
            }
        });
    }

    private void safelyDismissDialog(ProgressDialog dialog) {
        if (dialog != null && dialog.isShowing() && !isFinishing()) {
            try {
                dialog.dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing dialog", e);
            }
        }
    }

    private void handleLogoutError(ProgressDialog progressDialog, Exception e) {
        Log.e(TAG, "Error during logout: " + e.getMessage(), e);

        // Dismiss progress dialog
        safelyDismissDialog(progressDialog);

        // Show error message
        showToast(getString(R.string.logout_error, e.getMessage()), Toast.LENGTH_LONG);
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

        // Get the NavigationView
        NavigationView navigationView = findViewById(R.id.nav_view);
        Menu navMenu = navigationView.getMenu();

        // Get the volunteer status directly from UserSessionManager
        boolean isVolunteer = UserSessionManager.getInstance(this).isVolunteer();
        Log.d(TAG, "Refreshing navigation drawer. Current volunteer status: " + isVolunteer);

        // Find volunteer-related items
        MenuItem volunteerSection = navMenu.findItem(R.id.nav_volunteer_alerts);
        if (volunteerSection != null) {
            volunteerSection.setVisible(isVolunteer);
            Log.d(TAG, "Volunteer section visibility set to: " + isVolunteer);
        }

    }

    // Lifecycle methods managed properly to handle MapView and location updates

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
        refreshNavigationDrawer();
        updateDrawerWithFirebaseCheck();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) {
            mapView.onStart();
        }

        // Force a direct check with Firebase for volunteer status
        // This is crucial to ensure the correct status after login
        sessionManager.checkVolunteerStatus(isVolunteer -> {
            Log.d(TAG, "onStart volunteer status check: " + isVolunteer);
            refreshNavigationDrawer();
        });
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
}