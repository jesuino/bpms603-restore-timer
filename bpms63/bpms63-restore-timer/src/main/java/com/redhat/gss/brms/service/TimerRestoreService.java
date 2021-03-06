package com.redhat.gss.brms.service;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.drools.core.command.impl.CommandBasedStatefulKnowledgeSession;
import org.drools.core.command.impl.KnowledgeCommandContext;
import org.drools.core.impl.StatefulKnowledgeSessionImpl;
import org.jbpm.kie.services.impl.KModuleDeploymentUnit;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.command.UpdateTimerCommand;
import org.jbpm.process.instance.timer.TimerManager;
import org.jbpm.services.api.model.DeploymentUnit;
import org.jbpm.services.ejb.api.DeploymentServiceEJBLocal;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.node.TimerNodeInstance;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.internal.runtime.conf.RuntimeStrategy;
import org.kie.internal.runtime.manager.context.EmptyContext;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;

@Stateless
public class TimerRestoreService {

	@EJB
	DeploymentServiceEJBLocal deploymentService;

	private RuntimeStrategy strategy;

	private RuntimeManager runtimeManager;

	public void setAsTriggered(String deploymentId, long piid, RuntimeStrategy strategy) {
		this.strategy = strategy;
		RuntimeEngine runtimeEngine = getRuntimeEngine(deploymentId, piid);
		KieSession kSession = runtimeEngine.getKieSession();
		WorkflowProcessInstance pi = (WorkflowProcessInstance) kSession
				.getProcessInstance(piid);
		TimerNodeInstance oldTimerInstance = getTimerInstance(pi);
		oldTimerInstance.triggerCompleted(true);
		dispose(deploymentId, runtimeEngine);
	}
	
	public void cancelTimer(String deploymentId, long piid, RuntimeStrategy strategy) {
		this.strategy = strategy;
		RuntimeEngine runtimeEngine = getRuntimeEngine(deploymentId, piid);
		KieSession kSession = runtimeEngine.getKieSession();
		WorkflowProcessInstance pi = (WorkflowProcessInstance) kSession
				.getProcessInstance(piid);
		TimerNodeInstance timerInstance = getTimerInstance(pi);
		TimerManager tm = getTimerManager(kSession);
		tm.cancelTimer(timerInstance.getId());
		dispose(deploymentId, runtimeEngine);
	}

	public void updateTimerNode(Long piid, String deploymentId, long delay,
			long period, int repeatLimit) {
		RuntimeEngine runtimeEngine = getRuntimeEngine(deploymentId, piid);
		KieSession kSession = runtimeEngine.getKieSession();
		WorkflowProcessInstance pi = (WorkflowProcessInstance) kSession
				.getProcessInstance(piid);
		TimerNodeInstance timerInstance = getTimerInstance(pi);
		UpdateTimerCommand cmd = new UpdateTimerCommand(piid, timerInstance
				.getTimerNode().getName(), delay, period, repeatLimit);
		kSession.execute(cmd);
		dispose(deploymentId, runtimeEngine);
	}
	

	public RuntimeEngine getRuntimeEngine(String deploymentId, Long piid) {
		runtimeManager = getRuntimeManager(deploymentId);
		RuntimeEngine runtimeEngine = null;
		if (strategy == RuntimeStrategy.PER_PROCESS_INSTANCE) {
			runtimeEngine = runtimeManager
					.getRuntimeEngine(ProcessInstanceIdContext.get(piid));
		} else {
			strategy = RuntimeStrategy.SINGLETON;
			runtimeEngine = runtimeManager.getRuntimeEngine(EmptyContext.get());
		}
		return runtimeEngine;
	}
	
	private static TimerManager getTimerManager(KieSession ksession) {
		KieSession internal = ksession;
		if (ksession instanceof CommandBasedStatefulKnowledgeSession) {
			internal = ((KnowledgeCommandContext) ((CommandBasedStatefulKnowledgeSession) ksession)
					.getCommandService().getContext()).getKieSession();
		}
		return ((InternalProcessRuntime) ((StatefulKnowledgeSessionImpl) internal)
				.getProcessRuntime()).getTimerManager();
	}
	
	private TimerNodeInstance getTimerInstance(WorkflowProcessInstance pi) {
		TimerNodeInstance oldTimerInstance = null;
		for (NodeInstance n : pi.getNodeInstances()) {
			if (n instanceof TimerNodeInstance)
				oldTimerInstance = (TimerNodeInstance) n;
		}
		if (oldTimerInstance == null) {
			throw new WebApplicationException(Response
					.status(Status.BAD_REQUEST)
					.entity("Process NOT stopped on a TimerNodeInstance")
					.build());
		}
		return oldTimerInstance;
	}

	public void dispose(String deploymentId, RuntimeEngine runtimeEngine) {
		runtimeManager.disposeRuntimeEngine(runtimeEngine);
	}

	private RuntimeManager getRuntimeManager(String deploymentId) {
		if (!deploymentService.isDeployed(deploymentId)) {
			String[] gav = deploymentId.split(":");
			DeploymentUnit deploymentUnit = new KModuleDeploymentUnit(gav[0],
					gav[1], gav[2]);
			deploymentService.deploy(deploymentUnit);
		}
		return deploymentService.getRuntimeManager(deploymentId);
	}
}
