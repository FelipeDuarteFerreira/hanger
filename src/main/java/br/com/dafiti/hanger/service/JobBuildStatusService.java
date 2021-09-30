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
import br.com.dafiti.hanger.model.JobParent;
import br.com.dafiti.hanger.model.JobStatus;
import br.com.dafiti.hanger.option.Flow;
import br.com.dafiti.hanger.option.Phase;
import br.com.dafiti.hanger.option.Scope;
import br.com.dafiti.hanger.option.Status;
import static com.cronutils.model.CronType.QUARTZ;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.Minutes;
import org.springframework.stereotype.Service;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class JobBuildStatusService {

    /**
     * Identifies if a job is partially or fully built.
     *
     * @param job Job job
     * @param anyScope Identify whether the job was built partially or fully
     * @return Identify if a job parent is built
     */
    public boolean isBuilt(Job job, boolean anyScope) {
        return isBuilt(job, null, anyScope);
    }

    /**
     * Identifies if a job is built.
     *
     * @param job Job job
     * @return Identify if a job parent is built
     */
    public boolean isBuilt(Job job) {
        return isBuilt(job, null, false);
    }

    /**
     * Identifies if a job is built.
     *
     * @param job Job job
     * @param basedate Date base
     * @param anyScope Identify whether the job was built partially or fully
     * @return Identify if a job parent is built
     */
    public boolean isBuilt(Job job, Date basedate, boolean anyScope) {
        boolean built;

        //Get the status of each parent.
        JobStatus jobStatus = job.getStatus();

        //Identify if the job was built at least once a time.
        built = (jobStatus != null);

        if (built) {
            JobBuild jobBuild = jobStatus.getBuild();

            //Identify if the job was built sucessfully.
            built = (jobBuild != null);

            if (built) {
                //Get the job build date. 
                Date jobBuildDate = jobBuild.getDate();

                //Identify if the job was built today.
                built = Days.daysBetween(new LocalDate(jobBuildDate), new LocalDate()).getDays() == 0;

                if (!built) {
                    //Identify if it has antecipation tolerance.
                    if (job.getTolerance() != 0) {
                        //Identify if the job was built in the antecipation tolerance interval today.
                        built = Days.daysBetween(new LocalDate(
                                new DateTime(jobBuildDate).plusHours(job.getTolerance())),
                                new LocalDate()).getDays() == 0;
                    }
                } else {
                    //Idenitify if has a base date.
                    if (basedate != null) {
                        //Identify if job build date is greater than the base date. 
                        built = (jobBuild.getDate().compareTo(basedate) > 0);
                    }
                }

                if (built) {
                    //Identify if the job build is finalized and successfully. 
                    built = (jobBuild.getPhase().equals(Phase.FINALIZED)
                            && jobBuild.getStatus().equals(Status.SUCCESS)
                            && (jobStatus.getFlow().equals(Flow.NORMAL) || jobStatus.getFlow().equals(Flow.APPROVED)));

                    if (built) {
                        //Idenfity if any scope can push a job build.
                        if (!anyScope) {
                            built = jobStatus.getScope() == Scope.FULL;
                        }
                    }
                }
            }
        }

        return built;
    }

    /**
     * Identifies if a job can be built.
     *
     * @param job Job
     * @return Identify if can build a job
     */
    public boolean isBuildable(Job job) {
        boolean buildable = job.isEnabled();

        if (buildable) {
            //Identifies if it is within the job execution period defined by a cron.
            buildable = isTimeRestrictionMatch(job.getTimeRestriction());

            if (buildable) {
                //Get the status of each child.
                JobStatus jobStatus = job.getStatus();

                //Identifies if the job has status.
                buildable = (jobStatus == null);

                if (!buildable) {
                    JobBuild jobBuild = jobStatus.getBuild();

                    //Identifies if the job was never trigger.
                    buildable = (jobBuild == null);

                    if (!buildable) {
                        //Get the job build date. 
                        Date jobBuildDate = jobBuild.getDate();

                        //Identifies if the job was not built today.
                        buildable = Days.daysBetween(new LocalDate(jobBuildDate), new LocalDate()).getDays() != 0;

                        if (!buildable) {
                            //Identifies if the job was not built in the antecipation tolerance interval today.
                            if (job.getTolerance() != 0) {
                                buildable = Days.daysBetween(
                                        new LocalDate(new DateTime(jobBuildDate).plusHours(job.getTolerance())),
                                        new LocalDate()).getDays() != 0;
                            }
                        }
                    }

                    if (!buildable) {
                        //Identifies if the job was not built fully.
                        buildable = (jobStatus.getScope() == Scope.PARTIAL);

                        if (!buildable) {
                            //Identifies if the job can be rebuilt along the day. 
                            buildable = job.isRebuild();

                            if (buildable) {
                                //Identifies if should wait all parents be built before rebuild. 
                                if (job.isRebuildBlocked()) {
                                    boolean blocked;
                                    List<JobParent> parents = job.getParent();

                                    for (JobParent parent : parents) {
                                        //Identifies if a parent is a rebuild blocker. 
                                        if (parent.isBlocker()) {
                                            blocked = this.isBuilt(parent.getParent(), jobBuild.getDate(), false);

                                            if (!blocked) {
                                                buildable = false;
                                                break;
                                            }
                                        }
                                    }
                                }

                                if (buildable) {
                                    //Identifies if it is in waiting time.
                                    if (job.getWait() != 0) {
                                        buildable = Minutes
                                                .minutesBetween(
                                                        new DateTime(jobBuild.getDate()),
                                                        new DateTime()).getMinutes() >= job.getWait();
                                    }
                                }
                            }
                        }
                    }

                    if (buildable) {
                        //If a job is in a checkup process, it is not buildable. 
                        buildable = !jobStatus.getFlow().equals(Flow.CHECKUP);
                    } else {
                        // But if it failed ou is in a build mesh, it should be buildable. 
                        buildable = jobStatus.getFlow().equals(Flow.ERROR) || jobStatus.getFlow().equals(Flow.REBUILD);
                    }
                }
            }
        }

        return buildable;
    }

    /**
     * Identifies if this instant is matched by the cron expression.
     *
     * @param cron Cron expression.
     * @return Instant is matched by the cron expression.
     */
    public boolean isTimeRestrictionMatch(String cron) {
        boolean match = true;

        if (cron != null && !cron.isEmpty()) {
            match = ExecutionTime.forCron(
                    new CronParser(CronDefinitionBuilder.instanceDefinitionFor(QUARTZ))
                            .parse(cron)).isMatch(ZonedDateTime.now());
        }

        return match;
    }
}
