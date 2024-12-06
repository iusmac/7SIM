package com.github.iusmac.sevensim.telephony

import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.R
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress

import com.github.iusmac.sevensim.test.assumeDeviceWithRealRadioCapabilities

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest

import java.util.Optional

import javax.inject.Inject

import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*

import org.junit.Assume.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
@MediumTest
class SubscriptionsComponentTest {
    private val TAG: String = this::class.java.simpleName

    @get:Rule
    val mHiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @Inject
    lateinit var mSubscriptionManager: SubscriptionManager

    @Inject
    lateinit var mSubscriptionsImpl: SubscriptionsImpl

    @Inject
    lateinit var mSubscriptionsImplLegacy: SubscriptionsImplLegacy

    private lateinit var mVisibleSubInfos: List<SubscriptionInfo>

    @Before
    fun setUp() {
        assumeDeviceWithRealRadioCapabilities()

        mHiltRule.inject()

        mVisibleSubInfos = mSubscriptionManager.selectableSubscriptionInfoList ?: emptyList()
        Log.d(TAG, "Found ${mVisibleSubInfos.size} subInfos.")
        assumeTrue("No visible SIM cards found! Should ignore test.", mVisibleSubInfos.isNotEmpty())
    }

    /**
     * Precondition:
     * - At least one SIM card is inserted in the device and it's operational.
     * - Device has the capability to turn on/off SIM cards out-of-the-box.
     */
    @Test
    @SdkSuppress(minSdkVersion = R)
    fun test_shouldReturnTheSameVisibleSubscriptionInfoListWhenQueriedBySubId() {
        assumeTrue(mSubscriptionManager.canDisablePhysicalSubscription())
        for (subInfo in mVisibleSubInfos) {
            val sub = mSubscriptionsImpl.getSubscriptionForSubId(subInfo.subscriptionId)
            Log.d(TAG, "Comparing $subInfo with $sub")
            assertThat(sub, `is`(not(Optional.empty())))
            with(sub.get()) {
                assertThat(id, `is`(subInfo.subscriptionId))
                assertThat(slotIndex, `is`(SubscriptionManager.INVALID_SIM_SLOT_INDEX))
            }
        }
    }

    /**
     * Precondition:
     * - At least one SIM card is inserted in the device.
     * - Device doesn't have the capability to turn on/off SIM cards out-of-the-box.
     */
    @Test
    @SdkSuppress(minSdkVersion = R)
    fun test_shouldReturnTheSameVisibleSubscriptionInfoListWhenQueriedBySimSlotIndex_SinceR() {
        assumeFalse(mSubscriptionManager.canDisablePhysicalSubscription())
        for (subInfo in mVisibleSubInfos) {
            val sub = mSubscriptionsImplLegacy.getSubscriptionForSimSlotIndex(subInfo.simSlotIndex)
            Log.d(TAG, "Comparing $subInfo with $sub")
            assertThat(sub, `is`(not(Optional.empty())))
            with(sub.get()) {
                assertThat(id, `is`(subInfo.subscriptionId))
                assertThat(slotIndex, `is`(subInfo.simSlotIndex))
            }
        }
    }

    /**
     * Precondition:
     * - At least one SIM card is inserted in the device.
     * - Device doesn't have the capability to turn on/off SIM cards out-of-the-box.
     */
    @Test
    @SdkSuppress(maxSdkVersion = Q)
    fun test_shouldReturnTheSameVisibleSubscriptionInfoListWhenQueriedBySimSlotIndex_BeforeR() {
        for (subInfo in mVisibleSubInfos) {
            val sub = mSubscriptionsImplLegacy.getSubscriptionForSimSlotIndex(subInfo.simSlotIndex)
            Log.d(TAG, "Comparing $subInfo with $sub")
            assertThat(sub, `is`(not(Optional.empty())))
            with(sub.get()) {
                assertThat(id, `is`(subInfo.subscriptionId))
                assertThat(slotIndex, `is`(subInfo.simSlotIndex))
            }
        }
    }
}
