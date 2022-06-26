package KNTU;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static KNTU.Process.*;
import static KNTU.Main.mmu;

public class OS implements Runnable {

    @Override
    public void run() {
        processListLock.lock();
        int processes_size = processes.size();
        processListLock.unlock();
        while (processes_size > 0) {
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            ArrayList<String> outputs = new ArrayList<>();
            int occupiedSpace = 0, freeSpace = mmu.getMemorySize();
            int size, internalFrag = 0, processUsed, processOccupied;
            processListLock.lock();
            Map<Integer, Process> tempProcesses = new HashMap<>(processes);
            processListLock.unlock();
            mmu.usedListLock.lock();
            if (!mmu.getUsedBlocks().isEmpty()) {
                for (Integer used : mmu.getUsedBlocks().values()) {
                    occupiedSpace += used;
                }
            }
            System.out.println("Total occupied space = " + occupiedSpace);
            for (Process p : tempProcesses.values()) {
                processUsed = 0;
                processOccupied = 0;
                p.usedSpaceLock.lock();
                if (!p.getUsedSpace().isEmpty()) {
                    for (Integer address : p.getUsedSpace().keySet()) {
                        size = p.getUsedSpace().get(address);
                        processUsed += size;
                        freeSpace -= size;
                        processOccupied += mmu.getUsedBlocks().get(address);
                        internalFrag += mmu.getUsedBlocks().get(address) - size;
                    }
                }
                p.usedSpaceLock.unlock();
                outputs.add("Process " + p.getPid() + ": \n");
                outputs.add("\t start time = " + p.getStartTime() + "\n");
                if (p.getStopTime() == -1) {
                    outputs.add("\t stop time = <still running>\n");
                    outputs.add("\t total runtime = <still running>\n");
                } else {
                    outputs.add("\t stop time = " + p.getStopTime() + "\n");
                    outputs.add("\t total runtime = " + (p.getStopTime() - p.getStartTime()) + "\n");
                    p.remove();
                    processes_size--;
                }
                outputs.add("\t occupied space = " + processOccupied + "\n");
                outputs.add("\t used space = " + processUsed + "\n");
            }
            mmu.usedListLock.unlock();
            System.out.println("Total free space (completely or partially free blocks) = " + freeSpace);
            System.out.println("Total internal fragmentation = " + internalFrag);
            System.out.println("Total external fragmentation = " + (mmu.getMemorySize() - occupiedSpace));
            for (String s : outputs) {
                System.out.print(s);
            }
        }
    }
}
