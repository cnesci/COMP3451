package com.example.petpalfinder.ui.search;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.petpalfinder.database.AppDatabase;
import com.example.petpalfinder.database.FavoriteAnimal;
import com.example.petpalfinder.database.FavoriteDao;
import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.Organization;
import com.example.petpalfinder.repository.PetfinderRepository;

import java.util.concurrent.Executors;

public class PetDetailViewModel extends AndroidViewModel {
    private final PetfinderRepository repo;
    private final FavoriteDao favoriteDao;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<Animal> animal = new MutableLiveData<>(null);
    private final MutableLiveData<Organization> org = new MutableLiveData<>(null);

    public PetDetailViewModel(@NonNull Application app) {
        super(app);
        repo = new PetfinderRepository(app);
        favoriteDao = AppDatabase.getDatabase(app).favoriteDao();
    }

    public LiveData<Boolean> loading() {
        return loading;
    }

    public LiveData<String> error() {
        return error;
    }

    public LiveData<Animal> animal() {
        return animal;
    }

    public LiveData<Organization> organization() {
        return org;
    }

    public LiveData<Boolean> isFavorite(long animalId) {
        return favoriteDao.isFavorite(animalId);
    }

    public void toggleFavorite() {
        Animal currentAnimal = animal.getValue();
        if (currentAnimal == null) return;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (isFavorite(currentAnimal.id).getValue() == Boolean.TRUE) {
                favoriteDao.delete(new FavoriteAnimal(currentAnimal));
            } else {
                favoriteDao.insert(new FavoriteAnimal(currentAnimal));
            }
        });
    }

    public void load(long id) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Animal a = repo.getAnimal(id);
                animal.postValue(a);

                // also fetch org if present
                if (!TextUtils.isEmpty(a.organization_id)) {
                    try {
                        Organization o = repo.getOrganization(a.organization_id);
                        org.postValue(o);
                    } catch (Exception ignore) {
                    }
                }
            } catch (Exception e) {
                error.postValue(e.getMessage());
            } finally {
                loading.postValue(false);
            }
        });
    }
}
