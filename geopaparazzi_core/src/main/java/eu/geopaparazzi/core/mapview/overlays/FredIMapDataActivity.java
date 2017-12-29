/*
 * Geopaparazzi - Digital field mapping on Android based devices
 * Copyright (C) 2010  HydroloGIS (www.hydrologis.com)
 * Fred - integrated field data recording
 * Copyright (C) 2014 New York Natural Heritage Program (nynhp.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.geopaparazzi.core.mapview.overlays;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.geopaparazzi.library.database.GPLog;
import eu.geopaparazzi.library.gps.GpsServiceStatus;
import eu.geopaparazzi.library.gps.GpsServiceUtilities;
import eu.geopaparazzi.library.util.GPDialogs;
import eu.geopaparazzi.library.util.LibraryConstants;
import eu.geopaparazzi.library.util.PositionUtilities;
import eu.geopaparazzi.library.util.Utilities;
import eu.geopaparazzi.core.R;
import eu.geopaparazzi.core.database.DatabaseManager;
import eu.geopaparazzi.core.mapview.MapviewActivity;
//import eu.geopaparazzi.library.gps.GpsManager;

/**
 * Fred activity.
 * 
 * @author Andrea Antonello (hydrologis.eu)
 * @author Tim Howard (nynhp.org)
 */

@TargetApi(11)

public class FredIMapDataActivity extends Activity {

    private static final String USE_MAPCENTER_POSITION = "USE_MAPCENTER_POSITION"; //$NON-NLS-1$
    private double latitude;
    private double longitude;
    private double elevation;
    private String recordID;
    private double mapCenterLatitude;
    private double mapCenterLongitude;
    private double mapCenterElevation;
    private double[] gpsLocation;
    private BroadcastReceiver gpsBroadcastReceiver;

    private ToggleButton togglePositionTypeButtonGps;
    private ToggleButton writeDataButton;
    private Spinner lvlOneSpinner;
    private Spinner lvlTwoSpinner;

    private List<String> secondIDs; // second level information -- e.g. obspoint
    private static String EXTERNAL_DB = "EXTERNAL_DB";//$NON-NLS-1$
    private static String EXTERNAL_DB_NAME = "EXTERNAL_DB_NAME";//$NON-NLS-1$
    private static String SECOND_LEVEL_TABLE = "SECOND_LEVEL_TABLE";//$NON-NLS-1$
    private static String COLUMN_SECOND_LEVEL_ID = "COLUMN_SECOND_LEVEL_ID";//$NON-NLS-1$
    private static String COLUMN_LAT = "COLUMN_LAT";//$NON-NLS-1$
    private static String COLUMN_LON = "COLUMN_LON";//$NON-NLS-1$
    private static String COLUMN_NOTE = "COLUMN_NOTE";//$NON-NLS-1$
    private static String COLUMN_SECOND_LEVEL_DESCRIPTOR = "COLUMN_SECOND_LEVEL_DESCRIPTOR";//$NON-NLS-1$
    private static String COLUMN_SECOND_LEVEL_TIMESTAMP = "COLUMN_SECOND_LEVEL_TIMESTAMP";//$NON-NLS-1$
    private static String PREFS_KEY_FRED_QUICK_SET = "PREFS_KEY_FRED_QUICK_SET";//$NON-NLS-1$


    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // get preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        final String externalDB = preferences.getString(EXTERNAL_DB, "default"); //$NON-NLS-1$
        final String externalDBname = preferences.getString(EXTERNAL_DB_NAME, "default12"); //$NON-NLS-1$
        final String childTable = preferences.getString(SECOND_LEVEL_TABLE, "default3"); //$NON-NLS-1$
        final String childID = preferences.getString(COLUMN_SECOND_LEVEL_ID, "default4"); //$NON-NLS-1$  
        final String colLat = preferences.getString(COLUMN_LAT, "default5"); //$NON-NLS-1$  
        final String colLon = preferences.getString(COLUMN_LON, "default6"); //$NON-NLS-1$  
        final String childDescriptorField = preferences.getString(COLUMN_SECOND_LEVEL_DESCRIPTOR, "default10"); //$NON-NLS-1$
        final String childTimeStamp = preferences.getString(COLUMN_SECOND_LEVEL_TIMESTAMP, "default11"); //$NON-NLS-1$
        final String quicksetChoice = preferences.getString(PREFS_KEY_FRED_QUICK_SET, "default11"); //$NON-NLS-1$

        // debug some of the defaults in case of problems
        if (GPLog.LOG_HEAVY) {
            GPLog.addLogEntry("fred", "prefs DB val: " + externalDB); //$NON-NLS-1$
            GPLog.addLogEntry("fred", "prefs child table: " + childTable); //$NON-NLS-1$
            GPLog.addLogEntry("fred", "prefs child ID: " + childID); //$NON-NLS-1$
        }

        // Get intent, action
        Intent intent = getIntent();

        latitude = intent.getDoubleExtra(LibraryConstants.LATITUDE, 0.0);
        longitude = intent.getDoubleExtra(LibraryConstants.LONGITUDE, 0.0);
        elevation = intent.getDoubleExtra(LibraryConstants.ELEVATION, 0.0);
        recordID = intent.getStringExtra("recordID");

