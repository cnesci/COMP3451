package com.example.petpalfinder.repository;

import androidx.annotation.NonNull;

import com.example.petpalfinder.BuildConfig;
import com.example.petpalfinder.model.opencage.OpenCageResponse;
import com.example.petpalfinder.network.OpenCageApi;
import com.example.petpalfinder.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GeocodingRepository {

    private final OpenCageApi api;
    private final String apiKey = BuildConfig.OPEN_CAGE_API_KEY;

    public GeocodingRepository() {
        api = RetrofitClient.get().create(OpenCageApi.class);
    }

    /** Raw forward geocoding (full Retrofit callback) */
    public void forward(String address, Callback<OpenCageResponse> callback) {
        // Keep payload small: limit=1, no_annotations=1
        api.forwardGeocode(address, apiKey, 1, 1).enqueue(callback);
    }

    /** Raw reverse geocoding (full Retrofit callback) */
    public void reverse(double lat, double lng, Callback<OpenCageResponse> callback) {
        String q = lat + "," + lng;
        api.reverseGeocode(q, apiKey, 1, 1).enqueue(callback);
    }

    /**
     * Simple one-lambda handler. Because onError has a default implementation,
     * this interface has only ONE abstract method (onSuccess) and is therefore
     * a functional interface—so lambdas work.
     */
    public interface SimpleHandler {
        void onSuccess(double lat, double lng, String formatted);
        default void onError(String message) { /* no-op by default */ }
    }

    /** Convenience wrapper for forward geocoding that uses SimpleHandler */
    public void forwardSimple(String address, SimpleHandler handler) {
        forward(address, new Callback<OpenCageResponse>() {
            @Override
            public void onResponse(@NonNull Call<OpenCageResponse> call,
                                   @NonNull Response<OpenCageResponse> response) {
                if (!response.isSuccessful() || response.body() == null ||
                        response.body().results == null || response.body().results.isEmpty()) {
                    handler.onError("No results: HTTP " + response.code());
                    return;
                }
                OpenCageResponse.Result r = response.body().results.get(0);
                handler.onSuccess(r.geometry.lat, r.geometry.lng, r.formatted);
            }

            @Override
            public void onFailure(@NonNull Call<OpenCageResponse> call, @NonNull Throwable t) {
                handler.onError(t.getMessage() != null ? t.getMessage() : "Request failed");
            }
        });
    }

    /** Optional: convenience wrapper for reverse geocoding with SimpleHandler */
    public void reverseSimple(double lat, double lng, SimpleHandler handler) {
        reverse(lat, lng, new Callback<OpenCageResponse>() {
            @Override
            public void onResponse(@NonNull Call<OpenCageResponse> call,
                                   @NonNull Response<OpenCageResponse> response) {
                if (!response.isSuccessful() || response.body() == null ||
                        response.body().results == null || response.body().results.isEmpty()) {
                    handler.onError("No results: HTTP " + response.code());
                    return;
                }
                OpenCageResponse.Result r = response.body().results.get(0);
                handler.onSuccess(r.geometry.lat, r.geometry.lng, r.formatted);
            }

            @Override
            public void onFailure(@NonNull Call<OpenCageResponse> call, @NonNull Throwable t) {
                handler.onError(t.getMessage() != null ? t.getMessage() : "Request failed");
            }
        });
    }
}
