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
import br.com.dafiti.hanger.model.JobBuildMetric;
import br.com.dafiti.hanger.option.Phase;
import br.com.dafiti.hanger.repository.JobBuildRepository;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class JobBuildGraphService {

    private final JobBuildRepository jobBuildRepository;
    private final AuditorService auditorService;

    @Autowired
    public JobBuildGraphService(JobBuildRepository jobBuildRepository, AuditorService auditorService) {
        this.jobBuildRepository = jobBuildRepository;
        this.auditorService = auditorService;
    }

    /**
     * Find job build count by hour.
     *
     * @param phase Phase
     * @param startDate Start Date
     * @param endDate End Date
     *
     * @return Job build count by hour list.
     */
    @Cacheable(value = "jobBuildByHour", key = "{#phase, #startDate, #endDate}")
    public List<JobBuildMetric> findJobBuildCountByHour(
            Phase phase,
            Date startDate,
            Date endDate) {

        Date initialDate = new DateTime(startDate)
                .withTimeAtStartOfDay()
                .toDate();

        Date finalDate = new DateTime(endDate)
                .withTimeAtStartOfDay()
                .plusHours(23)
                .plusMinutes(59)
                .plusSeconds(59)
                .toDate();

        return jobBuildRepository.findJobBuildCountByHour(phase, initialDate, finalDate);
    }

    /**
     * Get the job build summary by job and hour in a 24 hours window.
     *
     * @param phase Phase
     * @param dateFrom Date From
     * @param dateTo Date To
     * @return Job build summary by job and hour.
     */
    public Map<Job, Long[]> getJobBuildDetail(
            Phase phase,
            Date dateFrom,
            Date dateTo) {

        Map<Job, Long[]> summary = new HashMap();
        List<JobBuildMetric> builds = this.findJobBuildCountByHour(phase, dateFrom, dateTo);

        builds.stream().forEach((data) -> {
            Job key = data.getJob();
            Long[] value = summary.containsKey(key) ? summary.get(key) : new Long[24];
            value[data.getHour()] = data.getBuild();
            summary.put(key, value);

        });

        return summary;
    }

    /**
     * Get the job build total by job and hour in a 24 hours window.
     *
     * @param phase Phase
     * @param dateFrom Date From
     * @param dateTo Date To
     * @return Job build summary summary by job and hour.
     */
    public Long[] getJobBuildTotal(
            Phase phase,
            Date dateFrom,
            Date dateTo) {

        Long[] value = new Long[24];
        List<JobBuildMetric> builds = this.findJobBuildCountByHour(phase, dateFrom, dateTo);

        builds.stream().forEach((data) -> {
            Long build = value[data.getHour()];

            if (build == null) {
                value[data.getHour()] = data.getBuild();
            } else {
                value[data.getHour()] += data.getBuild();
            }
        });

        for (int i = 0; i < value.length; i++) {
            if (value[i] == null) {
                value[i] = 0L;
            }
        }

        return value;
    }

    /**
     * Find job build history
     *
     * @param job Job
     * @param startDate Start Date
     * @param endDate End Date
     * @return job time by phase and nunber
     */
    public List<JobBuildMetric> findBuildHistory(
            List<Job> job,
            Date startDate,
            Date endDate) {

        List<JobBuildMetric> metrics = new ArrayList();

        if (job != null && !job.isEmpty()) {
            metrics = jobBuildRepository.findBuildHistory(job, startDate, endDate);
        }

        return metrics;
    }

    /**
     * Get the gantt graph data.
     *
     * @param jobs Job
     * @param dateFrom Date from
     * @param dateTo Date to
     * @return Gantt
     */
    public List getDHTMLXGanttData(
            List<Job> jobs,
            Date dateFrom,
            Date dateTo) {

        auditorService.publish("GANTT_RUN");

        Long surrogateID = 0L;
        List<DHTMLXGantt> data = new ArrayList();

        //Add all parents to gantt. 
        if (jobs != null) {
            List<JobBuildMetric> metrics = this.findBuildHistory(jobs, dateFrom, dateTo);

            for (Job job : jobs) {
                for (JobBuildMetric metric : metrics) {
                    if (job.getId().equals(metric.getJob().getId())) {
                        data.add(new DHTMLXGantt(
                                metric.getJob().getId().toString(),
                                metric.getJob().getDisplayName(),
                                metric.getQueueDate().toString(),
                                0L,
                                1.0,
                                true,
                                "",
                                "#D6DBE1",
                                ""
                        ));

                        break;
                    }
                }
            }

            //Add all children in the gantt.
            for (JobBuildMetric metric : metrics) {
                surrogateID++;

                data.add(new DHTMLXGantt(
                        surrogateID + "_",
                        metric.getQueueDate().toString().substring(0, 16) + " - " + metric.getFinishDate().toString().substring(0, 16),
                        metric.getQueueDate().toString(),
                        metric.getDurationTimeInMinutes(),
                        metric.getQueuePercentage(),
                        true,
                        metric.getJob().getId().toString(),
                        metric.isSuccess() ? "#3DB9D3" : "#DD424A",
                        "#E5E8EC"
                ));
            }
        }

        return data;
    }

    /**
     * DHTMLXGantt data format.
     */
    class DHTMLXGantt {

        private String id;
        private String text;
        private String start_date;
        private Long duration;
        private Double progress;
        private boolean open;
        private String parent;
        private String color;
        private String progressColor;

        public DHTMLXGantt(
                String id,
                String text,
                String start_date,
                Long duration,
                Double progress,
                boolean open,
                String parent,
                String color,
                String progressColor) {

            this.id = id;
            this.text = text;
            this.start_date = start_date;
            this.duration = duration;
            this.progress = progress;
            this.open = open;
            this.parent = parent;
            this.color = color;
            this.progressColor = progressColor;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getStart_date() {
            return start_date;
        }

        public void setStart_date(String start_date) {
            this.start_date = start_date;
        }

        public Long getDuration() {
            return duration;
        }

        public void setDuration(Long duration) {
            this.duration = duration;
        }

        public Double getProgress() {
            return progress;
        }

        public void setProgress(Double progress) {
            this.progress = progress;
        }

        public boolean isOpen() {
            return open;
        }

        public void setOpen(boolean open) {
            this.open = open;
        }

        public String getParent() {
            return parent;
        }

        public void setParent(String parent) {
            this.parent = parent;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public String getProgressColor() {
            return progressColor;
        }

        public void setProgressColor(String progressColor) {
            this.progressColor = progressColor;
        }
    }
}
