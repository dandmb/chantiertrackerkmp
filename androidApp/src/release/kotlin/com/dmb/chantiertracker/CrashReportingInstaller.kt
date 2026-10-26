package com.dmb.chantiertracker

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.dmb.chantiertracker.core.CrashReporter
import com.dmb.chantiertracker.core.CrashReportingSession
import com.dmb.chantiertracker.data.AuthStateHolder
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

private const val TEST_CRASH_DELAY_MS = 5_000L

// Play Store build only: crashes go to Crashlytics with the app version and the account id,
// never the e-mail or the name. Without the Firebase values of the CI, nothing is set up.
object CrashReportingInstaller {

    fun install(application: Application) {
        if (FirebaseApp.getApps(application).isEmpty()) return
        val crashlytics = FirebaseCrashlytics.getInstance()
        val reporter = object : CrashReporter {
            override fun setUserId(id: String) = crashlytics.setUserId(id)
            override fun setCustomKey(key: String, value: String) = crashlytics.setCustomKey(key, value)
        }
        val authState = GlobalContext.get().get<AuthStateHolder>().state
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            CrashReportingSession(reporter, application.versionLabel()).follow(authState)
        }
        if (application.resources.getBoolean(R.bool.crashlytics_test_crash)) {
            Handler(Looper.getMainLooper()).postDelayed({ throw RuntimeException("Plantage de test Crashlytics") }, TEST_CRASH_DELAY_MS)
        }
    }

    private fun Application.versionLabel(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        return "${info.versionName} ($code)"
    }
}
