package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.hasUpgrade
import com.dmb.chantiertracker.domain.model.ownerMaxVideoDurationSeconds
import com.dmb.chantiertracker.domain.model.ownerMaxVideos
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Client-side mirror of the backend video-upload rules (`PlanLimitService.maxVideos` /
 * `maxVideoDurationSeconds` / `VideoTooLongException`). Everything hangs off the
 * **project owner's** account, never the uploader's — a supervisor inherits the
 * owner's video limits.
 *
 * Used as a courtesy pre-check so a too-long video is rejected **before** a
 * slow upload the server would refuse anyway (same stance as the web, and as
 * the supervisor-limit check — ADR-33). The server re-asserts on every upload.
 */
sealed interface VideoDurationCheck {
    data object Ok : VideoDurationCheck

    /** `actual`/`limit` are already `mm:ss`-formatted; `hasUpgrade` drops the "upgrade" wording on LIBERTE. */
    data class TooLong(val actual: String, val limit: String, val hasUpgrade: Boolean) : VideoDurationCheck
}

/**
 * The owner's video allowance for one project, as the backend combined it
 * (plan + founder status, ADR-66) — or, against a backend that doesn't send
 * it, as the owner's plan alone implies. `maxDurationSeconds = null`: not known
 * yet. `hasUpgrade` stays a property of the plan: founder status is a floor,
 * not a tier to sell.
 */
data class OwnerVideoLimits(val maxVideos: Int, val maxDurationSeconds: Int?, val hasUpgrade: Boolean)

fun ProjectDetail.ownerVideoLimits() = OwnerVideoLimits(
    maxVideos = ownerMaxVideos(),
    maxDurationSeconds = ownerMaxVideoDurationSeconds(),
    hasUpgrade = ownerPlan?.hasUpgrade() ?: true,
)

object VideoLimit {

    /** Whether the "Add a video" affordance shows at all. */
    fun canAdd(limits: OwnerVideoLimits?): Boolean = (limits?.maxVideos ?: 0) > 0

    /**
     * @param durationSeconds the client-probed duration; `null` when probing
     *   failed (unusual container/codec) — treated as OK, the server decides.
     */
    fun check(limits: OwnerVideoLimits?, durationSeconds: Double?): VideoDurationCheck {
        val limit = limits?.maxDurationSeconds ?: return VideoDurationCheck.Ok
        if (limit <= 0 || durationSeconds == null) return VideoDurationCheck.Ok
        // ffprobe rounds up (Math.ceil) — a 119.4s clip counts as 120s. Match that.
        val actual = ceil(durationSeconds).roundToLong()
        return if (actual > limit) {
            VideoDurationCheck.TooLong(formatDuration(actual), formatDuration(limit.toLong()), limits.hasUpgrade)
        } else {
            VideoDurationCheck.Ok
        }
    }

    /** Mirrors `VideoTooLongException.format` / web `formatVideoDuration` — `1 min 05 s` or `45 s`. */
    fun formatDuration(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            "$minutes min ${seconds.toString().padStart(2, '0')} s"
        } else {
            "$seconds s"
        }
    }
}
