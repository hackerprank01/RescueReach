package com.rescuereach.service.sos;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.firebase.firestore.GeoPoint;
import com.rescuereach.data.model.EmergencyContact;
import com.rescuereach.data.model.EmergencyService;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.util.DeviceInfoHelper;
import com.rescuereach.util.EmergencyContactManager;
import com.rescuereach.util.LocationManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Service to collect all data required for SOS emergency reporting
 */
public class SOSDataCollectionService {
    private static final String TAG = "SOSDataCollection";

    // Constants
    private static final int SEARCH_RADIUS = 5000; // 5 km radius for emergency services
    private static final int MAX_SERVICES_PER_TYPE = 3; // Max number of each service type to include
    private static final long LOCATION_TIMEOUT_MS = 10000; // 10 seconds timeout for location
    private static final long NEARBY_SERVICES_TIMEOUT_MS = 15000; // 15 seconds timeout for services

    // Context and services
    private final Context context;
    private final UserSessionManager sessionManager;
    private final LocationManager locationManager;
    private final EmergencyContactManager contactManager;
    private final DeviceInfoHelper deviceInfoHelper;
    private PlacesClient placesClient;

    // Required for SOS
    private String emergencyType;
    private Location currentLocation;
    private Address currentAddress;
    private SOSReport sosReport;

    // Listener for data collection completion
    private SOSDataListener dataListener;

    // State variables
    private boolean isCollectingData = false;
    private boolean isOnline = false;

    /**
     * Constructor
     */
    public SOSDataCollectionService(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);
        this.locationManager = new LocationManager(context);
        this.contactManager = EmergencyContactManager.getInstance(context);
        this.deviceInfoHelper = new DeviceInfoHelper(context);

        // Initialize Places API if not already initialized
        if (!Places.isInitialized()) {
            Places.initialize(context.getApplicationContext(), context.getString(com.rescuereach.R.string.google_maps_key));
        }
        placesClient = Places.createClient(context);

