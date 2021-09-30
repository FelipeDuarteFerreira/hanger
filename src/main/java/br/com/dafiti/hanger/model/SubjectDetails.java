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

/**
 *
 * @author Guilherme Almeida
 */
public class SubjectDetails {

    private Subject subject;
    private int waiting;
    private int building;
    private int success;
    private int warning;
    private int failure;
    private int total;
    private float waitingPercent;
    private float successPercent;
    private float warningPercent;
    private float failurePercent;
    private float buildingPercent;

    public SubjectDetails(
            Subject subject,
            int building,
            int success,
            int warning,
            int failure,
            int total) {

        this.subject = subject;
        this.total = total;
        this.building = building;
        this.success = success;
        this.warning = warning;
        this.failure = failure;
        this.waiting = total - (building + success + warning + failure);

        this.successPercent = (((float) success / (total == 0 ? 1 : total)) * 100);
        this.warningPercent = (((float) (warning) / (total == 0 ? 1 : total)) * 100);
        this.failurePercent = (((float) (failure) / (total == 0 ? 1 : total)) * 100);
        this.buildingPercent = (((float) building / (total == 0 ? 1 : total)) * 100);
        this.waitingPercent = (100 - (this.successPercent + this.warningPercent + this.failurePercent + this.buildingPercent));
    }

    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public int getWaiting() {
        return waiting;
    }

    public void setWaiting(int waiting) {
        this.waiting = waiting;
    }

    public int getBuilding() {
        return building;
    }

    public void setBuilding(int building) {
        this.building = building;
    }

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    public int getWarning() {
        return warning;
    }

    public void setWarning(int warning) {
        this.warning = warning;
    }

    public int getFailure() {
        return failure;
    }

    public void setFailure(int failure) {
        this.failure = failure;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public float getWaitingPercent() {
        return waitingPercent;
    }

    public void setWaitingPercent(float waitingPercent) {
        this.waitingPercent = waitingPercent;
    }

    public float getSuccessPercent() {
        return successPercent;
    }

    public void setSuccessPercent(float successPercent) {
        this.successPercent = successPercent;
    }

    public float getWarningPercent() {
        return warningPercent;
    }

    public void setWarningPercent(float warningPercent) {
        this.warningPercent = warningPercent;
    }

    public float getBuildingPercent() {
        return buildingPercent;
    }

    public void setBuildingPercent(float buildingPercent) {
        this.buildingPercent = buildingPercent;
    }

    public float getFailurePercent() {
        return failurePercent;
    }

    public void setFailurePercent(float failurePercent) {
        this.failurePercent = failurePercent;
    }
}
