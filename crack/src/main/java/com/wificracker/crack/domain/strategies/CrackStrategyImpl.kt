package com.wificracker.crack.domain.strategies

import com.wificracker.crack.model.CrackJob
import com.wificracker.crack.model.CrackProgress
import kotlinx.coroutines.flow.Flow

interface CrackStrategyImpl {
    fun execute(job: CrackJob): Flow<CrackProgress>
    fun stop()
}
