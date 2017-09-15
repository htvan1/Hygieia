package com.capitalone.dashboard.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.CollectionUtils;

/**
 * Collector implementation for LvDM that stores LvDM server URLs.
 */
public class LvDMCollector extends Collector {
    private List<String> lvDMServers = new ArrayList<>();
    private List<String> niceNames = new ArrayList<>();

    public List<String> getLvDMServers() {
        return lvDMServers;
    }
    
    public List<String> getNiceNames() {
    	return niceNames;
    }

    public static LvDMCollector prototype(List<String> servers, List<String> niceNames) {
        LvDMCollector protoType = new LvDMCollector();
        protoType.setName("LvDM");
        protoType.setCollectorType(CollectorType.Deployment);
        protoType.setOnline(true);
        protoType.setEnabled(true);
        protoType.getLvDMServers().addAll(servers);
        if (!CollectionUtils.isEmpty(niceNames)) {
            protoType.getNiceNames().addAll(niceNames);
        }
        return protoType;
    }
}
