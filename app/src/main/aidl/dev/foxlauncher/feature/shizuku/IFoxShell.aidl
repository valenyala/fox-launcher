package dev.foxlauncher.feature.shizuku;

// Fox's code running in Shizuku's shell process (tasks 9.1 and 9.6, ADR-21, ADR-23). Fox binds it for one call at a time.
interface IFoxShell {

    // Shizuku calls this transaction to end a user service.
    void destroy() = 16777114;

    // Asks Android, as the shell, to close the background processes of each package (`cmd activity kill`).
    // Returns how many packages it asked about without an error.
    int killBackgroundProcesses(in String[] packages) = 1;

    // Turns full-screen gestures on (hidden) or off: MIUI's setting when miui, else Android's navigation overlay.
    boolean setNavigationButtonsHidden(boolean hidden, boolean miui) = 2;
}
