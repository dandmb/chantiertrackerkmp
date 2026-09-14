package com.dmb.chantiertracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dmb.chantiertracker.presentation.App
import com.dmb.chantiertracker.presentation.billing.dispatchCheckoutDeepLink
import com.dmb.chantiertracker.presentation.invitations.dispatchInvitationDeepLink

// ADR-51 point 4 / ADR-59 — android:launchMode="singleTask" (manifest) means
// a chantiertracker:// return from the browser after Stripe checkout/portal,
// or a real https://chantiertracker.com/invitations/{token} App Link, both
// reach THIS instance via onNewIntent(), never a second stacked one.
// onCreate also checks its own intent for the (rarer) cold-start case: the OS
// killed the process (or the app wasn't running yet) while the link fired,
// so the deep link is what launches/relaunches the app.
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        dispatchDeepLink(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dispatchDeepLink(intent)
    }

    private fun dispatchDeepLink(intent: Intent?) {
        val url = intent?.data?.toString() ?: return
        dispatchCheckoutDeepLink(url)
        dispatchInvitationDeepLink(url)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
