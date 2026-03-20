package com.wificracker.crack.model

enum class CrackStrategy(val label: String, val description: String) {
    DICTIONARY("Dictionary", "Test passwords from a wordlist file"),
    BRUTE_FORCE("Brute Force", "Try all combinations of a character set"),
    RULE_BASED("Rule Based", "Apply mutation rules to a wordlist"),
    COMBINATOR("Combinator", "Combine two wordlists together"),
}
