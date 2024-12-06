package com.github.iusmac.sevensim.test;

import android.content.Context;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import com.github.iusmac.sevensim.ApplicationInfo;
import com.github.iusmac.sevensim.telephony.PinEntity;

import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;

import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;

import static java.util.concurrent.TimeUnit.SECONDS;

import static org.hamcrest.Matchers.equalTo;

import static org.robolectric.Shadows.shadowOf;

public final class TestUtils {
    private static final Signature AOSP_SIGNATURE = new Signature("308204a830820390a003020102020900b3998086d056cffa300d06092a864886f70d0101040500308194310b3009060355040613025553311330110603550408130a43616c69666f726e6961311630140603550407130d4d6f756e7461696e20566965773110300e060355040a1307416e64726f69643110300e060355040b1307416e64726f69643110300e06035504031307416e64726f69643122302006092a864886f70d0109011613616e64726f696440616e64726f69642e636f6d301e170d3038303431353232343035305a170d3335303930313232343035305a308194310b3009060355040613025553311330110603550408130a43616c69666f726e6961311630140603550407130d4d6f756e7461696e20566965773110300e060355040a1307416e64726f69643110300e060355040b1307416e64726f69643110300e06035504031307416e64726f69643122302006092a864886f70d0109011613616e64726f696440616e64726f69642e636f6d30820120300d06092a864886f70d01010105000382010d003082010802820101009c780592ac0d5d381cdeaa65ecc8a6006e36480c6d7207b12011be50863aabe2b55d009adf7146d6f2202280c7cd4d7bdb26243b8a806c26b34b137523a49268224904dc01493e7c0acf1a05c874f69b037b60309d9074d24280e16bad2a8734361951eaf72a482d09b204b1875e12ac98c1aa773d6800b9eafde56d58bed8e8da16f9a360099c37a834a6dfedb7b6b44a049e07a269fccf2c5496f2cf36d64df90a3b8d8f34a3baab4cf53371ab27719b3ba58754ad0c53fc14e1db45d51e234fbbe93c9ba4edf9ce54261350ec535607bf69a2ff4aa07db5f7ea200d09a6c1b49e21402f89ed1190893aab5a9180f152e82f85a45753cf5fc19071c5eec827020103a381fc3081f9301d0603551d0e041604144fe4a0b3dd9cba29f71d7287c4e7c38f2086c2993081c90603551d230481c13081be80144fe4a0b3dd9cba29f71d7287c4e7c38f2086c299a1819aa48197308194310b3009060355040613025553311330110603550408130a43616c69666f726e6961311630140603550407130d4d6f756e7461696e20566965773110300e060355040a1307416e64726f69643110300e060355040b1307416e64726f69643110300e06035504031307416e64726f69643122302006092a864886f70d0109011613616e64726f696440616e64726f69642e636f6d820900b3998086d056cffa300c0603551d13040530030101ff300d06092a864886f70d01010405000382010100572551b8d93a1f73de0f6d469f86dad6701400293c88a0cd7cd778b73dafcc197fab76e6212e56c1c761cfc42fd733de52c50ae08814cefc0a3b5a1a4346054d829f1d82b42b2048bf88b5d14929ef85f60edd12d72d55657e22e3e85d04c831d613d19938bb8982247fa321256ba12d1d6a8f92ea1db1c373317ba0c037f0d1aff645aef224979fba6e7a14bc025c71b98138cef3ddfc059617cf24845cf7b40d6382f7275ed738495ab6e5931b9421765c491b72fb68e080dbdb58c2029d347c8b328ce43ef6a8b15533edfbe989bd6a48dd4b202eda94c6ab8dd5b8399203daae2ed446232e4fe9bd961394c6300e5138e3cfd285e6e4e483538cb8b1b357");

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

    /**
     * Set the response of {@link ApplicationInfo#hasAospPlatformSignature}.
     *
     * @param context Context to access system resources.
     * @param useAosp {@code true} if should use AOSP platform signature, otherwise {@code false}.
     */
    public static void useAospPlatformSignature(final Context context, final boolean useAosp) {
        final var packageInfo = shadowOf(context.getPackageManager())
            .getInternalMutablePackageInfo(context.getPackageName());
        final var signingInfo = Shadow.newInstanceOf(SigningInfo.class);

        shadowOf(signingInfo).setSignatures(new Signature[] {
            useAosp ? AOSP_SIGNATURE : new Signature("1234")
        });
        packageInfo.signingInfo = signingInfo;
    }

    /**
     * Set the response of {@link ApplicationInfo#isSystemApplication}.
     *
     * @param context Context to access system resources.
     * @param isSystemApp {@code true} if the app should be considered as a system app,
     * {@code false} otherwise.
     * @param isUpdatedSystemApp {@code true} if the app should be considered as an updated system
     * app, {@code false} otherwise. To have effect need {@code isSystemApp} to be {@code true}.
     */
    public static void setIsSystemApplication(final Context context, final boolean isSystemApp,
            final boolean isUpdatedSystemApp) {

        final var packageInfo = shadowOf(context.getPackageManager())
            .getInternalMutablePackageInfo(context.getPackageName());

        packageInfo.applicationInfo.flags =
            // Turn off both flags
            (packageInfo.applicationInfo.flags & ~(FLAG_UPDATED_SYSTEM_APP | FLAG_SYSTEM))
            // If needed, enable only one flag but not both
            | (isSystemApp ? (isUpdatedSystemApp ? FLAG_UPDATED_SYSTEM_APP : FLAG_SYSTEM) : 0);
    }

