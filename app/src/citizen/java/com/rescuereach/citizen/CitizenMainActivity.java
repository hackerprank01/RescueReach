package com.rescuereach.citizen;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.rescuereach.R;
import com.rescuereach.citizen.fragments.HomeFragment;
import com.rescuereach.citizen.fragments.ProfileFragment;
import com.rescuereach.citizen.fragments.ReportsFragment;
import com.rescuereach.service.auth.AuthService;
import com.rescuereach.service.auth.AuthServiceProvider;
import com.rescuereach.service.auth.UserSessionManager;

public class CitizenMainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "CitizenMainActivity";

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private TextView toolbarTitle;
    private ImageButton drawerToggleButton;
    private ExtendedFloatingActionButton fabEmergency;
    private ProgressBar progressIndicator;

    private AuthService authService;
    private UserSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_citizen_main);

        // Initialize services
        authService = AuthServiceProvider.getInstance().getAuthService();
        sessionManager = UserSessionManager.getInstance(this);

        // Ensure profile is marked as complete if user reaches this screen
        if (!sessionManager.isProfileComplete() && sessionManager.getSavedPhoneNumber() != null) {
            sessionManager.markProfileComplete();
        }

        // Initialize UI components
        initializeViews();
        setupToolbar();
        setupNavigationDrawer();
        setupFab();

        // Set default fragment if this is first creation
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment(), "Home");
            navigationView.setCheckedItem(R.id.nav_home);
        }
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbarTitle = findViewById(R.id.toolbar_title);
        drawerToggleButton = findViewById(R.id.drawer_toggle_button);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        fabEmergency = findViewById(R.id.fab_report_emergency);
        progressIndicator = findViewById(R.id.progress_indicator);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);

        // Hide default title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        setTitleColor(Color.BLACK);
    }

    public void setTitleColor(int color) {
        toolbarTitle.setTextColor(color);
    }


    private void setupNavigationDrawer() {
        // Set click listener for custom drawer toggle button
        drawerToggleButton.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        // Setup navigation item selection listener
        navigationView.setNavigationItemSelectedListener(this);
    }

    private void setupFab() {
        fabEmergency.setOnClickListener(v -> {
            Toast.makeText(this, "Report Emergency clicked", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation drawer item clicks
        int itemId = item.getItemId();
        Fragment selectedFragment = null;
        String title = "";

        if (itemId == R.id.nav_home) {
            selectedFragment = new HomeFragment();
            title = "Home";
            setTitleColor(Color.BLACK);
        } else if (itemId == R.id.nav_reports) {
            selectedFragment = new ReportsFragment();
            title = "My Reports";
            setTitleColor(Color.BLACK);
        } else if (itemId == R.id.nav_profile) {
            selectedFragment = new ProfileFragment();
            setTitleColor(Color.BLACK);

        } else if (itemId == R.id.nav_logout) {
            showLogoutConfirmationDialog();
            // Close drawer and return since we're showing a dialog
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        // Load the selected fragment
        if (selectedFragment != null) {
            loadFragment(selectedFragment, title);
        }

        // Close drawer after handling click
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }


    private void loadFragment(Fragment fragment, String title) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();

        if (title != null && !title.isEmpty()) {
            toolbarTitle.setText(title);
        }
    }

    private void showLogoutConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Logout");
        builder.setMessage("Are you sure you want to logout?");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            logout();
        });
        builder.setNegativeButton("No", (dialog, which) -> {
            dialog.dismiss();
            Intent intent = new Intent(CitizenMainActivity.this, CitizenMainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        builder.create().show();
    }

    private void logout() {
        showLoading(true);

        authService.signOut(new AuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                showLoading(false);

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
                showLoading(false);

                Log.e(TAG, "Logout failed", e);
                Toast.makeText(CitizenMainActivity.this,
                        "Logout failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoading(boolean isLoading) {
        if (progressIndicator != null) {
            progressIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        // Close drawer first if it's open
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}