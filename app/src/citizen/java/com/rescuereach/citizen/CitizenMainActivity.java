package com.rescuereach.citizen;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
    private ExtendedFloatingActionButton fabEmergency;

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
        setupToolbar();
        setupNavigationDrawer();
        setupFab();

        // Set default fragment if this is first creation
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

    private void setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Setup drawer toggle
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Setup navigation item selection listener
        navigationView.setNavigationItemSelectedListener(this);
    }

    private void setupFab() {
        fabEmergency = findViewById(R.id.fab_report_emergency);
        fabEmergency.setOnClickListener(v -> {
            // TODO: Open emergency reporting activity
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
        } else if (itemId == R.id.nav_reports) {
            selectedFragment = new ReportsFragment();
            title = "My Reports";
        } else if (itemId == R.id.nav_profile) {
            selectedFragment = new ProfileFragment();
            title = "Profile";
        } else if (itemId == R.id.nav_logout) {
            showLogoutConfirmationDialog();
            // Close drawer and return since we're showing a dialog
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        if (selectedFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();

            if (!title.isEmpty()) {
                setTitle(title);
            }
        }

        // Close drawer after handling click
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
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
        });
        builder.create().show();
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