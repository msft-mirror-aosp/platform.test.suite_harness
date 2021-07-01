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
package com.android.compatibility.common.tradefed.testtype.suite;

import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.testtype.suite.SuiteModuleLoader;
import com.android.tradefed.testtype.suite.SuiteTestFilter;
import com.android.tradefed.util.AbiUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class CompatibilitySuiteModuleLoader extends SuiteModuleLoader {

    /**
     * Ctor for the CompatibilitySuiteModuleLoader.
     *
     * @param includeFilters The formatted and parsed include filters.
     * @param excludeFilters The formatted and parsed exclude filters.
     * @param testArgs the list of test ({@link IRemoteTest}) arguments.
     * @param moduleArgs the list of module arguments.
     */
    public CompatibilitySuiteModuleLoader(
            Map<String, LinkedHashSet<SuiteTestFilter>> includeFilters,
            Map<String, LinkedHashSet<SuiteTestFilter>> excludeFilters,
            List<String> testArgs,
            List<String> moduleArgs) {
        super(includeFilters,excludeFilters,testArgs,moduleArgs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFiltersToTest(
            IRemoteTest test,
            IAbi abi,
            String name,
            Map<String, LinkedHashSet<SuiteTestFilter>> includeFilters,
            Map<String, LinkedHashSet<SuiteTestFilter>> excludeFilters) {
        String moduleId = AbiUtils.createId(abi.getName(), name);
        // Override the default behavior. Compatibility Suites expect the filter receiver.
        if (!(test instanceof ITestFilterReceiver)) {
            throw new IllegalArgumentException(String.format(
                    "Test in module %s must implement ITestFilterReceiver.", moduleId));
        }
        super.addFiltersToTest(test,abi,name,includeFilters,excludeFilters);
    }
}
