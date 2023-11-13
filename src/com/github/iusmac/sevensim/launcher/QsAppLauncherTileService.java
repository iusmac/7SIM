package com.github.iusmac.sevensim.launcher;

import android.content.Intent;
import android.service.quicksettings.TileService;

import com.github.iusmac.sevensim.ui.MainActivity;

public final class QsAppLauncherTileService extends TileService {
    @Override
    public void onClick() {
        final Intent aIntent = new Intent(this, MainActivity.class);
        aIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(aIntent);
    }
}
