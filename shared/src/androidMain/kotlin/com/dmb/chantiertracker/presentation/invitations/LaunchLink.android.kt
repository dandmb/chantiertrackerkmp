package com.dmb.chantiertracker.presentation.invitations

import android.content.Intent

fun launchLinkIsNew(stateRestored: Boolean, intentFlags: Int): Boolean =
    !stateRestored && intentFlags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
