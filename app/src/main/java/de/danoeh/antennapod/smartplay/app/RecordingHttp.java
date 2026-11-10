package de.danoeh.antennapod.smartplay.app;

import android.util.Log;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import okhttp3.OkHttpClient;

final class RecordingHttp {
    private static final String TAG = "Recordings";
    private static final ThreadLocal<String> USER_AGENT = new ThreadLocal<>();

    private RecordingHttp() {
    }

    static void begin(String userAgent) {
        USER_AGENT.set(userAgent);
        Log.d(TAG, "Recording requests send User-Agent: " + userAgent);
    }

    static void end() {
        USER_AGENT.remove();
    }

    static OkHttpClient client() {
        OkHttpClient base = AntennapodHttpClient.getHttpClient();
        String userAgent = USER_AGENT.get();
        if (userAgent == null) {
            return base;
        }
        return base.newBuilder()
                .addNetworkInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", userAgent).build()))
                .build();
    }
}
