package com.ifielsc.ijob.contract.job;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class JobLayout {
    private Integer version;
    private Map<String, Position> positions;
    private List<Connection> connections;

    @Getter
    @Setter
    public static class Connection {
        private String fromNodeId;
        private String toNodeId;
        private String sourceHandle;
        private String targetHandle;
    }

    @Getter
    @Setter
    public static class Position {
        private Double x;
        private Double y;
    }
}
