package com.example.petpalfinder.network.petfinder;

import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.AnimalsResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface PetfinderApiService {

    @GET("animals")
    Call<AnimalsResponse> searchAnimals(
            @Query("type") String type,
            @Query("breed") String breed,
            @Query("location") String location,        // "lat,lon" or postal code
            @Query("distance") Integer distanceMiles,  // 0..500
            @Query("page") Integer page,
            @Query("limit") Integer limit,
            @Query("sort") String sort                 // "distance","-distance","recent"
    );

    @GET("animals/{id}")
    Call<com.example.petpalfinder.model.petfinder.SingleAnimalResponse> getAnimal(@Path("id") long id);
}
