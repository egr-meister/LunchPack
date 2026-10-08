# LunchPack has no reflection-based code of its own.
# Room, DataStore, Compose, Navigation and AndroidX ship their own consumer rules.
# Receivers, the Activity and the Application class are referenced from the manifest (AGP keeps them).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
