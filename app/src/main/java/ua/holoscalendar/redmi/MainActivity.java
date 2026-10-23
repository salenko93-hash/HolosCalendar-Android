package ua.holoscalendar.redmi;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.content.res.Configuration;
import android.provider.CalendarContract;
import android.speech.RecognizerIntent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MainActivity extends Activity {
 private static final int VOICE=100;
 private EditText raw,name;
 private TextView status;
 private Button dateButton,timeButton,durationButton;
 private LocalDate selectedDate;
 private LocalTime selectedTime;
 private boolean dateOK,timeOK;
 private int duration=60;
 private int ink,muted,surface,accent;
 private int px(int dp) { return Math.round(getResources().getDisplayMetrics().density*dp); }
 private TextView label(String text,int size) {
  TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(ink);
  v.setPadding(0,px(12),0,px(8));return v;
 }
 private GradientDrawable background(int color) {
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(px(18));return d;
 }
 private Button button(String text,boolean primary) {
  Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(17);
  b.setTextColor(primary?Color.WHITE:ink);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33777777),background(primary?accent:surface),null));
  b.setMinHeight(px(56));b.setPadding(px(16),px(12),px(16),px(12));
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,px(6),0,px(6));b.setLayoutParams(p);return b;
 }
 private EditText field(String hint,TextView caption) {
  EditText e=new EditText(this);e.setId(View.generateViewId());e.setHint(hint);e.setTextSize(18);e.setTextColor(ink);e.setHintTextColor(muted);
  e.setMinHeight(px(56));e.setPadding(px(12),px(12),px(12),px(12));
  caption.setLabelFor(e.getId());return e;
 }
 @Override public void onCreate(Bundle saved) {
  super.onCreate(saved);
  boolean dark=(getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
  ink=Color.parseColor(dark?"#F1F5F9":"#172B3A");muted=Color.parseColor(dark?"#BBC7D2":"#526474");
  surface=Color.parseColor(dark?"#243544":"#E6EDF2");accent=Color.parseColor(dark?"#256C63":"#17665C");
  selectedDate=LocalDate.now();selectedTime=LocalTime.NOON;
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
  root.setPadding(px(20),px(16),px(20),px(24));root.setBackgroundColor(Color.parseColor(dark?"#101D28":"#F7F9FB"));
  scroll.addView(root);setContentView(scroll);
  // Android 15 enforces edge-to-edge: protect content from bars and the keyboard.
  scroll.setOnApplyWindowInsetsListener((v,insets)->{
   int left,top,right,bottom;
   if(android.os.Build.VERSION.SDK_INT>=30) {
    android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
    left=i.left;top=i.top;right=i.right;bottom=i.bottom;
   } else { left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom(); }
   v.setPadding(left,top,right,bottom);return insets;
  });scroll.requestApplyInsets();
  root.addView(label("ГолосКалендар",30));
  TextView intro=label("Додайте подію своїми словами",17);intro.setTextColor(muted);root.addView(intro);
  Button voice=button("Продиктувати українською",true);voice.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_btn_speak_now,0,0,0);voice.setOnClickListener(v->recognize());root.addView(voice);
  TextView rawLabel=label("Або введіть текст",15);root.addView(rawLabel);
  raw=field("Завтра о 14:30 зустріч із колегами",rawLabel);raw.setMinLines(2);raw.setMaxLines(5);root.addView(raw);
  Button process=button("Розпізнати подію",false);process.setOnClickListener(v->parse());root.addView(process);
  root.addView(label("Перевірте подію",22));
  TextView nameLabel=label("Назва події",15);root.addView(nameLabel);name=field("Додайте назву",nameLabel);root.addView(name);
  dateButton=button("",false);dateButton.setOnClickListener(v->pickDate());root.addView(dateButton);
  timeButton=button("",false);timeButton.setOnClickListener(v->pickTime());root.addView(timeButton);
  durationButton=button("",false);durationButton.setOnClickListener(v->pickDuration());root.addView(durationButton);
  status=label("Розпізнані дані можна змінити перед збереженням.",15);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
  Button save=button("Продовжити в календарі",true);save.setOnClickListener(v->openCalendar());root.addView(save);
  TextView note=label("Остаточне збереження підтвердите в календарі. Голосове введення може потребувати інтернету.",14);note.setTextColor(muted);root.addView(note);
  if(saved!=null) {
   raw.setText(saved.getString("raw",""));name.setText(saved.getString("name",""));
   selectedDate=LocalDate.ofEpochDay(saved.getLong("date",selectedDate.toEpochDay()));
   selectedTime=LocalTime.ofSecondOfDay(saved.getInt("time",selectedTime.toSecondOfDay()));
   dateOK=saved.getBoolean("dateOK");timeOK=saved.getBoolean("timeOK");duration=saved.getInt("duration",60);
   status.setText(saved.getString("status","Перевірте подію"));
  }
  refresh();
 }
 private void refresh() {
  dateButton.setText(dateOK?"Дата: "+selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy",Locale.forLanguageTag("uk-UA"))):"Оберіть дату");
  timeButton.setText(timeOK?"Час: "+selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")):"Оберіть час");
  durationButton.setText(getString(R.string.duration_minutes,duration));
 }
 private void parse() {
  String s=raw.getText().toString().trim();if(s.isEmpty()){raw.setError("Введіть або продиктуйте подію");return;}
  VoiceParser.Result r=VoiceParser.parse(s,LocalDateTime.now());name.setText(r.title);name.setError(null);
  dateOK=r.date!=null;timeOK=r.time!=null;if(dateOK)selectedDate=r.date;if(timeOK)selectedTime=r.time;
  refresh();status.setText(r.warning);if(r.title.isEmpty())name.setError("Додайте назву події");
 }
 private void pickDate() { new DatePickerDialog(this,(v,y,m,d)->{selectedDate=LocalDate.of(y,m+1,d);dateOK=true;refresh();},selectedDate.getYear(),selectedDate.getMonthValue()-1,selectedDate.getDayOfMonth()).show(); }
 private void pickTime() { new TimePickerDialog(this,(v,h,m)->{selectedTime=LocalTime.of(h,m);timeOK=true;refresh();},selectedTime.getHour(),selectedTime.getMinute(),true).show(); }
 private void pickDuration() {
  int[] values={15,30,60,90,120};String[] labels={"15 хвилин","30 хвилин","1 година","1 година 30 хвилин","2 години"};
  int checked=2;for(int i=0;i<values.length;i++)if(values[i]==duration)checked=i;
  new AlertDialog.Builder(this).setTitle("Тривалість події").setSingleChoiceItems(labels,checked,(dialog,index)->{duration=values[index];refresh();dialog.dismiss();}).setNegativeButton("Скасувати",null).show();
 }
 private void recognize() {
  Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
  i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"uk-UA");i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Назвіть подію, дату й час");
  try { startActivityForResult(i,VOICE); } catch(ActivityNotFoundException e) { status.setText("Голосове введення недоступне. Введіть подію текстом."); }
 }
 @Override protected void onActivityResult(int req,int result,Intent data) {
  super.onActivityResult(req,result,data);if(req==VOICE) {
   if(result==RESULT_OK && data!=null) { ArrayList<String> values=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(values!=null && !values.isEmpty()){raw.setText(values.get(0));parse();return;} }
   status.setText("Голосове введення скасовано або не вдалося. Можна ввести текст вручну.");
  }
 }
 private void openCalendar() {
  String title=name.getText().toString().trim();if(title.isEmpty()){name.setError("Додайте назву події");name.requestFocus();return;}
  if(!dateOK||!timeOK){status.setText(!dateOK?"Оберіть дату події":"Оберіть час події");(!dateOK?dateButton:timeButton).requestFocus();return;}
  LocalDateTime dt=LocalDateTime.of(selectedDate,selectedTime);
  if(dt.isBefore(LocalDateTime.now()))new AlertDialog.Builder(this).setTitle("Дата в минулому").setMessage("Відкрити цю подію в календарі?").setNegativeButton("Змінити",null).setPositiveButton("Продовжити",(d,w)->insert(title,dt)).show();else insert(title,dt);
 }
 private void insert(String title,LocalDateTime dt) {
  long begin=dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  Intent i=new Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI);
  i.putExtra(CalendarContract.Events.TITLE,title);i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,begin);i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,begin+duration*60_000L);
  try { startActivity(i);status.setText("Подію передано в календар. Перевірте форму та натисніть «Зберегти»."); }
  catch(ActivityNotFoundException e) { status.setText("Не знайдено календаря для створення подій. Установіть або ввімкніть застосунок календаря."); }
 }
 @Override protected void onSaveInstanceState(Bundle b) {
  b.putString("raw",raw.getText().toString());b.putString("name",name.getText().toString());b.putLong("date",selectedDate.toEpochDay());b.putInt("time",selectedTime.toSecondOfDay());
  b.putBoolean("dateOK",dateOK);b.putBoolean("timeOK",timeOK);b.putInt("duration",duration);b.putString("status",status.getText().toString());super.onSaveInstanceState(b);
 }
}
