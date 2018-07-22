/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.suite.SuiteResultHolder;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/**
 * Unit tests for {@link PreviousResultLoader}.
 */
@RunWith(JUnit4.class)
public class PreviousResultLoaderTest {

    private PreviousResultLoader mLoader;
    private IInvocationContext mContext;
    private File mRootDir;

    @Before
    public void setUp() throws Exception {
        mLoader = new PreviousResultLoader();
        OptionSetter setter = new OptionSetter(mLoader);
        setter.setOptionValue("retry", "0");
        mContext = new InvocationContext();
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mRootDir);
    }

    /**
     * Test the loader properly fails when the results are not loaded.
     */
    @Test
    public void testReloadTests_failed() throws Exception {
        mContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, createFakeBuild(""));
        try {
            mLoader.init(mContext);
            fail("Should have thrown an exception.");
        } catch (RuntimeException expected) {
            // expected
            assertEquals("Failed to load the previous results.", expected.getMessage());
        }
    }

    /**
     * Test that the loader can provide the results information back.
     */
    @Test
    public void testReloadTests() throws Exception {
        mContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME,
                createFakeBuild(createBasicResults()));
        mLoader.init(mContext);
        assertEquals("cts -m CtsGesture --skip-all-system-status-check", mLoader.getCommandLine());
        SuiteResultHolder results = mLoader.loadPreviousResults();
        assertEquals(2, results.totalModules);
    }

    private IBuildInfo createFakeBuild(String resultContent) throws Exception {
        DeviceBuildInfo build = new DeviceBuildInfo();
        build.addBuildAttribute(CompatibilityBuildHelper.SUITE_NAME, "CTS");
        mRootDir = FileUtil.createTempDir("cts-root-dir");
        new File(mRootDir, "android-cts/results").mkdirs();
        build.addBuildAttribute(CompatibilityBuildHelper.ROOT_DIR, mRootDir.getAbsolutePath());
        // Create fake result dir
        long time = System.currentTimeMillis();
        build.addBuildAttribute(CompatibilityBuildHelper.START_TIME_MS, Long.toString(time));
        new CompatibilityBuildHelper(build).getResultDir().mkdirs();
        // Populate a test_results.xml
        File testResult = new File(new CompatibilityBuildHelper(build).getResultDir(),
                "test_result.xml");
        testResult.createNewFile();
        FileUtil.writeToFile(resultContent, testResult);
        return build;
    }

    private String createBasicResults() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version='1.0' encoding='UTF-8' standalone='no' ?>\n");
        sb.append("<?xml-stylesheet type=\"text/xsl\" href=\"compatibility_result.xsl\"?>\n");
        sb.append("<Result start=\"1530218251501\" end=\"1530218261061\" "
                + "start_display=\"Thu Jun 28 13:37:31 PDT 2018\" "
                + "end_display=\"Thu Jun 28 13:37:41 PDT 2018\" "
                + "command_line_args=\"cts -m CtsGesture --skip-all-system-status-check\" "
                + "suite_name=\"CTS\" suite_version=\"9.0_r1\" "
                + "suite_plan=\"cts\" suite_build_number=\"8888\" report_version=\"5.0\" "
                + "devices=\"HT6570300047\"  >\n");
        sb.append("  <Build command_line_args=\"cts -m CtsGesture --skip-all-system-status-check\""
                + " build_reference_fingerprint=\"\" />\n");
        // Summary
        sb.append("  <Summary pass=\"0\" failed=\"0\" modules_done=\"2\" modules_total=\"2\" />\n");
        // Each module results
        sb.append("  <Module name=\"CtsGestureTestCases\" abi=\"arm64-v8a\" runtime=\"2776\" "
                + "done=\"true\" pass=\"0\" total_tests=\"0\" />\n");
        sb.append("  <Module name=\"CtsGestureTestCases\" abi=\"armeabi-v7a\" runtime=\"2776\" "
                + "done=\"true\" pass=\"0\" total_tests=\"0\" />\n");
        // End
        sb.append("</Result>");
        return sb.toString();
    }
}
