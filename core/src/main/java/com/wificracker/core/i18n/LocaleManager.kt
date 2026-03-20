package com.wificracker.core.i18n

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocaleManager @Inject constructor(
    private val prefs: SharedPreferences,
) {

    companion object {
        private const val KEY_LOCALE = "app_locale"
        val SUPPORTED_LOCALES = listOf(Locale.ENGLISH, Locale.FRENCH)
    }

    fun getCurrentLocale(): Locale {
        val saved = prefs.getString(KEY_LOCALE, null)
        return if (saved != null) Locale(saved) else Locale.getDefault()
    }

    fun setLocale(locale: Locale) {
        prefs.edit().putString(KEY_LOCALE, locale.language).apply()
    }

    fun applyLocale(context: Context): Context {
        val locale = getCurrentLocale()
        Locale.setDefault(locale)
        val config = context.resources.configuration.apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(config)
    }
}
