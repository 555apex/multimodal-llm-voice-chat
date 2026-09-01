package cn.fj.roadagent.application.dispatch;

import cn.fj.roadagent.domain.dispatch.WorkflowStage;

public interface QueryWorkflowInboxUseCase {
    WorkflowInbox inbox(WorkflowStage stage);
}
