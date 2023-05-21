package com.alibaba.csp.sentinel.dashboard.influxdb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: berwin
 * @Date: 2023/05/18/17:33
 * @Description:
 */
@Configuration
public class InfluxDBConfig {

    @Value("${influxdb.url}")
    public  String url;

    @Value("${influxdb.org}")
    public  String org;

    @Value("${influxdb.token}")
    public  String token;
    @Value("${influxdb.bucket}")
    public  String bucket;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(String org) {
        this.org = org;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
}
