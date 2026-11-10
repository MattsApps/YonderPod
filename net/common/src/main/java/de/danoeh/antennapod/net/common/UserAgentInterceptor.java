package de.danoeh.antennapod.net.common;

import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;

public class UserAgentInterceptor implements Interceptor {
    public static String USER_AGENT = "AntennaPod/0.0.0";
    public static String YONDER_VERSION = "0.0.0";

    public static String liveRadioUserAgent() {
        return "Yonder/" + YONDER_VERSION + " (+https://mattsapps.github.io/yonder/)";
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        return chain.proceed(chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build());
    }
}
