package com.wificracker.crack.model

data class Wordlist(
    val name: String,
    val path: String,
    val size: Long = 0,
    val wordCount: Long = 0,
    val isBuiltIn: Boolean = false,
)
