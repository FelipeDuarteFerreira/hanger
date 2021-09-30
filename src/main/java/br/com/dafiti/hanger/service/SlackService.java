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

import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestTemplate;

/**
 * Manage Slack API.
 *
 * @author Helio Leal
 *
 */
@Service
public class SlackService {

    private final ConfigurationService configurationService;
    private SlackSession slackSession;
    private static final Logger LOG = LogManager.getLogger(SlackService.class.getName());

    @Autowired
    public SlackService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    /**
     * Verify if slack session is connected.
     *
     * @return Identify if is connected.
     */
    private boolean isConnected() {
        return (this.slackSession != null
                && this.slackSession.isConnected());
    }

    /**
     * Connect to Slack API
     *
     * @return connected Identify if is connected
     */
    private boolean connect() {
        boolean connected = this.isConnected();

        if (!connected) {
            try {
                if (!configurationService.findByParameter("SLACK_BOT_TOKEN").getValue().isEmpty()) {
                    this.slackSession = SlackSessionFactory.createWebSocketSlackSession(configurationService.findByParameter("SLACK_BOT_TOKEN").getValue());
                    this.slackSession.connect();
                    connected = this.slackSession.isConnected();
                }
            } catch (IOException ex) {
                LOG.log(Level.ERROR, "Fail connecting to slack", ex);
            }
        }

        return connected;
    }

    /**
     * Get the slack channel list.
     *
     * @return slack channel list.
     */
    @Cacheable(value = "slackChannels")
    public Set<String> getChannels() {
        Set<String> channels = new HashSet();

        if (this.connect()) {
            Collection<SlackChannel> slackChannels = slackSession.getChannels();

            slackChannels.stream().forEach((channel) -> {
                if (channel.getName() != null) {
                    channels.add(channel.getName());
                }
            });
        }

        return channels;
    }

    /**
     * send method overload
     *
     * @param message Slack message.
     */
    public void send(String message) {
        this.send(message, new HashSet());
    }

    /**
     * Send a message to slack from a incoming WebHook.
     *
     * @param payload JSON payload.
     * @return HTTP Response.
     */
    @Async
    public String send(JSONObject payload) {
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<String> request = new HttpEntity<>(payload.toString());
        return restTemplate.postForObject(configurationService.findByParameter("SLACK_WEBHOOK_URL").getValue(), request, String.class);
    }

    /**
     * If connected to Slack API, send a message to a channel.
     *
     * @param message Slack message.
     * @param channels Slack channels.
     */
    @Async
    public void send(String message, Set<String> channels) {
        SlackChannel slackChannel;

        if (this.connect()) {
            if (channels.isEmpty()) {
                channels.add(configurationService.findByParameter("SLACK_CHANNEL").getValue());
            }

            for (String channel : channels) {
                slackChannel = slackSession.findChannelByName(channel);

                if (slackChannel != null) {
                    slackSession.sendMessage(slackChannel, message);
                }
            }
        }
    }

    @Caching(evict = {
        @CacheEvict(value = "slackChannels", allEntries = true)})
    public void refresh() {
        try {
            if (this.slackSession != null) {
                this.slackSession.disconnect();
            }
        } catch (IOException ex) {
            LOG.log(Level.ERROR, "Fail disconnecting from slack", ex);
        }
    }

}