        GPLog.addLogEntry("fred", "recordID is " + recordID); //$NON-NLS-1$

//        if (GPLog.LOG_HEAVY){
//            GPLog.addLogEntry(this, "Received intent action " + action); //$NON-NLS-1$
//            GPLog.addLogEntry(this, "Received intent type " + type); //$NON-NLS-1$
//        }

        // first off, check to see if dB exists
        final boolean dbExists = doesDatabaseExist(this, externalDB);
        if (!dbExists) {
            Toast.makeText(getApplicationContext(),
                    "Database does not exist", Toast.LENGTH_SHORT).show(); //$NON-NLS-1$
            finish();
        } else if (recordID == null){
            Toast.makeText(getApplicationContext(),
                    "No record ID, can't write point data", Toast.LENGTH_SHORT).show(); //$NON-NLS-1$
            finish();
        } else {
            secondIDs = new ArrayList<String>();
            try {
                final SQLiteDatabase sqlDB = DatabaseManager.getInstance().getDatabase(this)
                        .openDatabase(externalDB, null, 2);
                boolean tableExists = doesTableExist(childTable, sqlDB);
                if(tableExists) {
                    secondIDs = getTableIDs(sqlDB, childTable, childID, childDescriptorField, childTimeStamp, null, null, quicksetChoice);
                    // don't open form if no records
                    if(secondIDs.size()==0){
                        GPLog.addLogEntry(this, "Fred DB: no records 1 " + quicksetChoice);
                        GPDialogs.toast(this,"No records in DB - add one first",Toast.LENGTH_SHORT);
                        finish();
                    }
                } else {
                    GPLog.addLogEntry(this, "Fred DB: no Table 2 " + childTable);
                    GPDialogs.toast(this,"Missing table in DB - check settings", Toast.LENGTH_SHORT);
                    finish();
                }
                sqlDB.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        boolean IsWritten = false;
        try {
            final SQLiteDatabase sqlDB;
            sqlDB = DatabaseManager.getInstance().getDatabase(FredIMapDataActivity.this)
                    .openDatabase(externalDB, null, 2);
            IsWritten = writeGpsData(childTable, colLat, colLon,
                    childID, recordID, latitude, longitude, sqlDB);
            sqlDB.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


        if (IsWritten){
            Intent resultIntent = new Intent();
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        }

//        if (IsWritten){
//            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
//            List<ActivityManager.RunningTaskInfo> tasklist = am.getRunningTasks(10); // Number of tasks you want to get
//            if (!tasklist.isEmpty()) {
//                int nSize = tasklist.size();
//                boolean appFound = false;
//                for (int i = 0; i < nSize; i++) {
//                    ActivityManager.RunningTaskInfo taskinfo = tasklist.get(i);
//                    if (GPLog.LOG_HEAVY)
//                        GPLog.addLogEntry(this, "RunningTask " + i + " is " + taskinfo.topActivity.getPackageName()); //$NON-NLS-1$
//                    if (taskinfo.topActivity.getPackageName().equals("com.syware.droiddb")) {
//                        appFound = true;
//                        am.moveTaskToFront(taskinfo.id, 0);
//                    }
//                }
//                if (!appFound) {
//                    Intent intentDDB = new Intent("com.syware.droiddb"); //$NON-NLS-1$
//                    intentDDB.addFlags(intentDDB.FLAG_ACTIVITY_NEW_TASK);
//                    intentDDB.putExtra("parameter", externalDBname); //$NON-NLS-1$
//                    startActivity(intentDDB);
//                }
//            }
//        }


    }
    // TODO need an onStart() for this activity!!!
    // TODO need an onResume() for this activity!!!

    @Override
    public void onPause() {
        //maintain this boolean
        MapviewActivity.created = true;
        GPLog.addLogEntry(this, "Pausing Fred ... MapsActivity.created =  " + MapviewActivity.created); //$NON-NLS-1$

        super.onPause();
    }

    @Override
    public void onStop() {
        //maintain this boolean
        MapviewActivity.created = true;
        GPLog.addLogEntry(this, "Stopping Fred ... MapsActivity.created =  " + MapviewActivity.created); //$NON-NLS-1$

        super.onStop();
    }

    @Override
    protected void onDestroy() {

        if (gpsBroadcastReceiver != null)
            GpsServiceUtilities.unregisterFromBroadcasts(this, gpsBroadcastReceiver);

        //maintain this boolean
        MapviewActivity.created = true;
        GPLog.addLogEntry(this, "Destroying Fred ... MapsActivity.created =  " + MapviewActivity.created); //$NON-NLS-1$

        super.onDestroy();
    }

    /**
     * Gets a list of values from a table. This sorts descending
     * by time so the most recently created record appears first
     *
     * @param sqliteDatabase the DB to query
     * @param tableName      the table to query
     * @param IdCol          the ID column to grab
     * @param NameCol        the name column to grab (site name, or other text field)
     * @param tsCol          the time/date column
     * @param filterID       the column name on which to filter the list (parent table ID) using strWhere
     * @param strWhere       if selecting a row, which ID value to select on
     * @throws IOException if a problem
     */
    private static List<String> getTableIDs(SQLiteDatabase sqliteDatabase, String tableName, String IdCol, String NameCol,
                                            String tsCol, String filterID, String strWhere, String quickSet) throws IOException {

        GPLog.addLogEntry("in getTableIDs, tableName= " + tableName);
        GPLog.addLogEntry("in getTableIDs, IdCol= " + IdCol);
        GPLog.addLogEntry("in getTableIDs, NameCol= " + NameCol);

        String asColumnsToReturn[] = {NameCol, IdCol, tsCol};
        String strSortOrder = tsCol + " DESC"; //$NON-NLS-1$
        if (strWhere != null) {
            strWhere = filterID + "=" + strWhere; //$NON-NLS-1$
        }

        Cursor c = null;
        try {
            c = sqliteDatabase.query(tableName, asColumnsToReturn, strWhere, null, null, null, strSortOrder);
            int count = c.getCount();

            c.moveToFirst();
            List<String> NmIdTsList = new ArrayList<String>(count);
            String row = "";
            while (!c.isAfterLast()) {
                String fID = c.getString(1); // to handle non-int IDs
                // int fID = c.getInt(1);
                String fName = c.getString(0);
                String tStamp = c.getString(2);
                try {
                    //todo: customize for each type - does not work yet
                    if(quickSet == "Fred-Bot,Zool" && tableName == "IPAQ_SppSurvUtmList") {
                        row = "GPS ID = " + fName + " (created on " + tStamp + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } else {
                        //String row = fName + " (" + String.valueOf(fID) + ", " + tStamp + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        row = fName + " (" + fID + ", " + tStamp + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    }
                    NmIdTsList.add(row);
                } catch (Exception e) {
                    // ignore invalid rows
                }
                c.moveToNext();

                if (c.isAfterLast()) {
                    break;
                }
            }
            return NmIdTsList;
        } catch (Exception e) {
            GPLog.error("FredReadQuery", e.getLocalizedMessage(), e); //$NON-NLS-1$
            throw new IOException(e.getLocalizedMessage());
        } finally {
            if (c != null)
                c.close();
        }
    }


    /**
     * Writes GPS data to an external database
     *
     * @param tbl         is the name of the table to write to
     * @param colLat      is the column name for Latitude
     * @param colLon      is the column name for Longitude
     * @param colSecondID is the column name for the child ID column
     * @param lvlTwoID    the child ID for the record to update
     * @param ddLon       decimal degrees longitude
     * @param ddLat       decimal degrees latitude
     * @param sqlDB       the DB to write to
     * @throws IOException if a problem
     */
    private static boolean writeGpsData(String tbl, String colLat, String colLon,
                                        String colSecondID, String lvlTwoID, double ddLat, double ddLon,
                                        SQLiteDatabase sqlDB) throws IOException {

        try {
            sqlDB.beginTransaction();

            StringBuilder sb = new StringBuilder();
            sb.append("UPDATE "); //$NON-NLS-1$
            sb.append(tbl);
            sb.append(" SET "); //$NON-NLS-1$
            sb.append(colLat).append("=").append(ddLat).append(", "); //$NON-NLS-1$ //$NON-NLS-2$
            sb.append(colLon).append("=").append(ddLon).append(" "); //$NON-NLS-1$ //$NON-NLS-2$

            sb.append("WHERE ").append(colSecondID).append("= '").append(lvlTwoID).append("'"); //$NON-NLS-1$ //$NON-NLS-2$

            String query = sb.toString();
            if (GPLog.LOG_HEAVY)
                GPLog.addLogEntry("FredWriteQuery", query); //$NON-NLS-1$
            SQLiteStatement sqlUpdate = sqlDB.compileStatement(query);
            sqlUpdate.execute();
            sqlUpdate.close();

            sqlDB.setTransactionSuccessful();
        } catch (Exception e) {
            GPLog.error("FredWriteQuery", e.getLocalizedMessage(), e); //$NON-NLS-1$
            throw new IOException(e.getLocalizedMessage());
        } finally {
            sqlDB.endTransaction();
            sqlDB.close();
        }
        return true;
    }

    /**
     * Check to see if DB exists
     *
     * @param context is the context
     * @param dbName  is the name of the database to check
     */

    private static boolean doesDatabaseExist(Context context, String dbName) {
        File dbFile = context.getDatabasePath(dbName);
        return dbFile.exists();
    }

    /**
     * Check to see if a table in the DB exists
     *
     * @param tableName is the name of the table
     * @param sqlDb  is the name of the database
     * @param
     */
    public boolean doesTableExist(String tableName, SQLiteDatabase sqlDb) {

        Cursor cursor = sqlDb.rawQuery("select DISTINCT tbl_name from sqlite_master where tbl_name = '" + tableName + "'", null);
        if(cursor!=null) {
            if(cursor.getCount()>0) {
                cursor.close();
                return true;
            }
            cursor.close();
        }
        return false;
    }
}