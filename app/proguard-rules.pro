# Keep JavaBeans classes required by Rhino JavaScript engine
-keep class java.beans.** { *; }
-keep interface java.beans.** { *; }

# Keep Rhino JavaScript engine classes
-keep class org.mozilla.javascript.** { *; }
-keep interface org.mozilla.javascript.** { *; }

# Keep ScriptEngineFactory implementations
-keep class javax.script.** { *; }
-keep interface javax.script.** { *; }

# Keep NewPipe classes that might be using JavaScript
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }

# Keep all serialization related classes
-keep class * implements java.io.Serializable { *; }
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep all enum classes and their values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep all classes that might be reflected
-keep class * {
    @org.mozilla.javascript.annotations.* <methods>;
    @org.mozilla.javascript.annotations.* <fields>;
}

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep all classes in the extension package
-keep class dev.brahmkshatriya.echo.extension.** { *; }
-keep interface dev.brahmkshatriya.echo.extension.** { *; }

# Keep all public methods in extension classes for reflection
-keepclassmembers class dev.brahmkshatriya.echo.extension.** {
    public <methods>;
}

# Keep all fields in extension classes
-keepclassmembers class dev.brahmkshatriya.echo.extension.** {
    <fields>;
}

# Don't obfuscate extension class names
-keepnames class dev.brahmkshatriya.echo.extension.** { *; }

# Keep all model classes that might be serialized
-keep class dev.brahmkshatriya.echo.common.models.** { *; }
-keep interface dev.brahmkshatriya.echo.common.models.** { *; }

# Keep all settings classes
-keep class dev.brahmkshatriya.echo.common.settings.** { *; }
-keep interface dev.brahmkshatriya.echo.common.settings.** { *; }

# Keep all exception classes
-keep class ** extends java.lang.Exception { *; }
-keep class ** extends java.lang.Error { *; }

# Keep all classes that implement specific interfaces
-keep class * implements dev.brahmkshatriya.echo.common.clients.** { *; }

# Additional rules to prevent common minification issues
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class * {
    @org.jetbrains.annotations.** *;
}

# Keep all Kotlin metadata
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.coroutines.** { *; }

# Keep all inline functions
-keepclassmembers class * {
    @kotlin.internal.InlineOnly *;
}

# Keep all data classes
-keepclassmembers class * implements kotlin.io.Serializable {
    <init>(...);
}

# Keep all companion objects
-keepclassmembers class * {
    kotlin.Companion *;
}

# Prevent inlining of specific methods that might cause issues
-keepclassmembers class * {
    @kotlin.jvm.JvmStatic *;
}

# Keep all lambda classes
-keepclassmembers class ** {
    ** lambda$*(...);
}