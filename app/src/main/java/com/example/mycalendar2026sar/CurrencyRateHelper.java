package com.example.mycalendar2026sar;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class CurrencyRateHelper {

    private static final String PREFS_RATES = "RatesCachePrefs";
    private static final Map<String, Double> DEFAULT_RATES = new HashMap<>();

    static {
        DEFAULT_RATES.put("USD", 1.0);
        DEFAULT_RATES.put("EUR", 0.9148);
        DEFAULT_RATES.put("GBP", 0.7850);
        DEFAULT_RATES.put("JPY", 150.25);
        DEFAULT_RATES.put("CAD", 1.3520);
        DEFAULT_RATES.put("AUD", 1.5180);
        DEFAULT_RATES.put("CHF", 0.8840);
        DEFAULT_RATES.put("CNY", 7.2300);
        DEFAULT_RATES.put("INR", 83.1200);
        DEFAULT_RATES.put("AED", 3.6725);
        DEFAULT_RATES.put("SAR", 3.7500);
        DEFAULT_RATES.put("EGP", 48.5000);
        DEFAULT_RATES.put("LBP", 89500.0);
    }

    public static Map<String, Double> getCachedRates(Context context) {
        Map<String, Double> ratesMap = new HashMap<>(DEFAULT_RATES);
        if (context == null) return ratesMap;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_RATES, Context.MODE_PRIVATE);
        String jsonStr = prefs.getString("cached_rates_json", null);
        if (jsonStr != null) {
            try {
                JSONObject obj = new JSONObject(jsonStr);
                if (obj.has("rates")) {
                    JSONObject ratesObj = obj.getJSONObject("rates");
                    Iterator<String> keys = ratesObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        double val = ratesObj.getDouble(key);
                        ratesMap.put(key.toUpperCase(Locale.US), val);
                    }
                }
            } catch (Exception ignored) {}
        }
        return ratesMap;
    }

    public static long getLastUpdatedTime(Context context) {
        if (context == null) return 0;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_RATES, Context.MODE_PRIVATE);
        return prefs.getLong("last_updated_time", 0);
    }

    public static double convertAmount(double amount, String fromCurr, String toCurr, Map<String, Double> ratesMap) {
        if (fromCurr == null || toCurr == null || fromCurr.equalsIgnoreCase(toCurr)) {
            return amount;
        }
        double rateFrom = ratesMap.getOrDefault(fromCurr.toUpperCase(Locale.US), 1.0);
        double rateTo = ratesMap.getOrDefault(toCurr.toUpperCase(Locale.US), 1.0);

        if (rateFrom == 0) rateFrom = 1.0;
        double inUsd = amount / rateFrom;
        return inUsd * rateTo;
    }

    public static void fetchLiveRates(Context context, Runnable onComplete) {
        new Thread(() -> {
            try {
                URL url = new URL("https://open.er-api.com/v6/latest/USD");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    String jsonStr = sb.toString();
                    long now = System.currentTimeMillis();

                    if (context != null) {
                        context.getSharedPreferences(PREFS_RATES, Context.MODE_PRIVATE)
                                .edit()
                                .putString("cached_rates_json", jsonStr)
                                .putLong("last_updated_time", now)
                                .apply();
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {
            } finally {
                if (onComplete != null && context instanceof Activity) {
                    ((Activity) context).runOnUiThread(onComplete);
                }
            }
        }).start();
    }
}
