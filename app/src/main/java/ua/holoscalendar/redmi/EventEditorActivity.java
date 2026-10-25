package ua.holoscalendar.redmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.widget.Toast;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

/** One event at a time; the system calendar remains responsible for saving. */
public final class EventEditorActivity extends Activity {
    static final String EVENT_ID = "eventId";
    private static final int VOICE = 100;
    private static final int MAX_SPEECH_LENGTH = 1000;
    private static final int[] DURATIONS = {15, 30, 60, 90, 120};
    private static final Locale UK = Locale.forLanguageTag("uk-UA");
    private EditText raw, name;
    private TextView status;
    private Button dateButton, timeButton, durationButton;
    private LocalDate selectedDate = LocalDate.now();
    private LocalTime selectedTime = LocalTime.NOON;
    private boolean datePresent, timePresent, sourceDirty;
    private Integer durationMinutes;
    private boolean statusError;
    private String lastParsedText = "";
    private Instant relativeInstant;
    private long editingId;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        raw = findViewById(R.id.raw);
        name = findViewById(R.id.name);
        status = findViewById(R.id.status);
        dateButton = findViewById(R.id.date);
        timeButton = findViewById(R.id.time);
        durationButton = findViewById(R.id.duration);
        ScreenInsets.apply(findViewById(R.id.scroll));
        findViewById(R.id.voice).setOnClickListener(v -> recognize());
        findViewById(R.id.parse).setOnClickListener(v -> parse());
        findViewById(R.id.example).setOnClickListener(v -> { raw.setText(R.string.raw_hint); parse(); });
        dateButton.setOnClickListener(v -> pickDate());
        timeButton.setOnClickListener(v -> pickTime());
        durationButton.setOnClickListener(v -> pickDuration());
        findViewById(R.id.save).setOnClickListener(v -> openCalendar());
        findViewById(R.id.save_local).setOnClickListener(v -> prepareEvent(true));
        findViewById(R.id.my_calendar).setOnClickListener(v -> showOwnCalendar(datePresent ? selectedDate : LocalDate.now()));
        findViewById(R.id.help).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(getString(R.string.help_version, getString(R.string.app_version))).setMessage(R.string.help_body).setPositiveButton(android.R.string.ok, null).show());
        findViewById(R.id.clear).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.clear_title).setMessage(R.string.clear_message)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.clear, (d, w) -> clear()).show());
        if (saved != null) restore(saved);
        else initializeEvent();
        raw.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String current = s.toString().trim();
                sourceDirty = !current.isEmpty() && !current.equals(lastParsedText);
                setStatus(getString(sourceDirty ? R.string.dirty_source : current.isEmpty()
                        ? R.string.initial_status : R.string.review_ready), sourceDirty);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        refresh();
    }

    private void initializeEvent() {
        long id = getIntent().getLongExtra(EVENT_ID, 0);
        if (id > 0) {
            try (EventStore store = new EventStore(this)) {
                SavedEvent event = store.find(id);
                if (event == null) {
                    setStatus(getString(R.string.event_missing), true);
                    findViewById(R.id.save_local).setEnabled(false); return;
                }
                editingId = id; name.setText(event.title);
                relativeInstant = event.start;
                ZonedDateTime start = event.start.atZone(ZoneId.systemDefault());
                selectedDate = start.toLocalDate(); selectedTime = start.toLocalTime();
                datePresent = true; timePresent = true; durationMinutes = event.durationMinutes;
                setStatus(getString(R.string.editing_event), false);
            } catch (SQLiteException | IllegalArgumentException | IllegalStateException | DateTimeException error) {
                setStatus(getString(R.string.local_read_error), true);
                findViewById(R.id.save_local).setEnabled(false);
            }
        } else {
            String initial = getIntent().getStringExtra(CalendarActivity.SELECTED_DATE);
            if (initial != null) {
                try { selectedDate = LocalDate.parse(initial); datePresent = true; }
                catch (DateTimeException ignored) { }
            }
        }
    }

    private void refresh() {
        if (relativeInstant != null) {
            ZonedDateTime local = relativeInstant.atZone(ZoneId.systemDefault());
            selectedDate = local.toLocalDate(); selectedTime = local.toLocalTime();
        }
        dateButton.setText(datePresent ? getString(R.string.date_value,
                selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", UK))) : getString(R.string.choose_date));
        timeButton.setText(timePresent ? getString(R.string.time_value,
                selectedTime.format(DateTimeFormatter.ofPattern("HH:mm", UK))) : getString(R.string.choose_time));
        durationButton.setText(durationMinutes == null ? getString(R.string.duration_unspecified)
                : getString(R.string.duration_minutes, durationMinutes));
        ((Button) findViewById(R.id.save_local)).setText(editingId == 0 ? R.string.save_local : R.string.update_local);
        ((TextView) findViewById(R.id.timezone)).setText(getString(R.string.timezone, ZoneId.systemDefault().getId()));
    }

    private void setStatus(String message, boolean error) {
        statusError = error;
        status.setText(message);
        status.setTextColor(getColor(error ? R.color.error : R.color.ink));
    }

    private void parse() {
        String text = raw.getText().toString().trim();
        if (text.isEmpty()) { raw.setError(getString(R.string.empty_input)); raw.requestFocus(); return; }
        VoiceParser.Result result = VoiceParser.parse(text, ZonedDateTime.now());
        relativeInstant = result.instant;
        durationMinutes = result.durationMinutes;
        name.setText(result.title);
        name.setError(result.title.isEmpty() ? getString(R.string.missing_name) : null);
        datePresent = result.date != null;
        timePresent = result.time != null;
        if (datePresent) selectedDate = result.date;
        if (timePresent) selectedTime = result.time;
        lastParsedText = text;
        sourceDirty = false;
        raw.setError(null);
        refresh();
        setStatus(result.warning, !datePresent || !timePresent || result.title.isEmpty());
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(raw.getWindowToken(), 0);
        findViewById(R.id.status).requestRectangleOnScreen(new android.graphics.Rect(0, 0, status.getWidth(), status.getHeight()));
    }

    private void updateReadiness() {
        if (sourceDirty) return;
        setStatus(getString(!datePresent ? R.string.choose_date : !timePresent ? R.string.choose_time
                : name.getText().toString().trim().isEmpty() ? R.string.missing_name : R.string.review_ready),
                !datePresent || !timePresent || name.getText().toString().trim().isEmpty());
    }

    private void pickDate() {
        LocalDate initial = datePresent ? selectedDate : LocalDate.now();
        new DatePickerDialog(this, (v, year, month, day) -> {
            relativeInstant = null;
            selectedDate = LocalDate.of(year, month + 1, day); datePresent = true; refresh(); updateReadiness();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private void pickTime() {
        new TimePickerDialog(this, (v, hour, minute) -> {
            relativeInstant = null;
            selectedTime = LocalTime.of(hour, minute); timePresent = true; refresh(); updateReadiness();
        }, selectedTime.getHour(), selectedTime.getMinute(), true).show();
    }

    private void pickDuration() {
        ArrayList<Integer> values = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        values.add(null); labels.add(getString(R.string.duration_none));
        for (int value : DURATIONS) {
            values.add(value); labels.add(getString(R.string.duration_minutes, value));
        }
        if (durationMinutes != null && !values.contains(durationMinutes)) {
            values.add(durationMinutes); labels.add(getString(R.string.duration_minutes, durationMinutes));
        }
        int checked = values.indexOf(durationMinutes);
        new AlertDialog.Builder(this).setTitle(R.string.duration_title)
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, index) -> {
                    durationMinutes = values.get(index); refresh(); dialog.dismiss();
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void clear() {
        raw.setText(""); name.setText(""); raw.setError(null); name.setError(null);
        selectedDate = LocalDate.now(); selectedTime = LocalTime.NOON;
        datePresent = false; timePresent = false; sourceDirty = false; durationMinutes = null;
        lastParsedText = ""; relativeInstant = null; editingId = 0;
        findViewById(R.id.save_local).setEnabled(true);
        setStatus(getString(R.string.initial_status), false); refresh();
    }

    private void recognize() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA");
        // Let the provider use network recognition when no Ukrainian offline model is available.
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt));
        try { startActivityForResult(intent, VOICE); }
        catch (ActivityNotFoundException | SecurityException e) { setStatus(getString(R.string.voice_missing), true); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != VOICE) return;
        if (result == RESULT_OK && data != null) {
            ArrayList<String> values = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (values != null && !values.isEmpty() && values.get(0) != null && !values.get(0).trim().isEmpty()) {
                if (values.get(0).length() > MAX_SPEECH_LENGTH) {
                    setStatus(getString(R.string.voice_too_long), true); return;
                }
                raw.setText(values.get(0)); parse(); return;
            }
        }
        setStatus(getString(R.string.voice_cancelled), false);
    }

    private void openCalendar() { prepareEvent(false); }

    private void prepareEvent(boolean local) {
        refresh();
        if (sourceDirty) { raw.setError(getString(R.string.dirty_source)); raw.requestFocus(); return; }
        String title = name.getText().toString().trim();
        if (title.isEmpty()) { name.setError(getString(R.string.missing_name)); name.requestFocus(); return; }
        if (!datePresent || !timePresent) {
            setStatus(getString(!datePresent ? R.string.choose_date : R.string.choose_time), true);
            (!datePresent ? dateButton : timeButton).requestFocus(); return;
        }
        CalendarEvent event = new CalendarEvent(title, selectedDate, selectedTime, durationMinutes, ZoneId.systemDefault(), relativeInstant);
        if (event.isMissingLocalTime()) { setStatus(getString(R.string.dst_error), true); return; }
        if (event.startMillis() < System.currentTimeMillis()) {
            new AlertDialog.Builder(this).setTitle(R.string.past_title).setMessage(R.string.past_message)
                    .setNegativeButton(R.string.change, null)
                    .setPositiveButton(R.string.continue_action, (d, w) -> confirmOverlap(event, local)).show();
        } else confirmOverlap(event, local);
    }

    private void confirmOverlap(CalendarEvent event, boolean local) {
        if (event.requiresOffsetConfirmation()) {
            new AlertDialog.Builder(this).setTitle(R.string.dst_overlap_title).setMessage(R.string.dst_overlap_message)
                    .setNegativeButton(R.string.change, null)
                    .setPositiveButton(R.string.continue_action, (d, w) -> complete(event, local)).show();
        } else complete(event, local);
    }

    private void complete(CalendarEvent event, boolean local) {
        if (!local) { insert(event); return; }
        try (EventStore store = new EventStore(this)) {
            editingId = store.save(editingId, event);
            refresh();
            setStatus(getString(R.string.local_saved), false);
            Toast.makeText(this, R.string.local_saved, Toast.LENGTH_SHORT).show();
            showOwnCalendar(selectedDate);
        } catch (SQLiteException | IllegalStateException error) {
            setStatus(getString(R.string.local_write_error), true);
        }
    }

    private void showOwnCalendar(LocalDate date) {
        startActivity(new Intent(this, CalendarActivity.class)
                .putExtra(CalendarActivity.SELECTED_DATE, date.toString())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void insert(CalendarEvent event) {
        Intent intent = new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI);
        intent.putExtra(CalendarContract.Events.TITLE, event.title);
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis());
        if (event.durationMinutes != null)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis());
        intent.putExtra(CalendarContract.Events.EVENT_TIMEZONE, event.zone.getId());
        try { startActivity(intent); setStatus(getString(R.string.calendar_opened), false); }
        catch (ActivityNotFoundException | SecurityException e) { setStatus(getString(R.string.calendar_missing), true); }
    }

    private void restore(Bundle state) {
        editingId = state.getLong(EVENT_ID);
        findViewById(R.id.save_local).setEnabled(state.getBoolean("localSaveEnabled", true));
        lastParsedText = state.getString("lastParsedText", state.getBoolean("sourceDirty") ? "" : state.getString("raw", ""));
        String savedInstant = state.getString("relativeInstant");
        relativeInstant = savedInstant == null ? null : Instant.parse(savedInstant);
        raw.setText(state.getString("raw", "")); name.setText(state.getString("name", ""));
        selectedDate = LocalDate.ofEpochDay(state.getLong("date", selectedDate.toEpochDay()));
        selectedTime = LocalTime.ofSecondOfDay(state.getInt("time", selectedTime.toSecondOfDay()));
        datePresent = state.getBoolean("datePresent"); timePresent = state.getBoolean("timePresent");
        durationMinutes = state.containsKey("duration") ? state.getInt("duration") : null; sourceDirty = state.getBoolean("sourceDirty");
        setStatus(state.getString("status", getString(R.string.initial_status)), state.getBoolean("statusError"));
    }

    @Override protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        // EditText restores its text after onCreate; restore our dirty flag afterwards.
        restore(state); refresh();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong(EVENT_ID, editingId);
        state.putBoolean("localSaveEnabled", findViewById(R.id.save_local).isEnabled());
        state.putString("raw", raw.getText().toString()); state.putString("name", name.getText().toString());
        state.putLong("date", selectedDate.toEpochDay()); state.putInt("time", selectedTime.toSecondOfDay());
        state.putBoolean("datePresent", datePresent); state.putBoolean("timePresent", timePresent);
        if (durationMinutes != null) state.putInt("duration", durationMinutes);
        state.putBoolean("sourceDirty", sourceDirty);
        state.putString("lastParsedText", lastParsedText);
        if (relativeInstant != null) state.putString("relativeInstant", relativeInstant.toString());
        state.putString("status", status.getText().toString()); state.putBoolean("statusError", statusError);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() { super.onResume(); if (dateButton != null) refresh(); }
}
