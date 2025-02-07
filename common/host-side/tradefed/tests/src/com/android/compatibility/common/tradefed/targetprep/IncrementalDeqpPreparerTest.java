/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.compatibility.common.tradefed.targetprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link IncrementalDeqpPreparer}. */
@RunWith(JUnit4.class)
public class IncrementalDeqpPreparerTest {
    private static final String DEVICE_FINGERPRINT =
            "generic/aosp_cf_x86_64_phone/vsoc_x86_64:S/AOSP"
                    + ".MASTER/7363321:userdebug/test-keys";

    private IncrementalDeqpPreparer mPreparer;
    private ITestDevice mMockDevice;
    private OptionSetter mPreparerSetter = null;

    @Before
    public void setUp() throws Exception {
        mPreparer = new IncrementalDeqpPreparer();
        mMockDevice = mock(ITestDevice.class);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testRunIncrementalDeqp() throws Exception {
        File resultDir = FileUtil.createTempDir("result");
        InputStream zipStream =
                getClass().getResourceAsStream("/testdata/current_build_target-files.zip");
        File zipFile = FileUtil.createTempFile("targetFile", ".zip");
        try {
            FileUtil.writeToFile(zipStream, zipFile);
            mPreparerSetter = new OptionSetter(mPreparer);
            mPreparerSetter.setOptionValue(
                    "incremental-deqp-preparer:current-build", zipFile.getAbsolutePath());
            IBuildInfo mMockBuildInfo = new BuildInfo();
            IInvocationContext mMockContext = new InvocationContext();
            mMockContext.addDeviceBuildInfo("build", mMockBuildInfo);
            mMockContext.addAllocatedDevice("device", mMockDevice);
            File deviceInfoDir = new File(resultDir, "device-info-files");
            deviceInfoDir.mkdir();
            CompatibilityBuildHelper mMockBuildHelper =
                    new CompatibilityBuildHelper(mMockBuildInfo) {
                        @Override
                        public File getResultDir() {
                            return resultDir;
                        }
                    };
            InputStream perfDumpStream = getClass().getResourceAsStream("/testdata/perf-dump.txt");
            File dumpFile = FileUtil.createTempFile("parseDump", "perf-dump.txt");
            FileUtil.writeToFile(perfDumpStream, dumpFile);
            when(mMockDevice.pullFile(endsWith("-perf-dump.txt")))
                    .thenReturn(dumpFile, null, null, null);
            when(mMockDevice.getProperty("ro.build.fingerprint")).thenReturn(DEVICE_FINGERPRINT);

            File incrementalDeqpReport =
                    new File(deviceInfoDir, IncrementalDeqpPreparer.INCREMENTAL_DEQP_REPORT_NAME);
            assertFalse(incrementalDeqpReport.exists());
            mPreparer.runIncrementalDeqp(
                    mMockContext,
                    mMockDevice,
                    mMockBuildHelper,
                    IncrementalDeqpPreparer.RunMode.LIGHTWEIGHT_RUN);
            assertTrue(
                    mMockBuildInfo
                            .getBuildAttributes()
                            .containsKey(IncrementalDeqpPreparer.INCREMENTAL_DEQP_ATTRIBUTE_NAME));
            assertTrue(incrementalDeqpReport.exists());
        } finally {
            FileUtil.recursiveDelete(resultDir);
            FileUtil.deleteFile(zipFile);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testRunIncrementalDeqp_skipPreparerWhenReportExists() throws Exception {
        File resultDir = FileUtil.createTempDir("result");
        InputStream reportStream =
                getClass()
                        .getResourceAsStream("/testdata/IncrementalCtsDeviceInfo.deviceinfo.json");
        try {
            IBuildInfo mMockBuildInfo = new BuildInfo();
            IInvocationContext mMockContext = new InvocationContext();
            mMockContext.addDeviceBuildInfo("build", mMockBuildInfo);
            mMockContext.addAllocatedDevice("device", mMockDevice);
            File deviceInfoDir = new File(resultDir, "device-info-files");
            deviceInfoDir.mkdir();
            File report =
                    new File(deviceInfoDir, IncrementalDeqpPreparer.INCREMENTAL_DEQP_REPORT_NAME);
            report.createNewFile();
            FileUtil.writeToFile(reportStream, report);
            CompatibilityBuildHelper mMockBuildHelper =
                    new CompatibilityBuildHelper(mMockBuildInfo) {
                        @Override
                        public File getResultDir() {
                            return resultDir;
                        }
                    };

            mPreparer.runIncrementalDeqp(
                    mMockContext,
                    mMockDevice,
                    mMockBuildHelper,
                    IncrementalDeqpPreparer.RunMode.LIGHTWEIGHT_RUN);
            assertTrue(
                    mMockBuildInfo
                            .getBuildAttributes()
                            .containsKey(IncrementalDeqpPreparer.INCREMENTAL_DEQP_ATTRIBUTE_NAME));
        } finally {
            FileUtil.recursiveDelete(resultDir);
        }
    }

    @Test
    public void testParseDump() throws Exception {
        InputStream inputStream = getClass().getResourceAsStream("/testdata/perf-dump.txt");
        File dumpFile = FileUtil.createTempFile("parseDump", ".txt");
        try {
            FileUtil.writeToFile(inputStream, dumpFile);
            Set<String> dependency = mPreparer.parseDump(dumpFile);
            Set<String> expect = new HashSet<>();
            expect.add("/system/deqp_dependency_file_a.so");
            expect.add("/vendor/deqp_dependency_file_b.so");
            assertEquals(dependency, expect);
        } finally {
            FileUtil.deleteFile(dumpFile);
        }
    }

    @Test
    public void testGetTargetFileHash() throws IOException, TargetSetupError {
        Set<String> fileSet =
                new HashSet<>(
                        Arrays.asList(
                                "/system/deqp_dependency_file_a.so",
                                "/vendor/deqp_dependency_file_b.so",
                                "/vendor/file_not_exists.so"));
        // current_build_target-files.zip is a stripped down version of the target-files.zip
        // generated from the android build system, with a few added mocked target files for
        // testing.
        InputStream zipStream =
                getClass().getResourceAsStream("/testdata/current_build_target-files.zip");
        File zipFile = FileUtil.createTempFile("targetFile", ".zip");
        try {
            FileUtil.writeToFile(zipStream, zipFile);
            Map<String, String> fileHashMap = mPreparer.getTargetFileHash(fileSet, zipFile);

            assertEquals(fileHashMap.size(), 2);
            assertEquals(
                    fileHashMap.get("/system/deqp_dependency_file_a.so"),
                    StreamUtil.calculateMd5(
                            new ByteArrayInputStream(
                                    "placeholder\nplaceholder\n"
                                            .getBytes(StandardCharsets.UTF_8))));
            assertEquals(
                    fileHashMap.get("/vendor/deqp_dependency_file_b.so"),
                    StreamUtil.calculateMd5(
                            new ByteArrayInputStream(
                                    ("placeholder\nplaceholder" + "\nplaceholder\n\n")
                                            .getBytes(StandardCharsets.UTF_8))));
        } finally {
            FileUtil.deleteFile(zipFile);
        }
    }

    @Test
    public void testValidateBuildFingerprint() throws Exception {
        // current_build_target-files.zip is a stripped down version of the target-files.zip
        // generated from the android build system, with a few added mocked target files for
        // testing.
        InputStream zipStream =
                getClass().getResourceAsStream("/testdata/current_build_target-files.zip");
        File zipFile = FileUtil.createTempFile("targetFile", ".zip");
        try {
            FileUtil.writeToFile(zipStream, zipFile);
            when(mMockDevice.getProperty("ro.build.fingerprint")).thenReturn(DEVICE_FINGERPRINT);

            mPreparer.validateBuildFingerprint(zipFile, mMockDevice);
        } finally {
            FileUtil.deleteFile(zipFile);
        }
    }

    @Test
    public void testValidateBuildFingerprint_fingerprintMismatch() throws Exception {
        InputStream zipStream =
                getClass().getResourceAsStream("/testdata/current_build_target-files.zip");
        File zipFile = FileUtil.createTempFile("targetFile", ".zip");
        try {
            FileUtil.writeToFile(zipStream, zipFile);
            when(mMockDevice.getProperty("ro.build.fingerprint"))
                    .thenReturn(DEVICE_FINGERPRINT + "modified");

            assertThrows(
                    TargetSetupError.class,
                    () -> mPreparer.validateBuildFingerprint(zipFile, mMockDevice));
        } finally {
            FileUtil.deleteFile(zipFile);
        }
    }
}
