package com.github.iusmac.sevensim.test;

import android.app.PendingIntent;
import android.service.quicksettings.TileService;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowTileService;

import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

@Implements(TileService.class)
public class ShadowTileServiceUnimplementedApi extends ShadowTileService {
    public PendingIntent pendingIntent;

    /**
     * This method won't start the activity through {@link PendingIntent} or collapse the QS panel.
     * Manually invoke the {@link PendingIntent#send} method on the {@link #pendingIntent} property.
     */
    @Implementation(minSdk = UPSIDE_DOWN_CAKE)
    protected void startActivityAndCollapse(PendingIntent pendingIntent) {
        this.pendingIntent = pendingIntent;
    }
}
