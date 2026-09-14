package com.dmb.chantiertracker.presentation.admin

import com.dmb.chantiertracker.domain.model.Granularity

// Mirrors the web's formatBucket (TimeSeriesChart.tsx) exactly: the chart
// axis needs a label whose precision matches the active granularity — a
// full dd-MM-yyyy on every point (formatIsoDate, used by the old list
// rendering) reads as noise once there are a dozen points on one axis,
// especially under YEAR where every point would repeat the same day/month.
// Parsed as a string, same convention as formatIsoDate — bucket is always
// the backend's raw yyyy-MM-dd, never run through a datetime library here.
internal fun formatStatsBucket(bucket: String, granularity: Granularity): String {
    val parts = bucket.trim().split("-")
    if (parts.size != 3 || parts.any { it.isEmpty() }) return bucket
    val (year, month, day) = parts
    return when (granularity) {
        Granularity.DAY -> "$day/$month"
        Granularity.MONTH -> "$month/$year"
        Granularity.YEAR -> year
    }
}
