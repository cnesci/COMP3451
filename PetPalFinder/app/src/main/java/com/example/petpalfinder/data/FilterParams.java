package com.example.petpalfinder.data;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FilterParams {
    @Nullable public String type;      // seed from nav args
    @Nullable public String gender;
    @Nullable public String age;
    @Nullable public String size;

    // --- multi-select canonical fields used by UI / VM ---
    public List<String> types   = new ArrayList<>();
    public List<String> genders = new ArrayList<>();
    public List<String> ages    = new ArrayList<>();
    public List<String> sizes   = new ArrayList<>();

    // --- toggles ---
    /** Canonical flag used in queries/prefs. */
    public boolean goodWithChildren = false;
    public boolean goodWithDogs     = false;
    public boolean goodWithCats     = false;

    @Deprecated
    public boolean goodWithKids = false;

    // distance + sort
    @Nullable public Integer distanceKm;   // UI keeps km
    @Nullable public String  sort;         // e.g. "distance", "recent"

    public static FilterParams defaults(@Nullable String typeArg) {
        FilterParams f = new FilterParams();
        f.sort = "distance";
        if (typeArg != null && !typeArg.trim().isEmpty()) {
            f.type = typeArg.trim();
            f.types.add(f.type.toLowerCase(Locale.US));
            } else {
                    f.type = null;
            }
        if (f.distanceKm == null) f.distanceKm = 50; // sensible default
        return f;
    }


    public static FilterParams fromPrefs(Map<String, ?> prefs, @Nullable String typeArg) {
        FilterParams f = defaults(typeArg);

        // Lists saved as CSV
        String typesCsv   = getString(prefs, "typesCsv", null);
        String gendersCsv = getString(prefs, "gendersCsv", null);
        String agesCsv    = getString(prefs, "agesCsv", null);
        String sizesCsv   = getString(prefs, "sizesCsv", null);

        if (typesCsv != null)   f.types   = splitCsv(typesCsv);
        if (gendersCsv != null) f.genders = splitCsv(gendersCsv);
        if (agesCsv != null)    f.ages    = splitCsv(agesCsv);
        if (sizesCsv != null)   f.sizes   = splitCsv(sizesCsv);

        String legacyType   = getString(prefs, "type", null);
        String legacyGender = getString(prefs, "gender", null);
        String legacyAge    = getString(prefs, "age", null);
        String legacySize   = getString(prefs, "size", null);
        if (legacyType != null && !f.types.contains(legacyType))       f.types.add(legacyType);
        if (legacyGender != null && !f.genders.contains(legacyGender)) f.genders.add(legacyGender);
        if (legacyAge != null && !f.ages.contains(legacyAge))          f.ages.add(legacyAge);
        if (legacySize != null && !f.sizes.contains(legacySize))       f.sizes.add(legacySize);

        // Toggles
        boolean gwk = getBool(prefs, "gwk", false);
        f.goodWithChildren = getBool(prefs, "good_with_children", gwk);
        f.goodWithDogs     = getBool(prefs, "gwd", false) || getBool(prefs, "good_with_dogs", false);
        f.goodWithCats     = getBool(prefs, "gwc", false) || getBool(prefs, "good_with_cats", false);

        f.distanceKm = getInt(prefs, "distKm", (f.distanceKm != null ? f.distanceKm : 50));
        f.sort       = getString(prefs, "sort", (f.sort != null ? f.sort : "distance"));

        f.gender = firstOrNull(f.genders);
        f.age    = firstOrNull(f.ages);
        f.size   = firstOrNull(f.sizes);

        f.syncAliases();
        return f;
    }

    public Map<String, String> toPrefs() {
        syncAliases();
        Map<String, String> m = new LinkedHashMap<>();
        m.put("typesCsv",   joinCsv(types));
        m.put("gendersCsv", joinCsv(genders));
        m.put("agesCsv",    joinCsv(ages));
        m.put("sizesCsv",   joinCsv(sizes));
        m.put("gwk",        Boolean.toString(goodWithChildren)); // legacy key
        m.put("gwd",        Boolean.toString(goodWithDogs));
        m.put("gwc",        Boolean.toString(goodWithCats));
        m.put("good_with_children", Boolean.toString(goodWithChildren));
        m.put("good_with_dogs",     Boolean.toString(goodWithDogs));
        m.put("good_with_cats",     Boolean.toString(goodWithCats));
        if (distanceKm != null) m.put("distKm", Integer.toString(distanceKm));
        if (sort != null)       m.put("sort", sort);
        if (type != null)   m.put("type", type);
        if (gender != null) m.put("gender", gender);
        if (age != null)    m.put("age", age);
        if (size != null)   m.put("size", size);
        return m;
    }

    /** Build the Petfinder query. Assumes server accepts comma lists for multi-selects. */
    public Map<String, String> toQueryMap(@Nullable String locationOrLatLng) {
        Map<String, String> q = new HashMap<>();

        if (locationOrLatLng != null && !locationOrLatLng.trim().isEmpty()) {
            q.put("location", locationOrLatLng.trim());
        }

        if (!genders.isEmpty()) q.put("gender", String.join(",", lower(genders)));
        if (!ages.isEmpty())    q.put("age",    String.join(",", lower(ages)));
        if (!sizes.isEmpty())   q.put("size",   String.join(",", lower(sizes)));

        if (goodWithChildren) q.put("good_with_children", "true");
        if (goodWithDogs)     q.put("good_with_dogs", "true");
        if (goodWithCats)     q.put("good_with_cats", "true");

        // km -> miles (Petfinder expects miles, 0..500)
        if (distanceKm != null) {
            int miles = Math.max(0, Math.min(500, Math.round(distanceKm * 0.621371f)));
            q.put("distance", Integer.toString(miles));
        }

        if (sort != null && !sort.isEmpty()) q.put("sort", sort);
        return q;
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------------------------------------------------

    private void syncAliases() {
        if (goodWithKids && !goodWithChildren) {
            goodWithChildren = true;
        }
        goodWithKids = goodWithChildren;
    }

    private static String joinCsv(List<String> xs) {
        if (xs == null || xs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : xs) {
            if (s == null) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(s.trim());
        }
        return sb.toString();
    }

    private static List<String> splitCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) return out;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static List<String> lower(List<String> xs) {
        List<String> out = new ArrayList<>();
        for (String s : xs) if (s != null) out.add(s.toLowerCase(Locale.US));
        return out;
    }

    private static String firstOrNull(List<String> xs) {
        return (xs != null && !xs.isEmpty()) ? xs.get(0) : null;
    }

    private static String getString(Map<String, ?> m, String k, String def) {
        Object v = m.get(k);
        return (v instanceof String) ? (String) v : def;
    }

    private static boolean getBool(Map<String, ?> m, String k, boolean def) {
        Object v = m.get(k);
        if (v instanceof String) return Boolean.parseBoolean((String) v);
        if (v instanceof Boolean) return (Boolean) v;
        return def;
    }

    private static Integer getInt(Map<String, ?> m, String k, Integer def) {
        Object v = m.get(k);
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (Exception ignored) {}
        } else if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return def;
    }
}
