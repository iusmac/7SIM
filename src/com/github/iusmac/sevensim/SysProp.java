package com.github.iusmac.sevensim;

import android.os.SystemProperties;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Optional;

/**
 * <p>This wrapper class encapsulates and facilitates the use of
 * {@link SystemProperties#set(String,String)} and {@link SystemProperties#get(String)} API methods
 * for storing app-scoped system properties, that can be identified in the system environment as
 * "[ro.|persist.]service.7sim.{name}".
 *
 * <p>Note that, it's allowed for the property name to contain format specifiers compatible with
 * {@link String#format(String,Object...)}. The methods for reading/writing the property can be fed
 * with values for the format specifiers.
 */
public final class SysProp {
    /**
     * <p>The existing base context in which system properties will be stored.
     *
     * <p>Note that, this app shares system UID (sharedUserId="android.uid.system"), thus it will be
     * located in the "system_app" SEPolicy domain space defined in [1]. The "system_app" domain
     * has R/W access in the "system_prop" SEPolicy domain space, which is granted in [2].
     *
     * <p>The "service." property context is located in the "system_prop" SEPolicy domain space
     * defined in [3].
     *
     * <p>
     * [1] {@link https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/android-s-beta-4/private/seapp_contexts#140}
     * [2] {@link https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/android-s-beta-4/private/system_app.te#41}
     * [3] {@link https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/android-s-beta-4/private/property_contexts#29}
    */
    private final String SYSTEM_PROP_BASE_CONTEXT = "service.";

    /** The base context for all app-scoped system properties. */
    private final String SYSTEM_PROP_APP_BASE_CONTEXT = "7sim.";

    /** The property name. */
    private final String mFormattedPropNames;

    /**
     * Note that, only {@code isPersistent} or {@code isReadOnly} can be set at a time.
     *
     * @param formattedPropName The property name. Note that, it's allowed for the property name
     * to contain format specifiers compatible with {@link String#format(String,Object...)}. The
     * methods for reading/writing the property can be fed with values for the format
     * specifiers.
     * @param isPersistent Whether the property should persist across boots.
     * @param isReadOnly Whether the property permits strictly read-only access.
     */
    public SysProp(final @NonNull String formattedPropName, final boolean isPersistent,
            final boolean isReadOnly) {

        final StringBuilder builder = new StringBuilder(50);
        if (isPersistent) {
            builder.append("persist.");
        } else if (isReadOnly) {
            builder.append("ro.");
        }
        builder.append(SYSTEM_PROP_BASE_CONTEXT)
            .append(SYSTEM_PROP_APP_BASE_CONTEXT)
            .append(formattedPropName);

        mFormattedPropNames = builder.toString();
    }

    /**
     * @param formattedPropName The property name. Note that, it's allowed for the property name
     * to contain format specifiers compatible with {@link String#format(String,Object...)}. The
     * methods for reading/writing the property can be fed with values for the format
     * specifiers.
     * @param isPersistent Whether the property should persist across boots.
     */
    public SysProp(final @NonNull String formattedPropName, final boolean isPersistent) {
        this(formattedPropName, isPersistent, /*isReadOnly=*/ false);
    }

    /**
     * @param formatArgs Values to fill format specifiers in the property name.
     */
    private String getFormattedProp(final Object... formatArgs) {
        return String.format(Locale.US, mFormattedPropNames, formatArgs);
    }

    /**
     * @see SystemProperties#set(String,String)
     * @param val The value to store in the system property.
     * @param formatArgs Values to fill format specifiers in the property name.
     */
    public void set(final Optional<String> value, final Object... formatArgs) {
        SystemProperties.set(getFormattedProp(formatArgs), value.orElse(null));
    }

    /**
     * @see SystemProperties#get(String,String)
     * @param def The default value in case the property is not set or empty.
     * @param formatArgs Values to fill format specifiers in the property name.
     * @return The value stored in the system property.
     */
    public Optional<String> get(final Optional<String> def, final Object... formatArgs) {
        final String value = SystemProperties.get(getFormattedProp(formatArgs), def.orElse(null));
        return Optional.ofNullable(value).filter((val) -> !val.isEmpty());
    }

    /**
     * <p>Check whether the system property evaluates to a {@link Boolean#TRUE}.
     *
     * <p>Possible string values (ignoring case): "True", "yes", "1".
     *
     * @param formatArgs Values to fill format specifiers in the property name.
     * @return {@code true} if the system property value evaluates to one of the possible string
     * values, {@code false} otherwise.
     */
    public boolean isTrue(final Object... formatArgs) {
        final Optional<String> value = get(Optional.empty(), formatArgs);
        return value.map((val) -> val.equals("1") || Boolean.parseBoolean(val)).orElse(false);
    }
}
