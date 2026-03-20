# ========================================
# WiFi Cracker ProGuard Rules
# ========================================

# --- Kotlin ---
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- Kotlin Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.wificracker.**$$serializer { *; }
-keepclassmembers class com.wificracker.** { *** Companion; }
-keepclasseswithmembers class com.wificracker.** { kotlinx.serialization.KSerializer serializer(...); }

# --- Hilt / Dagger ---
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * { @dagger.hilt.* <methods>; }
-keepclasseswithmembers class * { @javax.inject.* <fields>; }
-keepclasseswithmembers class * { @javax.inject.* <init>(...); }

# Keep all Hilt generated components
-keep class **_HiltModules* { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# Keep ViewModels (Hilt injects them)
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# --- Compose ---
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * { @androidx.compose.runtime.Composable <methods>; }

# --- WiFi Cracker Models (used with serialization/Room) ---
-keep class com.wificracker.core.database.entity.** { *; }
-keep class com.wificracker.core.logging.AuditEntry { *; }
-keep class com.wificracker.core.root.ShellResult { *; }
-keep class com.wificracker.scan.model.** { *; }
-keep class com.wificracker.scan.data.ScanUpdate { *; }
-keep class com.wificracker.attack.model.** { *; }
-keep class com.wificracker.crack.model.** { *; }
-keep class com.wificracker.report.model.** { *; }

# --- Services ---
-keep class com.wificracker.core.service.PentestForegroundService { *; }

# --- Enums ---
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# --- General ---
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
