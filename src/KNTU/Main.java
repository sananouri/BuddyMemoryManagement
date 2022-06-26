package KNTU;

import java.util.Scanner;

public class Main {
    public static MemoryManagementUnit mmu;

    public static void main(String[] args){
        System.out.print("Enter size of memory in MB: ");
        Scanner scanner = new Scanner(System.in);
        int size = scanner.nextInt() * 1024; // in KB
        mmu = new MemoryManagementUnit(size);
        System.out.print("Enter number of processes: ");
        int num = scanner.nextInt();
        for (int i = 0; i < num; i++) {
            new Thread(new Process()).start();
        }
        new Thread(new OS()).start();
    }
}
