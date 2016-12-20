package org.jak_linux.dns66.tile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.jak_linux.dns66.R;
import org.jak_linux.dns66.main.StartFragment;
import org.jak_linux.dns66.vpn.AdVpnService;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

/**
 * Handle the launch of the AdVpnService from the TileActivity
 */
public class TileStartFragment extends StartFragment {

	private static final String TAG = "TileStartFragment";

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_tile, container, false);
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);

		// Immediately start or stop the service
		if (AdVpnService.vpnStatus != AdVpnService.VPN_STATUS_STOPPED) {
			Log.d(TAG, "Stopping service");

			stopService();
			getActivity().finish();
		} else {
			Log.d(TAG, "Starting service");

			checkHostsFilesAndStartService();
		}
	}

	@Override
	protected void onCancelStart() {
		// Dismiss the activity if the user cancels the start
		getActivity().finish();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		// If the service was correctly launched, dismiss this activity
		if (requestCode == REQUEST_START_VPN &&
				(resultCode == RESULT_OK || resultCode == RESULT_CANCELED)) {
			getActivity().finish();
		}
	}
}
