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
import br.com.dafiti.hanger.option.Scope;
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
    private final JobBuildStatusService jobBuildStatusService;

    @Autowired
    public JobDetailsService(
            JobBuildService jobBuildService,
            JobNotificationService jobNotificationService,
            RetryService retryService,
            JobApprovalService jobApprovalService,
            JobBuildStatusService jobBuildStatusService) {

        this.jobBuildService = jobBuildService;
        this.jobNotificationService = jobNotificationService;
        this.retryService = retryService;
        this.jobApprovalService = jobApprovalService;
        this.jobBuildStatusService = jobBuildStatusService;
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

        if (!job.isEnabled()) {
            if (!job.isEnabled()) {
                status = Status.DISABLED;
                building
                        .append("Modified by ")
                        .append(job.getModifiedBy())
                        .append(" at ")
                        .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(job.getUpdatedAt()));
            }
        } else {
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

                    //Identifies if the job match a time restriction. 
                    if (!jobBuildStatusService.isTimeRestrictionMatch(job.getTimeRestriction())) {
                        status = Status.RESTRICTED;
                        phase = Phase.NONE;
                        building.append("Last build at ")
                                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(jobBuild.getDate()));
                        //Identifies if is a current day build.  
                    } else if (today || (yesterday && eagerness)) {
                        switch (jobStatus.getFlow()) {
                            case QUEUED:
                            case REBUILD:
                                status = Status.valueOf(jobStatus.getFlow().toString());
                                phase = Phase.NONE;
                                building
                                        .append("Last build ")
                                        .append((yesterday ? "( - " + tolerance + " hours) yesterday" : "today"))
                                        .append(" at ")
                                        .append(new SimpleDateFormat("HH:mm:ss").format(jobBuild.getDate()));
                                break;

                            case UNHEALTHY:
                            case BLOCKED:
                            case APPROVED:
                            case DISAPPROVED:
                            case ERROR:
                            case CHECKUP:
                                status = Status.valueOf(jobStatus.getFlow().toString());
                                building
                                        .append((yesterday ? "( - " + tolerance + " hours ) Yesterday" : "Today"))
                                        .append(" at ")
                                        .append(new SimpleDateFormat("HH:mm:ss").format(jobBuild.getDate()));
                                break;

                            default:
                                //Identifies if the build is running. 
                                if (jobBuild.getPhase().equals(Phase.FINALIZED)
                                        || (jobBuild.getStatus().equals(Status.FAILURE) || jobBuild.getStatus().equals(Status.ABORTED))) {

                                    status = jobBuild.getStatus();

                                    if (jobBuild.getStatus().equals(Status.SUCCESS)) {
                                        //Identifies it was builded partially.
                                        if (jobStatus.getScope().equals(Scope.PARTIAL)) {
                                            status = Status.PARTIAL;
                                        } else {
                                            //Identifies if fail at least once today. 
                                            if (jobStatus.getFailureTimestamp() != null) {
                                                if (Days.daysBetween(new LocalDate(jobStatus.getFailureTimestamp()), new LocalDate()).getDays() == 0) {
                                                    status = Status.UNSTABLE;
                                                }
                                            }
                                        }
                                    }
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
                    } else if (jobStatus.getFlow().equals(Flow.REBUILD)
                            || jobStatus.getFlow().equals(Flow.QUEUED)) {
                        status = Status.valueOf(jobStatus.getFlow().toString());
                        phase = Phase.NONE;
                        building
                                .append("Last build at ")
                                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(jobBuild.getDate()));
                    } else {
                        //Identify the last build flow. 
                        if (!jobStatus.getFlow().equals(Flow.NORMAL)
                                && !jobStatus.getFlow().equals(Flow.TRANSIENT)) {

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
                } else if (jobStatus.getFlow().equals(Flow.REBUILD)
                        || jobStatus.getFlow().equals(Flow.QUEUED)) {
                    //Identify the first build of a job. 
                    status = Status.valueOf(jobStatus.getFlow().toString());
                    phase = Phase.NONE;
                    building
                            .append("Building now");
                } else if (!jobBuildStatusService.isTimeRestrictionMatch(job.getTimeRestriction())) {
                    //Identifies if the job match a time restriction. 
                    status = Status.RESTRICTED;
                    phase = Phase.NONE;
                    building.append("Never build");
                } else {
                    try {
                        status = Status.valueOf(jobStatus.getFlow().toString());
                    } catch (Exception ex) {
                    }
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
                        .append(job.isAnyScope() ? " FREE SCOPE " : "")
                        .append(job.isRebuild() ? " REBUILD " + (job.isRebuildBlocked() ? "after all blockers ready " : "") + (job.getWait() != 0 ? "once every " + job.getWait() + " min" : "") : "")
                        .append((job.getTimeRestriction() == null || job.getTimeRestriction().isEmpty()) ? "" : " " + job.getTimeRestrictionDescription().toLowerCase());

                //Identify the number of build retries. 
                if (retryService.exists(job)
                        && retryService.get(job) != 0
                        && job.getRetry() != 0) {

                    building
                            .append(" (")
                            .append(retryService.get(job))
                            .append(" of ")
                            .append(job.getRetry())
                            .append(" tries)");
                }
            }
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
