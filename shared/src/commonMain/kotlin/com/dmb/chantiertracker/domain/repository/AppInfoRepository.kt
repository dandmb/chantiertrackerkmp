package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.AppInfo

interface AppInfoRepository {
    suspend fun getAppInfo(): AppInfo
}
