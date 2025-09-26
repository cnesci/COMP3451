package com.example.petpalfinder.repository;

public interface GeocodeCallback {
    void onResult(double lat, double lng, String formatted);

    default void onError(String message) { /* optional */ }
}
