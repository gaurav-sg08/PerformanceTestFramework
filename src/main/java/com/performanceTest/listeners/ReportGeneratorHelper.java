package com.performanceTest.listeners;

import org.apache.jmeter.report.dashboard.ReportGenerator;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.util.JMeterUtils;

import java.io.File;

public class ReportGeneratorHelper {

    public static void generateHtmlReport(String jtlPath) {
        try {
            String jmeterHome = "C:\\Users\\GauravSinghKauda\\Pictures\\Screenshots\\PerformanceTestFramework\\jmeter";
            File jmeterProps = new File(jmeterHome + "/bin/jmeter.properties");
            JMeterUtils.setJMeterHome(jmeterHome);
            JMeterUtils.loadJMeterProperties(jmeterProps.getPath());
            JMeterUtils.initLocale();
            System.setProperty("jmeter.reportgenerator.outputdir", "C:\\Users\\GauravSinghKauda\\Pictures\\Screenshots\\PerformanceTestFramework\\report");
            SaveService.loadProperties();
            ReportGenerator reportGenerator = new ReportGenerator(jtlPath, null);
            reportGenerator.generate();

        } catch (Exception e) {
            System.err.println("Report generation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
