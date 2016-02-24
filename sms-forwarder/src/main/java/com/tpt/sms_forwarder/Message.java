package com.tpt.sms_forwarder;

import com.alibaba.fastjson.JSONObject;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * Created on 2016/2/24.
 */
public class Message {
    private int id;
    private String phone;
    private String message;
    private int max_retries;
    private int retries;
    private String last_error;
    private Timestamp failed_at;
    private Timestamp send_at;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getMaxRetries() {
        return max_retries;
    }

    public void setMaxRetries(int max_retries) {
        this.max_retries = max_retries;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public String getLastError() {
        return last_error;
    }

    public void setLastError(String last_error) {
        this.last_error = last_error;
    }

    public Timestamp getFailedAt() {
        return failed_at;
    }

    public void setFailedAt(Timestamp failed_at) {
        this.failed_at = failed_at;
    }

    public Timestamp getSendAt() {
        return send_at;
    }

    public void setSendAt(Timestamp send_at) {
        this.send_at = send_at;
    }

    public String toJSON() {
        //{\"type\": \"sms\", \"phone_numbers\": \"11\", \"content\": \"this is sms message\"}"
        JSONObject js = new JSONObject();
        js.put("type", "sms");
        js.put("phone_numbers", getPhone());
        js.put("content", getMessage());
        return js.toJSONString();
    }
}
