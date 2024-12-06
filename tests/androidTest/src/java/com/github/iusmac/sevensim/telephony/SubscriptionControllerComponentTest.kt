package com.github.iusmac.sevensim.telephony

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build.VERSION_CODES.R
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
import android.util.Log

import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.MediumTest

import com.github.iusmac.sevensim.DirectBootAwareBroadcastReceiver
import com.github.iusmac.sevensim.test.assumeDeviceWithRealRadioCapabilities
import com.github.iusmac.sevensim.test.setComponentEnabledSetting

import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest

import javax.inject.Inject

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*

import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*

import org.junit.After
import org.junit.Assume.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
@MediumTest
@SdkSuppress(minSdkVersion = R)
class SubscriptionControllerComponentTest {
    private val TAG: String = this::class.java.simpleName

    @Inject
    @ApplicationContext
    @Volatile lateinit var mApplicationContext: Context

    @Inject
    @Volatile lateinit var mSubscriptionManager: SubscriptionManager

    @Inject
    @Volatile lateinit var mSubscriptionController: SubscriptionController

    @get:Rule
    val mHiltRule: HiltAndroidRule = HiltAndroidRule(this)

    private val mSubId: Int by lazy() {
        val subInfo = mSubscriptionManager.selectableSubscriptionInfoList.firstOrNull()
        subInfo?.subscriptionId ?: INVALID_SUBSCRIPTION_ID
    }

    @Volatile private var mInitialSubEnabledState: Boolean = false

    @Before
    @UiThreadTest // Field injection should be done on a thread that invoked Looper.prepare()
    fun setUp() {
        assumeDeviceWithRealRadioCapabilities()

        mHiltRule.inject()

        assumeTrue("No support to disable physical SIM card without taking it out.",
            mSubscriptionManager.canDisablePhysicalSubscription())

        Log.d(TAG, "subId: $mSubId")
        assumeThat("No SIM card inserted! Should ignore test.", mSubId,
            `is`(not(INVALID_SUBSCRIPTION_ID)))

        mInitialSubEnabledState = areUiccApplicationsEnabled()
        Log.d(TAG, "initialSubEnabledState: $mInitialSubEnabledState")

        // We must ignore the CARRIER_CONFIG_CHANGED event while performing SIM subscription state
        // change requests, otherwise the test may be marked as failed due to an abrupt app close
        val cn = ComponentName(mApplicationContext, DirectBootAwareBroadcastReceiver::class.java)
        setComponentEnabledSetting(mApplicationContext, cn, false)
    }

    /**
     * Precondition:
     * - At least one SIM card is inserted in the device.
     */
    @Test
    fun test_setUiccApplicationsEnabled_shouldToggleCurrentState() {
        setUiccApplicationsEnabledSync(!mInitialSubEnabledState)
        assertThat(areUiccApplicationsEnabled(), `is`(not(mInitialSubEnabledState)))
    }

    @After
    fun tearDown() {
        // Restore SIM subscription enabled state
        if (mInitialSubEnabledState != areUiccApplicationsEnabled()) {
            setUiccApplicationsEnabledSync(mInitialSubEnabledState)
        }

        val cn = ComponentName(mApplicationContext, DirectBootAwareBroadcastReceiver::class.java)
        setComponentEnabledSetting(mApplicationContext, cn, true)
    }

    private fun setUiccApplicationsEnabledSync(enabled: Boolean) = runBlocking {
        val request = async(Dispatchers.Default) {
            mSubscriptionController.setUiccApplicationsEnabled(mSubId, enabled)
        }
        withTimeout(5.seconds) { request.await() }
    }

    private fun areUiccApplicationsEnabled() = with(mSubscriptionManager) {
        getSelectableSubscriptionInfoList()
            .first { sub -> sub.subscriptionId == mSubId }
            .areUiccApplicationsEnabled()
    }
}
