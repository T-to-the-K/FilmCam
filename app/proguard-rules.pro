# Compose + Kotlin metadata is handled by the default Android rules, but the
# GLES renderer and the film-look enum are referenced reflectively by nothing
# today; these keeps are defensive and cost nothing.

# Keep the film look enum intact so future serialization by name works.
-keepclassmembers enum com.tk.filmcam.film.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# CameraX loads implementations by name from metadata.
-keep class androidx.camera.camera2.Camera2Config { *; }
-keep class androidx.camera.camera2.internal.** { *; }

# GLES entry points are invoked from native-ish code paths.
-keepclassmembers class com.tk.filmcam.gl.** {
    native <methods>;
}