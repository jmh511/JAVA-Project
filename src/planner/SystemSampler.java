package planner;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Timer;

/**
 * Reads CPU load, memory use and free disk space once per second with standard JDK APIs
 * (com.sun.management.OperatingSystemMXBean, java.io.File), and keeps the last minute of
 * CPU / memory values for the graphs. One sampler for the whole program, so the history is
 * not lost when the widget is rebuilt.
 */
public final class SystemSampler {

    /** Number of samples kept (one per second = the last minute). */
    public static final int HISTORY = 60;

    private static SystemSampler instance;

    public static SystemSampler get() {
        if (instance == null) {
            instance = new SystemSampler();
        }
        return instance;
    }

    private final com.sun.management.OperatingSystemMXBean os;
    private final File disk;
    private final double[] cpu = new double[HISTORY];
    private final double[] memory = new double[HISTORY];
    private int count;      // how many samples are filled (up to HISTORY)
    private int next;       // where the next sample goes (ring buffer)
    private long memTotal;
    private long memUsed;
    private long diskTotal;
    private long diskFree;
    private final List<Runnable> listeners = new ArrayList<>();
    private final Timer timer = new Timer(1000, e -> sample());

    private SystemSampler() {
        os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        // The drive that holds the user's files (usually C:\).
        File home = new File(System.getProperty("user.home"));
        File root = home;
        while (root.getParentFile() != null) {
            root = root.getParentFile();
        }
        disk = root;
        sample();
        timer.start();
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    private void sample() {
        double load = os.getCpuLoad(); // 0..1, or negative when not known yet
        memTotal = os.getTotalMemorySize();
        memUsed = memTotal - os.getFreeMemorySize();
        diskTotal = disk.getTotalSpace();
        diskFree = disk.getUsableSpace();
        cpu[next] = load < 0 ? 0 : load;
        memory[next] = memTotal > 0 ? memUsed / (double) memTotal : 0;
        next = (next + 1) % HISTORY;
        count = Math.min(HISTORY, count + 1);
        for (Runnable r : new ArrayList<>(listeners)) {
            r.run();
        }
    }

    /** Oldest first; values 0..1. */
    public double[] cpuHistory() {
        return ordered(cpu);
    }

    public double[] memoryHistory() {
        return ordered(memory);
    }

    private double[] ordered(double[] ring) {
        double[] out = new double[count];
        int start = count < HISTORY ? 0 : next;
        for (int i = 0; i < count; i++) {
            out[i] = ring[(start + i) % HISTORY];
        }
        return out;
    }

    public double cpuNow() {
        return count == 0 ? 0 : cpu[(next - 1 + HISTORY) % HISTORY];
    }

    public double memoryNow() {
        return count == 0 ? 0 : memory[(next - 1 + HISTORY) % HISTORY];
    }

    public long memoryUsed() {
        return memUsed;
    }

    public long memoryTotal() {
        return memTotal;
    }

    public long diskFree() {
        return diskFree;
    }

    public long diskTotal() {
        return diskTotal;
    }

    public String diskName() {
        String p = disk.getPath();
        return p.endsWith("\\") || p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    /** 8.4 GB style text. */
    public static String gb(long bytes) {
        double g = bytes / (1024.0 * 1024 * 1024);
        return g >= 100 ? String.format("%.0f GB", g) : String.format("%.1f GB", g);
    }
}
