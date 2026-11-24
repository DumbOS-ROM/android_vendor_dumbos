package com.dumbos.launcher;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class DumbModeTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        DumbModeController.cycleMode(this);
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        String mode = DumbModeController.getCurrentMode(this);
        tile.setLabel("DumbOS Mode");
        tile.setSubtitle(mode);
        boolean active = !DumbModeController.MODE_NORMAL.equals(mode);
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
