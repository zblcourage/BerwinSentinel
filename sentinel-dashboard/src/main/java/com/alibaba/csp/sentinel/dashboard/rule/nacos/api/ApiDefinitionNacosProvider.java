package com.alibaba.csp.sentinel.dashboard.rule.nacos.api;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.gateway.ApiDefinitionEntity;
import com.alibaba.csp.sentinel.dashboard.rule.nacos.NacosConfigUtil;
import com.alibaba.csp.sentinel.dashboard.rule.nacos.RuleNacosProvider;
import org.springframework.stereotype.Component;


@Component("apiDefinitionNacosProvider")
public class ApiDefinitionNacosProvider extends RuleNacosProvider<ApiDefinitionEntity> {

    @Override
    public String getDataIdPostfix() {
        return NacosConfigUtil.GATEWAY_API_FLOW_DATA_ID_POSTFIX;
    }
}
