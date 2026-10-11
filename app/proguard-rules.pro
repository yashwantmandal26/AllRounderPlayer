# Keep Media3 session service entry points and serialized Navigation3 route metadata.
# Android's generated consumer rules cover the remainder of Media3 and Compose.
-keep class com.example.ymediaplayer.service.MusicService { *; }
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.libvlc.util.** { *; }
-keepattributes *Annotation*
