/*
 * Copyright (c) 2018 Dafiti Group
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 */
package br.com.dafiti.hanger.service;

import br.com.dafiti.hanger.model.Job;
import br.com.dafiti.hanger.model.JobDetails;
import br.com.dafiti.hanger.model.JobParent;
import br.com.dafiti.hanger.option.Phase;
import br.com.dafiti.hanger.option.Scope;

import com.hp.gagawa.java.elements.A;
import com.hp.gagawa.java.elements.P;
import com.hp.gagawa.java.elements.Span;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class FlowService {

    private Map<String, Step> flow;
    private Map<String, String> nodes;
    private Set<JobDetails> reach;
    private Set<Integer> levels;
    private final JobParentService jobParentService;
    private final HttpServletRequest request;
    private final JobDetailsService jobDetailsService;
    private final JobNotificationService jobNotificationService;

    @Autowired
    public FlowService(
            JobParentService jobParentService,
            HttpServletRequest request,
            JobDetailsService jobDetailsService,
            JobNotificationService jobNotificationService) {

        this.jobParentService = jobParentService;
        this.request = request;
        this.jobDetailsService = jobDetailsService;
        this.jobNotificationService = jobNotificationService;
    }

    /**
     * Define Treant configuration and data.
     *
     * @param job Job
     * @param reverse Identify if is flow or propagation
     * @param expanded Identify if show the flow expanded
     * @return TreantJS script
     */
    public Flow getJobFlow(
            Job job,
            boolean reverse,
            boolean expanded) {

        List<String> key = new ArrayList<String>();
        List<String> value = new ArrayList<String>();
        StringBuilder configuration = new StringBuilder();

        Flow flow = this.getFlowStructure(job, reverse, expanded);

        // Gets its as a key value structure.
        flow.getStructure().entrySet().stream().forEach((entry) -> {
            key.add(entry.getValue().getId());
            value.add(entry.getValue().getVariable());
        });

        // Sets job clickable class. 
        configuration.append("var _class = 'flow-job-clickable'; ");

        // Sets node info.
        nodes.entrySet().stream().forEach((entry) -> {
            configuration.append("var " + entry.getKey() + " = '" + entry.getValue() + "'; ");
        });

        // Define TreantJS configuration.
        configuration.append("var config = {");
        configuration.append("        container: \"#flow\",");
        configuration.append("        rootOrientation: \"").append(reverse ? "WEST" : "EAST").append("\",");
        configuration.append("        levelSeparation: 50,");
        configuration.append("        siblingSeparation: 20,");
        configuration.append("        nodeAlign: \"LEFT\",");
        configuration.append("        node: { collapsable: true },");
        configuration.append("        connectors: {");
        configuration.append("            type: 'curve'");
        configuration.append("        }");
        configuration.append("    }");

        // Define the tree variable values.
        configuration.append(",");
        configuration.append(String.join(", ", value));

        // Define the tree variables.
        configuration.append(",");
        configuration.append(" chart_config = [");
        configuration.append("        config,");
        configuration.append(String.join(",", key));
        configuration.append("];");

        // Define the tree graph.
        configuration.append("new Treant( chart_config );");
        flow.setConfiguration(configuration.toString());
        return flow;
    }

    /**
     * Get the job flow.
     *
     * @param job Job
     * @param reverse Identify if is flow or propagation
     * @param expanded Identify if show the flow expanded
     * @return Flow structure as a map.
     */
    private Flow getFlowStructure(
            Job job,
            boolean reverse,
            boolean expanded) {

        Scope parentScope = null;
        String jobLineage = "";
        String parentLineage = "";

        flow = new TreeMap<>();
        nodes = new TreeMap<>();
        reach = new HashSet<>();
        levels = new HashSet<>();

        return getFlowStructure(
                job,
                null,
                false,
                0,
                reverse,
                expanded,
                jobLineage,
                parentLineage,
                parentScope);
    }

    /**
     * Get the job flow.
     *
     * @param job Job
     * @param parent Job Parent
     * @return Flow structure as a map.
     */
    private Flow getFlowStructure(
            Job job,
            Job parent,
            boolean isBlocker,
            int level, boolean reverse,
            boolean expanded,
            String jobLineage,
            String parentLineage,
            Scope parentScope) {

        Scope scope = null;
        HashSet<JobParent> childs = new HashSet<>();

        // Identify the step.
        level++;

        // Identify the lineage.
        jobLineage += "_" + job.getId();

        if (parent != null) {
            parentLineage += "_" + parent.getId();
        }

        // Identify the flow direction.
        if (reverse) {
            childs = jobParentService.findByParent(job);

            if (!childs.isEmpty()) {
                for (JobParent child : childs) {
                    getFlowStructure(
                            child.getJob(),
                            child.getParent(),
                            child.isBlocker(),
                            level,
                            reverse,
                            expanded,
                            jobLineage,
                            parentLineage,
                            parentScope);
                }
            }
        } else if (!job.getParent().isEmpty()) {
            for (JobParent jobParent : job.getParent()) {
                getFlowStructure(
                        jobParent.getParent(),
                        jobParent.getJob(),
                        jobParent.isBlocker(),
                        level,
                        reverse,
                        expanded,
                        jobLineage,
                        parentLineage,
                        jobParent.getScope());
            }
        }

        // Get the details getDetails each job.
        JobDetails jobDetails = jobDetailsService.getDetailsOf(job);

        // Identifies all job in the flow.
        reach.add(jobDetails);
        levels.add(level);

        // Define the job check-up link.
        A jobCheckupLink = new A();

        if (!job.getCheckup().isEmpty()) {
            jobCheckupLink.setHref(request.getRequestURL().toString().replace(request.getRequestURI(), request.getContextPath()) + "/checkup/job/" + job.getId() + "/list/");
            jobCheckupLink.setTarget("_blank");
            jobCheckupLink.setCSSClass("node-checkup");
            jobCheckupLink.appendText("CHECKUP");
        }

        // Define the job phase link.
        A jobPhaseLink = new A();

        if (!jobDetails.getPhase().equals(Phase.NONE)) {
            jobPhaseLink.setHref(request.getRequestURL().toString().replace(request.getRequestURI(), request.getContextPath()) + "/job/log/" + job.getId());
            jobPhaseLink.setTarget("_self");
            jobPhaseLink.setCSSClass("node-phase");
            jobPhaseLink.appendText(jobDetails.getPhase().toString());
        }

        // Identify optional scope.
        if (parentScope != null) {
            if (parentScope.equals(Scope.OPTIONAL)) {
                scope = Scope.OPTIONAL;
            } else if (parentScope.equals(Scope.PARTIAL)) {
                scope = Scope.PARTIAL;
            }
        }

        // Define the job name link.
        A jobNameLink = new A();
        jobNameLink.setHref(request.getRequestURL().toString().replace(request.getRequestURI(), request.getContextPath()) + "/job/view/" + job.getId());
        jobNameLink.setTarget("_blank");
        jobNameLink.setCSSClass("node-name");
        jobNameLink.appendText(job.getDisplayName());

        // Define the Jenkins link.
        A jobLink = new A();
        jobLink.setHref(job.getServer().getUrl() + "job/" + job.getName());
        jobLink.setTarget("_blank");
        jobLink.setCSSClass("node-link");
        jobLink.appendText("+");

        // Define the job build status span.
        String label = "";

        switch (jobDetails.getStatus()) {
            case WAITING:
            case RESTRICTED:
            case DISABLED:
                label = "label label-neutral";
                break;

            case PARTIAL:
            case SUCCESS:
            case UNSTABLE:
            case APPROVED:
                label = "label label-success";
                break;

            case QUEUED:
            case REBUILD:
            case RUNNING:
            case CHECKUP:
                label = "label label-primary";
                break;

            case UNHEALTHY:
            case BLOCKED:
            case FAILURE:
            case ABORTED:
            case DISAPPROVED:
            case ERROR:
                label = "label label-danger";
                break;
            default:
                break;
        }

        Span spanStatus = new Span();
        spanStatus.setCSSClass(label);
        spanStatus.setTitle(jobDetails.getStatus().toString());
        spanStatus.appendText(jobDetails.getStatus().toString());
        spanStatus.setId("span-status-" + job.getId());

        P jobStatus = new P();
        jobStatus.appendChild(spanStatus);

        // Define the job build time paragraph.
        P jobBuildTime = new P();
        jobBuildTime.appendText(jobDetails.getBuildTime());
        jobBuildTime.setCSSClass("node-time");

        // Define the node HTML content.
        StringBuilder node = new StringBuilder();
        node.append(jobNameLink.write());
        node.append(jobLink.write());

        if (jobPhaseLink.getTarget() != null) {
            node.append(jobPhaseLink.write());
        }

        if (jobCheckupLink.getTarget() != null) {
            node.append(jobCheckupLink.write());
        }

        node.append(jobStatus.write());
        node.append(jobBuildTime.write());

        // Unique key for InnerHTML.
        String innerHtmlKey = (scope == null) ? "__" + job.getId() : "__" + job.getId() + "_" + scope;

        // Identify root step.
        if (parent == null) {
            // Define the job scope paragraph.
            P jobBuildScope = new P();
            jobBuildScope.appendText(jobDetails.getScope() == null ? "" : job.getServer().getName() + (jobDetails.getScope().isEmpty() ? "" : " | " + jobDetails.getScope()));
            jobBuildScope.setCSSClass("node-scope");

            node.append(jobBuildScope.write());

            // Define unique innerHTML variable.
            if (!nodes.containsKey(innerHtmlKey)) {
                nodes.put(innerHtmlKey, node.toString());
            }

            // Identify steps.
            flow.put(StringUtils.leftPad(String.valueOf(level), 5, "0"),
                    new Step(jobLineage,
                            jobLineage + " = " + "{" + "     innerHTML: " + innerHtmlKey + "," + "     HTMLid: \""
                            + job.getId() + "\"," + "     HTMLclass: " + "_class" + "}"));
        } else {
            // Define the job scope paragraph.
            P jobBuildScope = new P();
            jobBuildScope
                    .appendText(
                            (scope == null
                                    ? (jobDetails.getScope() == null ? ""
                                    : job.getServer().getName() + (jobDetails.getScope().isEmpty() ? ""
                                    : " | " + jobDetails.getScope()))
                                    : (job.getServer().getName() + " | " + scope.toString()
                                    + (job.isRebuild() ? " | REBUILD "
                                    + (job.getWait() != 0 ? "once every " + job.getWait() + " min" : "")
                                    : "")))
                            + (parent.isRebuildBlocked() && isBlocker ? " | BLOCKER" : ""));
            jobBuildScope.setCSSClass("node-scope");
            node.append(jobBuildScope.write());

            // Define unique innerHTML variable.
            if (!nodes.containsKey(innerHtmlKey)) {
                nodes.put(innerHtmlKey, node.toString());
            }

            // Identify child steps.
            flow.put(
                    StringUtils.leftPad(String.valueOf(level), 5, "0") + StringUtils.leftPad(jobLineage, 100, "0")
                    + StringUtils.leftPad(parentLineage, 100, "0"),
                    new Step(jobLineage, jobLineage + " = " + "{ parent: " + parentLineage + ", " + "     innerHTML: "
                            + innerHtmlKey + "," + "     HTMLid: \"" + job.getId() + "\"," + "     collapsed: "
                            + (job.getParent().isEmpty() || reverse || expanded ? "false" : "true") + ","
                            + "     HTMLclass: _class }"));
        }

        return new Flow(flow, reach.size(), levels.size());
    }

    /**
     * Get flow warning list.
     *
     * @param job Job
     * @return Warning list
     */
    public List<JobDetails> getFlowWarning(Job job) {
        List<JobDetails> jobDetails = new ArrayList<JobDetails>();

        if (jobNotificationService.isNotified(job)) {
            jobDetails = jobDetailsService.getDetailsOf(jobNotificationService.getNotice(job));
        }

        return jobDetails;
    }

    /**
     * Represents a flow step.
     */
    private class Step {

        private String id;
        private String variable;

        public Step(String id, String variable) {
            this.id = id;
            this.variable = variable;
        }

        public String getId() {
            return id;
        }

        public String getVariable() {
            return variable;
        }
    }

    /**
     * Represents a flow and its statistics.
     *
     * @author valdiney.gomes
     *
     */
    public class Flow {

        private Map<String, Step> structure;
        private int reach;
        private int level;
        private String configuration;

        public Flow(Map<String, Step> structure, int reach, int level) {
            this.structure = structure;
            this.reach = reach;
            this.level = level;
        }

        public Map<String, Step> getStructure() {
            return structure;
        }

        public void setStructure(Map<String, Step> structure) {
            this.structure = structure;
        }

        public int getReach() {
            return reach;
        }

        public void setReach(int reach) {
            this.reach = reach;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public String getConfiguration() {
            return configuration;
        }

        public void setConfiguration(String configuration) {
            this.configuration = configuration;
        }
    }
}
