package com.dmb.chantiertracker.presentation.billing

import org.koin.mp.KoinPlatformTools

// Platform entry points that capture a chantiertracker:// open — Android
// MainActivity.onNewIntent/onCreate, iOS's onOpenURL — call this directly
// instead of resolving CheckoutDeepLinkDispatcher themselves. Neither
// androidApp nor the Swift side has Koin on its own compile classpath
// (koin-core/koin-android are `implementation` dependencies of `shared`,
// not exposed to consumers) — same reason IosSyncBridgeKt exists as a plain
// top-level function rather than exposing Koin types across the Swift
// boundary. KoinPlatformTools.defaultContext() is this Koin version's
// multiplatform-safe global accessor (the classic top-level `GlobalContext`
// object only exists in koin-core's nativeMain, not commonMain, in 4.2.2).
// getOrNull() rather than get(): defensive only, Koin is always started
// (Application.onCreate/doInitKoin) before either platform entry point can
// fire in practice.
fun dispatchCheckoutDeepLink(url: String) {
    KoinPlatformTools.defaultContext().getOrNull()?.get<CheckoutDeepLinkDispatcher>()?.dispatch(url)
}
