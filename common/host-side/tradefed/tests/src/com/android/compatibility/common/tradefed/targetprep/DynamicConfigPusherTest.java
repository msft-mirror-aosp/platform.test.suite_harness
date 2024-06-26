/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link DynamicConfigPusher}.
 */
@RunWith(JUnit4.class)
public class DynamicConfigPusherTest {
    private static final String RESOURCE_DYNAMIC_CONFIG = "test-dynamic-config";
    private static final String RUN_TESTS_AS_USER_KEY = "RUN_TESTS_AS_USER";
    private DynamicConfigPusher mPreparer;
    private ITestDevice mMockDevice;
    private CompatibilityBuildHelper mMockBuildHelper;
    private IBuildInfo mMockBuildInfo;
    private IInvocationContext mModuleContext;
    private TestInformation mTestInfo;

    @Before
    public void setUp() {
        mModuleContext = new InvocationContext();
        mModuleContext.setConfigurationDescriptor(new ConfigurationDescriptor());
        mPreparer = new DynamicConfigPusher();
        mMockDevice = Mockito.mock(ITestDevice.class);
        mMockBuildInfo = Mockito.mock(IBuildInfo.class);
        mMockBuildHelper = new CompatibilityBuildHelper(mMockBuildInfo);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        mModuleContext.addDeviceBuildInfo("device", mMockBuildInfo);
        mModuleContext.addAllocatedDevice("device", mMockDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(mModuleContext).build();
    }

    /**
     * Test getSuiteName from /test-suite-info.properties.
     */
    @Test
    public void testGetSuiteName_fromTestSuiteInfo() throws Exception {
        mPreparer = new DynamicConfigPusher();
        mPreparer.setInvocationContext(mModuleContext);

        assertNotNull(mPreparer.getSuiteName());
    }

    /**
     * Test getSuiteName from test-suite-tag.
     */
    @Test
    public void testGetSuiteName_fromTestSuiteTag() throws Exception {
        mPreparer = new DynamicConfigPusher();
        mModuleContext
                .getConfigurationDescriptor()
                .setSuiteTags(Arrays.asList("cts", "cts-instant", "gts"));
        mPreparer.setInvocationContext(mModuleContext);

        assertNotNull(mPreparer.getSuiteName());
    }

    /**
     * Test that when we look up resources locally, we search them from the build helper.
     */
    @Test
    public void testLocalRead_fromDynamicConfigName() throws Exception {
        OptionSetter setter = new OptionSetter(mPreparer);
        setter.setOptionValue("config-filename", "config-test-name");
        setter.setOptionValue("dynamic-config-name", "dynamic-config-test-name");
        setter.setOptionValue("extract-from-resource", "false");

        File check = new File("anyfilewilldo");
        mMockBuildHelper = new CompatibilityBuildHelper(mMockBuildInfo) {
            @Override
            public File getTestFile(String filename) throws FileNotFoundException {
                return check;
            }
        };

        File res = mPreparer.getLocalConfigFile(mMockBuildHelper, mMockDevice);
        assertEquals(check, res);
    }

    /**
     * Test that when we look up resources locally, we search them from the build helper.
     */
    @Test
    public void testLocalRead() throws Exception {
        OptionSetter setter = new OptionSetter(mPreparer);
        setter.setOptionValue("config-filename", "config-test-name");
        setter.setOptionValue("extract-from-resource", "false");

        File check = new File("anyfilewilldo");
        mMockBuildHelper = new CompatibilityBuildHelper(mMockBuildInfo) {
            @Override
            public File getTestFile(String filename) throws FileNotFoundException {
                return check;
            }
        };

        File res = mPreparer.getLocalConfigFile(mMockBuildHelper, mMockDevice);
        assertEquals(check, res);
    }

    /**
     * Test that when we look up resources locally, we search them from the build helper and throw
     * if it's not found.
     */
    @Test
    public void testLocalRead_fileNotFound() throws Exception {
        OptionSetter setter = new OptionSetter(mPreparer);
        setter.setOptionValue("config-filename", "config-test-name");
        setter.setOptionValue("extract-from-resource", "false");

        mMockBuildHelper = new CompatibilityBuildHelper(mMockBuildInfo) {
            @Override
            public File getTestFile(String filename) throws FileNotFoundException {
                throw new FileNotFoundException("test");
            }
        };
        try {
            mPreparer.getLocalConfigFile(mMockBuildHelper, mMockDevice);
            fail("Should have thrown an exception.");
        } catch (TargetSetupError expected) {
            // expected
            assertEquals(
                    "Cannot get local dynamic config file from test directory",
                    expected.getMessage());
        }
    }

    /**
     * Test when we try to unpack a resource but it does not exists.
     */
    @Test
    public void testResourceRead_notFound() throws Exception {
        OptionSetter setter = new OptionSetter(mPreparer);
        setter.setOptionValue("config-filename", "not-an-existing-resource-name");
        setter.setOptionValue("extract-from-resource", "true");
        try {
            mPreparer.getLocalConfigFile(mMockBuildHelper, mMockDevice);
            fail("Should have thrown an exception.");
        } catch (TargetSetupError expected) {
            // expected
            assertEquals(
                    "Fail to unpack 'not-an-existing-resource-name.dynamic' from resources",
                    expected.getMessage());
        }
    }

    /**
     * Test when we get a config from the resources.
     */
    @Test
    public void testResourceRead() throws Exception {
        OptionSetter setter = new OptionSetter(mPreparer);
        setter.setOptionValue("config-filename", RESOURCE_DYNAMIC_CONFIG);
        setter.setOptionValue("extract-from-resource", "true");
        File res = null;
        try {
            res = mPreparer.getLocalConfigFile(mMockBuildHelper, mMockDevice);
            assertTrue(res.exists());
            assertTrue(FileUtil.readStringFromFile(res).contains("<dynamicConfig>"));
        } finally {
            FileUtil.deleteFile(res);
        }
    }

    /**
     * Test when we get a config from the resources under the alternative name.
     */
    @Test
    public void testResourceRead_resourceFileName() throws Exception {
        OptionSetter setter = new OptionSetter(mPreparer);
        setter.setOptionValue("config-filename", "moduleName");
        setter.setOptionValue("extract-from-resource", "true");
        // Look up the file under that name instead of the config-filename
        setter.setOptionValue("dynamic-resource-name", RESOURCE_DYNAMIC_CONFIG);
        File res = null;
        try {
            res = mPreparer.getLocalConfigFile(mMockBuildHelper, mMockDevice);
            assertTrue(res.exists());
            assertTrue(FileUtil.readStringFromFile(res).contains("<dynamicConfig>"));
        } finally {
            FileUtil.deleteFile(res);
        }
    }

    @Test
    public void testSetUp_usesRunTestsAsUserFromProperty() throws Exception {
        final File[] localConfig = new File[1];
        OptionSetter setter = prepareSetupTestTarget(localConfig);
        // Set target to DEVICE.
        setter.setOptionValue("target", "device");

        int runTestsAsUserId = 101;
        mTestInfo.properties().put(RUN_TESTS_AS_USER_KEY, String.valueOf(runTestsAsUserId));
        when(mMockDevice.pushFile(Mockito.any(), Mockito.any(), Mockito.anyInt())).thenReturn(true);

        mPreparer.setUp(mTestInfo);

        verify(mMockDevice, Mockito.never()).getCurrentUser();
        // pushFile() is called for the RUN_TESTS_AS_USER set in the TestInfo property.
        verify(mMockDevice).pushFile(Mockito.any(), Mockito.any(), Mockito.eq(runTestsAsUserId));
    }

    @Test
    public void testSetUp_currentUser() throws Exception {
        final File[] localConfig = new File[1];
        OptionSetter setter = prepareSetupTestTarget(localConfig);
        // Set target to DEVICE.
        setter.setOptionValue("target", "device");

        int currentUserId = 100;
        when(mMockDevice.getCurrentUser()).thenReturn(currentUserId);
        when(mMockDevice.pushFile(Mockito.any(), Mockito.any(), Mockito.anyInt())).thenReturn(true);

        mPreparer.setUp(mTestInfo);

        // pushFile() is called for the current user.
        verify(mMockDevice).pushFile(Mockito.any(), Mockito.any(), Mockito.eq(currentUserId));
    }

    /**
     * Test an end-to-end usage of the dynamic config file from the jar.
     */
    @Test
    public void testSetUp() throws Exception {
        final File[] localConfig = new File[1];
        prepareSetupTestTarget(localConfig);

        Map<String, String> attributes = new HashMap<>();
        attributes.put(CompatibilityBuildHelper.SUITE_VERSION, "v1");
        when(mMockBuildInfo.getBuildAttributes()).thenReturn(attributes);
        Collection<VersionedFile> versionedFiles = new ArrayList<VersionedFile>();
        when(mMockBuildInfo.getFiles()).thenReturn(versionedFiles);
        mPreparer.setInvocationContext(mModuleContext);

        mPreparer.setUp(mTestInfo);
        ArgumentCaptor<File> capture = ArgumentCaptor.forClass(File.class);
        verify(mMockBuildInfo)
                .setFile(
                        Mockito.contains("moduleName"),
                        capture.capture(),
                        Mockito.eq("DYNAMIC_CONFIG_FILE:moduleName"));
        assertNotNull(localConfig[0]);
        // Ensure that the extracted file was deleted.
        assertFalse(localConfig[0].exists());
        File dynamicFile = capture.getValue();
        assertTrue(dynamicFile.exists());
        FileUtil.deleteFile(dynamicFile);
    }

    /**
     * Prepares for running tests for DynamicConfigPusher#setUp method.
     *
     * @return an {@link OptionSetter} so that each test can override option valuses as necessary.
     */
    private OptionSetter prepareSetupTestTarget(File[] localConfig) throws Exception {
        mPreparer =
                new DynamicConfigPusher() {
                    @Override
                    File mergeConfigFiles(
                            File localConfigFile,
                            String apfeConfigInJson,
                            String moduleName,
                            ITestDevice device)
                            throws TargetSetupError {
                        localConfig[0] = localConfigFile;
                        return super.mergeConfigFiles(
                                localConfigFile, apfeConfigInJson, moduleName, device);
                    }
                };
        OptionSetter setter = new OptionSetter(mPreparer);
        setter.setOptionValue("has-server-side-config", "false");
        setter.setOptionValue("config-filename", "moduleName");
        setter.setOptionValue("extract-from-resource", "true");
        // Look up the file under that name instead of the config-filename
        setter.setOptionValue("dynamic-resource-name", RESOURCE_DYNAMIC_CONFIG);

        return setter;
    }
}
