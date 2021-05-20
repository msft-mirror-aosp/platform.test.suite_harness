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
 * limitations under the License
 */

package com.android.compatibility.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PropertyUtil} */
@RunWith(JUnit4.class)
public class PropertyUtilTest {
    private static final String FIRST_API_LEVEL = "ro.product.first_api_level";
    private static final String VENDOR_API_LEVEL = "ro.board.api_level";
    private static final String VENDOR_FIRST_API_LEVEL = "ro.board.first_api_level";
    private static final String VNDK_VERSION = "ro.vndk.version";

    private PropertyUtil mPropertyUtil;
    private ITestDevice mMockDevice;

    @Before
    public void setUp() {
        mPropertyUtil = new PropertyUtil();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
    }

    @Test
    public void testGetFirstApiLevelFromProductFirstApiLevel() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.getProperty(FIRST_API_LEVEL)).andReturn("31");
        EasyMock.replay(mMockDevice);
        assertEquals(31, mPropertyUtil.getFirstApiLevel(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    @Test
    public void testGetFirstApiLevelFromSdkVersion() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.getProperty(FIRST_API_LEVEL)).andReturn(null);
        EasyMock.expect(mMockDevice.getApiLevel()).andReturn(31);
        EasyMock.replay(mMockDevice);
        assertEquals(31, mPropertyUtil.getFirstApiLevel(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    @Test
    public void testGetVendorApiLevelFromVendorApiLevel() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.getProperty(VENDOR_API_LEVEL)).andReturn("31");
        EasyMock.replay(mMockDevice);
        assertEquals(31, mPropertyUtil.getVendorApiLevel(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    @Test
    public void testGetVendorApiLevelFromVendorFirstApiLevel() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.getProperty(VENDOR_API_LEVEL)).andReturn(null);
        EasyMock.expect(mMockDevice.getProperty(VENDOR_FIRST_API_LEVEL)).andReturn("31");
        EasyMock.replay(mMockDevice);
        assertEquals(31, mPropertyUtil.getVendorApiLevel(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    @Test
    public void testGetVendorApiLevelFromVndkVersion() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.getProperty(VENDOR_API_LEVEL)).andReturn(null);
        EasyMock.expect(mMockDevice.getProperty(VENDOR_FIRST_API_LEVEL)).andReturn(null);
        EasyMock.expect(mMockDevice.getProperty(VNDK_VERSION)).andReturn("31");
        EasyMock.replay(mMockDevice);
        assertEquals(31, mPropertyUtil.getVendorApiLevel(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    @Test
    public void testGetVendorApiLevelCurrent() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.getProperty(VENDOR_API_LEVEL)).andReturn(null);
        EasyMock.expect(mMockDevice.getProperty(VENDOR_FIRST_API_LEVEL)).andReturn(null);
        EasyMock.expect(mMockDevice.getProperty(VNDK_VERSION)).andReturn(null);
        EasyMock.replay(mMockDevice);
        assertEquals(PropertyUtil.API_LEVEL_CURRENT, mPropertyUtil.getVendorApiLevel(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    @Test
    public void testGetVendorApiLevelCurrent2() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.getProperty(VENDOR_API_LEVEL)).andReturn(null);
        EasyMock.expect(mMockDevice.getProperty(VENDOR_FIRST_API_LEVEL)).andReturn(null);
        EasyMock.expect(mMockDevice.getProperty(VNDK_VERSION)).andReturn("S");
        EasyMock.replay(mMockDevice);
        assertEquals(PropertyUtil.API_LEVEL_CURRENT, mPropertyUtil.getVendorApiLevel(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    @Test
    public void testIsVendorApiLevelNewerThan() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.getProperty(VENDOR_API_LEVEL)).andReturn(null);
        EasyMock.expect(mMockDevice.getProperty(VENDOR_FIRST_API_LEVEL)).andReturn("30");
        EasyMock.replay(mMockDevice);
        assertFalse(mPropertyUtil.isVendorApiLevelNewerThan(mMockDevice, 30));
        EasyMock.verify(mMockDevice);
    }

    @Test
    public void testIsVendorApiLevelAtLeast() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.getProperty(VENDOR_API_LEVEL)).andReturn("30");
        EasyMock.replay(mMockDevice);
        assertTrue(mPropertyUtil.isVendorApiLevelAtLeast(mMockDevice, 30));
        EasyMock.verify(mMockDevice);
    }
}
