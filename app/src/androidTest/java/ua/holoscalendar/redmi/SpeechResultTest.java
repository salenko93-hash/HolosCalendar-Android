package ua.holoscalendar.redmi;

import android.app.Activity;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Runs on Android's actual regex implementation and Activity lifecycle. No speech service needed. */
@RunWith(AndroidJUnit4.class)
public class SpeechResultTest {
    private Intent result(String text) {
        return new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,
                new ArrayList<>(Arrays.asList(text)));
    }

    @Test public void successfulSpeechResultPopulatesFormWithoutClosing() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.onActivityResult(100, Activity.RESULT_OK, result("Завтра о 14:30 Зустріч у Києві"));
                assertEquals("Зустріч у Києві", ((EditText) activity.findViewById(R.id.name)).getText().toString());
                assertTrue(((TextView) activity.findViewById(R.id.time)).getText().toString().contains("14:30"));
                assertFalse(activity.isFinishing());
            });
        }
    }

    @Test public void cancelledSpeechPreservesExistingForm() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.name)).setText("Моя подія");
                activity.onActivityResult(100, Activity.RESULT_CANCELED, null);
                assertEquals("Моя подія", ((EditText) activity.findViewById(R.id.name)).getText().toString());
                assertFalse(activity.isFinishing());
            });
        }
    }

    @Test public void speechResultWorksAfterRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.recreate();
            scenario.onActivity(activity -> {
                activity.onActivityResult(100, Activity.RESULT_OK, result("Завтра о 9 Нарада"));
                assertEquals("Нарада", ((EditText) activity.findViewById(R.id.name)).getText().toString());
                assertFalse(activity.isFinishing());
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals("Нарада",
                    ((EditText) activity.findViewById(R.id.name)).getText().toString()));
        }
    }
    @Test public void emptySpeechResultPreservesExistingForm() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.name)).setText("Моя подія");
                activity.onActivityResult(100, Activity.RESULT_OK, result(" "));
                assertEquals("Моя подія", ((EditText) activity.findViewById(R.id.name)).getText().toString());
                assertFalse(activity.isFinishing());
            });
        }
    }
}
