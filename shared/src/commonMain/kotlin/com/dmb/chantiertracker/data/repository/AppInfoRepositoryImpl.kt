package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.getPlatform
import com.dmb.chantiertracker.domain.model.AppInfo
import com.dmb.chantiertracker.domain.repository.AppInfoRepository

class AppInfoRepositoryImpl(
    private val appConfig: AppConfig,
) : AppInfoRepository {
    override suspend fun getAppInfo(): AppInfo = AppInfo(
        platformName = getPlatform().name,
        apiBaseUrl = appConfig.baseUrl,
    )
}
