package com.alibaba.csp.sentinel.dashboard.repository.metric;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.MetricEntity;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.influxdb.MetricPO;
import com.alibaba.csp.sentinel.dashboard.influxdb.InfluxDBConfig;
import com.alibaba.csp.sentinel.dashboard.influxdb.InfluxDBUtils;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.dsl.Flux;
import com.influxdb.query.dsl.functions.restriction.Restrictions;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import com.influxdb.client.write.Point;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: berwin
 * @Date: 2023/05/18/14:20
 * @Description:
 */
@Repository("influxDBMetricsRepository")
public class InfluxDBMetricsRepository implements MetricsRepository<MetricEntity>{

    private final Logger LOGGER = LoggerFactory.getLogger(InfluxDBMetricsRepository.class);
    /**时间格式*/
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

    /**北京时间领先UTC时间8小时 UTC: Universal Time Coordinated,世界统一时间*/
    private static final Integer UTC_8 = 8;

    /*table*/
    private static final String measurement = "sentinel_metric";

    @Autowired
    private InfluxDBConfig influxDBConfig;

    @Autowired
    private InfluxDBUtils influxDBUtils;

    @Override
    public void save(MetricEntity metric) {
        if (metric == null || StringUtil.isBlank(metric.getApp())) {
            return;
        }

        influxDBUtils.insert(new InfluxDBUtils.InfluxDBInsertCallback() {
            @Override
            public void doCallBack(InfluxDBClient influxDBClient) {
                if (metric.getId() == null) {
                    metric.setId(System.currentTimeMillis());
                }
                doSave(influxDBClient, metric);
            }
        });
    }

    @Override
    public void saveAll(Iterable<MetricEntity> metrics) {
        if (metrics == null) {
            return;
        }

        Iterator<MetricEntity> iterator = metrics.iterator();
        boolean next = iterator.hasNext();
        if (!next) {
            return;
        }

        influxDBUtils.insert(new InfluxDBUtils.InfluxDBInsertCallback() {
            @Override
            public void doCallBack(InfluxDBClient influxDBClient) {
                while (iterator.hasNext()) {
                    MetricEntity metric = iterator.next();
                    if (metric.getId() == null) {
                        metric.setId(System.currentTimeMillis());
                    }
                    doSave(influxDBClient, metric);
                }
            }
        });
    }

    @Override
    public List<MetricEntity> queryByAppAndResourceBetween(String app, String resource, long startTime, long endTime) {
        List<MetricEntity> results = new ArrayList<MetricEntity>();
        if (StringUtil.isBlank(app)) {
            return results;
        }
        if (StringUtil.isBlank(resource)) {
            return results;
        }

        Restrictions restriction = Restrictions.and(
            Restrictions.tag("app").equal(app),
            Restrictions.tag("resource").equal(resource),
            Restrictions.measurement().equal(measurement)
        );

        Flux flux = Flux
            .from(influxDBConfig.bucket)
            .range(-30L,ChronoUnit.MINUTES)
            //.range(startTime,endTime)
            .filter(restriction)
            .pivot(new String[]{"_time"},new String[]{"_field"}, "_value");
        /*Flux flux = Flux.from("itnio-sentinel")
            .range(-30L, ChronoUnit.MINUTES)
            .filter(Restrictions.and(Restrictions.measurement().equal("sentinel_metric")))
            .pivot(new String[]{"_time"},new String[]{"_field"}, "_value");*/

        List<MetricPO> metricPOS = influxDBUtils.queryList(flux);

        if (CollectionUtils.isEmpty(metricPOS)) {
            return results;
        }

        for (MetricPO metricPO : metricPOS) {
            results.add(convertToMetricEntity(metricPO));
        }

        return results;
    }

