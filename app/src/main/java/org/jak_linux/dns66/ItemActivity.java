/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import com.google.android.material.textfield.TextInputEditText;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;

public class ItemActivity extends AppCompatActivity {


    private static final int READ_REQUEST_CODE = 1;
    private static final String TAG = "ItemActivity";
    private TextInputEditText locationText;
    private TextInputEditText titleText;
    private Spinner stateSpinner;
    private Switch stateSwitch;
    private ImageView imageView;

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
            setTitle(R.string.activity_edit_dns_server);
        } else {
            setContentView(R.layout.activity_item);
            setTitle(R.string.activity_edit_filter);
        }

        titleText = (TextInputEditText) findViewById(R.id.title);
        locationText = (TextInputEditText) findViewById(R.id.location);
        stateSpinner = (Spinner) findViewById(R.id.state_spinner);
        stateSwitch = (Switch) findViewById(R.id.state_switch);
        imageView = (ImageView) findViewById(R.id.image_view);

        if (intent.hasExtra("ITEM_TITLE"))
            titleText.setText(intent.getStringExtra("ITEM_TITLE"));
        if (intent.hasExtra("ITEM_LOCATION"))
            locationText.setText(intent.getStringExtra("ITEM_LOCATION"));
        if (intent.hasExtra("ITEM_STATE") && stateSpinner != null)
            stateSpinner.setSelection(intent.getIntExtra("ITEM_STATE", 0));
        if (intent.hasExtra("ITEM_STATE") && stateSwitch != null)
            stateSwitch.setChecked(intent.getIntExtra("ITEM_STATE", 0) % 2 != 0);

        if (stateSpinner != null) {
            stateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    switch (position) {
                        case Configuration.Item.STATE_ALLOW:
                            imageView.setImageDrawable(ContextCompat.getDrawable(ItemActivity.this, R.drawable.ic_state_allow));
                            break;
                        case Configuration.Item.STATE_DENY:
                            imageView.setImageDrawable(ContextCompat.getDrawable(ItemActivity.this, R.drawable.ic_state_deny));
                            break;
                        case Configuration.Item.STATE_IGNORE:
                            imageView.setImageDrawable(ContextCompat.getDrawable(ItemActivity.this, R.drawable.ic_state_ignore));
                            break;
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
        }

        // We have an attachment icon for host files
        if (intent.getIntExtra("STATE_CHOICES", 3) == 3) {
            locationText.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        boolean isAttachIcon;
                        if (locationText.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR)
                            isAttachIcon = event.getRawX() >= locationText.getRight() - locationText.getTotalPaddingRight();
                        else
                            isAttachIcon = event.getRawX() <= locationText.getTotalPaddingLeft() - locationText.getLeft();

                        if (isAttachIcon) {
                            performFileSearch();
                            return true;
                        }

                    }
                    return false;
                }
            });

            // Tint the attachment icon, if any.
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);

            Drawable[] compoundDrawables = locationText.getCompoundDrawablesRelative();
            for (Drawable drawable : compoundDrawables) {
                if (drawable != null) {
                    drawable.setTint(ContextCompat.getColor(this, typedValue.resourceId));
                    Log.d(TAG, "onCreate: Setting tint");
                }
            }
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.item, menu);
        // We are creating an item
        if (!getIntent().hasExtra("ITEM_LOCATION")) {
            menu.findItem(R.id.action_delete).setVisible(false);
        }

        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_delete:
                Intent deleteIntent = new Intent();
                deleteIntent.putExtra("DELETE", true);
                setResult(RESULT_OK, deleteIntent);
                finish();
                break;
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
        }

        return super.onOptionsItemSelected(item);
    }
}
