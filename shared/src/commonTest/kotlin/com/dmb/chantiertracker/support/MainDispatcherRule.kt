package com.dmb.chantiertracker.support

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
fun installTestMainDispatcher(): TestDispatcher {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    return dispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
fun resetTestMainDispatcher() {
    Dispatchers.resetMain()
}
