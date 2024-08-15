/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** A reporter that helps merge and generate result files required for xTS Interactive tests. */
@OptionClass(alias = "result-reporter")
public class InteractiveResultReporter implements ITestInvocationListener {

    // The default directory under results/ which contains all screenshot files of xTS Interactive
    // tests.
    @VisibleForTesting static final String SCREENSHOTS_DIR_NAME = "screenshots";

    // The name of the XML file that contains the info of all screenshot files taken during xTS
    // Interactive tests' execution.
    @VisibleForTesting
    static final String SCREENSHOTS_METADATA_FILE_NAME = "screenshots_metadata.xml";

    // XML constants
    @VisibleForTesting static final String ENCODING = "UTF-8";
    @VisibleForTesting static final String NS = null;
    @VisibleForTesting static final String NAME_ATTR = "name";
    @VisibleForTesting static final String ABI_ATTR = "abi";
    private static final String DESCRIPTION_ATTR = "description";

    private static final String RESULT_TAG = "Result";
    @VisibleForTesting static final String MODULE_TAG = "Module";
    @VisibleForTesting static final String CASE_TAG = "TestCase";
    @VisibleForTesting static final String TEST_TAG = "Test";
    private static final String SCREENSHOTS_TAG = "Screenshots";
    @VisibleForTesting static final String SCREENSHOT_TAG = "Screenshot";

    // Default module name for all screenshot files that don't belong to a module.
    @VisibleForTesting static final String DEFAULT_MODULE_NAME = "UNKNOWN_MODULE";

    /** A model that contains the required data to create a new screenshot tag in an XML tree. */
    @VisibleForTesting
    static final class ScreenshotTagData {

        /** The name of the test case tag the screenshot tag belongs to. */
        final String mTestCaseName;

        /** The name of the test tag the screenshot tag belongs to. */
        final String mTestName;

        /** The name of the screenshot tag. */
        final String mScreenshotName;

        /** The description of the screenshot tag. */
        final String mScreenshotDescription;

        ScreenshotTagData(
                String testCaseName,
                String testName,
                String screenshotName,
                String screenshotDescription) {
            mTestCaseName = testCaseName;
            mTestName = testName;
            mScreenshotName = screenshotName;
            mScreenshotDescription = screenshotDescription;
        }
    }

    private CompatibilityBuildHelper mBuildHelper;

    /** The root directory of all results of this invocation. */
    private File mResultDir;

    @Override
    public void invocationStarted(IInvocationContext context) {
        if (mBuildHelper == null) {
            mBuildHelper = new CompatibilityBuildHelper(context.getBuildInfos().get(0));
            if (mResultDir == null) {
                try {
                    mResultDir = mBuildHelper.getResultDir();
                    CLog.i("Initialized mResultDir: %s", mResultDir);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(
                            "An initialized result directory is required for the reporter!", e);
                }
            }
        }
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        if (!Files.exists(Paths.get(mResultDir.getAbsolutePath(), SCREENSHOTS_DIR_NAME))) {
            CLog.i("No screenshot files are generated for the invocation.");
            return;
        }
        try {
            genScreenshotsMetadataFile(getScreenshotsMetadataFilePath());
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException(
                    "Failed to generate the " + SCREENSHOTS_METADATA_FILE_NAME, e);
        }
    }

    /** Gets the {@code File} that represents the path to the screenshot metadata file. */
    @VisibleForTesting
    File getScreenshotsMetadataFilePath() {
        return Paths.get(
                        mResultDir.getAbsolutePath(),
                        SCREENSHOTS_DIR_NAME,
                        SCREENSHOTS_METADATA_FILE_NAME)
                .toFile();
    }

    /** Generates the screenshot metadata file under the result directory. */
    @VisibleForTesting
    void genScreenshotsMetadataFile(File screenshotsMetadataFile)
            throws IOException, XmlPullParserException {
        XmlSerializer serializer =
                XmlPullParserFactory.newInstance(
                                "org.kxml2.io.KXmlParser,org.kxml2.io.KXmlSerializer", null)
                        .newSerializer();
        serializer.setOutput(new FileOutputStream(screenshotsMetadataFile), ENCODING);
        serializer.startDocument(ENCODING, false);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serializer.processingInstruction(
                "xml-stylesheet type=\"text/xsl\" href=\"compatibility_result.xsl\"");
        serializer.startTag(NS, RESULT_TAG);

        List<String> moduleNameWithAbis = new ArrayList<>();
        List<String> screenshotsInRoot = new ArrayList<>();
        try (Stream<Path> fileOrDirs =
                Files.list(Paths.get(mResultDir.getAbsolutePath(), SCREENSHOTS_DIR_NAME))) {
            fileOrDirs.forEach(
                    fileOrDir -> {
                        if (Files.isDirectory(fileOrDir)) {
                            moduleNameWithAbis.add(fileOrDir.getFileName().toString());
                        } else if (isScreenshotFile(fileOrDir)) {
                            screenshotsInRoot.add(fileOrDir.getFileName().toString());
                        }
                    });
        }

        // To keep module names in the metadata XML sorted.
        Collections.sort(moduleNameWithAbis);
        for (String moduleNameWithAbi : moduleNameWithAbis) {
            serializer.startTag(NS, MODULE_TAG);
            addModuleTagAttributes(serializer, moduleNameWithAbi);

            List<String> screenshotsOfModule = new ArrayList<>();
            try (Stream<Path> fileOrDirs =
                    Files.list(
                            Paths.get(
                                    mResultDir.getAbsolutePath(),
                                    SCREENSHOTS_DIR_NAME,
                                    moduleNameWithAbi))) {
                fileOrDirs.forEach(
                        fileOrDir -> {
                            if (!Files.isDirectory(fileOrDir) && isScreenshotFile(fileOrDir)) {
                                screenshotsOfModule.add(fileOrDir.getFileName().toString());
                            }
                        });
            }
            addScreenshotTags(serializer, screenshotsOfModule);

            serializer.endTag(NS, MODULE_TAG);
        }

        // All screenshots under the root directory are under the default module.
        if (!screenshotsInRoot.isEmpty()) {
            serializer.startTag(NS, MODULE_TAG);
            serializer.attribute(NS, NAME_ATTR, DEFAULT_MODULE_NAME);

            // No need to sort screenshotsInRoot as the tags map is sorted.
            addScreenshotTags(serializer, screenshotsInRoot);

            serializer.endTag(NS, MODULE_TAG);
        }

        serializer.endTag(NS, RESULT_TAG);
        serializer.endDocument();
        CLog.i("Successfully generated the screenshots metadata file: %s", screenshotsMetadataFile);
    }

