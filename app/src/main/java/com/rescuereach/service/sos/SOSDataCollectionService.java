package com.rescuereach.service.sos;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.model.TypeFilter;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.rescuereach.data.model.EmergencyService;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.util.DeviceUtils;
import com.rescuereach.util.LocationManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for collecting all data needed for an SOS emergency report
 */
public class SOSDataCollectionService {
    private static final String TAG = "SOSDataCollection";

    // Constants
    private static final int NEARBY_SERVICES_RADIUS = 5000; // 5km radius
    private static final int MAX_EMERGENCY_SERVICES = 3; // Max number of nearby services to include

    // Services and managers
    private final Context context;
    private final LocationManager locationManager;
    private final UserSessionManager sessionManager;
    private final PlacesClient placesClient;
    private final FirebaseFirestore db;
    private final ExecutorService executorService;

    // Emergency service types mapping
    private final Map<String, String> emergencyTypeToPlaceType;
    private final Map<String, String> emergencyTypeToTollFree;

    /**
     * Create a new SOS Data Collection Service
     * @param context The application context
     */
    public SOSDataCollectionService(Context context) {
        this.context = context.getApplicationContext();
        this.locationManager = new LocationManager(context);
        this.sessionManager = UserSessionManager.getInstance(context);
        this.db = FirebaseFirestore.getInstance();
        this.executorService = Executors.newCachedThreadPool();

        // Initialize Places API if needed
        if (!Places.isInitialized()) {
            // Note: This requires your API key to be correctly set in strings.xml
            Places.initialize(context, context.getString(com.rescuereach.R.string.google_maps_key));
        }

        this.placesClient = Places.createClient(context);

        // Set up emergency type mappings
        emergencyTypeToPlaceType = new HashMap<>();
        emergencyTypeToPlaceType.put("POLICE", "police");
        emergencyTypeToPlaceType.put("FIRE", "fire_station");
        emergencyTypeToPlaceType.put("MEDICAL", "hospital");

        // Set up toll-free numbers (example - will need to be configured per country)
        emergencyTypeToTollFree = new HashMap<>();
        emergencyTypeToTollFree.put("POLICE", "100");
        emergencyTypeToTollFree.put("FIRE", "101");
        emergencyTypeToTollFree.put("MEDICAL", "108");
    }

