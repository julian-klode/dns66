package org.jak_linux.dns66.tile;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.MainActivity;
import org.jak_linux.dns66.R;

public class TileStartActivity extends AppCompatActivity {

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// If the config was not initialized, do it here
		if (MainActivity.config == null) {
			MainActivity.config = FileHelper.loadCurrentSettings(this);
		}

		setContentView(R.layout.activity_tile);
	}
}
