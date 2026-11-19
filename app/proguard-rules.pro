# Project-specific R8 rules.
# Add keep rules only when a library or reflection-based feature requires them.

# MediaPipe Tasks reflects over generated proto fields while constructing its task logger.
# Keep the generated metadata intact in minified debug and release builds.
-keep class com.google.mediapipe.proto.** { *; }
-keepclassmembers class com.google.mediapipe.proto.** { *; }
