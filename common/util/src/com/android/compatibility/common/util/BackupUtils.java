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

package com.android.compatibility.common.util;

import static org.junit.Assert.assertTrue;

import com.android.compatibility.common.util.LogcatInspector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for backup and restore.
 */
public abstract class BackupUtils {

    /** The tag of the token */
    private static final String TOKEN_TAG = "Current:";

    /**
     * Should execute adb shell {@param command} and return an {@link InputStream} with the result.
     */
    protected abstract InputStream executeShellCommand(String command) throws IOException;

    /**
     * Execute shell command "bmgr backupnow <packageName>" and assert success.
     */
    public void backupNowAndAssertSuccess(String packageName) throws IOException {
        InputStream backupnowOutput = backupNow(packageName);
        assertBackupIsSuccessful(packageName, backupnowOutput);
    }

    /**
     * Execute shell command "bmgr backupnow <packageName>" and return output from this command.
     */
    private InputStream backupNow(String packageName) throws IOException {
        return executeShellCommand("bmgr backupnow " + packageName);
    }

    /**
     * Parsing the output of "bmgr backupnow" command and checking that the package under test
     * was backed up successfully. Close the input stream finally.
     *
     * Expected format: "Package <packageName> with result: Success"
     */
    private void assertBackupIsSuccessful(String packageName, InputStream backupnowOutput)
            throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(backupnowOutput, StandardCharsets.UTF_8));
        try {
            String str;
            boolean success = false;
            while ((str = br.readLine()) != null) {
                if (str.contains(packageName)) {
                    String result = str.split(":")[1].trim();
                    if ("Success".equals(result)) {
                        success = true;
                        break;
                    }
                }
            }
            assertTrue("Couldn't find package in output or its backup wasn't successful", success);
        } finally {
            if (br != null) LogcatInspector.drainAndClose(br);
        }
    }

    /**
     * Execute shell command "bmgr restore <token> <packageName>" and assert success.
     */
    public void restoreAndAssertSuccess(String packageName) throws IOException {
        InputStream dumpsysOutput = dumpsysBackup();
        String token = getTokenOrFail(dumpsysOutput);
        InputStream restoreOutput = restore(token, packageName);
        assertRestoreIsSuccessful(restoreOutput);
    }

    /**
     * Execute shell command "bmgr restore <token> <packageName>" and return output from this
     * command.
     */
    private InputStream restore(String token, String packageName) throws IOException {
        return executeShellCommand(String.format("bmgr restore %s %s", token, packageName));
    }

    /**
     * Parsing the output of "bmgr restore" command and checking that the package under test
     * was restored successfully. Close the input stream finally.
     *
     * Expected format: "restoreFinished: 0"
     */
    private void assertRestoreIsSuccessful(InputStream restoreOutput) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(restoreOutput, StandardCharsets.UTF_8));
        try {
            String str;
            boolean success = false;
            while ((str = br.readLine()) != null) {
                if (str.contains("restoreFinished: 0")) {
                    success = true;
                    break;
                }
            }
            assertTrue("Restore not successful", success);
        } finally {
            if (br != null) LogcatInspector.drainAndClose(br);
        }
    }

    /**
     * Execute shell command "dumpsys backup" and return output from this command.
     */
    private InputStream dumpsysBackup() throws IOException {
        return executeShellCommand("dumpsys backup");
    }

    /**
     * Parsing the output of "dumpsys backup" command to get token. Close the input stream finally.
     *
     * Expected format: "Current: token"
     */
    private String getTokenOrFail(InputStream dumpsysOutput) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(dumpsysOutput, StandardCharsets.UTF_8));
        String token = null;
        try {
            String str;
            while ((str = br.readLine()) != null) {
                if (str.contains(TOKEN_TAG)) {
                    token = str.split(TOKEN_TAG)[1].trim();
                    break;
                }
            }
            assertTrue("Couldn't find token in output", token != null);
        } finally {
            if (br != null) LogcatInspector.drainAndClose(br);
        }
        return token;
    }
}