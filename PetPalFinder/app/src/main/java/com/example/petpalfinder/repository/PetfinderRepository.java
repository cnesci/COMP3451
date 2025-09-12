package com.example.petpalfinder.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.petpalfinder.BuildConfig;
import com.example.petpalfinder.data.FilterParams;
import com.example.petpalfinder.model.petfinder.Animal;
import com.example.petpalfinder.model.petfinder.AnimalsResponse;
import com.example.petpalfinder.model.petfinder.Organization;
import com.example.petpalfinder.model.petfinder.OrganizationResponse;
import com.example.petpalfinder.model.petfinder.SingleAnimalResponse;
import com.example.petpalfinder.network.petfinder.PetfinderApiService;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.Route;
import okhttp3.Response;
import okio.Buffer;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class PetfinderRepository {
    private static final String TAG = "PetfinderRepo";

    private final PetfinderApiService api;
    private final TokenProvider tokenProvider;

    public PetfinderRepository(Context ctx) {
        this.tokenProvider = new TokenProvider(
                BuildConfig.PETFINDER_CLIENT_ID,
                BuildConfig.PETFINDER_CLIENT_SECRET
        );

        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                // Attach bearer to every request
                .addInterceptor(new Interceptor() {
                    @Override public Response intercept(Chain chain) throws IOException {
                        String token = tokenProvider.getValidToken();
                        Request req = chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer " + token)
                                .build();
                        return chain.proceed(req);
                    }
                })
                // If server says 401, refresh token once and retry
                .authenticator(new Authenticator() {
                    @Override public Request authenticate(@Nullable Route route, Response response) throws IOException {
                        // Avoid infinite loops
                        if (response.request().header("Authorization") != null && responseCount(response) > 1) {
                            return null;
                        }
                        tokenProvider.forceRefresh();
                        String fresh = tokenProvider.getValidToken();
                        return response.request().newBuilder()
                                .header("Authorization", "Bearer " + fresh)
                                .build();
                    }
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.petfinder.com/v2/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(http)
                .build();

        api = retrofit.create(PetfinderApiService.class);
    }

    // ----- Public API -----

    public AnimalsResponse searchAnimals(
            @Nullable String locationOrLatLng,
            int page,
            int limit,
            @NonNull FilterParams filters
    ) throws Exception {

        // Build base query from filters (already converts km tomiles, handles genders/ages/sizes/toggles)
        Map<String, String> q = new HashMap<>(filters.toQueryMap(locationOrLatLng));
        q.put("status", "adoptable");
        q.put("page", Integer.toString(Math.max(1, page)));
        q.put("limit", Integer.toString(Math.max(1, Math.min(100, limit))));
        if (filters.sort != null && !filters.sort.isEmpty()) {
            q.put("sort", filters.sort);
        }

        // Log the outgoing query to help debug
        Log.d(TAG, "GET /animals " + q.toString());

        retrofit2.Response<AnimalsResponse> resp = api.searchAnimals(q).execute();

        if (!resp.isSuccessful()) {
            String errBody = safeBody(resp);
            Log.e(TAG, "HTTP " + resp.code() + " searching animals – " + errBody);
            throw new IOException("Petfinder search failed: " + resp.code());
        }

        AnimalsResponse body = resp.body();
        return (body != null) ? body : new AnimalsResponse(); // empty-safe
    }

    public Animal getAnimal(long id) throws IOException {
        retrofit2.Response<SingleAnimalResponse> r = api.getAnimal(id).execute();
        if (!r.isSuccessful()) {
            Log.e(TAG, "HTTP " + r.code() + " getAnimal(" + id + "): " + safeBody(r));
            return null;
        }
        return (r.body() != null) ? r.body().animal : null;
    }

    public Organization getOrganization(String id) throws IOException {
        retrofit2.Response<OrganizationResponse> r = api.getOrganization(id).execute();
        if (!r.isSuccessful()) {
            Log.e(TAG, "HTTP " + r.code() + " getOrganization(" + id + "): " + safeBody(r));
            return null;
        }
        return (r.body() != null) ? r.body().organization : null;
    }

    // ----- Helpers -----

    private static int responseCount(Response response) {
        int count = 1;
        while ((response = response.priorResponse()) != null) count++;
        return count;
    }

    private static String safeBody(retrofit2.Response<?> r) {
        try {
            ResponseBody b = r.errorBody();
            return (b != null) ? b.string() : "<empty>";
        } catch (Exception e) {
            return "<unreadable>";
        }
    }

    // ----- Simple token provider -----

    private static final class TokenProvider {
        private static final String BASE = "https://api.petfinder.com/v2/";
        private final String clientId;
        private final String clientSecret;

        // cached token
        private volatile String accessToken = null;
        private volatile long   expiryEpochMillis = 0L;

        private final OkHttpClient bare = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        TokenProvider(String clientId, String clientSecret) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        synchronized String getValidToken() throws IOException {
            long now = System.currentTimeMillis();
            if (accessToken == null || now >= expiryEpochMillis) {
                fetchToken();
            }
            return accessToken;
        }

        synchronized void forceRefresh() {
            accessToken = null;
            expiryEpochMillis = 0L;
        }

        private void fetchToken() throws IOException {
            FormBody form = new FormBody.Builder()
                    .add("grant_type", "client_credentials")
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .build();

            Request req = new Request.Builder()
                    .url(BASE + "oauth2/token")
                    .post(form)
                    .build();

            try (Response resp = bare.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    String body = (resp.body() != null) ? resp.body().string() : "<empty>";
                    Log.e(TAG, "Token fetch failed " + resp.code() + ": " + body);
                    throw new IOException("Token fetch failed: " + resp.code());
                }
                String body = (resp.body() != null) ? resp.body().string() : "{}";
                JSONObject json = new JSONObject(body);
                accessToken = json.optString("access_token", null);
                int expiresIn = json.optInt("expires_in", 0);
                // Refresh a minute early
                long skewMs = 60_000L;
                expiryEpochMillis = System.currentTimeMillis() + Math.max(0, expiresIn * 1000L - skewMs);

                if (accessToken == null || accessToken.isEmpty()) {
                    throw new IOException("Token missing in response");
                }
                Log.d(TAG, "Fetched Petfinder token, expires in ~" + expiresIn + "s");
            } catch (Exception e) {
                // Ensure state is cleared so next attempt can try again
                accessToken = null;
                expiryEpochMillis = 0L;
                if (e instanceof IOException) throw (IOException) e;
                throw new IOException(e);
            }
        }
    }
}
