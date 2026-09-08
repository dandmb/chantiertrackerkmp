package com.dmb.chantiertracker.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Bannière d'information affichée sur l'écran de connexion après un flux d'auth.
 * Transportée dans la route sous forme de `String?` (son `name`) : Navigation
 * Compose type-safe ne sait pas générer de `NavType` pour un enum sur Kotlin/Native.
 */
enum class LoginNotice {
    AccountActivated,
    PasswordReset;

    fun toArg(): String = name

    companion object {
        fun fromArg(arg: String?): LoginNotice? =
            arg?.let { name -> entries.firstOrNull { it.name == name } }
    }
}

@Serializable
data object OnboardingRoute

@Serializable
data object WelcomeRoute

@Serializable
data class LoginRoute(
    val prefilledEmail: String? = null,
    val notice: String? = null,
)

@Serializable
data object RegisterRoute

@Serializable
data class VerifyEmailRoute(val email: String)

@Serializable
data object ForgotPasswordRoute

@Serializable
data class ResetPasswordRoute(val email: String)

@Serializable
data object ProjectsRoute

@Serializable
data object SettingsRoute

@Serializable
data object CreateProjectRoute

@Serializable
data class ProjectDetailRoute(val projectLocalId: String)

@Serializable
data class EditProjectRoute(val projectLocalId: String)

@Serializable
data class InviteMemberRoute(val projectLocalId: String)

@Serializable
data class ProjectHistoryRoute(val projectLocalId: String)

@Serializable
data class ProjectReportsRoute(val projectLocalId: String)

@Serializable
data class CreateStageRoute(val projectLocalId: String)

@Serializable
data class StageDetailRoute(val stageLocalId: String)

@Serializable
data class DailyLogRoute(val dailyLogLocalId: String)

@Serializable
data class EntrySummaryRoute(val entryLocalId: String)

@Serializable
data class ReportEntryRoute(val entryLocalId: String)

@Serializable
data class PurchaseLineFormRoute(
    val entryLocalId: String,
    val projectLocalId: String,
    val lineLocalId: String? = null,
    val currency: String? = null,
)

@Serializable
data class ConsumptionLineFormRoute(
    val entryLocalId: String,
    val projectLocalId: String,
    val lineLocalId: String? = null,
)
