package com.ifelio.ijob.infra.coordination.id;

import com.ifelio.ijob.common.config.AppProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@Component
public class DatabaseSegmentIdGenerator implements IdGenerator {
    private final IdSegmentProvider segmentProvider;
    private final int runtimeSegmentSize;
    private final boolean sqlite;
    private final Map<IdNamespace, LocalRange> ranges = new EnumMap<>(IdNamespace.class);

    @Autowired
    public DatabaseSegmentIdGenerator(IdSegmentProvider segmentProvider, AppProperties properties) {
        this(
                segmentProvider,
                properties.getRuntime().getIdSegmentSize(),
                properties.getPlatformDatabase().getVendor()
                        == AppProperties.PlatformDatabaseVendor.SQLITE);
    }

    DatabaseSegmentIdGenerator(IdSegmentProvider segmentProvider, int runtimeSegmentSize) {
        this(segmentProvider, runtimeSegmentSize, false);
    }

    private DatabaseSegmentIdGenerator(
            IdSegmentProvider provider, int runtimeSegmentSize, boolean sqlite) {
        if (runtimeSegmentSize < 1 || runtimeSegmentSize > 100000) {
            throw new IllegalArgumentException("ID segment size is invalid");
        }
        this.segmentProvider = provider;
        this.runtimeSegmentSize = runtimeSegmentSize;
        this.sqlite = sqlite;
        for (IdNamespace namespace : IdNamespace.values()) ranges.put(namespace, new LocalRange());
    }

    @Override
    public long nextId(IdNamespace namespace) {
        Objects.requireNonNull(namespace, "ID namespace is required");
        // SQLite must join an existing writer transaction; rolled-back IDs never escape in a cache.
        if (sqlite && TransactionSynchronizationManager.isActualTransactionActive()) {
            return segmentProvider.reserveInTransaction(namespace.segmentKey(), 1).startInclusive();
        }
        LocalRange local = ranges.get(namespace);
        synchronized (local) {
            if (local.nextValue >= local.endExclusive) {
                int size =
                        namespace.defaultSegmentSize() == 1000
                                ? runtimeSegmentSize
                                : namespace.defaultSegmentSize();
                IdSegmentRange range = segmentProvider.reserve(namespace.segmentKey(), size);
                local.nextValue = range.startInclusive();
                local.endExclusive = range.endExclusive();
            }
            return local.nextValue++;
        }
    }

    private static final class LocalRange {
        private long nextValue;
        private long endExclusive;
    }
}
