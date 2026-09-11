package com.dmb.chantiertracker.presentation.billing

// Mirrors the web's formatPhotoUsage/formatVideoUsage, generalized to the 3
// count-based metrics that share the exact same shape (projects/photos/
// supervisors — all Int?, null = unlimited). Videos are handled separately
// by the caller: their limit is never null (0 = feature off entirely, a
// state these two functions don't need to know about).
fun usageProgress(used: Int, limit: Int?): Float? =
    limit?.takeIf { it > 0 }?.let { (used.toFloat() / it).coerceIn(0f, 1f) }

fun usageAtLimit(used: Int, limit: Int?): Boolean = limit != null && used >= limit
