package com.github.iusmac.sevensim.scheduler;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.time.LocalTime;
import java.util.Objects;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public class SubscriptionScheduleEntityTest extends MockitoHiltAndroidTestBase {
    private SubscriptionScheduleEntity mEntity;

    @Override
    public void setUp() {
        super.setUp();

        mEntity = buildEntity();
    }

    @Inject
    DaysOfWeek.Factory mDaysOfWeekFactory;

    @Test
    public void test_equals_ByInstance() {
        assertThat(mEntity, is(mEntity));
        assertFalse(mEntity.equals(null));
        assertThat(mEntity, is(not(new Object())));
        // Test whether null fields are handled correctly when comparing (shouldn't crash with NPE)
        assertThat(mEntity, is(not(new SubscriptionScheduleEntity())));
        assertThat(mEntity, is(buildEntity()));
    }

    @Test
    public void test_equals_OnlyIdDiffers() {
        mEntity.setId(mEntity.getId() + 1);
        assertThat(buildEntity(), is(not(mEntity)));
    }

    @Test
    public void test_equals_OnlySubscriptionIdDiffers() {
        mEntity.setSubscriptionId(mEntity.getSubscriptionId() + 1);
        assertThat(buildEntity(), is(not(mEntity)));
    }

    @Test
    public void test_equals_OnlySubscriptionEnabledDiffers() {
        mEntity.setSubscriptionEnabled(!mEntity.getSubscriptionEnabled());
        assertThat(buildEntity(), is(not(mEntity)));
    }

    @Test
    public void test_equals_OnlyLabelDiffers() {
        mEntity.setLabel(null);
        assertThat(buildEntity(), is(not(mEntity)));
    }

    @Test
    public void test_equals_OnlyEnabledDiffers() {
        mEntity.setEnabled(!mEntity.getEnabled());
        assertThat(buildEntity(), is(not(mEntity)));
    }

    @Test
    public void test_equals_OnlyDaysOfWeekDiffer() {
        mEntity.setDaysOfWeek(mDaysOfWeekFactory.create(new int[] { DayOfWeek.MONDAY }));
        assertThat(buildEntity(), is(not(mEntity)));
    }

    @Test
    public void test_equals_OnlyTimeDiffers() {
        mEntity.setTime(mEntity.getTime().plusHours(1));
        assertThat(buildEntity(), is(not(mEntity)));
    }

    @Test
    public void test_hashCode() {
        final int expectedHash = Objects.hash(
                mEntity.getId(),
                mEntity.getSubscriptionId(),
                mEntity.getSubscriptionEnabled(),
                mEntity.getLabel(),
                mEntity.getEnabled(),
                mEntity.getDaysOfWeek(),
                mEntity.getTime());
        assertThat(mEntity.hashCode(), is(expectedHash));
    }

    private SubscriptionScheduleEntity buildEntity() {
        final var entity = new SubscriptionScheduleEntity();
        entity.setId(1);
        entity.setLabel("Label");
        entity.setTime(LocalTime.of(13, 0));
        entity.setSubscriptionId(1);
        entity.setDaysOfWeek(mDaysOfWeekFactory.create());
        return entity;
    }
}
