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
import static org.junit.Assert.assertTrue;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.util.FileUtil;

import com.google.common.hash.BloomFilter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * Unit tests for {@link CertificationChecksumHelper}.
 */
@RunWith(JUnit4.class)
public class CertificationChecksumHelperTest {
    private final static String FINGERPRINT = "thisismyfingerprint";
    private File mWorkingDir;
    private File mFakeLogFile;

    @Before
    public void setUp() throws Exception {
        mWorkingDir = FileUtil.createTempDir("certification-tests");
        mFakeLogFile = FileUtil.createTempFile("fake-log-file", ".xml", mWorkingDir);
        FileUtil.writeToFile("Bunch of data to make the file unique", mFakeLogFile);
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mWorkingDir);
    }

    /** Validation that the checksum generated can be properly checked when re-reading the file. */
    @Test
    public void testCreateChecksum() throws Exception {
        Collection<TestRunResult> results = new ArrayList<>();
        TestRunResult run1 = createFakeResults("run1", 2);
        results.add(run1);
        TestRunResult run2 = createFakeResults("run2", 3);
        results.add(run2);
        TestRunResult run3 =
                createFakeResultWithAssumptionFailure("run3", "expected:<-25.0> but was:<15.0>");
        results.add(run3);
        boolean res = CertificationChecksumHelper.tryCreateChecksum(
                mWorkingDir, results, FINGERPRINT);
        assertTrue(res);
        // Attempt to parse the results back
        File checksum = new File(mWorkingDir, CertificationChecksumHelper.NAME);
        assertTrue(checksum.exists());
        FileInputStream fileStream = new FileInputStream(checksum);
        InputStream outputStream = new BufferedInputStream(fileStream);
        ObjectInput objectInput = new ObjectInputStream(outputStream);

        short magicNumber = objectInput.readShort();
        short version = objectInput.readShort();
        BloomFilter<CharSequence> resultChecksum =
                (BloomFilter<CharSequence>) objectInput.readObject();
        HashMap<String, byte[]> fileChecksum = (HashMap<String, byte[]>) objectInput.readObject();

        assertEquals(magicNumber, 650);
        assertEquals(version, 1);
        // Assert that the info are checkable
        String filePath = mWorkingDir.getName() + "/" + mFakeLogFile.getName();
        assertTrue(fileChecksum.containsKey(filePath));
        // Check run1
        assertTrue(resultChecksum.mightContain("thisismyfingerprint/run1/true/0"));
        assertTrue(resultChecksum.mightContain("thisismyfingerprint/run1/0"));
        assertTrue(
                resultChecksum.mightContain(
                        "thisismyfingerprint/run1/com.class.path#testMethod0/pass//"));
        assertTrue(
                resultChecksum.mightContain(
                        "thisismyfingerprint/run1/com.class.path#testMethod1/pass//"));
        // Check run2
        assertTrue(resultChecksum.mightContain("thisismyfingerprint/run2/true/0"));
        assertTrue(resultChecksum.mightContain("thisismyfingerprint/run2/0"));
        assertTrue(
                resultChecksum.mightContain(
                        "thisismyfingerprint/run2/com.class.path#testMethod0/pass//"));
        assertTrue(
                resultChecksum.mightContain(
                        "thisismyfingerprint/run2/com.class.path#testMethod1/pass//"));
        assertTrue(
                resultChecksum.mightContain(
                        "thisismyfingerprint/run2/com.class.path#testMethod2/pass//"));
        // Check run3
        assertTrue(resultChecksum.mightContain("thisismyfingerprint/run3/true/0"));
        assertTrue(resultChecksum.mightContain("thisismyfingerprint/run3/0"));
        assertTrue(
                resultChecksum.mightContain(
                        "thisismyfingerprint/run3/com.class.path#testMethod/ASSUMPTION_FAILURE/expected:&lt;-25.0&gt;"
                            + " but was:&lt;15.0&gt;/"));
    }

    private TestRunResult createFakeResults(String runName, int testCount) {
        TestRunResult results = new TestRunResult();
        results.testRunStarted(runName, testCount);
        for (int i = 0; i < testCount; i++) {
            TestDescription test = new TestDescription("com.class.path", "testMethod" + i);
            results.testStarted(test);
            results.testEnded(test, new HashMap<String, Metric>());
        }
        results.testRunEnded(500L, new HashMap<String, Metric>());
        return results;
    }

    private TestRunResult createFakeResultWithAssumptionFailure(String runName, String trackTrace) {
        TestRunResult results = new TestRunResult();
        results.testRunStarted(runName, 1);
        TestDescription test = new TestDescription("com.class.path", "testMethod");
        results.testStarted(test);
        results.testAssumptionFailure(test, trackTrace);
        results.testRunEnded(500L, new HashMap<String, Metric>());
        return results;
    }
}
