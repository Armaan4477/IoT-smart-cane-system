package com.an.wrist.api;

import android.util.Log;

import com.an.wrist.BuildConfig;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String TAG = "ApiClient";
    // Use the API base URL from BuildConfig
    // This will be set in the build.gradle.kts file
    private static Retrofit retrofit = null;
    
    public static Retrofit getClient() {
        if (retrofit == null) {
            Log.d(TAG, "Initializing Retrofit with base URL: " + BuildConfig.API_BASE_URL);
            
            // Create logging interceptor
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            
            // Create OkHttp client with interceptor
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(interceptor)
                    .build();
            
            // Build retrofit instance
            retrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.API_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
            
            Log.d(TAG, "Retrofit initialized successfully");
        }
        return retrofit;
    }
    
    // Get API service
    public static ApiService getApiService() {
        return getClient().create(ApiService.class);
    }
}