package de.htwdd.htwdresden.types;

import io.realm.RealmObject;

/**
 * Repräsentiert eine Kalenderwoche für {@link Lesson2}
 *
 * @author Kay Förster
 */
public class LessonWeek extends RealmObject{
    public int getWeekOfYear() {
        return weekOfYear;
    }

    public void setWeekOfYear(final int weekOfYear) {
        this.weekOfYear = weekOfYear;
    }

    private int weekOfYear;
}
