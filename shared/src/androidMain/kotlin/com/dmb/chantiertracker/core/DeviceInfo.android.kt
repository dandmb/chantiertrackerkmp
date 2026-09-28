package com.dmb.chantiertracker.core

import java.util.TimeZone

actual fun deviceTimeZoneId(): String = TimeZone.getDefault().id
