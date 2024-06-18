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

import com.android.compatibility.common.util.ChecksumReporter.ChecksumValidationException;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.result.TestStatus;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 * Helper to generate the checksum of the results and files. Use
 * {@link #tryCreateChecksum(File, Collection, String)} to get the checksum file in the result dir.
 */
public class CertificationChecksumHelper {

    public static final String NAME = "checksum-suite.data";

    private static final double DEFAULT_FPP = 0.05;
    private static final String SEPARATOR = "/";

    private static final short CURRENT_VERSION = 1;
    // Serialized format Id (ie magic number) used to identify serialized data.
    static final short SERIALIZED_FORMAT_CODE = 650;

    private final BloomFilter<CharSequence> mResultChecksum;
    private final HashMap<String, byte[]> mFileChecksum;
    private final short mVersion;
    private final String mBuildFingerprint;

    /**
     * Create new instance of {@link CertificationChecksumHelper}
     *
     * @param totalCount the total number of module and test results that will be stored
     * @param fpp the false positive percentage for result lookup misses
     * @param version
     * @param buildFingerprint
     */
    public CertificationChecksumHelper(
            int totalCount, double fpp, short version, String buildFingerprint) {
        mResultChecksum = BloomFilter.create(Funnels.unencodedCharsFunnel(), totalCount, fpp);
        mFileChecksum = new HashMap<>();
        mVersion = version;
        mBuildFingerprint = buildFingerprint;
    }

    /**
     * Calculate checksum of test results and files in result directory and write to disk
     * @param dir test results directory
     * @param results test results
     * @return true if successful, false if unable to calculate or store the checksum
     */
    public static boolean tryCreateChecksum(File dir, Collection<TestRunResult> results,
            String buildFingerprint) {
        try {
            // The total number of module result signatures, module summary signatures and test
            // result signatures.
            int totalCount = results.size() * 2 + countTestResults(results);
            CertificationChecksumHelper checksumReporter =
                    new CertificationChecksumHelper(totalCount, DEFAULT_FPP, CURRENT_VERSION,
                            buildFingerprint);
            checksumReporter.addResults(results);
            checksumReporter.addDirectory(dir);
            checksumReporter.saveToFile(dir);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /***
     * Write the checksum data to disk.
     * Overwrites existing file
     * @param directory
     * @throws IOException
     */
    private void saveToFile(File directory) throws IOException {
        File file = new File(directory, NAME);

        try (FileOutputStream fileStream = new FileOutputStream(file, false);
             OutputStream outputStream = new BufferedOutputStream(fileStream);
             ObjectOutput objectOutput = new ObjectOutputStream(outputStream)) {
            objectOutput.writeShort(SERIALIZED_FORMAT_CODE);
            objectOutput.writeShort(mVersion);
            objectOutput.writeObject(mResultChecksum);
            objectOutput.writeObject(mFileChecksum);
        }
    }

    private static int countTestResults(Collection<TestRunResult> results) {
        int count = 0;
        for (TestRunResult result : results) {
            count += result.getNumTests();
        }
        return count;
    }

    private void addResults(Collection<TestRunResult> results) {
        for (TestRunResult moduleResult : results) {
            // First the module result signature
            mResultChecksum.put(
                    generateModuleResultSignature(moduleResult, mBuildFingerprint));
            // Second the module summary signature
            mResultChecksum.put(
                    generateModuleSummarySignature(moduleResult, mBuildFingerprint));

            for (Entry<TestDescription, TestResult> caseResult
                    : moduleResult.getTestResults().entrySet()) {
                mResultChecksum.put(generateTestResultSignature(
                        caseResult, moduleResult, mBuildFingerprint));
            }
        }
    }

    private static String generateModuleResultSignature(TestRunResult module,
            String buildFingerprint) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildFingerprint).append(SEPARATOR)
                .append(module.getName()).append(SEPARATOR)
                .append(module.isRunComplete()).append(SEPARATOR)
                .append(module.getNumTestsInState(TestStatus.FAILURE));
        return sb.toString();
    }

    private static String generateModuleSummarySignature(TestRunResult module,
            String buildFingerprint) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildFingerprint).append(SEPARATOR)
                .append(module.getName()).append(SEPARATOR)
                .append(module.getNumTestsInState(TestStatus.FAILURE));
        return sb.toString();
    }

    private static String generateTestResultSignature(
            Entry<TestDescription, TestResult> testResult, TestRunResult module,
            String buildFingerprint) {
        StringBuilder sb = new StringBuilder();
        String stacktrace = testResult.getValue().getStackTrace();

        stacktrace = stacktrace == null ? "" : stacktrace.trim();
        // Line endings for stacktraces are somewhat unpredictable and there is no need to
        // actually read the result they are all removed for consistency.
        stacktrace = stacktrace.replaceAll("\\r?\\n|\\r", "");
        String testResultStatus =
                TestStatus.convertToCompatibilityString(testResult.getValue().getResultStatus());
        sb.append(buildFingerprint)
                .append(SEPARATOR)
                .append(module.getName())
                .append(SEPARATOR)
                .append(testResult.getKey().toString())
                .append(SEPARATOR)
                .append(testResultStatus)
                .append(SEPARATOR)
                .append(stacktrace)
                .append(SEPARATOR);
        return sb.toString();
    }

    /***
     * Adds all child files recursively through all sub directories
     * @param directory target that is deeply searched for files
     */
    public void addDirectory(File directory) {
        addDirectory(directory, directory.getName());
    }

    /***
     * @param path the relative path to the current directory from the base directory
     */
    private void addDirectory(File directory, String path) {
        for(String childName : directory.list()) {
            File child = new File(directory, childName);
            if (child.isDirectory()) {
                addDirectory(child, path + SEPARATOR + child.getName());
            } else {
                addFile(child, path);
            }
        }
    }

    /***
     * Calculate CRC of file and store the result
     * @param file crc calculated on this file
     * @param path part of the key to identify the files crc
     */
    private void addFile(File file, String path) {
        byte[] crc;
        try {
            crc = calculateFileChecksum(file);
        } catch (ChecksumValidationException e) {
            crc = new byte[0];
        }
        String key = path + SEPARATOR + file.getName();
        mFileChecksum.put(key, crc);
    }

    private static byte[] calculateFileChecksum(File file) throws ChecksumValidationException {

        try (FileInputStream fis = new FileInputStream(file);
             InputStream inputStream = new BufferedInputStream(fis)) {
            MessageDigest hashSum = MessageDigest.getInstance("SHA-256");
            int cnt;
            int bufferSize = 8192;
            byte [] buffer = new byte[bufferSize];
            while ((cnt = inputStream.read(buffer)) != -1) {
                hashSum.update(buffer, 0, cnt);
            }

            byte[] partialHash = new byte[32];
            hashSum.digest(partialHash, 0, 32);
            return partialHash;
        } catch (NoSuchAlgorithmException e) {
            throw new ChecksumValidationException("Unable to hash file.", e);
        } catch (IOException e) {
            throw new ChecksumValidationException("Unable to hash file.", e);
        } catch (DigestException e) {
            throw new ChecksumValidationException("Unable to hash file.", e);
        }
    }
}
