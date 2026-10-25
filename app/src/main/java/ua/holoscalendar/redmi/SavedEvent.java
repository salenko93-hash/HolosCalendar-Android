package ua.holoscalendar.redmi;

import java.time.*;

/** A persisted event. Unknown duration remains unknown, including in the calendar. */
public final class SavedEvent {
    public final long id;
    public final String title;
    public final Instant start;
    public final ZoneId zone;
    public final Integer durationMinutes;

    public SavedEvent(long id, String title, Instant start, ZoneId zone, Integer durationMinutes) {
        ZonedDateTime local = start.atZone(zone);
        CalendarEvent validated = new CalendarEvent(title, local.toLocalDate(), local.toLocalTime(),
                durationMinutes, zone, start);
        this.id = id;
        this.title = validated.title;
        this.start = start;
        this.zone = zone;
        this.durationMinutes = durationMinutes;
    }

    public boolean occursOn(LocalDate date, ZoneId displayZone) {
        Instant dayStart = date.atStartOfDay(displayZone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(displayZone).toInstant();
        if (!start.isBefore(dayEnd)) return false;
        return !start.isBefore(dayStart) || durationMinutes != null
                && start.plusSeconds(durationMinutes * 60L).isAfter(dayStart);
    }
}
