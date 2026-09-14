package com.dmb.chantiertracker.presentation

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponsiveTest {

    @Test
    fun below_600dp_is_compact() {
        assertEquals(WidthSizeClass.COMPACT, widthSizeClassOf(0.dp))
        assertEquals(WidthSizeClass.COMPACT, widthSizeClassOf(412.dp))
        assertEquals(WidthSizeClass.COMPACT, widthSizeClassOf(599.dp))
    }

    @Test
    fun from_600dp_up_to_840dp_is_medium() {
        assertEquals(WidthSizeClass.MEDIUM, widthSizeClassOf(600.dp))
        assertEquals(WidthSizeClass.MEDIUM, widthSizeClassOf(720.dp))
        assertEquals(WidthSizeClass.MEDIUM, widthSizeClassOf(839.dp))
    }

    @Test
    fun from_840dp_up_is_expanded() {
        assertEquals(WidthSizeClass.EXPANDED, widthSizeClassOf(840.dp))
        assertEquals(WidthSizeClass.EXPANDED, widthSizeClassOf(1440.dp))
    }
}
