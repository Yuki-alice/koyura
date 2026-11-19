package app.koyura.data.session

import app.koyura.source.runtime.SourceSecretStore
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okio.FileSystem
import okio.Path

internal fun desktopSourceSecretStore(directory: Path): SourceSecretStore? {
    val accountPrefix = MessageDigest.getInstance("SHA-256")
        .digest(directory.toFile().canonicalPath.encodeToByteArray()).toHexString()
    return when {
        System.getProperty("os.name").startsWith("Mac") -> MacSourceSecretStore(accountPrefix)
        System.getProperty("os.name").startsWith("Linux") -> LinuxSourceSecretStore(accountPrefix)
        System.getProperty("os.name").startsWith("Windows") -> WindowsSourceSecretStore(directory / "source-sessions")
        else -> null
    }
}

private const val SERVICE = "app.koyura.source-sessions"

private class MacSourceSecretStore(private val prefix: String) : SourceSecretStore {
    override fun read(sourceKey: String): String? {
        val result = command("/usr/bin/security", "find-generic-password", "-s", SERVICE, "-a", "$prefix:$sourceKey", "-w")
        if (result.status == 44) return null
        check(result.status == 0) { "Could not read source session from Keychain" }
        return result.output.trimEnd('\r', '\n')
    }

    override fun write(sourceKey: String, value: String?) {
        val account = "$prefix:$sourceKey"
        if (value == null) {
            val result = command("/usr/bin/security", "delete-generic-password", "-s", SERVICE, "-a", account)
            check(result.status == 0 || result.status == 44) { "Could not delete source session from Keychain" }
        } else {
            // The interactive security subcommand reads the payload from stdin so it stays out of argv.
            val hex = value.encodeToByteArray().toHexString()
            command("/usr/bin/security", "-i", input = "add-generic-password -U -s $SERVICE -a $account -X $hex\nquit\n")
            check(read(sourceKey) == value) { "Could not save source session in Keychain" }
        }
    }
}

private class LinuxSourceSecretStore(private val prefix: String) : SourceSecretStore {
    override fun read(sourceKey: String): String? {
        val result = command("secret-tool", "lookup", "service", SERVICE, "account", "$prefix:$sourceKey")
        if (result.status == 1 && result.output.isEmpty()) return null
        check(result.status == 0) { "Could not read source session from Secret Service" }
        return result.output.trimEnd('\r', '\n')
    }

    override fun write(sourceKey: String, value: String?) {
        val attributes = arrayOf("service", SERVICE, "account", "$prefix:$sourceKey")
        val result = if (value == null) {
            command("secret-tool", "clear", *attributes)
        } else {
            command("secret-tool", "store", "--label=Reader source session", *attributes, input = value)
        }
        check(result.status == 0 || value == null && result.status == 1) { "Could not update source session in Secret Service" }
    }
}

private class WindowsSourceSecretStore(private val directory: Path) : SourceSecretStore {
    private val files = FileSystem.SYSTEM

    override fun read(sourceKey: String): String? {
        val path = directory / sourceKey
        if (!files.exists(path)) return null
        val encrypted = files.read(path) { readUtf8() }
        return protect(encrypted, "Unprotect").trimEnd('\r', '\n')
    }

    override fun write(sourceKey: String, value: String?) {
        val path = directory / sourceKey
        if (value == null) {
            files.delete(path, mustExist = false)
            return
        }
        val encrypted = protect(value, "Protect")
        files.createDirectories(directory)
        val temporary = directory / "$sourceKey.new"
        try {
            files.write(temporary) { writeUtf8(encrypted) }
            files.atomicMove(temporary, path)
        } finally {
            files.delete(temporary, mustExist = false)
        }
    }

    private fun protect(value: String, operation: String): String {
        val readBytes = if (operation == "Protect") {
            "[Text.Encoding]::UTF8.GetBytes([Console]::In.ReadToEnd())"
        } else {
            "[Convert]::FromBase64String([Console]::In.ReadToEnd())"
        }
        val output = if (operation == "Protect") "[Convert]::ToBase64String" else "[Text.Encoding]::UTF8.GetString"
        val script = "\$ErrorActionPreference = 'Stop'; Add-Type -AssemblyName System.Security; " +
            "\$bytes = $readBytes; \$result = [Security.Cryptography.ProtectedData]::$operation(" +
            "\$bytes, \$null, [Security.Cryptography.DataProtectionScope]::CurrentUser); " +
            "[Console]::Write($output(\$result))"
        val result = command("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script, input = value)
        check(result.status == 0) { "Could not access the Windows source session" }
        return result.output
    }
}

private data class CommandResult(val status: Int, val output: String)

private fun command(vararg args: String, input: String? = null): CommandResult {
    val process = ProcessBuilder(*args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    try {
        val output = process.inputStream.bufferedReader(Charsets.UTF_8)
        val read = java.util.concurrent.CompletableFuture.supplyAsync { output.use { it.readText() } }
        process.outputStream.bufferedWriter(Charsets.UTF_8).use { if (input != null) it.write(input) }
        check(process.waitFor(12, TimeUnit.SECONDS)) { "Platform credential store did not respond" }
        return CommandResult(process.exitValue(), read.get(2, TimeUnit.SECONDS))
    } finally {
        process.destroyForcibly()
    }
}
