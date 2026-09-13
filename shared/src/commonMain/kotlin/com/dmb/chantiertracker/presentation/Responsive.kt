package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Mirrors Material3's own WindowWidthSizeClass breakpoints (600dp/840dp) —
// not invented here, reused so a later NavigationRail-vs-bottom-bar or
// 1-column-vs-2-column decision lines up with the same, already-standard
// thresholds rather than a bespoke set of numbers.
enum class WidthSizeClass { COMPACT, MEDIUM, EXPANDED }

fun widthSizeClassOf(width: Dp): WidthSizeClass = when {
    width < 600.dp -> WidthSizeClass.COMPACT
    width < 840.dp -> WidthSizeClass.MEDIUM
    else -> WidthSizeClass.EXPANDED
}

// The one place a screen's root content gets a width cap + centering — every
// non-auth screen was a plain fillMaxSize() Column with no such cap (audited
// against real renders at 892x412 and 1440x900, not guessed), so a label/value
// row or a form field could stretch to the width of a resized Desktop window
// or a tablet, with the value or the field sitting hundreds of dp away from
// its label. AuthScreenLayout meant to solve exactly this for auth screens
// (`widthIn(max = 440.dp)`, centered) — its call sites had the two modifiers
// in the wrong order and the cap silently never applied (see below); fixed
// there and generalized here so every other screen can adopt the *correct*
// idiom with a one-line wrap around its existing Column, no restructuring.
//
// `content` receives the resolved WidthSizeClass (measured on the incoming
// constraint, before this composable's own cap is applied) so a screen can
// additionally decide to lay out in two columns, switch navigation style,
// etc. — not just avoid stretching. A screen that doesn't need that decision
// simply ignores the parameter.
//
// `widthIn(max = X)` BEFORE `fillMaxWidth()`, never the reverse — found the
// hard way while building this: `fillMaxWidth()` first passes a tight
// (min == max == available width) constraint downward, and a `widthIn(max =
// X)` chained after it can't shrink a constraint whose min already exceeds
// X, so the cap silently never applies. `widthIn` first (loosening the
// available max down to X, min still 0) lets `fillMaxWidth()` then fill
// exactly that already-capped band. Proven by rendering a plain colored Box
// both ways at 1440dp before trusting either — the wrong order really does
// stretch edge to edge, in a Box and in a Column alike (see AuthScreenLayout
// below, which had this exact reversed order and has now been corrected the
// same way).
@Composable
fun ResponsiveContent(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 640.dp,
    content: @Composable (widthClass: WidthSizeClass) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val widthClass = widthSizeClassOf(maxWidth)
        Box(Modifier.widthIn(max = maxContentWidth).fillMaxWidth()) {
            content(widthClass)
        }
    }
}
