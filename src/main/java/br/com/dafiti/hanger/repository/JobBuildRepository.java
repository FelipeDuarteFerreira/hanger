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
package br.com.dafiti.hanger.repository;

import br.com.dafiti.hanger.model.Job;
import br.com.dafiti.hanger.model.JobBuild;
import br.com.dafiti.hanger.model.JobBuildMetric;
import br.com.dafiti.hanger.option.Phase;
import java.util.Date;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface JobBuildRepository extends CrudRepository<JobBuild, Long> {

    /**
     * Get a job build elapsed time.
     *
     * @param job Job
     * @param buildNumber Build number
     * @return Elapsed time
     */
    @Query("select "
            + "     timediff( max(date), min(date) ) "
            + " from "
            + "     JobBuild j "
            + " where "
            + "     j.job = :job "
            + " and "
            + "     j.number = :buildNumber")
    String findJobBuildTime(
            @Param("job") Job job,
            @Param("buildNumber") int buildNumber);

    /**
     * Get job build count by hour.
     *
     * @param phase Phase
     * @param startDate Start Date
     * @param endDate End Date
     * @return Job build count by hour
     */
    @Query("select "
            + " new br.com.dafiti.hanger.model.JobBuildMetric"
            + "( "
            + "     b.job"
            + "     , hour( b.date ), count(1) "
            + ") "
            + " from "
            + "     JobBuild b "
            + " where "
            + "     b.phase = :phase"
            + " and "
            + "     b.date between :startdate and :enddate"
            + " group by "
            + "     b.job, hour( b.date )")

    List<JobBuildMetric> findJobBuildCountByHour(
            @Param("phase") Phase phase,
            @Param("startdate") Date startDate,
            @Param("enddate") Date endDate);

    /**
     * Get job build history.
     *
     * @param job Job list filter
     * @param startDate Start Date
     * @param endDate End Date
     * @return Build information by number.
     */
    @Query("select "
            + "new br.com.dafiti.hanger.model.JobBuildMetric "
            + "( "
            + "     b.job "
            + "     , min( b.date ) "
            + "     , case when max(case when b.phase = 'STARTED' then b.date end) is null then now() else max(case when b.phase = 'STARTED' then b.date end) end"
            + "     , case when max(case when b.phase = 'FINALIZED' then b.date end) is null then now() else max(case when b.phase = 'FINALIZED' then b.date end) end"
            + "     , case when min( b.status ) = 'SUCCESS' then true else false end"
            + ") "
            + " from JobBuild b "
            + " where "
            + "     b.job in (:job) "
            + "     and "
            + "     b.date between :startdate and :enddate"
            + " group by b.job,  b.number")
    List<JobBuildMetric> findBuildHistory(
            @Param("job") List<Job> job,
            @Param("startdate") Date startDate,
            @Param("enddate") Date endDate);
}
