package ua.holoscalendar.redmi;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Private local storage, with no dependency on calendar-provider permissions or accounts. */
final class EventStore extends SQLiteOpenHelper {
    EventStore(Context context) { this(context, "holoscalendar.db"); }
    EventStore(Context context, String databaseName) { super(context, databaseName, null, 2); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL CHECK(length(trim(title)) > 0), start_millis INTEGER NOT NULL, "
                + "zone_id TEXT NOT NULL, duration_minutes INTEGER "
                + "CHECK(duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 1440), "
                + "reminder_minutes INTEGER CHECK(reminder_minutes IS NULL OR reminder_minutes BETWEEN 0 AND 1440), "
                + "reminder_delivered_for INTEGER)");
        db.execSQL("CREATE INDEX events_start ON events(start_millis)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL("ALTER TABLE events ADD COLUMN reminder_minutes INTEGER "
                    + "CHECK(reminder_minutes IS NULL OR reminder_minutes BETWEEN 0 AND 1440)");
            db.execSQL("ALTER TABLE events ADD COLUMN reminder_delivered_for INTEGER");
        } else throw new IllegalStateException("A database migration is required");
    }

    long save(long id, CalendarEvent event) {
        return save(id, event, null);
    }

    long save(long id, CalendarEvent event, Integer reminderMinutes) {
        if (reminderMinutes != null && (reminderMinutes < 0 || reminderMinutes > 1440))
            throw new IllegalArgumentException("Invalid reminder");
        ContentValues values = new ContentValues();
        values.put("title", event.title);
        values.put("start_millis", event.startMillis());
        values.put("zone_id", event.zone.getId());
        if (event.durationMinutes == null) values.putNull("duration_minutes");
        else values.put("duration_minutes", event.durationMinutes);
        if (reminderMinutes == null) values.putNull("reminder_minutes");
        else values.put("reminder_minutes", reminderMinutes);
        SQLiteDatabase db = getWritableDatabase();
        if (id == 0) return db.insertOrThrow("events", null, values);
        SavedEvent previous = find(id);
        if (previous == null) throw new IllegalStateException("The event was already deleted");
        if (previous.start.toEpochMilli() != event.startMillis() || !Objects.equals(previous.reminderMinutes, reminderMinutes))
            values.putNull("reminder_delivered_for");
        if (db.update("events", values, "_id = ?", new String[]{Long.toString(id)}) != 1)
            throw new IllegalStateException("The event was already deleted");
        return id;
    }

    SavedEvent find(long id) {
        try (Cursor cursor = getReadableDatabase().query("events", null, "_id = ?",
                new String[]{Long.toString(id)}, null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    List<SavedEvent> between(Instant begin, Instant end) {
        long beginMillis = begin.toEpochMilli();
        // Selection args are text; arithmetic expressions need an explicit numeric cast.
        String where = "start_millis >= ? AND start_millis < ? AND "
                + "(start_millis >= ? OR (duration_minutes IS NOT NULL "
                + "AND start_millis + duration_minutes * 60000 > CAST(? AS INTEGER)))";
        String[] args = {Long.toString(beginMillis - 86_400_000L), Long.toString(end.toEpochMilli()),
                Long.toString(beginMillis), Long.toString(beginMillis)};
        List<SavedEvent> events = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("events", null, where, args,
                null, null, "start_millis ASC, _id ASC")) {
            while (cursor.moveToNext()) events.add(read(cursor));
        }
        return events;
    }

    boolean delete(long id) {
        return getWritableDatabase().delete("events", "_id = ?", new String[]{Long.toString(id)}) == 1;
    }

    SavedEvent nextReminder(Instant now) {
        try (Cursor cursor = getReadableDatabase().query("events", null,
                "reminder_minutes IS NOT NULL AND start_millis >= ? AND "
                + "(reminder_delivered_for IS NULL OR reminder_delivered_for != start_millis - reminder_minutes * 60000)",
                new String[]{Long.toString(now.toEpochMilli() - 300_000L)}, null, null,
                "start_millis - reminder_minutes * 60000 ASC, _id ASC", "1")) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    /** Atomically claim a still-current reminder so reboot or duplicate broadcasts cannot repeat it. */
    SavedEvent claimReminder(long id, long expectedTime, Instant now) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            SavedEvent event = find(id);
            if (event == null || !ReminderPolicy.ready(event, expectedTime, now)) return null;
            ContentValues values = new ContentValues();
            values.put("reminder_delivered_for", expectedTime);
            db.update("events", values, "_id = ?", new String[]{Long.toString(id)});
            db.setTransactionSuccessful();
            return event;
        } finally { db.endTransaction(); }
    }

    void releaseReminder(long id, long expectedTime) {
        ContentValues values = new ContentValues(); values.putNull("reminder_delivered_for");
        getWritableDatabase().update("events", values, "_id = ? AND reminder_delivered_for = ?",
                new String[]{Long.toString(id), Long.toString(expectedTime)});
    }

    private SavedEvent read(Cursor cursor) {
        int duration = cursor.getColumnIndexOrThrow("duration_minutes");
        int reminder = cursor.getColumnIndexOrThrow("reminder_minutes");
        int delivered = cursor.getColumnIndexOrThrow("reminder_delivered_for");
        return new SavedEvent(cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                Instant.ofEpochMilli(cursor.getLong(cursor.getColumnIndexOrThrow("start_millis"))),
                ZoneId.of(cursor.getString(cursor.getColumnIndexOrThrow("zone_id"))),
                cursor.isNull(duration) ? null : cursor.getInt(duration),
                cursor.isNull(reminder) ? null : cursor.getInt(reminder),
                cursor.isNull(delivered) ? null : cursor.getLong(delivered));
    }
}
