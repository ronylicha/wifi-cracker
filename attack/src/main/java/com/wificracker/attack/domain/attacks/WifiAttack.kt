package com.wificracker.attack.domain.attacks

import com.wificracker.attack.model.Attack
import com.wificracker.attack.model.AttackResult
import kotlinx.coroutines.flow.Flow

interface WifiAttack {
    fun execute(attack: Attack): Flow<String>
    fun stop(attack: Attack)
}
