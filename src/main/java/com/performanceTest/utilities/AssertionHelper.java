package com.performanceTest.utilities;

import org.apache.jmeter.assertions.DurationAssertion;
import org.apache.jmeter.assertions.JSONPathAssertion;
import org.apache.jmeter.assertions.ResponseAssertion;
import org.apache.jmeter.testelement.TestElement;

public class AssertionHelper {

    public static DurationAssertion createDurationAssertion(int maxResponseTimeMs) {
        DurationAssertion assertion = new DurationAssertion();
        assertion.setName("Max Response Time Assertion");
        assertion.setProperty(TestElement.TEST_CLASS, DurationAssertion.class.getName());
        assertion.setProperty(TestElement.GUI_CLASS, "DurationAssertionGui");
        assertion.setAllowedDuration(maxResponseTimeMs);
        return assertion;
    }

    public static ResponseAssertion createStatusCodeAssertion(int expectedStatusCode) {
        ResponseAssertion assertion = new ResponseAssertion();
        assertion.setName("HTTP Status Code Assertion");
        assertion.setProperty(TestElement.TEST_CLASS, ResponseAssertion.class.getName());
        assertion.setProperty(TestElement.GUI_CLASS, "AssertionGui");
        assertion.setTestFieldResponseCode();
        assertion.setToEqualsType();
        assertion.setAssumeSuccess(false);
        assertion.addTestString(String.valueOf(expectedStatusCode));
        return assertion;
    }

    public static ResponseAssertion createBodyContainsAssertion(String expectedSubstring) {
        ResponseAssertion assertion = new ResponseAssertion();
        assertion.setName("Body Contains Assertion");
        assertion.setProperty(TestElement.TEST_CLASS, ResponseAssertion.class.getName());
        assertion.setProperty(TestElement.GUI_CLASS, "AssertionGui");
        assertion.setTestFieldResponseData();
        assertion.setToContainsType();
        assertion.setAssumeSuccess(false);
        assertion.addTestString(expectedSubstring);
        return assertion;
    }

    public static ResponseAssertion createRegexAssertion(String regexPattern) {
        ResponseAssertion assertion = new ResponseAssertion();
        assertion.setName("Regex Match Assertion");
        assertion.setProperty(TestElement.TEST_CLASS, ResponseAssertion.class.getName());
        assertion.setProperty(TestElement.GUI_CLASS, "AssertionGui");
        assertion.setTestFieldResponseData();
        assertion.setToMatchType();
        assertion.setAssumeSuccess(false);
        assertion.addTestString(regexPattern);
        return assertion;
    }

    public static JSONPathAssertion createJSONPathAssertion(String jsonPath, String expectedValue) {
        JSONPathAssertion jsonPathAssertion = new JSONPathAssertion();
        jsonPathAssertion.setName("JSONPath Assertion");
        jsonPathAssertion.setProperty(TestElement.TEST_CLASS, JSONPathAssertion.class.getName());
        jsonPathAssertion.setProperty(TestElement.GUI_CLASS, "JSONPathAssertionGui");
        jsonPathAssertion.setJsonPath(jsonPath);
        jsonPathAssertion.setExpectedValue(expectedValue);
        jsonPathAssertion.setJsonValidationBool(true);
        jsonPathAssertion.setInvert(false);
        jsonPathAssertion.setExpectNull(false);
        return jsonPathAssertion;
    }


}
