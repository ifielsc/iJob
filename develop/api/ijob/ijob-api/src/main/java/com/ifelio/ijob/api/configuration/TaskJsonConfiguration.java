package com.ifelio.ijob.api.configuration;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ifelio.ijob.contract.notify.NotificationDeliveryDTO;
import com.ifelio.ijob.contract.notify.NotifySendLogDTO;
import com.ifelio.ijob.contract.runtime.GatewayDecisionDTO;
import com.ifelio.ijob.contract.runtime.InstanceDTO;
import com.ifelio.ijob.contract.runtime.InstanceLogDTO;
import com.ifelio.ijob.contract.runtime.RunTreeDTO;
import com.ifelio.ijob.contract.runtime.RuntimeCommandDTO;
import com.ifelio.ijob.contract.runtime.TriggerDTO;
import com.ifelio.ijob.contract.schedule.ScheduleDTO;
import com.ifelio.ijob.contract.task.TaskDetailDTO;
import com.ifelio.ijob.contract.task.TaskListDTO;
import com.ifelio.ijob.contract.task.TaskPublicationDTO;
import com.ifelio.ijob.contract.task.TaskVersionDTO;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exact management and runtime identifiers without changing persistence or numeric counters. */
@Configuration
public class TaskJsonConfiguration {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer taskIdentifiers() {
        return builder -> {
            builder.mixIn(ScheduleDTO.class, ScheduleIdentifiers.class);
            builder.mixIn(TaskListDTO.class, TaskIdentifiers.class);
            builder.mixIn(TaskDetailDTO.class, TaskIdentifiers.class);
            builder.mixIn(TaskPublicationDTO.class, PublicationIdentifiers.class);
            builder.mixIn(TaskVersionDTO.class, VersionIdentifiers.class);
            builder.mixIn(TriggerDTO.class, RuntimeIdentifiers.class);
            builder.mixIn(InstanceDTO.class, RuntimeIdentifiers.class);
            builder.mixIn(InstanceLogDTO.class, RuntimeIdentifiers.class);
            builder.mixIn(RunTreeDTO.class, RuntimeIdentifiers.class);
            builder.mixIn(GatewayDecisionDTO.class, RuntimeIdentifiers.class);
            builder.mixIn(RuntimeCommandDTO.class, RuntimeIdentifiers.class);
            builder.mixIn(NotifySendLogDTO.class, RuntimeIdentifiers.class);
            builder.mixIn(NotificationDeliveryDTO.class, RuntimeIdentifiers.class);
        };
    }

    abstract static class ScheduleIdentifiers {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getTargetId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getCalendarId();
    }

    abstract static class TaskIdentifiers {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getCurrentTaskDefinitionId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getCurrentRevision();
    }

    abstract static class PublicationIdentifiers {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long taskId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long definitionId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long revision();
    }

    abstract static class VersionIdentifiers {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getTaskId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getDefinitionId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getRevision();
    }

    abstract static class RuntimeIdentifiers {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getJobInstanceId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getNotifyConfigId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getNotifyBindingId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getTargetId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getScheduleId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getJobDefinitionId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getTaskDefinitionId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getRootInstanceId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getParentInstanceId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getNodeId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getDefinitionNodeId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getTriggerId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getInstanceId();
    }
}
