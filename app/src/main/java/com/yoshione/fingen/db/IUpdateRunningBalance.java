package com.yoshione.fingen.db;

import java.io.IOException;

import android.database.sqlite.SQLiteDatabase;

/**
 * Created by slv on 29.03.2017.
 */

public interface IUpdateRunningBalance {
    void updateRunningBalance(SQLiteDatabase database) throws IOException;
}
