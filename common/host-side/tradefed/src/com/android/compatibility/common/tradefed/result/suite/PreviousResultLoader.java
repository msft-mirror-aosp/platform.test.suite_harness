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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.testtype.retry.RetryFactoryTest;
import com.android.compatibility.common.util.ResultHandler;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.result.suite.SuiteResultHolder;
import com.android.tradefed.testtype.suite.retry.ITestSuiteResultLoader;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Implementation of {@link ITestSuiteResultLoader} to reload CTS previous results.
 */
public final class PreviousResultLoader implements ITestSuiteResultLoader {

    public static final String BUILD_FINGERPRINT = "build_fingerprint";

    @Option(name = RetryFactoryTest.RETRY_OPTION,
            shortName = 'r',
            description = "retry a previous session's failed and not executed tests.",
            mandatory = true)
    private Integer mRetrySessionId = null;

    private TestRecord mTestRecord;
    private IInvocationContext mPreviousContext;

    @Override
    public void init(IInvocationContext context) {
        IBuildInfo info = context.getBuildInfos().get(0);
        CompatibilityBuildHelper helperBuild = new CompatibilityBuildHelper(info);
        File resultDir = null;
        try {
            resultDir = ResultHandler.getResultDirectory(
                    helperBuild.getResultsDir(), mRetrySessionId);
            try (InputStream stream = new FileInputStream(
                    new File(resultDir, CompatibilityProtoResultReporter.PROTO_FILE_NAME))) {
                mTestRecord = TestRecord.parseDelimitedFrom(stream);
            }
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
            CertificationResultXml xmlParser = new CertificationResultXml();
            SuiteResultHolder holder = xmlParser.parseResults(resultDir, true);
            String previousFingerprint = holder.context.getAttributes()
                    .getUniqueMap().get(BUILD_FINGERPRINT);
            validateBuildFingerprint(previousFingerprint, context.getDevices().get(0));
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

    private void validateBuildFingerprint(String previousFingerprint, ITestDevice device) {
        if (previousFingerprint == null) {
            throw new IllegalArgumentException(
                    "Could not find the build_fingerprint field in the loaded result.");
        }
        try {
            String currentBuildFingerprint = device.getProperty("ro.build.fingerprint");
            if (!previousFingerprint.equals(currentBuildFingerprint)) {
                throw new IllegalArgumentException(String.format(
                        "Device build fingerprint must match %s to retry session %d",
                        previousFingerprint, mRetrySessionId));
            }
        } catch (DeviceNotAvailableException e) {
            throw new RuntimeException(e);
        }
    }
}
