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
import br.com.dafiti.hanger.model.JobParent;
import br.com.dafiti.hanger.model.JobStatus;
import br.com.dafiti.hanger.option.Flow;
import br.com.dafiti.hanger.option.Scope;
import br.com.dafiti.hanger.service.JobBuildService.BuildInfo;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class JobBuildPushService {

    private final JobBuildService jobBuildService;
    private final JobBuildStatusService jobBuildStatusService;
    private final JobParentService jobParentService;
    private final JobService jobService;
    private final JobStatusService jobStatusService;
    private final JobNotificationService jobNotificationService;

    private static final Logger LOG = LogManager.getLogger(JobBuildPushService.class.getName());

    @Autowired
    public JobBuildPushService(
            JobBuildService jobBuildService,
            JobBuildStatusService jobBuildStatusService,
            JobParentService jobParentService,
            JobService jobService,
            JobStatusService jobStatusService,
            JobNotificationService jobNotificationService) {

        this.jobBuildService = jobBuildService;
        this.jobBuildStatusService = jobBuildStatusService;
        this.jobParentService = jobParentService;
        this.jobService = jobService;
        this.jobStatusService = jobStatusService;
        this.jobNotificationService = jobNotificationService;
    }

    /**
     * Push child job build.
     *
     * @param job Job.
     */
    public void push(Job job) {
        HashSet<JobParent> childs = jobParentService.findByParent(job);

        //For each child.
        childs.forEach((JobParent child) -> {
            //Get the  child.
            Job childJob = child.getJob();

            //Get job build push information about a job.
            PushInfo push = this.getPushInfo(childJob);

            //Identify if should push the child build.
            if (push.isReady()) {
                JobStatus childJobStatus = childJob.getStatus();

                //Define the job status.
                if (childJobStatus == null) {
                    childJobStatus = new JobStatus();
                    childJobStatus.setScope(push.getScope());
                    childJobStatus.setDate(new Date());

                    //Defines a relation between a job and it status.
                    childJobStatus = jobStatusService.save(childJobStatus);
                    childJob.setStatus(childJobStatus);
                    jobService.save(childJob);
                } else {
                    childJobStatus.setScope(push.getScope());
                    childJobStatus.setDate(new Date());
                }

                try {
                    //Build the job.
                    BuildInfo buildInfo = jobBuildService.build(childJob);

                    //Identify if the prevalidation was sucessfuly.
                    if (!buildInfo.isHealthy()) {
                        //Set child job as blocked in case pre-validation fail. 
                        childJobStatus.setFlow(Flow.BLOCKED);
                        jobStatusService.save(childJobStatus);

                        //Publish a job notification.
                        jobNotificationService.notify(childJob, true);
                    } else {
                        //Set child job as queued.
                        childJobStatus.setFlow(Flow.QUEUED);
                        jobStatusService.save(childJobStatus);
                    }
                } catch (Exception ex) {
                    //Set child job build as fail in case of error. 
                    childJobStatus.setFlow(Flow.ERROR);
                    jobStatusService.save(childJobStatus);

                    //Publish a job notification.
                    jobNotificationService.notify(childJob, true);

                    //Fail log.
                    LOG.log(Level.ERROR, "Fail building job: " + childJob.getName(), ex);
                }
            }
        });
    }

    /**
     * Identify if can push a job build.
     *
     * @param job Job
     * @return PushInfo Job build push information.
     */
    public PushInfo getPushInfo(Job job) {
        Scope scope = null;
        boolean ready = false;
        Map<Job, Boolean> all = new HashMap();
        Map<Job, Boolean> partial = new HashMap();

        //Identifies the job is getPushInfo.
        boolean built = jobBuildStatusService.isBuildable(job);

        if (built) {
            //Get the job parents.
            List<JobParent> parents = job.getParent();

            for (JobParent parent : parents) {
                //Identifies if the parent is enabled. 
                if (parent.getParent().isEnabled()) {
                    //Identifies if the parent is optional. 
                    built = parent.getScope().equals(Scope.OPTIONAL);

                    //Identifies if the parent is built.
                    if (!built) {
                        built = jobBuildStatusService.isBuilt(parent.getParent(), job.isAnyScope());
                    }

                    //Record all parent build status.
                    all.put(parent.getParent(), built);

                    //Record partial dependencies build status.
                    if (parent.getScope().equals(Scope.PARTIAL)) {
                        partial.put(parent.getParent(), built);
                    }
                }
            }

            //Identifies if there are only healthy dependencies. 
            boolean full = partial.isEmpty();

            if (full) {
                //Identifies if healthy dependencies was built successfully. 
                for (Map.Entry<Job, Boolean> entry : all.entrySet()) {
                    ready = entry.getValue();

                    if (!ready) {
                        break;
                    }
                }

                //Identifies the scope.
                if (ready) {
                    scope = Scope.FULL;
                }

                //Log the full dependencies status.
                LOG.log(Level.INFO, "FULL -> Job={}, scope={}, ready={}, dependencies={}", new Object[]{job.getName(), scope, ready, all});
            } else {
                //Identifies if all dependencies was built successfully.
                for (Map.Entry<Job, Boolean> entry : all.entrySet()) {
                    full = entry.getValue();

                    if (!full) {
                        break;
                    }
                }

                if (full) {
                    ready = true;
                    scope = Scope.FULL;
                } else {
                    //Identifies if partial dependencies was built successfully. 
                    for (Map.Entry<Job, Boolean> entry : partial.entrySet()) {
                        ready = entry.getValue();

                        if (!ready) {
                            break;
                        }
                    }

                    //Identifies the scope.
                    if (ready) {
                        JobStatus jobStatus = job.getStatus();

                        //Identifies if the job are already in a partial scope.
                        if (jobStatus != null) {
                            int lastBuild = Days.daysBetween(new LocalDate(jobStatus.getDate()), new LocalDate()).getDays();

                            //Identifies if the job was built today. 
                            if (lastBuild == 0) {
                                ready = (jobStatus.getScope() != Scope.PARTIAL);
                            }
                        }

                        scope = Scope.PARTIAL;
                    }
                }

                //Log the partial dependencies status.
                LOG.log(Level.INFO, "PARTIAL -> job={}, scope={}, ready={}, partial={}, full={}", new Object[]{job.getName(), scope, ready, partial, all});
            }
        } else {
            LOG.log(Level.INFO, "Job {} is not buildable", new Object[]{job.getName()});
        }

        return new PushInfo(ready, scope);
    }

    /**
     * Push information.
     */
    public class PushInfo {

        private boolean ready;
        private Scope scope;

        public PushInfo(boolean ready, Scope scope) {
            this.ready = ready;
            this.scope = scope;
        }

        public boolean isReady() {
            return ready;
        }

        public void setReady(boolean ready) {
            this.ready = ready;
        }

        public Scope getScope() {
            return scope;
        }

        public void setScope(Scope scope) {
            this.scope = scope;
        }
    }
}
