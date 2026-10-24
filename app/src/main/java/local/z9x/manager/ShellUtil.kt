package local.z9x.manager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Best-effort shell helpers. App runs as normal uid; many cmds will fail. */
object ShellUtil {

    fun getprop(key: String): String {
        val out = execCapture("/system/bin/getprop", key)?.trim()
            ?: return "(getprop 失败)"
        return if (out.isEmpty()) "(空)" else out
    }

    fun runCmd(vararg argv: String): String {
        val sb = StringBuilder()
        sb.append('$')
        for (a in argv) sb.append(' ').append(a)
        sb.append('\n')
        try {
            val pb = ProcessBuilder(*argv)
            pb.redirectErrorStream(true)
            val p = pb.start()
            val out = readAll(p.inputStream)
            val finished = p.waitFor(12, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                sb.append("  (超时)\n")
                return sb.toString()
            }
            if (out.isNotEmpty()) sb.append("  out: ").append(out.trim()).append('\n')
            sb.append("  exit=").append(p.exitValue()).append('\n')
        } catch (e: Exception) {
            sb.append("  ERR: ").append(e.message).append('\n')
        }
        return sb.toString()
    }

    fun run(vararg argv: String): Result {
        val r = Result(cmd = argv.joinToString(" "))
        try {
            val pb = ProcessBuilder(*argv)
            pb.redirectErrorStream(true)
            val p = pb.start()
            r.out = readAll(p.inputStream).trim()
            val finished = p.waitFor(20, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                r.timedOut = true
                r.exit = -1
            } else {
                r.exit = p.exitValue()
            }
        } catch (e: Exception) {
            r.error = e.message
            r.exit = -1
        }
        return r
    }

    fun execCapture(vararg argv: String): String? {
        return try {
            val pb = ProcessBuilder(*argv)
            pb.redirectErrorStream(true)
            val p = pb.start()
            val out = readAll(p.inputStream)
            p.waitFor(5, TimeUnit.SECONDS)
            out
        } catch (_: Exception) {
            null
        }
    }

    fun readAll(input: InputStream): String {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            bos.write(buf, 0, n)
        }
        return bos.toString(Charsets.UTF_8.name())
    }

    class Result(
        var cmd: String = "",
        var out: String = "",
        var error: String? = null,
        var exit: Int = -1,
        var timedOut: Boolean = false,
    ) {
        fun ok(): Boolean = !timedOut && error == null && exit == 0

        fun summary(): String {
            val sb = StringBuilder()
            sb.append("$ ").append(cmd).append('\n')
            if (out.isNotEmpty()) sb.append("  out: ").append(out).append('\n')
            if (error != null) sb.append("  ERR: ").append(error).append('\n')
            if (timedOut) sb.append("  (超时)\n")
            sb.append("  exit=").append(exit).append('\n')
            return sb.toString()
        }
    }
}
