package com.example.petpalfinder.ui.map;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.example.petpalfinder.R;
import com.example.petpalfinder.data.FilterParams;
import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.Photo;
import com.example.petpalfinder.ui.search.FilterBottomSheetFragment;
import com.example.petpalfinder.ui.search.PetSearchViewModel;
import com.google.android.material.textfield.TextInputEditText;
import com.mapbox.bindgen.Value;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.EdgeInsets;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.QueriedFeature;
import com.mapbox.maps.RenderedQueryGeometry;
import com.mapbox.maps.RenderedQueryOptions;
import com.mapbox.maps.Style;
// *** FIX 1: Import the GesturesUtils to get the gestures plugin ***
import com.mapbox.maps.plugin.gestures.GesturesUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MapFragment extends Fragment implements FilterBottomSheetFragment.Listener {

    private static final String TAG = "MapFragment";
    private static final String SRC_ID = "pet_points";
    private static final String LAYER_CLUSTERS = "pet_points_clusters";
    private static final String LAYER_UNCLUSTERED = "pet_points_unclustered";

    private MapView mapView;
    private ImageButton btnMyLocation, btnListView, btnFilters;
    private PetSearchViewModel sharedVm;
    private ExecutorService exec;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private boolean cameraFittedOnce = false;
    private SharedPreferences prefs;
    private View infoWindow;
    private ImageView infoWindowImage;
    private TextView infoWindowDetails;
    private Button infoWindowButton;
    private TextView infoWindowName;
    private ActivityResultLauncher<String[]> locationPermsLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        destroyed.set(false);
        exec = Executors.newSingleThreadExecutor();
        locationPermsLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
            Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
            if (fineGranted || coarseGranted) {
                useCurrentDeviceLocation();
            } else {
                Toast.makeText(getContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        });
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);
        mapView = view.findViewById(R.id.mapView);
        btnMyLocation = view.findViewById(R.id.btnMyLocation);
        btnListView = view.findViewById(R.id.btnListView);
        btnFilters = view.findViewById(R.id.btnFilters);
        infoWindow = view.findViewById(R.id.info_window);
        infoWindowImage = infoWindow.findViewById(R.id.info_window_image);
        infoWindowDetails = infoWindow.findViewById(R.id.info_window_details);
        infoWindowButton = infoWindow.findViewById(R.id.info_window_button);
        infoWindowName = infoWindow.findViewById(R.id.info_window_name);

        View locationBar = view.findViewById(R.id.location_bar);
        TextInputEditText locationEditText = locationBar.findViewById(R.id.location_edit_text);
        ImageButton myLocationButton = locationBar.findViewById(R.id.my_location_button);

        prefs = requireContext().getSharedPreferences("location_prefs", Context.MODE_PRIVATE);
        sharedVm = new ViewModelProvider(requireActivity()).get(PetSearchViewModel.class);

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavHostFragment.findNavController(MapFragment.this).navigateUp();
            }
        });

        if (sharedVm.results().getValue() == null || sharedVm.results().getValue().isEmpty()) {
            String lastQuery = prefs.getString("last_query", "Bradford West Gwillimbury, ON");
            sharedVm.searchAtLocation(lastQuery);
        }

        sharedVm.getFormattedLocation().observe(getViewLifecycleOwner(), formatted -> {
            locationEditText.setText(formatted);
            prefs.edit()
                    .putString("last_query", sharedVm.lastLocation)
                    .putString("last_formatted", formatted)
                    .apply();
        });

        myLocationButton.setOnClickListener(v -> checkPermissionsAndUseLocation());
        locationEditText.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = textView.getText().toString().trim();
                if (!query.isEmpty()) {
                    sharedVm.searchAtLocation(query);
                    InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                    textView.clearFocus();
                }
                return true;
            }
            return false;
        });

        if (btnMyLocation != null) btnMyLocation.setOnClickListener(v -> checkPermissionsAndUseLocation());
        if (btnListView != null) btnListView.setOnClickListener(v -> NavHostFragment.findNavController(MapFragment.this).navigateUp());
        if (btnFilters != null) btnFilters.setOnClickListener(v -> {
            FilterParams cur = sharedVm.getFilters().getValue();
            if (cur == null) {
                cur = FilterParams.fromPrefs(prefs.getAll(), null);
            }
            FilterBottomSheetFragment.newInstance(cur).show(getChildFragmentManager(), "filters");
        });

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS, style -> {
            setupMapStyle(style);
            sharedVm.results().observe(getViewLifecycleOwner(), animals -> {
                if (animals == null) return;
                if (!isAdded() || destroyed.get()) return;
                if (sharedVm.getFormattedLocation().getValue() != null && !cameraFittedOnce) {
                    centerMapOnCurrentLocation();
                }
                geocodeAndShow(style, animals);
            });
            sharedVm.getFilters().observe(getViewLifecycleOwner(), f -> cameraFittedOnce = false);
            attachTapHandlers();
        });
    }

    private void setupMapStyle(@NonNull Style style) {
        mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                .center(Point.fromLngLat(-79.3832, 43.6532)) // Default
                .zoom(10.0)
                .build());

        String srcJson = "{\"type\":\"geojson\",\"data\":{\"type\":\"FeatureCollection\",\"features\":[]},\"cluster\":true,\"clusterMaxZoom\":14,\"clusterRadius\":50}";
        if (!style.styleSourceExists(SRC_ID)) {
            style.addStyleSource(SRC_ID, Value.fromJson(srcJson).getValue());
        }

        String clustersLayerJson = "{\"id\":\"" + LAYER_CLUSTERS + "\",\"type\":\"circle\",\"source\":\"" + SRC_ID + "\",\"filter\":[\"has\",\"point_count\"],\"paint\":{\"circle-color\":[\"step\",[\"get\",\"point_count\"],\"#93c5fd\",10,\"#60a5fa\",25,\"#3b82f6\"],\"circle-radius\":[\"step\",[\"get\",\"point_count\"],14,10,18,25,22]}}";
        if (!style.styleLayerExists(LAYER_CLUSTERS)) {
            style.addStyleLayer(Value.fromJson(clustersLayerJson).getValue(), null);
        }

        String countLayerJson = "{\"id\":\"" + "pet_points_cluster_count" + "\",\"type\":\"symbol\",\"source\":\"" + SRC_ID + "\",\"filter\":[\"has\",\"point_count\"],\"layout\":{\"text-field\":\"{point_count_abbreviated}\",\"text-size\":12},\"paint\":{\"text-color\":\"#ffffff\"}}";
        if (!style.styleLayerExists("pet_points_cluster_count")) {
            style.addStyleLayer(Value.fromJson(countLayerJson).getValue(), null);
        }

        String unclusteredLayerJson = "{\"id\":\"" + LAYER_UNCLUSTERED + "\",\"type\":\"circle\",\"source\":\"" + SRC_ID + "\",\"filter\":[\"!\",[\"has\",\"point_count\"]],\"paint\":{\"circle-radius\":8,\"circle-color\":\"#3b82f6\",\"circle-stroke-color\":\"#ffffff\",\"circle-stroke-width\":2}}";
        if (!style.styleLayerExists(LAYER_UNCLUSTERED)) {
            style.addStyleLayer(Value.fromJson(unclusteredLayerJson).getValue(), null);
        }
    }

    // *** FIX 1: This method now uses the GesturesPlugin to add the listener ***
    private void attachTapHandlers() {
        if (mapView == null) return;

        GesturesUtils.getGestures(mapView).addOnMapClickListener(point -> {
            if (infoWindow != null && infoWindow.getVisibility() == View.VISIBLE) {
                infoWindow.setVisibility(View.GONE);
            }

            RenderedQueryOptions queryOptions = new RenderedQueryOptions(
                    Arrays.asList(LAYER_CLUSTERS, LAYER_UNCLUSTERED), null
            );

            mapView.getMapboxMap().queryRenderedFeatures(new RenderedQueryGeometry(mapView.getMapboxMap().pixelForCoordinate(point)), queryOptions, expected -> {
                if (expected.isValue() && expected.getValue() != null && !expected.getValue().isEmpty()) {
                    Feature feature = expected.getValue().get(0).getFeature();

                    if (feature.hasProperty("point_count")) {
                        Toast.makeText(getContext(), "Zoom in to see individual pets", Toast.LENGTH_SHORT).show();
                    } else if (feature.hasProperty("animalId")) {
                        Number idNum = feature.getNumberProperty("animalId");
                        if (idNum != null) {
                            Animal animal = findAnimalById(idNum.longValue());
                            if (animal != null) {
                                showInfoWindow(animal);
                            }
                        }
                    }
                }
            });
            return true;
        });
    }

    // *** FIX 2: This method now passes the animal ID directly when creating the action ***
    private void openDetails(long id) {
        NavHostFragment.findNavController(this).navigate(
                MapFragmentDirections.actionMapToPetDetail(id)
        );
    }

    private void geocodeAndShow(@NonNull Style style, @NonNull List<Animal> animals) {
        if (destroyed.get()) return;
        ensureExec().submit(() -> {
            if (destroyed.get()) return;
            List<Feature> feats = new ArrayList<>();
            List<Point> boundsPts = new ArrayList<>();
            for (Animal a : animals) {
                if (destroyed.get()) return;
                String q = buildAddressQuery(a);
                if (q == null || q.trim().isEmpty()) continue;
                Point p = MapGeocoder.geocode(q);
                if (p != null) {
                    Feature f = Feature.fromGeometry(p);
                    f.addNumberProperty("animalId", a.id);
                    if (a.name != null) f.addStringProperty("name", a.name);
                    feats.add(f);
                    boundsPts.add(p);
                }
            }
            FeatureCollection fc = FeatureCollection.fromFeatures(MapJitter.fanOutDuplicates(feats, 12.0));
            FragmentActivity act = getActivity();
            if (act == null || destroyed.get()) return;
            act.runOnUiThread(() -> {
                if (!isAdded() || destroyed.get() || mapView == null) return;
                style.setStyleSourceProperty(SRC_ID, "data", Value.fromJson(fc.toJson()).getValue());
                if (!cameraFittedOnce && boundsPts.size() >= 2) {
                    try {
                        EdgeInsets pad = new EdgeInsets(100.0, 100.0, 100.0, 100.0);
                        CameraOptions cam = mapView.getMapboxMap().cameraForCoordinates(boundsPts, pad, 0.0, 0.0);
                        mapView.getMapboxMap().setCamera(cam);
                        cameraFittedOnce = true;
                    } catch (Throwable t) {
                        Log.w(TAG, "cameraForCoordinates failed", t);
                    }
                }
            });
        });
    }

    private void checkPermissionsAndUseLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            useCurrentDeviceLocation();
        } else {
            locationPermsLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void useCurrentDeviceLocation() {
        if (getContext() == null) return;

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            try {
                LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
                if (lm == null) throw new Exception("LocationManager not found");

                final LocationListener locationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location loc) {
                        String latLngQuery = loc.getLatitude() + "," + loc.getLongitude();
                        sharedVm.searchAtLocation(latLngQuery);
                        if (mapView != null) {
                            mapView.getMapboxMap().setCamera(new CameraOptions.Builder().center(Point.fromLngLat(loc.getLongitude(), loc.getLatitude())).zoom(12.0).build());
                            cameraFittedOnce = true;
                        }
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Location found!", Toast.LENGTH_SHORT).show());
                        }
                    }
                };

                Toast.makeText(getContext(), "Getting location...", Toast.LENGTH_SHORT).show();

                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, Looper.getMainLooper());
                } else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, Looper.getMainLooper());
                } else {
                    Toast.makeText(getContext(), "Could not retrieve location. Please ensure GPS or Network location is enabled.", Toast.LENGTH_LONG).show();
                }

            } catch (Exception e) { // SecurityException is caught here
                Toast.makeText(getContext(), "Failed to get location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

        } else {
            Toast.makeText(getContext(), "Location permission is required.", Toast.LENGTH_SHORT).show();
        }
    }

    private void centerMapOnCurrentLocation() {
        if (mapView == null || sharedVm.lastLocation == null) return;
        try {
            String[] parts = sharedVm.lastLocation.split(",");
            if (parts.length == 2) {
                double lat = Double.parseDouble(parts[0]);
                double lng = Double.parseDouble(parts[1]);
                mapView.getMapboxMap().setCamera(new CameraOptions.Builder().center(Point.fromLngLat(lng, lat)).zoom(12.0).build());
                cameraFittedOnce = true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse lastLocation to center map", e);
        }
    }

    private ExecutorService ensureExec() {
        if (exec == null || exec.isShutdown() || exec.isTerminated()) {
            exec = Executors.newSingleThreadExecutor();
        }
        return exec;
    }

    private String buildAddressQuery(Animal a) {
        if (a == null || a.contact == null || a.contact.address == null) return "Toronto, ON, Canada";
        StringBuilder sb = new StringBuilder();
        append(sb, a.contact.address.address1);
        append(sb, a.contact.address.address2);
        append(sb, a.contact.address.city);
        append(sb, a.contact.address.state);
        if (notEmpty(a.contact.address.postcode)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(a.contact.address.postcode.trim());
        }
        if (notEmpty(a.contact.address.country)) {
            sb.append(", ").append(a.contact.address.country.trim());
        }
        return (sb.length() == 0) ? "Toronto, ON, Canada" : sb.toString();
    }

    private static boolean notEmpty(String s) { return s != null && !s.trim().isEmpty(); }

    private static void append(StringBuilder sb, String s) {
        if (notEmpty(s)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.trim());
        }
    }

    @Override public void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
    @Override public void onStop() { super.onStop(); if (mapView != null) mapView.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }

    @Override
    public void onDestroyView() {
        destroyed.set(true);
        super.onDestroyView();
        if (mapView != null) {
            mapView.onDestroy();
            mapView = null;
        }
        if (exec != null) {
            exec.shutdownNow();
            exec = null;
        }
    }

    @Override
    public void onFiltersApplied(FilterParams params) {
        sharedVm.applyFilters(params);
        SharedPreferences filterPrefs = requireContext().getSharedPreferences("filters", Context.MODE_PRIVATE);
        SharedPreferences.Editor e = filterPrefs.edit();
        for (Map.Entry<String, String> en : params.toPrefs().entrySet())
            e.putString(en.getKey(), en.getValue());
        e.apply();
        cameraFittedOnce = false;
    }

    @Nullable
    private Animal findAnimalById(long id) {
        List<Animal> currentAnimals = sharedVm.results().getValue();
        if (currentAnimals == null) return null;
        for (Animal animal : currentAnimals) {
            if (animal.id == id) {
                return animal;
            }
        }
        return null;
    }

    private void showInfoWindow(@NonNull Animal animal) {
        if (infoWindow == null) return;
        infoWindowName.setText(animal.name != null && !animal.name.isEmpty() ? animal.name : "(Unnamed)");
        String details = String.format(Locale.US, "%s • %s • %s", animal.age != null ? animal.age : "?", animal.gender != null ? animal.gender : "?", animal.size != null ? animal.size : "?");
        infoWindowDetails.setText(details);
        String imageUrl = null;
        if (animal.photos != null && !animal.photos.isEmpty()) {
            Photo p = animal.photos.get(0);
            if (p.medium != null) imageUrl = p.medium;
            else if (p.small != null) imageUrl = p.small;
            else if (p.large != null) imageUrl = p.large;
            else if (p.full != null) imageUrl = p.full;
        }
        Glide.with(this).load(imageUrl).placeholder(R.drawable.ic_paw).centerInside().into(infoWindowImage);
        infoWindowButton.setOnClickListener(v -> openDetails(animal.id));
        infoWindow.setVisibility(View.VISIBLE);
    }
}