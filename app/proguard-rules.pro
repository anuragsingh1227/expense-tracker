# Keep Room entities
-keep class com.expensetracker.data.db.entity.** { *; }
# Keep Hilt-generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.** { *; }
