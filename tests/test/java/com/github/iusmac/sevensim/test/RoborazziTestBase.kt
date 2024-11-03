package com.github.iusmac.sevensim.test

import com.github.takahirom.roborazzi.DefaultFileNameGenerator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.FileProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.RoborazziRule.Options
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory

import dagger.hilt.android.testing.HiltAndroidTest

import java.io.File

import org.junit.Rule

import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Base class for screenshot tests using Roborazzi. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.SmallPhone)
open class RoborazziTestBase : MockitoHiltAndroidTestBase() {
    @ExperimentalRoborazziApi
    @get:Rule
    val mRoborazziRule = RoborazziRule(
        options = Options(
            outputDirectoryPath = "tests/test/roborazzi".replace('/', File.separatorChar),
            outputFileProvider = customFileProvider,
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    outputDirectoryPath = roborazziSystemPropertyOutputDirectory() +
                        File.separator + this::class.java.name.transformJUnitTestSuiteToDirectory(),
                ),
            ),
        ),
    )
}

/**
 * Custom file provider that changes the default screenshot naming strategy to:
 * <code>[outputDir]/[test.package.and.ClassName]/[test_method_name].[ext]</code>
 *
 * In case of JUnit test suites, all the nested static classes will be in a separate directory:
 * <code>[outputDir]/[test.package.and.ClassName]/[SuiteClassName]/[test_method_name].[ext]</code>
 */
@ExperimentalRoborazziApi
private val customFileProvider: FileProvider = { description, directory, ext ->
    val testPackageAndClassAndMethod =
        DefaultFileNameGenerator.generateCountableOutputNameWithDescription(description)
    val testPackageAndClass = testPackageAndClassAndMethod
        .substringBeforeLast('.')
        .transformJUnitTestSuiteToDirectory()
    val method = testPackageAndClassAndMethod.substringAfterLast('.')
    File(directory.absolutePath + File.separator + testPackageAndClass, method + "." + ext)
}

/*
 * Transform the JUnit test suite class name, such as
 * <code>test.package.and.ClassName$SuiteClassName</code>, to be in a separate directory.
 */
private fun String.transformJUnitTestSuiteToDirectory() = replace('$', File.separatorChar)
