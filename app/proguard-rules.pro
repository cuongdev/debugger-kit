# Shizuku creates the shell user service by reflection.
-keep class io.github.cuongdev.debuggerkit.shell.ShellUserService { <init>(...); }
-keep class io.github.cuongdev.debuggerkit.shell.IShellService { *; }
-keep class io.github.cuongdev.debuggerkit.shell.IShellService$* { *; }

# Enum names are stored in SharedPreferences and read back with valueOf().
-keepclassmembers enum io.github.cuongdev.debuggerkit.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
