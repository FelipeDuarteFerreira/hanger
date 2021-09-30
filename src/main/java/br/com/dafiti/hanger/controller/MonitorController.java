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

import br.com.dafiti.hanger.model.Job;
import br.com.dafiti.hanger.model.JobDetails;
import br.com.dafiti.hanger.service.JobDetailsService;
import br.com.dafiti.hanger.model.Subject;
import br.com.dafiti.hanger.service.JobService;
import br.com.dafiti.hanger.service.SubjectDetailsService;
import br.com.dafiti.hanger.service.SubjectService;
import br.com.dafiti.hanger.service.UserService;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author Guilherme Almeida
 * @author Valdiney V GOMES
 */
@Controller
@RequestMapping(path = {"/", "/home"})
public class MonitorController {

    private final JobService jobService;
    private final SubjectService subjectService;
    private final JobDetailsService jobDetailsService;
    private final SubjectDetailsService subjectDetailsService;
    private final UserService userService;

    private static final Logger LOG = LogManager.getLogger(MonitorController.class.getName());

    @Autowired
    public MonitorController(
            JobService jobService,
            SubjectService subjectService,
            JobDetailsService jobDetails,
            SubjectDetailsService subjectDetailsService,
            UserService userService) {

        this.jobService = jobService;
        this.subjectService = subjectService;
        this.jobDetailsService = jobDetails;
        this.subjectDetailsService = subjectDetailsService;
        this.userService = userService;
    }

    /**
     * Show the subjectDetail page.
     *
     * @param model Model
     * @return Home template
     */
    @GetMapping(path = {"/", "/home"})
    public String subjectDetail(Model model) {
        model.addAttribute("subjectDetails", subjectDetailsService.getDetailsOf(subjectService.findBySubscription()));
        modelDefault(model);
        return "monitor/monitor";
    }

    /**
     * List jobs by subject.
     *
     * @param principal Principal
     * @param model Model
     * @param request HttpServletRequest
     * @param id Subject ID
     * @return Home template
     */
    @GetMapping("/detail/{id}")
    public String jobDetail(
            Principal principal,
            Model model,
            HttpServletRequest request,
            @PathVariable(value = "id") String id) {

        Subject subject = new Subject();
        List<Job> jobs = new ArrayList();

        if (StringUtils.isNumeric(id)) {
            subject = subjectService.load(Long.valueOf(id));
            if (subject != null) {
                jobs = jobService.findBySubjectOrderByName(subject);
            }
        }

        modelDefault(model);
        modelDetails(model, subject, principal, jobs);

        return "monitor/monitor";
    }

    @PostMapping("/detail/filter")
    public String jobFilter(
            Principal principal,
            Model model,
            HttpServletRequest request,
            @RequestParam(value = "status", required = false, defaultValue = "") List<String> status) {

        modelDefault(model);
        modelDetails(model, new Subject(), principal, (List) jobService.list(), status);

        return "monitor/monitor";
    }

    /**
     * Model details
     *
     * @param model Model
     * @param subject Subject
     * @param principal Principal
     * @param jobs Jobs
     */
    private void modelDetails(
            Model model,
            Subject subject,
            Principal principal,
            List<Job> jobs) {

        this.modelDetails(model, subject, principal, jobs, new ArrayList());
    }