    /** Adds the name and abi attributes (if have) for the <Module> tag. */
    static void addModuleTagAttributes(XmlSerializer serializer, String moduleNameWithAbi)
            throws IOException {
        String[] splitModuleAbis = moduleNameWithAbi.split("__");
        if (splitModuleAbis.length == 2) {
            serializer.attribute(NS, NAME_ATTR, splitModuleAbis[0]);
            serializer.attribute(NS, ABI_ATTR, splitModuleAbis[1]);
        } else {
            serializer.attribute(NS, NAME_ATTR, moduleNameWithAbi);
        }
    }

    /** Checks if the given {@link Path} is a screenshot file. */
    static boolean isScreenshotFile(Path filePath) {
        return filePath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png");
    }

    /** Parses a list of screenshot file names to add tags into the given {@code XmlSerializer}. */
    @VisibleForTesting
    static void addScreenshotTags(XmlSerializer serializer, List<String> screenshotFileNames)
            throws IOException, XmlPullParserException {
        Map<String, Map<String, List<ScreenshotTagData>>> screenshotTagDatas =
                getScreenshotTagDatas(screenshotFileNames);
        for (String testCaseName : screenshotTagDatas.keySet()) {
            serializer.startTag(NS, CASE_TAG);
            serializer.attribute(NS, NAME_ATTR, testCaseName);

            Map<String, List<ScreenshotTagData>> testCaseScreenshotTagDatas =
                    screenshotTagDatas.get(testCaseName);
            for (String testName : testCaseScreenshotTagDatas.keySet()) {
                serializer.startTag(NS, TEST_TAG);
                serializer.attribute(NS, NAME_ATTR, testName);
                serializer.startTag(NS, SCREENSHOTS_TAG);

                List<ScreenshotTagData> testScreenshotTagDatas =
                        testCaseScreenshotTagDatas.get(testName);
                for (ScreenshotTagData tagData : testScreenshotTagDatas) {
                    serializer.startTag(NS, SCREENSHOT_TAG);
                    serializer.attribute(NS, NAME_ATTR, tagData.mScreenshotName);
                    serializer.attribute(NS, DESCRIPTION_ATTR, tagData.mScreenshotDescription);
                    serializer.endTag(NS, SCREENSHOT_TAG);
                }
                serializer.endTag(NS, SCREENSHOTS_TAG);
                serializer.endTag(NS, TEST_TAG);
            }
            serializer.endTag(NS, CASE_TAG);
        }
    }

    /**
     * Gets TestClass -> (TestCase -> List of screenshots mappings) mappings by the given list of
     * screenshot file names.
     */
    @VisibleForTesting
    static Map<String, Map<String, List<ScreenshotTagData>>> getScreenshotTagDatas(
            List<String> screenshotFileNames) {
        Map<String, Map<String, List<ScreenshotTagData>>> screenshotTagDatas = new TreeMap<>();
        for (String screenshotFileName : screenshotFileNames) {
            ScreenshotTagData screenshotTagData = getScreenshotTagData(screenshotFileName);
            screenshotTagDatas.putIfAbsent(screenshotTagData.mTestCaseName, new TreeMap<>());

            Map<String, List<ScreenshotTagData>> testCaseScreenshotTagDatas =
                    screenshotTagDatas.get(screenshotTagData.mTestCaseName);
            testCaseScreenshotTagDatas.putIfAbsent(screenshotTagData.mTestName, new ArrayList<>());
            testCaseScreenshotTagDatas.get(screenshotTagData.mTestName).add(screenshotTagData);
        }
        return screenshotTagDatas;
    }

    /** Parses the given screenshot file name to get a {@link ScreenshotTagData}. */
    @VisibleForTesting
    static ScreenshotTagData getScreenshotTagData(String screenshotFileName) {
        String[] screenshotDetails = screenshotFileName.split("__");
        // The length of the array is 3 if the screenshot is taken via Interactive framework.
        if (screenshotDetails.length == 3) {
            String[] testDetails = screenshotDetails[0].split("#");
            // If com.android.interactive.testrules.TestNameSaver is enabled,
            // the test class and test case are parsed. Otherwise aren't.
            if (testDetails.length == 2) {
                return new ScreenshotTagData(
                        testDetails[0], testDetails[1], screenshotFileName, screenshotDetails[1]);
            } else {
                CLog.w(
                        "Found a screenshot that doesn't contain test package and class info: %s",
                        screenshotFileName);
                return new ScreenshotTagData(
                        screenshotDetails[0],
                        screenshotDetails[0],
                        screenshotFileName,
                        screenshotDetails[1]);
            }
        } else {
            CLog.i(
                    "Found a screenshot that isn't taken via Interactive library: %s",
                    screenshotFileName);
            return new ScreenshotTagData(
                    screenshotFileName, screenshotFileName, screenshotFileName, screenshotFileName);
        }
    }
}
