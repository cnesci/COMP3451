package com.example.petpalfinder.repository;

/**
 * Single-abstract-method (SAM) interface so it works with Java lambdas.
 * onError is a default (non-abstract) method so it doesn't break lambdas.
 */
public interface GeocodeCallback {
    void onResult(double lat, double lng, String formatted);

    default void onError(String message) { /* optional */ }
}
