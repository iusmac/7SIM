package com.github.iusmac.sevensim;

import android.app.Application;

import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class SysPropTest {
    @Test
    public void test_constructingAsPersistentAndReadonlyProperty_PersistentTakesOver() {
        final var prop = new SysProp("persistent_prop.%d", /*isPersistent=*/ true,
                /*isReadOnly=*/ true);

        assertTrue(prop.isPersistent());
        assertFalse(prop.isReadOnly());
    }

    @Test
    public void test_isPersistentProperty() {
        final var prop = new SysProp("persistent_prop.%d", /*isPersistent=*/ true);

        assertTrue(prop.isPersistent());
        assertFalse(prop.isReadOnly());
    }

    @Test
    public void test_isReadOnlyProperty() {
        final var prop = new SysProp("read_only_prop.%d", /*isPersistent=*/ false,
                /*isReadOnly=*/ true);

        assertTrue(prop.isReadOnly());
        assertFalse(prop.isPersistent());
    }

    @Test
    public void test_isRegularProperty() {
        final var prop = new SysProp("regular_prop.%d", /*isPersistent=*/ false);

        assertFalse(prop.isReadOnly());
        assertFalse(prop.isPersistent());
    }

    @Test
    public void test_set_FormattedNonEmptyProperty() {
        final var prop = new SysProp("formatted_prop.%d", /*isPersistent=*/ false);
        final Optional<String> expected = Optional.of("value123");
        final Object[] formatArgs = { 1 };

        prop.set(expected, formatArgs);
        assertEquals(expected, prop.get(Optional.empty(), formatArgs));
    }

    @Test
    public void test_set_UnformattedEmptyNonEmptyProperty() {
        final var prop = new SysProp("unformatted_prop", /*isPersistent=*/ true);

        prop.set(Optional.of("abc123"));
        assertFalse(prop.get(Optional.empty()).isEmpty());

        prop.set(Optional.empty());
        assertTrue(prop.get(Optional.empty()).isEmpty());

        prop.set(Optional.of(""));
        assertTrue(prop.get(Optional.empty()).isEmpty());
    }

    @Test
    public void test_get_DefaultValueForNonExistentProp() {
        final var prop = new SysProp("non_existent_prop", /*isPersistent=*/ false);

        final Optional<String> expected = Optional.of("default_value");
        assertEquals(expected, prop.get(expected));
    }

    @Test
    public void test_isTrue() {
        final var prop = new SysProp("boolean_prop", /*isPersistent=*/ false);

        // Ensure returns false after initialized
        assertEquals(Optional.empty(), prop.get(Optional.empty()));
        assertFalse(prop.isTrue());

        prop.set(Optional.of("1"));
        assertTrue(prop.isTrue());

        prop.set(Optional.of("0"));
        assertFalse(prop.isTrue());

        prop.set(Optional.of("true"));
        assertTrue(prop.isTrue());

        prop.set(Optional.of("TRUE"));
        assertTrue(prop.isTrue());

        prop.set(Optional.of("yes"));
        assertTrue(prop.isTrue());

        prop.set(Optional.of("Yes"));
        assertTrue(prop.isTrue());

        prop.set(Optional.of("junk"));
        assertFalse(prop.isTrue());
    }
}
