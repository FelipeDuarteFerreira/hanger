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

import br.com.dafiti.hanger.model.SubjectDetails;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 *
 * @author Guilherme Oliveira
 */
@Service
public class SubjectNotificationService {

    private final SubjectService subjectService;
    private final SlackService slackService;
    private final SubjectDetailsService subjectDetailsService;
    private final ConfigurationService configurationService;

    @Autowired
    public SubjectNotificationService(
            SubjectService subjectService,
            SubjectDetailsService subjectDetailsService,
            SlackService slackService,
            ConfigurationService configurationService) {

        this.subjectService = subjectService;
        this.slackService = slackService;
        this.subjectDetailsService = subjectDetailsService;
        this.configurationService = configurationService;
    }

    /**
     * Send all subject status status on default Slack.
     */
    @Scheduled(cron = "0 0 0-23 * * ?")
    public void notifySubjectList() {
        List<SubjectDetails> subjectDetails
                = subjectDetailsService.getDetailsOf(subjectService.findAllByOrderByName());

        for (SubjectDetails detail : subjectDetails) {
            //Identify if should notify this subject. 
            if (detail.getSubject().isNotified()) {
                //Identify the channels to notify. 
                Set<String> channels = detail.getSubject().getChannel();

                //Identify if should notify only the default channel. 
                if (channels.isEmpty()) {
                    channels.add(configurationService.findByParameter("SLACK_CHANNEL").getValue());
                }

                //Notify each channel. 
                for (String channel : channels) {
                    JSONObject payload = new JSONObject();
                    JSONArray metrics = new JSONArray();
                    String color = "";

                    //Identify the subject metrics. 
                    JSONObject waiting = new JSONObject();
                    waiting.put("title", "Waiting");
                    waiting.put("value", detail.getWaitingPercent() + "%");
                    waiting.put("short", true);
                    metrics.put(waiting);

                    JSONObject success = new JSONObject();
                    success.put("title", "Success");
                    success.put("value", detail.getSuccessPercent() + "%");
                    success.put("short", true);
                    metrics.put(success);

                    JSONObject failure = new JSONObject();
                    failure.put("title", "Failure");
                    failure.put("value", detail.getFailurePercent() + "%");
                    failure.put("short", true);
                    metrics.put(failure);

                    JSONObject warning = new JSONObject();
                    warning.put("title", "Warning");
                    warning.put("value", detail.getWarningPercent() + "%");
                    warning.put("short", true);
                    metrics.put(warning);

                    JSONObject building = new JSONObject();
                    building.put("title", "Building");
                    building.put("value", detail.getBuildingPercent() + "%" + " (" + (detail.getBuilding()) + " jobs)");
                    building.put("short", true);
                    metrics.put(building);

                    //Identify subject color.
                    if (detail.getFailurePercent() > 0) {
                        color = "#cd5c5c";
                    } else if (detail.getSuccessPercent() == 100) {
                        color = "#20b2aa";
                    } else if (detail.getWarningPercent() == 100) {
                        color = "#ffa500";
                    }

                    //Build the payload. 
                    payload.put("pretext", "*" + detail.getSubject().getName() + "* hourly report");
                    payload.put("channel", channel);
                    payload.put("fields", metrics);
                    payload.put("color", color);
                    payload.put("mrkdwn_in", "[\"pretext\"]");

                    //Send the notification. 
                    slackService.send(payload);
                }
            }
        }
    }
}
