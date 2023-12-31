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

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * Checks that a device property is as expected
 */
@OptionClass(alias="property-check")
public class PropertyCheck extends PreconditionPreparer {

    @Option(
        name = "property-name",
        description = "The name of the property to check",
        mandatory = true
    )
    private String mPropertyName = null;

    @Option(name = "expected-value", description = "The expected value of the property")
    private String mExpectedPropertyValue = null;

    @Option(
            name = "is-set-only",
            description = "Whether this value must be set only (don't check value)")
    private boolean mPropertyValueIsSetOnly = false;

    @Option(
            name = "throw-error",
            description = "Whether to throw an error for an unexpected property value")
    private boolean mThrowError = false;

    @Override
    public void run(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        String propertyValue = device.getProperty(mPropertyName);

        if (mPropertyValueIsSetOnly && mExpectedPropertyValue != null) {
            throw new IllegalArgumentException(
                    "is-set-only and " + "expected-value cannot both be set.");
        } else if (!mPropertyValueIsSetOnly && mExpectedPropertyValue == null) {
            throw new IllegalArgumentException("is-set-only or expected-value must be set.");
        }

        if (mPropertyValueIsSetOnly) {
            if (propertyValue == null || propertyValue.equals("")) {
                String msg =
                        String.format(
                                "Property \"%s\" not found or not set on device", mPropertyName);
                // Handle missing property with either exception or warning
                if (mThrowError) {
                    throw new TargetSetupError(msg, device.getDeviceDescriptor(),
                        DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
                } else {
                    CLog.w(msg);
                }
            }
            return;
        }

        if (propertyValue == null) {
            CLog.w(
                    "Property \"%s\" not found on device, cannot verify value \"%s\" ",
                    mPropertyName, mExpectedPropertyValue);
            return;
        }

        if (!mExpectedPropertyValue.equalsIgnoreCase(propertyValue)) {
            String msg = String.format("Expected \"%s\" but found \"%s\" for property: %s",
                    mExpectedPropertyValue, propertyValue, mPropertyName);
            // Handle unexpected property value with either exception or warning
            if(mThrowError) {
                throw new TargetSetupError(msg, device.getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
            } else {
                CLog.w(msg);
            }
        }
    }

}
