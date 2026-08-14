package com.droidscan.telemetry;

/**
 * Remote telemetry surface exposed by the Shizuku UserService.
 *
 * Runs as UID 2000 (adb shell) inside an app_process started by Shizuku, so it
 * can perform shell-privileged reads that ordinary app processes cannot:
 * dumpsys on restricted services, root-gated sysfs, etc.
 *
 * `destroy()` is the reserved transaction defined by the Shizuku server.
 */
interface IShizukuTelemetry {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    /** Runs a shell command (blocking) and returns combined stdout+stderr. */
    String runCommand(String cmd) = 1;

    /** Reads a file with shell privileges; empty string if unreadable. */
    String readFile(String path) = 2;
}
