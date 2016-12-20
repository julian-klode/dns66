package org.jak_linux.dns66.tile;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.jak_linux.dns66.R;
import org.jak_linux.dns66.vpn.AdVpnService;

/**
 * TileService enabling a Tile to start or stop the service
 */
@TargetApi(Build.VERSION_CODES.N)
public class VpnTileService extends TileService {

	@Override
	public void onTileAdded() {
		super.onTileAdded();
	}

	@Override
	public void onTileRemoved() {
		super.onTileRemoved();
	}

	@Override
	public void onStartListening() {
		super.onStartListening();

		Tile tile = getQsTile();
		tile.setIcon(Icon.createWithResource(this, R.drawable.ic_menu_start_white));
		tile.setLabel(getString(R.string.app_name));
		tile.setContentDescription(getString(R.string.start_tab));
		tile.setState(
				AdVpnService.vpnStatus == AdVpnService.VPN_STATUS_STOPPED ?
						Tile.STATE_INACTIVE :
						Tile.STATE_ACTIVE);
		tile.updateTile();
	}

	@Override
	public void onStopListening() {
		super.onStopListening();
	}

	@Override
	public void onClick() {
		Tile tile = getQsTile();
		tile.setState(tile.getState() == Tile.STATE_INACTIVE ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
		tile.updateTile();

		startActivity(new Intent(this, TileStartActivity.class));
	}
}
