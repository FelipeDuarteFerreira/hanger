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
import br.com.dafiti.hanger.model.Subject;
import br.com.dafiti.hanger.model.User;
import br.com.dafiti.hanger.option.Flow;
import br.com.dafiti.hanger.repository.SubjectRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author Daniel D GOMES
 */
@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final UserService userService;
    private final JobService jobService;
    private final JenkinsService jenkinsService;
    private final JobStatusService jobStatusService;
    private final JobNotificationService jobNotificationService;

    private static final Logger LOG = LogManager.getLogger(SubjectService.class.getName());

    @Autowired
    public SubjectService(
            SubjectRepository subjectRepository,
            UserService userService,
            JobService jobService,
            JenkinsService jenkinsService,
            JobStatusService jobStatusService,
            JobNotificationService jobNotificationService) {

        this.subjectRepository = subjectRepository;
        this.userService = userService;
        this.jobService = jobService;
        this.jenkinsService = jenkinsService;
        this.jobStatusService = jobStatusService;
        this.jobNotificationService = jobNotificationService;
    }

    public Iterable<Subject> list() {
        return subjectRepository.findAll();
    }

    public Subject load(Long id) {
        return subjectRepository.findById(id).get();
    }

    public void save(Subject subject) {
        subjectRepository.save(subject);
    }

    public void delete(Long id) {
        subjectRepository.deleteById(id);
    }

    /**
     * Return subject list order by name.
     *
     * @return Subject list.
     */
    public List<Subject> findAllByOrderByName() {
        return subjectRepository.findAllByOrderByName();
    }

    /**
     * Return subject list according to signature rule.
     *
     * @return Subject list.
     */
    public List<Subject> findBySubscription() {
        final User user = userService.getLoggedIn();

        return subjectRepository
                .findAllByOrderByName()
                .stream()
                .filter(x -> x.isMandatory() || x.getUser().contains(user))
                .collect(Collectors.toList());
    }

    /**
     * Build all jobs in a swimlane.
     *
     * @param subject Subject
     * @param swimlane Swimlane name
     */
    public void buildSwimline(Subject subject, String swimlane) {
        List<Job> jobs = jobService.findBySubjectOrderByName(subject);

        //Identifies swimlane criteria.
        String criteria = "";
        Map<String, String> rules = subject.getSwimlane();

        for (Map.Entry<String, String> rule : rules.entrySet()) {
            if (rule.getKey().equals(swimlane)) {
                criteria = rule.getValue();
                break;
            }
        }

        //Build jobs that matchs swimlane criteria.
        for (Job job : jobs) {
            if (job.getName().matches(criteria)) {
                try {
                    if (!jenkinsService.build(job)) {
                        jobStatusService.updateFlow(job.getStatus(), Flow.ERROR);
                        jobNotificationService.notify(job, true);
                    } else {
                        jobStatusService.updateFlow(job.getStatus(), Flow.QUEUED);
                    }

                } catch (Exception ex) {
                    LOG.log(Level.ERROR, "Fail building job " + job.getName() + ".", ex);
                }

            }
        }
    }
}
