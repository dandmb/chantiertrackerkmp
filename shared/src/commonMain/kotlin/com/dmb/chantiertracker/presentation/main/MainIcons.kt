package com.dmb.chantiertracker.presentation.main

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
    }.build()

val ProjectsIcon: ImageVector = icon(
    "Apartment",
    "M17,11L17,3L7,3L7,7L3,7L3,21L11,21L11,17L13,17L13,21L21,21L21,11L17,11ZM7,19L5,19L5,17L7,17L7,19ZM7,15L5,15L5,13L7,13L7,15ZM7,11L5,11L5,9L7,9L7,11ZM11,15L9,15L9,13L11,13L11,15ZM11,11L9,11L9,9L11,9L11,11ZM11,7L9,7L9,5L11,5L11,7ZM15,15L13,15L13,13L15,13L15,15ZM15,11L13,11L13,9L15,9L15,11ZM15,7L13,7L13,5L15,5L15,7ZM19,19L17,19L17,17L19,17L19,19ZM19,15L17,15L17,13L19,13L19,15Z",
)

val SettingsIcon: ImageVector = icon(
    "Settings",
    "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 " +
        "0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4," +
        "2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 " +
        "7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84," +
        "11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 " +
        "0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24," +
        "0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92," +
        "-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6," +
        "1.62 3.6,3.6S13.98,15.6 12,15.6z",
)

val AccountIcon: ImageVector = icon(
    "Person",
    "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4S8,5.79 8,8s1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z",
)

val ChevronRightIcon: ImageVector = icon(
    "ChevronRight",
    "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z",
)

val AddIcon: ImageVector = icon(
    "Add",
    "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
)

val EditIcon: ImageVector = icon(
    "Edit",
    "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
)

val LocationIcon: ImageVector = icon(
    "LocationOn",
    "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zM12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z",
)

val SortIcon: ImageVector = icon(
    "Sort",
    "M3,18h6v-2L3,16v2zM3,6v2h18L21,6L3,6zM3,13h12v-2L3,11v2z",
)

val CheckIcon: ImageVector = icon(
    "Check",
    "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z",
)
