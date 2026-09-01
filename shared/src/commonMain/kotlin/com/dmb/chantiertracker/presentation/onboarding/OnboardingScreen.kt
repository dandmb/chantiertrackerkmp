package com.dmb.chantiertracker.presentation.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.onboarding_go_to_page
import com.dmb.chantiertracker.resources.onboarding_next
import com.dmb.chantiertracker.resources.onboarding_p1_body
import com.dmb.chantiertracker.resources.onboarding_p1_title
import com.dmb.chantiertracker.resources.onboarding_p2_body
import com.dmb.chantiertracker.resources.onboarding_p2_title
import com.dmb.chantiertracker.resources.onboarding_p3_body
import com.dmb.chantiertracker.resources.onboarding_p3_title
import com.dmb.chantiertracker.resources.onboarding_skip
import com.dmb.chantiertracker.resources.onboarding_start
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

const val ONBOARDING_PAGES = 3

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGES })
    val transition = rememberInfiniteTransition(label = "onboarding")
    val loop by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "loop",
    )
    OnboardingScreenContent(pagerState = pagerState, loop = loop, onFinish = onFinish)
}

@Composable
fun OnboardingScreenContent(pagerState: PagerState, loop: Float, onFinish: () -> Unit) {
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == ONBOARDING_PAGES - 1

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                if (!lastPage) {
                    TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text(stringResource(Res.string.onboarding_skip), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                var appeared by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { appeared = true }
                val entrance by animateFloatAsState(
                    targetValue = if (appeared && pagerState.currentPage == page) 1f else 0f,
                    animationSpec = tween(600),
                    label = "entrance",
                )
                OnboardingPage(page = page, entrance = entrance, loop = loop)
            }

            PageIndicator(
                count = ONBOARDING_PAGES,
                current = pagerState.currentPage,
                onSelect = { target -> scope.launch { pagerState.animateScrollToPage(target) } },
                modifier = Modifier.padding(vertical = 20.dp).align(Alignment.CenterHorizontally),
            )

            AuthPrimaryButton(
                text = stringResource(if (lastPage) Res.string.onboarding_start else Res.string.onboarding_next),
                onClick = {
                    if (lastPage) onFinish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun OnboardingPage(page: Int, entrance: Float, loop: Float) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val illustrationModifier = Modifier.fillMaxWidth(0.72f).widthIn(max = 300.dp)
            when (page) {
                0 -> RemoteSiteIllustration(illustrationModifier, entrance, loop)
                1 -> PhotoProofIllustration(illustrationModifier, entrance, loop)
                else -> BudgetIllustration(illustrationModifier, entrance, loop)
            }
        }
        Text(
            text = stringResource(titleFor(page)),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(bodyFor(page)),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 22.dp else 8.dp, label = "dotWidth")
            val color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            }
            val label = stringResource(Res.string.onboarding_go_to_page, index + 1)
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(onClickLabel = label) { onSelect(index) },
            )
        }
    }
}

private fun titleFor(page: Int): StringResource = when (page) {
    0 -> Res.string.onboarding_p1_title
    1 -> Res.string.onboarding_p2_title
    else -> Res.string.onboarding_p3_title
}

private fun bodyFor(page: Int): StringResource = when (page) {
    0 -> Res.string.onboarding_p1_body
    1 -> Res.string.onboarding_p2_body
    else -> Res.string.onboarding_p3_body
}
