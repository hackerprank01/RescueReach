package com.rescuereach.citizen;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.rescuereach.R;
import com.rescuereach.service.auth.AuthService;
import com.rescuereach.service.auth.AuthServiceProvider;

public class SplashActivity extends AppCompatActivity {
    private static final int SPLASH_DURATION = 2000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Use Handler to delay the transition
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAuthState, SPLASH_DURATION);
    }

    private void checkAuthState() {
        AuthService authService = AuthServiceProvider.getInstance().getAuthService();

        if (authService.isLoggedIn()) {
            // User is logged in, go to main activity
            startActivity(new Intent(this, CitizenMainActivity  .class));
        } else {
            // User is not logged in, go to phone auth activity
            startActivity(new Intent(this, PhoneAuthActivity.class));
        }

        // Close this activity
        finish();
    }
}