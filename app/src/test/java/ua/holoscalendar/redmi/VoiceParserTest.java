package ua.holoscalendar.redmi;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
public class VoiceParserTest {
 private final LocalDateTime now=LocalDateTime.of(2026,10,23,10,0);
 private VoiceParser.Result parse(String s) { return VoiceParser.parse(s,now); }
 @Test public void dateIsNotTime() { var r=parse("Зустріч 05.11"); assertEquals(LocalDate.of(2026,11,5),r.date); assertNull(r.time); }
 @Test public void explicitTimeWins() { var r=parse("Зустріч 05.11 о 14:30"); assertEquals(LocalTime.of(14,30),r.time); assertEquals("Зустріч",r.title); }
 @Test public void dottedClockWithPreposition() { var r=parse("Завтра о 14.30 Зустріч"); assertEquals(now.toLocalDate().plusDays(1),r.date); assertEquals(LocalTime.of(14,30),r.time); }
 @Test public void titleKeepsSpellingAndPrepositions() { assertEquals("Зустріч у Києві",parse("25 жовтня о 14:30 Зустріч у Києві").title); }
 @Test public void titleMustNotBeInvented() { assertEquals("",parse("Завтра о 14:30").title); }
 @Test public void invalidClockIsNotPartiallyParsed() { var r=parse("Завтра о 25:00 Зустріч"); assertNull(r.time); assertTrue(r.warning.contains("Некоректний час")); }
 @Test public void invalidDateRequiresCorrection() { var r=parse("31.02 о 9 Зустріч"); assertNull(r.date); assertTrue(r.warning.contains("Некоректна дата")); }
 @Test public void leapDayFindsNextOccurrence() { assertEquals(LocalDate.of(2028,2,29),parse("29.02 о 9 Зустріч").date); }
 @Test public void weekdayIsConsumed() { var r=parse("У понеділок о 9 Нарада"); assertEquals(LocalDate.of(2026,10,26),r.date); assertEquals("Нарада",r.title); }
 @Test public void nextWeekIsExplicit() { assertEquals(LocalDate.of(2026,11,2),parse("Наступного понеділка о 9 Нарада").date); }
 @Test public void missingFieldsStayMissing() { var r=parse("Зустріч у Києві"); assertNull(r.date); assertNull(r.time); assertEquals("Зустріч у Києві",r.title); }
 @Test public void namedDateIncludesYear() { assertEquals(LocalDate.of(2027,1,25),parse("25 січня 2027 о 9 Зустріч").date); }
}
