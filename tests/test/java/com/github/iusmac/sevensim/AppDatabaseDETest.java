package com.github.iusmac.sevensim;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import androidx.room.testing.MigrationTestHelper;
import androidx.test.platform.app.InstrumentationRegistry;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.io.IOException;
import java.util.LinkedHashMap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public class AppDatabaseDETest extends MockitoHiltAndroidTestBase {
    private static final String DB_NAME = AppDatabaseDE.class.getCanonicalName();

    @Rule
    public final MigrationTestHelper mMigrationHelper =
        new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabaseDE.class);

    private static final LinkedHashMap<String, Object> DATA_V1 = new LinkedHashMap<>();
    static {
        DATA_V1.put("id", 1);
        DATA_V1.put("sub_id", 5);
        DATA_V1.put("sub_enabled", false);
        DATA_V1.put("enabled", true);
        DATA_V1.put("days_of_week_bits", 1<<2 | 1<<6);
        DATA_V1.put("minutes_since_midnight", 7 * 60);
    }

    private static final LinkedHashMap<String, Object> DATA_V2 = new LinkedHashMap<>();
    static {
        DATA_V2.putAll(DATA_V1);
        DATA_V2.replace("id", 2);
        DATA_V2.put("label", "Test string label");
    }

    @Test
    public void test_migration_1_2() throws IOException {
        final var dbV1 = mMigrationHelper.createDatabase(DB_NAME, 1);
        final var dbV1Fields = String.join(",", DATA_V1.keySet().toArray(String[]::new));

        dbV1.execSQL("INSERT INTO subscription_schedules (" + dbV1Fields + ") VALUES (?,?,?,?,?,?)",
                DATA_V1.values().toArray());

        var cursor = dbV1.query("SELECT " + dbV1Fields + " FROM subscription_schedules");

        assertThat(cursor.getCount(), equalTo(1));
        cursor.moveToFirst();
        assertThat(cursor.getInt(0), equalTo(DATA_V1.get("id")));
        assertThat(cursor.getInt(1), equalTo(DATA_V1.get("sub_id")));
        assertThat(cursor.getInt(2), equalTo((boolean) DATA_V1.get("sub_enabled") ? 1 : 0));
        assertThat(cursor.getInt(3), equalTo((boolean) DATA_V1.get("enabled") ? 1 : 0));
        assertThat(cursor.getInt(4), equalTo(DATA_V1.get("days_of_week_bits")));
        assertThat(cursor.getInt(5), equalTo(DATA_V1.get("minutes_since_midnight")));

        final var dbV2 = mMigrationHelper.runMigrationsAndValidate(DB_NAME, 2, true,
                AppDatabaseDE.MIGRATION_1_2);
        final var dbV2Fields = String.join(",", DATA_V2.keySet().toArray(String[]::new));

        cursor = dbV2.query("SELECT " + dbV2Fields + " FROM subscription_schedules");
        assertThat(cursor.getCount(), equalTo(1));
        cursor.moveToFirst();
        assertThat(cursor.getInt(0), equalTo(DATA_V1.get("id")));
        assertThat(cursor.getInt(1), equalTo(DATA_V1.get("sub_id")));
        assertThat(cursor.getInt(2), equalTo((boolean) DATA_V1.get("sub_enabled") ? 1 : 0));
        assertThat(cursor.getInt(3), equalTo((boolean) DATA_V1.get("enabled") ? 1 : 0));
        assertThat(cursor.getInt(4), equalTo(DATA_V1.get("days_of_week_bits")));
        assertThat(cursor.getInt(5), equalTo(DATA_V1.get("minutes_since_midnight")));
        assertThat(cursor.getString(6), is(nullValue()));

        dbV2.execSQL("INSERT INTO subscription_schedules (" + dbV2Fields +
                ") VALUES (?,?,?,?,?,?,?)", DATA_V2.values().toArray());

        cursor = dbV1.query("SELECT " + dbV2Fields + " FROM subscription_schedules WHERE id = ?",
                new Object[] { DATA_V2.get("id") });
        assertThat(cursor.getCount(), equalTo(1));
        cursor.moveToFirst();
        assertThat(cursor.getInt(0), equalTo(DATA_V2.get("id")));
        assertThat(cursor.getInt(1), equalTo(DATA_V2.get("sub_id")));
        assertThat(cursor.getInt(2), equalTo((boolean) DATA_V2.get("sub_enabled") ? 1 : 0));
        assertThat(cursor.getInt(3), equalTo((boolean) DATA_V2.get("enabled") ? 1 : 0));
        assertThat(cursor.getInt(4), equalTo(DATA_V2.get("days_of_week_bits")));
        assertThat(cursor.getInt(5), equalTo(DATA_V2.get("minutes_since_midnight")));
        assertThat(cursor.getString(6), equalTo(DATA_V2.get("label")));
    }
}
