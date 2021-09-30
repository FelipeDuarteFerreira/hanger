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

import br.com.dafiti.hanger.model.SubjectSummary;
import br.com.dafiti.hanger.model.Subject;
import br.com.dafiti.hanger.model.SubjectDetails;
import br.com.dafiti.hanger.model.JobStatus;
import br.com.dafiti.hanger.model.Job;
import br.com.dafiti.hanger.model.JobBuild;
import br.com.dafiti.hanger.option.Flow;
import br.com.dafiti.hanger.option.Phase;
import br.com.dafiti.hanger.option.Status;
import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author guilherme.almeida
 */
@Service
public class SubjectDetailsService {

    private final JobNotificationService jobNotificationService;
    private final JobService jobService;

    @Autowired
    public SubjectDetailsService(
            JobNotificationService jobNotificationService,
            JobService jobService) {

        this.jobNotificationService = jobNotificationService;
        this.jobService = jobService;

    }

    /**
     * Get the defail of a subject.
     *
     * @param subject Subject.
     * @param subjectJobs Subject jobs.
     * @return Subject details.
     */
    public SubjectDetails getDetailsOf(
            Subject subject,
            List<Job> subjectJobs) {

        int building = 0;
        int success = 0;
        int warning = 0;
        int failure = 0;
        int total = 0;

        for (Job job : subjectJobs) {
            if (job.isEnabled()) {
                total += 1;
                JobStatus jobStatus = job.getStatus();

                if (jobStatus != null) {
                    JobBuild jobBuild = jobStatus.getBuild();

                    //Identify building jobs.
                    if (jobStatus.getFlow().equals(Flow.QUEUED)
                            || jobStatus.getFlow().equals(Flow.REBUILD)) {
                        building += 1;
                    } else if (jobStatus.getFlow().equals(Flow.ERROR)) {
                        failure += 1;
                    } else if (jobBuild != null) {
                        int lastBuild = Days.daysBetween(
                                new LocalDate(new DateTime(jobBuild.getDate()).plusHours(job.getTolerance())),
                                new LocalDate()).getDays();

                        if (lastBuild == 0) {
                            //Identify running jobs.
                            if ((jobBuild.getPhase().equals(Phase.STARTED) || jobBuild.getPhase().equals(Phase.QUEUED))
                                    && jobBuild.getStatus().equals(Status.SUCCESS)) {

                                building += 1;

                                //Identify success jobs.
                            } else if (jobBuild.getPhase().equals(Phase.FINALIZED)
                                    && jobBuild.getStatus().equals(Status.SUCCESS)
                                    && (jobStatus.getFlow().equals(Flow.NORMAL) || jobStatus.getFlow().equals(Flow.APPROVED))) {

                                if (jobNotificationService.isNotified(job)) {
                                    warning += 1;
                                } else {
                                    success += 1;
                                }

                                //Identify unhealthy jobs.
                            } else if (jobBuild.getPhase().equals(Phase.FINALIZED)
                                    && jobBuild.getStatus().equals(Status.SUCCESS)
                                    && (jobStatus.getFlow().equals(Flow.UNHEALTHY) || jobStatus.getFlow().equals(Flow.DISAPPROVED) || jobStatus.getFlow().equals(Flow.BLOCKED))) {

                                failure += 1;

                                //Identify failure jobs.
                            } else if (jobBuild.getStatus().equals(Status.FAILURE)
                                    || jobBuild.getStatus().equals(Status.ABORTED)
                                    || jobStatus.getFlow().equals(Flow.ERROR)) {

                                failure += 1;

                                //Identify warning jobs.
                            } else if (jobNotificationService.isNotified(job)) {
                                warning += 1;
                            }
                        }
                    }
                }
            }
        }

        return new SubjectDetails(
                subject,
                building,
                success,
                warning,
                failure,
                total);
    }

    /**
     * Get the defails of the list of subjects.
     *
     * @param subjects Subject list
     * @return Subject details.
     */
    public List<SubjectDetails> getDetailsOf(List<Subject> subjects) {
        List<SubjectDetails> subjectDetails = new ArrayList<>();

        subjects.stream().forEach((subject) -> {
            SubjectDetails details = this.getDetailsOf(
                    subject,
                    jobService.findBySubjectOrderByName(subject));
            subjectDetails.add(details);
        });

        return subjectDetails;
    }

    /**
     * Get count of jobs related with a subject.
     *
     * @param subjects Subject list.
     * @return SubjectSummary
     */
    public List<SubjectSummary> getSummaryOf(List<Subject> subjects) {
        List<SubjectSummary> subjectSummary = new ArrayList<>();

        subjects.stream().forEach((subject) -> {
            SubjectSummary summary = new SubjectSummary(subject, jobService.countByEnabledTrueAndSubject(subject));
            subjectSummary.add(summary);
        });

        return subjectSummary;
    }
}
