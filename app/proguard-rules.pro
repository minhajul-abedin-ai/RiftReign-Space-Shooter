# RiftReign release rules.
# Google Play Billing, Mobile Ads/UMP and Play Games ship their own consumer rules.
# The launcher Activity is manifest-referenced and retained automatically.
# Keep source/line information for useful Play Console crash reports while still obfuscating.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
