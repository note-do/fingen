# Keep behaviour of the previous proguard-android.txt (AGP 9 uses the optimize variant).
# Remove this line to enable R8 optimizations and check release builds.
-dontoptimize

# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\Leonid\AppData\Local\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

#-keep class !android.support.v7.internal.view.menu.**,** {*;}
#-dontwarn
#-ignorewarnings
#-dontshrink


#MP Android chart
-keep class com.github.mikephil.charting.** { *; }
-dontwarn io.realm.**

-dontwarn android.databinding.**
-keep class android.databinding.** { *; }

#OpenCSV
-dontwarn au.com.bytecode.opencsv.bean.**
-keep class au.com.bytecode.opencsv.bean.** { *; }
-dontwarn java.beans.**
-keep class java.beans.** { *; }
-dontwarn javax.annotation.**
-keep class javax.annotation.** { *; }
-dontwarn javax.naming.**
-keep class javax.naming.** { *; }

#Eventbus
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

#Dropbox OkHttp and Servlet optional dependencies
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn com.google.appengine.**
-dontwarn javax.servlet.**
-dontwarn com.squareup.okhttp.**

#Retrofit 2
# Platform calls Class.forName on types which do not exist on Android to determine platform.
-dontnote retrofit2.Platform
# Platform used when running on Java 8 VMs. Will not be used at runtime.
-dontwarn retrofit2.Platform$Java8
# Retain generic type information for use by reflection by converters and adapters.
-keepattributes Signature
# Retain declared checked exceptions for use by a Proxy instance.
-keepattributes Exceptions

-keep class androidx.appcompat.widget.SearchView { *; }

-dontwarn com.sothree.**
-keep class com.sothree.**
-keep interface com.sothree.**