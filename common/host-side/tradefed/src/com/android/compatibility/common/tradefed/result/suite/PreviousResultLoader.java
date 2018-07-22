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
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.result.suite.SuiteResultHolder;
import com.android.tradefed.testtype.suite.retry.ITestSuiteResultLoader;

import java.io.IOException;
import java.util.List;

/**
 * Implementation of {@link ITestSuiteResultLoader} to reload CTS previous results.
 * <p/>
 * TODO(110265525): Add the fingerprint validation
 */
public class PreviousResultLoader implements ITestSuiteResultLoader {

    @Option(name = RetryFactoryTest.RETRY_OPTION,
            shortName = 'r',
            description = "retry a previous session's failed and not executed tests.",
            mandatory = true)
    private Integer mRetrySessionId = null;

    private SuiteResultHolder mPreviousResults;

    @Override
    public void init(IInvocationContext context) {
        IBuildInfo info = context.getBuildInfos().get(0);
        CompatibilityBuildHelper helperBuild = new CompatibilityBuildHelper(info);
        CertificationResultXml loader = new CertificationResultXml();
        try {
            mPreviousResults = loader.parseResults(
                    ResultHandler.getResultDirectory(helperBuild.getResultsDir(), mRetrySessionId));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (mPreviousResults == null) {
            throw new RuntimeException("Failed to load the previous results.");
        }
    }

    @Override
    public String getCommandLine() {
        List<String> command = mPreviousResults.context.getAttributes().get(
                TestInvocation.COMMAND_ARGS_KEY);
        if (command == null) {
            throw new RuntimeException("Couldn't find the command line arg.");
        }
        return command.get(0);
    }

    @Override
    public SuiteResultHolder loadPreviousResults() {
        return mPreviousResults;
    }
}
