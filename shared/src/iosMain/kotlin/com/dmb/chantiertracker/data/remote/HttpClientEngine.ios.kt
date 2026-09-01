package com.dmb.chantiertracker.data.remote

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun httpClientEngine(): HttpClientEngineFactory<*> = Darwin
