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
import com.android.compatibility.common.tradefed.build.CompatibilityBuildProvider;
import com.android.compatibility.common.tradefed.targetprep.BuildFingerPrintPreparer;
import com.android.compatibility.common.tradefed.testtype.retry.RetryFactoryTest;
import com.android.compatibility.common.util.ResultHandler;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.result.suite.SuiteResultHolder;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.suite.retry.ITestSuiteResultLoader;
import com.android.tradefed.util.proto.TestRecordProtoUtil;

import com.google.api.client.util.Strings;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link ITestSuiteResultLoader} to reload CTS previous results.
 */
public final class PreviousResultLoader implements ITestSuiteResultLoader {

    /** Usually associated with ro.build.fingerprint. */
    public static final String BUILD_FINGERPRINT = "build_fingerprint";
    /** Usally associated with ro.vendor.build.fingerprint. */
    public static final String BUILD_VENDOR_FINGERPRINT = "build_vendor_fingerprint";
    /**
     * Some suites have a business need to alter the original real device fingerprint value, in this
     * case we expect an "unaltered" version to be available to still do the original check.
     */
    public static final String BUILD_FINGERPRINT_UNALTERED = "build_fingerprint_unaltered";

    @Option(name = RetryFactoryTest.RETRY_OPTION,
            shortName = 'r',
            description = "retry a previous session's failed and not executed tests.",
            mandatory = true)
    private Integer mRetrySessionId = null;

    @Option(
        name = "fingerprint-property",
        description = "The property name to check for the fingerprint."
    )
    private String mFingerprintProperty = "ro.build.fingerprint";

    private TestRecord mTestRecord;
    private IInvocationContext mPreviousContext;
    private String mExpectedFingerprint;
    private String mExpectedVendorFingerprint;
    private String mUnalteredFingerprint;

    private File mResultDir;

    private IBuildProvider mProvider;

    @Override
    public void init() {
        IBuildInfo info = null;
        try {
            info = getProvider().getBuild();
        } catch (BuildRetrievalError e) {
            throw new RuntimeException(e);
        }
        CompatibilityBuildHelper helperBuild = new CompatibilityBuildHelper(info);
        mResultDir = null;
        try {
            CLog.logAndDisplay(LogLevel.DEBUG, "Start loading the record protobuf.");
            mResultDir =
                    ResultHandler.getResultDirectory(helperBuild.getResultsDir(), mRetrySessionId);
            mTestRecord =
                    TestRecordProtoUtil.readFromFile(
                            new File(mResultDir, CompatibilityProtoResultReporter.PROTO_FILE_NAME));
            CLog.logAndDisplay(LogLevel.DEBUG, "Done loading the record protobuf.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Context contextProto = null;
        try {
            contextProto = mTestRecord.getDescription().unpack(Context.class);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        mPreviousContext = InvocationContext.fromProto(contextProto);

        // Validate the fingerprint
        // TODO: Use fingerprint argument from TestRecord but we have to deal with suite namespace
        // for example: cts:build_fingerprint instead of just build_fingerprint.
        try {
            CLog.logAndDisplay(LogLevel.DEBUG, "Start parsing previous test_results.xml");
            CertificationResultXml xmlParser = new CertificationResultXml();
            SuiteResultHolder holder = xmlParser.parseResults(mResultDir, true);
            CLog.logAndDisplay(LogLevel.DEBUG, "Done parsing previous test_results.xml");
            mExpectedFingerprint = holder.context.getAttributes()
                    .getUniqueMap().get(BUILD_FINGERPRINT);
            if (mExpectedFingerprint == null) {
                throw new IllegalArgumentException(
                        String.format(
                                "Could not find the %s field in the loaded result.",
                                BUILD_FINGERPRINT));
            }
            /** If available in the report, collect the vendor fingerprint too. */
            mExpectedVendorFingerprint =
                    holder.context.getAttributes().getUniqueMap().get(BUILD_VENDOR_FINGERPRINT);
            if (mExpectedVendorFingerprint == null) {
                throw new IllegalArgumentException(
                        String.format(
                                "Could not find the %s field in the loaded result.",
                                BUILD_VENDOR_FINGERPRINT));
            }
            // Some cases will have an unaltered fingerprint
            mUnalteredFingerprint =
                    holder.context.getAttributes().getUniqueMap().get(BUILD_FINGERPRINT_UNALTERED);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getCommandLine() {
        List<String> command = mPreviousContext.getAttributes().get(
                TestInvocation.COMMAND_ARGS_KEY);
        if (command == null) {
            throw new RuntimeException("Couldn't find the command line arg.");
        }
        return command.get(0);
    }

    @Override
    public TestRecord loadPreviousRecord() {
        return mTestRecord;
    }

    @Override
    public final void cleanUp() {
        if (mTestRecord != null) {
            mTestRecord = null;
        }
    }

    @Override
    public final void customizeConfiguration(IConfiguration config) {
        // This is specific to Compatibility checking and does not work for multi-device.
        List<ITargetPreparer> preparers = config.getTargetPreparers();
        List<ITargetPreparer> newList = new ArrayList<>();
        // Add the fingerprint checker first to ensure we check it before rerunning the config.
        BuildFingerPrintPreparer fingerprintChecker = new BuildFingerPrintPreparer();
        fingerprintChecker.setExpectedFingerprint(mExpectedFingerprint);
        fingerprintChecker.setExpectedVendorFingerprint(mExpectedVendorFingerprint);
        fingerprintChecker.setFingerprintProperty(mFingerprintProperty);
        if (!Strings.isNullOrEmpty(mUnalteredFingerprint)) {
            fingerprintChecker.setUnalteredFingerprint(mUnalteredFingerprint);
        }
        newList.add(fingerprintChecker);
        newList.addAll(preparers);
        config.setTargetPreparers(newList);

        // Add the file copier last to copy from previous sesssion
        List<ITestInvocationListener> listeners = config.getTestInvocationListeners();
        PreviousSessionFileCopier copier = new PreviousSessionFileCopier();
        copier.setPreviousSessionDir(mResultDir);
        listeners.add(copier);
    }

    @VisibleForTesting
    protected void setProvider(IBuildProvider provider) {
        mProvider = provider;
    }

    private IBuildProvider getProvider() {
        if (mProvider == null) {
            mProvider = new CompatibilityBuildProvider();
        }
        return mProvider;
    }
}