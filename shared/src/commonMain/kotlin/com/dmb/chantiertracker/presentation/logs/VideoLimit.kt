package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.hasUpgrade
import com.dmb.chantiertracker.domain.model.maxVideoDurationSeconds
import com.dmb.chantiertracker.domain.model.maxVideos
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Client-side mirror of the backend video-upload plan rules
 * (`PlanLimitService.maxVideos` / `maxVideoDurationSeconds` /
 * `VideoTooLongException`). Everything hangs off the **project owner's** plan
 * (`ProjectDetail.ownerPlan`, ADR-33), never the uploader's — a supervisor
 * inherits the owner's video limits.
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

object VideoLimit {

    /** Whether the "Add a video" affordance shows at all (SEMI_FLEX / LIBERTE). */
    fun canAdd(ownerPlan: Plan?): Boolean = (ownerPlan ?: Plan.UNKNOWN).maxVideos() > 0

    /**
     * @param durationSeconds the client-probed duration; `null` when probing
     *   failed (unusual container/codec) — treated as OK, the server decides.
     */
    fun check(ownerPlan: Plan?, durationSeconds: Double?): VideoDurationCheck {
        val plan = ownerPlan ?: return VideoDurationCheck.Ok
        val limit = plan.maxVideoDurationSeconds()
        if (limit <= 0 || durationSeconds == null) return VideoDurationCheck.Ok
        // ffprobe rounds up (Math.ceil) — a 119.4s clip counts as 120s. Match that.
        val actual = ceil(durationSeconds).roundToLong()
        return if (actual > limit) {
            VideoDurationCheck.TooLong(formatDuration(actual), formatDuration(limit.toLong()), plan.hasUpgrade())
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
