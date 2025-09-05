package com.example.petpalfinder.model.opencage;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OpenCageResponse {
    @SerializedName("results")
    public List<Result> results;

    public static class Result {
        @SerializedName("formatted")
        public String formatted;

        @SerializedName("geometry")
        public Geometry geometry;

        @SerializedName("components")
        public Components components;
    }

    public static class Geometry {
        @SerializedName("lat")
        public double lat;

        @SerializedName("lng")
        public double lng;
    }

    public static class Components {
        @SerializedName("city")
        public String city;

        @SerializedName("town")
        public String town; // Sometimes "town" instead of "city"

        @SerializedName("state")
        public String state;

        @SerializedName("postcode")
        public String postcode;

        @SerializedName("country")
        public String country;
    }
}
