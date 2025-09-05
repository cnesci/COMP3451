package com.example.petpalfinder.network.auth;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import com.example.petpalfinder.model.petfinder.TokenResponse;

public class TokenManager {
    private static final String PREF = "pf_token_store";
    private static final String KEY_ACCESS = "access";
    private static final String KEY_EXP_MS = "exp_ms";
    private static final long SAFETY_WINDOW_MS = 60_000;

    private final SharedPreferences prefs;

    public TokenManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public synchronized @Nullable String getCachedValidToken() {
        long exp = prefs.getLong(KEY_EXP_MS, 0);
        if (System.currentTimeMillis() < exp - SAFETY_WINDOW_MS) {
            String tok = prefs.getString(KEY_ACCESS, null);
            if (tok != null && !tok.isEmpty()) return tok;
        }
        return null;
    }

    public synchronized void save(TokenResponse t) {
        long expAt = System.currentTimeMillis() + (t.expires_in * 1000L);
        prefs.edit().putString(KEY_ACCESS, t.access_token).putLong(KEY_EXP_MS, expAt).apply();
    }

    public synchronized void clear() { prefs.edit().clear().apply(); }
}
