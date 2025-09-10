package com.example.petpalfinder.ui.search;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.petpalfinder.data.FilterParams;
import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.AnimalsResponse;
import com.example.petpalfinder.repository.PetfinderRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PetSearchViewModel extends AndroidViewModel {
    private static final int PAGE_SIZE = 100;

    private final PetfinderRepository repo;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<List<Animal>> results = new MutableLiveData<>(Collections.<Animal>emptyList());

    // Filters
    private final MutableLiveData<FilterParams> filters = new MutableLiveData<>();

    // Paging + context
    private int currentPage = 1;
    private boolean endReached = false;
    private String lastLocation;
    private String lastType;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public PetSearchViewModel(@NonNull Application app) {
        super(app);
        repo = new PetfinderRepository(app);
    }

    public LiveData<Boolean> loading() { return loading; }
    public LiveData<String> error() { return error; }
    public LiveData<List<Animal>> results() { return results; }
    public LiveData<FilterParams> getFilters() { return filters; }

    /**
     * Initialize the baseline search context and default filters.
     * Call this once when the fragment starts (or when args change).
     */
    public void firstSearch(String type, String location) {
        // Remember context
        lastType = type;
        lastLocation = location;

        // Initialize filters if not set
        if (filters.getValue() == null) {
            FilterParams def = FilterParams.defaults(type);
            filters.postValue(def);
        } else if (filters.getValue().type == null) {
            // Ensure type is always set
            FilterParams cur = filters.getValue();
            cur.type = type;
            filters.postValue(cur);
        }

        // Reset paging and load
        currentPage = 1;
        endReached = false;
        runSearch(1, true);
    }

    public void applyFilters(@NonNull FilterParams newFilters) {
        // Keep lastType/lastLocation as fallbacks
        if (newFilters.type == null) newFilters.type = lastType;

        filters.postValue(newFilters);
        // Reset pagination and data
        currentPage = 1;
        endReached = false;
        results.postValue(Collections.<Animal>emptyList());
        runSearch(1, true);
    }

    /**
     * Load next page when user scrolls.
     */
    public void nextPage() {
        if (Boolean.TRUE.equals(loading.getValue())) return;
        if (endReached) return;
        runSearch(currentPage + 1, false);
    }

    private void runSearch(final int page, final boolean replace) {
        final FilterParams f = filters.getValue();
        final String type = (f != null && f.type != null) ? f.type : lastType;
        final String location = lastLocation;
        final int distanceKm = (f != null && f.distanceKm != null) ? f.distanceKm : 50; // default 50km
        final String sort = (f != null && f.sort != null) ? f.sort : "distance";

        loading.postValue(true);
        error.postValue(null);

        io.execute(() -> {
            try {
                AnimalsResponse r = repo.searchAnimals(
                        type,
                        null,
                        location,
                        distanceKm,
                        page,
                        PAGE_SIZE,
                        sort
                );

                // Pagination bookkeeping
                currentPage = r.pagination != null ? r.pagination.current_page : page;
                boolean gotEmpty = (r.animals == null || r.animals.isEmpty());
                if (gotEmpty && page > 1) {
                    endReached = true;
                }

                // Post results
                if (replace) {
                    results.postValue(r.animals != null ? r.animals : Collections.<Animal>emptyList());
                } else {
                    List<Animal> cur = results.getValue();
                    List<Animal> merged = new ArrayList<>(cur != null ? cur : Collections.<Animal>emptyList());
                    if (r.animals != null) merged.addAll(r.animals);
                    results.postValue(merged);
                }
            } catch (Exception e) {
                error.postValue(e.getMessage());
            } finally {
                loading.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        io.shutdownNow();
    }
}
