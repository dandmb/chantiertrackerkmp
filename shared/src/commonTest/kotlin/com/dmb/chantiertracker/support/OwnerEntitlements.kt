package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.domain.model.ProjectOwnerEntitlements

/** Defaults = what the backend combines for a FREE owner who is not a founder. */
fun ownerEntitlements(
    isFounder: Boolean = false,
    canExportPdf: Boolean = false,
    maxHistoryDays: Int? = 30,
    maxVideos: Int = 0,
    maxVideoDurationSeconds: Int = 0,
    maxSupervisorsPerProject: Int? = 1,
) = ProjectOwnerEntitlements(
    isFounder = isFounder,
    canExportPdf = canExportPdf,
    maxHistoryDays = maxHistoryDays,
    maxVideos = maxVideos,
    maxVideoDurationSeconds = maxVideoDurationSeconds,
    maxSupervisorsPerProject = maxSupervisorsPerProject,
)

/** A founder still on the FREE plan: the Semi-Flex floor, as `PlanLimitService` combines it. */
val founderOnFreeEntitlements = ownerEntitlements(
    isFounder = true,
    canExportPdf = true,
    maxHistoryDays = 180,
    maxVideos = 5,
    maxVideoDurationSeconds = 120,
    maxSupervisorsPerProject = 3,
)
