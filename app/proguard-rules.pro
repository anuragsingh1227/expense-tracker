# Keep Room entities / enums used via reflection
-keep class com.expensetracker.data.db.entity.** { *; }
-keepclassmembers enum com.expensetracker.domain.model.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.** { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <methods>;
}

# Kotlin metadata for coroutines / serialization-ish reflection
-dontwarn kotlin.**
-dontwarn javax.annotation.**
