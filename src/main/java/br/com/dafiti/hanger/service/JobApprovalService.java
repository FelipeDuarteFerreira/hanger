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
import br.com.dafiti.hanger.model.JobApproval;
import br.com.dafiti.hanger.model.Role;
import br.com.dafiti.hanger.model.User;
import br.com.dafiti.hanger.option.Flow;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import br.com.dafiti.hanger.repository.JobApprovalRepository;
import java.security.Principal;
import java.util.Date;
import java.util.HashSet;

/**
 *
 * @author Guilherme ALMEIDA
 * @author Helio Leal
 */
@Service
public class JobApprovalService {

    private final JobApprovalRepository jobApprovalRepository;
    private final UserService userService;
    private final JobService jobService;
    private final RetryService retryService;
    private final JenkinsService jenkinsService;
    private final JobStatusService jobStatusService;
    private final JobNotificationService jobNotificationService;
    private final JobBuildPushService jobBuildPushService;
    private final RoleService roleService;

    @Autowired
    public JobApprovalService(
            JobApprovalRepository jobApprovalRepository,
            UserService userService,
            JobService jobService,
            RetryService retryService,
            JenkinsService jenkinsService,
            JobStatusService jobStatusService,
            JobNotificationService jobNotificationService,
            JobBuildPushService jobBuildPushService,
            RoleService roleService) {

        this.jobApprovalRepository = jobApprovalRepository;
        this.userService = userService;
        this.jobService = jobService;
        this.retryService = retryService;
        this.jenkinsService = jenkinsService;
        this.jobStatusService = jobStatusService;
        this.jobNotificationService = jobNotificationService;
        this.jobBuildPushService = jobBuildPushService;
        this.roleService = roleService;
    }

    public Iterable<JobApproval> list() {
        return jobApprovalRepository.findAll();
    }

    public void save(JobApproval jobApproval) {
        jobApprovalRepository.save(jobApproval);
    }

    public void delete(JobApproval jobApproval) {
        jobApprovalRepository.delete(jobApproval);
    }

    public List<JobApproval> findByJobOrderByCreatedAtDesc(Job job) {
        return jobApprovalRepository.findByJobOrderByCreatedAtDesc(job);
    }

    public HashSet<Job> findPending() {
        return jobApprovalRepository.findPending();
    }

    public void cleaneup(Date expiration) {
        jobApprovalRepository.deleteByDateBefore(expiration);
    }

    /**
     * Approve or disapprove a job.
     *
     * @param jobApproval jobApproval object.
     * @param job job
     * @param approve approve or not.
     */
    public void approve(
            JobApproval jobApproval,
            Job job,
            boolean approve) {

        // Update the approval. 
        jobApproval.setApproved(approve);
        jobApproval.setJob(job);
        jobApproval.setUser(this.userService.findByUsername(SecurityContextHolder.getContext().getAuthentication().getName()));
        this.save(jobApproval);

        // Update the job flow. 
        if (approve) {
            job.getStatus().setFlow(Flow.APPROVED);
        } else {
            job.getStatus().setFlow(Flow.DISAPPROVED);
        }

        this.jobService.save(job);

        // Update the retry service. 
        this.retryService.remove(job);
    }

    /**
     * When job is approved and flow is UNHEALTHY, then trigger children,
     * otherwise if approved and job is BLOCKED, then build the job itself.
     *
     * @param job job object
     * @param flow flow object
     *
     * @return everything ran with success.
     */
    public boolean push(
            Job job,
            Flow flow) {

        if (flow.equals(Flow.UNHEALTHY)) {
            //Notify that the job was approved. 
            this.jobNotificationService.notify(job, false);

            //Push the childs build if is a postvalidation.
            jobBuildPushService.push(job);

        } else if (flow.equals(Flow.BLOCKED)) {
            try {
                //Build the job if is a prevalidation.
                if (!jenkinsService.build(job)) {
                    jobStatusService.updateFlow(job.getStatus(), Flow.ERROR);
                    jobNotificationService.notify(job, true);

                } else {
                    jobStatusService.updateFlow(job.getStatus(), Flow.QUEUED);
                }
            } catch (Exception ex) {
                return false;
            }
        }

        return true;
    }

    /**
     * Identify if a job has pending approval.
     *
     * @param job job
     * @param principal logged user.
     *
     * @return if user can approve job.
     */
    public boolean hasApproval(Job job, Principal principal) {
        boolean hasApproval = false;

        // Check if user is logged.
        if (principal != null) {
            User user = userService.findByUsername(principal.getName());

            if (user != null) {
                Role userRole = roleService.findByName("USER");

                // Check if user is ADMIN or HERO.
                if ((!user.getRoles().contains(userRole))
                        || (job.getApprover() != null && job.getApprover().getUsername().equals(user.getUsername()))) {

                    // Check if job is UNHEALTHY or BLOCKED.
                    if (job.getStatus().getFlow().equals(Flow.UNHEALTHY)
                            || job.getStatus().getFlow().equals(Flow.BLOCKED)) {
                        hasApproval = true;
                    }
                }
            }
        }

        return hasApproval;
    }
}
