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

import com.android.annotations.VisibleForTesting;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.DeviceInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.cluster.SubprocessConfigBuilder;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.ShardListener;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.LogFileSaver;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.result.suite.IFormatterGenerator;
import com.android.tradefed.result.suite.SuiteResultReporter;
import com.android.tradefed.result.suite.XmlFormattedGeneratorReporter;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extension of {@link XmlFormattedGeneratorReporter} and {@link SuiteResultReporter} to handle
 * Compatibility specific format and operations.
 */
@OptionClass(alias = "result-reporter")
public class CertificationSuiteResultReporter extends XmlFormattedGeneratorReporter
        implements ITestSummaryListener {

    // The known existing variant of suites.
    // Adding a new variant requires approval from Android Partner team and Test Harness team.
    private enum SuiteVariant {
        CTS_ON_GSI("CTS_ON_GSI", "cts-on-gsi");

        private final String mReportDisplayName;
        private final String mConfigName;

        private SuiteVariant(String reportName, String configName) {
            mReportDisplayName = reportName;
            mConfigName = configName;
        }

        public String getReportDisplayName() {
            return mReportDisplayName;
        }

        public String getConfigName() {
            return mConfigName;
        }
    }

    public static final String LATEST_LINK_NAME = "latest";
    public static final String SUMMARY_FILE = "invocation_summary.txt";

    public static final String BUILD_FINGERPRINT = "cts:build_fingerprint";

    @Option(name = "result-server", description = "Server to publish test results.")
    @Deprecated
    private String mResultServer;

    @Option(
            name = "disable-result-posting",
            description = "Disable result posting into report server.")
    @Deprecated
    private boolean mDisableResultPosting = false;

    @Option(name = "include-test-log-tags", description = "Include test log tags in report.")
    private boolean mIncludeTestLogTags = false;

    @Option(name = "use-log-saver", description = "Also saves generated result with log saver")
    private boolean mUseLogSaver = false;

    @Option(name = "compress-logs", description = "Whether logs will be saved with compression")
    private boolean mCompressLogs = true;

    public static final String INCLUDE_HTML_IN_ZIP = "html-in-zip";

    @Option(
            name = INCLUDE_HTML_IN_ZIP,
            description = "Whether failure summary report is included in the zip fie.")
    @Deprecated
    private boolean mIncludeHtml = false;

    @Option(
            name = "result-attribute",
            description =
                    "Extra key-value pairs to be added as attributes and corresponding values "
                            + "of the \"Result\" tag in the result XML.")
    private Map<String, String> mResultAttributes = new HashMap<String, String>();

    // Should be removed for the S release.
    @Option(
            name = "cts-on-gsi-variant",
            description =
                    "Workaround for the R release to ensure the CTS-on-GSI report can be parsed "
                            + "by the APFE.")
    private boolean mCtsOnGsiVariant = false;

    private CompatibilityBuildHelper mBuildHelper;

    /** The directory containing the results */
    private File mResultDir = null;
    /** The directory containing the logs */
    private File mLogDir = null;

    /** LogFileSaver to copy the file to the CTS results folder */
    private LogFileSaver mTestLogSaver;

    private Map<LogFile, InputStreamSource> mPreInvocationLogs = new HashMap<>();
    /** Invocation level Log saver to receive when files are logged */
    private ILogSaver mLogSaver;

    private String mReferenceUrl;

    private Map<String, String> mLoggedFiles;

    private static final String[] RESULT_RESOURCES = {
        "compatibility_result.css",
        "compatibility_result.xsl",
        "logo.png"
    };

    public CertificationSuiteResultReporter() {
        super();
        mLoggedFiles = new LinkedHashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void invocationStarted(IInvocationContext context) {
        super.invocationStarted(context);

        if (mBuildHelper == null) {
            mBuildHelper = createBuildHelper();
        }
        if (mResultDir == null) {
            initializeResultDirectories();
        }
    }

    @VisibleForTesting
    CompatibilityBuildHelper createBuildHelper() {
        return new CompatibilityBuildHelper(getPrimaryBuildInfo());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String name, LogDataType type, InputStreamSource stream) {
        if (name.endsWith(DeviceInfo.FILE_SUFFIX)) {
            // Handle device info file case
            testLogDeviceInfo(name, stream);
            return;
        }
        if (mTestLogSaver == null) {
            LogFile info = new LogFile(name, null, type);
            mPreInvocationLogs.put(
                    info, new SnapshotInputStreamSource(name, stream.createInputStream()));
            return;
        }
        try {
            File logFile = null;
            if (mCompressLogs) {
                try (InputStream inputStream = stream.createInputStream()) {
                    logFile = mTestLogSaver.saveAndGZipLogData(name, type, inputStream);
                }
            } else {
                try (InputStream inputStream = stream.createInputStream()) {
                    logFile = mTestLogSaver.saveLogData(name, type, inputStream);
                }
            }
            CLog.d("Saved logs for %s in %s", name, logFile.getAbsolutePath());
        } catch (IOException e) {
            CLog.e("Failed to write log for %s", name);
            CLog.e(e);
        }
    }

    /** Write device-info files to the result */
    private void testLogDeviceInfo(String name, InputStreamSource stream) {
        try {
            File ediDir = new File(mResultDir, DeviceInfo.RESULT_DIR_NAME);
            ediDir.mkdirs();
            File ediFile = new File(ediDir, name);
            if (!ediFile.exists()) {
                // only write this file to the results if not already present
                FileUtil.writeToFile(stream.createInputStream(), ediFile);
            }
        } catch (IOException e) {
            CLog.w("Failed to write device info %s to result", name);
            CLog.e(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLogSaved(String dataName, LogDataType dataType, InputStreamSource dataStream,
            LogFile logFile) {
        if (mIncludeTestLogTags) {
            switch (dataType) {
                case BUGREPORT:
                case LOGCAT:
                case PNG:
                    mLoggedFiles.put(dataName, logFile.getUrl());
                    break;
                default:
                    // Do nothing
                    break;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putSummary(List<TestSummary> summaries) {
        for (TestSummary summary : summaries) {
            if (mReferenceUrl == null && summary.getSummary().getString() != null) {
                mReferenceUrl = summary.getSummary().getString();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogSaver(ILogSaver saver) {
        mLogSaver = saver;
    }

    /**
     * Create directory structure where results and logs will be written.
     */
    private void initializeResultDirectories() {
        CLog.d("Initializing result directory");
        try {
            mResultDir = mBuildHelper.getResultDir();
            if (mResultDir != null) {
                mResultDir.mkdirs();
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        if (mResultDir == null) {
            throw new RuntimeException("Result Directory was not created");
        }
        if (!mResultDir.exists()) {
            throw new RuntimeException("Result Directory was not created: " +
                    mResultDir.getAbsolutePath());
        }

        CLog.d("Results Directory: %s", mResultDir.getAbsolutePath());

        try {
            mLogDir = mBuildHelper.getInvocationLogDir();
        } catch (FileNotFoundException e) {
            CLog.e(e);
        }
        if (mLogDir != null && mLogDir.mkdirs()) {
            CLog.d("Created log dir %s", mLogDir.getAbsolutePath());
        }
        if (mLogDir == null || !mLogDir.exists()) {
            throw new IllegalArgumentException(String.format("Could not create log dir %s",
                    mLogDir.getAbsolutePath()));
        }
        // During sharding, we reach here before invocationStarted is called so the log_saver will
        // be null at that point.
        if (mTestLogSaver == null) {
            mTestLogSaver = new LogFileSaver(mLogDir);
            // Log all the early logs from before init.
            for (LogFile earlyLog : mPreInvocationLogs.keySet()) {
                try (InputStreamSource source = mPreInvocationLogs.get(earlyLog)) {
                    testLog(earlyLog.getPath(), earlyLog.getType(), source);
                }
            }
            mPreInvocationLogs.clear();
        }
    }

    @Override
    public IFormatterGenerator createFormatter() {
        return new CertificationResultXml(
                createSuiteName(mBuildHelper.getSuiteName()),
                mBuildHelper.getSuiteVersion(),
                createSuiteVariant(),
                mBuildHelper.getSuitePlan(),
                mBuildHelper.getSuiteBuild(),
                mReferenceUrl,
                getLogUrl(),
                mResultAttributes);
    }

    @Override
    public void preFormattingSetup(IFormatterGenerator formater) {
        super.preFormattingSetup(formater);
        // Log the summary
        TestSummary summary = getSummary();
        try {
            File summaryFile = new File(mResultDir, SUMMARY_FILE);
            FileUtil.writeToFile(summary.getSummary().toString(), summaryFile);
        } catch (IOException e) {
            CLog.e("Failed to save the summary.");
            CLog.e(e);
        }

        copyDynamicConfigFiles();
        copyFormattingFiles(mResultDir, mBuildHelper.getSuiteName());
    }

    @Override
    public File createResultDir() throws IOException {
        return mResultDir;
    }

    @Override
    public void postFormattingStep(File resultDir, File reportFile) {
        super.postFormattingStep(resultDir,reportFile);

        createChecksum(
                resultDir,
                getMergedTestRunResults(),
                getPrimaryBuildInfo().getBuildAttributes().get(BUILD_FINGERPRINT));

        Path latestLink = createLatestLinkDirectory(mResultDir.toPath());
        if (latestLink != null) {
            CLog.i("Latest results link: " + latestLink.toAbsolutePath());
        }

        latestLink = createLatestLinkDirectory(mLogDir.toPath());
        if (latestLink != null) {
            CLog.i("Latest logs link: " + latestLink.toAbsolutePath());
        }

        for (ITestInvocationListener resultReporter :
                getConfiguration().getTestInvocationListeners()) {
            if (resultReporter instanceof CertificationReportCreator) {
                ((CertificationReportCreator) resultReporter).setReportFile(reportFile);
            }
            if (resultReporter instanceof ShardListener) {
                for (ITestInvocationListener subListener : ((ShardListener) resultReporter).getUnderlyingResultReporter()) {
                    if (subListener instanceof CertificationReportCreator) {
                        ((CertificationReportCreator) subListener).setReportFile(reportFile);
                    }
                }
            }
        }
    }

    /**
     * Return the path in which log saver persists log files or null if
     * logSaver is not enabled.
     */
    private String getLogUrl() {
        if (!mUseLogSaver || mLogSaver == null) {
            return null;
        }

        return mLogSaver.getLogReportDir().getUrl();
    }

    /**
     * Update the "latest" symlink to the newest result directory. CTS specific.
     */
    private Path createLatestLinkDirectory(Path directory) {
        Path link = null;

        Path parent = directory.getParent();

        if (parent != null) {
            link = parent.resolve(LATEST_LINK_NAME);
            try {
                // if latest already exists, we have to remove it before creating
                Files.deleteIfExists(link);
                Files.createSymbolicLink(link, directory);
            } catch (IOException ioe) {
                CLog.e("Exception while attempting to create 'latest' link to: [%s]",
                    directory);
                CLog.e(ioe);
                return null;
            } catch (UnsupportedOperationException uoe) {
                CLog.e("Failed to create 'latest' symbolic link - unsupported operation");
                return null;
            }
        }
        return link;
    }

    /**
     * move the dynamic config files to the results directory
     */
    private void copyDynamicConfigFiles() {
        File configDir = new File(mResultDir, "config");
        if (!configDir.exists() && !configDir.mkdir()) {
            CLog.w(
                    "Failed to make dynamic config directory \"%s\" in the result.",
                    configDir.getAbsolutePath());
        }

        Set<String> uniqueModules = new HashSet<>();
        // Check each build of the invocation, in case of multi-device invocation.
        for (IBuildInfo buildInfo : getInvocationContext().getBuildInfos()) {
            CompatibilityBuildHelper helper = new CompatibilityBuildHelper(buildInfo);
            Map<String, File> dcFiles = helper.getDynamicConfigFiles();
            for (String moduleName : dcFiles.keySet()) {
                File srcFile = dcFiles.get(moduleName);
                if (!uniqueModules.contains(moduleName)) {
                    // have not seen config for this module yet, copy into result
                    File destFile = new File(configDir, moduleName + ".dynamic");
                    if (destFile.exists()) {
                        continue;
                    }
                    try {
                        FileUtil.copyFile(srcFile, destFile);
                        uniqueModules.add(moduleName); // Add to uniqueModules if copy succeeds
                    } catch (IOException e) {
                        CLog.w("Failure when copying config file \"%s\" to \"%s\" for module %s",
                                srcFile.getAbsolutePath(), destFile.getAbsolutePath(), moduleName);
                        CLog.e(e);
                    }
                }
                FileUtil.deleteFile(srcFile);
            }
        }
    }

    /**
     * Copy the xml formatting files stored in this jar to the results directory. CTS specific.
     *
     * @param resultsDir
     */
    private void copyFormattingFiles(File resultsDir, String suiteName) {
        for (String resultFileName : RESULT_RESOURCES) {
            InputStream configStream = CertificationResultXml.class.getResourceAsStream(
                    String.format("/report/%s-%s", suiteName, resultFileName));
            if (configStream == null) {
                // If suite specific files are not available, fallback to common.
                configStream = CertificationResultXml.class.getResourceAsStream(
                    String.format("/report/%s", resultFileName));
            }
            if (configStream != null) {
                File resultFile = new File(resultsDir, resultFileName);
                try {
                    FileUtil.writeToFile(configStream, resultFile);
                } catch (IOException e) {
                    CLog.w("Failed to write %s to file", resultFileName);
                }
            } else {
                CLog.w("Failed to load %s from jar", resultFileName);
            }
        }
    }

    /**
     * Generates a checksum files based on the results.
     */
    private void createChecksum(File resultDir, Collection<TestRunResult> results,
            String buildFingerprint) {
        CertificationChecksumHelper.tryCreateChecksum(resultDir, results, buildFingerprint);
    }

    private String createSuiteName(String originalSuiteName) {
        if (mCtsOnGsiVariant) {
            String commandLine = getConfiguration().getCommandLine();
            // SubprocessConfigBuilder is added to support ATS current way of running things.
            // It won't be needed after the R release.
            if (commandLine.startsWith("cts-on-gsi")
                    || commandLine.startsWith(
                            SubprocessConfigBuilder.createConfigName("cts-on-gsi"))) {
                return "VTS";
            }
        }
        return originalSuiteName;
    }

    private String createSuiteVariant() {
        IConfiguration currentConfig = getConfiguration();
        String commandLine = currentConfig.getCommandLine();
        for (SuiteVariant var : SuiteVariant.values()) {
            if (commandLine.startsWith(var.getConfigName() + " ")
                    || commandLine.equals(var.getConfigName())) {
                return var.getReportDisplayName();
            }
        }
        return null;
    }
}
