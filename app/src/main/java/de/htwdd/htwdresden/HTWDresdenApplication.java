package de.htwdd.htwdresden;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import io.realm.Realm;

@ReportsCrashes(
        mode = ReportingInteractionMode.DIALOG,
        mailTo = "app@htw-dresden.de",
        resDialogTitle = R.string.app_name,
        resDialogIcon = R.drawable.ic_warning_24dp,
        resDialogText = R.string.crash_dialog_text,
        resDialogCommentPrompt = R.string.crash_dialog_comment_prompt, // optional. When defined, adds a user text field input with this text resource as a label
        resDialogOkToast = R.string.crash_dialog_ok_toast, // optional. displays a Toast message when the user accepts to send a report.
        customReportContent = {
                ReportField.APP_VERSION_CODE,
                ReportField.APP_VERSION_NAME,
                ReportField.ANDROID_VERSION,
                ReportField.STACK_TRACE,
                ReportField.LOGCAT,
                ReportField.USER_COMMENT
        },
        sharedPreferencesName = "de.htwdd.htwdresden_preferences"
)
public class HTWDresdenApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        final Context context = getApplicationContext();

        // ARCA starten
        ACRA.init(this);

        // Realm initialisieren
        Realm.init(this);

        // Updates laden
        final Thread thread = new Thread(new CheckUpdates(context));
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();

        /**
         * Funktionen welche nur beim ersten Start der App ausgeführt werden:
         * <ul>
         *     <li>Wizard starten</li>
         *     <li>Einstellungen initial setzen</li>
         * </ul>
         * Wichtig ist, dass die erst am Ende ausführt wird um vorher Abhängigkeiten (Datenbank, Studiengänge) auflösen zu können
         */
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences.getBoolean("FIRST_RUN", true)) {
            final Intent intent = new Intent(context, WizardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            final SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("FIRST_RUN", false);
            editor.putBoolean("acra.enable", true);
            editor.apply();
        }
    }
}
