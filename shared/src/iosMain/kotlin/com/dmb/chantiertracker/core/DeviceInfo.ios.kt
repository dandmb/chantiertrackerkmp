package com.dmb.chantiertracker.core

import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

actual fun deviceTimeZoneId(): String = NSTimeZone.localTimeZone.name
