package com.github.iusmac.sevensim.telephony

import android.content.ComponentName
import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.SIM_STATE_ABSENT
import android.telephony.TelephonyManager.SIM_STATE_UNKNOWN
import android.util.Log

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest

import com.github.iusmac.sevensim.DirectBootAwareBroadcastReceiver
import com.github.iusmac.sevensim.test.assumeDeviceWithRealRadioCapabilities
import com.github.iusmac.sevensim.test.setComponentEnabledSetting

import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest

import javax.inject.Inject

import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

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
class TelephonyControllerComponentTest {
    private val TAG: String = this::class.java.simpleName

    @Inject
    @ApplicationContext
    lateinit var mApplicationContext: Context

    @Inject
    lateinit var mTelephonyManager: TelephonyManager

    @Inject
    lateinit var mTelephonyController: TelephonyController

    @get:Rule
    val mHiltRule: HiltAndroidRule = HiltAndroidRule(this)

    private var mSlotIndex: Int = SubscriptionManager.INVALID_SIM_SLOT_INDEX

    @TelephonyManager.SimState
    private var mInitialSimState: Int = SIM_STATE_UNKNOWN

    @Before
    fun setUp() {
        assumeDeviceWithRealRadioCapabilities()

        mHiltRule.inject()

        mSlotIndex = mTelephonyManager.slotIndex
        mInitialSimState = mTelephonyManager.getSimState(mSlotIndex)
        Log.d(TAG, "slotIndex: $mSlotIndex, initialSimState: $mInitialSimState")
        assumeThat("SIM card is either not powered on, absent, or in an intermediate state."
            + " Should ignore test.", mInitialSimState,
                both(not(SIM_STATE_ABSENT)).and(not(SIM_STATE_UNKNOWN)))

        // We must ignore the CARRIER_CONFIG_CHANGED event while performing SIM card state change
        // requests, otherwise the test may be marked as failed due to an abrupt app close
        val cn = ComponentName(mApplicationContext, DirectBootAwareBroadcastReceiver::class.java)
        setComponentEnabledSetting(mApplicationContext, cn, false)
    }

    /**
     * Precondition:
     * - At least one SIM card is inserted in the device and it's operational.
     */
    @Test
    fun test_setSimState_shouldPowerOff() {
        setSimStateSync(false)
        assertThat(mTelephonyManager.getSimState(mSlotIndex),
            both(`is`(SIM_STATE_ABSENT)).and(not(mInitialSimState)))
    }

    @After
    fun tearDown() {
        // Power up SIM card
        if (mInitialSimState != mTelephonyManager.getSimState(mSlotIndex)) {
            setSimStateSync(true)
        }

        val cn = ComponentName(mApplicationContext, DirectBootAwareBroadcastReceiver::class.java)
        setComponentEnabledSetting(mApplicationContext, cn, true)
    }

    private fun setSimStateSync(enabled: Boolean) = runBlocking {
        val request = async(Dispatchers.Default) {
            mTelephonyController.setSimState(mSlotIndex, enabled, false)
        }
        withTimeout(10.seconds) { request.await() }
        delay(3.seconds)
    }
}
