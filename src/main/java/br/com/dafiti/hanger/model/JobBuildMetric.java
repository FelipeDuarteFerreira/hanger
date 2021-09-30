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
package br.com.dafiti.hanger.model;

import java.util.Date;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 *
 * @author Valdiney V GOMES
 */
public class JobBuildMetric {

    private Job job;
    private int hour;
    private Long build;
    private Date queueDate;
    private Date startDate;
    private Date finishDate;
    private boolean success;

    public JobBuildMetric(
            Job job,
            int hour,
            Long build) {

        this.job = job;
        this.hour = hour;
        this.build = build;
    }

    public JobBuildMetric(
            Job job,
            Date queueDate,
            Date startDate,
            Date finishDate,
            boolean success) {

        this.job = job;
        this.queueDate = queueDate;

        if (startDate.after(finishDate)) {
            this.startDate = finishDate;
        } else {
            this.startDate = startDate;
        }

        this.finishDate = finishDate;
        this.success = success;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getHour() {
        return hour;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    public Long getBuild() {
        return build;
    }

    public void setBuild(Long build) {
        this.build = build;
    }

    public Date getQueueDate() {
        return queueDate;
    }

    public void setQueueDate(Date queueDate) {
        this.queueDate = queueDate;
    }

    public Date getFinishDate() {
        return finishDate;
    }

    public void setFinishDate(Date finishDate) {
        this.finishDate = finishDate;
    }

    public Date getStartDate() {
        return startDate;
    }

    public Long getQueueTimeInMinutes() {
        return new Duration(
                new DateTime(this.queueDate),
                new DateTime(this.startDate)).getStandardMinutes();
    }

    public Long getDurationTimeInMinutes() {
        return new Duration(
                new DateTime(this.queueDate),
                new DateTime(this.finishDate)).getStandardMinutes();
    }

    public Long getBuildTimeInMinutes() {
        return new Duration(
                new DateTime(this.startDate),
                new DateTime(this.finishDate)).getStandardMinutes();
    }

    public Double getQueuePercentage() {
        Double progress = 0.0;

        if (this.getQueueTimeInMinutes() != 0) {
            progress = (double) this.getQueueTimeInMinutes() / (double) this.getDurationTimeInMinutes();
        }

        return progress;
    }
}
