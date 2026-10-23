package ua.holoscalendar.redmi;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.*;

/** Local parser. Matched spans are consumed once; the remaining title keeps its spelling. */
public final class VoiceParser {
 private VoiceParser() {}
 public static final class Result {
  public final String title;
  public final LocalDate date;
  public final LocalTime time;
  public final String warning;
  Result(String title, LocalDate date, LocalTime time, String warning) {
   this.title=title; this.date=date; this.time=time; this.warning=warning;
  }
 }
 private static Pattern pattern(String s) { return Pattern.compile(s, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS); }
 // A dotted clock requires a time preposition; a bare dotted pair is a date.
 private static final Pattern CLOCK=pattern("(?<![\\p{L}\\d])(?:(?:о|об|на)\\s+\\d{1,2}[.:]\\d{2}|\\d{1,2}:\\d{2})(?!\\d)");
 private static final Pattern DATE=pattern("(?<![\\p{L}\\d])(?:[ув]\\s+)?(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{4}))?(?![\\d./])");
 private static final String[] MONTHS={"січня","лютого","березня","квітня","травня","червня","липня","серпня","вересня","жовтня","листопада","грудня"};
 private static final Pattern NAMED=pattern("(?<!\\p{L})(?:[ув]\\s+)?(\\d{1,2})\\s+("+String.join("|",MONTHS)+")(?:\\s+(\\d{4}))?(?!\\p{L})");
 private static final Pattern RELATIVE=pattern("\\b(?:[ув]\\s+)?(післязавтра|завтра|сьогодні)\\b");
 private static final Pattern WEEKDAY=pattern("\\b(?:[ув]\\s+)?(?:(наступного|наступної|наступну)\\s+)?(понеділ(?:ок|ка)|вівтор(?:ок|ка)|серед(?:а|у|и)|четвер(?:га)?|п['’]ятниц(?:я|ю|і)|субот(?:а|у|и)|неділ(?:я|ю|і))\\b");
 private static final Pattern HOUR=pattern("\\b(?:о|об)\\s+(\\d{1,2})(?:\\s+годин(?:і|у)?)?(?![\\d.:])\\b");
 private static void consume(StringBuilder s, Matcher m) { for(int i=m.start();i<m.end();i++) s.setCharAt(i,' '); }
 private static LocalDate date(int d,int m,String y,LocalDate now) {
  if(y!=null) return LocalDate.of(Integer.parseInt(y),m,d);
  // Find the next valid occurrence, including February 29 in a leap year.
  for(int year=now.getYear();year<=now.getYear()+8;year++) {
   try { LocalDate value=LocalDate.of(year,m,d); if(!value.isBefore(now)) return value; }
   catch(DateTimeException ignored) { }
  }
  throw new DateTimeException("Invalid date");
 }
 public static Result parse(String original, LocalDateTime now) {
  StringBuilder remaining=new StringBuilder(original.trim());
  LocalDate day=null; LocalTime time=null; boolean badDate=false,badTime=false;
  Matcher clock=CLOCK.matcher(remaining);
  if(clock.find()) {
   String[] parts=clock.group().replaceFirst("(?iu)^(?:о|об|на)\\s+", "").split("[.:]");
   try { time=LocalTime.of(Integer.parseInt(parts[0]),Integer.parseInt(parts[1])); }
   catch(DateTimeException e) { badTime=true; }
   consume(remaining,clock);
  }
  Matcher numeric=DATE.matcher(remaining), named=NAMED.matcher(remaining);
  if(numeric.find()) {
   try { day=date(Integer.parseInt(numeric.group(1)),Integer.parseInt(numeric.group(2)),numeric.group(3),now.toLocalDate()); }
   catch(DateTimeException e) { badDate=true; }
   consume(remaining,numeric);
  } else if(named.find()) {
   int month=Arrays.asList(MONTHS).indexOf(named.group(2).toLowerCase(Locale.ROOT))+1;
   try { day=date(Integer.parseInt(named.group(1)),month,named.group(3),now.toLocalDate()); }
   catch(DateTimeException e) { badDate=true; }
   consume(remaining,named);
  } else {
   Matcher relative=RELATIVE.matcher(remaining), weekday=WEEKDAY.matcher(remaining);
   if(relative.find()) {
    String word=relative.group(1).toLowerCase(Locale.ROOT);
    day=now.toLocalDate().plusDays(word.equals("сьогодні")?0:word.equals("завтра")?1:2);
    consume(remaining,relative);
   } else if(weekday.find()) {
    String word=weekday.group(2).toLowerCase(Locale.ROOT);
    int n=word.startsWith("пон")?1:word.startsWith("вів")?2:word.startsWith("сер")?3:word.startsWith("чет")?4:word.startsWith("п")?5:word.startsWith("суб")?6:7;
    day=now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(n)));
    if(weekday.group(1)!=null) day=day.plusWeeks(1);
    consume(remaining,weekday);
   }
  }
  if(time==null && !badTime) {
   Matcher hour=HOUR.matcher(remaining);
   if(hour.find()) {
    try { time=LocalTime.of(Integer.parseInt(hour.group(1)),0); }
    catch(DateTimeException e) { badTime=true; }
    consume(remaining,hour);
   }
  }
  String title=remaining.toString().replaceAll("\\s+"," ").replaceAll("^[,.:;\\s]+|[,.:;\\s]+$","");
  String warning=badDate?"Некоректна дата — оберіть її вручну":badTime?"Некоректний час — оберіть його вручну":day==null?"Оберіть дату":time==null?"Оберіть час":title.isEmpty()?"Додайте назву події":"Перевірте розпізнані дані";
  return new Result(title,day,time,warning);
 }
}
