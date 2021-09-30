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
import br.com.dafiti.hanger.model.JobBuild;
import br.com.dafiti.hanger.model.JobStatus;
import br.com.dafiti.hanger.option.Flow;
import br.com.dafiti.hanger.option.Phase;
import br.com.dafiti.hanger.option.Status;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class JobNotificationService {

    private final JobService jobService;
    private final JobBuildStatusService jobBuildStatusService;
    private final SlackService slackService;
    private final Multimap<Job, Job> warning;

    @Autowired
    public JobNotificationService(
            JobService jobService,
            JobBuildStatusService jobBuildStatusService,
            SlackService slackService) {

        this.jobService = jobService;
        this.jobBuildStatusService = jobBuildStatusService;
        this.slackService = slackService;
        this.warning = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
    }

    /**
     * Identify if a job has notification.
     *
     * @param job Job
     * @return Identify if a job has warning
     */
    public boolean isNotified(Job job) {
        return warning.containsKey(job);
    }

    /**
     * Get job notification.
     *
     * @param job Job
     * @return Job warning list
     */
    public List<Job> getNotice(Job job) {
        return (List<Job>) warning.get(job);
    }

    /**
     * Notify.
     *
     * @param job Job
     * @param slack Identity if send message to slack.
     */
    public void notify(Job job, boolean slack) {
        this.notify(job, slack, false);
    }

    /**
     * Notify.
     *
     * @param job Job
     * @param slack Identity if send message to slack.
     * @param setup Identify if is a setup.
     */
    public void notify(Job job, boolean slack, boolean setup) {
        Set<Job> pendencies = new HashSet();
        StringBuilder message = new StringBuilder();

        if (job.isEnabled()) {
            JobStatus jobStatus = job.getStatus();
            
            if (jobStatus != null) {
                JobBuild jobBuild = jobStatus.getBuild();

                if (jobBuild != null) {
                    if ((jobStatus.getFlow().equals(Flow.NORMAL) || jobStatus.getFlow().equals(Flow.APPROVED))
                            && jobBuild.getPhase().equals(Phase.FINALIZED)
                            && jobBuild.getStatus().equals(Status.SUCCESS)
                            && !setup) {

                        //Remove a warnig in case of success.  
                        if (!warning.isEmpty()
                                && warning.containsValue(job)) {

                            jobService.getPropagation(job, false).stream().forEach((child) -> {
                                warning.remove(child, job);
                            });
                        }

                        if (slack) {
                            //Identifies if job notification is enabled.
                            if (job.isNotify()) {
                                if (job.getParent().isEmpty()) {
                                    message
                                            .append(":star-struck: The job *")
                                            .append(job.getDisplayName())
                                            .append("* finished *successfully*");
                                } else {
                                    job.getParent().stream().forEach((parent) -> {
                                        if (!jobBuildStatusService.isBuilt(parent.getParent())) {
                                            pendencies.add(parent.getParent());
                                        }
                                    });

                                    if (pendencies.isEmpty()) {
                                        message
                                                .append(":star-struck: The job *")
                                                .append(job.getDisplayName())
                                                .append("* and its dependencies has finished *successfully*");
                                    } else {
                                        message
                                                .append(":worried: The job *")
                                                .append(job.getDisplayName())
                                                .append("* has finished *successfully*, but the following dependencies was not finished yet: ");

                                        pendencies.stream().forEach((parent) -> {
                                            message
                                                    .append("\n         *")
                                                    .append(parent.getDisplayName())
                                                    .append("* ");
                                        });
                                    }

                                    if (warning.containsKey(job)) {
                                        message
                                                .append("\n(")
                                                .append(warning.get(job).size())
                                                .append(" warning(s) in the job flow!)");
                                    }
                                }
                                slackService.send(message.toString(), job.getChannel());
                            }
                        }
                    } else if (jobStatus.getFlow().equals(Flow.ERROR)
                            || (jobBuild.getPhase().equals(Phase.FINALIZED) && (jobBuild.getStatus().equals(Status.FAILURE) || jobBuild.getStatus().equals(Status.ABORTED)))) {

                        //Put a warnig in case of error. 
                        jobService.getPropagation(job, false).stream().forEach((child) -> {
                            warning.remove(child, job);
                            warning.put(child, job);
                        });

                        if (slack) {
                            //Identifies if job notification is enabled.
                            if (job.isNotify()) {
                                message
                                        .append(":fire: Something wrong happened to the job *")
                                        .append(job.getDisplayName())
                                        .append("*");

                                slackService.send(message.toString(), job.getChannel());
                            }
                        }
                    }
                }
            }
        }
    }
}
