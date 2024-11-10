package com.github.iusmac.sevensim;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public class SystemTimeProviderImplTest extends MockitoHiltAndroidTestBase {
    @Inject
    SystemTimeProvider mSystemTimeProvider;

    @Test
    public void test_now() {
        final var now = mSystemTimeProvider.now();
        final var expected = LocalDateTime.now(ZoneId.systemDefault());
        assertThat(now, is(not(nullValue())));
        assertThat(Duration.between(now, expected).abs().toMillis(), is(lessThanOrEqualTo(1000L)));
    }
}
