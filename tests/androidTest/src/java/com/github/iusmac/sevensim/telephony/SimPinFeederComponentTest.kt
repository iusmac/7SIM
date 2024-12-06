package com.github.iusmac.sevensim.telephony

import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.SIM_STATE_ABSENT
import android.telephony.TelephonyManager.SIM_STATE_PIN_REQUIRED
import android.telephony.TelephonyManager.SIM_STATE_PUK_REQUIRED
import android.util.Log

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest

import com.github.iusmac.sevensim.test.assumeDeviceWithRealRadioCapabilities

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest

import javax.inject.Inject

import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.*

import org.hamcrest.FeatureMatcher
import org.hamcrest.Matcher
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
class SimPinFeederComponentTest {
    private val TAG = this::class.java.simpleName

    @Inject
    lateinit var mTelephonyManager: TelephonyManager

    @Inject
    lateinit var mSubscriptionManager: SubscriptionManager

    @Inject
    lateinit var mSimPinFeederFactory: SimPinFeeder.Factory

    @get:Rule
    val mHiltRule: HiltAndroidRule = HiltAndroidRule(this)

    private lateinit var mLockedSimCardsBySubId: Map<Int, TelephonyManager>

    @Before
    fun setUp() {
        assumeDeviceWithRealRadioCapabilities()

        mHiltRule.inject()

        val visibleSubInfos = mSubscriptionManager.selectableSubscriptionInfoList ?: emptyList()
        Log.d(TAG, "visibleSubInfos = $visibleSubInfos")

        mLockedSimCardsBySubId = visibleSubInfos
            .associateBy { sub -> sub.subscriptionId }
            .mapValues { mTelephonyManager.createForSubscriptionId(it.key) }
            .filterValues { tm -> tm.simState == SIM_STATE_PIN_REQUIRED }
        Log.d(TAG, "Locked SIM cards subIDs: " + mLockedSimCardsBySubId.keys)

        assumeThat("No SIM cards were found that require a PIN to unlock. Should ignore test.",
            mLockedSimCardsBySubId, not(anEmptyMap()))
    }

    /**
     * Precondition:
     * - At least one SIM card is inserted in the device and it requires a PIN to unlock.
     * - SIM card's PIN code should be equal to "1234".
     * - Having 3 attempts remaining to enter SIM PIN.
     *
     * *IMPORTANT*
     * If the unlock fails, you will still be left with 2 more attempts.
     */
    @Test
    fun test_unlockAllSimCards() {
        val clearPinEntities = mLockedSimCardsBySubId.keys.map(::createClearPinEntity)
        val simPinFeeder = mSimPinFeederFactory.create(clearPinEntities)
        simPinFeeder.start()
        runBlocking {
            val task = async(Dispatchers.Default) {
                simPinFeeder.join()
            }
            withTimeout(30.seconds) { task.await() }

            // Wait to settle down everything so that the new SIM states become visible
            delay(3.seconds)
        }

        Log.d(TAG, "After unlock: " + mLockedSimCardsBySubId.mapValues { it.value.simState })
        assertThat(mLockedSimCardsBySubId.values,
            everyItem(
                withSimState(
                    both(not(SIM_STATE_PIN_REQUIRED))
                        .and(not(SIM_STATE_PUK_REQUIRED))
                        .and(not(SIM_STATE_ABSENT)))))
    }
}

private fun createClearPinEntity(subId: Int) = PinEntity().apply {
    subscriptionId = subId
    setClearPin("1234")
}

private fun withSimState(matcher: Matcher<Int>): FeatureMatcher<TelephonyManager, Int> =
    object : FeatureMatcher<TelephonyManager, Int>(matcher, "with SIM state", "SimState") {
        override fun featureValueOf(tm: TelephonyManager): Int = tm.simState
    }
