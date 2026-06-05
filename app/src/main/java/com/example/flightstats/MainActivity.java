package com.example.flightstats;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();

            MaterialButton btnMap = findViewById(R.id.btn_nav_map);
            MaterialButton btnFlights = findViewById(R.id.btn_nav_flights);
            MaterialButton btnStats = findViewById(R.id.btn_nav_stats);

            // Configure standard navigation options to mimic BottomNavigationView behavior (singleTop, restoreState, pop to start destination)
            NavOptions navOptions = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), false, true)
                    .build();

            btnMap.setOnClickListener(v -> {
                if (navController.getCurrentDestination() == null || navController.getCurrentDestination().getId() != R.id.nav_map) {
                    navController.navigate(R.id.nav_map, null, navOptions);
                }
            });

            btnFlights.setOnClickListener(v -> {
                if (navController.getCurrentDestination() == null || navController.getCurrentDestination().getId() != R.id.nav_flights) {
                    navController.navigate(R.id.nav_flights, null, navOptions);
                }
            });

            btnStats.setOnClickListener(v -> {
                if (navController.getCurrentDestination() == null || navController.getCurrentDestination().getId() != R.id.nav_stats) {
                    navController.navigate(R.id.nav_stats, null, navOptions);
                }
            });

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int destId = destination.getId();
                btnMap.setChecked(destId == R.id.nav_map);
                btnFlights.setChecked(destId == R.id.nav_flights);
                btnStats.setChecked(destId == R.id.nav_stats);
            });
        }
    }
}