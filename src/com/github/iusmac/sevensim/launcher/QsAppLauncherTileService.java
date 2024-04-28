package com.github.iusmac.sevensim.launcher;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.UserHandle;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.text.TextUtils;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.Utils;
import com.github.iusmac.sevensim.ui.MainActivity;

public final class QsAppLauncherTileService extends TileService {
    @Override
    public void onTileAdded() {
        super.onTileAdded();

        updateTileStrings();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();

        updateTileStrings();
    }

    @Override
    public void onClick() {
        final Intent aIntent = new Intent(this, MainActivity.class);
        aIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (Utils.IS_AT_LEAST_U) {
            startActivityAndCollapse(PendingIntent.getActivityAsUser(this, /*requestCode=*/ 0,
                        aIntent, PendingIntent.FLAG_IMMUTABLE, /*bundle=*/ null,
                        UserHandle.CURRENT));
        } else {
            ApiDeprecated.startActivityAndCollapse(this, aIntent);
        }
    }

    private void updateTileStrings() {
        final Tile tile = getQsTile();
        final CharSequence subtitle = getString(R.string.app_description);
        if (!TextUtils.equals(tile.getSubtitle(), subtitle)) {
            tile.setSubtitle(subtitle);
            tile.setContentDescription(subtitle);
            tile.updateTile();
        }
    }

    /** Nested class to suppress warning only for API methods annotated as Deprecated. */
    @SuppressWarnings("deprecation")
    private static class ApiDeprecated {
        static void startActivityAndCollapse(final TileService service, final Intent intent) {
            service.startActivityAndCollapse(intent);
        }
    }
}
