package com.example.petpalfinder.repository;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.petpalfinder.data.FilterParams;
import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.AnimalsResponse;
import com.example.petpalfinder.model.petfinder.Organization;
import com.example.petpalfinder.model.petfinder.OrganizationResponse;
import com.example.petpalfinder.network.petfinder.PetfinderApiService;
import com.example.petpalfinder.network.petfinder.RetrofitProviders;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

public class PetfinderRepository {
    private final PetfinderApiService api;

    public PetfinderRepository(Context ctx) {
        this.api = new RetrofitProviders(ctx).api();
    }

    public AnimalsResponse searchAnimals(String locationOrLatLng,
                                         int page,
                                         int limit,
                                         @Nullable FilterParams filters) throws IOException {
        // Build query map from filters
        final Map<String, String> q = (filters == null)
                ? new HashMap<>()
                : filters.toQueryMap(locationOrLatLng);

        // Ensure required paging params are present
        q.put("page", String.valueOf(page));
        q.put("limit", String.valueOf(limit));

        Response<AnimalsResponse> r = api.searchAnimals(q).execute();
        if (!r.isSuccessful() || r.body() == null) {
            String body = (r.errorBody() != null) ? r.errorBody().string() : "";
            throw new IOException("Search failed: HTTP " + r.code() + (body.isEmpty() ? "" : " - " + body));
        }
        return r.body();
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
            String body = (r.errorBody() != null) ? r.errorBody().string() : "";
            throw new IOException("Search failed: HTTP " + r.code() + (body.isEmpty() ? "" : " - " + body));
        }
        return r.body();
    }

    public Animal getAnimal(long id) throws IOException {
        Response<com.example.petpalfinder.model.petfinder.SingleAnimalResponse> r =
                api.getAnimal(id).execute();
        if (!r.isSuccessful() || r.body() == null || r.body().animal == null) {
            String body = (r.errorBody() != null) ? r.errorBody().string() : "";
            throw new IOException("Animal fetch failed: HTTP " + r.code() + (body.isEmpty() ? "" : " - " + body));
        }
        return r.body().animal;
    }

    public Organization getOrganization(String id) throws IOException {
        Response<OrganizationResponse> r = api.getOrganization(id).execute();
        if (!r.isSuccessful() || r.body() == null || r.body().organization == null) {
            String body = (r.errorBody() != null) ? r.errorBody().string() : "";
            throw new IOException("Org fetch failed: HTTP " + r.code() + (body.isEmpty() ? "" : " - " + body));
        }
        return r.body().organization;
    }
}
