package com.github.iusmac.sevensim.telephony;

import android.app.Application;
import android.graphics.Color;
import android.os.Parcel;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;

import static android.telephony.SubscriptionManager.INVALID_SIM_SLOT_INDEX;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public final class SubscriptionTest {
    private static final LocalDateTime MONDAY_01_00_55PM = LocalDateTime.of(2007, 1, 1, 13, 0, 55);

    private final Subscription mSubscription = new Subscription();

    @Test
    public void test_GettersDefaults() {
        assertThat(mSubscription.getId(), is(INVALID_SUBSCRIPTION_ID));
        assertThat(mSubscription.getSlotIndex(), is(INVALID_SIM_SLOT_INDEX));
        assertThat(mSubscription.getSimState(), is(SimState.UNKNOWN));
        assertThat(mSubscription.getSimName(), is(emptyString()));
        assertThat(mSubscription.getIconTint(), is(Color.BLACK));
        assertThat(mSubscription.getLastActivatedTime(), is(LocalDateTime.MIN));
        assertThat(mSubscription.getLastDeactivatedTime(), is(LocalDateTime.MIN));
        assertThat(mSubscription.getKeepDisabledAcrossBoots(), is(nullValue()));
        assertFalse(mSubscription.isSimEnabled());
    }

    @Test
    public void test_setLastActivatedTime_ShouldTruncateToMinutes() {
        mSubscription.setLastActivatedTime(MONDAY_01_00_55PM);
        assertThat(mSubscription.getLastActivatedTime(),
                is(MONDAY_01_00_55PM.truncatedTo(ChronoUnit.MINUTES)));
    }

    @Test
    public void test_setLastDeactivatedTime_ShouldTruncateToMinutes() {
        mSubscription.setLastDeactivatedTime(MONDAY_01_00_55PM);
        assertThat(mSubscription.getLastDeactivatedTime(),
                is(MONDAY_01_00_55PM.truncatedTo(ChronoUnit.MINUTES)));
    }

    @Test
    public void test_equals_ByInstance() {
        assertThat(mSubscription, is(mSubscription));
        assertFalse(mSubscription.equals(null));
        assertThat(mSubscription, is(not(new Object())));
        assertThat(mSubscription, is(new Subscription()));
    }

    @Test
    public void test_equals_OnlyIdDiffers() {
        mSubscription.setId(1);
        assertThat(new Subscription(), is(not(mSubscription)));
    }

    @Test
    public void test_equals_OnlySlotIndexDiffers() {
        mSubscription.setSlotIndex(0);
        assertThat(new Subscription(), is(not(mSubscription)));
    }

    @Test
    public void test_equals_OnlySimStateDiffers() {
        mSubscription.setSimState(SimState.ENABLED);
        assertThat(new Subscription(), is(not(mSubscription)));
    }

    @Test
    public void test_equals_OnlyIconTintDiffers() {
        mSubscription.setIconTint(Color.BLUE);
        assertThat(new Subscription(), is(not(mSubscription)));
    }

    @Test
    public void test_equals_OnlySimNameDiffers() {
        mSubscription.setSimName("Work");
        assertThat(new Subscription(), is(not(mSubscription)));
    }

    @Test
    public void test_equals_OnlyLastActivatedTimeDiffers() {
        mSubscription.setLastActivatedTime(MONDAY_01_00_55PM);
        assertThat(new Subscription(), is(not(mSubscription)));
    }

    @Test
    public void test_equalsOnlyLastDeactivatedTimeDiffers() {
        mSubscription.setLastDeactivatedTime(MONDAY_01_00_55PM);
        assertThat(new Subscription(), is(not(mSubscription)));
    }

    @Test
    public void test_equals_OnlyKeepDisabledAcrossBootsDiffers() {
        // Test whether nulls are handled correctly when only one is null
        final var sub = new Subscription();
        sub.keepDisabledAcrossBoots(false);
        assertThat(sub, is(not(mSubscription)));

        mSubscription.keepDisabledAcrossBoots(false);
        assertThat(new Subscription(), is(not(mSubscription)));
    }

    @Test
    public void test_isSimEnabled() {
        mSubscription.setSimState(SimState.ENABLED);
        assertTrue(mSubscription.isSimEnabled());
    }

    @Test
    public void test_hashCode() {
        final var expectedHash = Objects.hash(
                mSubscription.getId(),
                mSubscription.getSlotIndex(),
                mSubscription.getSimState(),
                mSubscription.getIconTint(),
                mSubscription.getSimName(),
                mSubscription.getLastActivatedTime(),
                mSubscription.getLastDeactivatedTime(),
                mSubscription.getKeepDisabledAcrossBoots());
        assertThat(mSubscription.hashCode(), is(expectedHash));
    }

    @Test
    public void test_ParcelingDescribedContents() {
        assertThat(mSubscription.describeContents(), is(0));
    }

    @Test
    public void test_ParcelingWithDefaults() {
        assertThat(copyViaParcel(mSubscription), is(mSubscription));
    }

    @Test
    public void test_ParcelingWithKeepDisabledAcrossBootsNonNull() {
        mSubscription.keepDisabledAcrossBoots(true);
        assertThat(copyViaParcel(mSubscription), is(mSubscription));
    }

    private static Subscription copyViaParcel(final Subscription orig) {
        final var parcel = Parcel.obtain();
        // Copy via a typed array to cover also the Creator#newArray method
        parcel.writeTypedArray(new Subscription[] { orig }, 0);
        parcel.setDataPosition(0);
        return parcel.createTypedArray(Subscription.CREATOR)[0];
    }
}
