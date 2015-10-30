package com.example.asus.anxietytrackerapp;

import android.app.Activity;
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import java.util.Calendar;

class DiaryDbAdapter {

    public static final String KEY_TITLE = "title";
    public static final String KEY_BODY = "body";
    public static final String KEY_ROWID = "_id";
    public static final String KEY_CREATED = "created";

    private static final String TAG = "DiaryDbAdapter";
    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;

    private static final String DATABASE_CREATE = "create table diary (_id integer primary key autoincrement, "
            + "title text not null, body text not null, created text not null);";

    private static final String DATABASE_NAME = "database";
    private static final String DATABASE_TABLE = "diary";
    private static final int DATABASE_VERSION = 1;

    private final Context mCtx;

    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS diary");
            onCreate(db);
        }
    }

    public DiaryDbAdapter(Context ctx) {
        this.mCtx = ctx;
    }

    public DiaryDbAdapter open() throws SQLException {
        mDbHelper = new DatabaseHelper(mCtx);
        mDb = mDbHelper.getWritableDatabase();
        return this;
    }

    public void closeclose() {
        mDbHelper.close();
    }

    public long createDiary(String title, String body) {
        ContentValues initialValues = new ContentValues();
        initialValues.put(KEY_TITLE, title);
        initialValues.put(KEY_BODY, body);
        Calendar calendar = Calendar.getInstance();
        String created = calendar.get(Calendar.YEAR) + ""
                + calendar.get(Calendar.MONTH) + ""
                + calendar.get(Calendar.DAY_OF_MONTH) + ""
                + calendar.get(Calendar.HOUR_OF_DAY) + ""
                + calendar.get(Calendar.MINUTE) + "";
        initialValues.put(KEY_CREATED, created);
        return mDb.insert(DATABASE_TABLE, null, initialValues);
    }

    public boolean deleteDiary(long rowId) {

        return mDb.delete(DATABASE_TABLE, KEY_ROWID + "=" + rowId, null) > 0;
    }

    public Cursor getAllNotes() {

        return mDb.query(DATABASE_TABLE, new String[] { KEY_ROWID, KEY_TITLE,
                KEY_BODY, KEY_CREATED }, null, null, null, null, null);
    }

    public Cursor getDiary(long rowId) throws SQLException {

        Cursor mCursor =

                mDb.query(true, DATABASE_TABLE, new String[] { KEY_ROWID, KEY_TITLE,
                                KEY_BODY, KEY_CREATED }, KEY_ROWID + "=" + rowId, null, null,
                        null, null, null);
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;

    }

    public boolean updateDiary(long rowId, String title, String body) {
        ContentValues args = new ContentValues();
        args.put(KEY_TITLE, title);
        args.put(KEY_BODY, body);
        Calendar calendar = Calendar.getInstance();
        String created = calendar.get(Calendar.YEAR) + ""
                + calendar.get(Calendar.MONTH) + ""
                + calendar.get(Calendar.DAY_OF_MONTH) + ""
                + calendar.get(Calendar.HOUR_OF_DAY) + ""
                + calendar.get(Calendar.MINUTE) + "";
        args.put(KEY_CREATED, created);

        return mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
    }
}

class ActivityDiaryEdit extends Activity {

    private EditText mTitleText;
    private EditText mBodyText;
    private Long mRowId;
    private DiaryDbAdapter mDbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDbHelper = new DiaryDbAdapter(this);
        mDbHelper.open();
        setContentView(R.layout.diary_edit);

        mTitleText = (EditText) findViewById(R.id.title);
        mBodyText = (EditText) findViewById(R.id.body);

        Button confirmButton = (Button) findViewById(R.id.confirm);

        mRowId = null;
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String title = extras.getString(DiaryDbAdapter.KEY_TITLE);
            String body = extras.getString(DiaryDbAdapter.KEY_BODY);
            mRowId = extras.getLong(DiaryDbAdapter.KEY_ROWID);

            if (title != null) {
                mTitleText.setText(title);
            }
            if (body != null) {
                mBodyText.setText(body);
            }
        }

        confirmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String title = mTitleText.getText().toString();
                String body = mBodyText.getText().toString();
                if (mRowId != null) {
                    mDbHelper.updateDiary(mRowId, title, body);
                } else
                    mDbHelper.createDiary(title, body);
                Intent mIntent = new Intent();
                setResult(RESULT_OK, mIntent);
                finish();
            }

        });
    }
}

public class DailyAnxietyDiary extends ListActivity {
    private static final int ACTIVITY_CREATE = 0;
    private static final int ACTIVITY_EDIT = 1;

    private static final int INSERT_ID = Menu.FIRST;
    private static final int DELETE_ID = Menu.FIRST + 1;

    private DiaryDbAdapter mDbHelper;
    private Cursor mDiaryCursor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.diary_list);
        mDbHelper = new DiaryDbAdapter(this);
        mDbHelper.open();
        renderListView();

    }

    private void renderListView() {
        mDiaryCursor = mDbHelper.getAllNotes();
        startManagingCursor(mDiaryCursor);
        String[] from = new String[] { DiaryDbAdapter.KEY_TITLE,
                DiaryDbAdapter.KEY_CREATED };
        int[] to = new int[] { R.id.text1, R.id.created };
        SimpleCursorAdapter notes = new SimpleCursorAdapter(this,
                R.layout.diary_row, mDiaryCursor, from, to);
        setListAdapter(notes);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, INSERT_ID, 0, R.string.menu_insert);
        menu.add(0, DELETE_ID, 0, R.string.menu_delete);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case INSERT_ID:
                createDiary();
                return true;
            case DELETE_ID:
                mDbHelper.deleteDiary(getListView().getSelectedItemId());
                renderListView();
                return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    private void createDiary() {
        Intent i = new Intent(this, ActivityDiaryEdit.class);
        startActivityForResult(i, ACTIVITY_CREATE);
    }

    @Override

    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Cursor c = mDiaryCursor;
        c.moveToPosition(position);
        Intent i = new Intent(this, ActivityDiaryEdit.class);
        i.putExtra(DiaryDbAdapter.KEY_ROWID, id);
        i.putExtra(DiaryDbAdapter.KEY_TITLE, c.getString(c
                .getColumnIndexOrThrow(DiaryDbAdapter.KEY_TITLE)));
        i.putExtra(DiaryDbAdapter.KEY_BODY, c.getString(c
                .getColumnIndexOrThrow(DiaryDbAdapter.KEY_BODY)));
        startActivityForResult(i, ACTIVITY_EDIT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        renderListView();
    }
}




