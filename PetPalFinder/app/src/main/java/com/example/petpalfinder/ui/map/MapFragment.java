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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mapbox.bindgen.Value;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.EdgeInsets;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.RenderedQueryGeometry;
import com.mapbox.maps.RenderedQueryOptions;
import com.mapbox.maps.Style;
import com.mapbox.maps.plugin.gestures.GesturesUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MapFragment extends Fragment implements FilterBottomSheetFragment.Listener {

    // Constants for logging and Mapbox layer IDs
    private static final String TAG = "MapFragment";
    private static final String SRC_ID = "pet_points";
    private static final String LAYER_CLUSTERS = "pet_points_clusters";
    private static final String LAYER_CLUSTER_COUNT = "pet_points_cluster_count";
    private static final String LAYER_UNCLUSTERED = "pet_points_unclustered";
    private static final String LAYER_GROUP_COUNT = "pet_points_group_count";

    // UI Components
    private MapView mapView;
    private ImageButton btnMyLocation, btnListView, btnFilters;
    private PetSearchViewModel sharedVm;

    // Concurrency and state management
    private ExecutorService exec;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private boolean cameraFittedOnce = false;

    // Data persistence and Info Window UI
    private SharedPreferences prefs;
    private View infoWindow;
    private ImageView infoWindowImage;
    private TextView infoWindowDetails;
    private Button infoWindowButton;
    private TextView infoWindowName;

    // Handles requesting location permissions from the user
    private ActivityResultLauncher<String[]> locationPermsLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        destroyed.set(false);
        exec = Executors.newSingleThreadExecutor();

        // Initialize the permission launcher to handle the result of the location permission request.
        locationPermsLauncher =
                registerForActivityResult(
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

        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);

        // Initialize all UI views from the layout
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

        // Initialize SharedPreferences and the shared ViewModel
        prefs = requireContext().getSharedPreferences("location_prefs", Context.MODE_PRIVATE);
        sharedVm = new ViewModelProvider(requireActivity()).get(PetSearchViewModel.class);

        // Handle the device's back button press to navigate up
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavHostFragment.findNavController(MapFragment.this).navigateUp();
            }
        });

        // If there are no results, perform an initial search with the last saved location
        if (sharedVm.results().getValue() == null || sharedVm.results().getValue().isEmpty()) {
            String lastQuery = prefs.getString("last_query", "Bradford West Gwillimbury, ON");
            sharedVm.searchAtLocation(lastQuery);
        }

        // Observe location changes and update the search bar text
        sharedVm.getFormattedLocation().observe(getViewLifecycleOwner(), formatted -> {
            locationEditText.setText(formatted);
            prefs.edit().putString("last_query", sharedVm.lastLocation).putString("last_formatted", formatted).apply();
        });

        // Set up click listeners for location bar and map buttons
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

        // Load the map style and set up observers and tap handlers once loaded
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS, style -> {
            setupMapStyle(style);
            sharedVm.results().observe(getViewLifecycleOwner(), animals -> {
                if (animals == null) return;
                if (!isAdded() || destroyed.get()) return;
                geocodeAndShow(style, animals);
            });
            sharedVm.getFilters().observe(getViewLifecycleOwner(), f -> cameraFittedOnce = false);
            attachTapHandlers();
        });
    }

    // Configures the initial map style, source, and layers for displaying pet data
    private void setupMapStyle(@NonNull Style style) {
        mapView.getMapboxMap().setCamera(new CameraOptions.Builder().center(Point.fromLngLat(-79.3832, 43.6532)).zoom(10.0).build());

        // Define the GeoJSON source for pet data with clustering enabled
        String srcJson = "{\"type\":\"geojson\",\"data\":{\"type\":\"FeatureCollection\",\"features\":[]},\"cluster\":true,\"clusterMaxZoom\":14,\"clusterRadius\":50}";
        if (!style.styleSourceExists(SRC_ID)) {
            style.addStyleSource(SRC_ID, Value.fromJson(srcJson).getValue());
        }

        // Define the layer for displaying clustered points (circles)
        String clustersLayerJson = "{\"id\":\"" + LAYER_CLUSTERS + "\",\"type\":\"circle\",\"source\":\"" + SRC_ID + "\",\"filter\":[\"has\",\"point_count\"],\"paint\":{\"circle-color\":[\"step\",[\"get\",\"point_count\"],\"#93c5fd\",10,\"#60a5fa\",25,\"#3b82f6\"],\"circle-radius\":[\"step\",[\"get\",\"point_count\"],14,10,18,25,22]}}";
        if (!style.styleLayerExists(LAYER_CLUSTERS)) {
            style.addStyleLayer(Value.fromJson(clustersLayerJson).getValue(), null);
        }

        // Define the layer for displaying the number inside each cluster
        String countLayerJson = "{\"id\":\"" + LAYER_CLUSTER_COUNT + "\",\"type\":\"symbol\",\"source\":\"" + SRC_ID + "\",\"filter\":[\"has\",\"point_count\"],\"layout\":{\"text-field\":\"{point_count_abbreviated}\",\"text-size\":12},\"paint\":{\"text-color\":\"#ffffff\"}}";
        if (!style.styleLayerExists(LAYER_CLUSTER_COUNT)) {
            style.addStyleLayer(Value.fromJson(countLayerJson).getValue(), null);
        }

        // Define the layer for displaying individual (unclustered) pet pins
        String unclusteredLayerJson = "{\"id\":\"" + LAYER_UNCLUSTERED + "\",\"type\":\"circle\",\"source\":\"" + SRC_ID + "\",\"filter\":[\"!\",[\"has\",\"point_count\"]],\"paint\":{\"circle-radius\":8,\"circle-color\":\"#3b82f6\",\"circle-stroke-color\":\"#ffffff\",\"circle-stroke-width\":2}}";
        if (!style.styleLayerExists(LAYER_UNCLUSTERED)) {
            style.addStyleLayer(Value.fromJson(unclusteredLayerJson).getValue(), null);
        }

        // Define the layer for showing a count on pins with multiple pets at the same location
        String groupCountLayerJson = "{\"id\":\"" + LAYER_GROUP_COUNT + "\",\"type\":\"symbol\",\"source\":\"" + SRC_ID + "\",\"filter\":[\"all\",[\"!\",[\"has\",\"point_count\"]],[\"has\",\"count\"]],\"layout\":{\"text-field\":\"{count}\",\"text-size\":12,\"text-ignore-placement\":true,\"text-allow-overlap\":true},\"paint\":{\"text-color\":\"#ffffff\"}}";
        if (!style.styleLayerExists(LAYER_GROUP_COUNT)) {
            style.addStyleLayer(Value.fromJson(groupCountLayerJson).getValue(), null);
        }
    }

    // Sets up a listener to handle taps on the map
    private void attachTapHandlers() {
        if (mapView == null) return;
        MapboxMap map = mapView.getMapboxMap();
        Gson gson = new Gson();

        GesturesUtils.getGestures(mapView).addOnMapClickListener(point -> {
            if (infoWindow != null && infoWindow.getVisibility() == View.VISIBLE) {
                infoWindow.setVisibility(View.GONE);
            }

            // Query the map to see if a feature was tapped
            RenderedQueryOptions queryOptions = new RenderedQueryOptions(Arrays.asList(LAYER_CLUSTERS, LAYER_UNCLUSTERED), null);
            map.queryRenderedFeatures(new RenderedQueryGeometry(map.pixelForCoordinate(point)), queryOptions, expected -> {
                if (expected.isValue() && expected.getValue() != null && !expected.getValue().isEmpty()) {
                    Feature feature = expected.getValue().get(0).getFeature();
                    if (feature.hasProperty("point_count")) { // Tapped a cluster
                        Toast.makeText(getContext(), "Zoom in to see individual pets", Toast.LENGTH_SHORT).show();
                    } else if (feature.hasProperty("isGroup")) { // Tapped a group of pets
                        String idsJson = feature.getStringProperty("animalIdsJson");
                        List<Long> ids = gson.fromJson(idsJson, new TypeToken<List<Long>>() {}.getType());
                        showPetListDialog(ids);
                    } else if (feature.hasProperty("animalId")) { // Tapped a single pet
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

    // Navigates to the pet detail screen for the given animal ID
    private void openDetails(long id) {
        NavHostFragment.findNavController(this).navigate(MapFragmentDirections.actionMapToPetDetail(id));
    }

    // Processes the list of animals, geocodes their locations, groups them, and updates the map
    private void geocodeAndShow(@NonNull Style style, @NonNull List<Animal> animals) {
        if (destroyed.get()) return;
        ensureExec().submit(() -> {
            if (destroyed.get()) return;

            // Group animals by their geocoded location
            Map<String, List<Animal>> animalsByLocation = new LinkedHashMap<>();
            Map<String, Point> locationPoints = new HashMap<>();
            for (Animal a : animals) {
                if (destroyed.get()) return;
                String q = buildAddressQuery(a);
                if (q == null || q.trim().isEmpty()) continue;
                Point p = MapGeocoder.geocode(q);
                if (p != null) {
                    String key = String.format(Locale.US, "%.4f,%.4f", p.latitude(), p.longitude());
                    if (!animalsByLocation.containsKey(key)) {
                        animalsByLocation.put(key, new ArrayList<>());
                        locationPoints.put(key, p);
                    }
                    animalsByLocation.get(key).add(a);
                }
            }

            // Create map features from the grouped animals
            List<Feature> feats = new ArrayList<>();
            List<Point> boundsPts = new ArrayList<>();
            Gson gson = new Gson();
            for (Map.Entry<String, List<Animal>> entry : animalsByLocation.entrySet()) {
                Point point = locationPoints.get(entry.getKey());
                List<Animal> petsAtLocation = entry.getValue();
                if (point == null) continue;
                boundsPts.add(point);
                Feature f = Feature.fromGeometry(point);

                if (petsAtLocation.size() == 1) { // Single pet at this location
                    Animal singlePet = petsAtLocation.get(0);
                    f.addNumberProperty("animalId", singlePet.id);
                    if (singlePet.name != null) f.addStringProperty("name", singlePet.name);
                } else { // Multiple pets at this location
                    f.addBooleanProperty("isGroup", true);
                    List<Long> ids = new ArrayList<>();
                    for (Animal pet : petsAtLocation) {
                        ids.add(pet.id);
                    }
                    f.addStringProperty("animalIdsJson", gson.toJson(ids));
                    f.addNumberProperty("count", petsAtLocation.size());
                }
                feats.add(f);
            }

            // Update the map on the main thread
            FeatureCollection fc = FeatureCollection.fromFeatures(feats);
            FragmentActivity act = getActivity();
            if (act == null || destroyed.get()) return;
            act.runOnUiThread(() -> {
                if (!isAdded() || destroyed.get() || mapView == null) return;
                style.setStyleSourceProperty(SRC_ID, "data", Value.fromJson(fc.toJson()).getValue());

                // Zoom the camera to fit all results, but only on the first load or after filters change
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

    // Checks for location permissions and requests them if necessary
    private void checkPermissionsAndUseLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            useCurrentDeviceLocation();
        } else {
            locationPermsLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    // Retrieves the user's current device location
    private void useCurrentDeviceLocation() {
        if (getContext() == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
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
        } catch (Exception e) {
            Toast.makeText(getContext(), "Failed to get location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Centers the map on the last searched location from the ViewModel
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

    // Ensures the background thread executor is running
    private ExecutorService ensureExec() {
        if (exec == null || exec.isShutdown() || exec.isTerminated()) {
            exec = Executors.newSingleThreadExecutor();
        }
        return exec;
    }

    // Constructs a full address string from an animal's contact info for geocoding
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

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static void append(StringBuilder sb, String s) {
        if (notEmpty(s)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.trim());
        }
    }

    // Fragment lifecycle methods to manage the MapView
    @Override
    public void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mapView != null) mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

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

    // Callback from the filter sheet, applies new filters and resets the map view
    @Override
    public void onFiltersApplied(FilterParams params) {
        sharedVm.applyFilters(params);
        SharedPreferences filterPrefs = requireContext().getSharedPreferences("filters", Context.MODE_PRIVATE);
        SharedPreferences.Editor e = filterPrefs.edit();
        for (Map.Entry<String, String> en : params.toPrefs().entrySet())
            e.putString(en.getKey(), en.getValue());
        e.apply();
        cameraFittedOnce = false; // Reset camera flag to allow zooming to new results
    }

    // Helper to find an animal object from the current list by its ID
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

    // Displays the bottom info window for a specific animal
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

    // Shows a dialog with a list of pets when a group pin is tapped
    private void showPetListDialog(List<Long> animalIds) {
        if (animalIds == null || animalIds.isEmpty() || getContext() == null) return;
        List<Animal> petsToShow = new ArrayList<>();
        List<String> petNames = new ArrayList<>();
        for (Long id : animalIds) {
            Animal pet = findAnimalById(id);
            if (pet != null) {
                petsToShow.add(pet);
                petNames.add(pet.name != null ? pet.name : "(Unnamed)");
            }
        }
        if (petsToShow.isEmpty()) return;
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(petsToShow.size() + " pets at this location")
                .setItems(petNames.toArray(new String[0]), (dialog, which) -> {
                    Animal selectedPet = petsToShow.get(which);
                    if (selectedPet != null) {
                        showInfoWindow(selectedPet); // Show preview instead of opening details
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

