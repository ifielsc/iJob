package com.ifielsc.ijob.core.job.definition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.job.JobEdgeRequest;
import com.ifielsc.ijob.contract.job.JobLayout;
import com.ifielsc.ijob.contract.job.JobNodeRequest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class JobLayoutCodecTest {
    @Test
    void roundTripsPositionsWithoutChangingStableKeys() {
        var node = new JobNodeRequest();
        node.setNodeId("read");
        var position = new JobLayout.Position();
        position.setX(-20.25);
        position.setY(800.0);
        var layout = new JobLayout();
        layout.setVersion(1);
        layout.setPositions(Map.of("read", position));
        assertDoesNotThrow(() -> JobLayoutCodec.validate(null, List.of(node)));
        JobLayoutCodec.validate(layout, List.of(node));
        var restored = JobLayoutCodec.decode(JobLayoutCodec.encode(layout));
        assertEquals(-20.25, restored.getPositions().get("read").getX());
        assertEquals(800.0, restored.getPositions().get("read").getY());
        assertThrows(BusinessException.class, () -> JobLayoutCodec.validate(layout, List.of()));
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 1000001}) {
            position.setX(invalid);
            assertEquals(
                    "IJOB-JOB-LAYOUT",
                    assertThrows(
                                    BusinessException.class,
                                    () -> JobLayoutCodec.validate(layout, List.of(node)))
                            .getErrorCode());
        }
    }

    @Test
    void preservesDirectionalPortsAndRejectsInvalidOrOrphanedConnections() {
        var a = new JobNodeRequest();
        a.setNodeId("a");
        var b = new JobNodeRequest();
        b.setNodeId("b");
        var edge = new JobEdgeRequest();
        edge.setFromNodeId("a");
        edge.setToNodeId("b");
        var layout = new JobLayout();
        layout.setVersion(1);
        layout.setPositions(Map.of());
        var port = new JobLayout.Connection();
        port.setFromNodeId("a");
        port.setToNodeId("b");
        port.setSourceHandle("bottom");
        port.setTargetHandle("top");
        layout.setConnections(List.of(port));
        assertDoesNotThrow(() -> JobLayoutCodec.validate(layout, List.of(a, b), List.of(edge)));
        var restored = JobLayoutCodec.decode(JobLayoutCodec.encode(layout));
        assertEquals("bottom", restored.getConnections().getFirst().getSourceHandle());
        assertEquals("top", restored.getConnections().getFirst().getTargetHandle());
        assertThrows(
                BusinessException.class,
                () -> JobLayoutCodec.validate(layout, List.of(a, b), null));
        assertThrows(
                BusinessException.class,
                () -> JobLayoutCodec.validate(layout, List.of(a, b), List.of()));
        layout.setConnections(List.of(port, port));
        assertThrows(
                BusinessException.class,
                () -> JobLayoutCodec.validate(layout, List.of(a, b), List.of(edge)));
        layout.setConnections(List.of(port));
        port.setSourceHandle("center");
        assertThrows(
                BusinessException.class,
                () -> JobLayoutCodec.validate(layout, List.of(a, b), List.of(edge)));
        port.setSourceHandle("right");
        port.setTargetHandle("diagonal");
        assertThrows(
                BusinessException.class,
                () -> JobLayoutCodec.validate(layout, List.of(a, b), List.of(edge)));
        layout.setConnections(null);
        assertDoesNotThrow(() -> JobLayoutCodec.validate(layout, List.of(a, b), null));
    }

    @Test
    void everySideCanBeAnInputOrOutputAndRoundTripsWithoutReversingTheEdge() {
        var a = new JobNodeRequest();
        a.setNodeId("a");
        var b = new JobNodeRequest();
        b.setNodeId("b");
        var edge = new JobEdgeRequest();
        edge.setFromNodeId("a");
        edge.setToNodeId("b");
        var layout = new JobLayout();
        layout.setVersion(1);
        layout.setPositions(Map.of());
        var port = new JobLayout.Connection();
        port.setFromNodeId("a");
        port.setToNodeId("b");
        layout.setConnections(List.of(port));
        for (String source : List.of("left", "right", "top", "bottom")) {
            for (String target : List.of("left", "right", "top", "bottom")) {
                port.setSourceHandle(source);
                port.setTargetHandle(target);
                assertDoesNotThrow(
                        () -> JobLayoutCodec.validate(layout, List.of(a, b), List.of(edge)));
                var restored =
                        JobLayoutCodec.decode(JobLayoutCodec.encode(layout))
                                .getConnections()
                                .getFirst();
                assertEquals("a", restored.getFromNodeId());
                assertEquals("b", restored.getToNodeId());
                assertEquals(source, restored.getSourceHandle());
                assertEquals(target, restored.getTargetHandle());
            }
        }
    }
}
