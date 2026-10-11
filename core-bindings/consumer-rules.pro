# JNA and UniFFI discover callbacks, structure fields and native methods reflectively.
# Limit the broad rule to generated code; app/platform code remains shrinkable.
-keep class dev.hootkit.core.** { *; }
-keep class com.sun.jna.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

# JNA also ships Desktop-only Native$AWT entry points. Android has no AWT;
# UniFFI uses JNA's native/callback APIs, never these desktop window helpers.
# Exact missing types from the actual final-App R8 run; keep all callback rules above.
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
