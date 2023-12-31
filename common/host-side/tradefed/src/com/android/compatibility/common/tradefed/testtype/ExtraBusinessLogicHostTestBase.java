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

package com.android.compatibility.common.tradefed.testtype;

import static org.junit.Assert.assertTrue;

import com.android.compatibility.common.util.BusinessLogicExecutor;
import com.android.compatibility.common.util.BusinessLogicHostExecutor;
import com.android.tradefed.log.LogUtil.CLog;

import org.junit.Before;

import java.util.List;

/**
 * Host-side base class for tests to run extra Business Logics in addition to the test-specific
 * Business Logics.
 *
 * <p>Used when running a common set of business logics against several tests.
 *
 * <p>Usage: 1. Implement the common logic in an interface with default methods. 2. Extend this
 * class and implement the interface.
 *
 * <p>Now Business Logics rules and actions can be called from the GCL by using the interface fully
 * qualified name.
 */
public abstract class ExtraBusinessLogicHostTestBase extends BusinessLogicHostTestBase {

    protected boolean mDependentOnBusinessLogic = true;

    public abstract List<String> getExtraBusinessLogics();

    @Before
    @Override
    public void handleBusinessLogic() {
        loadBusinessLogic();
        if (mDependentOnBusinessLogic) {
            assertTrue(String.format(
                    "Test \"%s\" is unable to execute as it depends on the missing remote "
                    + "configuration.", mTestCase.getMethodName()), mCanReadBusinessLogic);
        } else if (!mCanReadBusinessLogic) {
            CLog.i("Skipping Business Logic for %s", mTestCase.getMethodName());
            return;
        }

        BusinessLogicExecutor executor = new BusinessLogicHostExecutor(
                getDevice(), getBuild(), this, mBusinessLogic.getRedactionRegexes());
        for (String extraBusinessLogic : getExtraBusinessLogics()) {
            if (!mBusinessLogic.hasLogicFor(extraBusinessLogic)) {
                throw new RuntimeException(String.format(
                        "can't find extra business logic for %s.", extraBusinessLogic));
            }
            mBusinessLogic.applyLogicFor(extraBusinessLogic, executor);
        }
        executeBusinessLogic();
    }

}
