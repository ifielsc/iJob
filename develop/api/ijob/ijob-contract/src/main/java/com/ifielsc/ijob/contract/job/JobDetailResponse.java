package com.ifielsc.ijob.contract.job;

import com.ifielsc.ijob.contract.notify.NotifyBindingDTO;
import com.ifielsc.ijob.contract.schedule.ScheduleDTO;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class JobDetailResponse {
    private Long id;
    private JobLayout layout;
    private String name;
    private Integer timeout;
    private Boolean enabled;
    private String description;
    private Long currentDefinitionId;
    private Long currentRevision;
    private LocalDateTime publishedAt;
    private Boolean current;
    private List<String> groups;
    private List<JobNodeResponse> nodes;
    private List<JobEdgeResponse> edges;
    private List<ScheduleDTO> schedules;
    private List<NotifyBindingDTO> notifyBindings;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
