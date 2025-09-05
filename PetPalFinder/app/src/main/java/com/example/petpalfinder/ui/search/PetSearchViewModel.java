package com.example.petpalfinder.ui.search;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.AnimalsResponse;
import com.example.petpalfinder.repository.PetfinderRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

public class PetSearchViewModel extends AndroidViewModel {
    private final PetfinderRepository repo;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<List<Animal>> results = new MutableLiveData<>(Collections.<Animal>emptyList());

    private int currentPage = 1;
    private String lastLocation;
    private String lastType;

    public PetSearchViewModel(@NonNull Application app) {
        super(app);
        repo = new PetfinderRepository(app);
    }

    public LiveData<Boolean> loading() { return loading; }
    public LiveData<String> error() { return error; }
    public LiveData<List<Animal>> results() { return results; }

    public void firstSearch(String type, String location) {
        currentPage = 1;
        lastType = type;
        lastLocation = location;
        runSearch(1, true);
    }

    public void nextPage() { runSearch(currentPage + 1, false); }

    private void runSearch(final int page, final boolean replace) {
        loading.postValue(true);
        error.postValue(null);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AnimalsResponse r = repo.searchAnimals(
                        lastType, null, lastLocation, 50, page, 20, "distance"
                );
                currentPage = r.pagination.current_page;
                if (replace) {
                    results.postValue(r.animals);
                } else {
                    List<Animal> cur = results.getValue();
                    List<Animal> merged = new ArrayList<>(cur != null ? cur : Collections.<Animal>emptyList());
                    merged.addAll(r.animals);
                    results.postValue(merged);
                }
            } catch (Exception e) {
                error.postValue(e.getMessage());
            } finally {
                loading.postValue(false);
            }
        });
    }
}
