package com.example.petpalfinder.network.petfinder;

import com.example.petpalfinder.model.petfinder.AnimalsResponse;
import com.example.petpalfinder.model.petfinder.OrganizationResponse;
import com.example.petpalfinder.model.petfinder.SingleAnimalResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface PetfinderApiService {

    // Flexible search that takes a full filter map from FilterParams.toQueryMap()
    @GET("animals")
    Call<AnimalsResponse> searchAnimals(@QueryMap Map<String, String> options);

    // Direct animal detail
    @GET("animals/{id}")
    Call<SingleAnimalResponse> getAnimal(@Path("id") long id);

    // Organization detail
    @GET("organizations/{id}")
    Call<OrganizationResponse> getOrganization(@Path("id") String id);
}
