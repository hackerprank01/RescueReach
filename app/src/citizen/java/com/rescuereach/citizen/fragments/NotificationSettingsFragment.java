package com.rescuereach.citizen.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.onesignal.OneSignal;
import com.rescuereach.RescueReachApplication;
import com.rescuereach.databinding.FragmentNotificationSettingsBinding;
import com.rescuereach.service.notification.OneSignalManager;

import org.json.JSONException;
import org.json.JSONObject;

public class NotificationSettingsFragment extends Fragment {

    private FragmentNotificationSettingsBinding binding;
    private OneSignalManager oneSignalManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNotificationSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get OneSignal Manager
        oneSignalManager = ((RescueReachApplication) requireActivity().getApplication())
                .getOneSignalManager();

        // Set up UI
        setupUI();
        loadCurrentSettings();
    }

    private void setupUI() {
        // Set up main notification toggle
        binding.switchEnableNotifications.setOnCheckedChangeListener(
                (buttonView, isChecked) -> updateNotificationPermission(isChecked));

        // Set up topic subscriptions
        binding.switchEmergencyAlerts.setOnCheckedChangeListener(
                (buttonView, isChecked) -> toggleTopic("emergency_alerts", isChecked));

        binding.switchCommunityAlerts.setOnCheckedChangeListener(
                (buttonView, isChecked) -> toggleTopic("community_alerts", isChecked));

        binding.switchSafetyTips.setOnCheckedChangeListener(
                (buttonView, isChecked) -> toggleTopic("safety_tips", isChecked));
    }

    private void loadCurrentSettings() {
        // Get current permission status
        OneSignal.getDeviceState().areNotificationsEnabled();
        boolean notificationsEnabled = OneSignal.getDeviceState().areNotificationsEnabled();
        binding.switchEnableNotifications.setChecked(notificationsEnabled);

        // Get current topic subscriptions from OneSignal
        OneSignal.getTags((tags) -> {
            try {
                if (tags != null) {
                    boolean emergencyAlerts = tags.optBoolean("emergency_alerts", true);
                    boolean communityAlerts = tags.optBoolean("community_alerts", true);
                    boolean safetyTips = tags.optBoolean("safety_tips", true);

                    requireActivity().runOnUiThread(() -> {
                        binding.switchEmergencyAlerts.setChecked(emergencyAlerts);
                        binding.switchCommunityAlerts.setChecked(communityAlerts);
                        binding.switchSafetyTips.setChecked(safetyTips);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateNotificationPermission(boolean enabled) {
        if (enabled) {
            // Request notification permission
            OneSignal.promptForPushNotifications();
        } else {
            // Show message that they need to disable in system settings
            Snackbar.make(binding.getRoot(),
                    "Please disable notifications in your device settings",
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void toggleTopic(String topic, boolean subscribe) {
        if (subscribe) {
            oneSignalManager.subscribeToTopic(topic);
        } else {
            oneSignalManager.unsubscribeFromTopic(topic);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}