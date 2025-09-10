package com.example.petpalfinder.ui.map;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fans out features that share identical coordinates by placing them
 * around a small circle so they don't overlap visually.
 */
public final class MapJitter {
    private MapJitter() {}

    public static List<Feature> fanOutDuplicates(List<Feature> in, double radiusMeters) {
        if (in == null || in.isEmpty()) return Collections.emptyList();

        // Group by rounded lat/lon so "identical" points land in same bucket
        Map<String, List<Feature>> groups = new LinkedHashMap<>();
        for (Feature f : in) {
            if (f == null || f.geometry() == null || !(f.geometry() instanceof Point)) continue;
            Point p = (Point) f.geometry();
            String key = round6(p.latitude()) + "," + round6(p.longitude());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }

        List<Feature> out = new ArrayList<>(in.size());
        for (List<Feature> group : groups.values()) {
            int n = group.size();
            if (n <= 1) {
                out.add(group.get(0));
                continue;
            }

            // Use the first point as the center
            Point center = (Point) group.get(0).geometry();
            double lat = center.latitude();
            double lon = center.longitude();
            double latRad = Math.toRadians(lat);

            // meters -> degrees (approx)
            double dLat = radiusMeters / 111_320.0;
            double dLon = radiusMeters / (111_320.0 * Math.cos(latRad));

            for (int i = 0; i < n; i++) {
                double angle = (2 * Math.PI * i) / n;
                double newLat = lat + dLat * Math.sin(angle);
                double newLon = lon + dLon * Math.cos(angle);

                Feature src  = group.get(i);
                Feature copy = Feature.fromGeometry(Point.fromLngLat(newLon, newLat));

                try {
                    if (src.hasProperty("animalId")) {
                        copy.addNumberProperty("animalId", src.getNumberProperty("animalId"));
                    }
                    if (src.hasProperty("name")) {
                        copy.addStringProperty("name", src.getStringProperty("name"));
                    }
                } catch (Exception ignored) {}

                out.add(copy);
            }
        }
        return out;
    }

    private static String round6(double v) {
        return String.format(Locale.US, "%.6f", v);
    }
}
