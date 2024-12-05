package com.github.iusmac.sevensim

import android.os.SystemProperties

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest

import java.util.Optional

import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*

import org.junit.After
import org.junit.Assume.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class SysPropComponentTest {
    private val TEST_PROP_NAME = "test_prop"
    private val TEST_PROP_RAW = "service.7sim.$TEST_PROP_NAME"
    private val TEST_SYSPROP = SysProp(TEST_PROP_NAME, /*isPersistent=*/ false)

    @Before
    fun setUp() {
        // Ensure the property has not yet been initialized or has been properly reset
        assumeThat(SystemProperties.get(TEST_PROP_RAW, null), emptyString())
    }

    /** Verify the app can read/write its own system properties and not blocked by SELinux. */
    @Test
    fun test_SetAndGet() {
        val value = "qwerty1234"
        TEST_SYSPROP.set(Optional.of(value))
        assertThat(TEST_SYSPROP.get(Optional.empty()), `is`(Optional.of(value)))
        assertThat(TEST_SYSPROP.get(Optional.empty()),
            `is`(Optional.of(SystemProperties.get(TEST_PROP_RAW, null))))
    }

    @After
    fun tearDown() {
        SystemProperties.set(TEST_PROP_RAW, null)
    }
}
