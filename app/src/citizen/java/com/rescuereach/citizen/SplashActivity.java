package com.rescuereach.citizen;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.rescuereach.R;
import com.rescuereach.service.auth.AuthService;
import com.rescuereach.service.auth.AuthServiceProvider;
import com.rescuereach.service.auth.UserSessionManager;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private static final int SPLASH_DISPLAY_TIME = 1000; // 2 seconds

    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize services
        authService = AuthServiceProvider.getInstance().getAuthService();

        // Delay for splash screen
        new Handler().postDelayed(this::navigateToNextScreen, SPLASH_DISPLAY_TIME);
    }

    private void navigateToNextScreen() {
        if (authService.isLoggedIn()) {
            // User is logged in, check if profile is complete
            UserSessionManager sessionManager = UserSessionManager.getInstance(this);

            if (sessionManager.isProfileComplete()) {
                // Profile is complete, go to main activity
                startActivity(new Intent(this, CitizenMainActivity.class));
            } else {
                // Profile is not complete, go to profile completion
                startActivity(new Intent(this, ProfileCompletionActivity.class));
            }
        } else {
            // User is not logged in, go to auth screen
            startActivity(new Intent(this, PhoneAuthActivity.class));
        }

        // Close splash activity
        finish();
    }
}