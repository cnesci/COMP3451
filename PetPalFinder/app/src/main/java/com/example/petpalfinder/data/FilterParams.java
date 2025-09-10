package com.example.petpalfinder.data;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FilterParams {
    public String type;                 // e.g., "dog", "cat"
    public String gender;               // "male" | "female" | null
    public String age;                  // "baby" | "young" | "adult" | "senior" | null
    public String size;                 // "small" | "medium" | "large" | "xlarge" | null
    public boolean goodWithKids;
    public boolean goodWithDogs;
    public boolean goodWithCats;
    public Integer distanceKm;          // null means API default
    public String sort;                 // e.g., "distance"

    public static FilterParams defaults(String type) {
        FilterParams f = new FilterParams();
        f.type = type;
        f.sort = "distance";
        f.distanceKm = 50;
        return f;
    }

    public Map<String, String> toQueryMap(String locationOrLatLng) {
        Map<String, String> q = new HashMap<>();
        if (!TextUtils.isEmpty(type))     q.put("type", type);
        if (!TextUtils.isEmpty(gender))   q.put("gender", gender);
        if (!TextUtils.isEmpty(age))      q.put("age", age);
        if (!TextUtils.isEmpty(size))     q.put("size", size);
        if (goodWithKids)                 q.put("good_with_children", "true");
        if (goodWithDogs)                 q.put("good_with_dogs", "true");
        if (goodWithCats)                 q.put("good_with_cats", "true");
        if (!TextUtils.isEmpty(locationOrLatLng)) q.put("location", locationOrLatLng);
        if (distanceKm != null) {
            // Petfinder expects miles; convert km to mi
            int miles = Math.max(1, Math.round(distanceKm * 0.621371f));
            q.put("distance", String.valueOf(miles));
        }
        if (!TextUtils.isEmpty(sort))     q.put("sort", sort);
        return q;
    }

    public Map<String, String> toPrefs() {
        Map<String, String> m = new HashMap<>();
        m.put("type", type == null ? "" : type);
        m.put("gender", gender == null ? "" : gender);
        m.put("age", age == null ? "" : age);
        m.put("size", size == null ? "" : size);
        m.put("gwk", String.valueOf(goodWithKids));
        m.put("gwd", String.valueOf(goodWithDogs));
        m.put("gwc", String.valueOf(goodWithCats));
        m.put("distKm", distanceKm == null ? "" : String.valueOf(distanceKm));
        m.put("sort", sort == null ? "" : sort);
        return m;
    }

    public static FilterParams fromPrefs(Map<String, ?> p, String fallbackType) {
        FilterParams f = new FilterParams();
        f.type = str(p, "type", fallbackType);
        f.gender = emptyToNull(str(p, "gender", ""));
        f.age = emptyToNull(str(p, "age", ""));
        f.size = emptyToNull(str(p, "size", ""));
        f.goodWithKids = bool(p, "gwk", false);
        f.goodWithDogs = bool(p, "gwd", false);
        f.goodWithCats = bool(p, "gwc", false);
        String d = str(p, "distKm", "");
        f.distanceKm = d.isEmpty() ? null : Integer.parseInt(d);
        f.sort = emptyToNull(str(p, "sort", "distance"));
        return f;
    }

    private static String str(Map<String, ?> p, String k, String def) {
        Object v = p.get(k);
        return v == null ? def : String.valueOf(v);
    }
    private static boolean bool(Map<String, ?> p, String k, boolean def) {
        Object v = p.get(k);
        return v == null ? def : Boolean.parseBoolean(String.valueOf(v));
    }
    private static String emptyToNull(String s) { return (s == null || s.trim().isEmpty()) ? null : s; }

    @Override public String toString() {
        return String.format(Locale.US, "Filters{type=%s, gender=%s, age=%s, size=%s, kids=%s, dogs=%s, cats=%s, km=%s, sort=%s}",
                type, gender, age, size, goodWithKids, goodWithDogs, goodWithCats, distanceKm, sort);
    }
}
