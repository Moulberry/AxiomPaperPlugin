package com.moulberry.axiom.operations;

import com.moulberry.axiom.FoliaSupport;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OperationQueue {

    private record QueueKey(ServerLevel level, @Nullable UUID player) {
        static QueueKey of(ServerLevel level, PendingOperation operation) {
            return new QueueKey(level, FoliaSupport.FOLIA ? operation.executor().getUUID() : null);
        }
    }

    private final Lock queueLock = new ReentrantLock();
    private final Map<QueueKey, List<PendingOperation>> newPendingOperations = new HashMap<>();
    private final Lock executionLock = new ReentrantLock();
    private final Map<QueueKey, List<PendingOperation>> pendingOperations = new HashMap<>();

    public void tick() {
        if (!MinecraftServer.getServer().isSameThread()) {
            throw new WrongThreadException();
        }

        this.executionLock.lock(); // Just in case we're in some weird Folia environment or something
        try {
            this.queueLock.lock();
            try {
                for (Map.Entry<QueueKey, List<PendingOperation>> entry : this.newPendingOperations.entrySet()) {
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

            var queueIterator = this.pendingOperations.entrySet().iterator();
            while (queueIterator.hasNext()) {
                Map.Entry<QueueKey, List<PendingOperation>> queuedOperations = queueIterator.next();
                ServerLevel level = queuedOperations.getKey().level();

                var operationIterator = queuedOperations.getValue().iterator();
                while (operationIterator.hasNext()) {
                    PendingOperation operation = operationIterator.next();

                    try {
                        operation.tick(level);
                        if (operation.isFinished()) {
                            operationIterator.remove();
                        } else {
                            break;
                        }
                    } catch (Throwable t) {
                        ServerPlayer executor = operation.executor();
                        executor.getBukkitEntity().kick(net.kyori.adventure.text.Component.text("An error occurred while processing operation: " + t.getMessage()));
                        operationIterator.remove();
                    }
                }

                if (queuedOperations.getValue().isEmpty()) {
                    queueIterator.remove();
                }
            }
        } finally {
            this.executionLock.unlock();
        }

    }

    public void add(ServerLevel level, PendingOperation operation) {
        QueueKey key = QueueKey.of(level, operation);

        this.queueLock.lock();
        try {
            List<PendingOperation> operations = this.newPendingOperations.computeIfAbsent(key, k -> new ArrayList<>());

            if (operations.isEmpty() && MinecraftServer.getServer().isSameThread() && this.executionLock.tryLock()) {
                try {
                    var currentOperations = this.pendingOperations.get(key);
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
