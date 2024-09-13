/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.compatibility.common.tradefed.result.suite;

import static com.android.compatibility.common.tradefed.result.suite.InteractiveResultReporter.CASE_TAG;
import static com.android.compatibility.common.tradefed.result.suite.InteractiveResultReporter.DEFAULT_MODULE_NAME;
import static com.android.compatibility.common.tradefed.result.suite.InteractiveResultReporter.ENCODING;
import static com.android.compatibility.common.tradefed.result.suite.InteractiveResultReporter.MODULE_TAG;
import static com.android.compatibility.common.tradefed.result.suite.InteractiveResultReporter.NS;
import static com.android.compatibility.common.tradefed.result.suite.InteractiveResultReporter.SCREENSHOTS_DIR_NAME;
import static com.android.compatibility.common.tradefed.result.suite.InteractiveResultReporter.SCREENSHOTS_METADATA_FILE_NAME;
import static com.android.compatibility.common.tradefed.result.suite.InteractiveResultReporter.SCREENSHOT_TAG;
import static com.android.compatibility.common.tradefed.result.suite.InteractiveResultReporter.ScreenshotTagData;
import static com.android.compatibility.common.tradefed.result.suite.InteractiveResultReporter.TEST_TAG;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link InteractiveResultReporter}. */
@RunWith(JUnit4.class)
public final class InteractiveResultReporterTest {

    private static final String INTERACTIVE_STEP_1 = "VerifyAppMenuStep";
    private static final String INTERACTIVE_STEP_2 = "VerifyScreenshot";
    private static final String SCREENSHOT_SUFFIX = "123456789.png";
    private static final String TEST_CLASS_1 = "com.google.android.gts.SampleTest";
    private static final String TEST_CLASS_2 = "com.google.android.gts.SampleDeviceTest";
    private static final String TEST_CASE_1 = "testScreenshot";
    private static final String TEST_CASE_2 = "testDeviceScreenshot";
    private static final String SCREENSHOT_FILE_1 = "screenshot.png";
    private static final String SCREENSHOT_FILE_2 =
            String.format("%s__%s__%s", TEST_CLASS_1, INTERACTIVE_STEP_1, SCREENSHOT_SUFFIX);
    private static final String SCREENSHOT_FILE_3 =
            String.format(
                    "%s#%s__%s__%s",
                    TEST_CLASS_1, TEST_CASE_1, INTERACTIVE_STEP_1, SCREENSHOT_SUFFIX);
    private static final String SCREENSHOT_FILE_4 =
            String.format(
                    "%s#%s__%s__%s",
                    TEST_CLASS_1, TEST_CASE_1, INTERACTIVE_STEP_2, SCREENSHOT_SUFFIX);
    private static final String SCREENSHOT_FILE_5 =
            String.format(
                    "%s#%s__%s__%s",
                    TEST_CLASS_2, TEST_CASE_1, INTERACTIVE_STEP_1, SCREENSHOT_SUFFIX);
    private static final String SCREENSHOT_FILE_6 =
            String.format(
                    "%s#%s__%s__%s",
                    TEST_CLASS_2, TEST_CASE_2, INTERACTIVE_STEP_2, SCREENSHOT_SUFFIX);

    private DeviceBuildInfo mDeviceBuild;

    @After
    public void tearDown() throws Exception {
        if (mDeviceBuild != null) {
            FileUtil.recursiveDelete(new CompatibilityBuildHelper(mDeviceBuild).getRootDir());
        }
    }

    @Test
    public void invocationStarted_mResultDirInitialized() throws Exception {
        File resultDir = getFakeResultDir(false);
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, mDeviceBuild);
        InteractiveResultReporter resultReporter = new InteractiveResultReporter();

        resultReporter.invocationStarted(context);

