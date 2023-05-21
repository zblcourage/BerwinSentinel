package com.alibaba.csp.sentinel.dashboard.influxdb;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.influxdb.MetricPO;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.influxdb.query.dsl.Flux;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: berwin
 * @Date: 2023/05/18/14:10
 * @Description:
 */
@Component
public class InfluxDBUtils {
    private final Logger LOGGER = LoggerFactory.getLogger(InfluxDBUtils.class);

    @Autowired
    private InfluxDBConfig influxDBConfig;

    public <T> T process(InfluxDBCallback callback) {
        InfluxDBClient influxDBClient = null;
        T t = null;
        try {
            influxDBClient = InfluxDBClientFactory.create(influxDBConfig.url, influxDBConfig.token.toCharArray(), influxDBConfig.org, influxDBConfig.bucket);
            t = callback.doCallBack(influxDBClient);
        } catch (Exception e) {
            LOGGER.error("[process exception]", e);
        } finally {
            if (influxDBClient != null) {
                try {
                    influxDBClient.close();
                } catch (Exception e) {
                    LOGGER.error("[influxDB.close exception]", e);
                }
            }
        }
        return t;
    }

    public void insert(InfluxDBInsertCallback influxDBInsertCallback) {
        process(new InfluxDBCallback() {
            @Override
            public <T> T doCallBack(InfluxDBClient influxDBClient) {
                influxDBInsertCallback.doCallBack(influxDBClient);
                return null;
            }
        });

    }

    public List<MetricPO> query(InfluxDBQueryCallback influxDBQueryCallback) {
        return process(new InfluxDBCallback() {
            @Override
            public <T> T doCallBack(InfluxDBClient influxDBClient) {
                List<MetricPO> queryResult = influxDBQueryCallback.doCallBack(influxDBClient);
                return (T) queryResult;
            }
        });
    }

    public <T> List<T> queryList(Flux flux) {
        List<MetricPO> queryResult = query(new InfluxDBQueryCallback() {
            @Override
            public List<MetricPO> doCallBack(InfluxDBClient influxDBClient) {
                List<MetricPO> queryResult = influxDBClient.getQueryApi().query(flux.toString(),MetricPO.class);
                return queryResult;
            }
        });

        /*//List<FluxTable>对象转换为clasz列表
        List<T> resultlist = new ArrayList<T>();
        for (FluxTable fluxTable : queryResult) {
            List<FluxRecord> records = fluxTable.getRecords();
            for (FluxRecord fluxRecord : records) {
                resultlist.add((T) convertToMetricEntity(fluxRecord));
            }
        }*/
        return (List<T>) queryResult;
    }


    private MetricPO convertToMetricEntity(FluxRecord fluxRecord) {
        MetricPO metricPO = new MetricPO();

        metricPO.setId(Long.parseLong(fluxRecord.getValueByKey("id").toString()));
        metricPO.setGmtCreate(Long.parseLong(fluxRecord.getValueByKey("gmtCreate").toString()));
        metricPO.setGmtModified(Long.parseLong(fluxRecord.getValueByKey("gmtModified").toString()));
        metricPO.setApp(fluxRecord.getValueByKey("app").toString());
        metricPO.setTime(fluxRecord.getTime());
        metricPO.setResource(fluxRecord.getValueByKey("resource").toString());
        metricPO.setPassQps(Long.parseLong(fluxRecord.getValueByKey("passQps").toString()));
        metricPO.setSuccessQps(Long.parseLong(fluxRecord.getValueByKey("successQps").toString()));
        metricPO.setBlockQps(Long.parseLong(fluxRecord.getValueByKey("blockQps").toString()));
        metricPO.setExceptionQps(
            Long.parseLong(fluxRecord.getValueByKey("exceptionQps").toString()));
        metricPO.setRt(Long.parseLong(fluxRecord.getValueByKey("rt").toString()));
        metricPO.setCount(Integer.parseInt(fluxRecord.getValueByKey("count").toString()));
        metricPO.setResourceCode(
            Integer.parseInt(fluxRecord.getValueByKey("resourceCode").toString()));

        return metricPO;
    }


    public interface InfluxDBCallback {
        <T> T doCallBack(InfluxDBClient influxDBClient);
    }

    public interface InfluxDBInsertCallback {
        void doCallBack(InfluxDBClient influxDBClient);
    }

    public interface InfluxDBQueryCallback {
        List<MetricPO> doCallBack(InfluxDBClient influxDBClient);
    }
}
