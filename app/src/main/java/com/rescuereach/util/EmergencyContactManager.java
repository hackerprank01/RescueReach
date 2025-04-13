//package com.rescuereach.util;
//
//import android.content.Context;
//import android.util.Log;
//
//import com.google.firebase.firestore.FirebaseFirestore;
//import com.rescuereach.data.model.EmergencyContact;
//import com.rescuereach.data.repository.OnCompleteListener;
//import com.rescuereach.service.auth.UserSessionManager;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
///**
// * Manages emergency contacts and emergency service numbers
// * Provides functionality to retrieve, validate and communicate with emergency contacts
// */
//public class EmergencyContactManager {
//    private static final String TAG = "EmergencyContactManager";
//
//    private static EmergencyContactManager instance;
//    private final Context context;
//    private final UserSessionManager sessionManager;
//    private final FirebaseFirestore firestore;
//
//    // Emergency service toll-free numbers by state
//    private final Map<String, Map<String, String>> emergencyServiceNumbers;
//
//    // Cache of emergency contacts to avoid repeated Firebase calls
//    private EmergencyContact primaryEmergencyContact;
//    private long lastContactRefreshTime = 0;
//    private static final long REFRESH_INTERVAL = 5 * 60 * 1000; // 5 minutes
//
//    private EmergencyContactManager(Context context) {
//        this.context = context.getApplicationContext();
//        this.sessionManager = UserSessionManager.getInstance(context);
//        this.firestore = FirebaseFirestore.getInstance();
//
//        // Initialize emergency service numbers for different states
//        this.emergencyServiceNumbers = initEmergencyServiceNumbers();
//    }
//
//    /**
//     * Get singleton instance of EmergencyContactManager
//     */
//    public static synchronized EmergencyContactManager getInstance(Context context) {
//        if (instance == null) {
//            instance = new EmergencyContactManager(context);
//        }
//        return instance;
//    }
//
//    /**
//     * Get the primary emergency contact for the current user
//     * @param listener Callback with the emergency contact
//     */
//    public void getPrimaryEmergencyContact(EmergencyContactListener listener) {
//        // Check if we have a cached contact that is still fresh
//        if (primaryEmergencyContact != null &&
//                System.currentTimeMillis() - lastContactRefreshTime < REFRESH_INTERVAL) {
//            listener.onContactRetrieved(primaryEmergencyContact);
//            return;
//        }
//
//        // Try to get from shared preferences first (faster)
//        String phoneNumber = sessionManager.getEmergencyContactPhone();
//        if (phoneNumber != null && !phoneNumber.isEmpty()) {
//            EmergencyContact contact = new EmergencyContact();
//            contact.setPhoneNumber(phoneNumber);
//            contact.setPrimary(true);
//
//            // Cache the contact
//            primaryEmergencyContact = contact;
//            lastContactRefreshTime = System.currentTimeMillis();
//
//            listener.onContactRetrieved(contact);
//            return;
//        }
//
//        // If not in shared preferences, try to get from Firebase
//        String userPhoneNumber = sessionManager.getSavedPhoneNumber();
//        if (userPhoneNumber == null || userPhoneNumber.isEmpty()) {
//            listener.onError(new Exception("User not authenticated"));
//            return;
//        }
//
//        firestore.collection("users")
//                .document(userPhoneNumber)
//                .get()
//                .addOnSuccessListener(documentSnapshot -> {
//                    if (documentSnapshot.exists()) {
//                        String emergencyPhone = documentSnapshot.getString("emergencyContact");
//
//                        if (emergencyPhone != null && !emergencyPhone.isEmpty()) {
//                            EmergencyContact contact = new EmergencyContact();
//                            contact.setPhoneNumber(emergencyPhone);
//                            contact.setPrimary(true);
//
//                            // Cache the contact
//                            primaryEmergencyContact = contact;
//                            lastContactRefreshTime = System.currentTimeMillis();
//
//                            // Also update shared preferences
//                            sessionManager.setEmergencyContactPhone(emergencyPhone);
//
//                            listener.onContactRetrieved(contact);
//                            return;
//                        }
//                    }
//
//                    listener.onError(new Exception("No emergency contact found"));
//                })
//                .addOnFailureListener(e -> {
//                    Log.e(TAG, "Error getting emergency contact", e);
//                    listener.onError(e);
//                });
//    }
//
//    /**
//     * Get emergency service toll-free number based on state and service type
//     * @param state The state name
//     * @param serviceType "POLICE", "FIRE", or "MEDICAL"
//     * @return Toll-free number or default national emergency number
//     */
//    public String getEmergencyServiceNumber(String state, String serviceType) {
//        // Normalize inputs
//        state = state != null ? state.trim() : "";
//        serviceType = serviceType != null ? serviceType.toUpperCase() : "";
//
//        // Default emergency numbers
//        String defaultNumber = "112"; // Universal emergency number in India
//
//        // Individual default numbers
//        if (serviceType.equals("POLICE")) {
//            defaultNumber = "100";
//        } else if (serviceType.equals("FIRE")) {
//            defaultNumber = "100";
//        } else if (serviceType.equals("MEDICAL")) {
//            defaultNumber = "100";
//        }
//
//        // Return state-specific number if available
//        if (emergencyServiceNumbers.containsKey(state)) {
//            Map<String, String> stateNumbers = emergencyServiceNumbers.get(state);
//            if (stateNumbers != null && stateNumbers.containsKey(serviceType)) {
//                return stateNumbers.get(serviceType);
//            }
//        }
//
//        return defaultNumber;
//    }
//
//    /**
//     * Get nearest emergency service toll-free number based on incident type and location
//     * @param serviceType "POLICE", "FIRE", or "MEDICAL"
//     * @return The appropriate toll-free emergency number
//     */
//    public String getNearestEmergencyServiceNumber(String serviceType) {
//        // Get user's state from session
//        String userState = sessionManager.getState();
//
//        // Return state-specific or default number
//        return getEmergencyServiceNumber(userState, serviceType);
//    }
//
//    /**
//     * Verify if a phone number is a valid emergency contact
//     * @param phoneNumber The phone number to validate
//     * @return True if valid, false otherwise
//     */
//    public boolean isValidEmergencyContact(String phoneNumber) {
//        if (phoneNumber == null || phoneNumber.isEmpty()) {
//            return false;
//        }
//
//        // Remove any non-digit characters except the + symbol
//        String cleaned = phoneNumber.replaceAll("[^\\d+]", "");
//
//        // Check if it's the user's own number
//        String userPhone = sessionManager.getSavedPhoneNumber();
//        if (userPhone != null && !userPhone.isEmpty()) {
//            String cleanedUserPhone = userPhone.replaceAll("[^\\d+]", "");
//            if (cleaned.endsWith(cleanedUserPhone.substring(cleanedUserPhone.length() - 10)) ||
//                    cleanedUserPhone.endsWith(cleaned.substring(cleaned.length() - 10))) {
//                return false; // Can't use own number as emergency contact
//            }
//        }
//
//        // Ensure it has at least 10 digits
//        return cleaned.length() >= 10;
//    }
//
//    /**
//     * Initialize the emergency service toll-free numbers for different states
//     */
//    private Map<String, Map<String, String>> initEmergencyServiceNumbers() {
//        Map<String, Map<String, String>> numbers = new HashMap<>();
//
//        // National emergency numbers (these apply to all states in India)
//        Map<String, String> nationalNumbers = new HashMap<>();
//        nationalNumbers.put("POLICE", "100");
//        nationalNumbers.put("FIRE", "100");
//        nationalNumbers.put("MEDICAL", "100");
//        nationalNumbers.put("DISASTER", "100");
//        nationalNumbers.put("WOMEN_HELPLINE", "100");
//        nationalNumbers.put("CHILD_HELPLINE", "100");
//
//        // Apply national numbers to all states
//        String[] states = {
//                "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
//                "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand",
//                "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur",
//                "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab",
//                "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura",
//                "Uttar Pradesh", "Uttarakhand", "West Bengal",
//                "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu",
//                "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry"
//        };
//
//        for (String state : states) {
//            numbers.put(state, new HashMap<>(nationalNumbers));
//        }
//
//        // Add state-specific numbers where they differ
//        // Maharashtra special numbers
//        Map<String, String> maharashtraNumbers = numbers.get("Maharashtra");
//        maharashtraNumbers.put("DISASTER", "100");
//
//        // Delhi special numbers
//        Map<String, String> delhiNumbers = numbers.get("Delhi");
//        delhiNumbers.put("POLICE", "100");
//        delhiNumbers.put("DISASTER", "100");
//
//        return numbers;
//    }
//
//    /**
//     * Listener interface for emergency contact operations
//     */
//    public interface EmergencyContactListener {
//        void onContactRetrieved(EmergencyContact contact);
//        void onError(Exception e);
//    }
//}