    /**
     * Model details
     *
     * @param model Model
     * @param subject Subject
     * @param principal Principal
     * @param jobs Jobs
     * @param status Status
     */
    private void modelDetails(
            Model model,
            Subject subject,
            Principal principal,
            List<Job> jobs,
            List<String> status) {

        model.addAttribute("currentSubject", subject);

        if (principal != null || jobs != null) {
            Map<String, List<JobDetails>> swimlanes = new TreeMap();
            Map<String, String> rules = subject.getSwimlane();
            List<JobDetails> jobDetails = jobDetailsService.getDetailsOf(jobs, principal);

            //Sorts details. 
            jobDetails = jobDetails
                    .stream()
                    .parallel()
                    .filter(a -> status.isEmpty() || status.contains(a.getStatus().toString()))
                    .sorted((a, b) -> (a.getJob().getName().compareTo(b.getJob().getName())))
                    .sorted((a, b) -> a.getStatus().toString().compareTo(b.getStatus().toString())).collect(Collectors.toList());

            //Classify each job detail in a swimlane. 
            for (JobDetails jobDetail : jobDetails) {
                boolean classified = false;

                //Identifies if the job detail match any swimlanes criteria. 
                for (Entry<String, String> entry : rules.entrySet()) {
                    if (jobDetail.getJob().getName().matches(entry.getValue())) {
                        if (swimlanes.containsKey(entry.getKey())) {
                            swimlanes.get(entry.getKey()).add(jobDetail);
                        } else {
                            List<JobDetails> details = new ArrayList();
                            details.add(jobDetail);
                            swimlanes.put(entry.getKey(), details);
                        }

                        classified = true;
                        break;
                    }
                }

                //Classify in ALL job detail that dont match any criteria.
                if (!classified) {
                    if (swimlanes.containsKey("ALL")) {
                        swimlanes.get("ALL").add(jobDetail);
                    } else {
                        List<JobDetails> details = new ArrayList();
                        details.add(jobDetail);
                        swimlanes.put("ALL", details);
                    }
                }
            }

            model.addAttribute("rules", rules);
            model.addAttribute("swimlanes", swimlanes);
        }
    }

    /**
     * Add a job into a subject.
     *
     * @param jobsID Job list ID
     * @param subject Subject
     * @param model Model
     * @return Redirect to subject template
     */
    @PostMapping(path = "/detail/add/{id}", params = {"addJobs"})
    public String addSubject(
            @RequestParam(value = "jobList", required = false, defaultValue = "") String jobsID,
            @PathVariable(value = "id") Subject subject,
            Model model) {

        if (!jobsID.isEmpty()) {
            for (String jobId : jobsID.split(",")) {
                Job job = jobService.load(Long.parseLong(jobId));

                if (job != null) {
                    if (!job.getSubject().contains(subject)) {
                        job.addSubject(subject);
                        jobService.save(job);
                    }
                }
            }
        }

        model.addAttribute("currentSubject", subject);
        modelDefault(model);

        return "redirect:/detail/" + subject.getId();
    }

    /**
     * Remove a subject from a job.
     *
     * @param job Job
     * @param subject Subject
     * @param model Model
     * @return Redirect to subject template
     */
    @GetMapping(path = "/detail/remove/{jobID}/{subjectID}")
    public String removeSubject(
            @PathVariable(value = "jobID") Job job,
            @PathVariable(value = "subjectID") Subject subject,
            Model model
    ) {

        job.getSubject().remove(subject);
        jobService.save(job);

        model.addAttribute("currentSubject", subject);
        modelDefault(model);

        return "redirect:/detail/" + subject.getId();
    }

    /**
     * Job list modal.
     *
     * @param subject Subject
     * @param model Model
     * @return Modal template
     */
    @GetMapping(path = "/detail/modal/{subjectID}")
    public String jobModal(
            @PathVariable(value = "subjectID") Subject subject,
            Model model
    ) {

        model.addAttribute("subject", subject);
        model.addAttribute("jobs", jobService.list());

        return "monitor/modalJobList::job";
    }

    /**
     * Model default attribute.
     *
     * @param model Model
     */
    private void modelDefault(Model model) {
        model.addAttribute("all", jobService.count());
        model.addAttribute("subjectSummary", subjectDetailsService.getSummaryOf(subjectService.findBySubscription()));
        model.addAttribute("loggedIn", userService.getLoggedIn());
        model.addAttribute("subjects", subjectService.findBySubscription());
    }
}
