package de.htwdd.htwdresden.service;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

import de.htwdd.htwdresden.MainActivity;
import de.htwdd.htwdresden.R;
import de.htwdd.htwdresden.classes.API.IExamResultsService;
import de.htwdd.htwdresden.classes.API.Retrofit2Qis;
import de.htwdd.htwdresden.classes.Const;
import de.htwdd.htwdresden.classes.ExamsHelper;
import de.htwdd.htwdresden.types.exams.ExamResult;
import io.realm.Realm;
import io.realm.RealmResults;
import okhttp3.Credentials;

/**
 * Service zum automatischen aktualisieren von Prüfungsergebnissen und senden einer Benachrichtigung
 *
 * @author Kay Förster
 */
public final class ExamAutoUpdateService extends ExamSyncService {
    public static final int REQUEST_CODE = 9988;

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        credentials = Credentials.basic("s" + sharedPreferences.getString("sNummer", ""), sharedPreferences.getString("RZLogin", ""));
        examResultsService = Retrofit2Qis.getInstance(context).getRetrofit().create(IExamResultsService.class);

        // Alle IDs der aktuell gespeicherten Noten zwischenspeichern um später zu erkennen welche Noten neu sind.
        final Realm realm = Realm.getDefaultInstance();
        RealmResults<ExamResult> examResults = realm.where(ExamResult.class).findAll();
        final LongSparseArray<Float> existingExams = new LongSparseArray<>(examResults.size());
        for (final ExamResult examResult : examResults) {
            existingExams.append(examResult.id, examResult.getGrade());
        }

        // Alle Noten laden
        getGradeResults();
        // Auf Fertigstellung warten
        waitForFinish();
        // Bei Abbruch hier beenden
        if (isCancel()) {
            return;
        }
        // Noten speichern
        final boolean result = saveGrades();

        if (result) {
            final ArrayList<ExamResult> newResults = new ArrayList<>();
            // Alle Noten durchgehen und neue herauspicken
            examResults = realm.where(ExamResult.class).findAll();
            Float grade;
            for (final ExamResult examResult : examResults) {
                grade = existingExams.get(examResult.id);
                if (grade == null || !grade.equals(examResult.getGrade())) {
                    newResults.add(examResult);
                }
            }

            final int countNewResults = newResults.size();
            if (countNewResults != 0) {
                final Intent startIntent = new Intent(context, MainActivity.class);
                startIntent.setAction(Const.IntentParams.START_ACTION_EXAM_RESULTS);
                final NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
                final PendingIntent pendingIntent = PendingIntent.getActivity(this, 645, startIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                // Vorschau der ersten beiden Noten
                ExamResult examResult;
                for (int i = 0; i < countNewResults && i < 3; i++) {
                    examResult = newResults.get(i);
                    if (examResult.grade != null && examResult.grade > 0.0) {
                        inboxStyle.addLine(getString(R.string.exams_notification_result, examResult.text, examResult.getGrade()));
                    } else {
                        inboxStyle.addLine(examResult.text);
                    }
                }
                // Hinweis das weitere Noten vorhanden sind
                if (countNewResults > 3) {
                    inboxStyle.setSummaryText(getString(R.string.exams_notification_more_results, countNewResults - 3));
                }

                // Allgemeine Notifikation-Einstellungen
                final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Const.NOTIFICATION_CHANNEL_EXAMS);
                builder.setContentTitle(getResources().getQuantityString(R.plurals.exams_notification_title, countNewResults, countNewResults))
                        .setContentText(getText(R.string.exams_notification_contentText))
                        .setSmallIcon(R.drawable.ic_htw_logo_bildmarke_gray_android)
                        .setColor(ContextCompat.getColor(this, R.color.primary_dark))
                        .setStyle(inboxStyle)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true);

                // Notifikation absenden
                final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (notificationManager != null) {
                    notificationManager.notify(645, builder.build());
                }
            }
        }
        realm.close();
    }

    /**
     * Aktiviert die automatische Noten Aktualisierung
     *
     * @param context  aktueller App-Context
     * @param interval Interval in welchem die Noten aktualisiert werden, ideal vom Typ {@link AlarmManager}
     */
    public static void startAutoUpdate(@NonNull final Context context, final long interval) {
        // Construct an intent that will execute the AlarmReceiver
        final Intent intent = new Intent(context.getApplicationContext(), ExamAutoUpdateService.class);
        // Create a PendingIntent to be triggered when the alarm goes off
        final PendingIntent pIntent = PendingIntent.getService(context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), interval, pIntent);
        }
    }

    /**
     * Deaktiviert die automatische Noten Aktualisierung
     *
     * @param context aktueller App-Context
     */
    public static void cancelAutoUpdate(@NonNull final Context context) {
        final Intent intent = new Intent(context.getApplicationContext(), ExamAutoUpdateService.class);
        final PendingIntent pIntent = PendingIntent.getService(context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pIntent);
        }
    }

    /**
     * Broadcast Receiver welcher nach dem Booten den Service automatisch wieder aktiviert
     */
    public static class UpdateExamsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(@NonNull final Context context, @NonNull final Intent intent) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                final long updateInterval = ExamsHelper.getUpdateInterval(sharedPreferences.getString(Const.preferencesKey.PREFERENCES_AUTO_EXAM_UPDATE, "0"));
                if (updateInterval == 0) {
                    ExamAutoUpdateService.cancelAutoUpdate(context);
                } else ExamAutoUpdateService.startAutoUpdate(context, updateInterval);
            }
        }
    }
}
