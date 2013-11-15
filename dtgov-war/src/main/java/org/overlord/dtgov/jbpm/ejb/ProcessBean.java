/**
 * Copyright 2013 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.overlord.dtgov.jbpm.ejb;

import java.util.Collection;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import org.jboss.seam.transaction.Transactional;
import org.jbpm.kie.services.impl.KModuleDeploymentUnit;
import org.jbpm.kie.services.impl.event.DeploymentEvent;
import org.jbpm.kie.services.impl.event.Undeploy;
import org.jbpm.kie.services.impl.model.ProcessInstanceDesc;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.task.TaskService;
import org.kie.internal.runtime.manager.context.EmptyContext;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.overlord.dtgov.jbpm.util.KieSrampUtil;
import org.overlord.dtgov.jbpm.util.ProcessEngineService;
import org.overlord.dtgov.server.i18n.Messages;
import org.overlord.sramp.governance.Governance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Transactional
public class ProcessBean {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	private static Boolean hasSRAMPPackageDeployed = Boolean.FALSE;

	@Inject
	@ApplicationScoped
	private ProcessEngineService processEngineService;
  
	@PostConstruct
	public void configure() {
		//we need it to start to startup task management - however
		//we don't want it to start before we have the workflow are
		//definitions deployed (on first time boot)
		synchronized(hasSRAMPPackageDeployed) {
			KieSrampUtil kieSrampUtil = new KieSrampUtil();
			Governance governance = new Governance();
			String groupId = governance.getGovernanceWorkflowGroup();
			String artifactId = governance.getGovernanceWorkflowName();
			String version = governance.getGovernanceWorkflowVersion();
			
			if (kieSrampUtil.isSRAMPPackageDeployed(groupId, artifactId, version)) {
				KModuleDeploymentUnit unit = new KModuleDeploymentUnit(
						groupId, 
						artifactId,
						version,
						Governance.DEFAULT_GOVERNANCE_WORKFLOW_PACKAGE,
						Governance.DEFAULT_GOVERNANCE_WORKFLOW_KSESSION);
				RuntimeManager runtimeManager = kieSrampUtil.getRuntimeManager(processEngineService, unit);
				RuntimeEngine runtime = runtimeManager.getRuntimeEngine(EmptyContext.get());
				//use toString to make sure CDI initializes the bean
				//to make sure the task manager starts up on reboot
				runtime.getTaskService().toString();
			}
		}
	}
	
	@PreDestroy
	public void cleanup() {
		logger.info("Cleaning up jBPM Runtime Managers");
		processEngineService.closeAllRuntimeManagers();
	}
	
	@Inject
	TaskService taskService;

	/**
	 * Starts up a new ProcessInstance with the given ProcessId. The
	 * parameters Map is set into the context of the workflow.
	 *
	 */
    public long startProcess(String deploymentId, String processId, Map<String, Object> parameters)
			throws Exception {
		
	
		long processInstanceId = -1;
		try {
			KieSrampUtil kieSrampUtil = new KieSrampUtil();
			RuntimeManager runtimeManager = kieSrampUtil.getRuntimeManager(processEngineService, deploymentId);
			RuntimeEngine runtime = runtimeManager.getRuntimeEngine(EmptyContext.get());
			KieSession ksession = runtime.getKieSession();
			// start a new process instance
			ProcessInstance processInstance = ksession.startProcess(processId,
					parameters);
			processInstanceId = processInstance.getId();
			logger.info(Messages.i18n.format("ProcessBean.Started", processInstanceId)); //$NON-NLS-1$
			
		} catch (Exception e) {
			e.printStackTrace();
			
			throw e;
		}
		return processInstanceId;
	}
	
	public void signalProcess(long processInstanceId, String signalType, Object event) {
		KieSrampUtil kieSrampUtil = new KieSrampUtil();
		logger.info("signalling processInstance " + processInstanceId + " " + signalType); //$NON-NLS-1$ //$NON-NLS-2$
		String deploymentId = processEngineService.getProcessInstance(processInstanceId).getDeploymentId();
		RuntimeManager runtimeManager = kieSrampUtil.getRuntimeManager(processEngineService, deploymentId);
		RuntimeEngine runtime = runtimeManager.getRuntimeEngine(ProcessInstanceIdContext.get());
		KieSession ksession = runtime.getKieSession();
		ProcessInstance processInstance = ksession.getProcessInstance(processInstanceId);
		ksession.signalEvent(signalType, event,processInstance.getId());
	}

    public Collection<ProcessInstanceDesc> listProcessInstances() throws Exception {

		Collection<ProcessInstanceDesc> processInstances = null;
		
		//note that, if needed, the processEngineService can easily be extended with
		//methods that can filter by deploymentId and processId
		try {
			processInstances = processEngineService.getProcessInstances();
			for (ProcessInstanceDesc processInstanceDesc : processInstances) {
				logger.info(processInstanceDesc.getDeploymentId() + " " + 
							processInstanceDesc.getProcessName() + " " +
							processInstanceDesc.getId() 
							);
				System.out.println(".."); //$NON-NLS-1$
			}
		
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		return processInstances;

	}

    public void listProcessInstanceDetail(long processInstanceId) throws Exception {

		try {
			ProcessInstanceDesc processInstanceDesc = processEngineService.getProcessInstance(processInstanceId);
			if (processInstanceDesc != null) {
				System.out.println(processInstanceDesc.getProcessName());
				System.out.println(processInstanceDesc.getState());

				System.out.println(".."); //$NON-NLS-1$
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			
			throw e;
		}

	}

}
