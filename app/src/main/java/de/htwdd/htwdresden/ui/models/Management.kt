package de.htwdd.htwdresden.ui.models

import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableField
import de.htwdd.htwdresden.BR
import de.htwdd.htwdresden.R
import de.htwdd.htwdresden.adapter.ManagementBindables
import de.htwdd.htwdresden.databinding.TemplateFreeDayBindableBinding
import de.htwdd.htwdresden.databinding.TemplateManagementOffersBindableBinding
import de.htwdd.htwdresden.databinding.TemplateManagementTimesBindableBinding
import de.htwdd.htwdresden.interfaces.Identifiable
import de.htwdd.htwdresden.utils.extensions.format
import de.htwdd.htwdresden.utils.extensions.toDate
import de.htwdd.htwdresden.utils.holders.StringHolder
import java.util.*

// region - Managementable
interface Managementable: Identifiable<ManagementBindables>
interface ManagementableModels
// endregion

// region - JSON
data class JSemesterPlan (
    val year: Long,
    val type: String,
    val period: JPeriod,
    val freeDays: List<JFreeDay>,
    val lecturePeriod: JPeriod,
    val examsPeriod: JPeriod,
    val reregistration: JPeriod
)

data class JPeriod (
    val beginDay: String,
    val endDay: String
)

data class JFreeDay (
    val name: String,
    val beginDay: String,
    val endDay: String
)

data class JManagement (
    val type: Int,
    val room: String,
    val offeredServices: List<String>,
    val officeHours: List<JOfficeHour>
)

data class JOfficeHour (
    val day: String,
    val times: List<JTime>
)

data class JTime (
    val begin: String,
    val end: String
)
// endregion

// region - Models
class SemesterPlan(
    val year: Long,
    val type: String,
    val period: Period,
    val freeDays: List<FreeDay>,
    val lecturePeriod: Period,
    val examsPeriod: Period,
    val reregistration: Period) {

    companion object {
        fun from(json: JSemesterPlan): SemesterPlan {
            return SemesterPlan(
                json.year,
                json.type,
                Period.from(json.period),
                json.freeDays.map { FreeDay.from(it) },
                Period.from(json.lecturePeriod),
                Period.from(json.examsPeriod),
                Period.from(json.reregistration)
            )
        }
    }
}

class Period(
    val beginDay: Date,
    val endDay: Date) {

    companion object {
        fun from(json: JPeriod): Period {
            return Period(
                json.beginDay.toDate()!!,
                json.endDay.toDate()!!
            )
        }
    }
}

class FreeDay(
    val name: String,
    val beginDay: Date,
    val endDay: Date) {

    companion object {
        fun from(json: JFreeDay): FreeDay {
            return FreeDay(
                json.name,
                json.beginDay.toDate()!!,
                json.endDay.toDate()!!
            )
        }
    }
}

class Management(
    val type: Int,
    val room: String,
    val offeredServices: List<String>,
    val officeHours: List<OfficeHour>
) {
    companion object {
        fun from(json: JManagement): Management {
            return Management(
                json.type,
                json.room,
                json.offeredServices,
                json.officeHours.map { OfficeHour.from(it) }
            )
        }
    }
}

class OfficeHour(
    val day: String,
    val times: List<Time>
) {
    companion object {
        fun from(json: JOfficeHour): OfficeHour {
            return OfficeHour(
                json.day,
                json.times.map { Time.from(it) }
            )
        }
    }
}

class Time(
    val begin: String,
    val end: String
) {
    companion object {
        fun from(json: JTime): Time {
            return Time(
                json.begin,
                json.end
            )
        }
    }
}
// endregion

// region - ManagetableItems
class SemesterPlanItem(private val item: SemesterPlan): Managementable, Comparable<SemesterPlanItem> {

    private val bindingTypes: ManagementBindables by lazy {
        ManagementBindables().apply {
            add(Pair(BR.semsterPlanModel, model))
        }
    }
    private val model = SemesterPlanModel()
    private val sh: StringHolder by lazy { StringHolder.instance }

    init {
        model.apply {
            year.set(item.year.toString())
            type.set(when (item.type) {
                "W" -> sh.getString(R.string.academic_year_winter)
                else -> sh.getString(R.string.academic_year_summer)
            })
            semesterPeriod.set("${item.period.beginDay.format(sh.getString(R.string.period_date_format))} - ${item.period.endDay.format(sh.getString(R.string.period_date_format))}")
            examsPeriod.set("${item.examsPeriod.beginDay.format(sh.getString(R.string.period_date_format))} - ${item.examsPeriod.endDay.format(sh.getString(R.string.period_date_format))}")
            reregistration.set("${item.reregistration.beginDay.format(sh.getString(R.string.period_date_format))} - ${item.reregistration.endDay.format(sh.getString(R.string.period_date_format))}")
        }
    }

