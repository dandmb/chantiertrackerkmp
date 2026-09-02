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
    const val PROJECTS = "projects"

    fun project(id: Long) = "projects/$id"
    fun projectMembers(id: Long) = "projects/$id/members"
}
