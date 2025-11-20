package com.performanceTest.jengine;

import com.performanceTest.authenticationmanager.AuthTokenManager;
import com.performanceTest.utilities.AssertionHelper;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.protocol.http.util.HTTPArgument;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.SampleSaveConfiguration;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.ListedHashTree;
import org.apache.jmeter.save.SaveService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.lang.ProcessBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class JMeterEngine {

    private final StandardJMeterEngine jmeter;
    private final ListedHashTree testPlanTree;
    private final ResultCollector fileCollector;
    private final ConsoleResultPrinter consolePrinter;
    private final String jtlFilePath;

    public JMeterEngine(String resultFilePath) throws Exception {
        System.out.println("Initializing JMeter Engine...");

        this.jtlFilePath = resultFilePath;

        // For embedded mode we expect jmeter folder under project root
        String jmeterHome = System.getProperty("user.dir") + File.separator + "jmeter";
        File jmeterProperties = new File(jmeterHome + File.separator + "bin" + File.separator + "jmeter.properties");

        if (!jmeterProperties.exists()) {
            // In Docker-only environments, embedded mode won't be used; still keep validation for local runs.
            System.out.println("Warning: JMeter properties not found at: " + jmeterProperties.getAbsolutePath() + " (if running docker mode this is ok)");
        } else {
            JMeterUtils.setJMeterHome(jmeterHome);
            JMeterUtils.loadJMeterProperties(jmeterProperties.getAbsolutePath());
            JMeterUtils.initLocale();
        }

        jmeter = new StandardJMeterEngine();
        TestPlan testPlan = new TestPlan("Unified Test Plan");
        testPlan.setProperty(TestElement.TEST_CLASS, TestPlan.class.getName());
        testPlan.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.testelement.gui.TestPlanGui");

        testPlanTree = new ListedHashTree();
        ListedHashTree testPlanChildTree = testPlanTree.add(testPlan);

        SampleSaveConfiguration saveConfig = new SampleSaveConfiguration();
        saveConfig.setAsXml(false);
        saveConfig.setTime(true);
        saveConfig.setLatency(true);
        saveConfig.setTimestamp(true);
        saveConfig.setLabel(true);
        saveConfig.setCode(true);
        saveConfig.setSuccess(true);
        saveConfig.setBytes(true);
        saveConfig.setSentBytes(true);
        saveConfig.setThreadName(true);
        saveConfig.setConnectTime(true);
        saveConfig.setIdleTime(true);
        saveConfig.setSampleCount(true);
        saveConfig.setResponseHeaders(true);
        saveConfig.setRequestHeaders(true);
        saveConfig.setResponseData(true);
        saveConfig.setDataType(true);
        saveConfig.setSamplerData(true);

        fileCollector = new ResultCollector();
        fileCollector.setSaveConfig(saveConfig);
        fileCollector.setFilename(resultFilePath);
        testPlanChildTree.add(fileCollector);

        consolePrinter = new ConsoleResultPrinter();
        consolePrinter.setSaveConfig(saveConfig);
        testPlanChildTree.add(consolePrinter);
    }

    public void addHttpSampler(String name, String url, String method, String payload, Map<String, String> headers,
                               int threads, int loopCount, int rampUpSeconds,
                               int expectedStatusCode, int maxResponseTimeMs) {

        System.out.println(" Adding " + name + " sampler ");

        HTTPSamplerProxy sampler = new HTTPSamplerProxy();

        URI u = URI.create(url);
        String scheme = u.getScheme() == null ? "https" : u.getScheme();
        String host = u.getHost();
        int port = u.getPort();
        if (port == -1) {
            port = "https".equalsIgnoreCase(scheme) ? 443 : 80;
        }
        String fullPath = (u.getRawPath() == null || u.getRawPath().isEmpty()) ? "/" : u.getRawPath();
        if (u.getRawQuery() != null && !u.getRawQuery().isEmpty()) {
            fullPath += "?" + u.getRawQuery();
        }

        sampler.setProtocol(scheme);
        sampler.setDomain(host);
        sampler.setPort(port);
        sampler.setPath(fullPath);
        sampler.setMethod(method.toUpperCase());
        sampler.setName(name);

        boolean isWrite = method.equalsIgnoreCase("POST")
                || method.equalsIgnoreCase("PUT")
                || method.equalsIgnoreCase("PATCH")
                || method.equalsIgnoreCase("DELETE");

        sampler.setFollowRedirects(!isWrite);
        sampler.setAutoRedirects(false);
        sampler.setImageParser(false);

        sampler.setUseKeepAlive(true);
        sampler.setConnectTimeout("10000");
        sampler.setResponseTimeout("20000");
        sampler.setContentEncoding("UTF-8");

        if (isWrite) {
            String resolvedPayload = (payload == null) ? "" : payload;
            resolvedPayload = resolvedPayload.trim();
            // below code is to strip UTF-8 BOM if any
            if (!resolvedPayload.isEmpty() && resolvedPayload.charAt(0) == '\uFEFF') {
                resolvedPayload = resolvedPayload.substring(1);
            }

            /*Here I am Clearing all arguments and adding it manually, because at the time of running test for
            put/post method payload issue happening i.e. fault string.
            setting metadata to prevent no name:value as our apis are preferred to take raw data

            */
            sampler.getArguments().clear();
            HTTPArgument arg = new HTTPArgument();
            arg.setAlwaysEncoded(false);
            arg.setMetaData("");
            arg.setName("");
            arg.setValue(resolvedPayload);
            sampler.getArguments().addArgument(arg);
            sampler.setPostBodyRaw(true);
            sampler.setDoMultipartPost(false);
        }

        sampler.setProperty(TestElement.TEST_CLASS, HTTPSamplerProxy.class.getName());
        sampler.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.protocol.http.control.gui.HttpTestSampleGui");

        Map<String, String> resolvedHeaders = resolvePlaceholders(headers);
        HeaderManager headerManager = new HeaderManager();

        if (isWrite) {
            resolvedHeaders.keySet().removeIf(k -> k.equalsIgnoreCase("Content-Type"));
            headerManager.add(new Header("Content-Type", "application/json"));
        } else {
            resolvedHeaders.keySet().removeIf(k -> k.equalsIgnoreCase("Content-Type"));
        }

        for (Map.Entry<String, String> entry : resolvedHeaders.entrySet()) {
            headerManager.add(new Header(entry.getKey(), entry.getValue()));
        }
        headerManager.setName("Header Manager");
        headerManager.setProperty(TestElement.TEST_CLASS, HeaderManager.class.getName());
        headerManager.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.protocol.http.gui.HeaderPanel");

        LoopController loopController = new LoopController();
        loopController.setLoops(loopCount);
        loopController.setContinueForever(false);
        loopController.setFirst(true);
        loopController.setProperty(TestElement.TEST_CLASS, LoopController.class.getName());
        loopController.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.control.gui.LoopControlPanel");
        loopController.initialize();

        ThreadGroup threadGroup = new ThreadGroup();
        threadGroup.setName("TG - " + name);
        threadGroup.setNumThreads(threads);
        threadGroup.setRampUp(rampUpSeconds);
        threadGroup.setSamplerController(loopController);
        threadGroup.setProperty(TestElement.TEST_CLASS, ThreadGroup.class.getName());
        threadGroup.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.threads.gui.ThreadGroupGui");

        HashTree testPlanChildTree = testPlanTree.get(testPlanTree.getArray()[0]);
        ListedHashTree threadGroupTree = (ListedHashTree) testPlanChildTree.add(threadGroup);
        ListedHashTree samplerTree = threadGroupTree.add(sampler);

        samplerTree.add(headerManager);
        samplerTree.add(AssertionHelper.createStatusCodeAssertion(expectedStatusCode));
        samplerTree.add(AssertionHelper.createDurationAssertion(maxResponseTimeMs));

    }

    /**
     * Export the built test plan (testPlanTree) to a JMX file so an external JMeter CLI (or containerized JMeter) can run it.
     */
    public void exportTestPlanToJmx(String jmxFilePath) throws IOException {
        try {
            File jmxFile = new File(jmxFilePath);
            jmxFile.getParentFile().mkdirs();
            try (OutputStream out = new FileOutputStream(jmxFile)) {
                // Save the subtree representing the TestPlan
                SaveService.saveTree(testPlanTree.getTree(testPlanTree.getArray()[0]), out);
                out.flush();
            }
            System.out.println("Exported test plan to JMX: " + jmxFilePath);
        } catch (Exception e) {
            throw new IOException("Failed to export test plan to JMX: " + e.getMessage(), e);
        }
    }

    /**
     * Run JMeter inside Docker. Expects the host directory containing the exported JMX to be mounted into the container.
     * jmxHostPath: absolute path on the host to the exported .jmx (e.g., /home/runner/.../generated/generated-testplan.jmx)
     * resultHostPath: absolute path on the host where JTL should be written (e.g., /home/runner/.../report/result.jtl)
     * jmeterImage: docker image to use (e.g., jmeter-custom-image:latest)
     */
    public void runTestPlanInDocker(String jmxHostPath, String resultHostPath, String jmeterImage) throws Exception {
        File jmxFile = new File(jmxHostPath);
        File resultFile = new File(resultHostPath);

        String hostDir = jmxFile.getParentFile().getAbsolutePath(); // directory to mount
        String containerJmx = "/tests/" + jmxFile.getName();
        String containerResult = "/tests/" + resultFile.getName();

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        // mount the host directory containing jmx & result path
        cmd.add("-v");
        cmd.add(hostDir + ":/tests");
        // set working dir inside container optionally
        cmd.add("-w");
        cmd.add("/tests");
        // image
        cmd.add(jmeterImage);
        // JMeter CLI args
        cmd.add("-n");
        cmd.add("-t");
        cmd.add(containerJmx);
        cmd.add("-l");
        cmd.add(containerResult);

        System.out.println("Executing Docker JMeter: " + String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("Docker JMeter exited with code: " + exit);
        }

        System.out.println("Docker JMeter run complete. Result at: " + resultHostPath);
    }

    public void runTestPlan() throws Exception {
        String useDocker = System.getenv().getOrDefault("USE_DOCKER_JMETER", "false");
        if (useDocker.equalsIgnoreCase("true")) {
            System.out.println("Running test plan in DOCKER mode.");

            // Prepare host-side paths (project root assumed to be user.dir)
            String projectRoot = System.getProperty("user.dir");
            String generatedDir = projectRoot + File.separator + "generated"; // we create a generated folder
            new File(generatedDir).mkdirs();

            // Export JMX into generated folder
            String jmxName = "generated-testplan.jmx";
            String jmxHostPath = generatedDir + File.separator + jmxName;
            exportTestPlanToJmx(jmxHostPath);

            // Ensure result folder exists
            File resultFile = new File(this.jtlFilePath);
            resultFile.getParentFile().mkdirs();
            String resultHostPath = projectRoot + File.separator + this.jtlFilePath; // e.g., report/result.jtl

            // Docker image name from env or default
            String jmeterImage = System.getenv().getOrDefault("JMETER_DOCKER_IMAGE", "jmeter-custom-image:latest");

            // Run Docker
            runTestPlanInDocker(jmxHostPath, resultHostPath, jmeterImage);

            // After Docker run, results are available at resultHostPath on host
            System.out.println("Result file available at: " + resultHostPath);
        } else {
            // existing embedded behavior — unchanged
            System.out.println(" Tie your seatbelt we are running test plan ");
            jmeter.configure(testPlanTree);
            jmeter.run();

            while (jmeter.isActive()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            System.out.println("Test run complete.");
            jmeter.reset();
        }
    }

    private static Map<String, String> resolvePlaceholders(Map<String, String> headers) {
        Map<String, String> resolved = new HashMap<>();
        if (headers == null) return resolved;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            value = value
                    .replace("{cookies.access_token}", getAccessToken())
                    .replace("{cookies.x-auth-token}", getXAuthToken())
                    .replace("{sessionToken}", AuthTokenManager.getAuthSessionData().getOrDefault("sessionToken", ""));
            resolved.put(entry.getKey(), value);
        }
        return resolved;
    }

    private static String getAccessToken() {
        return AuthTokenManager.getAuthSessionData().getOrDefault("access_token", "");
    }

    private static String getXAuthToken() {
        return AuthTokenManager.getAuthSessionData().getOrDefault("x-auth-token", "");
    }

    public static class ConsoleResultPrinter extends ResultCollector {
        @Override
        public void sampleOccurred(SampleEvent event) {
            SampleResult result = event.getResult();
            System.out.println("==== Sample Result for: " + result.getSampleLabel() + " ====");
            System.out.println("Status Code: " + result.getResponseCode());
            // print request headers + exact request body that was sent
//            System.out.println("--- Request Headers ---");
//            System.out.println(result.getRequestHeaders());
//            System.out.println("--- Sampler Data (request line + body) ---");
//            System.out.println(result.getSamplerData());
//            System.out.println("------ Response Body ------");
//            System.out.println(result.getResponseDataAsString());
//            System.out.println("==========================================================");
            super.sampleOccurred(event);
        }
    }
}
