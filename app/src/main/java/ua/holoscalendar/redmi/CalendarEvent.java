package ua.holoscalendar.redmi;

import java.time.*;
import java.util.List;

/** Validated event data, independent of Android intents and UI. */
public final class CalendarEvent {
    public final String title;
    public final LocalDateTime localStart;
    public final ZoneId zone;
    /** Null means no end time was specified. */
    public final Integer durationMinutes;
    private final Instant exactStart;

    public CalendarEvent(String title, LocalDate date, LocalTime time, Integer durationMinutes, ZoneId zone) {
        this(title, date, time, durationMinutes, zone, null);
    }

    public CalendarEvent(String title, LocalDate date, LocalTime time, Integer durationMinutes, ZoneId zone, Instant exactStart) {
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("Missing title");
        if (date == null || time == null || zone == null) throw new IllegalArgumentException("Missing date, time or zone");
        if (durationMinutes != null && (durationMinutes < 1 || durationMinutes > 1440)) throw new IllegalArgumentException("Invalid duration");
        this.title = title.trim();
        this.localStart = LocalDateTime.of(date, time);
        this.zone = zone;
        this.durationMinutes = durationMinutes;
        if (exactStart != null && !exactStart.atZone(zone).toLocalDateTime().equals(localStart))
            throw new IllegalArgumentException("Exact start does not match the local fields");
        this.exactStart = exactStart;
    }

    public boolean isMissingLocalTime() { return offsets().isEmpty(); }
    public boolean isRepeatedLocalTime() { return offsets().size() > 1; }
    public boolean requiresOffsetConfirmation() { return exactStart == null && isRepeatedLocalTime(); }
    private List<ZoneOffset> offsets() { return zone.getRules().getValidOffsets(localStart); }

    /** Preserve a known instant; otherwise use the first overlap occurrence after UI confirmation. */
    public long startMillis() {
        if (exactStart != null) return exactStart.toEpochMilli();
        List<ZoneOffset> values = offsets();
        if (values.isEmpty()) throw new IllegalStateException("Time does not exist in this zone");
        return localStart.toInstant(values.get(0)).toEpochMilli();
    }
    public long endMillis() {
        if (durationMinutes == null) throw new IllegalStateException("Duration was not specified");
        return startMillis() + durationMinutes * 60_000L;
    }
}
