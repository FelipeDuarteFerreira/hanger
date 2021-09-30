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
package br.com.dafiti.hanger.controller;

import br.com.dafiti.hanger.exception.Message;
import br.com.dafiti.hanger.model.Job;
import br.com.dafiti.hanger.model.JobDetails;
import br.com.dafiti.hanger.service.FlowService;
import br.com.dafiti.hanger.service.FlowService.Flow;
import br.com.dafiti.hanger.service.JobApprovalService;
import br.com.dafiti.hanger.service.JobDetailsService;
import br.com.dafiti.hanger.service.ServerService;
import br.com.dafiti.hanger.service.SubjectDetailsService;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author Valdiney V GOMES
 */
@Controller
public class FlowController {

    private final FlowService flowService;
    private final JobApprovalService jobApprovalService;
    private final SubjectDetailsService subjectDetailsService;
    private final ServerService serverService;
    private final JobDetailsService jobDetailsService;

    @Autowired
    public FlowController(
            FlowService flowService,
            JobApprovalService jobApprovalService,
            SubjectDetailsService subjectDetailsService,
            ServerService serverService,
            JobDetailsService jobDetailsService) {

        this.flowService = flowService;
        this.jobApprovalService = jobApprovalService;
        this.subjectDetailsService = subjectDetailsService;
        this.serverService = serverService;
        this.jobDetailsService = jobDetailsService;
    }

    /**
     * Show the job flow.
     *
     * @param expanded Identify if should show the flow expanded
     * @param principal logged user
     * @param job Job
     * @param model Model
     * @return Flow display
     */
    @GetMapping(path = "/flow/job/{id}")
    public String flow(
            @RequestParam(value = "expanded", required = false) boolean expanded,
            Principal principal,
            @PathVariable("id") Job job,
            Model model) {

        if (job != null) {
        	Flow flow = flowService.getJobFlow(job, false, expanded);
        	
            model.addAttribute("job", job);
            model.addAttribute("subjectSummary", subjectDetailsService.getSummaryOf(job.getSubject()));
            model.addAttribute("warnings", flowService.getFlowWarning(job));
            model.addAttribute("chart", flow.getConfiguration());
            model.addAttribute("reach", flow.getReach());
            model.addAttribute("level", flow.getLevel());
            model.addAttribute("approval", this.jobApprovalService.hasApproval(job, principal));
            model.addAttribute("servers", this.serverService.list());
        }

        return "flow/display";
    }

    /**
     * Show the job propagation flow.
     *
     * @param principal logged user
     * @param job Job
     * @param model Model
     * @return Flow display
     */
    @GetMapping(path = {"/flow/propagation/job/{id}", "/propagation/job/{id}"})
    public String propagation(
            Principal principal,
            @PathVariable("id") Job job,
            Model model) {

        if (job != null) {
        	Flow flow = flowService.getJobFlow(job, true, false);

            model.addAttribute("job", job);
            model.addAttribute("subjectSummary", subjectDetailsService.getSummaryOf(job.getSubject()));
            model.addAttribute("chart", flow.getConfiguration());
            model.addAttribute("reach", flow.getReach());
            model.addAttribute("level", flow.getLevel());
            model.addAttribute("approval", this.jobApprovalService.hasApproval(job, principal));
            model.addAttribute("servers", this.serverService.list());
        }

        return "flow/display";
    }

    /**
     * Show the job flow warning modal.
     *
     * @param job Job
     * @param model Model
     * @return flow modal
     */
    @GetMapping(path = "/flow/warning/{id}")
    public String warning(
            @PathVariable("id") Job job,
            Model model) {

        if (job != null) {
            model.addAttribute("warnings", flowService.getFlowWarning(job));
        }

        return "flow/modalWarning::warning";
    }
    
    /**
     * Show the job parent modal.
     *
     * @param job
     * @param model
     * @return
     */
    @GetMapping(path = "/flow/modal/parents/details/{id}")
    public String jobRelativesDetails(
            @PathVariable(value = "id") Job job,
            Model model) {

        try {
            List<JobDetails> jobDetails = new ArrayList<JobDetails>();

            job.getParent().stream().forEach((parent) -> {
                jobDetails.add(jobDetailsService.getDetailsOf(parent.getParent()));
            });

            model.addAttribute("jobDetails", jobDetails
                    .stream()
                    .sorted((a, b) -> (a.getJob().getName().compareTo(b.getJob().getName())))
                    .collect(Collectors.toList()));
            model.addAttribute("job", job);
        } catch (Exception ex) {
            model.addAttribute("errorMessage", "Fail getting parameters " + new Message().getErrorMessage(ex));
        }

        return "flow/modalParentsDetails::parentsDetails";
    }
}
