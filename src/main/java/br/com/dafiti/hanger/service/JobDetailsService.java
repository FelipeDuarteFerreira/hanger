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
import br.com.dafiti.hanger.model.JobDetails;
import br.com.dafiti.hanger.model.JobStatus;
import br.com.dafiti.hanger.option.Flow;
import br.com.dafiti.hanger.option.Phase;
import br.com.dafiti.hanger.option.Status;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class JobDetailsService {

    private final JobNotificationService jobNotificationService;
    private final JobBuildService jobBuildService;
    private final RetryService retryService;
    private final JobApprovalService jobApprovalService;

    @Autowired
    public JobDetailsService(
            JobBuildService jobBuildService,
            JobNotificationService jobNotificationService,
            RetryService retryService,
            JobApprovalService jobApprovalService) {

        this.jobBuildService = jobBuildService;
        this.jobNotificationService = jobNotificationService;
        this.retryService = retryService;
        this.jobApprovalService = jobApprovalService;
    }

    /**
     * Get the details getDetails a job.
     *
     * @param job Job
     * @return Job details
     */
    public JobDetails getDetailsOf(Job job) {
        int number = 0;
        Flow flow = Flow.NORMAL;
        Phase phase = Phase.NONE;
        Status status = Status.WAITING;
        StringBuilder scope = new StringBuilder();
        StringBuilder building = new StringBuilder();
        List<Job> notice = jobNotificationService.getNotice(job);

        JobStatus jobStatus = job.getStatus();

        if (jobStatus != null) {
            JobBuild jobBuild = jobStatus.getBuild();

            if (jobBuild != null) {
                int tolerance = job.getTolerance();
                int days = Days.daysBetween(new LocalDate(jobBuild.getDate()), new LocalDate()).getDays();
                boolean today = (days == 0);
                boolean yesterday = (days == 1);
                boolean eagerness = false;

                number = jobBuild.getNumber();

                if (!today) {
                    //Identify if has tolerance. 
                    if (tolerance != 0) {
                        //Identify if the job is in the tolerance limit. 
                        eagerness = (Days.daysBetween(
                                new LocalDate(new DateTime(jobBuild.getDate()).plusHours(tolerance)),
                                new LocalDate()).getDays() == 0);
                    }
                }

                //Identify if is a current day build.  
                if (today || (yesterday && eagerness)) {
                    switch (jobStatus.getFlow()) {
                        case REBUILD:
                            status = Status.REBUILD;
                            phase = Phase.NONE;
                            building
                                    .append("Building, last build ")
                                    .append((yesterday ? "( - " + tolerance + " hours) yesterday" : "today"))
                                    .append(" at ")
                                    .append(new SimpleDateFormat("HH:mm:ss").format(jobBuild.getDate()));
                            break;

                        case UNHEALTHY:
                        case BLOCKED:
                        case APPROVED:
                        case DISAPPROVED:
                        case ERROR:
                            status = Status.valueOf(jobStatus.getFlow().toString());
                            building
                                    .append((yesterday ? "( - " + tolerance + " hours ) Yesterday" : "Today"))
                                    .append(" at ")
                                    .append(new SimpleDateFormat("HH:mm:ss").format(jobBuild.getDate()));
                            break;

                        default:
                            //Identify if the build is running. 
                            if (jobBuild.getPhase().equals(Phase.FINALIZED)
                                    || (jobBuild.getStatus().equals(Status.FAILURE)
                                    || jobBuild.getStatus().equals(Status.ABORTED))) {

                                status = jobBuild.getStatus();
                            } else {
                                status = Status.RUNNING;
                            }

                            phase = jobBuild.getPhase();

                            building
                                    .append((yesterday ? "( - " + tolerance + " hours ) Yesterday" : "Today"))
                                    .append(" at ")
                                    .append(new SimpleDateFormat("HH:mm:ss").format(jobBuild.getDate()));
                            break;
                    }
                } else if (jobStatus.getFlow().equals(Flow.REBUILD)) {
                    status = Status.REBUILD;
                    phase = Phase.NONE;
                    building
                            .append("Building, last build at ")
                            .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(jobBuild.getDate()));
                } else {
                    //Identify the last build flow. 
                    if (!jobStatus.getFlow().equals(Flow.NORMAL)
                            && !jobStatus.getFlow().equals(Flow.TRANSIENT)
                            && !jobStatus.getFlow().equals(Flow.UPSTREAM)) {

                        status = Status.valueOf(jobStatus.getFlow().toString());
                    }

                    //Identify the number of days, time and duration of the last build. 
                    building
                            .append(Math.abs(days))
                            .append(" days ago at ")
                            .append(new SimpleDateFormat("HH:mm:ss").format(jobBuild.getDate()));
                }

                //Identify the duration of a running build. 
                if (jobBuild.getPhase().equals(Phase.STARTED)
                        && (jobBuild.getDate() != null)) {

                    Period period = new Period(new DateTime(jobBuild.getDate()), new DateTime(new Date()));

                    building
                            .append(" running for ")
                            .append(StringUtils.leftPad(String.valueOf(period.getHours()), 2, "0"))
                            .append(":")
                            .append(StringUtils.leftPad(String.valueOf(period.getMinutes()), 2, "0"))
                            .append(":")
                            .append(StringUtils.leftPad(String.valueOf(period.getSeconds()), 2, "0"));
                } else {
                    //Identify the duration of finished build. 
                    try {
                        building
                                .append(" in ")
                                .append(jobBuildService.findJobBuildTime(job, number));
                    } catch (Exception ex) {
                    }
                }
            } else if (jobStatus.getFlow().equals(Flow.REBUILD)) {
                //Identify the first build of a job. 
                status = Status.REBUILD;
                phase = Phase.NONE;
                building
                        .append("Building now");
            }

            //The QUEUED and COMPLETED phases should be ignored. 
            if (phase.equals(Phase.QUEUED)) {
                phase = Phase.STARTED;
            } else if (phase.equals(Phase.COMPLETED)) {
                phase = Phase.FINALIZED;
            }

            //Identify the job flow. 
            flow = jobStatus.getFlow();

            //Identifi if the job scope. 
            scope
                    .append(jobStatus.getScope().toString())
                    .append((job.isRebuild() ? ", Rebuildable" + (job.getWait() != 0 ? " once every " + job.getWait() + " min" : "") : ""));

            //Identify the number of build retries. 
            if (retryService.exists(job)
                    && (job.getRetry() != 0)
                    && (retryService.get(job) >= job.getRetry())) {

                building
                        .append(" ( ")
                        .append(job.getRetry())
                        .append("x )");
            }
        }

        //Identify is the job is disabled.
        if (!job.isEnabled()) {
            status = Status.DISABLED;
        }

        //Identify if the job was never built.
        if (building.length() == 0) {
            building
                    .append("Never build");
        }

        return new JobDetails(
                job,
                status,
                scope.toString(),
                flow,
                phase,
                number,
                building.toString(),
                notice);
    }

    /**
     * Get the details of a list of jobs.
     *
     * @param jobs Job list
     * @return Job details.
     */
    public List<JobDetails> getDetailsOf(List<Job> jobs) {
        return this.getDetailsOf(jobs, null);
    }

    /**
     * Get the details of a list of jobs.
     *
     * @param jobs Job list
     * @param principal Logged user.
     * @return Job details.
     */
    public List<JobDetails> getDetailsOf(List<Job> jobs, Principal principal) {
        List<JobDetails> jobDetails = new ArrayList<>();

        jobs.stream().forEach((job) -> {
            JobDetails details = this.getDetailsOf(job);

            if (principal != null) {
                details.setApproval(this.jobApprovalService.hasApproval(job, principal));
            }

            jobDetails.add(details);
        });

        return jobDetails;
    }
}
