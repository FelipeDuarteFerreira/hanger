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
import br.com.dafiti.hanger.model.Server;
import br.com.dafiti.hanger.model.Subject;
import br.com.dafiti.hanger.model.User;
import br.com.dafiti.hanger.option.Action;
import br.com.dafiti.hanger.option.Flow;
import br.com.dafiti.hanger.option.Scope;
import br.com.dafiti.hanger.repository.JobRepository;
import com.cronutils.descriptor.CronDescriptor;
import static com.cronutils.model.CronType.QUARTZ;
import static com.cronutils.model.CronType.UNIX;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobParentService jobParentService;
    private final JenkinsService jenkinsService;
    private final JobStatusService jobStatusService;

    private static final Logger LOG = LogManager.getLogger(JobService.class.getName());

    @Autowired
    public JobService(
            JobRepository jobRepository,
            JobParentService jobParentService,
            JenkinsService jenkinsService,
            JobStatusService jobStatusService) {

        this.jobRepository = jobRepository;
        this.jobParentService = jobParentService;
        this.jenkinsService = jenkinsService;
        this.jobStatusService = jobStatusService;
    }

    public Iterable<Job> list() {
        return jobRepository.findAll();
    }

    @Cacheable(value = "jobs")
    public Iterable<Job> listFromCache() {
        return jobRepository.findAll();
    }

    /**
     * Return a server job list.
     *
     * @param server Server
     * @param incremental Identifies if should list only not imported jobs.
     * @return server job list.
     * @throws URISyntaxException
     * @throws IOException
     */
    public List<String> listFromServer(
            Server server,
            boolean incremental) throws URISyntaxException, IOException {

        List<String> list = new ArrayList<>();
        List<String> jenkinsJob = jenkinsService.listJob(server);

        if (incremental) {
            Iterable<Job> cache = this.listFromCache();

            for (String jobServer : jenkinsJob) {
                boolean exists = false;

                for (Job job : cache) {
                    if (jobServer.equalsIgnoreCase(job.getName())) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    list.add(jobServer);
                }
            }
        } else {
            list = jenkinsJob;
        }

        return list;
    }

    public Job load(Long id) {
        return jobRepository.findById(id).get();
    }

    public List<Job> findBySubjectOrderByName(Subject subject) {
        return jobRepository.findBySubjectOrderByName(subject);
    }

    public HashSet<Job> findByApprover(User user) {
        return this.jobRepository.findByApprover(user);
    }

    public Job findByName(String name) {
        return jobRepository.findByName(name);
    }

    public List<Job> findByNameContainingOrAliasContaining(String search) {
        return jobRepository.findByNameContainingOrAliasContaining(search, search);
    }

    public List<Job> findByServer(Server server) {
        return jobRepository.findByServer(server);
    }

    @Cacheable(value = "job_count")
    public long count() {
        return jobRepository.count();
    }

    @Cacheable(value = "job_count_by_subject")
    public long countByEnabledTrueAndSubject(Subject subject) {
        return jobRepository.countByEnabledTrueAndSubject(subject);
    }

    @Caching(evict = {
        @CacheEvict(value = "job_count", allEntries = true),
        @CacheEvict(value = "job_count_by_subject", allEntries = true)})
    public Job save(Job job) {
        //Define the relation between the job and its parents.
        job.getParent().stream().forEach((parent) -> {
            if (parent.getJob() == null) {
                parent.setJob(job);
            }
        });

        //Define if the job checkup can have a trigger.
        if (!job.getCheckup().isEmpty()) {
            job.getCheckup().stream().forEach((checkup) -> {
                if (!checkup.getAction().equals(Action.REBUILD_TRIGGER)) {
                    checkup.setTrigger(new ArrayList());
                }
            });
        }

        //Define if rebuilt blocker can be checked. 
        job.setRebuildBlocked(
                job.
                        getParent().
                        stream().
                        filter(parent -> parent.isBlocker()).count() != 0
        );

        //Identify if the time windows cron expression is valid. 
        if ((job.getTimeRestriction() != null)
                && (!job.getTimeRestriction().isEmpty())) {
            new CronParser(
                    CronDefinitionBuilder.instanceDefinitionFor(QUARTZ))
                    .parse(job.getTimeRestriction()).validate();
        }

        return jobRepository.save(job);
    }

    /**
     * Save a job and update its related job on Jenkins.
     *
     * @param job Job
     * @return Job
     */
    @Caching(evict = {
        @CacheEvict(value = "jobs", allEntries = true),
        @CacheEvict(value = "job_count", allEntries = true),
        @CacheEvict(value = "job_count_by_subject", allEntries = true),
        @CacheEvict(value = "propagation", allEntries = true)})
    public Job saveAndUpdateJobConfig(Job job) throws Exception {
        Long id = job.getId();

        if (id != null) {
            Job previousVersion = this.load(id);

            if (previousVersion != null) {
                if (!job.getName().equals(previousVersion.getName())) {
                    jenkinsService.renameJob(job, previousVersion.getName());
                }
            }
        }

        //Update shell script plugin. 
        jenkinsService.updateShellScript(job);

        //Update node. 
        jenkinsService.updateNode(job);

        //Update cron.
        jenkinsService.updateCron(job);

        //Update blocking jobs.
        jenkinsService.updateBlockingJobs(job);

        //Update name, notification plugin and enable/disable a job. 
        jenkinsService.updateJob(job);

        return this.save(job);
    }

    @Caching(evict = {
        @CacheEvict(value = "jobs", allEntries = true),
        @CacheEvict(value = "job_count", allEntries = true),
        @CacheEvict(value = "job_count_by_subject", allEntries = true),
        @CacheEvict(value = "propagation", allEntries = true)})
    public Job enable(Job job) {
        jenkinsService.updateJob(job);
        return this.save(job);
    }

    @Caching(evict = {
        @CacheEvict(value = "jobs", allEntries = true),
        @CacheEvict(value = "job_count", allEntries = true),
        @CacheEvict(value = "job_count_by_subject", allEntries = true),
        @CacheEvict(value = "propagation", allEntries = true)})
    public void delete(Long id) {
        jobRepository.deleteById(id);
    }

    @Caching(evict = {
        @CacheEvict(value = "jobs", allEntries = true),
        @CacheEvict(value = "job_count", allEntries = true),
        @CacheEvict(value = "job_count_by_subject", allEntries = true),
        @CacheEvict(value = "propagation", allEntries = true)})
    public void refresh() {
    }

    /**
     * Rebuild mesh.
     *
     * @param job Job
     */
    public void rebuildMesh(Job job) {
        job.getParent().stream().forEach((jobParent) -> {
            this.rebuildMesh(jobParent.getParent());
        });

        jobStatusService.updateFlow(job.getStatus(), Flow.REBUILD);
    }

    /**
     * Add a job parent.
     *
     * @param job Job
     * @param server Server
     * @param parentJobList ParentJobList
     * @param parentUpstream ParentUpstream
     * @param error Errors
     * @throws Exception
     */
    public void addParent(
            Job job,
            Server server,
            List<String> parentJobList,
            boolean parentUpstream,
            List<String> error) throws Exception {

        if (parentJobList == null) {
            parentJobList = new ArrayList();
        }

        //Identify if upstream jobs should be imported to Hanger. 
        if (parentUpstream) {
            List<String> upstreamJobs = jenkinsService.getUpstreamProjects(job);

            if (upstreamJobs.size() > 0) {
                for (String upstreamName : upstreamJobs) {
                    parentJobList.add(upstreamName);
                }
            }
        }

        for (String name : parentJobList) {
            StringBuilder lineage = new StringBuilder();
            Job parentJob = this.findByName(name);

            //Identify if the parent is valid.
            if (!job.getName().equals(name) && jenkinsService.isBuildable(name, server)) {
                //Identify if the parent job should be imported. 
                if (parentJob == null) {
                    Job transientJob = new Job(name, server);

                    //Identify if should import upstream jobs recursively.
                    if (parentUpstream) {
                        this.addParent(transientJob, server, null, parentUpstream, error);
                    }

                    //Import the parent job. 
                    parentJob = this.save(transientJob);
                    jenkinsService.updateJob(parentJob);
                }

                //Identify if there are ciclic reference in the flow. 
                if (!this.hasCyclicReference(job, parentJob, lineage)) {
                    boolean addParent = true;

                    for (JobParent parent : job.getParent()) {
                        addParent = !parent.getParent().equals(parentJob);

                        if (!addParent) {
                            break;
                        }
                    }

                    //Define the relation between a job and it parents.
                    if (addParent) {
                        job.addParent(new JobParent(job, parentJob, Scope.FULL));
                    }
                } else {
                    throw new Exception("Cyclic Reference: " + lineage.toString().concat(parentJob.getName()));
                }
            } else {
                error.add(name);
            }
        }
    }

    /**
     * Add a job checkup trigger.
     *
     * @param job Job
     * @param checkupIndex checkupIndex
     * @param triggers triggers
     * @throws Exception
     */
    public void addTrigger(
            Job job,
            int checkupIndex,
            List<String> triggers) throws Exception {

        if (triggers == null) {
            triggers = new ArrayList();
        }

        for (String name : triggers) {
            Job triggeredJob = this.findByName(name);

            if (triggeredJob != null) {
                //Identify if the job has checkup.  
                if (job.getCheckup().size() >= checkupIndex) {
                    //Identify if the trigger was already add. 
                    if (!job.getCheckup().get(checkupIndex).getTrigger().contains(triggeredJob)) {
                        //Add the trigger. 
                        job.getCheckup().get(checkupIndex).addTrigger(triggeredJob);
                    }
                }
            }
        }
    }

    /**
     * Identify ciclic Reference.
     *
     * @param job Job
     * @param parent Job
     * @param lineage lineage
     * @return Identify ciclic Reference
     */
    public boolean hasCyclicReference(
            Job job,
            Job parent,
            StringBuilder lineage) {

        return hasCyclicReference(job, parent, false, lineage);
    }

    /**
     * Identify ciclic Reference recursively.
     *
     * @param job Job
     * @param parent Job
     * @param stop stop
     * @param lineage lineage
     * @return Identify ciclic Reference
     */
    private boolean hasCyclicReference(
            Job job,
            Job parent,
            boolean stop,
            StringBuilder lineage) {

        if (!stop) {
            stop = parent.equals(job);
            lineage.append(parent.getName()).append(" < ");

            for (JobParent jobParent : parent.getParent()) {
                stop = this.hasCyclicReference(job, jobParent.getParent(), stop, lineage);
            }
        }

        return stop;
    }

    /**
     * Identify relation path.
     *
     * @param jobTo
     * @param jobFrom
     * @return
     */
    public HashSet<Job> getRelationPath(
            Job jobTo,
            Job jobFrom) {

        return this.getRelationPath(jobTo, jobFrom, new HashSet(), "");
    }

    /**
     * Identify relation path recursively.
     *
     * @param jobTo Job
     * @param jobFrom Job
     * @param lineage lineage
     * @param path path
     * @param parent Job
     * @return Identify relation path recursively
     */
    private HashSet<Job> getRelationPath(
            Job jobTo,
            Job jobFrom,
            HashSet<Job> path,
            String lineage) {

        if (lineage.isEmpty()) {
            lineage += jobTo.getId();
        } else {
            lineage += "," + jobTo.getId();
        }

        for (JobParent jobParent : jobTo.getParent()) {
            getRelationPath(jobParent.getParent(), jobFrom, path, lineage);
        }

        //Identify if the job target was found. 
        if (jobTo.equals(jobFrom)) {
            List<String> jobPath = Arrays.asList(lineage.split(","));

            for (String jobId : jobPath) {
                Job jobFound = this.load(Long.valueOf(jobId));

                if (jobFound != null) {
                    path.add(jobFound);
                }
            }
        }

        return path;
    }

    /**
     * Identify mesh.
     *
     * @param job Job
     * @param self self
     * @return Identify mesh
     */
    public HashSet<Job> getMesh(
            Job job,
            boolean self) {

        HashSet<Job> mesh = this.getMesh(job, new HashSet());

        if (!self) {
            mesh.remove(job);
        }

        return mesh;
    }

    /**
     * Identify mesh recursively.
     *
     * @param job Job
     * @param mesh Mesh list
     * @return Mesh list.
     */
    private HashSet<Job> getMesh(
            Job job,
            HashSet<Job> mesh) {

        job.getParent().stream().forEach((jobParent) -> {
            this.getMesh(jobParent.getParent(), mesh);
        });

        mesh.add(job);

        return mesh;
    }

    /**
     * Identify mesh parent.
     *
     * @param job Job
     * @return Return the mesh parent list.
     */
    public HashSet<Job> getMeshParent(Job job) {
        return this.getMeshParent(job, new HashSet());
    }

    /**
     * Identify mesh parent recursively.
     *
     * @param job Job
     * @param meshParent Mesh parent list
     * @return Return the mesh parent list.
     */
    private HashSet<Job> getMeshParent(
            Job job,
            HashSet<Job> meshParent) {

        job.getParent().stream().forEach((jobParent) -> {
            this.getMeshParent(jobParent.getParent(), meshParent);
        });

        if (job.getParent().isEmpty()) {
            meshParent.add(job);
        }

        return meshParent;
    }

    /**
     * Identify propagation.
     *
     * @param job Job
     * @param self self
     * @return Identify mesh
     */
    public HashSet<Job> getPropagation(
            Job job,
            boolean self) {

        HashSet<Job> propagation = this.getPropagation(job, new HashSet(), 0);

        if (!self) {
            propagation.remove(job);
        }

        return propagation;
    }

    /**
     * Identify propagation recursively.
     *
     * @param job Job
     * @param childJob Job
     * @param propagation Propagation list
     * @return Mesh list.
     */
    @Cacheable(value = "propagation", key = "{#job.id}")
    private HashSet<Job> getPropagation(
            Job job,
            HashSet<Job> propagation,
            int level) {

        level++;

        HashSet<JobParent> childs = jobParentService.findByParent(job);

        if (!childs.isEmpty()) {
            for (JobParent child : childs) {
                if (!propagation.contains(job)) {
                    LOG.log(Level.INFO, StringUtils.repeat(".", level) + child.getJob().getName());

                    this.getPropagation(child.getJob(), propagation, level);
                }
            }
        }

        propagation.add(job);
        return propagation;
    }

    /**
     * Add job child (children).
     *
     * @param job Job
     * @param server Server
     * @param childrenJobList
     * @param parentUpstream ParentUpstream
     * @param rebuildable Identify if should make a job rebuildable.
     * @param error errors
     * @throws Exception
     */
    public void addChildren(
            Job job,
            Server server,
            List<String> childrenJobList,
            boolean parentUpstream,
            boolean rebuildable,
            List<String> error) throws Exception {

        for (String child : childrenJobList) {
            Job jobChild = this.findByName(child);

            //Identify if child exists.
            if (jobChild == null) {
                jobChild = new Job(child, server);
            }

            //Identify if child is valid.
            if (jenkinsService.isBuildable(jobChild)) {
                ArrayList parentList = new ArrayList();
                parentList.add(job.getName());

                if (rebuildable) {
                    jobChild.setRebuild(rebuildable);
                }

                this.addParent(jobChild, job.getServer(), parentList, false, error);
                this.save(jobChild);

                jenkinsService.updateJob(jobChild);
            } else {
                error.add(child);
            }
        }
    }

    /**
     * Get children by parent.
     *
     * @param job Job
     * @return
     */
    public HashSet<JobParent> getChildrenlist(Job job) {
        return jobParentService.findByParent(job);
    }

    /**
     * Get cron description.
     *
     * @param cron
     * @return
     */
    public String getCronDescription(String cron) {
        String description = "";

        try {
            if (cron != null) {
                if (!cron.isEmpty()) {
                    description = StringUtils.capitalize(
                            CronDescriptor
                                    .instance(Locale.ENGLISH)
                                    .describe(new CronParser(
                                            CronDefinitionBuilder.instanceDefinitionFor(UNIX))
                                            .parse(cron)
                                    )
                    );
                }
            }
        } catch (Exception e) {
        }

        return description;
    }
}
