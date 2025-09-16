package com.example.petpalfinder.ui.search;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.petpalfinder.R;
import com.example.petpalfinder.data.FilterParams;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import java.util.Map;
import android.location.LocationListener;
import android.os.Looper;

public class PetSearchFragment extends Fragment implements FilterBottomSheetFragment.Listener {

    private PetSearchViewModel vm;
    private AnimalAdapter adapter;
    private RecyclerView list;
    private SharedPreferences prefs;
    private ActivityResultLauncher<String[]> locationPermsLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_pet_search, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        vm = new ViewModelProvider(requireActivity()).get(PetSearchViewModel.class);
        prefs = requireContext().getSharedPreferences("location_prefs", Context.MODE_PRIVATE);

        // --- View Initialization ---
        list = v.findViewById(R.id.list);
        ProgressBar pb = v.findViewById(R.id.progress);
        MaterialToolbar toolbar = v.findViewById(R.id.toolbar);
        View locationBar = v.findViewById(R.id.location_bar);
        TextInputEditText locationEditText = locationBar.findViewById(R.id.location_edit_text);
        ImageButton myLocationButton = locationBar.findViewById(R.id.my_location_button);

        // --- LOCATION PERMISSIONS LAUNCHER ---
        locationPermsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    if (fineGranted || coarseGranted) {
                        useCurrentDeviceLocation();
                    } else {
                        Toast.makeText(getContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
                    }
                });

        // --- ADAPTER AND RECYCLERVIEW SETUP ---
        adapter = new AnimalAdapter(a -> {
            PetSearchFragmentDirections.ActionPetSearchFragmentToPetDetailFragment dir =
                    PetSearchFragmentDirections.actionPetSearchFragmentToPetDetailFragment(a.id);
            NavHostFragment.findNavController(PetSearchFragment.this).navigate(dir);
        });
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        // --- OBSERVERS ---
        vm.loading().observe(getViewLifecycleOwner(),
                isLoading -> pb.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE));

        vm.error().observe(getViewLifecycleOwner(), err -> {
            if (!TextUtils.isEmpty(err)) {
                Toast.makeText(getContext(), err, Toast.LENGTH_LONG).show();
            }
        });

        vm.results().observe(getViewLifecycleOwner(), adapter::setItems);

        vm.getFormattedLocation().observe(getViewLifecycleOwner(), formatted -> {
            if (locationEditText != null) {
                locationEditText.setText(formatted);
            }
            prefs.edit()
                    .putString("last_query", vm.lastLocation)
                    .putString("last_formatted", formatted)
                    .apply();
        });

        // --- UI LISTENERS ---
        myLocationButton.setOnClickListener(view -> checkPermissionsAndUseLocation());

        locationEditText.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = textView.getText().toString().trim();
                if (!query.isEmpty()) {
                    vm.searchAtLocation(query);
                    // Hide keyboard
                    InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                    }
                    textView.clearFocus();
                }
                return true;
            }
            return false;
        });

        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_toggle_map) {
                NavHostFragment.findNavController(PetSearchFragment.this).navigate(R.id.action_petSearchFragment_to_mapFragment);
                return true;
            } else if (id == R.id.action_filters) {
                FilterParams cur = vm.getFilters().getValue();
                if (cur == null) {
                    // Use filter prefs, not location prefs
                    SharedPreferences filterPrefs = requireContext().getSharedPreferences("filters", Context.MODE_PRIVATE);
                    cur = FilterParams.fromPrefs(filterPrefs.getAll(), null);
                }
                FilterBottomSheetFragment.newInstance(cur).show(getChildFragmentManager(), "filters");
                return true;
            }
            return false;
        });
        toolbar.setNavigationOnClickListener(click -> {
            if (NavHostFragment.findNavController(this).getPreviousBackStackEntry() != null) {
                NavHostFragment.findNavController(this).navigateUp();
            } else {
                // No back stack, perhaps close activity
            }
        });

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int visible = lm.getChildCount();
                int total = lm.getItemCount();
                int first = lm.findFirstVisibleItemPosition();
                if (first + visible >= (int) (0.8 * total)) {
                    vm.nextPage();
                }
            }
        });

        // Now that all observers and adapters are set, it's safe to load data.
        if (vm.results().getValue() == null || vm.results().getValue().isEmpty()) {
            String lastQuery = prefs.getString("last_query", "Bradford West Gwillimbury, ON");
            if(locationEditText != null) {
                locationEditText.setText(prefs.getString("last_formatted", lastQuery));
            }
            vm.searchAtLocation(lastQuery);
        }
    }

    @Override
    public void onFiltersApplied(FilterParams params) {
        vm.applyFilters(params);
        // Save filters to the correct prefs file
        SharedPreferences filterPrefs = requireContext().getSharedPreferences("filters", Context.MODE_PRIVATE);
        SharedPreferences.Editor e = filterPrefs.edit();
        for (Map.Entry<String, String> en : params.toPrefs().entrySet()) {
            e.putString(en.getKey(), en.getValue());
        }
        e.apply();
        if (list != null) list.scrollToPosition(0);
    }

    // --- Location Helper Methods ---

    private void checkPermissionsAndUseLocation() {
        if (getContext() == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            useCurrentDeviceLocation();
        } else {
            locationPermsLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void useCurrentDeviceLocation() {
        if (getContext() == null) return;
        try {
            android.location.LocationManager lm = (android.location.LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) throw new Exception("LocationManager not found");

            // Define the listener that will receive the single location update
            final LocationListener locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location loc) {
                    // We got the location! Now update the ViewModel
                    String latLngQuery = loc.getLatitude() + "," + loc.getLongitude();
                    vm.searchAtLocation(latLngQuery);

                    // This callback can come from a different thread, ensure UI work is on the main thread
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "Location found!", Toast.LENGTH_SHORT).show()
                        );
                    }
                }

                // Add other required overrides, even if empty
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };

            // Check which providers are enabled and request a single update
            boolean isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            Toast.makeText(getContext(), "Getting location...", Toast.LENGTH_SHORT).show();
            if (isNetworkEnabled) {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, Looper.getMainLooper());
            } else if (isGpsEnabled) {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, Looper.getMainLooper());
            } else {
                // No providers are enabled in settings
                Toast.makeText(getContext(), "Could not retrieve location. Please ensure GPS or Network location is enabled.", Toast.LENGTH_LONG).show();
            }

        } catch (SecurityException e) {
            Toast.makeText(getContext(), "Location permission is required to use this feature.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Failed to get location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}