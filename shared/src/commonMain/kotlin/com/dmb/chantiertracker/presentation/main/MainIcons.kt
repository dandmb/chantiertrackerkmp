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

val ShoppingCartIcon: ImageVector = icon(
    "ShoppingCart",
    "M7,18c-1.1,0 -1.99,0.9 -1.99,2S5.9,22 7,22s2,-0.9 2,-2s-0.9,-2 -2,-2zM1,2v2h2l3.6,7.59 -1.35,2.45c-0.16,0.28 " +
        "-0.25,0.61 -0.25,0.96 0,1.1 0.9,2 2,2h12v-2L7.42,15c-0.14,0 -0.25,-0.11 -0.25,-0.25l0.03,-0.12L8.1,13h7.45c0.75," +
        "0 1.41,-0.41 1.75,-1.03l3.58,-6.49c0.08,-0.14 0.12,-0.31 0.12,-0.48 0,-0.55 -0.45,-1 -1,-1L5.21,4l-0.94,-2L1,2zM17," +
        "18c-1.1,0 -1.99,0.9 -1.99,2s0.89,2 1.99,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
)

val ConstructionIcon: ImageVector = icon(
    "Build",
    "M22.7,19l-9.1,-9.1c0.9,-2.3 0.4,-5 -1.5,-6.9c-2,-2 -5,-2.4 -7.4,-1.3L9,6L6,9L1.6,4.7C0.4,7.1 0.9,10.1 2.9,12.1c1.9," +
        "1.9 4.6,2.4 6.9,1.5l9.1,9.1c0.4,0.4 1,0.4 1.4,0l2.3,-2.3c0.5,-0.4 0.5,-1.1 0.1,-1.4z",
)

val DeleteIcon: ImageVector = icon(
    "Delete",
    "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2L18,7L6,7v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z",
)

val CloseIcon: ImageVector = icon(
    "Close",
    "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z",
)

val CalendarIcon: ImageVector = icon(
    "Event",
    "M17,12h-5v5h5v-5zM16,1v2L8,3L8,1L6,1v2L5,3c-1.11,0 -1.99,0.9 -1.99,2L3,19c0,1.1 0.89,2 2,2h14c1.1,0 " +
        "2,-0.9 2,-2L21,5c0,-1.1 -0.9,-2 -2,-2h-1L18,1h-2zM19,19L5,19L5,8h14v11z",
)

val VideocamIcon: ImageVector = icon(
    "Videocam",
    "M17,10.5L17,7c0,-0.55 -0.45,-1 -1,-1L4,6c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h12c0.55,0 " +
        "1,-0.45 1,-1v-3.5l4,4v-11l-4,4z",
)

val PlayIcon: ImageVector = icon(
    "PlayArrow",
    "M8,5v14l11,-7z",
)

val FlagIcon: ImageVector = icon(
    "OutlinedFlag",
    "M14.4,6L14,4L5,4v17h2v-7h5.6l0.4,2h7L18.4,6L14.4,6zM16,12h-3.8l-0.4,-2L7,10L7,6h5.8l0.4,2L16,8v4z",
)
