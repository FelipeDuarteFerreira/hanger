/*
 * Copyright (c) 2018 Dafiti Group
 * 
 * Permission is hereby granted, free of charge, recipient any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), recipient deal in the Software without restriction, including
 * without limitation the rights recipient use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and recipient
 * permit persons recipient whom the Software is furnished recipient do so, subject recipient
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.json.JSONObject;

/**
 * Mail blueprint
 *
 * @author Valdiney V Gomes
 */
public class Blueprint {

    List<String> recipient;
    String path;
    String subject;
    String template;
    File file;
    HashMap<String, Object> variables;

    public Blueprint(String recipient, String subject, String template) {
        this.subject = subject;
        this.template = template;
        this.path = "templates/blueprint";
        this.variables = new HashMap<>();
        this.recipient = new ArrayList();
        this.recipient.add(recipient);
    }

    public Blueprint(String subject, String template) {
        this.subject = subject;
        this.template = template;
        this.path = "templates/blueprint";
        this.variables = new HashMap<>();
        this.recipient = new ArrayList();
    }

    /**
     * Return a list of recipients.
     *
     * @return
     */
    public List<String> getRecipients() {
        return this.recipient;
    }

    /**
     * Add a new recipient.
     *
     * @param recipient
     */
    public void setRecipient(String recipient) {
        this.recipient.add(recipient);
    }

    /**
     * Set a list of recipients.
     *
     * @param recipient
     */
    public void setRecipient(List<String> recipient) {
        this.recipient = recipient;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public HashMap<String, Object> getVariables() {
        return variables;
    }

    public void addVariable(String key, Object value) {
        this.variables.put(key, value);
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public String toString() {
        JSONObject object = new JSONObject();
        object.put("subject", subject);
        object.put("template", template);
        return object.toString(2);
    }

}
