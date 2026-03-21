package com.wificracker.core.model

/**
 * Réseau sélectionné partagé entre les modules scan, attack et crack.
 * Contient uniquement les champs nécessaires au ciblage — pas le modèle complet du scan.
 */
data class SelectedNetwork(
    val bssid: String,
    val ssid: String,
    val channel: Int = 0,
    val encryption: String = "",
    val signalStrength: Int = 0,
)
