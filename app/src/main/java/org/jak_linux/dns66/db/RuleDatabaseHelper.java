/* Copyright (C) 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Helper for database
 */
public class RuleDatabaseHelper extends SQLiteOpenHelper {
    // If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "DNS66.db";

    public RuleDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE ruleset (id INTEGER PRIMARY KEY, action INTEGER, priority INTEGER);");
        db.execSQL("CREATE TABLE rule (host TEXT, ruleset INTEGER REFERENCES ruleset(id) ON DELETE CASCADE)");
        db.execSQL("CREATE VIEW fullrule AS SELECT host, action, priority from ruleset JOIN rule ON rule.ruleset = ruleset.id;");
        db.execSQL("CREATE INDEX ruleindex ON rule(host);");
        db.execSQL("CREATE INDEX rulePriority ON ruleset(priority);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP VIEW IF EXISTS fullrule;");
        db.execSQL("DROP TABLE IF EXISTS rule;");
        db.execSQL("DROP TABLE IF EXISTS ruleset;");
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}