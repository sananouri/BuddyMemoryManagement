package KNTU;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static KNTU.Process.getProcess;

public class MemoryManagementUnit {
    public final ReentrantLock usedListLock;
    private final ReentrantLock freeListLock;
    // <beginning address, size>: used blocks -> size can be 1024, 512, 256, 128, 64, 32
    private final Map<Integer, Integer> usedBlocks = new HashMap<>();
    // <beginning address, size>: free blocks -> size can be 1024, 512, 256, 128, 64, 32
    private final Map<Integer, Integer> freeBlocks = new HashMap<>();
    private final int memorySize;

    public int getMemorySize() {
        return memorySize;
    }

    public Map<Integer, Integer> getUsedBlocks() {
        return usedBlocks;
    }

    public MemoryManagementUnit(int memorySize) {
        usedListLock = new ReentrantLock();
        freeListLock = new ReentrantLock();
        this.memorySize = memorySize;
        int remaining = memorySize, address = 0;
        while (remaining >= 1024) {
            freeBlocks.put(address, 1024);
            remaining -= 1024;
            address += 1024;
        }
    }

    public int allocate(int pid, int size) throws Exception {
        int spaceOfBlock, usedSpaceOfBlock;
        Map<Integer, Integer> processUsedSpace = getProcess(pid).getUsedSpace();
        for (Integer usedAddress : processUsedSpace.keySet()) {
            usedListLock.lock();
            spaceOfBlock = usedBlocks.get(usedAddress);
            usedListLock.unlock();
            usedSpaceOfBlock = processUsedSpace.get(usedAddress);
            if (spaceOfBlock - usedSpaceOfBlock >= size) {
                processUsedSpace.remove(usedAddress);
                processUsedSpace.put(usedAddress, size + usedSpaceOfBlock);
                return -1;
            }
        }
        int address = 0;
        int sizeFound, currentSize = 0;
        freeListLock.lock();
        for (Integer freeAddress : freeBlocks.keySet()) {
            sizeFound = freeBlocks.get(freeAddress);
            if (currentSize < size || (sizeFound >= size && sizeFound < currentSize)) {
                address = freeAddress;
                currentSize = sizeFound;
            }
        }
        freeListLock.unlock();
        if (currentSize < size) { // cannot allocate
            StringBuilder sb = new StringBuilder("cannot allocate memory: process " + pid +
                    " requested " + size + "KB, free spaces: ");
            freeListLock.lock();
            for (Integer size1 : freeBlocks.values())
                sb.append(size1).append("KB, ");
            if (freeBlocks.size() == 0) {
                sb.append("none");
            } else {
                sb.delete(sb.length() - 2, sb.length());
            }
            freeListLock.unlock();
            throw new Exception(sb.toString());
        }
        freeListLock.lock();
        freeBlocks.remove(address);
        while (size <= currentSize / 2) {
            currentSize /= 2;
            freeBlocks.put(address + currentSize, currentSize);
        }
        usedListLock.lock();
        freeListLock.unlock();
        usedBlocks.put(address, currentSize);
        getProcess(pid).usedSpaceLock.lock();
        getProcess(pid).getUsedSpace().put(address, size);
        getProcess(pid).usedSpaceLock.unlock();
        usedListLock.unlock();
        System.out.println("allocated " + currentSize + "KB to process " + pid + " starting from address " + address);
        return address;
    }

    public void deallocate(int pid, int address) {
        usedListLock.lock();
        int size = usedBlocks.get(address);
        usedBlocks.remove(address);
        getProcess(pid).usedSpaceLock.lock();
        getProcess(pid).getUsedSpace().remove(address);
        getProcess(pid).usedSpaceLock.unlock();
        usedListLock.unlock();
        freeListLock.lock();
        combineFreeSpaces(address, size);
        freeListLock.unlock();
        System.out.println("process " + pid + " deallocated " + size + "KB starting from address " + address);
    }


    public void combineFreeSpaces(int gAddress, int gSize) {
        int address_before, size_before, address_after, size_after;
        while (true) {
            address_before = 0;
            size_before = 0;
            address_after = memorySize;
            size_after = 0;
            for (Integer freeAddress : freeBlocks.keySet()) {
                if (freeAddress < gAddress) {
                    if (address_before <= freeAddress) {
                        address_before = freeAddress;
                        size_before = freeBlocks.get(freeAddress);
                    }
                } else if (freeAddress > gAddress) {
                    if (address_after >= freeAddress) {
                        address_after = freeAddress;
                        size_after = freeBlocks.get(freeAddress);
                    }
                }
            }
            if (address_before + size_before == gAddress && gSize == size_before && gSize != 1024) {
                freeBlocks.remove(gAddress);
                freeBlocks.remove(address_before);
                if (gAddress + gSize == address_after && 2 * gSize == size_after && size_after != 1024) {
                    freeBlocks.remove(address_after);
                    freeBlocks.put(address_before, gSize + size_before + size_after);
                    gAddress = address_before;
                    gSize = gSize + size_before + size_after;
                } else {
                    freeBlocks.put(address_before, gSize + size_before);
                    gAddress = address_before;
                    gSize = gSize + size_before;
                }
            } else if (gAddress + gSize == address_after && gSize == size_after && gSize != 1024) {
                freeBlocks.remove(address_after);
                if (address_before + size_before == gAddress && 2 * gSize == size_before && size_before != 1024) {
                    freeBlocks.remove(address_before);
                    freeBlocks.remove(gAddress);
                    freeBlocks.put(address_before, gSize + size_before + size_after);
                    gAddress = address_before;
                    gSize = gSize + size_before + size_after;
                } else {
                    freeBlocks.remove(gAddress);
                    freeBlocks.put(gAddress, gSize + size_after);
                    gSize = gSize + size_after;
                }
            } else if (address_before + size_before == gAddress && gAddress + gSize == address_after && size_before == size_after && size_after != 1024) {
                freeBlocks.remove(address_before);
                freeBlocks.remove(address_after);
                freeBlocks.remove(gAddress);
                if ((2 * size_after == gSize && gSize != 1024) || size_after == 0) {
                    freeBlocks.put(address_before, gSize + size_before + size_after);
                    gAddress = address_before;
                    gSize = gSize + size_before + size_after;
                } else {
                    freeBlocks.put(address_before, 2 * size_before);
                    freeBlocks.put(gAddress + 2 * size_before, gSize);
                    gAddress = gAddress + 2 * size_before;
                }
            } else {
                freeBlocks.put(gAddress, gSize);
                break;
            }
        }
    }
}
