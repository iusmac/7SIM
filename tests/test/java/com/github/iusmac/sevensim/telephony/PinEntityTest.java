package com.github.iusmac.sevensim.telephony;

import android.app.Application;

import java.util.Arrays;
import java.util.Objects;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public final class PinEntityTest {
    private final PinEntity mPinEntity = new PinEntity();

    @Test
    public void test_isEncrypted_UnencryptedWithoutDataAndIV() {
        assertFalse(mPinEntity.isEncrypted());
    }

    @Test
    public void test_isEncrypted_UnencryptedWithDataOnly() {
        mPinEntity.setData(new byte[0]);
        assertFalse(mPinEntity.isEncrypted());
    }

    @Test
    public void test_isEncrypted_UnencryptedWithIVOnly() {
        mPinEntity.setIV(new byte[0]);
        assertFalse(mPinEntity.isEncrypted());
    }

    @Test
    public void test_isEncrypted_EncryptedWithDataAndIV() {
        mPinEntity.setData(new byte[0]);
        mPinEntity.setIV(new byte[0]);
        assertTrue(mPinEntity.isEncrypted());
    }

    @Test
    public void test_isEncrypted_ShouldBecomeUnencryptedAfterClearPinIsSet() {
        mPinEntity.setData(new byte[0]);
        mPinEntity.setIV(new byte[0]);
        mPinEntity.setClearPin("1234");
        assertFalse(mPinEntity.isEncrypted());
    }

    @Test
    public void test_isCorrupted_ShouldBecomeUncorruptedAfterClearPinIsSet() {
        mPinEntity.setCorrupted(true);
        assertTrue(mPinEntity.isCorrupted());

        mPinEntity.setClearPin("1234");
        assertFalse(mPinEntity.isCorrupted());
    }

    @Test
    public void test_hashCode() {
        mPinEntity.setData(new byte[0]);
        mPinEntity.setIV(new byte[0]);
        assertThat(mPinEntity.hashCode(), is(Objects.hash(
                        mPinEntity.getId(),
                        mPinEntity.getSubscriptionId(),
                        Arrays.hashCode(mPinEntity.getData()),
                        Arrays.hashCode(mPinEntity.getIV()),
                        mPinEntity.isInvalid(),
                        mPinEntity.isCorrupted(),
                        mPinEntity.getClearPin())));

    }

    @Test
    public void test_equals_ByInstance() {
        assertThat(mPinEntity, is(mPinEntity));
        assertFalse(mPinEntity.equals(null));
        assertThat(mPinEntity, is(not(new Object())));
        assertThat(mPinEntity, is(new PinEntity()));
    }

    @Test
    public void test_equals_OnlyIdDiffers() {
        mPinEntity.setId(1);
        assertThat(new PinEntity(), is(not(mPinEntity)));
    }

    @Test
    public void test_equals_OnlySubscriptionIdDiffers() {
        mPinEntity.setSubscriptionId(1);
        assertThat(new PinEntity(), is(not(mPinEntity)));
    }

    @Test
    public void test_equals_OnlyDataDiffers() {
        // Test whether nulls are handled correctly when only one is null
        final var pinEntity = new PinEntity();
        pinEntity.setData(new byte[0]);
        assertThat(pinEntity, is(not(mPinEntity)));

        mPinEntity.setData(new byte[0]);
        assertThat(new PinEntity(), is(not(mPinEntity)));
    }

    @Test
    public void test_equals_OnlyIVDiffers() {
        // Test whether nulls are handled correctly when only one is null
        final var pinEntity = new PinEntity();
        pinEntity.setIV(new byte[0]);
        assertThat(pinEntity, is(not(mPinEntity)));

        mPinEntity.setIV(new byte[0]);
        assertThat(new PinEntity(), is(not(mPinEntity)));
    }

    @Test
    public void test_equals_OnlyInvalidDiffers() {
        mPinEntity.setInvalid(!mPinEntity.isInvalid());
        assertThat(new PinEntity(), is(not(mPinEntity)));
    }

    @Test
    public void test_equals_OnlyCorruptedDiffers() {
        mPinEntity.setCorrupted(!mPinEntity.isCorrupted());
        assertThat(new PinEntity(), is(not(mPinEntity)));
    }

    @Test
    public void test_equals_OnlyClearPinDiffers() {
        // Test whether nulls are handled correctly when only one is null
        final var pinEntity = new PinEntity();
        pinEntity.setClearPin("1234");
        assertThat(pinEntity, is(not(mPinEntity)));

        mPinEntity.setClearPin("1234");
        assertThat(new PinEntity(), is(not(mPinEntity)));
    }

    @Test
    public void test_toString_NonEmptyClearPinCode() {
        mPinEntity.setClearPin("1234");
        assertThat(mPinEntity.toString(),
                is(both(containsString("data=[{ empty data }]"))
                    .and(containsString("IV=[{ empty data }]"))
                    .and(containsString("clearPin.isEmpty=false"))));
    }

    @Test
    public void test_toString_NoClearPinCodeButEncrypted() {
        mPinEntity.setData(new byte[0]);
        mPinEntity.setIV(new byte[0]);
        assertThat(mPinEntity.toString(),
                is(both(containsString("data=[{ has data }]"))
                    .and(containsString("IV=[{ has data }]"))
                    .and(containsString("clearPin.isEmpty=true"))));
    }
}
