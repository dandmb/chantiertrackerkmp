package com.dmb.chantiertracker.data.remote

object ApiRoutes {
    const val AUTH_REGISTER = "auth/register"
    const val AUTH_VERIFY_EMAIL = "auth/verify-email"
    const val AUTH_RESEND_CODE = "auth/resend-code"
    const val AUTH_LOGIN = "auth/login"
    const val AUTH_REFRESH_TOKEN = "auth/refresh-token"
    const val AUTH_LOGOUT = "auth/logout"
    const val AUTH_FORGOT_PASSWORD = "auth/forgot-password"
    const val AUTH_RESET_PASSWORD = "auth/reset-password"
    const val USERS_ME = "users/me"
    const val USERS_ME_PLAN_USAGE = "users/me/plan-usage"
    const val USERS_ME_INVITATIONS = "users/me/invitations"
    const val PROJECTS = "projects"

    fun project(id: Long) = "projects/$id"
    fun projectMembers(id: Long) = "projects/$id/members"
    fun projectHistory(id: Long) = "projects/$id/history"
    fun projectInvitations(id: Long) = "projects/$id/invitations"
    fun invitation(id: Long) = "invitations/$id"
    fun acceptInvitation(token: String) = "invitations/$token/accept"
    fun declineInvitation(token: String) = "invitations/$token/decline"
    fun projectStages(id: Long) = "projects/$id/stages"
    fun stage(id: Long) = "stages/$id"

    fun projectMaterials(id: Long) = "projects/$id/materials"
    fun material(id: Long) = "materials/$id"

    fun stageLogs(stageId: Long) = "stages/$stageId/logs"
    fun stageLogPurchases(stageId: Long, date: String) = "stages/$stageId/logs/$date/purchases"
    fun stageLogWorks(stageId: Long, date: String) = "stages/$stageId/logs/$date/works"
    fun log(id: Long) = "logs/$id"
    fun entry(id: Long) = "entries/$id"

    fun entryPurchaseLines(entryId: Long) = "entries/$entryId/purchase-lines"
    fun purchaseLine(id: Long) = "purchase-lines/$id"
    fun entryConsumptionLines(entryId: Long) = "entries/$entryId/consumption-lines"
    fun consumptionLine(id: Long) = "consumption-lines/$id"

    fun entryAttachments(entryId: Long) = "entries/$entryId/attachments"
    fun attachment(id: Long) = "attachments/$id"

    fun entryReports(entryId: Long) = "entries/$entryId/reports"
    fun projectReports(id: Long) = "projects/$id/reports"
    fun reportProcess(id: Long) = "reports/$id/process"

    fun projectExportPdf(id: Long) = "projects/$id/export/pdf"
}
