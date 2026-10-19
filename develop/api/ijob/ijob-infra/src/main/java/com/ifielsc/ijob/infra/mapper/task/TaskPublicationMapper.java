package com.ifielsc.ijob.infra.mapper.task;

public interface TaskPublicationMapper {
    TaskPublicationIdentityRow selectIdentityForUpdate(Long taskId);

    int insertIdentity(TaskPublicationIdentityInsert identity);

    int initializeCurrentDefinition(TaskCurrentDefinitionInitialization initialization);

    int advanceCurrentDefinition(TaskCurrentDefinitionAdvance advance);

    int retireIdentity(TaskIdentityRetirement retirement);
}
