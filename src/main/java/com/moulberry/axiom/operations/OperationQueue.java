package com.moulberry.axiom.operations;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OperationQueue {

    private final Lock queueLock = new ReentrantLock();
    private final Map<ServerLevel, List<PendingOperation>> newPendingOperations = new HashMap<>();
    private final Lock executionLock = new ReentrantLock();
    private final Map<ServerLevel, List<PendingOperation>> pendingOperations = new HashMap<>();

    public void tick() {
        if (!MinecraftServer.getServer().isSameThread()) {
            throw new WrongThreadException();
        }

        this.executionLock.lock(); // Just in case we're in some weird Folia environment or something
        try {
            this.queueLock.lock();
            try {
                for (Map.Entry<ServerLevel, List<PendingOperation>> entry : this.newPendingOperations.entrySet()) {
                    List<PendingOperation> currentOperations = this.pendingOperations.get(entry.getKey());
                    if (currentOperations != null) {
                        currentOperations.addAll(entry.getValue());
                    } else {
                        this.pendingOperations.put(entry.getKey(), entry.getValue());
                    }
                }
                this.newPendingOperations.clear();
            } finally {
                this.queueLock.unlock();
            }

            var worldIterator = this.pendingOperations.entrySet().iterator();
            while (worldIterator.hasNext()) {
                Map.Entry<ServerLevel, List<PendingOperation>> perWorldOperations = worldIterator.next();

                var perWorldIterator = perWorldOperations.getValue().iterator();
                while (perWorldIterator.hasNext()) {
                    PendingOperation operation = perWorldIterator.next();

                    try {
                        operation.tick(perWorldOperations.getKey());
                        if (operation.isFinished()) {
                            perWorldIterator.remove();
                        } else {
                            break;
                        }
                    } catch (Throwable t) {
                        ServerPlayer executor = operation.executor();
                        executor.getBukkitEntity().kick(net.kyori.adventure.text.Component.text("An error occurred while processing operation: " + t.getMessage()));
                        perWorldIterator.remove();
                    }
                }

                if (perWorldOperations.getValue().isEmpty()) {
                    worldIterator.remove();
                }
            }
        } finally {
            this.executionLock.unlock();
        }

    }

    public void add(ServerLevel level, PendingOperation operation) {
        this.queueLock.lock();
        try {
            List<PendingOperation> operations = this.newPendingOperations.computeIfAbsent(level, k -> new ArrayList<>());

            if (operations.isEmpty() && MinecraftServer.getServer().isSameThread() && this.executionLock.tryLock()) {
                try {
                    var currentOperations = this.pendingOperations.get(level);
                    if (currentOperations == null || currentOperations.isEmpty()) {
                        operation.tick(level);
                        if (operation.isFinished()) {
                            return;
                        }
                    }
                } finally {
                    this.executionLock.unlock();
                }
            }

            operations.add(operation);
        } finally {
            this.queueLock.unlock();
        }
    }

}
