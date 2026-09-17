package com.example.prototipo.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class HealthConnectManager(private val context: Context) {

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient? by lazy {
        if (isAvailable) HealthConnectClient.getOrCreate(context) else null
    }

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    suspend fun hasAllPermissions(): Boolean {
        val client = client ?: return false
        return client.permissionController.getGrantedPermissions().containsAll(requiredPermissions)
    }

    suspend fun readLatestHeartRate(): MetricState {
        val client = client ?: return MetricState(unit = "bpm")
        val now = Instant.now()
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofDays(3)), now),
                ascendingOrder = false,
                pageSize = 50,
            )
        ).records

        val lastSample = records.flatMap { it.samples }.maxByOrNull { it.time }
            ?: return MetricState(unit = "bpm")

        return MetricState(
            value = lastSample.beatsPerMinute.toString(),
            unit = "bpm",
            recordedAt = lastSample.time,
        )
    }

    suspend fun readTodaySteps(): MetricState {
        val client = client ?: return MetricState(unit = "pasos")
        val now = Instant.now()
        val startOfDay = startOfLocalDay(now)
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
            )
        )
        val total = response[StepsRecord.COUNT_TOTAL] ?: return MetricState(unit = "pasos")
        return MetricState(value = total.toString(), unit = "pasos", recordedAt = now)
    }

    suspend fun readTodayDistance(): MetricState {
        val client = client ?: return MetricState(unit = "km")
        val now = Instant.now()
        val startOfDay = startOfLocalDay(now)
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
            )
        )
        val totalDistance = response[DistanceRecord.DISTANCE_TOTAL]
            ?: return MetricState(unit = "km")
        return MetricState(
            value = "%.2f".format(totalDistance.inKilometers),
            unit = "km",
            recordedAt = now,
        )
    }

    suspend fun readLastSleepSession(): MetricState {
        val client = client ?: return MetricState(unit = "h")
        val now = Instant.now()
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofDays(14)), now),
                ascendingOrder = false,
                pageSize = 20,
            )
        ).records

        val lastSession = records.maxByOrNull { it.endTime } ?: return MetricState(unit = "h")
        val hours = Duration.between(lastSession.startTime, lastSession.endTime).toMinutes() / 60.0

        return MetricState(
            value = "%.1f".format(hours),
            unit = "h",
            recordedAt = lastSession.endTime,
        )
    }

    private fun startOfLocalDay(instant: Instant): Instant =
        instant.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
}
