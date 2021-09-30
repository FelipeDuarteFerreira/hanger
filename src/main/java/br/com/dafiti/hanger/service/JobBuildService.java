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
import br.com.dafiti.hanger.option.Scope;
import br.com.dafiti.hanger.repository.JobBuildRepository;
import java.time.LocalDateTime;
import static java.time.temporal.ChronoUnit.SECONDS;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class JobBuildService {

    private final JobBuildRepository jobBuildRepository;
    private final JenkinsService jenkinsService;
    private final JobCheckupService checkupService;
    private final JobStatusService jobStatusService;
    private final JobNotificationService jobNotificationService;
    private final ConcurrentHashMap<Long, LocalDateTime> build;

    private static final Logger LOG = LogManager.getLogger(JobBuildService.class.getName());

    @Autowired
    public JobBuildService(
            JobBuildRepository jobBuildRepository,
            JenkinsService jenkinsService,
            JobCheckupService checkupService,
            JobStatusService jobStatusService,
            JobNotificationService jobNotificationService) {

        this.jobBuildRepository = jobBuildRepository;
        this.jenkinsService = jenkinsService;
        this.checkupService = checkupService;
        this.jobStatusService = jobStatusService;
        this.jobNotificationService = jobNotificationService;
        this.build = new ConcurrentHashMap();
    }

    /**
     * Save a build.
     *
     * @param jobBuild Build;
     * @return
     */
    public JobBuild save(JobBuild jobBuild) {
        return jobBuildRepository.save(jobBuild);
    }

    /**
     * Delete a build.
     *
     * @param id Build ID.
     */
    public void delete(Long id) {
        jobBuildRepository.deleteById(id);
    }

    /**
     * Evaluate the elapsed time of a job.
     *
     * @param job Job
     * @param buildNumber Build number
     * @return Elapsed time.
     */
    @Cacheable(value = "jobBuildTime", key = "{#job.id, #buildNumber}")
    public String findJobBuildTime(
            Job job,
            int buildNumber) {

        return jobBuildRepository.findJobBuildTime(job, buildNumber);
    }

    /**
     * Build a job.
     *
     * @param job Job.
     * @return Build information.
     * @throws java.net.URISyntaxException
     * @throws java.io.IOException
     */
    public BuildInfo build(Job job) throws Exception {
        Scope scope;
        boolean buildable;
        boolean built = false;
        boolean healthy = true;

        //Identifies if the job is waiting in queue. 
        buildable = !jenkinsService.isInQueue(job);

        if (buildable) {
            //Identifies if the job was built recently (10 seconds).
            if (build.containsKey(job.getId())) {
                buildable = SECONDS.between(build.get(job.getId()), LocalDateTime.now()) > 10;

                if (!buildable) {
                    LOG.info("Job " + job.getName() + " duplicated build protection (Job built " + build.get(job.getId()) + ")!");
                } else {
                    build.put(job.getId(), LocalDateTime.now());
                }
            } else {
                build.put(job.getId(), LocalDateTime.now());
            }

            if (buildable) {
                //Identifies if it has prevalidation.
                if (checkupService.hasPrevalidation(job)) {
                    JobStatus jobStatus = job.getStatus();

                    //Identifies what is the correct build scope.
                    if (jobStatus == null) {
                        scope = Scope.FULL;
                    } else {
                        scope = jobStatus.getScope();
                    }

                    //Prevalidation checkup.
                    healthy = checkupService.evaluate(job, true, scope);
                }

                //Identifies if a job is healthy. 
                if (healthy) {
                    built = jenkinsService.build(job);

                    if (!built) {
                        jobStatusService.updateFlow(job.getStatus(), Flow.ERROR);
                        jobNotificationService.notify(job, true);
                    }
                }
            }
        } else {
            LOG.info("Job " + job.getName() + " duplicated build protection (Job in queue)!");
        }

        return new BuildInfo(built, healthy);
    }

    /**
     * Build information.
     */
    public class BuildInfo {

        private boolean built;
        private boolean healthy;

        public BuildInfo(boolean built, boolean healthy) {
            this.built = built;
            this.healthy = healthy;
        }

        public boolean isBuilt() {
            return built;
        }

        public void setBuilt(boolean built) {
            this.built = built;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }
    }
}
