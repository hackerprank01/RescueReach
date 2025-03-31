package com.rescuereach.citizen;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.rescuereach.R;
import com.rescuereach.citizen.fragments.EmergencyContactsFragment;
import com.rescuereach.citizen.fragments.HomeFragment;
import com.rescuereach.citizen.fragments.NearbyFacilitiesFragment;
import com.rescuereach.citizen.fragments.ProfileFragment;
import com.rescuereach.citizen.fragments.ReportsFragment;
import com.rescuereach.data.model.User;
import com.rescuereach.service.auth.AuthService;
import com.rescuereach.service.auth.AuthServiceProvider;
import com.rescuereach.service.auth.UserSessionManager;

public class CitizenMainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private ExtendedFloatingActionButton fabReportEmergency;

    private UserSessionManager sessionManager;
    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_citizen_main);

        // Initialize services
        sessionManager = UserSessionManager.getInstance(this);
        authService = AuthServiceProvider.getInstance().getAuthService();

        // Initialize UI components
        setupToolbar();
        setupDrawer();
        setupNavigation();
        setupFAB();

        // Load user data
        loadUserData();

        // Set default fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
            navigationView.setCheckedItem(R.id.nav_home);
        }
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
    }

    private void setupNavigation() {
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    private void setupFAB() {
        fabReportEmergency = findViewById(R.id.fab_report_emergency);
        fabReportEmergency.setOnClickListener(v -> {
            // Start emergency reporting activity
            Intent intent = new Intent(CitizenMainActivity.this, ReportEmergencyActivity.class);
            startActivity(intent);
        });
    }

    private void loadUserData() {
        View headerView = navigationView.getHeaderView(0);
        TextView nameTextView = headerView.findViewById(R.id.nav_header_name);
        TextView phoneTextView = headerView.findViewById(R.id.nav_header_phone);

        sessionManager.loadCurrentUser(new UserSessionManager.OnUserLoadedListener() {
            @Override
            public void onUserLoaded(User user) {
                nameTextView.setText(user.getFullName());
                phoneTextView.setText(user.getPhoneNumber());
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(CitizenMainActivity.this,
                        "Failed to load user data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks
        Fragment selectedFragment = null;

        int itemId = item.getItemId();
        if (itemId == R.id.nav_home) {
            selectedFragment = new HomeFragment();
            setTitle("Home");
        } else if (itemId == R.id.nav_reports) {
            selectedFragment = new ReportsFragment();
            setTitle("My Reports");
        } else if (itemId == R.id.nav_emergency_contacts) {
            selectedFragment = new EmergencyContactsFragment();
            setTitle("Emergency Contacts");
        } else if (itemId == R.id.nav_nearby_facilities) {
            selectedFragment = new NearbyFacilitiesFragment();
            setTitle("Nearby Facilities");
        } else if (itemId == R.id.nav_profile) {
            selectedFragment = new ProfileFragment();
            setTitle("Profile");
        } else if (itemId == R.id.nav_settings) {
            // Launch settings activity
            // Intent intent = new Intent(this, SettingsActivity.class);
            // startActivity(intent);
        } else if (itemId == R.id.nav_logout) {
            logoutUser();
        }

        if (selectedFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void logoutUser() {
        authService.signOut(new AuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                // Clear user session
                sessionManager.clearSession();

                // Redirect to login
                Intent intent = new Intent(CitizenMainActivity.this, PhoneAuthActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(CitizenMainActivity.this,
                        "Failed to logout. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Close drawer on back press if open
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}