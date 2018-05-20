package de.htwdd.htwdresden.classes;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.LinearLayout;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import de.htwdd.htwdresden.R;
import de.htwdd.htwdresden.interfaces.ILesson;
import de.htwdd.htwdresden.types.LessonUser;
import de.htwdd.htwdresden.types.LessonWeek;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import io.realm.Sort;

import static de.htwdd.htwdresden.classes.Const.Timetable.endDS;


/**
 * Stellt Hilfsmethoden für den Stundenplan zur Verfügung
 *
 * @author Kay Förster
 */
public class TimetableHelper extends AbstractTimetableHelper {

    /**
     * Liefert eine Liste der aktuell laufenden Lehrveranstaltungen
     *
     * @param realm aktuelle Datenbankverbindung
     * @return Liste von aktuell laufenden Lehrveranstaltungen. Finden aktuell keine statt ist die Liste leer
     */
    @NonNull
    public static RealmResults<LessonUser> getCurrentLessons(@NonNull final Realm realm) {
        final Calendar calendar = GregorianCalendar.getInstance(Locale.GERMANY);
        final long currentTime = getMinutesSinceMidnight(calendar);
        return realm.where(LessonUser.class)
                // Nach Tag einschränken
                .equalTo(Const.database.Lesson.DAY, calendar.get(Calendar.DAY_OF_WEEK) - 1)
                // Nicht ausgeblendet
                .equalTo(Const.database.Lesson.HIDE_LESSON, false)
                // Nach Kalenderwoche einschränken
                .beginGroup()
                .equalTo(Const.database.Lesson.WEEK, getWeekTyp(calendar.get(Calendar.WEEK_OF_YEAR)))
                .or().equalTo(Const.database.Lesson.WEEK, 0)
                .endGroup()
                // Nach Zeit einschränken
                .greaterThan(Const.database.Lesson.END_TIME, currentTime)
                .lessThan(Const.database.Lesson.BEGIN_TIME, currentTime)
                // Einzelne Wochen ausschließen
                .beginGroup()
                .isEmpty(Const.database.Lesson.WEEKS_ONLY)
                .or().equalTo(Const.database.Lesson.WEEKS_ONLY + ".weekOfYear", calendar.get(Calendar.WEEK_OF_YEAR))
                .endGroup()
                .sort(Const.database.Lesson.END_TIME)
                .findAll();
    }

    /**
     * Liefert eine List der Lehrveranstaltungen des übergebenen Tages und Ds
     *
     * @param realm aktuelle Datenbankverbindung
     * @param calendar Tag für welchen die Lehrveranstaltungen gelistet werden soll
     * @param ds Zeit in welcher die Lehrveranstaltungen stattfinden sollen
     * @param filterCurrentWeek Nur Lehrveranstaltungen der aktuellen Kalenderwoche zurückgeben
     * @param showHiddenLessons versteckte Lehrveranstaltungen mit anzeigen
     * @return Liste von passenden Lehrveranstaltungen
     */
    public static RealmResults<LessonUser> getLessonsByDateAndDs(@NonNull final Realm realm, @NonNull final Calendar calendar, final int ds, final boolean filterCurrentWeek,
                                                                 final boolean showHiddenLessons) {
        final int dsIndex = ds > 0 ? ds - 1 : 0;
        final RealmQuery<LessonUser> realmQuery = realm.where(LessonUser.class)
                .equalTo(Const.database.Lesson.DAY, calendar.get(Calendar.DAY_OF_WEEK) - 1)
                // Nach Kalenderwoche einschränken
                .beginGroup()
                .equalTo(Const.database.Lesson.WEEK, getWeekTyp(calendar.get(Calendar.WEEK_OF_YEAR)))
                .or().equalTo(Const.database.Lesson.WEEK, 0)
                .endGroup()
                // Vor dem Ende dieser DS beginnen und länger gehen als DS startet
                .lessThan(Const.database.Lesson.BEGIN_TIME, Const.Timetable.endDS[dsIndex])
                .greaterThan(Const.database.Lesson.END_TIME, Const.Timetable.beginDS[dsIndex]);

        if (filterCurrentWeek) {
            realmQuery.beginGroup()
                    .isEmpty(Const.database.Lesson.WEEKS_ONLY)
                    .or().equalTo(Const.database.Lesson.WEEKS_ONLY + ".weekOfYear", calendar.get(Calendar.WEEK_OF_YEAR))
                    .endGroup();
        }

        // Versteckte Lehrveranstaltungen ausschließen
        if (!showHiddenLessons) {
            realmQuery.equalTo(Const.database.Lesson.HIDE_LESSON, false);
        }

        return realmQuery.findAll();
    }

