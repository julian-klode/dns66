/* Copyright (C) 2016 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Spinner;
import android.widget.Switch;

public class ItemActivity extends AppCompatActivity {


    private static final int READ_REQUEST_CODE = 1;
    private TextInputEditText locationText;
    private TextInputEditText titleText;
    private Spinner stateSpinner;
    private Switch stateSwitch;

    public void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {

            if (resultData != null) {
                Uri uri = resultData.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    locationText.setText(uri.toString());
                } catch (SecurityException e) {
                    new AlertDialog.Builder(this).setIcon(R.drawable.ic_warning)
                            .setTitle(R.string.permission_denied)
                            .setMessage(R.string.persistable_uri_permission_failed)
                            .setPositiveButton(android.R.string.yes, null).show();
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent.getIntExtra("STATE_CHOICES", 3) == 2) {
            setContentView(R.layout.activity_item_dns);
        } else {
            setContentView(R.layout.activity_item);
        }

        titleText = (TextInputEditText) findViewById(R.id.title);
        locationText = (TextInputEditText) findViewById(R.id.location);
        stateSpinner = (Spinner) findViewById(R.id.state_spinner);
        stateSwitch = (Switch) findViewById(R.id.state_switch);

        if (intent.hasExtra("ITEM_TITLE"))
            titleText.setText(intent.getStringExtra("ITEM_TITLE"));
        if (intent.hasExtra("ITEM_LOCATION"))
            locationText.setText(intent.getStringExtra("ITEM_LOCATION"));
        if (intent.hasExtra("ITEM_STATE") && stateSpinner != null)
            stateSpinner.setSelection(intent.getIntExtra("ITEM_STATE", 0));
        if (intent.hasExtra("ITEM_STATE") && stateSwitch != null)
            stateSwitch.setChecked(intent.getIntExtra("ITEM_STATE", 0) % 2 != 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.item, menu);
        if (getIntent().getIntExtra("STATE_CHOICES", 3) == 2) {
            menu.findItem(R.id.action_use_file).setVisible(false);
        }
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_save:
                Intent intent = new Intent();
                intent.putExtra("ITEM_TITLE", titleText.getText().toString());
                intent.putExtra("ITEM_LOCATION", locationText.getText().toString());
                if (stateSpinner != null)
                    intent.putExtra("ITEM_STATE", stateSpinner.getSelectedItemPosition());
                if (stateSwitch != null)
                    intent.putExtra("ITEM_STATE", stateSwitch.isChecked() ? 1 : 0);
                setResult(RESULT_OK, intent);
                finish();
                break;
            case R.id.action_use_file:
                performFileSearch();
                break;
        }

        return super.onOptionsItemSelected(item);
    }
}
