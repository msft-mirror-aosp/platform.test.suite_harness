/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link ITargetPreparer} that performs checks and/or tasks to ensure the the device is ready to
 * run the test suite.
 */
public abstract class PreconditionPreparer extends BaseTargetPreparer {

    public static final String SKIP_PRECONDITIONS_OPTION = "skip-preconditions";
    public static final String PRECONDITION_ARG_OPTION = "precondition-arg";

    @Option(
        name = SKIP_PRECONDITIONS_OPTION,
        shortName = 'o',
        description = "Whether preconditions should be skipped"
    )
    private boolean mSkipPreconditions = false;

    @Option(
        name = PRECONDITION_ARG_OPTION,
        description =
                "the arguments to pass to a precondition. The expected format is"
                        + "\"<arg-name>:<arg-value>\""
    )
    private List<String> mPreconditionArgs = new ArrayList<>();

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mSkipPreconditions) {
            return;
        }
        for (String preconditionArg : mPreconditionArgs) {
            String[] parts = preconditionArg.split(":");
            String argName = parts[0];
            // If arg-value is not supplied, set to "true"
            String argValue = (parts.length > 1) ? parts[1] : Boolean.toString(true);
            setOption(argName, argValue);
        }
        run(testInfo);
    }

    private void setOption(String option, String value) {
        try {
            OptionSetter setter = new OptionSetter(this);
            setter.setOptionValue(option, value);
        } catch (ConfigurationException e) {
            CLog.i("Value %s for option %s not applicable for class %s", value, option,
                    this.getClass().getName());
        }
    }

    /**
     * All PreconditionPreparer implementations share a base setup and can implement their own
     * specific run logic.
     */
    public void run(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        // TODO: Make this method abstract again once other one is removed.
        run(testInfo.getDevice(), testInfo.getBuildInfo());
    }

    /** @deprecated Use {@link #run(TestInformation)} instead. */
    @Deprecated
    public void run(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        // Empty on purpose.
    }

    /** @deprecated Use {@link CLog} instead. */
    @Deprecated
    protected void logInfo(String info) {
        CLog.i(info);
    }

    /** @deprecated Use {@link CLog} instead. */
    @Deprecated
    protected void logInfo(String infoFormat, Object... args) {
        CLog.i(infoFormat, args);
    }

    /** @deprecated Use {@link CLog} instead. */
    @Deprecated
    protected void logWarning(String warning) {
        CLog.w(warning);
    }

    /** @deprecated Use {@link CLog} instead. */
    @Deprecated
    protected void logWarning(String warningFormat, Object... args) {
        CLog.w(warningFormat, args);
    }

    /** @deprecated Use {@link CLog} instead. */
    @Deprecated
    protected void logError(String error) {
        CLog.e(error);
    }

    /** @deprecated Use {@link CLog} instead. */
    @Deprecated
    protected void logError(String errorFormat, Object... args) {
        CLog.e(errorFormat, args);
    }

    /** @deprecated Use {@link CLog} instead. */
    @Deprecated
    protected void logError(Throwable t) {
        if (t != null) {
            CLog.e(t);
        }
    }
}
