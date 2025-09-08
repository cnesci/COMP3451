package com.example.petpalfinder.ui.search;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.repository.PetfinderRepository;

import java.util.concurrent.Executors;

public class PetDetailViewModel extends AndroidViewModel {
    private final PetfinderRepository repo;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<Animal> animal = new MutableLiveData<>(null);

    public PetDetailViewModel(@NonNull Application app) {
        super(app);
        repo = new PetfinderRepository(app);
    }

    public LiveData<Boolean> loading() { return loading; }
    public LiveData<String> error() { return error; }
    public LiveData<Animal> animal() { return animal; }

    public void load(long id) {
        loading.postValue(true);
        error.postValue(null);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Animal a = repo.getAnimal(id);
                animal.postValue(a);
            } catch (Exception e) {
                error.postValue(e.getMessage());
            } finally {
                loading.postValue(false);
            }
        });
    }
}
