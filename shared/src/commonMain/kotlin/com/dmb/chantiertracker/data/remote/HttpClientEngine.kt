package com.dmb.chantiertracker.data.remote

import io.ktor.client.engine.HttpClientEngineFactory

expect fun httpClientEngine(): HttpClientEngineFactory<*>
