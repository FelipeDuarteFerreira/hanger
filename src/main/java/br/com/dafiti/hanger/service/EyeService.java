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
import br.com.dafiti.hanger.option.Scope;
import br.com.dafiti.hanger.option.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Async;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class EyeService {

    private final JobService jobService;
    private final JobStatusService jobStatusService;
    private final JobBuildService jobBuildService;
    private final JobBuildPushService jobBuildPushService;
    private final JobCheckupService jobCheckupService;
    private final JobNotificationService jobNotificationService;
    private final WorkbenchEmailService workbenchEmailService;

    private static final Logger LOG = LogManager.getLogger(EyeService.class.getName());

    @Autowired
    public EyeService(
            JobService jobService,
            JobStatusService jobStatusService,
            JobBuildService jobBuildService,
            JobBuildPushService jobBuildPushService,
            JobCheckupService jobCheckupService,
            SlackService slackService,
            JobNotificationService jobNotificationService,
            WorkbenchEmailService workbenchEmailService) {

        this.jobService = jobService;
        this.jobStatusService = jobStatusService;
        this.jobBuildService = jobBuildService;
        this.jobBuildPushService = jobBuildPushService;
        this.jobCheckupService = jobCheckupService;
        this.jobNotificationService = jobNotificationService;
        this.workbenchEmailService = workbenchEmailService;
    }

    /**
     * Hanger eyes that sees everything :).
     *
     * @param notificationPayload Notification Plugin information
     */
    @Async
    public void observer(String notificationPayload) {
        UUID uuid = UUID.randomUUID();
        JSONObject notification = new JSONObject(notificationPayload);
        JSONObject buildNotification = notification.getJSONObject("build");

        //Log the notification payload.
        LOG.log(Level.INFO, "[" + uuid + "] Received notification payload: " + notificationPayload);

        //Identify if the job is observed.
        Job job = jobService.findByName(notification.getString("name"));

        if (job != null) {
            //Log the job found. 
            LOG.log(Level.INFO, "[" + uuid + "] Job found " + job.getName());

            //Define the job build.
            JobBuild jobBuild = new JobBuild();

            jobBuild.setNumber(buildNotification.optInt("number"));
            jobBuild.setPhase(Phase.valueOf(buildNotification.optString("phase")));
            jobBuild.setDateFromTimestamp(buildNotification.optLong("timestamp"));
            jobBuild.setStatus(buildNotification.optString("status").isEmpty() ? Status.SUCCESS : Status.valueOf(buildNotification.optString("status")));
            jobBuild.setJob(job);

            //Define the job status.
            JobStatus jobStatus = job.getStatus();

            if (jobStatus == null) {
                jobStatus = new JobStatus();
                jobStatus.setScope(Scope.FULL);
                jobStatus.setFlow(Flow.NORMAL);

                //Defines a relation between a job and it status. 
                jobStatus = jobStatusService.save(jobStatus);
                job.setStatus(jobStatus);
                job = jobService.save(job);
            }

            //Log the job status.
            LOG.log(Level.INFO, "[" + uuid + "] Job status " + jobStatus.toString());

            //Define the job status update rule.  
            boolean update;

            switch (jobBuild.getPhase()) {
                case QUEUED:
                    update = false;
                    break;
                case STARTED:
                    update = true;

                    //Identifies if the STARTED status was received after a FINALIZED for the same execution.
                    if (jobStatus.getBuild() != null) {
                        update = jobStatus.getBuild().getNumber() != jobBuild.getNumber();

                        if (!update) {
                            LOG.log(Level.INFO, "[" + uuid + "] Status reversion attempt blocked");
                        }
                    }

                    break;
                case COMPLETED:
                    update = false;
                    break;
                case FINALIZED:
                    update = true;
                    break;
                default:
                    update = false;
                    break;
            }

            //Save the build.
            jobBuild = jobBuildService.save(jobBuild);

            //Log the job build.
            LOG.log(Level.INFO, "[" + uuid + "] Job build " + jobBuild.toString());

            //Add the trigger and status to the job. 
            if (update) {
                boolean healthy = true;

                if (jobBuild.getPhase().equals(Phase.FINALIZED)
                        && jobBuild.getStatus().equals(Status.SUCCESS)) {

                    //Evaluates the job checkup. 
                    healthy = jobCheckupService.evaluate(job, jobStatus.getScope());
                }

                jobStatus.setDate(new Date());
                jobStatus.setBuild(jobBuild);
                jobStatus.setScope(jobStatus.getScope() == null ? Scope.FULL : jobStatus.getScope());
                jobStatus.setFlow(healthy ? Flow.NORMAL : Flow.UNHEALTHY);

                //Save the job status.
                jobStatus = jobStatusService.save(jobStatus);

                //Log the job update.
                LOG.log(Level.INFO, "[" + uuid + "] Job updated " + jobBuild.toString());

                //Publish a job notification.
                jobNotificationService.notify(job, true);

                //Log the job notification.
                LOG.log(Level.INFO, "[" + uuid + "] Job notification sent sucessfully");

                //Identify if the job is finalized sucessfully. 
                if (jobStatus.getFlow().equals(Flow.NORMAL)
                        && jobBuild.getPhase().equals(Phase.FINALIZED)
                        && jobBuild.getStatus().equals(Status.SUCCESS)) {

                    //If job ran sucessfully send e-mails linked to it.
                    workbenchEmailService.toEmail(job);

                    //Log the e-mail send.
                    LOG.log(Level.INFO, "[" + uuid + "] Job e-mails sent sucessfully");

                    //Log the job children build push.
                    LOG.log(Level.INFO, "[" + uuid + "] Job children build pushed");

                    //Push all jobs dependents on a job build. 
                    jobBuildPushService.push(job);
                }
            }
        } else {
            LOG.log(Level.INFO, "[" + uuid + "] Rejected notification payload " + notificationPayload);
        }
    }
}
