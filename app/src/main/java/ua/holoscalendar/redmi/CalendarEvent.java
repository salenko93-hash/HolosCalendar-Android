package ua.holoscalendar.redmi;

import java.time.*;
import java.util.List;

/** Validated event data, independent of Android intents and UI. */
public final class CalendarEvent {
    public final String title;
    public final LocalDateTime localStart;
    public final ZoneId zone;
    public final int durationMinutes;

    public CalendarEvent(String title, LocalDate date, LocalTime time, int durationMinutes, ZoneId zone) {
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("Missing title");
        if (date == null || time == null || zone == null) throw new IllegalArgumentException("Missing date, time or zone");
        if (durationMinutes < 1 || durationMinutes > 1440) throw new IllegalArgumentException("Invalid duration");
        this.title = title.trim();
        this.localStart = LocalDateTime.of(date, time);
        this.zone = zone;
        this.durationMinutes = durationMinutes;
    }

    public boolean isMissingLocalTime() { return offsets().isEmpty(); }
    public boolean isRepeatedLocalTime() { return offsets().size() > 1; }
    private List<ZoneOffset> offsets() { return zone.getRules().getValidOffsets(localStart); }

    /** First occurrence during an overlap. UI must ask the user before using it. */
    public long startMillis() {
        List<ZoneOffset> values = offsets();
        if (values.isEmpty()) throw new IllegalStateException("Time does not exist in this zone");
        return localStart.toInstant(values.get(0)).toEpochMilli();
    }
    public long endMillis() { return startMillis() + durationMinutes * 60_000L; }
}
