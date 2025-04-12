//package com.rescuereach.responder;
//
//import android.content.Intent;
//import android.graphics.Color;
//import android.os.Bundle;
//import android.util.Log;
//import android.view.MenuItem;
//import android.view.View;
//import android.widget.ImageButton;
//import android.widget.ProgressBar;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AlertDialog;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.appcompat.widget.Toolbar;
//import androidx.core.view.GravityCompat;
//import androidx.drawerlayout.widget.DrawerLayout;
//import androidx.fragment.app.Fragment;
//
//import com.google.android.material.navigation.NavigationView;
//import com.rescuereach.R;
//import com.rescuereach.data.repository.OnCompleteListener;
//import com.rescuereach.responder.fragments.DashboardFragment;
//import com.rescuereach.responder.fragments.IncidentHistoryFragment;
//import com.rescuereach.responder.fragments.IncidentsFragment;
//import com.rescuereach.responder.fragments.ProfileFragment;
//import com.rescuereach.responder.fragments.SettingsFragment;
//import com.rescuereach.service.auth.ResponderSessionManager;
//
//public class ResponderMainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
//    private static final String TAG = "ResponderMainActivity";
//
//    private DrawerLayout drawerLayout;
//    private NavigationView navigationView;
//    private Toolbar toolbar;
//    private TextView toolbarTitle;
//    private ImageButton drawerToggleButton;
//    private ProgressBar progressIndicator;
//
//    private ResponderSessionManager sessionManager;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_responder_main);
//
//        // Initialize services
//        sessionManager = ResponderSessionManager.getInstance(this);
//
//        // Initialize UI components
//        initializeViews();
//        setupToolbar();
//        setupNavigationDrawer();
//
//        // Set default fragment if this is first creation
//        if (savedInstanceState == null) {
//            // Start with dashboard fragment
//            loadFragment(new DashboardFragment(), "Responder Dashboard");
//            navigationView.setCheckedItem(R.id.nav_dashboard);
//        }
//    }
//
//    private void initializeViews() {
//        toolbar = findViewById(R.id.toolbar);
//        toolbarTitle = findViewById(R.id.toolbar_title);
//        drawerToggleButton = findViewById(R.id.drawer_toggle_button);
//        drawerLayout = findViewById(R.id.drawer_layout);
//        navigationView = findViewById(R.id.nav_view);
//        progressIndicator = findViewById(R.id.progress_indicator);
//    }
//
//    private void setupToolbar() {
//        setSupportActionBar(toolbar);
//
//        // Hide default title
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().setDisplayShowTitleEnabled(false);
//        }
//
//        // Set toolbar title color
//        toolbarTitle.setTextColor(Color.WHITE);
//    }
//
//    private void setupNavigationDrawer() {
//        // Set click listener for custom drawer toggle button
//        drawerToggleButton.setOnClickListener(v -> {
//            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
//                drawerLayout.closeDrawer(GravityCompat.START);
//            } else {
//                drawerLayout.openDrawer(GravityCompat.START);
//            }
//        });
//
//        // Setup navigation item selection listener
//        navigationView.setNavigationItemSelectedListener(this);
//    }
//
//    @Override
//    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
//        // Handle navigation drawer item clicks
//        int itemId = item.getItemId();
//        Fragment selectedFragment = null;
//        String title = "";
//
//        if (itemId == R.id.nav_dashboard) {
//            selectedFragment = new DashboardFragment();
//            title = "Responder Dashboard";
//        } else if (itemId == R.id.nav_incidents) {
//            selectedFragment = new IncidentsFragment();
//            title = "Active Incidents";
//        } else if (itemId == R.id.nav_history) {
//            selectedFragment = new IncidentHistoryFragment();
//            title = "Incident History";
//        } else if (itemId == R.id.nav_profile) {
//            selectedFragment = new ProfileFragment();
//            title = "Profile";
//        } else if (itemId == R.id.nav_settings) {
//            selectedFragment = new SettingsFragment();
//            title = "Settings";
//        } else if (itemId == R.id.nav_logout) {
//            showLogoutConfirmationDialog();
//            // Close drawer and return since we're showing a dialog
//            drawerLayout.closeDrawer(GravityCompat.START);
//            return true;
//        }
//
//        // Load the selected fragment
//        if (selectedFragment != null) {
//            loadFragment(selectedFragment, title);
//        }
//
//        // Close drawer after handling click
//        drawerLayout.closeDrawer(GravityCompat.START);
//        return true;
//    }
//
//    private void loadFragment(Fragment fragment, String title) {
//        getSupportFragmentManager().beginTransaction()
//                .replace(R.id.fragment_container, fragment)
//                .commit();
//
//        if (title != null && !title.isEmpty()) {
//            toolbarTitle.setText(title);
//        }
//    }
//
//    private void showLogoutConfirmationDialog() {
//        new AlertDialog.Builder(this)
//                .setTitle("Confirm Logout")
//                .setMessage("Are you sure you want to logout?")
//                .setPositiveButton("Yes", (dialog, which) -> {
//                    logout();
//                })
//                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
//                .create()
//                .show();
//    }
//
//    private void logout() {
//        showLoading(true);
//
//        sessionManager.logout(new OnCompleteListener() {
//            @Override
//            public void onSuccess() {
//                showLoading(false);
//                // Navigate to login screen
//                Intent intent = new Intent(ResponderMainActivity.this, LoginActivity.class);
//                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                startActivity(intent);
//                finish();
//            }
//
//            @Override
//            public void onError(Exception e) {
//                showLoading(false);
//                Log.e(TAG, "Logout failed", e);
//                Toast.makeText(ResponderMainActivity.this,
//                        "Logout failed: " + e.getMessage(),
//                        Toast.LENGTH_SHORT).show();
//            }
//        });
//    }
//
//    private void showLoading(boolean isLoading) {
//        if (progressIndicator != null) {
//            progressIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
//        }
//    }
//
//    @Override
//    public void onBackPressed() {
//        // Close drawer first if it's open
//        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
//            drawerLayout.closeDrawer(GravityCompat.START);
//        } else {
//            super.onBackPressed();
//        }
//    }
//}