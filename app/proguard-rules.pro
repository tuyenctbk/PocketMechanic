# Preserve Firestore & Room data models
-keep @androidx.annotation.Keep class * { *; }
-keep class com.example.model.** { *; }
-keep class com.example.data.local.** { *; }

# Keep source file and line numbers for readable crash reports
-keepattributes SourceFile,LineNumberTable
