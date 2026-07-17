# Add project-specific ProGuard rules here.
# YPVM ships as a debug build via CI by default; these rules only apply
# if a release build is produced locally.

-keep class com.mnmyounus.ypvm.admin.YpvmDeviceAdminReceiver { *; }
-keep class com.mnmyounus.ypvm.watchdog.BootReceiver { *; }
