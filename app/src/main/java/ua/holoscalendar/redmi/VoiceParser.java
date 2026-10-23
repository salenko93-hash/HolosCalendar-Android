package ua.holoscalendar.redmi;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.*;
public final class VoiceParser {
 private VoiceParser(){}
 public static final class Result {
  public final String title;
  public final LocalDate date;
  public final LocalTime time;
  public final String warning;
  Result(String title,LocalDate date,LocalTime time,String warning){this.title=title;this.date=date;this.time=time;this.warning=warning;}
 }
 private static final Pattern CLOCK=Pattern.compile("(?<!\\d)(?:о\\s+|об\\s+|на\\s+)?([01]?\\d|2[0-3])[:.]([0-5]\\d)(?!\\d)");
 private static final Pattern HOUR=Pattern.compile("\\b(?:о|об)\\s+([01]?\\d|2[0-3])(?:\\s+годин(?:і|у)?)?\\b");
 private static final Pattern NUMBER_DATE=Pattern.compile("(?<!\\d)([0-3]?\\d)[./]([01]?\\d)(?:[./](20\\d{2}))?(?!\\d)");
 private static final Map<String,DayOfWeek> DAYS=new LinkedHashMap<>();
 static {DAYS.put("понеділ",DayOfWeek.MONDAY);DAYS.put("вівтор",DayOfWeek.TUESDAY);DAYS.put("серед",DayOfWeek.WEDNESDAY);DAYS.put("четвер",DayOfWeek.THURSDAY);DAYS.put("п'ятниц",DayOfWeek.FRIDAY);DAYS.put("субот",DayOfWeek.SATURDAY);DAYS.put("неділ",DayOfWeek.SUNDAY);}
 public static Result parse(String original,LocalDateTime now){
  String s=original.trim().toLowerCase(new Locale("uk","UA")).replace('’','\'');
  LocalDate date=null;LocalTime time=null;boolean bad=false;
  Matcher dm=NUMBER_DATE.matcher(s);
  if(dm.find()){try{int year=dm.group(3)==null?now.getYear():Integer.parseInt(dm.group(3));date=LocalDate.of(year,Integer.parseInt(dm.group(2)),Integer.parseInt(dm.group(1)));if(dm.group(3)==null&&date.isBefore(now.toLocalDate()))date=date.plusYears(1);}catch(Exception e){bad=true;}}
  else if(s.contains("післязавтра"))date=now.toLocalDate().plusDays(2);
  else if(s.contains("завтра"))date=now.toLocalDate().plusDays(1);
  else if(s.contains("сьогодні"))date=now.toLocalDate();
  else for(Map.Entry<String,DayOfWeek> e:DAYS.entrySet()){if(s.contains(e.getKey())){date=now.toLocalDate().with(TemporalAdjusters.nextOrSame(e.getValue()));if(s.contains("наступн"))date=date.plusWeeks(1);break;}}
  Matcher tm=CLOCK.matcher(s),hr=HOUR.matcher(s);
  if(tm.find())time=LocalTime.of(Integer.parseInt(tm.group(1)),Integer.parseInt(tm.group(2)));
  else if(hr.find())time=LocalTime.of(Integer.parseInt(hr.group(1)),0);
  String title=s.replaceAll("(?iu)\\b(сьогодні|завтра|післязавтра|наступного|наступної|наступну)\\b"," ");
  title=NUMBER_DATE.matcher(title).replaceAll(" ");title=CLOCK.matcher(title).replaceAll(" ");title=HOUR.matcher(title).replaceAll(" ");
  for(String key:DAYS.keySet())title=title.replaceAll("(?iu)\\b[ув]?\\s*"+key+"[а-яіїє']*\\b"," ");
  title=title.replaceAll("(?iu)\\b(?:у|в|о|об|на)\\b"," ").replaceAll("\\s+"," ").trim().replaceAll("^[,.:; ]+|[,.:; ]+$","");
  if(title.isEmpty())title=original.trim();
  String warning=bad?"Некоректна дата — виберіть її вручну":date==null?"Уточніть дату":time==null?"Уточніть час":"Перевірте подію";
  return new Result(title,date,time,warning);
 }
}
