package com.dmb.chantiertracker.presentation.invitations

import org.koin.mp.KoinPlatformTools

fun dispatchInvitationDeepLink(url: String) {
    val token = parseInvitationToken(url) ?: return
    val koin = KoinPlatformTools.defaultContext().getOrNull() ?: return
    koin.get<InvitationDeepLinkDispatcher>().dispatch(token)
}
