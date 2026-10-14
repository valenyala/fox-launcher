package dev.foxlauncher.feature.memory.shizuku;

// Fox's code running in Shizuku's shell process (task 9.1, ADR-21). Fox binds it only for a deep boost.
interface IFoxShell {

    // Shizuku calls this transaction to end a user service.
    void destroy() = 16777114;

    // Asks Android, as the shell, to close the background processes of each package (`cmd activity kill`).
    // Returns how many packages it asked about without an error.
    int killBackgroundProcesses(in String[] packages) = 1;
}
