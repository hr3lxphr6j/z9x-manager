package local.z9x.manager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Best-effort shell helpers. App runs as normal uid; many cmds will fail. */
public final class ShellUtil {
    private ShellUtil() {}

    public static String getprop(String key) {
        String out = execCapture("/system/bin/getprop", key);
        if (out == null) return "(getprop 失败)";
        out = out.trim();
        return out.length() == 0 ? "(空)" : out;
    }

    public static String runCmd(String... argv) {
        StringBuilder sb = new StringBuilder();
        sb.append("$");
        for (String a : argv) sb.append(' ').append(a);
        sb.append('\n');
        try {
            ProcessBuilder pb = new ProcessBuilder(argv);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = readAll(p.getInputStream());
            boolean finished = p.waitFor(12, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                sb.append("  (超时)\n");
                return sb.toString();
            }
            int code = p.exitValue();
            if (out.length() > 0) sb.append("  out: ").append(out.trim()).append('\n');
            sb.append("  exit=").append(code).append('\n');
        } catch (Exception e) {
            sb.append("  ERR: ").append(e.getMessage()).append('\n');
        }
        return sb.toString();
    }

    public static Result run(String... argv) {
        Result r = new Result();
        r.cmd = join(argv);
        try {
            ProcessBuilder pb = new ProcessBuilder(argv);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            r.out = readAll(p.getInputStream()).trim();
            boolean finished = p.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                r.timedOut = true;
                r.exit = -1;
            } else {
                r.exit = p.exitValue();
            }
        } catch (Exception e) {
            r.error = e.getMessage();
            r.exit = -1;
        }
        return r;
    }

    public static String execCapture(String... argv) {
        try {
            ProcessBuilder pb = new ProcessBuilder(argv);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = readAll(p.getInputStream());
            p.waitFor(5, TimeUnit.SECONDS);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    private static String join(String[] argv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < argv.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(argv[i]);
        }
        return sb.toString();
    }

    public static final class Result {
        public String cmd;
        public String out = "";
        public String error;
        public int exit = -1;
        public boolean timedOut;

        public boolean ok() {
            return !timedOut && error == null && exit == 0;
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("$ ").append(cmd).append('\n');
            if (out.length() > 0) sb.append("  out: ").append(out).append('\n');
            if (error != null) sb.append("  ERR: ").append(error).append('\n');
            if (timedOut) sb.append("  (超时)\n");
            sb.append("  exit=").append(exit).append('\n');
            return sb.toString();
        }
    }
}
