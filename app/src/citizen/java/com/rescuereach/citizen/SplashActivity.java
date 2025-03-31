package com.rescuereach.citizen;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.rescuereach.R;
import com.rescuereach.service.auth.AuthService;
import com.rescuereach.service.auth.AuthServiceProvider;
import com.rescuereach.service.auth.UserSessionManager;

public class SplashActivity extends AppCompatActivity {
    private static final int SPLASH_DURATION = 2000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Use Handler to delay the transition
        new Handler(Looper.getMainLooper()).postDelayed(this::navigateToNextScreen, SPLASH_DURATION);
    }

    // Add this code to the existing navigateToNextScreen method
    private void navigateToNextScreen() {
        AuthService authService = AuthServiceProvider.getInstance().getAuthService();

        if (authService.isLoggedIn()) {
            // User is logged in, check if profile is complete
            UserSessionManager sessionManager = UserSessionManager.getInstance(this);
            if (sessionManager.isProfileComplete()) {
                // Profile is complete, go to main screen
                startActivity(new Intent(this, CitizenMainActivity.class));
            } else {
                // Profile is incomplete, go to profile completion
                startActivity(new Intent(this, ProfileCompletionActivity.class));
            }
        } else {
            // User is not logged in, go to auth screen
            startActivity(new Intent(this, PhoneAuthActivity.class));
        }
        finish();
    }
}