    /**
     * Liefert die nächste stattfinde Lehrveranstaltungen
     *
     * @param realm aktuelle Datenbankverbindung
     * @return {@link NextLessonResult} mit Informationen zur nächsten Lehrveranstaltung
     */
    public static NextLessonResult getNextLessons(@NonNull final Realm realm) {
        final NextLessonResult lessonResult = new NextLessonResult(GregorianCalendar.getInstance(Locale.GERMANY));

        // Suche Lehrveranstaltung in der aktuellen Woche
        LessonUser startLesson = getStartNextLessonInWeek(realm, lessonResult.getOnNextDay());
        // Suche Lehrveranstaltung in der nächsten Woche
        if (startLesson == null) {
            final Calendar calendar = lessonResult.getOnNextDay();
            calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
            calendar.add(Calendar.WEEK_OF_YEAR, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            startLesson = getStartNextLessonInWeek(realm, calendar);
        }

        if (startLesson != null) {
            final int nextDs = getCurrentDS((long) startLesson.getBeginTime());
            lessonResult.getOnNextDay().set(Calendar.DAY_OF_WEEK, startLesson.getDay() + 1);

            // Da in der passenden Zeit mehrere Veranstaltungen stattfinden können, diese suchen
            final RealmResults<LessonUser> results = realm.where(LessonUser.class)
                    .equalTo(Const.database.Lesson.HIDE_LESSON, false)
                    .equalTo(Const.database.Lesson.DAY, startLesson.getDay())
                    .greaterThanOrEqualTo(Const.database.Lesson.BEGIN_TIME, startLesson.getBeginTime())
                    .lessThan(Const.database.Lesson.BEGIN_TIME, endDS[nextDs > 0 ? nextDs - 1 : nextDs])
                    .beginGroup()
                    .isEmpty(Const.database.Lesson.WEEKS_ONLY)
                    .or().equalTo(Const.database.Lesson.WEEKS_ONLY + ".weekOfYear", lessonResult.getOnNextDay().get(Calendar.WEEK_OF_YEAR))
                    .endGroup()
                    .sort(new String[]{Const.database.Lesson.DAY, Const.database.Lesson.BEGIN_TIME}, new Sort[]{Sort.ASCENDING, Sort.ASCENDING})
                    .findAll();
            lessonResult.setResults(results);
        }
        return lessonResult;
    }

    /**
     * Liefert Text wie lange die aktuelle Lehrveranstaltung noch geht
     *
     * @param context      aktueller App-Context
     * @param targetLesson aktuelle Lehrveranstaltung
     * @return String wie lange eine Lehrveranstaltung noch geht
     */
    public static String getStringRemainingTime(@NonNull final Context context, @NonNull final LessonUser targetLesson) {
        final Calendar calendar = GregorianCalendar.getInstance(Locale.GERMANY);
        final long difference = getMinutesSinceMidnight(calendar) - targetLesson.getEndTime();

        if (difference < 0) {
            return String.format(context.getString(R.string.overview_lessons_remaining_end), -difference);
        } else {
            return String.format(context.getString(R.string.overview_lessons_remaining_final), difference);
        }
    }

    /**
     * Text wann die nächste Lehrveranstaltung startet
     *
     * @param context          aktueller App-Context
     * @param nextLessonResult Informationen zu den nächsten Lehrveranstaltungen
     * @return String wenn die nächste Lehrveranstaltung startet
     */
    public static String getStringStartNextLesson(@NonNull final Context context, @NonNull final NextLessonResult nextLessonResult) {
        final Calendar calendar = GregorianCalendar.getInstance(Locale.GERMANY);
        final int differenceDay = Math.abs(nextLessonResult.getOnNextDay().get(Calendar.DAY_OF_YEAR) - calendar.get(Calendar.DAY_OF_YEAR));
        final LessonUser firstLesson = (nextLessonResult.getResults() != null && !nextLessonResult.getResults().isEmpty()) ? nextLessonResult.getResults().first() : null;

        if (firstLesson == null) {
            return "";
        }

        switch (differenceDay) {
            case 0:
                return String.format(context.getString(R.string.overview_lessons_remaining_start), (firstLesson.getBeginTime() - getMinutesSinceMidnight(calendar)));
            case 1:
                return context.getString(
                        R.string.overview_lessons_tomorrow_param,
                        context.getString(
                                R.string.timetable_ds_list_simple,
                                DATE_FORMAT.format(Const.Timetable.getDate(firstLesson.getBeginTime())),
                                DATE_FORMAT.format(Const.Timetable.getDate(firstLesson.getEndTime()))
                        )
                );
            default:
                final String[] nameOfDays = DateFormatSymbols.getInstance().getWeekdays();
                return context.getString(
                        R.string.overview_lessons_future,
                        nameOfDays[nextLessonResult.getOnNextDay().get(Calendar.DAY_OF_WEEK)],
                        context.getString(
                                R.string.timetable_ds_list_simple,
                                DATE_FORMAT.format(Const.Timetable.getDate(firstLesson.getBeginTime())),
                                DATE_FORMAT.format(Const.Timetable.getDate(firstLesson.getEndTime()))
                        )
                );
        }
    }

    /**
     * Liefert die Räume verkettet als String zurück
     *
     * @param lesson Lehrveranstaltung aus welcher Räume ausgegeben werden sollen
     * @return verkette Räume
     */
    public static String getStringOfRooms(@NonNull final ILesson lesson) {
        final StringBuilder roomsString = new StringBuilder();
        final RealmList<String> rooms = lesson.getRooms();

        for (final String room : rooms) {
            roomsString.append(room).append("; ");
        }
        return removeLastComma(roomsString.toString());
    }

    /**
     * Liefert die Kalenderwochen verkettet als String zurück
     *
     * @param lesson Lehrveranstaltung aus welcher Kalenderwochen ausgegeben werden sollen
     * @return verkettete Kalenderwochen
     */
    public static String getStringOfKws(@NonNull final LessonUser lesson) {
        final StringBuilder weeks = new StringBuilder();
        final RealmList<LessonWeek> lessonWeeks = lesson.getWeeksOnly();

        for (final LessonWeek lessonWeek : lessonWeeks) {
            weeks.append(lessonWeek.getWeekOfYear()).append("; ");
        }
        return removeLastComma(weeks.toString());
    }

    /**
     * Erstellt eine Liste von Lehrveranstaltungen des Tages
     *
     * @param context      aktueller App-Context
     * @param realm        aktuelle Datenbankverbindung
     * @param linearLayout {@link LinearLayout} in welchem die Liste eingefügt wird
     * @param day          Tag für welchen die Übersicht erstellt werden soll
     * @param current_ds   aktuelle DS welche hervorgehoben werden soll,sonst 0
     */
    public static void createSimpleLessonOverview(@NonNull final Context context, final Realm realm, @NonNull final LinearLayout linearLayout, final Calendar day,
                                                  final int current_ds) {
        final int countUnits = Const.Timetable.beginDS.length;
        final List<RealmResults<LessonUser>> realmResultsList = new ArrayList<>(countUnits);
        for (int i = 0; i < countUnits; i++) {
            realmResultsList.add(getLessonsByDateAndDs(realm, day, i + 1, true, false));
        }
        createSimpleLessonOverview(context, realmResultsList, linearLayout, current_ds);
    }

    /**
     * Überprüft ob die Einstellungen zur Studiengruppe korrekt gesetzt sind
     *
     * @param sharedPreferences SharedPreferences der App
     * @return true wenn Einstellungen korrekt, sonst false
     */
    public static boolean checkPreferencesSettings(@NonNull final SharedPreferences sharedPreferences) {
        return sharedPreferences.contains(Const.preferencesKey.PREFERENCES_TIMETABLE_STUDIENJAHR)
                && sharedPreferences.getString(Const.preferencesKey.PREFERENCES_TIMETABLE_STUDIENGANG, "").length() == 3
                && sharedPreferences.getString(Const.preferencesKey.PREFERENCES_TIMETABLE_STUDIENGRUPPE, "").length() != 0;
    }

    /**
     * Sucht nach der ersten passenden Lehrveranstaltung die als nächstes in der aktuellen Woche stattfindet
     *
     * @param realm    aktuelle Datenbankverbindung
     * @param calendar aktuelle Zeitpunkt ab welchem gesucht werden soll
     * @return erste passende {@link LessonUser} oder null
     */
    @Nullable
    private static LessonUser getStartNextLessonInWeek(@NonNull final Realm realm, @NonNull final Calendar calendar) {
        final long currentTime = getMinutesSinceMidnight(calendar);
        final int currentDs = getCurrentDS(currentTime);

        // Nur Lehrveranstaltungen suchen die nicht ausgeblendet werden sollen
        final RealmQuery<LessonUser> realmQuery = realm.where(LessonUser.class).equalTo(Const.database.Lesson.HIDE_LESSON, false);

        // Lehrveranstaltungen auf aktuelle Woche einschränken
        realmQuery.beginGroup()
                .equalTo(Const.database.Lesson.WEEK, getWeekTyp(calendar.get(Calendar.WEEK_OF_YEAR)))
                .or()
                .equalTo(Const.database.Lesson.WEEK, 0)
                .endGroup()
                .beginGroup()
                .isEmpty(Const.database.Lesson.WEEKS_ONLY)
                .or().equalTo(Const.database.Lesson.WEEKS_ONLY + ".weekOfYear", calendar.get(Calendar.WEEK_OF_YEAR))
                .endGroup();

        // Veranstaltungen in der restlichen Woche
        realmQuery.beginGroup()
                // Veranstaltungen in der restlichen Woche
                .greaterThan(Const.database.Lesson.DAY, calendar.get(Calendar.DAY_OF_WEEK) - 1);

        // Heutige Lehrveranstaltungen innerhalb der Vorlesungszeit mit berücksichtigen
        if (currentDs > 0) {
            realmQuery.or()
                    // Veranstaltungen die noch heute stattfinden, aber...
                    .beginGroup()
                    .equalTo(Const.database.Lesson.DAY, calendar.get(Calendar.DAY_OF_WEEK) - 1)
                    .beginGroup()
                    // ... erst nach dieser Stunde anfangen ...
                    .greaterThan(Const.database.Lesson.BEGIN_TIME, endDS[currentDs - 1])
                    .or()
                    // ... oder erst nach dieser Stunde enden
                    .beginGroup()
                    .greaterThan(Const.database.Lesson.END_TIME, endDS[currentDs - 1])
                    .lessThan(Const.database.Lesson.END_TIME, currentTime)
                    .endGroup()
                    .endGroup()
                    .endGroup();
        }
        // Heutige Lehrveranstaltungen vor der der Vorlesungszeit berücksichtigen
        else if (currentDs == 0) {
            realmQuery.or()
                    .equalTo(Const.database.Lesson.DAY, calendar.get(Calendar.DAY_OF_WEEK) - 1);
        }
        realmQuery.endGroup();

        // Ergebnisse sortieren und erste Stunde bestimmen
        final RealmResults<LessonUser> results = realmQuery.sort(Const.database.Lesson.DAY, Sort.ASCENDING, Const.database.Lesson.BEGIN_TIME, Sort.ASCENDING).findAll();
        return results.size() > 0 ? results.first() : null;
    }
}