        assertEquals(
                resultReporter.getScreenshotsMetadataFilePath().getAbsolutePath(),
                Paths.get(
                                resultDir.getAbsolutePath(),
                                SCREENSHOTS_DIR_NAME,
                                SCREENSHOTS_METADATA_FILE_NAME)
                        .toAbsolutePath()
                        .toString());
    }

    @Test
    public void invocationEnded_noScreenshotsDir_doesNothing() throws Exception {
        File resultDir = getFakeResultDir(false);
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, mDeviceBuild);
        InteractiveResultReporter resultReporter = new InteractiveResultReporter();

        resultReporter.invocationStarted(context);
        resultReporter.invocationEnded(1000);

        assertFalse(Files.exists(Paths.get(resultDir.getAbsolutePath(), SCREENSHOTS_DIR_NAME)));
    }

    @Test
    public void isScreenshotFile_byFileExtension() {
        assertTrue(InteractiveResultReporter.isScreenshotFile(Path.of("tmp/screenshot_1.png")));
        assertTrue(InteractiveResultReporter.isScreenshotFile(Path.of("screenshot_2.jpeg")));
        assertTrue(InteractiveResultReporter.isScreenshotFile(Path.of("../screenshot_3.jpg")));
        assertFalse(InteractiveResultReporter.isScreenshotFile(Path.of("screenshot_4")));
    }

    @Test
    public void genScreenshotsMetadataFile_verifyFileContent() throws Exception {
        File resultDir = getFakeResultDir(true);
        File screenshotsDir = new File(resultDir, SCREENSHOTS_DIR_NAME);
        String moduleName1 = "testModule1";
        String moduleName2 = "testModule2";
        prepareModuleDir(
                screenshotsDir,
                moduleName1 + "__x86",
                Arrays.asList(SCREENSHOT_FILE_3, SCREENSHOT_FILE_6));
        prepareModuleDir(screenshotsDir, moduleName2, Arrays.asList(SCREENSHOT_FILE_1));
        new File(screenshotsDir, SCREENSHOT_FILE_1).createNewFile();
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, mDeviceBuild);
        InteractiveResultReporter resultReporter = new InteractiveResultReporter();
        File screenshotMetadataFile = new File(screenshotsDir, SCREENSHOTS_METADATA_FILE_NAME);

        resultReporter.invocationStarted(context);
        resultReporter.genScreenshotsMetadataFile(screenshotMetadataFile);

        String xmlContent = FileUtil.readStringFromFile(screenshotMetadataFile);
        verifyModuleTags(xmlContent, Arrays.asList(moduleName1, moduleName2, DEFAULT_MODULE_NAME));
        verifyTestCaseTags(
                xmlContent, Arrays.asList(TEST_CLASS_1, TEST_CLASS_2, SCREENSHOT_FILE_1));
        verifyTestTags(xmlContent, Arrays.asList(TEST_CASE_1, TEST_CASE_2, SCREENSHOT_FILE_1));
        verifyScreenshotTags(
                xmlContent, Arrays.asList(SCREENSHOT_FILE_3, SCREENSHOT_FILE_6, SCREENSHOT_FILE_1));
    }

    private static void prepareModuleDir(
            File screenshotsDir, String moduleNameWithAbi, List<String> screenshotNames)
            throws IOException {
        File moduleDir = new File(screenshotsDir, moduleNameWithAbi);
        moduleDir.mkdirs();
        for (String screenshotName : screenshotNames) {
            new File(moduleDir, screenshotName).createNewFile();
        }
    }

    @Test
    public void addScreenshotTags_verifyXmlContent() throws Exception {
        XmlSerializer serializer = XmlPullParserFactory.newInstance().newSerializer();
        StringWriter writer = new StringWriter();
        serializer.setOutput(writer);
        serializer.startDocument(ENCODING, false);
        serializer.startTag(NS, MODULE_TAG);
        InteractiveResultReporter.addScreenshotTags(
                serializer, Arrays.asList(SCREENSHOT_FILE_1, SCREENSHOT_FILE_3, SCREENSHOT_FILE_6));
        serializer.endTag(NS, MODULE_TAG);
        serializer.endDocument();

        String xmlContent = writer.toString();

        verifyTestCaseTags(
                xmlContent, Arrays.asList(SCREENSHOT_FILE_1, TEST_CLASS_1, TEST_CLASS_2));
        verifyTestTags(xmlContent, Arrays.asList(SCREENSHOT_FILE_1, TEST_CASE_1, TEST_CASE_2));
        verifyScreenshotTags(
                xmlContent, Arrays.asList(SCREENSHOT_FILE_1, SCREENSHOT_FILE_3, SCREENSHOT_FILE_6));
    }

    @Test
    public void getScreenshotTagDatas_verifyResultSorted() {
        Map<String, Map<String, List<ScreenshotTagData>>> screenshotTagDatas =
                InteractiveResultReporter.getScreenshotTagDatas(
                        Arrays.asList(
                                SCREENSHOT_FILE_4,
                                SCREENSHOT_FILE_3,
                                SCREENSHOT_FILE_5,
                                SCREENSHOT_FILE_6));

        verifyKeys(screenshotTagDatas.keySet(), Arrays.asList(TEST_CLASS_2, TEST_CLASS_1));

        Map<String, List<ScreenshotTagData>> tagDataOfClass = screenshotTagDatas.get(TEST_CLASS_1);
        verifyKeys(tagDataOfClass.keySet(), Arrays.asList(TEST_CASE_1));
        verifyScreenshotTagDatas(
                tagDataOfClass.get(TEST_CASE_1),
                Arrays.asList(
                        new ScreenshotTagData(
                                TEST_CLASS_1, TEST_CASE_1, SCREENSHOT_FILE_4, INTERACTIVE_STEP_2),
                        new ScreenshotTagData(
                                TEST_CLASS_1, TEST_CASE_1, SCREENSHOT_FILE_3, INTERACTIVE_STEP_1)));

        tagDataOfClass = screenshotTagDatas.get(TEST_CLASS_2);
        verifyKeys(tagDataOfClass.keySet(), Arrays.asList(TEST_CASE_2, TEST_CASE_1));
        verifyScreenshotTagDatas(
                tagDataOfClass.get(TEST_CASE_1),
                Arrays.asList(
                        new ScreenshotTagData(
                                TEST_CLASS_2, TEST_CASE_1, SCREENSHOT_FILE_5, INTERACTIVE_STEP_1)));
        verifyScreenshotTagDatas(
                tagDataOfClass.get(TEST_CASE_2),
                Arrays.asList(
                        new ScreenshotTagData(
                                TEST_CLASS_2, TEST_CASE_2, SCREENSHOT_FILE_6, INTERACTIVE_STEP_2)));
    }

    @Test
    public void getScreenshotTagData_withoutStepInfo() {
        verifyScreenshotTagData(
                InteractiveResultReporter.getScreenshotTagData(SCREENSHOT_FILE_1),
                new ScreenshotTagData(
                        SCREENSHOT_FILE_1,
                        SCREENSHOT_FILE_1,
                        SCREENSHOT_FILE_1,
                        SCREENSHOT_FILE_1));
    }

    @Test
    public void getScreenshotTagData_withoutTestInfo() {
        verifyScreenshotTagData(
                InteractiveResultReporter.getScreenshotTagData(SCREENSHOT_FILE_2),
                new ScreenshotTagData(
                        TEST_CLASS_1, TEST_CLASS_1, SCREENSHOT_FILE_2, INTERACTIVE_STEP_1));
    }

    @Test
    public void getScreenshotTagData_withTestInfo() {
        verifyScreenshotTagData(
                InteractiveResultReporter.getScreenshotTagData(SCREENSHOT_FILE_3),
                new ScreenshotTagData(
                        TEST_CLASS_1, TEST_CASE_1, SCREENSHOT_FILE_3, INTERACTIVE_STEP_1));
    }

    private static void verifyModuleTags(String xmlContent, List<String> moduleNames) {
        for (String moduleName : moduleNames) {
            verifyXmlContent(xmlContent, MODULE_TAG, moduleName);
        }
    }

    private static void verifyTestCaseTags(String xmlContent, List<String> testCaseNames) {
        for (String testCaseName : testCaseNames) {
            verifyXmlContent(xmlContent, CASE_TAG, testCaseName);
        }
    }

    private static void verifyTestTags(String xmlContent, List<String> testNames) {
        for (String testName : testNames) {
            verifyXmlContent(xmlContent, TEST_TAG, testName);
        }
    }

    private static void verifyScreenshotTags(String xmlContent, List<String> screenshotNames) {
        for (String screenshotName : screenshotNames) {
            verifyXmlContent(xmlContent, SCREENSHOT_TAG, screenshotName);
        }
    }

    private static void verifyXmlContent(String xmlContent, String tagName, String nameAttr) {
        assertThat(xmlContent, containsString(String.format("<%s name=\"%s\"", tagName, nameAttr)));
    }

    private static void verifyKeys(Set<String> keys, List<String> expected) {
        int i = 0;
        for (String key : keys) {
            assertEquals(key, expected.get(i++));
        }
        assertEquals(i, expected.size());
    }

    private static void verifyScreenshotTagDatas(
            List<ScreenshotTagData> results, List<ScreenshotTagData> expected) {
        assertEquals(results.size(), expected.size());
        for (int i = 0; i < results.size(); i++) {
            verifyScreenshotTagData(results.get(i), expected.get(i));
        }
    }

    private static void verifyScreenshotTagData(
            ScreenshotTagData result, ScreenshotTagData expected) {
        assertEquals(result.mTestCaseName, expected.mTestCaseName);
        assertEquals(result.mTestName, expected.mTestName);
        assertEquals(result.mScreenshotName, expected.mScreenshotName);
        assertEquals(result.mScreenshotDescription, expected.mScreenshotDescription);
    }

    private File getFakeResultDir(boolean withScreenshotDir) throws IOException {
        mDeviceBuild = new DeviceBuildInfo();
        mDeviceBuild.addBuildAttribute(CompatibilityBuildHelper.SUITE_NAME, "CTS");
        File rootDir = FileUtil.createTempDir("cts-root-dir");
        new File(rootDir, "android-cts/results/").mkdirs();
        mDeviceBuild.addBuildAttribute(
                CompatibilityBuildHelper.ROOT_DIR, rootDir.getAbsolutePath());
        mDeviceBuild.addBuildAttribute(
                CompatibilityBuildHelper.START_TIME_MS, Long.toString(System.currentTimeMillis()));
        File resultDir = new CompatibilityBuildHelper(mDeviceBuild).getResultDir();
        resultDir.mkdirs();
        if (withScreenshotDir) {
            new File(resultDir, SCREENSHOTS_DIR_NAME).mkdirs();
        }
        return resultDir;
    }
}
