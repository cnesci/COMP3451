package com.example.petpalfinder.ui.search;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.example.petpalfinder.data.FilterParams;
import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.AnimalsResponse;
import com.example.petpalfinder.repository.PetfinderRepository;
import java.util.*;
import java.util.concurrent.*;

public class PetSearchViewModel extends AndroidViewModel {
    private static final int PAGE_SIZE = 100;

    private final PetfinderRepository repo;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<List<Animal>> results = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<FilterParams> filters = new MutableLiveData<>(FilterParams.defaults(null));

    private int currentPage = 1;
    private int totalPages = 1;
    private String lastLocation = null;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public PetSearchViewModel(@NonNull Application app) {
        super(app);
        repo = new PetfinderRepository(app);
    }

    public LiveData<Boolean> loading() { return loading; }
    public LiveData<String> error() { return error; }
    public LiveData<List<Animal>> results() { return results; }
    public LiveData<FilterParams> getFilters() { return filters; }

    public void firstSearch(String type, String location) {
        FilterParams f = (filters.getValue() != null) ? filters.getValue() : FilterParams.defaults(type);
        if ((f.types == null || f.types.isEmpty()) && type != null && !type.isEmpty()) {
            f.types = new ArrayList<>(List.of(type));
        }
        if (f.sort == null || f.sort.isEmpty()) f.sort = "distance";

        filters.setValue(f);
        currentPage = 1;
        lastLocation = location;
        runSearch(1, true);
    }

    public void applyFilters(FilterParams newFilters) {
        if (newFilters == null) return;

        FilterParams f = (filters.getValue() != null) ? filters.getValue() : FilterParams.defaults(null);

        f.type = (newFilters.type != null) ? newFilters.type : f.type;
        f.types = (newFilters.types != null) ? new ArrayList<>(newFilters.types)
                : (f.types != null ? new ArrayList<>(f.types) : new ArrayList<>());

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
        if (loading.getValue() != null && loading.getValue()) return; // Don't fetch if already loading
        if (currentPage >= totalPages) return;
        runSearch(currentPage + 1, false);
    }

    private void runSearch(final int page, final boolean replace) {
        loading.postValue(true);
        error.postValue(null);

        final FilterParams f = filters.getValue();
        final String location = lastLocation;

        io.execute(() -> {
            try {
                AnimalsResponse r = repo.searchAnimals(location, page, PAGE_SIZE, f);
                if (r != null && r.pagination != null) {
                    currentPage = Math.max(1, r.pagination.current_page);
                    totalPages = Math.max(1, r.pagination.total_pages);
                } else {
                    // If pagination info is missing, assume we are on the last page.
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
            }
        });
    }

    @Override protected void onCleared() {
        super.onCleared();
        io.shutdownNow();
    }
}
