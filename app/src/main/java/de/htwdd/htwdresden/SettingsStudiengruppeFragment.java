package de.htwdd.htwdresden;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import de.htwdd.htwdresden.adapter.SpinnerAdapter;
import de.htwdd.htwdresden.classes.Const;
import de.htwdd.htwdresden.service.TimetableStudentSyncService;
import de.htwdd.htwdresden.types.studyGroups.StudyCourse;
import de.htwdd.htwdresden.types.studyGroups.StudyGroup;
import de.htwdd.htwdresden.types.studyGroups.StudyYear;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;

/**
 * Gruppierter Dialog für die Einstellung der Studiengruppe
 *
 * @author Kay Förster
 */
public class SettingsStudiengruppeFragment extends PreferenceDialogFragmentCompat {
    private Realm realm;
    private int studyYear;
    private String studyCourse;
    private String studyGroup;

    public static SettingsStudiengruppeFragment newInstance(@Nullable final String key) {
        final SettingsStudiengruppeFragment fragment = new SettingsStudiengruppeFragment();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);

        return fragment;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        realm = Realm.getDefaultInstance();
        final SharedPreferences sharedPreferences = getPreference().getSharedPreferences();
        final RealmResults<StudyYear> realmResultsYear = realm.where(StudyYear.class).findAll();

        // Finde aktuell ausgewählte Position
        int yearPosition = 0;
        if (sharedPreferences.contains(Const.preferencesKey.PREFERENCES_TIMETABLE_STUDIENJAHR)) {
            yearPosition = 1 + realmResultsYear.indexOf(realm.where(StudyYear.class)
                    .equalTo(Const.database.StudyGroups.STUDY_YEAR, sharedPreferences.getInt(Const.preferencesKey.PREFERENCES_TIMETABLE_STUDIENJAHR, 18))
                    .findFirst()
            );
        }

        final String pleaseSelectString = view.getContext().getString(R.string.general_select_option);
        final Spinner studyYearSpinner = view.findViewById(R.id.studyYearSpinner);
        final Spinner studyCourseSpinner = view.findViewById(R.id.studyCourseSpinner);
        final Spinner studyGroupSpinner = view.findViewById(R.id.studyGroupSpinner);
        studyGroupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> adapterView, final View view, final int i, final long l) {
                if (i != 0) {
                    // Wenn "Bitte auswählen" ausgewählt ist, sind keine Daten für nachfolgende Spinner verfügbar.
                    studyGroup = ((StudyGroup) adapterView.getAdapter().getItem(i)).getStudyGroup();
                }
            }

            @Override
            public void onNothingSelected(final AdapterView<?> adapterView) {
            }
        });
        studyCourseSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> adapterView, final View view, final int i, final long l) {
                if (i == 0) {
                    // Wenn "Bitte auswählen" ausgewählt ist, sind keine Daten für nachfolgende Spinner verfügbar.
                    studyGroupSpinner.setAdapter(new SpinnerAdapter<>(null, pleaseSelectString));
                    studyGroup = null;
                    return;
                }
                // Auswahl merken
                final StudyCourse studyCourseObject = (StudyCourse) adapterView.getAdapter().getItem(i);
                studyCourse = studyCourseObject.getStudyCourse();

                // Auswahl selektieren
                int position = 0;
                final RealmList<StudyGroup> studyGroups = studyCourseObject.getStudyGroups();
                if (sharedPreferences.contains(Const.preferencesKey.PREFERENCES_TIMETABLE_STUDIENGRUPPE)) {
                    position = 1 + studyGroups.indexOf(studyGroups
                            .where()
                            .equalTo(Const.database.StudyGroups.STUDY_GROUP, sharedPreferences.getString(Const.preferencesKey.PREFERENCES_TIMETABLE_STUDIENGRUPPE, ""))
                            .findFirst()
                    );
                }
                studyGroupSpinner.setAdapter(new SpinnerAdapter<>(((StudyCourse) adapterView.getAdapter().getItem(i)).getStudyGroups(), pleaseSelectString));
                studyGroupSpinner.setSelection(position);
            }

            @Override
            public void onNothingSelected(final AdapterView<?> adapterView) {
            }
        });
        studyYearSpinner.setAdapter(new SpinnerAdapter<>(realmResultsYear, pleaseSelectString));
        studyYearSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> adapterView, final View view, final int i, final long l) {
                if (i == 0) {
                    // Wenn "Bitte auswählen" ausgewählt ist, sind keine Daten für nachfolgende Spinner verfügbar.
                    studyCourseSpinner.setAdapter(new SpinnerAdapter<>(null, pleaseSelectString));
                    studyYear = 0;
                    return;
                }
                // Auswahl merken
                final StudyYear studyYearObject = (StudyYear) adapterView.getAdapter().getItem(i);
                studyYear = studyYearObject.getStudyYear();

                // Nachfolgenden Spinner fühlen und Auswahl selektieren
                int position = 0;
                final RealmList<StudyCourse> studyCourses = studyYearObject.getStudyCourses();
                // Finde ausgewählte Position
                if (sharedPreferences.contains(Const.preferencesKey.PREFERENCES_TIMETABLE_STUDIENGANG)) {
                    position = 1 + studyCourses.indexOf(studyCourses
                            .where()
                            .equalTo(Const.database.StudyGroups.STUDY_COURSE, sharedPreferences.getString(Const.preferencesKey.PREFERENCES_TIMETABLE_STUDIENGANG, ""))
                            .findFirst()
                    );
                }
                studyCourseSpinner.setAdapter(new SpinnerAdapter<>(studyCourses, pleaseSelectString));
                studyCourseSpinner.setSelection(position);
            }

            @Override
            public void onNothingSelected(final AdapterView<?> adapterView) {
            }
        });
        studyYearSpinner.setSelection(yearPosition);
    }

    @Override
    public void onDialogClosed(final boolean positiveResult) {
        if (positiveResult) {
            final Context context = requireContext();
            final SharedPreferences.Editor editor = getPreference().getSharedPreferences().edit();
            editor.putInt(Const.preferencesKey.PREFERENCES_TIMETABLE_STUDIENJAHR, studyYear);
            editor.putString(Const.preferencesKey.PREFERENCES_TIMETABLE_STUDIENGANG, studyCourse);
            editor.putString(Const.preferencesKey.PREFERENCES_TIMETABLE_STUDIENGRUPPE, studyGroup);
            editor.apply();
            // Stundenplan aktualisieren
            context.startService(new Intent(context, TimetableStudentSyncService.class));
        }
        realm.close();
    }
}
