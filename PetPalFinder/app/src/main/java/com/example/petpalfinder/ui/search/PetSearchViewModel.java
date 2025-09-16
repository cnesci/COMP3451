package com.example.petpalfinder.ui.search;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.example.petpalfinder.data.FilterParams;
import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.AnimalsResponse;
import com.example.petpalfinder.repository.GeocodingRepository;
import com.example.petpalfinder.repository.PetfinderRepository;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PetSearchViewModel extends AndroidViewModel {
    private static final int PAGE_SIZE = 100;

    private final PetfinderRepository repo;
    private final GeocodingRepository geocodingRepo;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<List<Animal>> results = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<FilterParams> filters = new MutableLiveData<>(FilterParams.defaults(null));
    private final MutableLiveData<String> formattedLocation = new MutableLiveData<>();
    private final AtomicBoolean isSearching = new AtomicBoolean(false);

    private int currentPage = 1;
    private int totalPages = 1;
    public String lastLocation = null;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public PetSearchViewModel(@NonNull Application app) {
        super(app);
        repo = new PetfinderRepository(app);
        geocodingRepo = new GeocodingRepository();
    }

    public LiveData<Boolean> loading() { return loading; }
    public LiveData<String> error() { return error; }
    public LiveData<List<Animal>> results() { return results; }
    public LiveData<FilterParams> getFilters() { return filters; }
    public LiveData<String> getFormattedLocation() { return formattedLocation; }

    public void searchAtLocation(String query) {
        if (query == null || query.trim().isEmpty() || isSearching.getAndSet(true)) {
            return;
        }
        loading.postValue(true);
        results.postValue(Collections.emptyList()); // Clear old results immediately

        io.execute(() -> {
            // Check if query is already a lat,lng string
            if (query.matches("^-?[0-9.]+,-?[0-9.]+$")) {
                lastLocation = query;
                String[] parts = query.split(",");

                geocodingRepo.reverseSimple(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                        new GeocodingRepository.SimpleHandler() {
                            @Override
                            public void onSuccess(double lat, double lng, String formatted) {
                                formattedLocation.postValue(formatted);
                            }

                            @Override
                            public void onError(String message) {
                                formattedLocation.postValue(query); // Fallback
                            }
                        }
                );

                currentPage = 1;
                totalPages = 1;
                runSearch(1, true);
            } else {
                // Forward geocode the address
                geocodingRepo.forwardSimple(query,
                        new GeocodingRepository.SimpleHandler() {
                            @Override
                            public void onSuccess(double lat, double lng, String formatted) {
                                lastLocation = lat + "," + lng;
                                formattedLocation.postValue(formatted);
                                currentPage = 1;
                                totalPages = 1;
                                runSearch(1, true);
                            }

                            @Override
                            public void onError(String message) {
                                error.postValue("Could not find location: " + query);
                                loading.postValue(false);
                                isSearching.set(false);
                            }
                        }
                );
            }
        });
    }

    public void applyFilters(FilterParams newFilters) {
        if (newFilters == null) return;

        FilterParams f = (filters.getValue() != null) ? filters.getValue() : FilterParams.defaults(null);
        f.type = (newFilters.type != null) ? newFilters.type : f.type;
        f.types = (newFilters.types != null) ? new ArrayList<>(newFilters.types) : new ArrayList<>();
        f.sort = (newFilters.sort == null || newFilters.sort.isEmpty()) ? "distance" : newFilters.sort;
        f.distanceKm = newFilters.distanceKm;
        f.genders = (newFilters.genders != null) ? new ArrayList<>(newFilters.genders) : new ArrayList<>();
        f.ages    = (newFilters.ages    != null) ? new ArrayList<>(newFilters.ages)    : new ArrayList<>();
        f.sizes   = (newFilters.sizes   != null) ? new ArrayList<>(newFilters.sizes)   : new ArrayList<>();
        f.goodWithChildren = newFilters.goodWithChildren;
        f.goodWithDogs     = newFilters.goodWithDogs;
        f.goodWithCats     = newFilters.goodWithCats;

        filters.setValue(f);
        currentPage = 1;
        totalPages = 1;
        runSearch(1, true);
    }

    public void nextPage() {
        if (loading.getValue() != null && loading.getValue()) return;
        if (currentPage >= totalPages) return;
        runSearch(currentPage + 1, false);
    }

    private void runSearch(final int page, final boolean replace) {
        if (lastLocation == null) {
            loading.postValue(false);
            isSearching.set(false);
            return;
        }
        loading.postValue(true);
        error.postValue(null);

        final FilterParams f = filters.getValue();

        io.execute(() -> {
            try {
                AnimalsResponse r = repo.searchAnimals(lastLocation, page, PAGE_SIZE, f);

                if (r != null && r.pagination != null) {
                    currentPage = Math.max(1, r.pagination.current_page);
                    totalPages = Math.max(1, r.pagination.total_pages);
                } else {
                    currentPage = page;
                    totalPages = page;
                }

                List<Animal> newList = (r != null && r.animals != null) ? r.animals : Collections.emptyList();
                if (replace) {
                    results.postValue(newList);
                } else {
                    List<Animal> cur = results.getValue();
                    List<Animal> merged = new ArrayList<>(cur != null ? cur : Collections.emptyList());
                    merged.addAll(newList);
                    results.postValue(merged);
                }
            } catch (Exception e) {
                error.postValue(e.getMessage());
            } finally {
                loading.postValue(false);
                isSearching.set(false);
            }
        });
    }

    @Override protected void onCleared() {
        super.onCleared();
        io.shutdownNow();
    }
}