    @Override
    public List<String> listResourcesOfApp(String app) {
        List<String> results = new ArrayList<>();
        if (StringUtil.isBlank(app)) {
            return results;
        }

        Restrictions restriction = Restrictions.and(
            Restrictions.tag("app").equal(app),
            Restrictions.measurement().equal(measurement)
        );

        Flux flux = Flux
            .from(influxDBConfig.bucket)
            .range(-30L,ChronoUnit.MINUTES)
            .filter(restriction)
            .pivot(new String[]{"_time"},new String[]{"_field"}, "_value");
        /*Flux flux = Flux.from("itnio-sentinel")
            .range(-30L, ChronoUnit.MINUTES)
            .filter(Restrictions.and(Restrictions.measurement().equal("sentinel_metric")))
            .pivot(new String[]{"_time"},new String[]{"_field"}, "_value");*/

        List<MetricPO> metricPOS = influxDBUtils.queryList(flux);

        if (CollectionUtils.isEmpty(metricPOS)) {
            return results;
        }

        List<MetricEntity> metricEntities = new ArrayList<MetricEntity>();
        for (MetricPO metricPO : metricPOS) {
            metricEntities.add(convertToMetricEntity(metricPO));
        }

        Map<String, MetricEntity> resourceCount = new HashMap<>(32);

        for (MetricEntity metricEntity : metricEntities) {
            String resource = metricEntity.getResource();
            if (resourceCount.containsKey(resource)) {
                MetricEntity oldEntity = resourceCount.get(resource);
                oldEntity.addPassQps(metricEntity.getPassQps());
                oldEntity.addRtAndSuccessQps(metricEntity.getRt(), metricEntity.getSuccessQps());
                oldEntity.addBlockQps(metricEntity.getBlockQps());
                oldEntity.addExceptionQps(metricEntity.getExceptionQps());
                oldEntity.addCount(1);
            } else {
                resourceCount.put(resource, MetricEntity.copyOf(metricEntity));
            }
        }

        // Order by last minute b_qps DESC.
        return resourceCount.entrySet()
            .stream()
            .sorted((o1, o2) -> {
                MetricEntity e1 = o1.getValue();
                MetricEntity e2 = o2.getValue();
                int t = e2.getBlockQps().compareTo(e1.getBlockQps());
                if (t != 0) {
                    return t;
                }
                return e2.getPassQps().compareTo(e1.getPassQps());
            })
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private MetricEntity convertToMetricEntity(MetricPO metricPO) {
        MetricEntity metricEntity = new MetricEntity();

        metricEntity.setId(metricPO.getId());
        metricEntity.setGmtCreate(new Date(metricPO.getGmtCreate()));
        metricEntity.setGmtModified(new Date(metricPO.getGmtModified()));
        metricEntity.setApp(metricPO.getApp());
        metricEntity.setTimestamp(Date.from(metricPO.getTime()));
        metricEntity.setResource(metricPO.getResource());
        metricEntity.setPassQps(metricPO.getPassQps());
        metricEntity.setSuccessQps(metricPO.getSuccessQps());
        metricEntity.setBlockQps(metricPO.getBlockQps());
        metricEntity.setExceptionQps(metricPO.getExceptionQps());
        metricEntity.setRt(metricPO.getRt());
        metricEntity.setCount(metricPO.getCount());

        return metricEntity;
    }

    private void doSave(InfluxDBClient influxDBClient, MetricEntity metric) {
        LOGGER.info("doSave metric:{}", metric);

        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();

        Point point = Point.measurement(measurement)
            .time(metric.getTimestamp().getTime(), WritePrecision.MS)// 因InfluxDB默认UTC时间，按北京时间算写入数据加8小时
            .addTag("app", metric.getApp())
            .addTag("resource", metric.getResource())
            .addField("id", metric.getId())
            .addField("gmtCreate", metric.getGmtCreate().getTime())
            .addField("gmtModified", metric.getGmtModified().getTime())
            .addField("passQps", metric.getPassQps())
            .addField("successQps", metric.getSuccessQps())
            .addField("blockQps", metric.getBlockQps())
            .addField("exceptionQps", metric.getExceptionQps())
            .addField("rt", metric.getRt())
            .addField("count", metric.getCount())
            .addField("resourceCode", metric.getResourceCode());

        writeApi.writePoint(point);
    }
}
