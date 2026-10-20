# Readable release stack traces (keep mapping.txt of every release you ship).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Shizuku starts FoxShellService by class name in its own process, through the no-arg constructor
# that nothing in Fox calls (task 9.1).
-keep class dev.foxlauncher.feature.shizuku.FoxShellService {
    <init>();
}
