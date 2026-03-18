package com.hrcoach.domain.simulation

import kotlinx.coroutines.flow.StateFlow

interface HrDataSource {
    val heartRate: StateFlow<Int>
    val isConnected: StateFlow<Boolean>
}
