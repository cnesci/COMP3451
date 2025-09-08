package com.example.petpalfinder.repository;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.AnimalsResponse;
import com.example.petpalfinder.network.petfinder.PetfinderApiService;
import com.example.petpalfinder.network.petfinder.RetrofitProviders;

import java.io.IOException;

import retrofit2.Response;

public class PetfinderRepository {
    private final PetfinderApiService api;

    public PetfinderRepository(Context ctx) {
        this.api = new RetrofitProviders(ctx).api();
    }

    public AnimalsResponse searchAnimals(@Nullable String type,
                                         @Nullable String breed,
                                         String location,
                                         @Nullable Integer distanceMiles,
                                         int page,
                                         int limit,
                                         @Nullable String sort) throws IOException {
        Response<AnimalsResponse> r =
                api.searchAnimals(type, breed, location, distanceMiles, page, limit, sort).execute();
        if (!r.isSuccessful() || r.body() == null) {
            throw new IOException("Search failed: HTTP " + r.code());
        }
        return r.body();
    }

    public Animal getAnimal(long id) throws IOException {
        retrofit2.Response<com.example.petpalfinder.model.petfinder.SingleAnimalResponse> r =
                api.getAnimal(id).execute();
        if (!r.isSuccessful() || r.body() == null || r.body().animal == null) {
            throw new IOException("Animal fetch failed: HTTP " + r.code());
        }
        return r.body().animal;
    }

}
