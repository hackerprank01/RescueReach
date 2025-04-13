package com.rescuereach.citizen.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rescuereach.R;
import com.rescuereach.util.LocationManager;
import com.rescuereach.util.NetworkManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SafetyFeaturesFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "SafetyFeaturesFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String MAPVIEW_BUNDLE_KEY = "MapViewBundleKey";

    // Distance threshold for refreshing services (meters)
    private static final float LOCATION_REFRESH_THRESHOLD = 500.0f; // 500 meters

    // Cache keys for SharedPreferences
    private static final String PREFS_NAME = "SafetyFeatureCache";
    private static final String KEY_LAST_LOCATION = "LastLocation";
    private static final String KEY_CACHED_SERVICES = "CachedServices";
    private static final String KEY_CACHE_TIMESTAMP = "CacheTimestamp";
    private static final long CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000; // 24 hours

    // UI Components
    private MapView mapView;
    private GoogleMap googleMap;
    private ImageView centerLocationButton;
    private ImageView zoomInButton;
    private ImageView zoomOutButton;
    private RadioGroup serviceCategoryGroup;
    private Spinner proximitySpinner;
    private MaterialButton searchButton;
    private ProgressBar progressBar;
    private TextView offlineModeIndicator;
    private View offlineBanner;

    // Service components
    private LocationManager locationManager;
    private PlacesClient placesClient;
    private NetworkManager networkManager;
    private SharedPreferences sharedPreferences;
    private Gson gson;

    // State management
    private Location currentLocation;
    private Location lastSearchLocation; // Track location of last search
    private Marker userLocationMarker; // Reference to user's location marker
    private Toast currentToast = null;
    private boolean isSearchingPlaces = false;
    private int selectedProximityMeters = 3000; // Default 3km
    private String selectedServiceType = "all"; // Default all services
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean autoRefreshEnabled = true; // Enable auto-refresh by default
    private boolean hasPerformedSearch = false; // Track if a search has been performed
    private boolean isOfflineMode = false; // Track if we're in offline mode
    private boolean hasShownOfflineNotice = false; // Track if we've already shown the offline notice

    // Place search configuration
    private final Map<String, List<String>> placeTypeKeywords = new HashMap<>();

    // Store place data for markers
    private final Map<String, EmergencyServicePlace> markerPlaceData = new HashMap<>();

    // Field to track the total number of places found
    // Field to track the total number of places found
    private int totalPlacesFound = 0;

    // Class to store emergency service place data
    private static class EmergencyServicePlace {
        String name;
        String address;
        String phoneNumber;
        String placeType; // "hospital", "police", "fire_station"
        double distanceKm;
        LatLng location;

        EmergencyServicePlace(String name, String address, String phoneNumber, String placeType, double distanceKm, LatLng location) {
            this.name = name;
            this.address = address;
            this.phoneNumber = phoneNumber;
            this.placeType = placeType;
            this.distanceKm = distanceKm;
            this.location = location;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize location manager
        locationManager = new LocationManager(requireContext());

        // Initialize network manager
        networkManager = new NetworkManager(requireContext());

        // Initialize shared preferences for caching
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();

        // Initialize Places API if needed
        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), getString(R.string.google_maps_key));
        }
        placesClient = Places.createClient(requireContext());

        // Setup place type keywords for emergency services search
        setupPlaceTypeKeywords();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_safety_features, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize UI components
        mapView = view.findViewById(R.id.safety_map_view);
        centerLocationButton = view.findViewById(R.id.btn_center_location);
        zoomInButton = view.findViewById(R.id.btn_zoom_in);
        zoomOutButton = view.findViewById(R.id.btn_zoom_out);
        serviceCategoryGroup = view.findViewById(R.id.service_category_group);
        proximitySpinner = view.findViewById(R.id.proximity_spinner);
        searchButton = view.findViewById(R.id.btn_search);
        progressBar = view.findViewById(R.id.progress_bar);
        offlineModeIndicator = view.findViewById(R.id.offline_mode_indicator);
        offlineBanner = view.findViewById(R.id.offline_banner);

        // Create bundle for MapView
        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }

        // Initialize MapView
        mapView.onCreate(mapViewBundle);
        mapView.getMapAsync(this);

        // Set up proximity spinner
        setupProximitySpinner();

        // Setup click listeners
        setupClickListeners();

        // Setup network monitoring
        setupNetworkMonitoring();

        // Setup location listener
        setupLocationListener();

        // Request location permissions if not granted
        requestLocationPermissions();

        // Try to load cached location data if available
        loadCachedLocationData();
    }

    /**
     * Setup network monitoring to detect connectivity changes
     */
    private void setupNetworkMonitoring() {
        networkManager.startNetworkMonitoring(isConnected -> {
            // Update UI based on network state
            isOfflineMode = !isConnected;
            updateOfflineModeUI();

            if (isConnected) {
                // Reconnected - try to refresh data
                if (hasPerformedSearch) {
                    showToast(getString(R.string.back_online_refreshing), Toast.LENGTH_SHORT);
                    searchNearbyServices();
                }
            } else {
                // Went offline - notify user
                if (!hasShownOfflineNotice) {
                    showOfflineSnackbar();
                    hasShownOfflineNotice = true;
                }

                // Try to use cached data
                if (!hasPerformedSearch && hasCachedServicesData()) {
                    loadCachedServicesData();
                }
            }
        });
    }

    /**
     * Update UI components based on offline mode status
     */
    private void updateOfflineModeUI() {
        if (!isAdded()) return;

        mainHandler.post(() -> {
            if (isOfflineMode) {
                offlineBanner.setVisibility(View.VISIBLE);
                offlineModeIndicator.setText(R.string.offline_mode);
            } else {
                offlineBanner.setVisibility(View.GONE);
            }

            // Update search button text based on mode
            if (isOfflineMode) {
                searchButton.setText(R.string.use_cached_data);
            } else {
                searchButton.setText(R.string.search_emergency_services);
            }
        });
    }

    /**
     * Show a snackbar notifying user about offline mode
     */
    private void showOfflineSnackbar() {
        if (!isAdded() || getView() == null) return;

        Snackbar snackbar = Snackbar.make(
                getView(),
                R.string.offline_mode_message,
                Snackbar.LENGTH_LONG);

        snackbar.setAction(R.string.use_cached, v -> {
            if (hasCachedServicesData()) {
                loadCachedServicesData();
            } else if (hasCachedLocationData()) {
                loadCachedLocationData();
                showToast(getString(R.string.using_cached_location), Toast.LENGTH_SHORT);
            } else {
                showToast(getString(R.string.no_cached_data), Toast.LENGTH_SHORT);
            }
        });

        snackbar.show();
    }

    /**
     * Check if we have cached location data
     */
    private boolean hasCachedLocationData() {
        return sharedPreferences.contains(KEY_LAST_LOCATION);
    }

    /**
     * Check if we have cached services data
     */
    private boolean hasCachedServicesData() {
        if (!sharedPreferences.contains(KEY_CACHED_SERVICES)) {
            return false;
        }

        // Check if cache is still valid (not too old)
        long timestamp = sharedPreferences.getLong(KEY_CACHE_TIMESTAMP, 0);
        long currentTime = System.currentTimeMillis();

        return (currentTime - timestamp) < CACHE_MAX_AGE_MS;
    }

    /**
     * Load cached location data
     */
    private void loadCachedLocationData() {
        String locationJson = sharedPreferences.getString(KEY_LAST_LOCATION, null);
        if (locationJson != null) {
            try {
                // Parse cached location data
                String[] parts = locationJson.split(",");
                if (parts.length >= 3) {
                    double latitude = Double.parseDouble(parts[0]);
                    double longitude = Double.parseDouble(parts[1]);
                    long time = Long.parseLong(parts[2]);

                    // Create location object
                    Location cachedLocation = new Location("cache");
                    cachedLocation.setLatitude(latitude);
                    cachedLocation.setLongitude(longitude);
                    cachedLocation.setTime(time);

                    // Only use if we don't have a current location
                    if (currentLocation == null) {
                        currentLocation = cachedLocation;

                        // Update map if ready
                        if (googleMap != null) {
                            updateUserLocationMarker();
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading cached location data", e);
            }
        }
    }

    /**
     * Save current location to cache
     */
    private void cacheLocationData() {
        if (currentLocation != null) {
            String locationJson = String.format(Locale.US, "%f,%f,%d",
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude(),
                    currentLocation.getTime());

            sharedPreferences.edit()
                    .putString(KEY_LAST_LOCATION, locationJson)
                    .apply();
        }
    }

    /**
     * Cache emergency services data for offline use
     */
    private void cacheServicesData() {
        if (markerPlaceData.isEmpty()) {
            return;
        }

        try {
            // Convert to JSON
            String servicesJson = gson.toJson(new ArrayList<>(markerPlaceData.values()));

            // Save to SharedPreferences
            sharedPreferences.edit()
                    .putString(KEY_CACHED_SERVICES, servicesJson)
                    .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                    .apply();

            Log.d(TAG, "Cached " + markerPlaceData.size() + " emergency services for offline use");
        } catch (Exception e) {
            Log.e(TAG, "Error caching services data", e);
        }
    }

    /**
     * Load cached emergency services data
     */
    private void loadCachedServicesData() {
        if (!isAdded()) return;

        try {
            String servicesJson = sharedPreferences.getString(KEY_CACHED_SERVICES, null);
            if (servicesJson != null) {
                progressBar.setVisibility(View.VISIBLE);

                // Parse JSON
                Type listType = new TypeToken<ArrayList<EmergencyServicePlace>>(){}.getType();
                List<EmergencyServicePlace> cachedServices = gson.fromJson(servicesJson, listType);

                // Clear existing markers
                if (googleMap != null) {
                    googleMap.clear();
                    markerPlaceData.clear();
                    userLocationMarker = null;
                }

                // Add user location marker
                updateUserLocationMarker();

                // Add service markers
                for (EmergencyServicePlace place : cachedServices) {
                    // Only add services matching the selected filter
                    if (shouldAddServiceBasedOnFilter(place)) {
                        // Choose marker color based on service type
                        float markerColor;
                        if (place.placeType.equals("hospital")) {
                            markerColor = BitmapDescriptorFactory.HUE_RED;
                        } else if (place.placeType.equals("police")) {
                            markerColor = BitmapDescriptorFactory.HUE_BLUE;
                        } else if (place.placeType.equals("fire_station")) {
                            markerColor = BitmapDescriptorFactory.HUE_ORANGE;
                        } else {
                            markerColor = BitmapDescriptorFactory.HUE_GREEN;
                        }

                        // Add marker
                        Marker marker = googleMap.addMarker(new MarkerOptions()
                                .position(place.location)
                                .title(place.name)
                                .snippet(String.format(Locale.getDefault(),
                                        getString(R.string.place_distance), place.distanceKm))
                                .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                                .zIndex(0.5f));

                        if (marker != null) {
                            markerPlaceData.put(marker.getId(), place);
                        }
                    }
                }

                // Hide progress and show notice
                progressBar.setVisibility(View.GONE);
                showToast(getString(R.string.using_cached_data,
                                formatTimestamp(sharedPreferences.getLong(KEY_CACHE_TIMESTAMP, 0))),
                        Toast.LENGTH_LONG);

                hasPerformedSearch = true;
            } else {
                showToast(getString(R.string.no_cached_data), Toast.LENGTH_SHORT);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached services data", e);
            showToast(getString(R.string.error_loading_cached_data), Toast.LENGTH_SHORT);
            progressBar.setVisibility(View.GONE);
        }
    }

    /**
     * Determine if a service should be added based on the selected filter
     */
    private boolean shouldAddServiceBasedOnFilter(EmergencyServicePlace place) {
        // Check service type filter
        if (!selectedServiceType.equals("all") && !place.placeType.equals(selectedServiceType)) {
            return false;
        }

        // Check proximity filter (if we have current location)
        if (currentLocation != null && place.location != null) {
            float[] results = new float[1];
            Location.distanceBetween(
                    currentLocation.getLatitude(), currentLocation.getLongitude(),
                    place.location.latitude, place.location.longitude,
                    results);

            float distanceKm = results[0] / 1000; // Convert meters to km
            return distanceKm <= (selectedProximityMeters / 1000.0f);
        }

        return true;
    }

    /**
     * Format a timestamp into a readable date string
     */
    private String formatTimestamp(long timestamp) {
        if (timestamp == 0) {
            return "unknown time";
        }

        // Simple date format
        Date date = new Date(timestamp);
        return String.format(Locale.getDefault(), "%1$tB %1$td, %1$tY %1$tI:%1$tM %1$Tp", date);
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

    private void setupProximitySpinner() {
        // Setup proximity spinner with distance options
        List<String> proximityOptions = new ArrayList<>();
        proximityOptions.add("1 KM");
        proximityOptions.add("3 KM");
        proximityOptions.add("5 KM");
        proximityOptions.add("10 KM");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                proximityOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        proximitySpinner.setAdapter(adapter);
        proximitySpinner.setSelection(1); // Default to 3 KM

        proximitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = proximityOptions.get(position);
                int previousRadius = selectedProximityMeters;

                if (selected.equals("1 KM")) {
                    selectedProximityMeters = 1000;
                } else if (selected.equals("3 KM")) {
                    selectedProximityMeters = 3000;
                } else if (selected.equals("5 KM")) {
                    selectedProximityMeters = 5000;
                } else if (selected.equals("10 KM")) {
                    selectedProximityMeters = 10000;
                }

                // Adjust map zoom level based on selected radius
                if (googleMap != null && currentLocation != null && previousRadius != selectedProximityMeters) {
                    adjustMapZoomForRadius();
                }

                // If a search has already been performed and proximity changed
                if (hasPerformedSearch && lastSearchLocation != null) {
                    if (isOfflineMode && hasCachedServicesData()) {
                        // In offline mode, reload cached data with new filter
                        loadCachedServicesData();
                    } else if (!isOfflineMode) {
                        // Online mode - do a new search
                        searchNearbyServices();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    /**
     * Adjust the map zoom level based on the selected radius
     */
    private void adjustMapZoomForRadius() {
        if (googleMap != null && currentLocation != null) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

            // Calculate appropriate zoom level based on radius
            float zoomLevel;
            if (selectedProximityMeters <= 1000) {
                zoomLevel = 15.5f;      // 1km
            } else if (selectedProximityMeters <= 3000) {
                zoomLevel = 14f;        // 3km
            } else if (selectedProximityMeters <= 5000) {
                zoomLevel = 13f;        // 5km
            } else {
                zoomLevel = 12f;        // 10km
            }

            // Smoothly animate to new zoom level
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel));
        }
    }

    private void setupClickListeners() {
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

        // Setup service category radio buttons
        serviceCategoryGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_all) {
                selectedServiceType = "all";
            } else if (checkedId == R.id.radio_hospital) {
                selectedServiceType = "hospital";
            } else if (checkedId == R.id.radio_police) {
                selectedServiceType = "police";
            } else if (checkedId == R.id.radio_fire) {
                selectedServiceType = "fire_station";
            }

            // If we've already done a search
            if (hasPerformedSearch) {
                if (isOfflineMode && hasCachedServicesData()) {
                    // In offline mode, reload cached data with new filter
                    loadCachedServicesData();
                } else if (!isOfflineMode) {
                    // Online mode - do a new search
                    searchNearbyServices();
                }
            }
        });

        // Setup search button
        searchButton.setOnClickListener(v -> {
            if (isOfflineMode) {
                // In offline mode, use cached data
                if (hasCachedServicesData()) {
                    loadCachedServicesData();
                } else if (hasCachedLocationData() && currentLocation == null) {
                    loadCachedLocationData();
                    showToast(getString(R.string.no_emergency_services_cached), Toast.LENGTH_SHORT);
                } else {
                    showToast(getString(R.string.no_cached_data), Toast.LENGTH_SHORT);
                }
                return;
            }

            // Online mode
            if (currentLocation != null) {
                searchNearbyServices();
            } else {
                showToast(getString(R.string.waiting_for_location), Toast.LENGTH_SHORT);
                // Try to get a fresh location
                locationManager.getCurrentLocation(new LocationManager.LocationUpdateListener() {
                    @Override
                    public void onLocationUpdated(Location location) {
                        currentLocation = location;
                        cacheLocationData();
                        searchNearbyServices();
                    }

                    @Override
                    public void onLocationError(String message) {
                        showToast(getString(R.string.location_unavailable), Toast.LENGTH_SHORT);

                        // Try to use cached location as fallback
                        if (hasCachedLocationData()) {
                            loadCachedLocationData();
                            if (currentLocation != null) {
                                searchNearbyServices();
                            }
                        }
                    }
                });
            }
        });
    }

    private void setupLocationListener() {
        locationManager.setLocationUpdateListener(new LocationManager.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(Location location) {
                // Check if we've moved enough to warrant an update
                boolean significantMove = isSignificantLocationChange(currentLocation, location);

                // Update current location
                currentLocation = location;

                // Cache the location for offline use
                cacheLocationData();

                // Update user marker
                updateUserLocationMarker();

                // Check if we should refresh emergency services
                if (!isOfflineMode && hasPerformedSearch && significantMove && autoRefreshEnabled && lastSearchLocation != null) {
                    // Calculate distance from last search location
                    float distanceFromLastSearch = location.distanceTo(lastSearchLocation);

                    // If we've moved beyond the threshold, refresh services
                    if (distanceFromLastSearch > LOCATION_REFRESH_THRESHOLD) {
                        showLocationUpdateSnackbar(distanceFromLastSearch);
                        searchNearbyServices();
                    }
                }
            }

            @Override
            public void onLocationError(String message) {
                showToast("Location error: " + message, Toast.LENGTH_SHORT);

                // Use cached location as fallback if we don't have a current location
                if (currentLocation == null && hasCachedLocationData()) {
                    loadCachedLocationData();
                    if (currentLocation != null) {
                        updateUserLocationMarker();
                    }
                }
            }
        });
    }

    /**
     * Determines if there's been a significant location change that warrants an update
     * @param oldLocation Previous location
     * @param newLocation Current location
     * @return true if the change is significant
     */
    private boolean isSignificantLocationChange(Location oldLocation, Location newLocation) {
        if (oldLocation == null || newLocation == null) {
            return true;
        }

        // Check distance between locations
        float distance = oldLocation.distanceTo(newLocation);

        // Consider it significant if moved more than 10 meters
        return distance > 10;
    }

    /**
     * Shows a snackbar with information about location update and service refresh
     * @param distance Distance in meters from last search
     */
    private void showLocationUpdateSnackbar(float distance) {
        if (!isAdded() || getView() == null) return;

        // Convert to appropriate unit
        String distanceText;
        if (distance >= 1000) {
            distanceText = String.format(Locale.getDefault(), "%.1f km", distance/1000);
        } else {
            distanceText = String.format(Locale.getDefault(), "%d m", (int)distance);
        }

        Snackbar snackbar = Snackbar.make(getView(),
                getString(R.string.location_moved_refreshing, distanceText),
                Snackbar.LENGTH_LONG);

        snackbar.setAction(R.string.disable_auto_refresh, v -> {
            autoRefreshEnabled = false;
            showToast(getString(R.string.auto_refresh_disabled), Toast.LENGTH_SHORT);
        });

        snackbar.show();
    }

    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    requireActivity(),
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

    /**
     * Updates just the user's location marker without affecting other markers
     */
    private void updateUserLocationMarker() {
        if (googleMap != null && currentLocation != null && isAdded()) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

            // Remove old user marker if it exists
            if (userLocationMarker != null) {
                userLocationMarker.remove();
            }

            // Add new marker at current location
            userLocationMarker = googleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.current_location))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .zIndex(1.0f)); // Make user marker appear on top of other markers
        }
    }

    /**
     * Initialize the map with the user's location marker
     * Called when the map is first ready
     */
    private void initializeMapWithLocation() {
        if (googleMap != null && currentLocation != null) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

            // Add marker at current location
            userLocationMarker = googleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.current_location))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .zIndex(1.0f));

            // Animate camera to show current location
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f));
        }
    }

    private void showToast(String message, int duration) {
        if (getActivity() == null) return;

        requireActivity().runOnUiThread(() -> {
            // Cancel any existing toast
            if (currentToast != null) {
                currentToast.cancel();
            }

            // Create and show new toast
            currentToast = Toast.makeText(requireContext(), message, duration);
            currentToast.show();
        });
    }

    private void searchNearbyServices() {
        if (isOfflineMode) {
            // In offline mode, use cached data
            if (hasCachedServicesData()) {
                loadCachedServicesData();
            } else if (hasCachedLocationData() && currentLocation == null) {
                loadCachedLocationData();
                showToast(getString(R.string.no_emergency_services_cached), Toast.LENGTH_SHORT);
            } else {
                showToast(getString(R.string.no_cached_data), Toast.LENGTH_SHORT);
            }
            return;
        }

        if (currentLocation == null) {
            showToast(getString(R.string.waiting_for_location), Toast.LENGTH_SHORT);
            return;
        }

        // Show loading indicator
        progressBar.setVisibility(View.VISIBLE);

        // Mark as searching
        isSearchingPlaces = true;

        // Store this location as the last search location
        lastSearchLocation = new Location(currentLocation);

        // Mark that we've performed a search
        hasPerformedSearch = true;

        // Clear previous markers and data, but keep user marker reference
        if (googleMap != null) {
            googleMap.clear();
            markerPlaceData.clear();
            userLocationMarker = null;

            // Add back the user's current location marker
            updateUserLocationMarker();
        }

        // Create location for search
        LatLng location = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

        // Counter to track completion of all searches
        AtomicInteger searchCounter = new AtomicInteger(0);

        // For larger search radii, adjust the priority to find more results
        int maxResultsPerType;
        if (selectedProximityMeters <= 3000) {
            maxResultsPerType = 5;  // Default 5 results for smaller radii
        } else if (selectedProximityMeters <= 5000) {
            maxResultsPerType = 8;  // Increase for 5km
        } else {
            maxResultsPerType = 10; // Max 10 results for 10km
        }

        // Determine services to search based on selection
        if (selectedServiceType.equals("all") || selectedServiceType.equals("hospital")) {
            searchNearbyPlaces(location, "hospital", BitmapDescriptorFactory.HUE_RED, searchCounter, maxResultsPerType);
        }

        if (selectedServiceType.equals("all") || selectedServiceType.equals("police")) {
            searchNearbyPlaces(location, "police", BitmapDescriptorFactory.HUE_BLUE, searchCounter, maxResultsPerType);
        }

        if (selectedServiceType.equals("all") || selectedServiceType.equals("fire_station")) {
            searchNearbyPlaces(location, "fire_station", BitmapDescriptorFactory.HUE_ORANGE, searchCounter, maxResultsPerType);
        }

        showToast(getString(R.string.loading_places), Toast.LENGTH_SHORT);
    }

    private void searchNearbyPlaces(LatLng location, String placeType, float markerColor, AtomicInteger counter, int maxResults) {
        // Get the keywords for this place type
        List<String> keywords = placeTypeKeywords.get(placeType);
        if (keywords == null || keywords.isEmpty()) {
            return;
        }

        // For larger radii, use a more aggressive search approach
        List<String> effectiveKeywords;
        if (selectedProximityMeters >= 5000) {
            // For larger radii, add additional keywords to find more places
            effectiveKeywords = new ArrayList<>(keywords);

            // Add general emergency service keywords for broader search
            if (placeType.equals("hospital")) {
                effectiveKeywords.add("clinic");
                effectiveKeywords.add("emergency");
                effectiveKeywords.add("urgent care");
            } else if (placeType.equals("police")) {
                effectiveKeywords.add("sheriff");
                effectiveKeywords.add("security");
            } else if (placeType.equals("fire_station")) {
                effectiveKeywords.add("emergency services");
                effectiveKeywords.add("rescue");
            }
        } else {
            // For smaller radii, use original keywords
            effectiveKeywords = keywords;
        }

        // Expand bounds slightly for larger radii to ensure we don't miss any places
        double expansionFactor = 1.0;
        if (selectedProximityMeters >= 5000) {
            expansionFactor = 1.2; // Expand bounds by 20% for larger radii
        }

        // Calculate bounds with potential expansion
        double latRadiusDegrees = (selectedProximityMeters * expansionFactor) / 111000.0; // approximate degrees per meter
        double lngRadiusDegrees = latRadiusDegrees / Math.cos(Math.toRadians(location.latitude));

        RectangularBounds bounds = RectangularBounds.newInstance(
                new LatLng(location.latitude - latRadiusDegrees, location.longitude - lngRadiusDegrees),
                new LatLng(location.latitude + latRadiusDegrees, location.longitude + lngRadiusDegrees));

        // Counter for found places
        AtomicInteger foundPlacesCounter = new AtomicInteger(0);
        AtomicInteger keywordRequestsCounter = new AtomicInteger(0);
        final int totalKeywords = effectiveKeywords.size();
        final int MAX_RESULTS = maxResults;

        // Search for places using each keyword
        for (String keyword : effectiveKeywords) {
            if (foundPlacesCounter.get() >= MAX_RESULTS) {
                // Already found enough places
                if (keywordRequestsCounter.incrementAndGet() >= totalKeywords) {
                    checkSearchCompletion(counter);
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
                                    Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG,
                                    Place.Field.ADDRESS, Place.Field.PHONE_NUMBER);

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

                                        if (distanceKm <= (selectedProximityMeters / 1000.0f)) {
                                            // Store place data
                                            String phoneNumber = place.getPhoneNumber() != null ?
                                                    place.getPhoneNumber() : getString(R.string.no_phone_available);
                                            String address = place.getAddress() != null ?
                                                    place.getAddress() : getString(R.string.address_unavailable);

                                            // Create place data object
                                            EmergencyServicePlace servicePlace = new EmergencyServicePlace(
                                                    place.getName(),
                                                    address,
                                                    phoneNumber,
                                                    placeType,
                                                    distanceKm,
                                                    place.getLatLng()
                                            );

                                            // Add marker to map on main thread
                                            mainHandler.post(() -> {
                                                if (googleMap != null && isAdded() && !isDetached()) {
                                                    // Add marker
                                                    Marker marker = googleMap.addMarker(new MarkerOptions()
                                                            .position(place.getLatLng())
                                                            .title(place.getName())
                                                            .snippet(String.format(Locale.getDefault(),
                                                                    getString(R.string.place_distance), distanceKm))
                                                            .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                                                            .zIndex(0.5f)); // Lower than user marker

                                                    // Store marker data with marker ID
                                                    if (marker != null) {
                                                        markerPlaceData.put(marker.getId(), servicePlace);
                                                    }

                                                    // Increment counter only for unique places
                                                    foundPlacesCounter.incrementAndGet();
                                                }
                                            });
                                        }
                                    })
                                    .addOnCompleteListener(task -> {
                                        // Whether successful or not, count this request as completed
                                        if (keywordRequestsCounter.incrementAndGet() >= totalKeywords) {
                                            checkSearchCompletion(counter);
                                        }
                                    });
                        }

                        // If no predictions found, we still need to track completion
                        if (response.getAutocompletePredictions().isEmpty() &&
                                keywordRequestsCounter.incrementAndGet() >= totalKeywords) {
                            checkSearchCompletion(counter);
                        }
                    })
                    .addOnFailureListener(exception -> {
                        Log.e(TAG, "Place search failed: " + exception.getMessage());

                        // Count this as completed even on failure
                        if (keywordRequestsCounter.incrementAndGet() >= totalKeywords) {
                            checkSearchCompletion(counter);
                        }
                    });
        }
    }

    private void checkSearchCompletion(AtomicInteger counter) {
        // For "all" services, we need to wait for all 3 types to complete
        int requiredCompletions = selectedServiceType.equals("all") ? 3 : 1;

        if (counter.incrementAndGet() >= requiredCompletions) {
            // All searches completed
            isSearchingPlaces = false;

            // Cache the search results for offline use
            cacheServicesData();

            // Hide progress bar
            mainHandler.post(() -> {
                if (isAdded() && !isDetached()) {
                    progressBar.setVisibility(View.GONE);

                    // Check if we found any places
                    if (markerPlaceData.isEmpty()) {
                        // No emergency services found within radius
                        showNoServicesFoundMessage();
                    } else {
                        showToast(getString(R.string.places_loaded), Toast.LENGTH_SHORT);
                    }

                    // Ensure the user marker is still visible on top
                    updateUserLocationMarker();
                }
            });
        }
    }

    private void showNoServicesFoundMessage() {
        if (!isAdded() || getView() == null) return;

        // Show toast
        showToast(getString(R.string.no_places_found), Toast.LENGTH_SHORT);

        // Show more detailed snackbar with options
        Snackbar snackbar = Snackbar.make(
                getView(),
                getString(R.string.no_services_in_radius,
                        selectedProximityMeters / 1000), // Convert to km for display
                Snackbar.LENGTH_LONG);

        // Option to increase search radius
        snackbar.setAction(R.string.increase_radius, v -> {
            // Find the next larger radius option
            int currentPosition = proximitySpinner.getSelectedItemPosition();
            if (currentPosition < proximitySpinner.getCount() - 1) {
                proximitySpinner.setSelection(currentPosition + 1);
                // Search will be triggered by the spinner selection listener
            } else {
                showToast(getString(R.string.already_max_radius), Toast.LENGTH_SHORT);
            }
        });

        snackbar.show();
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
        boolean isNightMode = requireContext().getResources().getBoolean(R.bool.is_night_mode);
        if (isNightMode) {
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_night));
        } else {
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_day));
        }

        // Set custom info window adapter
        googleMap.setInfoWindowAdapter(new EmergencyServiceInfoWindowAdapter());

        // Set marker click listener
        googleMap.setOnInfoWindowClickListener(this::handleInfoWindowClick);

        // Try to enable my location layer if permission is granted
        enableMyLocationIfPermitted();

        // If we already have a location, initialize the map
        if (currentLocation != null) {
            initializeMapWithLocation();
        }

        // If in offline mode, try to load cached data automatically
        if (isOfflineMode && hasCachedServicesData()) {
            loadCachedServicesData();
        }
    }

    private void handleInfoWindowClick(Marker marker) {
        // Retrieve the place data associated with this marker
        EmergencyServicePlace place = markerPlaceData.get(marker.getId());
        if (place == null) return;

        // Create and show action dialog
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(place.name)
                .setItems(new String[]{
                        getString(R.string.call),
                        getString(R.string.navigate)
                }, (dialog, which) -> {
                    switch (which) {
                        case 0: // Call
                            if (place.phoneNumber != null && !place.phoneNumber.equals(getString(R.string.no_phone_available))) {
                                Intent callIntent = new Intent(Intent.ACTION_DIAL);
                                callIntent.setData(Uri.parse("tel:" + place.phoneNumber));
                                startActivity(callIntent);
                            } else {
                                showToast(getString(R.string.no_phone_available), Toast.LENGTH_SHORT);
                            }
                            break;
                        case 1: // Navigate
                            // Open Google Maps with navigation
                            Uri navigationUri = Uri.parse("google.navigation:q=" +
                                    place.location.latitude + "," + place.location.longitude);
                            Intent mapIntent = new Intent(Intent.ACTION_VIEW, navigationUri);
                            mapIntent.setPackage("com.google.android.apps.maps");

                            // Check if Google Maps is installed
                            if (mapIntent.resolveActivity(requireContext().getPackageManager()) != null) {
                                startActivity(mapIntent);
                            } else {
                                // Fallback to browser if Google Maps isn't installed
                                Uri browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" +
                                        place.location.latitude + "," + place.location.longitude);
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, browserUri);
                                startActivity(browserIntent);
                            }
                            break;
                    }
                })
                .show();
    }

    // Custom Info Window Adapter for emergency service markers
    private class EmergencyServiceInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {
        private final View infoWindow;

        EmergencyServiceInfoWindowAdapter() {
            infoWindow = getLayoutInflater().inflate(R.layout.emergency_service_info_window, null);
        }

        @Override
        public View getInfoWindow(Marker marker) {
            // Return null to use the default window frame
            return null;
        }

        @Override
        public View getInfoContents(Marker marker) {
            // Get place data associated with this marker
            EmergencyServicePlace place = markerPlaceData.get(marker.getId());

            // If no place data is found, use default info window
            if (place == null) {
                return null;
            }

            // Set up the custom info window view
            TextView titleText = infoWindow.findViewById(R.id.text_title);
            TextView addressText = infoWindow.findViewById(R.id.text_address);
            TextView phoneText = infoWindow.findViewById(R.id.text_phone);
            TextView distanceText = infoWindow.findViewById(R.id.text_distance);
            ImageView phoneIcon = infoWindow.findViewById(R.id.icon_phone);

            // Set the content
            titleText.setText(place.name);
            addressText.setText(place.address);

            if (place.phoneNumber != null && !place.phoneNumber.equals(getString(R.string.no_phone_available))) {
                phoneText.setText(place.phoneNumber);
                phoneText.setVisibility(View.VISIBLE);
                phoneIcon.setVisibility(View.VISIBLE);
            } else {
                phoneText.setVisibility(View.GONE);
                phoneIcon.setVisibility(View.GONE);
            }

            distanceText.setText(String.format(Locale.getDefault(),
                    getString(R.string.place_distance), place.distanceKm));

            return infoWindow;
        }
    }

    private void enableMyLocationIfPermitted() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (googleMap != null) {
                googleMap.setMyLocationEnabled(true);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                startLocationUpdates();
                enableMyLocationIfPermitted();
            } else {
                // Permission denied
                showPermissionDeniedMessage();

                // The implementation falls back to cached data if available
                if (hasCachedLocationData()) {
                    loadCachedLocationData();
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void showPermissionDeniedMessage() {
        if (!isAdded() || getView() == null) return;

        // Show toast
        showToast(getString(R.string.location_permission_denied), Toast.LENGTH_SHORT);

        // Show more detailed snackbar with option to go to settings
        Snackbar snackbar = Snackbar.make(
                getView(),
                getString(R.string.location_permission_required),
                Snackbar.LENGTH_LONG);

        snackbar.setAction(R.string.permission_settings, v -> {
            // Open app settings so user can enable permissions
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        });

        snackbar.show();
    }

    // MapView lifecycle methods
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
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
    public void onStart() {
        super.onStart();
        mapView.onStart();
        startLocationUpdates();

        // Reset offline notice flag when starting
        hasShownOfflineNotice = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();

        // Check network state when resuming
        updateOfflineModeUI();
    }

    @Override
    public void onPause() {
        mapView.onPause();
        super.onPause();
    }

    @Override
    public void onStop() {
        mapView.onStop();
        locationManager.stopLocationUpdates();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        mapView.onDestroy();
        networkManager.stopNetworkMonitoring();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}