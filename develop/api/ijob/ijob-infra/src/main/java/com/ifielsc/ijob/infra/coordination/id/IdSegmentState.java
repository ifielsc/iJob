package com.ifielsc.ijob.infra.coordination.id;

public class IdSegmentState {
    private String segmentKey;
    private Long nextValue;
    private Integer segmentSize;

    public String getSegmentKey() {
        return segmentKey;
    }

    public void setSegmentKey(String segmentKey) {
        this.segmentKey = segmentKey;
    }

    public Long getNextValue() {
        return nextValue;
    }

    public void setNextValue(Long nextValue) {
        this.nextValue = nextValue;
    }

    public Integer getSegmentSize() {
        return segmentSize;
    }

    public void setSegmentSize(Integer segmentSize) {
        this.segmentSize = segmentSize;
    }
}
