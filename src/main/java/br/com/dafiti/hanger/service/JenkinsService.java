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
import br.com.dafiti.hanger.model.Server;
import static com.cronutils.model.CronType.UNIX;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.JobWithDetails;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.servlet.http.HttpServletRequest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class JenkinsService {

    private final HttpServletRequest request;

    private static final Logger LOG = LogManager.getLogger(JenkinsService.class.getName());

    @Autowired
    public JenkinsService(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * Get a Jenkins server connection.
     *
     * @param server Server Server
     * @return Server connection
     * @throws URISyntaxException
     */
    @Cacheable(value = "jenkinsserver", key = "#server.id")
    public JenkinsServer getJenkinsServer(Server server) throws URISyntaxException {
        JenkinsServer jenkins = null;

        if (server != null) {
            jenkins = new JenkinsServer(new URI(
                    server.getUrl()),
                    server.getUsername(),
                    server.getToken());
        }

        return jenkins;
    }

    /**
     * Test if a server is running.
     *
     * @param server Server Server
     * @return Identify if a job is running.
     */
    public boolean isRunning(Server server) {
        boolean running = false;
        JenkinsServer jenkins;
        try {
            jenkins = this.getJenkinsServer(server);

            if (jenkins != null) {
                running = (jenkins.isRunning());
                jenkins.close();
            }
        } catch (URISyntaxException ex) {
            LOG.log(Level.ERROR, "Fail checking if a server is running!", ex);
        }

        return running;
    }

    /**
     * Get Jenkins job list.
     *
     * @param server Server
     * @return Job list
     * @throws URISyntaxException
     * @throws IOException
     */
    @Cacheable(value = "serverJobs", key = "#server.id")
    public List<String> listJob(Server server) throws URISyntaxException, IOException {
        List<String> jobs = new ArrayList<>();
        JenkinsServer jenkins = this.getJenkinsServer(server);

        if (jenkins != null) {
            if (jenkins.isRunning()) {
                for (String job : jenkins.getJobs().keySet()) {
                    jobs.add(job);
                }
            } else {
                throw new URISyntaxException("Jenkins is not running", "Can't import Jenkins job list");
            }

            jenkins.close();
        }

        return jobs;
    }

    /**
     * Identify if a job is buildable.
     *
     * @param job Job
     * @return Identify if job was built.
     * @throws URISyntaxException
     * @throws IOException
     */
    public boolean isBuildable(Job job) throws URISyntaxException, IOException {
        return this.isBuildable(job.getName(), job.getServer());
    }

    /**
     * Identify if a job is buildable.
     *
     * @param name Job name
     * @param server Server
     * @return Identify if job was built.
     * @throws URISyntaxException
     * @throws IOException
     */
    public boolean isBuildable(String name, Server server) throws URISyntaxException, IOException {
        JenkinsServer jenkins;
        boolean isBuildable = false;

        if (name != null && server != null) {
            jenkins = this.getJenkinsServer(server);

            if (jenkins != null) {
                if (jenkins.isRunning()) {
                    JobWithDetails jobWithDetails = jenkins.getJob(name);

                    if (jobWithDetails != null) {
                        isBuildable = (jobWithDetails.isBuildable());
                    }

                    jenkins.close();
                }
            }
        }

        return isBuildable;
    }

    /**
     * Build a Jenkins job.
     *
     * @param job Job
     * @return Identify if job was built.
     * @throws URISyntaxException
     * @throws IOException
     */
    public boolean build(Job job) throws Exception {
        JenkinsServer jenkins;
        boolean built = false;

        if (job != null) {
            jenkins = this.getJenkinsServer(job.getServer());

            if (jenkins != null) {
                if (jenkins.isRunning()) {
                    JobWithDetails jobWithDetails = jenkins.getJob(job.getName());

                    if (jobWithDetails != null) {
                        try {
                            built = (jobWithDetails.build(true) != null);
                        } catch (IOException ex) {
                            LOG.log(Level.ERROR, "Fail building job: " + job.getName(), ex);
                            try {
                                built = (jobWithDetails.build(new HashMap(), true) != null);
                            } catch (IOException e) {
                                LOG.log(Level.ERROR, "Fail building parametrized job: " + job.getName(), ex);
                                throw ex;
                            }
                        } finally {
                            jenkins.close();
                        }
                    }
                }
            }
        }

        return built;
    }

    /**
     * Get a Jenkins job upstream project list.
     *
     * @param job Job
     * @return Upstream project list
     */
    public List<String> getUpstreamProjects(Job job) {
        JenkinsServer jenkins;
        List<String> upstreamProjects = new ArrayList<>();

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning()) {
                        JobWithDetails jobWithDetails = jenkins.getJob(job.getName());

                        if (jobWithDetails != null) {
                            jobWithDetails.getUpstreamProjects().stream().forEach((upstream) -> {
                                upstreamProjects.add(upstream.getName());
                            });
                        }
                    }

                    jenkins.close();
                }
            } catch (URISyntaxException | IOException ex) {
                LOG.log(Level.ERROR, "Fail getting upstream projects!", ex);
            }
        }

        return upstreamProjects;
    }

    /**
     * Retrieves one or more shell script commands related to a job.
     *
     * @param job Job
     * @param jobName Name of job to get configuration from
     * @return Shell script list.
     */
    public List<String> getShellScript(Job job, String jobName) {
        JenkinsServer jenkins;
        List<String> shellScripts = new ArrayList<>();

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning() && jobName != null) {
                        String config = jenkins.getJobXml(jobName);

                        if (config != null) {
                            Document document = Jsoup.parse(config);

                            document.getElementsByTag("hudson.tasks.Shell").forEach(element -> {
                                shellScripts.add(element.wholeText().trim());
                            });
                        }
                    }

                    jenkins.close();
                }
            } catch (IOException | URISyntaxException ex) {
                LOG.log(Level.ERROR, "Fail getting shell script from job " + jobName + "!", ex);
            }
        }

        return shellScripts;
    }

    /**
     * Identify if a job is in queue.
     *
     * @param job Job
     * @return Identify if a job is in queue
     */
    public boolean isInQueue(Job job) {
        JenkinsServer jenkins;
        boolean isInQueue = false;

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning()) {
                        JobWithDetails jobWithDetails = jenkins.getJob(job.getName());

                        if (jobWithDetails != null) {
                            isInQueue = jobWithDetails.isInQueue();
                        }
                    }

                    jenkins.close();
                }
            } catch (IOException | URISyntaxException ex) {
                LOG.log(Level.ERROR, "Fail identifying if a job is in queue!", ex);
            }
        }

        return isInQueue;
    }

    /**
     * Identify if a job is building.
     *
     * @param job Job
     * @param number Job build number
     * @return Identify if a job is building
     */
    public boolean isBuilding(Job job, int number) {
        JenkinsServer jenkins;
        boolean isBuilding = false;

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning()) {
                        JobWithDetails jobWithDetails = jenkins.getJob(job.getName());

                        if (jobWithDetails != null) {
                            //Identifies if the job is in queue. 
                            isBuilding = jobWithDetails.isInQueue();

                            if (!isBuilding) {
                                Build build = jobWithDetails.getBuildByNumber(number);

                                if (build != null) {
                                    BuildWithDetails buildWithDetails = build.details();

                                    if (buildWithDetails != null) {
                                        //Identifies if the job is running. 
                                        isBuilding = buildWithDetails.isBuilding();
                                    }
                                }
                            }
                        }
                    }

                    jenkins.close();
                }
            } catch (IOException | URISyntaxException ex) {
                LOG.log(Level.ERROR, "Fail identifying if a job is building!", ex);
            }
        }

        return isBuilding;
    }

    /**
     * Identify if a job is running.
     *
     * @param server Jenkins server
     * @param name Job name
     * @return Identify if a job is running
     */
    public boolean isRunning(Server server, String name) {
        JenkinsServer jenkins;
        boolean isRunning = false;

        if (server != null) {
            try {
                jenkins = this.getJenkinsServer(server);

                if (jenkins != null) {
                    if (jenkins.isRunning()) {
                        JobWithDetails jobWithDetails = jenkins.getJob(name);

                        if (jobWithDetails != null) {
                            Build build = jobWithDetails.getLastBuild();

                            if (build != null) {
                                BuildWithDetails buildWithDetails = build.details();

                                if (buildWithDetails != null) {
                                    isRunning = buildWithDetails.isBuilding();
                                }
                            }
                        }
                    }

                    jenkins.close();
                }
            } catch (IOException | URISyntaxException ex) {
                LOG.log(Level.ERROR, "Fail identifying if a job is building!", ex);
            }
        }

        return isRunning;
    }

    /**
     * Get estimated duration of a job.
     *
     * @param job Job
     * @return estimated duration of a job in seconds
     */
    public long getEstimatedDuration(Job job) {
        JenkinsServer jenkins;
        long duration = 0;

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning()) {
                        JobWithDetails jobWithDetails = jenkins.getJob(job.getName());

                        if (jobWithDetails != null) {
                            Build lastSuccessfulBuild = jobWithDetails.getLastSuccessfulBuild();

                            if (lastSuccessfulBuild != null) {
                                BuildWithDetails details = lastSuccessfulBuild.details();

                                if (details != null) {
                                    duration = TimeUnit.MILLISECONDS.toSeconds(details.getEstimatedDuration());
                                }
                            }
                        }
                    }

                    jenkins.close();
                }
            } catch (IOException | URISyntaxException ex) {
                LOG.log(Level.ERROR, "Fail identifying job estimated duration!", ex);
            }
        }

        return duration;
    }

    /**
     * Rename a job.
     *
     * @param job Job
     * @param name Name
     */
    public void renameJob(Job job, String name) throws Exception {
        JenkinsServer jenkins;

        if (job != null) {
            if (!name.isEmpty() && !job.getName().equals(name)) {
                try {
                    jenkins = this.getJenkinsServer(job.getServer());

                    if (jenkins != null) {
                        if (jenkins.isRunning()) {
                            //Identifies if the name is already in use. 
                            if (!this.exists(job)) {
                                //Identifies if the job is running at the rename moment. 
                                if (!this.isRunning(job.getServer(), name)) {
                                    jenkins.renameJob(name, job.getName(), true);
                                } else {
                                    throw new Exception("This job is building and cannot be renamed, please wait build finish!");
                                }
                            } else {
                                throw new Exception(job.getName() + " is alread in use on Jenkins, please choose another name!");
                            }
                        }

                        jenkins.close();
                    }
                } catch (URISyntaxException | IOException ex) {
                    LOG.log(Level.ERROR, "Fail renaming a job!", ex);
                }
            }
        }
    }

    /**
     * Updates job shell script on jenkins.
     *
     * @param job Job
     */
    public void updateShellScript(Job job) {
        JenkinsServer jenkins;

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning()) {
                        String name = job.getName();
                        String config = jenkins.getJobXml(name);

                        String shellScripts = "";

                        for (String shellScript : job.getShellScript()) {
                            //Escape special characteres. 
                            shellScript = shellScript
                                    .replaceAll("\\\\", "\\\\\\\\")
                                    .replaceAll("\\$", "\\\\\\$");

                            //Escape XML reserverd characteres. 
                            shellScript = StringUtils.escapeXml(shellScript);

                            //Define a hudson.tasks.shell command tag. 
                            shellScripts += "<hudson.tasks.Shell>\n<command>" + shellScript + "</command>\n</hudson.tasks.Shell>\n";
                        }

                        //Identifies if job has builders tag for update shell script
                        if (config.contains("<builders>") && config.contains("<hudson.tasks.Shell>")) {
                            config = config.replaceAll("(?s)<builders>(.*)</builders>", "<builders>" + shellScripts + "</builders>");
                        } else if (job.getShellScript().size() > 0) {
                            config = config.replaceAll("(?s)<builders/>", "<builders>" + shellScripts + "</builders>");
                        }

                        //Update Jenkins job. 
                        jenkins.updateJob(name, config, true);
                    }
                }
            } catch (URISyntaxException | IOException ex) {
                LOG.log(Level.WARN, "Fail updating Jenkins job shell script!", ex);
            }
        }
    }

    /**
     * Updates job node on jenkins.
     *
     * @param job Job
     */
    public void updateNode(Job job) {
        JenkinsServer jenkins;

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning()) {
                        String name = job.getName();
                        String config = jenkins.getJobXml(name);

                        //If the node attribute is empty, remove the tag from xml.
                        if (job.getNode() == null || job.getNode().trim().isEmpty()) {
                            config = config.replaceAll("(?s)<assignedNode>(.*)</assignedNode>", "");
                            //Check: Restricts where this project can be executed.
                            config = config.replaceAll("(?s)<canRoam>(.*)</canRoam>", "<canRoam>true</canRoam>");
                        } else {
                            //If node attribute exists, add or update the tag.
                            if (config.contains("<assignedNode>")) {
                                config = config.replaceAll("(?s)<assignedNode>(.*)</assignedNode>", "<assignedNode>" + job.getNode() + "</assignedNode>");
                            } else {
                                config = config.replace("</project>", "<assignedNode>" + job.getNode() + "</assignedNode></project>");
                            }
                            //Check: Restricts where this project can be executed.
                            config = config.replaceAll("(?s)<canRoam>(.*)</canRoam>", "<canRoam>false</canRoam>");
                        }

                        //Update Jenkins job. 
                        jenkins.updateJob(name, config, true);
                    }
                }
            } catch (URISyntaxException | IOException ex) {
                LOG.log(Level.WARN, "Fail updating Jenkins job node!", ex);
            }
        }
    }

    /**
     * Updates job cron on jenkins.
     *
     * @param job Job
     */
    public void updateCron(Job job) {
        JenkinsServer jenkins;

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning()) {
                        String name = job.getName();
                        String config = jenkins.getJobXml(name);

                        //If the cron attribute is empty, remove the tag from xml.
                        if (job.getCron() == null || job.getCron().trim().isEmpty()) {
                            config = config.replaceAll("(?s)<triggers>(.*)</triggers>", "<triggers/>");
                        } else {
                            //Identify if cron expression is valid.
                            new CronParser(
                                    CronDefinitionBuilder.instanceDefinitionFor(UNIX))
                                    .parse(job.getCron()).validate();

                            //If cron attribute exists, add or update the tag.
                            if (config.contains("<triggers>")) {
                                config = config.replaceAll("(?s)<spec>(.*)</spec>", "<spec>" + job.getCron() + "</spec>");
                            } else {
                                config = config.replace("<triggers/>", "<triggers><hudson.triggers.TimerTrigger><spec>" + job.getCron() + "</spec></hudson.triggers.TimerTrigger></triggers>");
                            }
                        }

                        //Update Jenkins job. 
                        jenkins.updateJob(name, config, true);
                    }
                }
            } catch (URISyntaxException | IOException ex) {
                LOG.log(Level.WARN, "Fail updating Jenkins job!", ex);
            }
        }
    }

    /**
     * Updates job blocking jobs on jenkins.
     *
     * @param job Job
     */
    public void updateBlockingJobs(Job job) {
        JenkinsServer jenkins;

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning()) {
                        String name = job.getName();
                        String config = jenkins.getJobXml(name);

                        //If the blocking jobs attribute is empty, remove the tag from xml.
                        if (job.getBlockingJobs() == null || job.getBlockingJobs().trim().isEmpty()) {
                            config = config.replaceAll("(?s)<blockingJobs>(.*)</blockingJobs>", "<blockingJobs/>");
                            config = config.replaceAll("(?s)<useBuildBlocker>(.*)</useBuildBlocker>", "<useBuildBlocker>false</useBuildBlocker>");
                        } else {
                            //If blocking jobs attribute exists, add or update the tag.
                            if (config.contains("<blockingJobs>")) {
                                config = config.replaceAll("(?s)<blockingJobs>(.*)</blockingJobs>", "<blockingJobs>" + job.getBlockingJobs() + "</blockingJobs>");
                            } else {
                                if (config.contains("<blockingJobs/>")) {
                                    config = config.replace("<blockingJobs/>", "<blockingJobs>" + job.getBlockingJobs() + "</blockingJobs>");
                                } else {
                                    //Build Blocker plugin configuration in case the job does not contain.
                                    String buildBlockerPluginConfig = ""
                                            + "<hudson.plugins.buildblocker.BuildBlockerProperty plugin=\"build-blocker-plugin@1.7.3\">\n"
                                            + "     <useBuildBlocker>true</useBuildBlocker>\n"
                                            + "     <blockLevel>GLOBAL</blockLevel>\n"
                                            + "     <scanQueueFor>DISABLED</scanQueueFor>\n"
                                            + "     <blockingJobs>" + job.getBlockingJobs() + "</blockingJobs>\n"
                                            + "</hudson.plugins.buildblocker.BuildBlockerProperty>\n";

                                    if (config.contains("<properties>")) {
                                        config = config.replace("</properties>", buildBlockerPluginConfig + "</properties>");
                                    } else {
                                        config = config.replace("<properties/>", "<properties>" + buildBlockerPluginConfig + "</properties>");
                                    }
                                }
                            }

                            config = config.replaceAll("(?s)<useBuildBlocker>(.*)</useBuildBlocker>", "<useBuildBlocker>true</useBuildBlocker>");
                        }

                        //Update Jenkins job. 
                        jenkins.updateJob(name, config, true);
                    }
                }
            } catch (URISyntaxException | IOException ex) {
                LOG.log(Level.WARN, "Fail updating Jenkins job!", ex);
            }
        }
    }

    /**
     * Clone a job template from jenkins.
     *
     * @param job Job
     * @param template
     * @throws java.net.URISyntaxException
     * @throws java.io.IOException
     */
    public void clone(Job job, String template) throws URISyntaxException, IOException {
        JenkinsServer jenkins;

        if (job != null) {
            jenkins = this.getJenkinsServer(job.getServer());

            if (jenkins != null) {
                if (jenkins.isRunning()) {
                    if (!this.exists(job)) {
                        String config = jenkins.getJobXml(template);

                        //Update Jenkins job. 
                        jenkins.createJob(job.getName(), config, true);
                    } else {
                        throw new URISyntaxException(job.getName(), "The job already exists on Jenkins");
                    }
                } else {
                    throw new URISyntaxException(job.getName(), "Jenkins is not running, can't create a new job");
                }
            }
        }
    }

    /**
     * Add the Hanger endpoint to the notificion plugin configuration of a
     * Jenkins job.
     *
     * @param job Job
     */
    public void updateJob(Job job) {
        JenkinsServer jenkins;

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning()) {
                        String name = job.getName();
                        String config = jenkins.getJobXml(name);
                        String url = request.getRequestURL().toString().replace(request.getRequestURI(), request.getContextPath());

                        //Identify if Notification plugin is configured. 
                        if (!config.contains("com.tikal.hudson.plugins.notification.HudsonNotificationProperty")) {
                            String notificationPluginConfig = ""
                                    + "    <com.tikal.hudson.plugins.notification.HudsonNotificationProperty plugin=\"notification@1.12\">\n"
                                    + "      <endpoints>\n"
                                    + "        <com.tikal.hudson.plugins.notification.Endpoint>\n"
                                    + "          <protocol>HTTP</protocol>\n"
                                    + "          <format>JSON</format>\n"
                                    + "          <urlInfo>\n"
                                    + "            <urlOrId>" + url + "/observer</urlOrId>\n"
                                    + "            <urlType>PUBLIC</urlType>\n"
                                    + "          </urlInfo>\n"
                                    + "          <event>all</event>\n"
                                    + "          <timeout>30000</timeout>\n"
                                    + "          <loglines>0</loglines>\n"
                                    + "          <retries>3</retries>\n"
                                    + "        </com.tikal.hudson.plugins.notification.Endpoint>\n"
                                    + "      </endpoints>\n"
                                    + "    </com.tikal.hudson.plugins.notification.HudsonNotificationProperty>\n";

                            //Add notification plugin tags to Jenkins config XML. 
                            if (config.contains("<properties>")) {
                                config = config.replace("</properties>", notificationPluginConfig + "</properties>");
                            } else {
                                config = config.replace("<properties/>", "<properties>\n" + notificationPluginConfig + "</properties>");
                            }
                        } else if (config.contains("<com.tikal.hudson.plugins.notification.Endpoint>")) {
                            String notificationPluginEndpoint = ""
                                    + "        <com.tikal.hudson.plugins.notification.Endpoint>\n"
                                    + "          <protocol>HTTP</protocol>\n"
                                    + "          <format>JSON</format>\n"
                                    + "          <urlInfo>\n"
                                    + "            <urlOrId>" + url + "/observer</urlOrId>\n"
                                    + "            <urlType>PUBLIC</urlType>\n"
                                    + "          </urlInfo>\n"
                                    + "          <event>all</event>\n"
                                    + "          <timeout>30000</timeout>\n"
                                    + "          <loglines>0</loglines>\n"
                                    + "          <retries>3</retries>\n"
                                    + "        </com.tikal.hudson.plugins.notification.Endpoint>\n";

                            //Replace notification plugin endpoint tag of Jenkins config XML
                            config = config.replaceAll("(?s)<com\\.tikal\\.hudson\\.plugins\\.notification\\.Endpoint>(.*)</com\\.tikal\\.hudson\\.plugins\\.notification\\.Endpoint>", notificationPluginEndpoint);
                        }

                        //Update Jenkins job. 
                        jenkins.updateJob(name, config, true);

                        //Identifies if should enable or disable a job on Jenkins. 
                        if (job.isEnabled()) {
                            jenkins.enableJob(name, true);
                        } else {
                            jenkins.disableJob(name, true);
                        }
                    }

                    jenkins.close();
                }
            } catch (URISyntaxException | IOException ex) {
                LOG.log(Level.WARN, "Fail updating Jenkins job!", ex);
            }
        }
    }

    /**
     * Identify if notification plugin is deployed.
     *
     * @param server Server
     * @return Identify if notification plugin is deployed.
     */
    public boolean hasNotificationPlugin(Server server) {
        boolean notification = false;

        try {
            JenkinsServer jenkins = this.getJenkinsServer(server);

            if (jenkins != null) {
                if (jenkins.isRunning()) {
                    notification = jenkins
                            .getPluginManager()
                            .getPlugins()
                            .stream()
                            .filter(x -> x.getShortName().equals("notification"))
                            .count() == 1;
                }

                jenkins.close();
            }
        } catch (URISyntaxException | IOException ex) {
            LOG.log(Level.ERROR, "Fail getting plugin list!", ex);
        }

        return notification;
    }

    /**
     * Identify if build blocker plugin is deployed.
     *
     * @param server Server
     * @return Identify if build blocker plugin is deployed.
     */
    public boolean hasBuildBlockerPlugin(Server server) {
        boolean hasPlugin = false;

        try {
            JenkinsServer jenkins = this.getJenkinsServer(server);

            if (jenkins != null) {
                if (jenkins.isRunning()) {
                    hasPlugin = jenkins
                            .getPluginManager()
                            .getPlugins()
                            .stream()
                            .filter(x -> x.getShortName().equals("build-blocker-plugin"))
                            .count() == 1;
                }

                jenkins.close();
            }
        } catch (URISyntaxException | IOException ex) {
            LOG.log(Level.ERROR, "Fail getting plugin list!", ex);
        }

        return hasPlugin;
    }

    /**
     * Clean the job list cache.
     */
    @Caching(evict = {
        @CacheEvict(value = "serverJobs", allEntries = true),
        @CacheEvict(value = "jobShellScript", allEntries = true)
    })
    public void refresh() {
    }

    /**
     * Identify if a job exists on jenkins.
     *
     * @param job Job
     * @return Identify if a job exists on jenkins.
     */
    public boolean exists(Job job) {
        JenkinsServer jenkins;
        boolean exists = false;

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning()) {
                        JobWithDetails jobWithDetails = jenkins.getJob(job.getName());

                        if (jobWithDetails != null) {
                            exists = true;
                        }
                    }

                    jenkins.close();
                }
            } catch (IOException | URISyntaxException ex) {
                LOG.log(Level.ERROR, "Fail identifying if a job exists!", ex);
            }
        }

        return exists;
    }

    /**
     * Retrieves a string with node that executes the job.
     *
     * @param job Job
     * @return Assigned node
     */
    public String getNode(Job job) {
        return this.getNode(job, job.getName());
    }

    /**
     * Retrieves a string with node that executes the job.
     *
     * @param job Job
     * @param jobName Name of job to get configuration from
     * @return Assigned node
     */
    public String getNode(Job job, String jobName) {
        JenkinsServer jenkins;
        StringBuilder node = new StringBuilder();

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning() && jobName != null) {
                        String config = jenkins.getJobXml(jobName);

                        if (config != null) {
                            Document document = Jsoup.parse(config);

                            document.getElementsByTag("assignedNode").forEach(element -> {
                                node.append(element
                                        .wholeText()
                                        .trim());
                            });
                        }
                    }

                    jenkins.close();
                }
            } catch (IOException | URISyntaxException ex) {
                LOG.log(Level.ERROR, "Fail getting node from job " + jobName + "!", ex);
            }
        }

        return node.toString();
    }

    /**
     * Retrieves a string with cron that builds the job periodically.
     *
     * @param job Job
     * @return cron
     */
    public String getCron(Job job) {
        return this.getCron(job, job.getName());
    }

    /**
     * Retrieves a string with cron that builds the job periodically.
     *
     * @param job Job
     * @param jobName
     * @return cron
     */
    public String getCron(Job job, String jobName) {
        JenkinsServer jenkins;
        StringBuilder cron = new StringBuilder();

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning() && jobName != null) {
                        String config = jenkins.getJobXml(jobName);

                        if (config != null) {
                            Document document = Jsoup.parse(config);

                            document.getElementsByTag("triggers").forEach(element -> {
                                cron.append(element
                                        .wholeText()
                                        .trim());
                            });
                        }
                    }

                    jenkins.close();
                }
            } catch (IOException | URISyntaxException ex) {
                LOG.log(Level.ERROR, "Fail getting cron from job " + jobName + "!", ex);
            }
        }

        return cron.toString();
    }

    /**
     * Recovers a string with blocking jobs.
     *
     * @param job Job
     * @return Blocking jobs
     */
    public String getBlockingJobs(Job job) {
        return this.getBlockingJobs(job, job.getName());
    }

    /**
     * Recovers a string with blocking jobs.
     *
     * @param job Job
     * @param jobName
     * @return Blocking jobs
     */
    public String getBlockingJobs(Job job, String jobName) {
        JenkinsServer jenkins;
        StringBuilder blockingJobs = new StringBuilder();

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning() && jobName != null) {
                        String config = jenkins.getJobXml(jobName);

                        if (config != null) {
                            Document document = Jsoup.parse(config);

                            document.getElementsByTag("blockingJobs").forEach(element -> {
                                blockingJobs.append(element
                                        .wholeText()
                                        .trim());
                            });
                        }
                    }

                    jenkins.close();
                }
            } catch (IOException | URISyntaxException ex) {
                LOG.log(Level.ERROR, "Fail getting blocking jobs from job " + jobName + "!", ex);
            }
        }

        return blockingJobs.toString();
    }

    /**
     * Abort a job execution by its number.
     *
     * @param job Job
     * @param number execution number.
     * @return
     */
    public boolean abort(Job job, int number) {
        JenkinsServer jenkins;
        boolean aborted = false;

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning()) {
                        JobWithDetails jobWithDetails = jenkins.getJob(job.getName());

                        if (jobWithDetails != null) {
                            //Identifies if the job is in queue. 
                            aborted = jobWithDetails.isInQueue();

                            if (!aborted) {
                                Build build = jobWithDetails.getBuildByNumber(number);

                                if (build != null) {
                                    BuildWithDetails buildWithDetails = build.details();

                                    if (buildWithDetails != null) {
                                        //Identifies if the job is running. 
                                        aborted = buildWithDetails.isBuilding();

                                        if (aborted) {
                                            //Stops job execution.
                                            buildWithDetails.Stop(true);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    jenkins.close();
                }
            } catch (IOException | URISyntaxException ex) {
                LOG.log(Level.ERROR, "Fail aborting job!", ex);
            }
        }

        return aborted;
    }

    /**
     * Get a job build log.
     *
     * @param job Job
     * @return Console output text.
     */
    public String getLog(Job job) {
        String log = "";
        JenkinsServer jenkins;

        if (job != null) {
            try {
                jenkins = this.getJenkinsServer(job.getServer());

                if (jenkins != null) {
                    if (jenkins.isRunning()) {
                        JobWithDetails jobWithDetails = jenkins.getJob(job.getName());

                        if (jobWithDetails != null) {
                            Build lastSuccessfulBuild = jobWithDetails.getLastBuild();

                            if (lastSuccessfulBuild != null) {
                                BuildWithDetails details = lastSuccessfulBuild.details();

                                if (details != null) {

                                    if (details.getId() != null) {
                                        log = details.getConsoleOutputText();
                                    }
                                }
                            }
                        }
                    }

                    jenkins.close();
                }
            } catch (IOException | URISyntaxException ex) {
                LOG.log(Level.ERROR, "Fail getting " + job + " log!", ex);
            }
        }

        return log;
    }
}