    /** Set the response of {@link SubscriptionInfo#areUiccApplicationsEnabled}. */
    public static void setAreUiccApplicationsEnabled(final SubscriptionInfo subInfo,
            final boolean enabled) {

        ReflectionHelpers.setField(subInfo, "mAreUiccApplicationsEnabled", enabled);
    }

    /** Matches an encrypted {@link PinEntity}. */
    public static FeatureMatcher<PinEntity, Boolean> encrypted() {
        return new FeatureMatcher<>(equalTo(true), "encrypted", "encrypted") {
            @Override
            protected Boolean featureValueOf(final PinEntity pinEntity) {
                return pinEntity.isEncrypted();
            }
        };
    }

    /** Matches a {@link PinEntity} with {@link PinEntity#isInvalid} flag. */
    public static FeatureMatcher<PinEntity, Boolean> invalid() {
        return new FeatureMatcher<>(equalTo(true), "with invalid flag", "invalid flag") {
            @Override
            protected Boolean featureValueOf(final PinEntity pinEntity) {
                return pinEntity.isInvalid();
            }
        };
    }

    /** Matches a {@link PinEntity} with {@link PinEntity#isCorrupted} flag. */
    public static FeatureMatcher<PinEntity, Boolean> corrupted() {
        return new FeatureMatcher<>(equalTo(true), "with corrupted flag", "corrupted flag") {
            @Override
            protected Boolean featureValueOf(final PinEntity pinEntity) {
                return pinEntity.isCorrupted();
            }
        };
    }

    /** Matches a {@link PinEntity} with the given subscription ID. */
    public static FeatureMatcher<PinEntity, Integer> withSubId(final Matcher<Integer> matcher) {
        return new FeatureMatcher<>(matcher, "with subscription ID", "subscription ID") {
            @Override
            protected Integer featureValueOf(final PinEntity pinEntity) {
                return pinEntity.getSubscriptionId();
            }
        };
    }

    /**
     * Use this helper method like {@code CountDownLatch.wait()} to make the main (UI) thread looper
     * that is also running the test, to wait for the non-main (worker) thread to become idle.
     * <p>
     * By default, the worker looper will be in "unpaused" state, so it will be "moved" by the main
     * thread looper, as they both share the same system clock. Since both of them run in parallel,
     * and there isn't any synchronization between the two, it's possible that the worker can take a
     * little bit longer to complete, so the test won't see the result what makes it flaky.
     * <p>
     * It will timeout after 60 seconds of waiting to prevent the tests from hanging forever.
     *
     * @return {@code true} when the given looper was already idle, {@code false} otherwise.
     */
    public static boolean waitWorkerThreadLooperUntilIdle(final Looper workerLooper)
            throws TimeoutException {

        if (workerLooper == Looper.getMainLooper()) {
            throw new AssertionError("Expected non-main looper.");
        }
        final var timeout = System.currentTimeMillis() + SECONDS.toMillis(60);
        final var shadowLooper = shadowOf(workerLooper);
        var wasAlreadyIdle = true;
        while (System.currentTimeMillis() <= timeout) {
            if (shadowLooper.isIdle()) {
                return wasAlreadyIdle;
            }
            wasAlreadyIdle = false;
        }
        throw new TimeoutException(
                "Waited for the worker thread " + workerLooper + " to become idle for 60 seconds. "
                + "Most likely, an unhandled exception has occurred in the worker thread.");
    }

    /**
     * Set {@code true} if times should be formatted as 24-hour times, {@code false} if times should
     * be formatted as 12-hour (AM/PM) times, or {@code null} to base on the user's chosen locale.
     */
    public static void set24Hour(final Context context, final Boolean is24Hour) {
        final var value = is24Hour == null ? null : is24Hour ? "24" : "12";
        Settings.System.putString(context.getContentResolver(), Settings.System.TIME_12_24, value);
    }

    /**
     * Use this helper method like {@code CountDownLatch.wait()} to make the main (UI) thread looper
     * that is also running the test, to wait for the {@link AsyncTestTaskExecutor} used by all
     * databases to perform asynchronous queries and tasks, including LiveData invalidation,
     * Flowable scheduling and ListenableFuture tasks, to become idle.
     * <p>
     * This method will also drain the main looper after waiting, to ensure the UI is up-to-date if
     * using Room's LiveData implementation to observe changes to the database only on is UI active.
     * <p>
     * It will timeout after 60 seconds of waiting to prevent the tests from hanging forever.
     */
    @MainThread
    public static void waitDatabasesUntilIdle() throws TimeoutException {
        final var timeout = System.currentTimeMillis() + SECONDS.toMillis(60);
        final var shadowMainLooper = shadowOf(Looper.getMainLooper());
        var settledUp = true;
        while (System.currentTimeMillis() <= timeout) {
            if (!waitWorkerThreadLooperUntilIdle(AsyncTestTaskExecutor.INSTANCE.getLooper())) {
                settledUp = false;
            }
            // After the database became idle, the Room library could have scheduled on the main
            // thread a task to re-compute the LiveData, so we need to run it now and wait again
            if (shadowMainLooper.getNextScheduledTaskTime().toMillis() ==
                    SystemClock.uptimeMillis()) {
                shadowMainLooper.idle();
                settledUp = false;
            }
            if (settledUp) {
                return;
            }
            settledUp = true;
        }
        throw new TimeoutException(
                "Waited for databases & main loopers to become idle for 60 seconds.");
    }

    /** Do not initialize. */
    private TestUtils() { }
}
