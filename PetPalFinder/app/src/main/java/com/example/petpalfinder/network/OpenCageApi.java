package com.example.petpalfinder.network;

import com.example.petpalfinder.model.opencage.OpenCageResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenCageApi {

    // Forward geocoding: q = "street, city" (e.g., "Toronto, ON")
    @GET("geocode/v1/json")
    Call<OpenCageResponse> forwardGeocode(
            @Query("q") String address,
            @Query("key") String apiKey,
            @Query("limit") Integer limit,
            @Query("no_annotations") Integer noAnnotations
    );

    // Reverse geocoding: q = "lat,lng" (e.g., "43.6532,-79.3832")
    @GET("geocode/v1/json")
    Call<OpenCageResponse> reverseGeocode(
            @Query("q") String latCommaLng,
            @Query("key") String apiKey,
            @Query("limit") Integer limit,
            @Query("no_annotations") Integer noAnnotations
    );
}
