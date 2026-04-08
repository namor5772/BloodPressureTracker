package au.roman.bloodpressuretracker.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyAverage(
    val date: LocalDate,
    val avgSystolic: Double,
    val avgDiastolic: Double,
    val avgPulse: Double?,
    val recordCount: Int
)

fun computeDailyAverages(records: List<BloodPressureRecord>): List<DailyAverage> {
    val zone = ZoneId.systemDefault()
    return records
        .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        .map { (date, dayRecords) ->
            val pulseRecords = dayRecords.filter { it.pulse > 0 }
            DailyAverage(
                date = date,
                avgSystolic = dayRecords.map { it.systolic }.average(),
                avgDiastolic = dayRecords.map { it.diastolic }.average(),
                avgPulse = if (pulseRecords.isNotEmpty()) pulseRecords.map { it.pulse }.average() else null,
                recordCount = dayRecords.size
            )
        }
        .sortedByDescending { it.date }
}
