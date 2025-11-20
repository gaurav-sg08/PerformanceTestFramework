package com.performanceTest.tests;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.performanceTest.Inputreader.ExcelReader;
import com.performanceTest.authenticationmanager.AuthTokenManager;
import com.performanceTest.jengine.JMeterEngine;
import com.performanceTest.listeners.ReportGeneratorHelper;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static com.performanceTest.utilities.HelpingMethods.deleteDirectory;

public class PerformanceTestRunner {

    @BeforeSuite
    public void setup() throws Exception {
        // Use relative report path so it works both locally and when Docker mounts folders
        deleteDirectory(new File("report"));

        // Do not set JMeter home or load properties here.
        // JMeterEngine will handle initialization for embedded mode and Docker mode.
    }

    @Test
    public void testRunPerformanceTest() throws Exception {
        String resultFile = "report/result.jtl";
        JMeterEngine engine = new JMeterEngine(resultFile);

        String excelPath = "resources/APITestData.xlsx";
        Map<String, List<Map<String, String>>> stepsMap = ExcelReader.readTestSteps(excelPath);

        List<Map<String, String>> loginSteps = stepsMap.get("loginSteps");
        List<Map<String, String>> testSteps = stepsMap.get("testSteps");

        Map<String, String> token = AuthTokenManager.generateAuthToken(loginSteps);
        System.out.println("Final X-Auth Token: " + token);

        for (Map<String, String> step : testSteps) {
            try {
                String name = step.get("name");
                String url = step.get("url");
                String method = step.get("method");
                String payload = step.getOrDefault("payload", "");
                int threads = (int) Double.parseDouble(step.get("threads"));
                int loop = (int) Double.parseDouble(step.get("loopCount"));
                int rampUPperiod = (int) Double.parseDouble(step.get("RampUpPeriod"));
                int maxResponseTime = (int) Double.parseDouble(step.get("MaxResponseTime(ms)"));
                int statusCode = (int) Double.parseDouble(step.get("StatusCode"));

                Map<String, String> headers = new HashMap<>();
                String headerStr = step.get("header");
                if (headerStr != null && !headerStr.isEmpty()) {
                    JsonObject headerJson = JsonParser.parseString(headerStr).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : headerJson.entrySet()) {
                        String headerKey = entry.getKey();
                        String headerValue = entry.getValue().getAsString();

                        if (headerValue.contains("{cookies.")) {
                            int startIndex = headerValue.indexOf("{cookies.") + 9;
                            int endIndex = headerValue.indexOf("}", startIndex);
                            String cookieKey = headerValue.substring(startIndex, endIndex);
                            headerValue = headerValue.replace("{cookies." + cookieKey + "}", AuthTokenManager.getAuthSessionData().getOrDefault(cookieKey, ""));
                        }

                        headers.put(headerKey, headerValue);
                    }
                }

                headers.put("x-auth-token", token.get("x-auth-token"));
                headers.put("Content-Type", "application/json");

                System.out.println("🔄 Executing step: " + name);
                engine.addHttpSampler(name, url, method, payload, headers,
                        threads, loop, rampUPperiod,
                        statusCode, maxResponseTime);

            } catch (Exception e) {
                System.err.println("Error in test step. Skipping to next...");
                e.printStackTrace();
            }
        }

        // This will either run embedded JMeter (default) OR Docker JMeter if env var is set.
        engine.runTestPlan();

        // Unchanged: generate HTML from the JTL
        ReportGeneratorHelper.generateHtmlReport(resultFile);
    }
}
