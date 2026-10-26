package com.dmb.chantiertracker.domain.model

// UNKNOWN (never fetched, offline, server error) is treated exactly like
// CLOSED by every screen: paid tiers are only ever shown on a confirmed OPEN.
enum class BillingAvailability { OPEN, CLOSED, UNKNOWN }
