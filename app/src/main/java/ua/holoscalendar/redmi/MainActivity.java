package ua.holoscalendar.redmi;
import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.provider.CalendarContract;
import android.speech.RecognizerIntent;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
public final class MainActivity extends Activity {
 private static final int VOICE=100;
 private EditText raw,name;
 private TextView status;
 private Button dateButton,timeButton;
 private LocalDate selectedDate;
 private LocalTime selectedTime;
 private boolean dateOK=false,timeOK=false;
 private final DateTimeFormatter fmt=DateTimeFormatter.ofPattern("dd.MM.yyyy");
 private int px(int dp){return Math.round(getResources().getDisplayMetrics().density*dp);}
 private TextView label(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.rgb(25,40,63));v.setPadding(0,px(10),0,px(8));return v;}
 private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextAllCaps(false);b.setTextSize(16);return b;}
 @Override public void onCreate(Bundle saved){super.onCreate(saved);selectedDate=LocalDate.now();selectedTime=LocalTime.NOON;
  ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(px(20),px(22),px(20),px(28));root.setBackgroundColor(Color.rgb(246,249,253));scroll.addView(root);setContentView(scroll);
  root.addView(label("ГолосКалендар",29));root.addView(label("Redmi Note 15 Pro • Без власного сервера",13));
  Button voice=button("🎙 Продиктувати українською");voice.setOnClickListener(v->recognize());root.addView(voice,new LinearLayout.LayoutParams(-1,px(60)));
  root.addView(label("Ваш текст",16));raw=new EditText(this);raw.setHint("Завтра о 14:30 зустріч із колегами");raw.setMinLines(2);raw.setMaxLines(5);root.addView(raw);
  Button process=button("Визначити подію");process.setOnClickListener(v->parse());root.addView(process,new LinearLayout.LayoutParams(-1,px(54)));
  root.addView(label("Назва",16));name=new EditText(this);root.addView(name);
  root.addView(label("Дата",16));dateButton=button("");dateButton.setOnClickListener(v->pickDate());root.addView(dateButton);
  root.addView(label("Час",16));timeButton=button("");timeButton.setOnClickListener(v->pickTime());root.addView(timeButton);
  Button save=button("📅 Відкрити в календарі");save.setOnClickListener(v->openCalendar());root.addView(save,new LinearLayout.LayoutParams(-1,px(64)));
  status=label("Продиктуйте подію, перевірте результат і збережіть її в календарі.",14);root.addView(status);
  if(saved!=null){raw.setText(saved.getString("raw",""));name.setText(saved.getString("name",""));selectedDate=LocalDate.ofEpochDay(saved.getLong("date",selectedDate.toEpochDay()));selectedTime=LocalTime.ofSecondOfDay(saved.getInt("time",selectedTime.toSecondOfDay()));dateOK=saved.getBoolean("dateOK");timeOK=saved.getBoolean("timeOK");}refresh();
 }
 private void refresh(){dateButton.setText(selectedDate.format(fmt)+(dateOK?" ✓":" • уточніть"));timeButton.setText(selectedTime.toString()+(timeOK?" ✓":" • уточніть"));}
 private void parse(){String s=raw.getText().toString().trim();if(s.isEmpty()){status.setText("Введіть текст.");return;}VoiceParser.Result r=VoiceParser.parse(s,LocalDateTime.now());name.setText(r.title);if(r.date!=null){selectedDate=r.date;dateOK=true;}else dateOK=false;if(r.time!=null){selectedTime=r.time;timeOK=true;}else timeOK=false;refresh();status.setText(r.warning);}
 private void pickDate(){new DatePickerDialog(this,(v,y,m,d)->{selectedDate=LocalDate.of(y,m+1,d);dateOK=true;refresh();},selectedDate.getYear(),selectedDate.getMonthValue()-1,selectedDate.getDayOfMonth()).show();}
 private void pickTime(){new TimePickerDialog(this,(v,h,m)->{selectedTime=LocalTime.of(h,m);timeOK=true;refresh();},selectedTime.getHour(),selectedTime.getMinute(),true).show();}
 private void recognize(){Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"uk-UA");i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"uk-UA");i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Назвіть подію, дату й час");try{startActivityForResult(i,VOICE);}catch(Exception e){status.setText("Мовне розпізнавання недоступне. Скористайтеся ручним введенням.");}}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==VOICE){if(result==RESULT_OK&&data!=null){ArrayList<String> values=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(values!=null&&!values.isEmpty()){raw.setText(values.get(0));parse();return;}}status.setText("Розпізнавання скасовано або не вдалося.");}}
 private void openCalendar(){String title=name.getText().toString().trim();if(title.isEmpty()){status.setText("Вкажіть назву події.");return;}if(!dateOK||!timeOK){status.setText("Уточніть дату й час.");return;}LocalDateTime dt=LocalDateTime.of(selectedDate,selectedTime);if(dt.isBefore(LocalDateTime.now())){new AlertDialog.Builder(this).setTitle("Дата в минулому").setMessage("Продовжити?").setNegativeButton("Змінити",null).setPositiveButton("Продовжити",(d,w)->insert(title,dt)).show();}else insert(title,dt);}
 private void insert(String title,LocalDateTime dt){long begin=dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();long end=dt.plusHours(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();Intent i=new Intent(Intent.ACTION_INSERT);i.setData(CalendarContract.Events.CONTENT_URI);i.putExtra(CalendarContract.Events.TITLE,title);i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,begin);i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,end);try{startActivity(i);status.setText("Натисніть «Зберегти» у календарі.");}catch(Exception e){status.setText("Не знайдено календаря для створення події.");}}
 @Override protected void onSaveInstanceState(Bundle b){b.putString("raw",raw.getText().toString());b.putString("name",name.getText().toString());b.putLong("date",selectedDate.toEpochDay());b.putInt("time",selectedTime.toSecondOfDay());b.putBoolean("dateOK",dateOK);b.putBoolean("timeOK",timeOK);super.onSaveInstanceState(b);}
}
