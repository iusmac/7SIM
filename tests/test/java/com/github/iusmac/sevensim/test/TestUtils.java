package com.github.iusmac.sevensim.test;

import androidx.annotation.Nullable;

import java.util.Arrays;

public final class TestUtils {
    /**
     * Convenience method for creating an appropriately typed {@link GivenHolder} object.
     *
     * @param <G> Type of given value.
     * @param given The value to hold.
     * @return A given object that is templatized with types of {@link G}.
     *
     * @see TestUtils#expected(E)
     */
    public static <G> GivenHolder<G> given(final @Nullable G given) {
        return new GivenHolder<>(given);
    }

    /**
     * Convenience method for creating an appropriately typed {@link ExpectedHolder} object.
     *
     * @param <E> Type of expected value.
     * @param expected The value to hold.
     * @return A given object that is templatized with types of {@link E}.
     *
     * @see TestUtils#given(G)
     */
    public static <E> ExpectedHolder<E> expected(final @Nullable E expected) {
        return new ExpectedHolder<>(expected);
    }

    /** A "Value Object" that holds any immutable value including {@code null}. */
    private static class ValueObject<V> {
        public final V value;

        private ValueObject(final @Nullable V value) {
            this.value = value;
        }

        /**
         * @throws UnsupportedOperationException if hashCode operation is not supported by this
         * value holder.
         */
        @Override
        public int hashCode() {
            throw new UnsupportedOperationException("hashCode");
        }

        /**
         * @throws UnsupportedOperationException if the equals operation is not supported by this
         * value holder.
         */
        @Override
        public boolean equals(Object obj) {
            throw new UnsupportedOperationException("equals");
        }

        @Override
        public String toString() {
            if (value == null) {
                return "null";
            } else if (value.getClass().isArray()) {
                if (value instanceof int[]) {
                    return Arrays.toString((int[]) value);
                } else if (value instanceof long[]) {
                    return Arrays.toString((long[]) value);
                } else if (value instanceof double[]) {
                    return Arrays.toString((double[]) value);
                } else if (value instanceof boolean[]) {
                    return Arrays.toString((boolean[]) value);
                } else if (value instanceof char[]) {
                    return Arrays.toString((char[]) value);
                } else if (value instanceof byte[]) {
                    return Arrays.toString((byte[]) value);
                } else if (value instanceof float[]) {
                    return Arrays.toString((float[]) value);
                } else if (value instanceof short[]) {
                    return Arrays.toString((short[]) value);
                }
                return Arrays.deepToString((Object[]) value);
            }
            return value.toString();
        }
    }

    /**
     * <p>A convenient class for holding a generic value representing a "given" value in the context of
     * a parameterized test.
     *
     * <p>See example:
     * <p><code>var given = given(new int[] {1, 2, 3});</code>
     * <p><code>assertEquals("123", String.join("", given.value));</code>
     *
     * <p>When used as name placeholder in JUnit's or Robolectric's {@code @Parameters} annotation,
     * it will be converted to a human-readable description, such as {@code given [1, 2, 3]}. Also,
     * if the held value is an array, of any depth and type, it will be correctly processed via the
     * {@link Arrays#toString} to avoid displaying something like {@code given [I@7276c8cd}.
     *
     * @see ExpectedHolder
     * @see TestUtils#given(G)
     */
    public static class GivenHolder<V> extends ValueObject<V> {
        public GivenHolder(final @Nullable V value) {
            super(value);
        }

        @Override
        public String toString() {
            return "given " + super.toString();
        }
    }

    /**
     * <p>A convenient class for holding a generic value representing an "expected" value in the context of
     * a parameterized test.
     *
     * <p>See example:
     * <p><code>var expected = expected(new int[] {1, 2, 3});</code>
     * <p><code>assertArrayEquals(expected.value, new int[] {1, 2, 3});</code>
     *
     * <p>When used as name placeholder in JUnit's or Robolectric's {@code @Parameters} annotation,
     * it will be converted to a human-readable description, such as {@code expected [1, 2, 3]}. Also,
     * if the held value is an array, of any depth and type, it will be correctly processed via the
     * {@link Arrays#toString} to avoid displaying something like {@code expected [I@7276c8cd}.
     *
     * <p>See example combined use with Hamcrest:
     * <p><code>var expected = expected(is(arrayContainingInAnyOrder("a", "b", "c")));</code>
     * <p><code>assertThat(new String[] {"c", "b", "a"}, expected.value);</code>
     * <p>The result of {@code expected.toString()} will be a human-readable description, such as
     * <code>expected is [a, b, c] in any order</code>.
     *
     * @see ExpectedHolder
     * @see TestUtils#given(G)
     */
    public static class ExpectedHolder<V> extends ValueObject<V> {
        public ExpectedHolder(final @Nullable V value) {
            super(value);
        }

        @Override
        public String toString() {
            return "expected " + super.toString();
        }
    }

    /** Do not initialize. */
    private TestUtils() { }
}
