package ua.holoscalendar.redmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

/** One event at a time; the system calendar remains responsible for saving. */
public final class MainActivity extends Activity {
    private static final int VOICE = 100;
    private static final int[] DURATIONS = {15, 30, 60, 90, 120};
    private static final Locale UK = Locale.forLanguageTag("uk-UA");
    private EditText raw, name;
    private TextView status;
    private Button dateButton, timeButton, durationButton;
    private LocalDate selectedDate = LocalDate.now();
    private LocalTime selectedTime = LocalTime.NOON;
    private boolean datePresent, timePresent, sourceDirty;
    private int durationMinutes = 60;
    private boolean statusError;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        raw = findViewById(R.id.raw);
        name = findViewById(R.id.name);
        status = findViewById(R.id.status);
        dateButton = findViewById(R.id.date);
        timeButton = findViewById(R.id.time);
        durationButton = findViewById(R.id.duration);
        applyInsets();
        findViewById(R.id.voice).setOnClickListener(v -> recognize());
        findViewById(R.id.parse).setOnClickListener(v -> parse());
        findViewById(R.id.example).setOnClickListener(v -> { raw.setText(R.string.raw_hint); parse(); });
        dateButton.setOnClickListener(v -> pickDate());
        timeButton.setOnClickListener(v -> pickTime());
        durationButton.setOnClickListener(v -> pickDuration());
        findViewById(R.id.save).setOnClickListener(v -> openCalendar());
        findViewById(R.id.help).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.help).setMessage(R.string.help_body).setPositiveButton(android.R.string.ok, null).show());
        findViewById(R.id.clear).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.clear_title).setMessage(R.string.clear_message)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.clear, (d, w) -> clear()).show());
        if (saved != null) restore(saved);
        raw.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                sourceDirty = !s.toString().trim().isEmpty();
                setStatus(getString(sourceDirty ? R.string.dirty_source : R.string.initial_status), sourceDirty);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        refresh();
    }

    private void applyInsets() {
        View scroll = findViewById(R.id.scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        scroll.requestApplyInsets();
    }

    private void refresh() {
        dateButton.setText(datePresent ? getString(R.string.date_value,
                selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", UK))) : getString(R.string.choose_date));
        timeButton.setText(timePresent ? getString(R.string.time_value,
                selectedTime.format(DateTimeFormatter.ofPattern("HH:mm", UK))) : getString(R.string.choose_time));
        durationButton.setText(getString(R.string.duration_minutes, durationMinutes));
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
        VoiceParser.Result result = VoiceParser.parse(text, LocalDateTime.now());
        name.setText(result.title);
        name.setError(result.title.isEmpty() ? getString(R.string.missing_name) : null);
        datePresent = result.date != null;
        timePresent = result.time != null;
        if (datePresent) selectedDate = result.date;
        if (timePresent) selectedTime = result.time;
        sourceDirty = false;
        raw.setError(null);
        refresh();
        setStatus(result.warning, !datePresent || !timePresent || result.title.isEmpty());
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(raw.getWindowToken(), 0);
        findViewById(R.id.status).requestRectangleOnScreen(new android.graphics.Rect(0, 0, status.getWidth(), status.getHeight()));
    }

    private void pickDate() {
        LocalDate initial = datePresent ? selectedDate : LocalDate.now();
        new DatePickerDialog(this, (v, year, month, day) -> {
            selectedDate = LocalDate.of(year, month + 1, day); datePresent = true; refresh();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private void pickTime() {
        new TimePickerDialog(this, (v, hour, minute) -> {
            selectedTime = LocalTime.of(hour, minute); timePresent = true; refresh();
        }, selectedTime.getHour(), selectedTime.getMinute(), true).show();
    }

    private void pickDuration() {
        int checked = 2;
        for (int i = 0; i < DURATIONS.length; i++) if (DURATIONS[i] == durationMinutes) checked = i;
        new AlertDialog.Builder(this).setTitle(R.string.duration_title)
                .setSingleChoiceItems(R.array.duration_choices, checked, (dialog, index) -> {
                    durationMinutes = DURATIONS[index]; refresh(); dialog.dismiss();
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void clear() {
        raw.setText(""); name.setText(""); raw.setError(null); name.setError(null);
        selectedDate = LocalDate.now(); selectedTime = LocalTime.NOON;
        datePresent = false; timePresent = false; sourceDirty = false; durationMinutes = 60;
        setStatus(getString(R.string.initial_status), false); refresh();
    }

    private void recognize() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA");
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
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
                raw.setText(values.get(0)); parse(); return;
            }
        }
        setStatus(getString(R.string.voice_cancelled), false);
    }

    private void openCalendar() {
        if (sourceDirty) { raw.setError(getString(R.string.dirty_source)); raw.requestFocus(); return; }
        String title = name.getText().toString().trim();
        if (title.isEmpty()) { name.setError(getString(R.string.missing_name)); name.requestFocus(); return; }
        if (!datePresent || !timePresent) {
            setStatus(getString(!datePresent ? R.string.choose_date : R.string.choose_time), true);
            (!datePresent ? dateButton : timeButton).requestFocus(); return;
        }
        CalendarEvent event = new CalendarEvent(title, selectedDate, selectedTime, durationMinutes, ZoneId.systemDefault());
        if (event.isMissingLocalTime()) { setStatus(getString(R.string.dst_error), true); return; }
        if (event.startMillis() < System.currentTimeMillis()) {
            new AlertDialog.Builder(this).setTitle(R.string.past_title).setMessage(R.string.past_message)
                    .setNegativeButton(R.string.change, null)
                    .setPositiveButton(R.string.continue_action, (d, w) -> confirmOverlap(event)).show();
        } else confirmOverlap(event);
    }

    private void confirmOverlap(CalendarEvent event) {
        if (event.isRepeatedLocalTime()) {
            new AlertDialog.Builder(this).setTitle(R.string.dst_overlap_title).setMessage(R.string.dst_overlap_message)
                    .setNegativeButton(R.string.change, null)
                    .setPositiveButton(R.string.continue_action, (d, w) -> insert(event)).show();
        } else insert(event);
    }

    private void insert(CalendarEvent event) {
        Intent intent = new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI);
        intent.putExtra(CalendarContract.Events.TITLE, event.title);
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis());
        intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis());
        intent.putExtra(CalendarContract.Events.EVENT_TIMEZONE, event.zone.getId());
        try { startActivity(intent); setStatus(getString(R.string.calendar_opened), false); }
        catch (ActivityNotFoundException | SecurityException e) { setStatus(getString(R.string.calendar_missing), true); }
    }

    private void restore(Bundle state) {
        raw.setText(state.getString("raw", "")); name.setText(state.getString("name", ""));
        selectedDate = LocalDate.ofEpochDay(state.getLong("date", selectedDate.toEpochDay()));
        selectedTime = LocalTime.ofSecondOfDay(state.getInt("time", selectedTime.toSecondOfDay()));
        datePresent = state.getBoolean("datePresent"); timePresent = state.getBoolean("timePresent");
        durationMinutes = state.getInt("duration", 60); sourceDirty = state.getBoolean("sourceDirty");
        setStatus(state.getString("status", getString(R.string.initial_status)), state.getBoolean("statusError"));
    }

    @Override protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        // EditText restores its text after onCreate; restore our dirty flag afterwards.
        restore(state); refresh();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("raw", raw.getText().toString()); state.putString("name", name.getText().toString());
        state.putLong("date", selectedDate.toEpochDay()); state.putInt("time", selectedTime.toSecondOfDay());
        state.putBoolean("datePresent", datePresent); state.putBoolean("timePresent", timePresent);
        state.putInt("duration", durationMinutes); state.putBoolean("sourceDirty", sourceDirty);
        state.putString("status", status.getText().toString()); state.putBoolean("statusError", statusError);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() { super.onResume(); if (dateButton != null) refresh(); }
}
