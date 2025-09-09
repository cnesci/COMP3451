package com.example.petpalfinder.ui.map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.petpalfinder.R;
import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.ui.search.PetSearchViewModel;
import com.mapbox.bindgen.Value;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapFragment extends Fragment {

    private static final String TAG = "MapFragment";
    private static final String SRC_ID = "pet_points";
    private static final String LAYER_ID = "pet_points_layer";

    private MapView mapView;
    private ImageButton btnMyLocation, btnListView;

    private PetSearchViewModel sharedVm; // Activity-scoped ViewModel shared with list screen
    private final ExecutorService exec = Executors.newFixedThreadPool(2);

    private final ActivityResultLauncher<String[]> locationPermsLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> centerOnUserIfPermitted()
            );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mapView = view.findViewById(R.id.mapView);
        btnMyLocation = view.findViewById(R.id.btnMyLocation);
        btnListView   = view.findViewById(R.id.btnListView);

        if (btnMyLocation != null) btnMyLocation.setOnClickListener(v -> ensureLocationThenCenter());
        if (btnListView != null) btnListView.setOnClickListener(v -> navigateBackToList());

        // Activity scope so both fragments can share this VM
        sharedVm = new ViewModelProvider(requireActivity()).get(PetSearchViewModel.class);

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS, style -> {
            // Toronto fallback camera
            mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                    .center(Point.fromLngLat(-79.3832, 43.6532))
                    .zoom(10.0)
                    .build());

            // Add source + layer (JSON API — Java-safe)
            String emptySourceJson = "{"
                    + "\"type\":\"geojson\","
                    + "\"data\":{\"type\":\"FeatureCollection\",\"features\":[]}"
                    + "}";
            style.addStyleSource(SRC_ID, Value.fromJson(emptySourceJson).getValue());

            String layerJson = "{"
                    + "\"id\":\"" + LAYER_ID + "\","
                    + "\"type\":\"circle\","
                    + "\"source\":\"" + SRC_ID + "\","
                    + "\"paint\":{"
                    + "  \"circle-radius\":8,"
                    + "  \"circle-color\":\"#3b82f6\","
                    + "  \"circle-stroke-color\":\"#ffffff\","
                    + "  \"circle-stroke-width\":2"
                    + "}"
                    + "}";
            style.addStyleLayer(Value.fromJson(layerJson).getValue(), null);

            // OpenCage smoke test (confirms key flows)
            new Thread(() -> {
                Point test = MapGeocoder.test();
                Log.d(TAG, "OpenCage test: " + (test != null ? test.toString() : "null"));
            }).start();

            // Observe results; if empty, kick off a search (using args if provided)
            sharedVm.results().observe(getViewLifecycleOwner(), animals -> {
                int n = (animals == null) ? 0 : animals.size();
                Log.d(TAG, "results() size = " + n);

                if (n == 0) {
                    // Try to start a search so we have data to plot
                    String type = "dog";
                    String location = "43.6532,-79.3832"; // Toronto fallback

                    Bundle b = getArguments();
                    if (b != null) {
                        String t = b.getString("type", "");
                        String l = b.getString("location", "");
                        if (!isEmpty(t)) type = t;
                        if (!isEmpty(l)) location = l;
                    }
                    Log.d(TAG, "No results yet. Calling firstSearch(" + type + ", " + location + ")");
                    try {
                        sharedVm.firstSearch(type, location);
                    } catch (Exception e) {
                        Log.w(TAG, "firstSearch failed", e);
                    }
                    return; // wait for next emission
                }

                geocodeAndShow(style, animals);
            });
        });
    }

    private void geocodeAndShow(@NonNull Style style, @NonNull List<Animal> animals) {
        exec.submit(() -> {
            List<Feature> feats = new ArrayList<>();

            for (Animal a : animals) {
                String q = buildAddressQuery(a);
                if (isEmpty(q)) continue;
                Point p = MapGeocoder.geocode(q);
                Log.d(TAG, "geocode: \"" + q + "\" -> " + (p != null ? p.toString() : "null"));
                if (p != null) {
                    Feature f = Feature.fromGeometry(p);
                    try { f.addNumberProperty("animalId", a.id); } catch (Exception ignored) {}
                    feats.add(f);
                }
            }

            FeatureCollection fc = FeatureCollection.fromFeatures(feats);
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "Mapped " + feats.size() + " pets", Toast.LENGTH_SHORT).show();
                style.setStyleSourceProperty(SRC_ID, "data", Value.fromJson(fc.toJson()).getValue());
            });
        });
    }

    private String buildAddressQuery(Animal a) {
        StringBuilder sb = new StringBuilder();

        if (a != null && a.contact != null && a.contact.address != null) {
            append(sb, a.contact.address.address1);
            append(sb, a.contact.address.address2);
            append(sb, a.contact.address.city);
            append(sb, a.contact.address.state);

            if (!isEmpty(a.contact.address.postcode)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(a.contact.address.postcode.trim());
            }
            if (!isEmpty(a.contact.address.country)) {
                sb.append(", ").append(a.contact.address.country.trim());
            }
        }

        if (sb.length() == 0 && a != null && a.contact != null && a.contact.address != null) {
            String city = a.contact.address.city;
            String state = a.contact.address.state;
            if (!isEmpty(city) || !isEmpty(state)) {
                if (!isEmpty(city)) sb.append(city.trim());
                if (!isEmpty(state)) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(state.trim());
                }
                sb.append(", Canada");
            }
        }

        if (sb.length() == 0) return "Toronto, ON, Canada";
        return sb.toString();
    }

    private void navigateBackToList() {
        requireActivity().onBackPressed();
    }

    private void ensureLocationThenCenter() {
        boolean coarse = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean fine = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (coarse || fine) {
            centerOnUserIfPermitted();
        } else {
            locationPermsLauncher.launch(new String[] {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        }
    }

    private void centerOnUserIfPermitted() {
        try {
            android.location.LocationManager lm =
                    (android.location.LocationManager) requireContext()
                            .getSystemService(android.content.Context.LOCATION_SERVICE);
            Location loc = null;
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                if (loc == null) {
                    loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                }
            } else if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
            }

            if (loc != null) {
                mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                        .center(Point.fromLngLat(loc.getLongitude(), loc.getLatitude()))
                        .zoom(12.0)
                        .build());
            }
        } catch (Exception ignored) { }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static void append(StringBuilder sb, String s) {
        if (!isEmpty(s)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.trim());
        }
    }

    @Override public void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
    @Override public void onStop()  { super.onStop();  if (mapView != null) mapView.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
    @Override public void onDestroyView() {
        super.onDestroyView();
        if (mapView != null) {
            mapView.onDestroy();
            mapView = null;
        }
        exec.shutdownNow();
    }
}
