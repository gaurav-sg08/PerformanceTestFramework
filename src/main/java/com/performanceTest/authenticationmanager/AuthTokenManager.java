package com.performanceTest.authenticationmanager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.*;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthTokenManager {

    static String xAuthToken = "";
    static String sessionToken = "";
    static String authorization = "";

    static Map<String, String> authSessionData = new HashMap<>();

    public static Map<String, String> generateAuthToken(List<Map<String, String>> loginSteps) {
        CookieStore cookieStore = new BasicCookieStore();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .build()) {

            for (Map<String, String> step : loginSteps) {
                String method = step.getOrDefault("method", "POST").toUpperCase();
                String url = step.get("url");

                if (url.contains("{sessionToken}")) {
                    url = url.replace("{sessionToken}", sessionToken);
                    step.put("url", url);
                }

                HttpRequestBase request;

                switch (method) {
                    case "GET":
                        request = new HttpGet(url);
                        break;
                    case "POST":
                        HttpPost post = new HttpPost(url);
                        attachPayload(post, step);
                        request = post;
                        break;
                    case "PUT":
                        HttpPut put = new HttpPut(url);
                        attachPayload(put, step);
                        request = put;
                        break;
                    case "DELETE":
                        request = new HttpDelete(url);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported HTTP method: " + method);
                }

                attachHeaders(request, step);

                System.out.println("Executing Request: " + request);
                for (Header header : request.getAllHeaders()) {
                    System.out.println("Request Header -> " + header.getName() + ": " + header.getValue());
                }

                try (CloseableHttpResponse response = client.execute(request)) {
                    HttpEntity entity = response.getEntity();
                    String responseStr = EntityUtils.toString(entity, "UTF-8");

                    for (Cookie cookie : cookieStore.getCookies()) {
                        authSessionData.put(cookie.getName(), cookie.getValue());
                    }

                    System.out.println("Response: " + response.getStatusLine());
                    for (Header header : response.getAllHeaders()) {
                        System.out.println("Response Header -> " + header.getName() + ": " + header.getValue());

                        if (header.getName().equalsIgnoreCase("Authorization")) {
                            authorization = header.getValue();
                            authSessionData.put("authorization", authorization);
                        } else if (header.getName().equalsIgnoreCase("x-auth-token")) {
                            xAuthToken = header.getValue();
                            authSessionData.put("x-auth-token", xAuthToken);
                        }
                    }

                    if (!responseStr.contains("<!DOCTYPE html>")) {
                        parseAuthTokensFromJson(responseStr);
                    }

                    System.out.println("AuthSessionData so far: " + authSessionData);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return getAuthSessionData();
    }

    private static void attachPayload(HttpEntityEnclosingRequestBase request, Map<String, String> step) {
        String payload = step.get("payload");
        if (payload != null && !payload.isEmpty()) {
            for (Map.Entry<String, String> entry : authSessionData.entrySet()) {
                payload = payload.replace("{cookies." + entry.getKey() + "}", entry.getValue());
            }
            request.setEntity(new StringEntity(payload, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static void attachHeaders(HttpRequestBase request, Map<String, String> step) {
        String headers = step.get("header");
        if (headers != null && !headers.isEmpty()) {
            JsonObject headerJson = JsonParser.parseString(headers).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : headerJson.entrySet()) {
                String value = entry.getValue().getAsString();

                if (value.contains("{cookies.")) {
                    int startIndex = value.indexOf("{cookies.") + 9;
                    int endIndex = value.indexOf("}", startIndex);
                    String cookieKey = value.substring(startIndex, endIndex);
                    value = value.replace("{cookies." + cookieKey + "}", authSessionData.getOrDefault(cookieKey, ""));
                }

                request.setHeader(entry.getKey(), value);
            }
        }
    }

    private static void parseAuthTokensFromJson(String responseStr) {
        try {
            Gson gson = new Gson();
            Map<String, Object> responseMap = gson.fromJson(responseStr, Map.class);

            if (responseMap.containsKey("x-auth-token")) {
                xAuthToken = responseMap.get("x-auth-token").toString();
                authSessionData.put("x-auth-token", xAuthToken);
            }

            if (responseMap.containsKey("sessionToken")) {
                sessionToken = responseMap.get("sessionToken").toString();
                authSessionData.put("sessionToken", sessionToken);
            }

            if (responseMap.containsKey("authorization")) {
                authorization = responseMap.get("authorization").toString();
                authSessionData.put("authorization", authorization);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse JSON auth tokens: " + e.getMessage());
        }
    }

    public static Map<String, String> getAuthSessionData() {
        return authSessionData;
    }
}
