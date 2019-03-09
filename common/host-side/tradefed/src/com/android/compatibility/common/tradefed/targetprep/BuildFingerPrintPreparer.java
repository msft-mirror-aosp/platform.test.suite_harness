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
package com.android.compatibility.common.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * Special preparer used to check the build fingerprint of a device against an expected one.
 */
public final class BuildFingerPrintPreparer extends BaseTargetPreparer {

    private String mExpectedFingerprint = null;
    private String mFingerprintProperty = "ro.build.fingerprint";

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mExpectedFingerprint == null) {
            throw new TargetSetupError("build fingerprint shouldn't be null",
                    device.getDeviceDescriptor());
        }
        try {
            String currentBuildFingerprint = device.getProperty(mFingerprintProperty);
            if (!mExpectedFingerprint.equals(currentBuildFingerprint)) {
                throw new IllegalArgumentException(String.format(
                        "Device build fingerprint must match %s.",
                        mExpectedFingerprint));
            }
        } catch (DeviceNotAvailableException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the expected fingerprint we are checking against.
     */
    public void setExpectedFingerprint(String expectedFingerprint) {
        mExpectedFingerprint = expectedFingerprint;
    }

    /**
     * Returns the expected fingerprint.
     */
    public String getExpectedFingerprint() {
        return mExpectedFingerprint;
    }

    /**
     * Allow to override the base fingerprint property. In some cases, we want to check the
     * "ro.vendor.build.fingerpint" for example.
     */
    public void setFingerprintProperty(String property) {
        mFingerprintProperty = property;
    }
}
