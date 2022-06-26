package KNTU;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import static KNTU.Main.mmu;

public class Process implements Runnable {
    public static Map<Integer, Process> processes = new HashMap<>();
    public static final ReentrantLock processListLock = new ReentrantLock();
    public final ReentrantLock usedSpaceLock;
    private final int pid;
    private final Map<Integer, Integer> usedSpace; // <beginning address, size>: used space of each block this process has
    private final long startTime;
    private long stopTime;

    public int getPid() {
        return pid;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getStopTime() {
        return stopTime;
    }

    public synchronized static Process getProcess(int pid) {
        return processes.get(pid);
    }

    public Map<Integer, Integer> getUsedSpace() {
        return usedSpace;
    }

    public Process() {
        usedSpaceLock = new ReentrantLock();
        processListLock.lock();
        pid = processes.size() + 1;
        processes.put(pid, this);
        processListLock.unlock();
        usedSpace = new HashMap<>();
        startTime = System.nanoTime();
        stopTime = -1;
    }

    public void remove() {
        usedSpaceLock.lock();
        Map<Integer, Integer> temp = new HashMap<>(usedSpace);
        usedSpaceLock.unlock();
        if (!usedSpace.isEmpty()) {
            for (Integer address : temp.keySet()) {
                mmu.deallocate(pid, address);
            }
        }
        processListLock.lock();
        processes.remove(pid);
        processListLock.unlock();
    }

    @Override
    public void run() {
        Random random = new Random();
        int address, size, num, count = random.nextInt(10);
        for (int i = 0; i < count; i++) {
            num = random.nextInt();
            if (num % 2 == 0 || getUsedSpace().size() == 0) { // allocate
                try {
                    Thread.sleep(1745);
                    size = random.nextInt(500) + 20;
                    address = mmu.allocate(pid, size);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            } else { // deallocate
                try {
                    Thread.sleep(1745);
                    num = random.nextInt(getUsedSpace().size());
                    address = (int) getUsedSpace().keySet().toArray()[num];
                    mmu.deallocate(pid, address);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        }
        stopTime = System.nanoTime();
    }
}
