package com.dmb.chantiertracker.domain.usecase

import com.dmb.chantiertracker.domain.model.WelcomeMessage
import com.dmb.chantiertracker.domain.repository.AppInfoRepository

class GetWelcomeMessageUseCase(
    private val appInfoRepository: AppInfoRepository,
) {
    suspend operator fun invoke(): WelcomeMessage {
        val info = appInfoRepository.getAppInfo()
        return WelcomeMessage(
            title = "Hello ChantierTracker",
            platformName = info.platformName,
            apiBaseUrl = info.apiBaseUrl,
        )
    }
}