        // Check network connectivity
        checkNetworkConnectivity();
    }

    /**
     * Begin collecting all necessary data for an SOS report
     * @param emergencyType The type of emergency (POLICE, FIRE, MEDICAL)
     * @param listener Callback for when data collection is complete
     */
    public void collectSOSData(String emergencyType, SOSDataListener listener) {
        if (isCollectingData) {
            Log.w(TAG, "Already collecting data, ignoring duplicate request");
            return;
        }

        this.emergencyType = emergencyType;
        this.dataListener = listener;
        this.isCollectingData = true;

        // Initialize SOS report
        sosReport = new SOSReport(emergencyType);
        sosReport.setOnline(isOnline);

        // Start collecting data in sequence
        collectUserData();
    }

    /**
     * Step 1: Collect user data from UserSessionManager
     */
    private void collectUserData() {
        Log.d(TAG, "Collecting user data from session");

        try {
            // Basic user info
            String userId = sessionManager.getUserId();
            String phoneNumber = sessionManager.getSavedPhoneNumber();
            String fullName = sessionManager.getFullName();

            sosReport.setUserId(userId);
            sosReport.setUserPhoneNumber(phoneNumber);
            sosReport.setUserFullName(fullName);

            // Additional user info
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("firstName", sessionManager.getFirstName());
            userInfo.put("lastName", sessionManager.getLastName());
            userInfo.put("gender", sessionManager.getGender());
            userInfo.put("state", sessionManager.getState());

            if (sessionManager.getDateOfBirth() != null) {
                userInfo.put("dateOfBirth", sessionManager.getDateOfBirth());
            }

            // Set user info in report
            sosReport.setUserInfo(userInfo);

            // Proceed to collect emergency contacts
            collectEmergencyContacts();
        } catch (Exception e) {
            Log.e(TAG, "Error collecting user data", e);
            // Continue to next step even if this fails
            collectEmergencyContacts();
        }
    }

    /**
     * Step 2: Collect emergency contacts
     */
    private void collectEmergencyContacts() {
        Log.d(TAG, "Collecting emergency contact data");

        contactManager.getPrimaryEmergencyContact(new EmergencyContactManager.EmergencyContactListener() {
            @Override
            public void onContactRetrieved(EmergencyContact contact) {
                sosReport.addEmergencyContact(contact);
                collectLocationData();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error retrieving emergency contact", e);
                // Continue even if emergency contact retrieval fails
                collectLocationData();
            }
        });
    }

    /**
     * Step 3: Collect precise location data
     */
    private void collectLocationData() {
        Log.d(TAG, "Collecting location data");

        // Get emergency-specific location (high accuracy)
        final CountDownLatch locationLatch = new CountDownLatch(1);

        locationManager.shareLocationDuringEmergency(new LocationManager.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(Location location) {
                currentLocation = location;

                // Use this location for the SOS report
                GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                sosReport.setLocation(geoPoint);
                sosReport.setLocationAccuracy(location.getAccuracy());

                // Geocode to get address information
                geocodeLocation(location);

                locationLatch.countDown();
            }

            @Override
            public void onLocationError(String message) {
                Log.e(TAG, "Location error: " + message);

                // Try to use last known location
                Location lastKnown = locationManager.getLastKnownLocation();
                if (lastKnown != null) {
                    currentLocation = lastKnown;

                    // Use this location for the SOS report
                    GeoPoint geoPoint = new GeoPoint(lastKnown.getLatitude(), lastKnown.getLongitude());
                    sosReport.setLocation(geoPoint);
                    sosReport.setLocationAccuracy(lastKnown.getAccuracy());

                    // Geocode to get address information
                    geocodeLocation(lastKnown);
                }

                locationLatch.countDown();
            }
        });

        // Wait for location data with timeout
        try {
            locationLatch.await(LOCATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Location collection interrupted", e);
        }

        // Proceed to collect device info
        collectDeviceInfo();
    }

    /**
     * Step 4: Geocode the location to get address, city, state
     */
    private void geocodeLocation(Location location) {
        if (location == null) {
            return;
        }

        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(), 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                currentAddress = address;

                // Extract address components
                String addressLine = address.getMaxAddressLineIndex() > 0 ?
                        address.getAddressLine(0) : "";
                String city = address.getLocality();
                String state = address.getAdminArea();
                String postalCode = address.getPostalCode();

                // Add to SOS report
                sosReport.setAddress(addressLine);
                sosReport.setCity(city);
                sosReport.setState(state);
                sosReport.setZipCode(postalCode);

                Log.d(TAG, "Address: " + addressLine + ", " + city + ", " + state);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error geocoding location", e);
        }
    }

    /**
     * Step 5: Collect device information
     */
    private void collectDeviceInfo() {
        Log.d(TAG, "Collecting device information");

        try {
            // Get comprehensive device info
            Map<String, Object> deviceInfo = deviceInfoHelper.getAllDeviceInfo();
            sosReport.setDeviceInfo(deviceInfo);
        } catch (Exception e) {
            Log.e(TAG, "Error collecting device info", e);
        }

        // If online and we have location, find nearby emergency services
        if (isOnline && currentLocation != null) {
            findNearbyEmergencyServices();
        } else {
            finalizeSOSReport();
        }
    }

    /**
     * Step 6: Find nearby emergency services
     */
    private void findNearbyEmergencyServices() {
        Log.d(TAG, "Finding nearby emergency services");

        // Skip if Places API client not available
        if (placesClient == null || currentLocation == null) {
            Log.w(TAG, "Places client or location not available, skipping emergency services search");
            finalizeSOSReport();
            return;
        }

        final CountDownLatch servicesLatch = new CountDownLatch(1);
        final List<EmergencyService> foundServices = new ArrayList<>();

        // Calculate search bounds (approximately SEARCH_RADIUS meters)
        double latRadiusDegrees = SEARCH_RADIUS / 111000.0; // approximate degrees per meter
        double lngRadiusDegrees = latRadiusDegrees /
                Math.cos(Math.toRadians(currentLocation.getLatitude()));

        LatLng currentLatLng = new LatLng(
                currentLocation.getLatitude(),
                currentLocation.getLongitude());

        RectangularBounds bounds = RectangularBounds.newInstance(
                new LatLng(
                        currentLocation.getLatitude() - latRadiusDegrees,
                        currentLocation.getLongitude() - lngRadiusDegrees),
                new LatLng(
                        currentLocation.getLatitude() + latRadiusDegrees,
                        currentLocation.getLongitude() + lngRadiusDegrees));

        // Determine what type to search for based on emergency type
        String placeType;
        switch (emergencyType) {
            case "POLICE":
                placeType = "police";
                break;
            case "FIRE":
                placeType = "fire_station";
                break;
            case "MEDICAL":
                placeType = "hospital";
                break;
            default:
                placeType = "police"; // Default to police
        }

        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setLocationBias(bounds)
                .setOrigin(currentLatLng)
                .setQuery(placeType)
                .build();

        // Execute the request
        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener(response -> {
                    Log.d(TAG, "Found " + response.getAutocompletePredictions().size() + " predictions");

                    int count = 0;
                    // Process each prediction up to the maximum
                    for (int i = 0; i < response.getAutocompletePredictions().size() &&
                            count < MAX_SERVICES_PER_TYPE; i++) {

                        String placeId = response.getAutocompletePredictions().get(i).getPlaceId();

                        // Request more details about the place
                        List<Place.Field> placeFields = Arrays.asList(
                                Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS,
                                Place.Field.LAT_LNG, Place.Field.PHONE_NUMBER);

                        FetchPlaceRequest fetchRequest = FetchPlaceRequest.builder(
                                placeId, placeFields).build();

                        // Error: The variable i is changing in the loop and is used in lambda
                        final int index = i; // Make i effectively final for lambda
                        placesClient.fetchPlace(fetchRequest)
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error fetching place details", e);

                                    // Now use index instead of i
                                    if (index == response.getAutocompletePredictions().size() - 1) {
                                        servicesLatch.countDown();
                                    }
                                });

                        count++;
                    }

                    // If no predictions were found, release the latch
                    if (response.getAutocompletePredictions().isEmpty()) {
                        servicesLatch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error finding emergency services: " + e.getMessage(), e);
                    servicesLatch.countDown();
                });

        // Wait for nearby services with timeout
        try {
            servicesLatch.await(NEARBY_SERVICES_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Emergency services search interrupted", e);
        }

        // Add found services to the report
        for (EmergencyService service : foundServices) {
            sosReport.addNearbyService(service);
        }

        // Fallback: If no services found, create a default one with toll-free number
        if (foundServices.isEmpty()) {
            EmergencyService defaultService = new EmergencyService();
            defaultService.setName("Emergency Services");
            defaultService.setType(emergencyType);

            // Get toll-free number from emergency contact manager
            String tollFreeNumber = contactManager.getEmergencyServiceNumber(
                    sosReport.getState(), emergencyType);
            defaultService.setTollFreeNumber(tollFreeNumber);

            sosReport.addNearbyService(defaultService);
        }

        // Finalize SOS report
        finalizeSOSReport();
    }

    /**
     * Final step: Complete the SOS report
     */
    private void finalizeSOSReport() {
        Log.d(TAG, "Finalizing SOS report");

        // Update timestamp
        sosReport.setTimestamp(new Date());

        // Reset state
        isCollectingData = false;

        // Notify listener
        if (dataListener != null) {
            dataListener.onSOSDataCollected(sosReport);
        }
    }

    /**
     * Check network connectivity
     */
    private void checkNetworkConnectivity() {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm != null) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
            isOnline = capabilities != null &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        }
    }

    /**
     * Listener for SOS data collection completion
     */
    public interface SOSDataListener {
        void onSOSDataCollected(SOSReport sosReport);
    }
}