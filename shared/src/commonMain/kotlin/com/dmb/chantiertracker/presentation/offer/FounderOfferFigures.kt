package com.dmb.chantiertracker.presentation.offer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.FounderOffer
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.founder_offer_pdf
import com.dmb.chantiertracker.resources.founder_offer_photos
import com.dmb.chantiertracker.resources.founder_offer_projects
import com.dmb.chantiertracker.resources.founder_offer_supervisors
import com.dmb.chantiertracker.resources.founder_offer_videos
import com.dmb.chantiertracker.resources.founder_offer_videos_length
import com.dmb.chantiertracker.resources.history_kept_days
import com.dmb.chantiertracker.resources.history_kept_months
import org.jetbrains.compose.resources.stringResource

@Composable
fun FounderOfferFigures(offer: FounderOffer) {
    val lines = buildList {
        offer.projects?.let { add(stringResource(Res.string.founder_offer_projects, it)) }
        offer.photos?.let { add(stringResource(Res.string.founder_offer_photos, it)) }
        offer.videos?.takeIf { it > 0 }?.let { videos ->
            val seconds = offer.videoMaxSeconds?.takeIf { it > 0 }
            add(
                if (seconds != null) {
                    stringResource(Res.string.founder_offer_videos_length, videos, formatVideoDuration(seconds))
                } else {
                    stringResource(Res.string.founder_offer_videos, videos)
                },
            )
        }
        offer.supervisorsPerProject?.let { add(stringResource(Res.string.founder_offer_supervisors, it)) }
        if (offer.pdfExport) add(stringResource(Res.string.founder_offer_pdf))
        offer.historyDays?.let { add(historyKeptLabel(it)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

private const val DAYS_PER_MONTH = 30

// Whole months read better than days, from two months up: 180 is 6 months, 30 stays 30 days.
fun wholeHistoryMonths(days: Int): Int? = (days / DAYS_PER_MONTH).takeIf { days % DAYS_PER_MONTH == 0 && it >= 2 }

@Composable
fun historyKeptLabel(days: Int): String =
    wholeHistoryMonths(days)?.let { stringResource(Res.string.history_kept_months, it) }
        ?: stringResource(Res.string.history_kept_days, days)
