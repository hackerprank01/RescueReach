package com.rescuereach.citizen;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.rescuereach.R;
import com.rescuereach.data.model.User;
import com.rescuereach.service.auth.AuthService;
import com.rescuereach.service.auth.AuthServiceProvider;
import com.rescuereach.service.auth.UserSessionManager;

public class CitizenMainActivity extends AppCompatActivity {
    private static final String TAG = "CitizenMainActivity";

    private BottomNavigationView bottomNavigationView;
    private AuthService authService;
    private UserSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_citizen_main);

        // Initialize services
        authService = AuthServiceProvider.getInstance().getAuthService();
        sessionManager = UserSessionManager.getInstance(this);

        // Initialize UI components
        setupBottomNavigation();

        // Load user profile
        loadUserProfile();
    }

    private void setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            Fragment selectedFragment = null;

            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_reports) {
                selectedFragment = new ReportsFragment();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }

            return false;
        });

        // Set default fragment
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .commit();
    }

    private void loadUserProfile() {
        sessionManager.loadCurrentUser(new UserSessionManager.OnUserLoadedListener() {
            @Override
            public void onUserLoaded(User user) {
                // Update UI with user information
                if (user != null) {
                    Log.d(TAG, "User profile loaded: " + user.getFullName());
                    // Update UI elements if needed
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error loading user profile", e);
                Toast.makeText(CitizenMainActivity.this,
                        "Failed to load profile: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_logout) {
            logout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void logout() {
        authService.signOut(new AuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                // Clear user session
                sessionManager.clearSession();

                // Navigate to login screen
                Intent intent = new Intent(CitizenMainActivity.this, PhoneAuthActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Logout failed", e);
                Toast.makeText(CitizenMainActivity.this,
                        "Logout failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}