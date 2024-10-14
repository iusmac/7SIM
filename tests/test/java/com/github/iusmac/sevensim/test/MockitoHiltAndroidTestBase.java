package com.github.iusmac.sevensim.test;

import android.content.Context;

import androidx.annotation.CallSuper;

import com.github.iusmac.sevensim.inject.SevenSimTestModule;

import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import org.robolectric.annotation.Config;

/** Base class for tests with Mockito + {@link HiltAndroidTest}. */
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class MockitoHiltAndroidTestBase {
    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public final HiltAndroidRule mHiltRule = new HiltAndroidRule(this);

    @Inject
    public @ApplicationContext Context mApplicationContext;

    @Before
    @CallSuper
    public void setUp() {
        mHiltRule.inject();
    }

    @After
    public final void closeAllDatabases() {
        SevenSimTestModule.closeAllDatabases();
    }
}
