/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.suite.ModuleDefinition;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ITargetPreparer} that attempts to pull any number of files from device path(s) to any
 * xTS report.
 *
 * <p>Should be performed *before* a new build is flashed, and *after* DeviceSetup is run (if
 * enabled).
 */
@OptionClass(alias = "interactive-result-collector")
public class InteractiveResultCollector extends BaseTargetPreparer
        implements IInvocationContextReceiver {

    @Option(
            name = "host-path",
            description =
                    "The host-side relative path to the directory where results should be"
                        + " collected. If not specified, defaults to the 'screenshots' directory.")
    private String hostPath = "screenshots";

    @Option(
            name = "create-module-dir",
            description =
                    "Whether creating a sub-directory under the host-path to distinguish "
                            + "files of different modules.")
    private boolean createModuleDir = false;

    @Option(
            name = "device-cleanup",
            description =
                    "Whether all files in the device folders should be cleaned up during setup. "
                            + "Note that the preparer does not verify that files/directories have "
                            + "been deleted successfully.")
    private boolean deviceCleanup = true;

    @Option(
            name = "device-paths",
            description = "The list of paths to the files stored on the device.")
    private List<String> devicePaths = new ArrayList<>();

    // Paired with create-module-dir option to create the sub-directory with the module name.
    private String mModuleName = null;

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        ITestDevice mDevice = testInfo.getDevice();
        if (!(testInfo.getBuildInfo() instanceof IDeviceBuildInfo)) {
            throw new TargetSetupError(
                    "Invalid buildInfo, expecting an IDeviceBuildInfo",
                    mDevice.getDeviceDescriptor(),
                    InfraErrorIdentifier.UNDETERMINED);
        }
        if (deviceCleanup && !devicePaths.isEmpty()) {
            for (String devicePath : devicePaths) {
                if (!devicePath.isEmpty()) {
                    CLog.d("Start clean up path: %s", devicePath);
                    mDevice.executeAdbCommand("shell", "rm", "-rf", devicePath);
                }
            }
        }
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        if (e != null && (e instanceof DeviceNotAvailableException)) {
            CLog.e("Module finished with DeviceNotAvailable, skip collecting results.");
            return;
        }

        File hostResultDir = null;
        if (!devicePaths.isEmpty()) {
            try {
                hostResultDir = getHostResultDir(testInfo);
                if (!hostResultDir.exists()) {
                    hostResultDir.mkdirs();
                }
            } catch (FileNotFoundException exception) {
                CLog.e(exception);
                return;
            }
        }
        if (hostResultDir == null) {
            // No host result directory, either no device paths, or fail to create it.
            return;
        }

        ITestDevice testDevice = testInfo.getDevice();
        for (String devicePath : devicePaths) {
            if (!devicePath.isEmpty()) {
                if (testDevice.pullDir(devicePath, hostResultDir)) {
                    CLog.d(
                            String.format(
                                    "Successfully pulled %s to %s.",
                                    devicePath, hostResultDir.getAbsolutePath()));
                }
            }
        }
    }

    @Override
    public void setInvocationContext(IInvocationContext invocationContext) {
        if (createModuleDir
                && invocationContext.getAttributes().get(ModuleDefinition.MODULE_NAME) != null) {
            mModuleName =
                    invocationContext.getAttributes().get(ModuleDefinition.MODULE_NAME).get(0);
        }
    }

    private File getHostResultDir(TestInformation testInfo) throws FileNotFoundException {
        File resultDir = new CompatibilityBuildHelper(testInfo.getBuildInfo()).getResultDir();
        return mModuleName == null
                ? Paths.get(resultDir.getAbsolutePath(), hostPath).toFile()
                : Paths.get(resultDir.getAbsolutePath(), hostPath, mModuleName).toFile();
    }
}
