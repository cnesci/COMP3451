package com.example.petpalfinder.ui.map;

import android.util.Log;

import androidx.annotation.Nullable;

import com.example.petpalfinder.BuildConfig;
import com.mapbox.geojson.Point;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class MapGeocoder {
    private static final String TAG = "MapGeocoder";
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Map<String, Point> CACHE = new ConcurrentHashMap<>();

    private MapGeocoder() {}

    @Nullable
    public static Point geocode(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String q = query.trim();
        if (CACHE.containsKey(q)) return CACHE.get(q);

        try {
            String key = BuildConfig.OPEN_CAGE_API_KEY; // exposed from build.gradle.kts
            if (key == null || key.isEmpty()) {
                Log.w(TAG, "OpenCage key missing (BuildConfig.OPEN_CAGE_API_KEY empty).");
                return null;
            }
            String url = "https://api.opencagedata.com/geocode/v1/json?q=" +
                    URLEncoder.encode(q, StandardCharsets.UTF_8.name()) +
                    "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8.name()) +
                    "&limit=1&no_annotations=1";
            Log.d(TAG, "GET " + url.replace(key, "****")); // mask key in logs

            Request req = new Request.Builder().url(url).build();
            try (Response res = CLIENT.newCall(req).execute()) {
                if (!res.isSuccessful() || res.body() == null) {
                    Log.w(TAG, "HTTP " + res.code() + " for: " + q);
                    return null;
                }
                String body = res.body().string();
                JSONObject root = new JSONObject(body);
                JSONArray results = root.optJSONArray("results");
                if (results == null || results.length() == 0) {
                    Log.d(TAG, "No results for: " + q);
                    return null;
                }
                JSONObject first = results.getJSONObject(0);
                JSONObject geom = first.getJSONObject("geometry");
                double lat = geom.getDouble("lat");
                double lng = geom.getDouble("lng");
                Point p = Point.fromLngLat(lng, lat);
                CACHE.put(q, p);
                Log.d(TAG, "OK " + q + " -> " + lat + "," + lng);
                return p;
            }
        } catch (Exception e) {
            Log.w(TAG, "geocode error for: " + q, e);
            return null;
        }
    }

    @Nullable
    public static Point test() {
        return geocode("Toronto, ON, Canada");
    }
}
