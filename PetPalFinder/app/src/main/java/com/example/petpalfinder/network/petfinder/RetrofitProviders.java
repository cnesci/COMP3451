package com.example.petpalfinder.network.petfinder;

import android.content.Context;

import com.example.petpalfinder.BuildConfig;
import com.example.petpalfinder.model.petfinder.TokenResponse;
import com.example.petpalfinder.network.auth.TokenManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitProviders {
    private static final String BASE = "https://api.petfinder.com/v2/";

    private final TokenManager tokenManager;
    private final PetfinderAuthService authService;
    private final Retrofit apiRetrofit;

    public RetrofitProviders(Context ctx) {
        tokenManager = new TokenManager(ctx.getApplicationContext());

        HttpLoggingInterceptor logs = new HttpLoggingInterceptor();
        logs.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient authClient = new OkHttpClient.Builder()
                .addInterceptor(logs)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        Retrofit authRetrofit = new Retrofit.Builder()
                .baseUrl(BASE)
                .addConverterFactory(GsonConverterFactory.create())
                .client(authClient)
                .build();

        authService = authRetrofit.create(PetfinderAuthService.class);

        Interceptor bearer = chain -> {
            String token = tokenManager.getCachedValidToken();
            if (token == null) token = fetchAndStoreToken();

            Request req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer " + token)
                    .build();

            Response res = chain.proceed(req);

            if (res.code() == 401) {
                res.close();
                token = fetchAndStoreToken();
                Request retry = chain.request().newBuilder()
                        .removeHeader("Authorization")
                        .addHeader("Authorization", "Bearer " + token)
                        .build();
                return chain.proceed(retry);
            }
            return res;
        };

        OkHttpClient apiClient = new OkHttpClient.Builder()
                .addInterceptor(bearer)
                .addInterceptor(logs)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        apiRetrofit = new Retrofit.Builder()
                .baseUrl(BASE)
                .addConverterFactory(GsonConverterFactory.create())
                .client(apiClient)
                .build();
    }

    private synchronized String fetchAndStoreToken() throws IOException {
        Call<TokenResponse> call = authService.getToken(
                "client_credentials",
                BuildConfig.PETFINDER_CLIENT_ID,
                BuildConfig.PETFINDER_CLIENT_SECRET
        );
        retrofit2.Response<TokenResponse> r = call.execute();
        if (!r.isSuccessful() || r.body() == null) {
            throw new IOException("Token fetch failed: HTTP " + r.code());
        }
        tokenManager.save(r.body());
        return r.body().access_token;
    }

    public PetfinderApiService api() {
        return apiRetrofit.create(PetfinderApiService.class);
    }
}
