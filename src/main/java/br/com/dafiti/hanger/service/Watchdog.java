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

import br.com.dafiti.hanger.model.AuditorData;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import br.com.dafiti.hanger.model.Job;
import br.com.dafiti.hanger.model.JobBuild;
import br.com.dafiti.hanger.model.JobStatus;
import br.com.dafiti.hanger.option.Status;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import org.joda.time.LocalDateTime;
import org.joda.time.Minutes;
import org.joda.time.Seconds;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class Watchdog {

    private final JobService jobService;
    private final JobDetailsService jobDetailsService;
    private final JobBuildService jobBuildService;
    private final JobBuildPushService jobBuildPushService;
    private final SlackService slackService;
    private final ConnectionService connectionService;
    private final JenkinsService jenkinsServive;
    private final AuditorService auditorService;
    private final JobBuildStatusService jobBuildStatusService;

    private static final Logger LOG = LogManager.getLogger(Watchdog.class.getName());

    @Autowired
    public Watchdog(
            JobService jobService,
            JobDetailsService jobDetailsService,
            JobBuildService jobBuildService,
            JobBuildPushService jobBuildPushService,
            SlackService slackService,
            ConnectionService connectionService,
            JenkinsService jenkinsServive,
            AuditorService auditorService,
            JobBuildStatusService jobBuildStatusService) {

        this.jobService = jobService;
        this.jobDetailsService = jobDetailsService;
        this.jobBuildService = jobBuildService;
        this.jobBuildPushService = jobBuildPushService;
        this.slackService = slackService;
        this.connectionService = connectionService;
        this.jenkinsServive = jenkinsServive;
        this.auditorService = auditorService;
        this.jobBuildStatusService = jobBuildStatusService;
    }

    /**
     * Paw Patrol
     */
    @Scheduled(cron = "${hanger.watchdog.cron}")
    public void patrol() {
        jobPatrol();
        connectionPatrol();

        LOG.log(Level.INFO, "The watchdog patrol is finished!");
    }

    /**
     * Looks for jobs problems.
     */
    private void jobPatrol() {
        LOG.log(Level.INFO, "The watchdog is patrolling jobs!");

        Iterable<Job> jobs = jobService.list();

        for (Job job : jobs) {
            //Identifies enabled jobs.
            if (job.isEnabled()) {
                Status status = jobDetailsService.getDetailsOf(job).getStatus();

                //Identifies jobs that are watchdog candidate. 
                if (status.equals(Status.WAITING) || status.equals(Status.QUEUED) || status.equals(Status.RUNNING)) {
                    //Identifies jobs waiting forever. 
                    if (status.equals(Status.WAITING)) {
                        //Identifies if parents were built at least 30 minutes ago.
                        boolean buildable = !job.getParent().stream()
                                .filter(
                                        jobParent -> jobParent.getParent().getStatus() != null
                                        && jobParent.getParent().getStatus().getBuild() != null
                                        && Minutes
                                                .minutesBetween(
                                                        new LocalDateTime(jobParent.getParent().getStatus().getBuild().getDate()),
                                                        new LocalDateTime()).getMinutes() >= 30
                                ).collect(Collectors.toList()).isEmpty();

                        if (buildable
                                && jobBuildPushService.getPushInfo(job).isReady()) {

                            this.catcher(job, status);
                        } else {
                            LOG.log(Level.INFO, "The watchdog just sniffed {} job {}", new Object[]{status, job.getName()});
                        }
                    }

                    //Identifies jobs running forever.
                    if (status.equals(Status.QUEUED) || status.equals(Status.RUNNING)) {
                        JobStatus jobStatus = job.getStatus();

                        if (jobStatus != null) {
                            JobBuild jobBuild = jobStatus.getBuild();

                            if (jobBuild != null) {
                                int duration = (int) jenkinsServive.getEstimatedDuration(job);
                                int interval = Seconds
                                        .secondsBetween(
                                                new LocalDateTime(jobBuild.getDate()),
                                                new LocalDateTime()).getSeconds();

                                //Identifies if its building or running for at least 30 minutes. 
                                boolean buildable = (interval >= (duration < 1800 ? 1800 : duration));

                                if (buildable
                                        && !jenkinsServive.isBuilding(job, jobBuild.getNumber())
                                        && jobBuildStatusService.isBuildable(job)) {

                                    this.catcher(job, status);
                                } else {
                                    LOG.log(Level.INFO, "The watchdog just sniffed {} job {} with build number {} (Estimated job duration {} s | Interval {} s)", new Object[]{status, job.getName(), jobBuild.getNumber(), duration, interval});
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Looks for connections problems.
     */
    private void connectionPatrol() {
        LOG.log(Level.INFO, "The watchdog is patrolling connections!");

        this.connectionService.listConnectionStatus().forEach((status) -> {
            StringBuilder message = new StringBuilder();
            if (status.getStatus().equals(Status.FAILURE)) {
                message
                        .append(":broken_heart: The connection ")
                        .append(status.getConnection().getName())
                        .append(" is broken!");

                slackService.send(message.toString());
            }
        });
    }

    /**
     * Catch a lost job.
     *
     * @param Job job
     * @param Status status
     */
    private void catcher(Job job, Status status) {
        StringBuilder message = new StringBuilder();

        try {
            //Build a job. 
            jobBuildService.build(job);

            //Log on console. 
            LOG.log(Level.INFO, "The watchdog catched " + status + " job " + job.getName());

            //Log on auditor. 
            auditorService.publish("WATCHDOG",
                    new AuditorData()
                            .addData("name", job.getName())
                            .addData("status", status.toString())
                            .getData());

            //Log on Slack.
            slackService.send(message
                    .append(":dog: The watchdog catched job ")
                    .append("*")
                    .append(job.getDisplayName())
                    .append("*").toString());
        } catch (Exception ex) {
            //Log on console. 
            LOG.log(Level.ERROR, "The watchdog fail building " + job.getName(), ex);

            //Log on Slack.
            slackService.send(message
                    .append(":hotdog: The watchdog fail building ")
                    .append("*")
                    .append(job.getDisplayName())
                    .append("*").toString());
        }
    }
}