    fun addAdditionalInfo(layout: LinearLayout) {
        layout.removeAllViews()
        item.freeDays.forEach { freeDay ->
            val additionalView = LayoutInflater.from(layout.context).inflate(R.layout.template_free_day_bindable, null, false)
            val binding = DataBindingUtil.bind<TemplateFreeDayBindableBinding>(additionalView)?.apply {
                freeDayModel = FreeDayModel().apply {
                    name.set(freeDay.name)
                    if (freeDay.beginDay != freeDay.endDay) {
                        time.set("${freeDay.beginDay.format(sh.getString(R.string.period_date_format))} - ${freeDay.endDay.format(sh.getString(R.string.period_date_format))}")
                    } else {
                        time.set(freeDay.beginDay.format(sh.getString(R.string.period_date_format)))
                    }
                }
            }
            layout.addView(binding?.root)
        }
    }

    override fun itemViewType() = R.layout.list_item_management_semester_plan_bindable

    override fun bindingTypes() = bindingTypes

    override fun compareTo(other: SemesterPlanItem) = item.year.compareTo(other.item.year)

}

class ManagementItem(private val item: Management): Managementable, Comparable<ManagementItem> {

    private val bindingTypes: ManagementBindables by lazy {
        ManagementBindables().apply {
            add(Pair(BR.managementModel, model))
        }
    }
    private val model = ManagementModel()
    private val sh: StringHolder by lazy { StringHolder.instance }

    init {
        model.apply {
            name.set(when (item.type) {
                1 -> sh.getString(R.string.management_office)
                2 -> sh.getString(R.string.management_examination_office)
                else -> sh.getString(R.string.management_stura)
            })
            room.set(item.room)
        }
    }

    fun offers(layout: LinearLayout) {
        layout.removeAllViews()
        item.offeredServices.forEach { offerEntry ->
            val offerView = LayoutInflater.from(layout.context).inflate(R.layout.template_management_offers_bindable, null, false)
            val binding = DataBindingUtil.bind<TemplateManagementOffersBindableBinding>(offerView)?.apply {
                offerModel = OfferModel().apply {
                    offer.set(offerEntry)
                }
            }
            layout.addView(binding?.root)
        }
    }

    fun times(layout: LinearLayout) {
        layout.removeAllViews()
        item.officeHours.forEach { officeHour ->
            val officeHourView = LayoutInflater.from(layout.context).inflate(R.layout.template_management_times_bindable, null, false)
            val binding = DataBindingUtil.bind<TemplateManagementTimesBindableBinding>(officeHourView)?.apply {
                timeModel = TimeModel().apply {
                    day.set(when (officeHour.day) {
                        "Mo" -> sh.getString(R.string.monday)
                        "Di" -> sh.getString(R.string.tuesday)
                        "Mi" -> sh.getString(R.string.wednesday)
                        "Do" -> sh.getString(R.string.thursday)
                        "Fr" -> sh.getString(R.string.friday)
                        else -> officeHour.day
                    })
                    time.set(officeHour.times.joinToString("\n") { "${it.begin} - ${it.end}"})
                }
            }
            layout.addView(binding?.root)
        }
    }

    override fun itemViewType() = R.layout.list_item_management_bindable

    override fun bindingTypes() = bindingTypes

    override fun compareTo(other: ManagementItem) = item.type.compareTo(other.item.type)
}
// endregion

// region ManagementModels
class SemesterPlanModel: ManagementableModels {
    val year            = ObservableField<String>()
    val type            = ObservableField<String>()
    val semesterPeriod  = ObservableField<String>()
    val examsPeriod     = ObservableField<String>()
    val reregistration  = ObservableField<String>()
}

class ManagementModel: ManagementableModels {
    val name = ObservableField<String>()
    val room = ObservableField<String>()
}

class FreeDayModel: ManagementableModels {
    val name = ObservableField<String>()
    val time = ObservableField<String>()
}

class OfferModel: ManagementableModels {
    val offer = ObservableField<String>()
}

class TimeModel: ManagementableModels {
    val day  = ObservableField<String>()
    val time = ObservableField<String>()
}
// endregion
