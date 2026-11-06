package io.github.cuongdev.debuggerkit.shell;

interface IShellService {
    /** Reserved transaction id Shizuku calls to stop the user service. */
    void destroy() = 16777114;

    /** Runs a command with "sh -c". Returns "exitCode\noutput". */
    String exec(String command) = 1;
}
