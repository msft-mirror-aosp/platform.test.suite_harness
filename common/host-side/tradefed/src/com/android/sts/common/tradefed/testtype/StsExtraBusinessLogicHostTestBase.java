/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.sts.common.tradefed.testtype;

import com.android.compatibility.common.tradefed.testtype.ExtraBusinessLogicHostTestBase;
import com.android.compatibility.common.util.DescriptionProvider;
import com.android.sts.common.util.SplUtils;
import com.android.sts.common.util.StsLogic;
import com.android.tradefed.device.DeviceNotAvailableException;

import org.junit.Rule;
import org.junit.runner.Description;

import java.time.LocalDate;
import java.util.List;

/** The host-side implementation of StsLogic. */
public class StsExtraBusinessLogicHostTestBase extends ExtraBusinessLogicHostTestBase
        implements StsLogic {

    private LocalDate deviceSpl = null;
    @Rule public DescriptionProvider descriptionProvider = new DescriptionProvider();

    public StsExtraBusinessLogicHostTestBase() {
        super();
        mDependentOnBusinessLogic = false;
    }

    @Override
    public List<String> getExtraBusinessLogics() {
        String stsDynamicPlan = getBuild().getBuildAttributes().get("sts-dynamic-plan");
        switch (stsDynamicPlan) {
            case "incremental":
                return StsLogic.STS_EXTRA_BUSINESS_LOGIC_INCREMENTAL;
            case "full":
                return StsLogic.STS_EXTRA_BUSINESS_LOGIC_FULL;
            default:
                throw new RuntimeException("Could not find Dynamic STS plan in build attributes");
        }
    }

    @Override
    public Description getTestDescription() {
        return descriptionProvider.getDescription();
    }

    @Override
    public LocalDate getDeviceSpl() {
        if (deviceSpl == null) {
            try {
                String splString = getDevice().getProperty("ro.build.version.security_patch");
                deviceSpl = SplUtils.localDateFromSplString(splString);
            } catch (DeviceNotAvailableException e) {
                throw new RuntimeException("couldn't get the security patch level", e);
            }
        }
        return deviceSpl;
    }
}