    /**
     * Collect all data for an SOS report
     * @param emergencyType The type of emergency (POLICE, FIRE, MEDICAL)
     * @param dataCollectionListener Callback for when data collection is complete
     */
    public void collectSOSData(String emergencyType, SOSDataCollectionListener dataCollectionListener) {
        Log.d(TAG, "Starting SOS data collection for: " + emergencyType);

        // Create progress tracker
        final boolean[] networkAvailable = {isNetworkAvailable()};
        final SOSReport[] report = {new SOSReport()};
        report[0].setEmergencyType(emergencyType);
        report[0].setOnline(networkAvailable[0]);
        report[0].setUserId(sessionManager.getSavedPhoneNumber());

        // Get precise location for emergency
        locationManager.shareLocationDuringEmergency(new LocationManager.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(Location location) {
                // Process with this location
                processLocationData(location, emergencyType, report[0], networkAvailable[0], dataCollectionListener);
            }

            @Override
            public void onLocationError(String message) {
                Log.e(TAG, "Location error: " + message);

                // Try to use last known location
                Location lastKnown = locationManager.getLastKnownLocation();
                if (lastKnown != null) {
                    Log.d(TAG, "Using last known location");
                    processLocationData(lastKnown, emergencyType, report[0], networkAvailable[0], dataCollectionListener);
                } else {
                    // We couldn't get a location, report error
                    dataCollectionListener.onDataCollectionFailed(
                            "Could not determine your location. Please try again.");
                }
            }
        });
    }

    /**
     * Process location data and collect other required information
     */
    private void processLocationData(Location location, String emergencyType, SOSReport report,
                                     boolean isOnline, SOSDataCollectionListener listener) {
        // Create GeoPoint from location
        GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        report.setLocation(geoPoint);

        // Get address information
        getAddressFromLocation(location, report, () -> {
            // Add user information
            addUserInformation(report);

            // Add device information
            addDeviceInformation(report);

            // Get emergency contacts (just phone numbers)
            getEmergencyContactNumbers(report, () -> {
                // If online, get nearby services
                if (isOnline) {
                    getNearbyEmergencyServices(location, emergencyType, report, () -> {
                        // All data collected, return the report
                        listener.onDataCollectionComplete(report);
                    });
                } else {
                    // When offline, use cached emergency services if available
                    getCachedEmergencyServices(location, emergencyType, report);

                    // All data collected (as much as possible offline), return the report
                    listener.onDataCollectionComplete(report);
                }
            });
        });
    }

    /**
     * Use Geocoder to get address information from location
     */
    private void getAddressFromLocation(Location location, SOSReport report, Runnable onComplete) {
        executorService.execute(() -> {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(
                        location.getLatitude(), location.getLongitude(), 1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);

                    // Extract address components
                    StringBuilder fullAddress = new StringBuilder();
                    for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                        if (i > 0) fullAddress.append(", ");
                        fullAddress.append(address.getAddressLine(i));
                    }

                    String city = address.getLocality();
                    String state = address.getAdminArea();

                    // Update report on main thread
                    runOnMainThread(() -> {
                        report.setAddress(fullAddress.toString());
                        report.setCity(city != null ? city : "Unknown");
                        report.setState(state != null ? state : "Unknown");
                        onComplete.run();
                    });
                } else {
                    // No address found, use coordinates
                    String coordsStr = String.format(Locale.US, "%.6f, %.6f",
                            location.getLatitude(), location.getLongitude());

                    runOnMainThread(() -> {
                        report.setAddress(coordsStr);
                        report.setCity("Unknown");
                        report.setState("Unknown");
                        onComplete.run();
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Error getting address", e);

                // Handle error - use coordinates as fallback
                String coordsStr = String.format(Locale.US, "%.6f, %.6f",
                        location.getLatitude(), location.getLongitude());

                runOnMainThread(() -> {
                    report.setAddress(coordsStr);
                    report.setCity("Unknown");
                    report.setState("Unknown");
                    onComplete.run();
                });
            }
        });
    }

    /**
     * Add user information from session manager
     */
    private void addUserInformation(SOSReport report) {
        // Add basic user info
        report.addUserInfo("phoneNumber", sessionManager.getSavedPhoneNumber());
        report.addUserInfo("name", sessionManager.getFullName());
        report.addUserInfo("isVolunteer", sessionManager.isVolunteer());

        // Add user's state if available
        String state = sessionManager.getState();
        if (state != null && !state.isEmpty()) {
            report.addUserInfo("state", state);
        }

        // Add gender if available
        String gender = sessionManager.getGender();
        if (gender != null && !gender.isEmpty()) {
            report.addUserInfo("gender", gender);
        }

        // Add any other available profile fields
        report.addUserInfo("firstName", sessionManager.getFirstName());
        report.addUserInfo("lastName", sessionManager.getLastName());
    }

    /**
     * Add device information
     */
    private void addDeviceInformation(SOSReport report) {
        // Add device model and OS information
        report.addDeviceInfo("model", Build.MODEL);
        report.addDeviceInfo("manufacturer", Build.MANUFACTURER);
        report.addDeviceInfo("osVersion", Build.VERSION.RELEASE);
        report.addDeviceInfo("apiLevel", Build.VERSION.SDK_INT);

        // Add battery information
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        report.addDeviceInfo("batteryLevel", batteryLevel);

        // Add network information
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());

        String networkType = "OFFLINE";
        boolean hasInternet = false;

        if (capabilities != null) {
            hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                networkType = "WIFI";
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                networkType = "CELLULAR";
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                networkType = "ETHERNET";
            }
        }

        report.addDeviceInfo("networkType", networkType);
        report.addDeviceInfo("hasInternet", hasInternet);

        // Add device ID (installation ID)
        String deviceId = DeviceUtils.getDeviceId(context);
        report.addDeviceInfo("deviceId", deviceId);
    }

    /**
     * Get emergency contact numbers from Firestore or local storage
     */
    private void getEmergencyContactNumbers(SOSReport report, Runnable onComplete) {
        String userId = sessionManager.getSavedPhoneNumber();
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "No user ID available for getting emergency contacts");
            onComplete.run();
            return;
        }

        // First check if we have emergency contact directly from session manager
        String emergencyContact = sessionManager.getEmergencyContactPhone();
        if (emergencyContact != null && !emergencyContact.isEmpty()) {
            report.addEmergencyContactNumber(emergencyContact);
            onComplete.run();
            return;
        }

        if (isNetworkAvailable()) {
            // Get from Firestore
            db.collection("users").document(userId)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            // Get emergency contact number(s)
                            String contactFromFirestore = task.getResult().getString("emergencyContact");
                            if (contactFromFirestore != null && !contactFromFirestore.isEmpty()) {
                                report.addEmergencyContactNumber(contactFromFirestore);
                            }

                            // Look for a list of numbers too (for backward compatibility)
                            List<String> emergencyContacts =
                                    (List<String>) task.getResult().get("emergencyContacts");

                            if (emergencyContacts != null && !emergencyContacts.isEmpty()) {
                                for (String contactNumber : emergencyContacts) {
                                    report.addEmergencyContactNumber(contactNumber);
                                }
                            }
                        } else {
                            Log.e(TAG, "Error getting emergency contacts",
                                    task.getException());
                        }
                        onComplete.run();
                    });
        } else {
            // TODO: Get from local storage in a future implementation
            onComplete.run();
        }
    }

    /**
     * Get nearby emergency services using Places API
     */
    private void getNearbyEmergencyServices(Location location, String emergencyType,
                                            SOSReport report, Runnable onComplete) {
        // Get the place type based on emergency type
        String placeType = emergencyTypeToPlaceType.get(emergencyType);
        if (placeType == null) {
            Log.e(TAG, "Unknown emergency type: " + emergencyType);
            onComplete.run();
            return;
        }

        // Create bounds for the search (approximately within NEARBY_SERVICES_RADIUS)
        double latRadiusDegrees = NEARBY_SERVICES_RADIUS / 111000.0; // approximate degrees per meter
        double lngRadiusDegrees = latRadiusDegrees /
                Math.cos(Math.toRadians(location.getLatitude()));

        LatLng center = new LatLng(location.getLatitude(), location.getLongitude());

        RectangularBounds bounds = RectangularBounds.newInstance(
                new LatLng(center.latitude - latRadiusDegrees, center.longitude - lngRadiusDegrees),
                new LatLng(center.latitude + latRadiusDegrees, center.longitude + lngRadiusDegrees));

        // Create the query
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setLocationBias(bounds)
                .setOrigin(center)
                .setTypeFilter(TypeFilter.ESTABLISHMENT)
                .setQuery(placeType)
                .build();

        // Execute the search
        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener(response -> {
                    // Process predictions
                    int servicesFound = 0;
                    List<Task<?>> tasks = new ArrayList<>();

                    for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                        if (servicesFound >= MAX_EMERGENCY_SERVICES) break;

                        // Create a fetch place request
                        List<Place.Field> placeFields = Arrays.asList(
                                Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG,
                                Place.Field.ADDRESS, Place.Field.PHONE_NUMBER);

                        FetchPlaceRequest fetchRequest = FetchPlaceRequest.builder(
                                prediction.getPlaceId(), placeFields).build();

                        // Queue the request
                        Task<Void> task = placesClient.fetchPlace(fetchRequest)
                                .addOnSuccessListener(fetchResponse -> {
                                    Place place = fetchResponse.getPlace();
                                    if (place.getLatLng() != null) {
                                        // Calculate distance
                                        float[] results = new float[1];
                                        Location.distanceBetween(
                                                location.getLatitude(), location.getLongitude(),
                                                place.getLatLng().latitude, place.getLatLng().longitude,
                                                results);

                                        float distanceKm = results[0] / 1000; // Convert meters to km

                                        // Create emergency service object
                                        EmergencyService service = new EmergencyService(
                                                place.getId(),
                                                place.getName(),
                                                emergencyType,
                                                new GeoPoint(
                                                        place.getLatLng().latitude,
                                                        place.getLatLng().longitude),
                                                place.getAddress(),
                                                place.getPhoneNumber(),
                                                distanceKm,
                                                emergencyTypeToTollFree.get(emergencyType)
                                        );

                                        // Add to report
                                        report.addNearbyService(service);

                                        // Cache this service for offline use
                                        cacheEmergencyService(service);
                                    }
                                })
                                .addOnFailureListener(e ->
                                        Log.e(TAG, "Error fetching place details", e))
                                .continueWith(task2 -> null);

                        tasks.add(task);
                        servicesFound++;
                    }

                    // Wait for all tasks to complete
                    try {
                        Tasks.whenAll(tasks).addOnCompleteListener(task -> {
                            // Sort services by distance
                            Collections.sort(report.getNearbyServices(),
                                    (s1, s2) -> Double.compare(s1.getDistance(), s2.getDistance()));

                            // Limit to max services
                            if (report.getNearbyServices().size() > MAX_EMERGENCY_SERVICES) {
                                report.setNearbyServices(report.getNearbyServices()
                                        .subList(0, MAX_EMERGENCY_SERVICES));
                            }

                            // Complete the operation
                            onComplete.run();
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error waiting for place details", e);
                        onComplete.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error finding nearby emergency services", e);
                    onComplete.run();
                });
    }

    /**
     * Cache emergency services for offline use
     */
    private void cacheEmergencyService(EmergencyService service) {
        // TODO: Implement local caching of emergency services in a future phase
        // This would use Room Database to store services for offline retrieval
    }

    /**
     * Get cached emergency services for offline use
     */
    private void getCachedEmergencyServices(Location location, String emergencyType, SOSReport report) {
        // TODO: Implement retrieval of cached emergency services in a future phase
        // This would use Room Database to retrieve previously cached nearby services

        // For now, just add the toll-free number
        String tollFree = emergencyTypeToTollFree.get(emergencyType);
        if (tollFree != null) {
            EmergencyService emergencyService = new EmergencyService();
            emergencyService.setName("Emergency " + emergencyType + " Service");
            emergencyService.setType(emergencyType);
            emergencyService.setTollFreeNumber(tollFree);
            report.addNearbyService(emergencyService);
        }
    }

    /**
     * Check if network is available
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());

        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    /**
     * Run task on main thread
     */
    private void runOnMainThread(Runnable runnable) {
        android.os.Handler handler = new android.os.Handler(context.getMainLooper());
        handler.post(runnable);
    }

    /**
     * Interface for SOS data collection callbacks
     */
    public interface SOSDataCollectionListener {
        void onDataCollectionComplete(SOSReport report);
        void onDataCollectionFailed(String errorMessage);
    }
}