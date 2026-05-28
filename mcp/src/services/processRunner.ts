import {spawn} from "node:child_process";

/**
 * Result of a process execution.
 */
export type ProcessRunResult = {
    /** The command that was executed. */
    command: string;
    /** The arguments passed to the command. */
    args: string[];
    /** The exit code of the process, or null if it was terminated by a signal. */
    exitCode: number | null;
    /** The signal that terminated the process, if any. */
    signal: NodeJS.Signals | null;
    /** The standard output of the process. */
    stdout: string;
    /** The standard error of the process. */
    stderr: string;
    /** Total execution time in milliseconds. */
    durationMs: number;
    /** Whether the process was terminated because it exceeded the timeout. */
    timedOut: boolean;
};

/**
 * Options for running a command.
 */
type RunCommandOptions = {
    /** The command to execute. */
    command: string;
    /** Arguments to pass to the command. */
    args: string[];
    /** Working directory for the process. */
    cwd: string;
    /** Maximum execution time in milliseconds. */
    timeoutMs: number;
    /** Optional environment variables to merge with the current process environment. */
    env?: NodeJS.ProcessEnv;
};

/**
 * Executes a command as a child process and returns its output and execution metadata.
 *
 * @param options - Configuration for the command execution.
 * @returns A promise that resolves to a {@link ProcessRunResult} containing the output and status.
 * @throws An error if the process fails to start.
 */
export async function runCommand(options: RunCommandOptions): Promise<ProcessRunResult> {
    const startedAt = Date.now();

    return await new Promise<ProcessRunResult>((resolve, reject) => {
        const child = spawn(options.command, options.args, {
            cwd: options.cwd,
            env: {
                ...process.env,
                ...options.env,
            },
            shell: false,
            stdio: ["ignore", "pipe", "pipe"],
        });

        let stdout = "";
        let stderr = "";
        let timedOut = false;

        const timeoutHandle = setTimeout(() => {
            timedOut = true;
            child.kill("SIGTERM");
        }, options.timeoutMs);

        child.stdout.setEncoding("utf8");
        child.stderr.setEncoding("utf8");

        child.stdout.on("data", (chunk: string) => {
            stdout += chunk;
        });

        child.stderr.on("data", (chunk: string) => {
            stderr += chunk;
        });

        child.on("error", (error) => {
            clearTimeout(timeoutHandle);
            reject(error);
        });

        child.on("close", (exitCode, signal) => {
            clearTimeout(timeoutHandle);
            resolve({
                command: options.command,
                args: options.args,
                exitCode,
                signal,
                stdout: stdout.trim(),
                stderr: stderr.trim(),
                durationMs: Date.now() - startedAt,
                timedOut,
            });
        });
    });
}
