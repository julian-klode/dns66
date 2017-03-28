/* Copyright (C) 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.IOException;

/**
 * Helper for database
 */
public class RuleDatabaseHelper extends SQLiteOpenHelper {
    // If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "DNS66.db";
    private final Context context;


    public RuleDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE hostfiles (" +
                "hostfile_id INTEGER PRIMARY KEY NOT NULL," +
                "title TEXT NOT NULL," +
                "location TEXT UNIQUE NOT NULL," +
                "action INTEGER NOT NULL," +
                "priority INTEGER NOT NULL," +
                "last_modified_millis INTEGER NOT NULL," +
                "last_updated_millis INTEGER  NOT NULL," +
                "last_seen_millis INTEGER  NOT NULL);");
        db.execSQL("CREATE TABLE hosts (" +
                "host TEXT  NOT NULL," +
                "hostfile_id INTEGER NOT NULL REFERENCES hostfiles(hostfile_id) ON DELETE CASCADE," +
                "target TEXT)");
        db.execSQL("CREATE VIEW host_action AS " +
                "SELECT host, action, target, priority FROM hosts NATURAL JOIN hostfiles " +
                "WHERE action != 2");
        db.execSQL("CREATE INDEX host_index on hosts(host);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onDowngrade(db, oldVersion, newVersion);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Just drop everything and recreate
        Cursor c = db.rawQuery("select type, name from sqlite_master", null);

        while (c.moveToNext()) {
            final String type = c.getString(0);
            final String name = c.getString(1);
            if (type.equals("table") || type.equals("view")) {
                Log.d("", "onUpgrade: Executing: " + "DROP " + c.getString(0) + " " + c.getString(1));
                db.execSQL("DROP " + c.getString(0) + " " + c.getString(1));
            }
        }

        c.close();

        onCreate(db);
    }
}