/* Copyright (C) 2016 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Spinner;

public class ItemActivity extends AppCompatActivity {


    private TextInputEditText locationText;
    private TextInputEditText titleText;
    private Spinner stateSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        setContentView(R.layout.activity_item);

        titleText = (TextInputEditText) findViewById(R.id.title);
        locationText = (TextInputEditText) findViewById(R.id.location);
        stateSpinner = (Spinner) findViewById(R.id.state);


        if (intent.hasExtra("ITEM_TITLE"))
            titleText.setText(intent.getStringExtra("ITEM_TITLE"));
        if (intent.hasExtra("ITEM_LOCATION"))
            locationText.setText(intent.getStringExtra("ITEM_LOCATION"));
        if (intent.hasExtra("ITEM_STATE"))
            stateSpinner.setSelection(intent.getIntExtra("ITEM_STATE", 0));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.item, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_save:
                Intent intent=new Intent();
                intent.putExtra("ITEM_TITLE", titleText.getText().toString());
                intent.putExtra("ITEM_LOCATION", locationText.getText().toString());
                intent.putExtra("ITEM_STATE", stateSpinner.getSelectedItemPosition());
                setResult(RESULT_OK, intent);
                finish();
                break;
        }

        return super.onOptionsItemSelected(item);
    }
}
