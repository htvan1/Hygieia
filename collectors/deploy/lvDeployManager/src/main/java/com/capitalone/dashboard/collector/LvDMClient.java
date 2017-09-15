package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.Environment;
import com.capitalone.dashboard.model.EnvironmentComponent;
import com.capitalone.dashboard.model.EnvironmentStatus;
import com.capitalone.dashboard.model.LvDMApplication;
import com.capitalone.dashboard.model.LvDMEnvResCompData;

import java.util.List;

/**
 * Client for fetching information from Learnvest Deploy Manager
 */
public interface LvDMClient {

    /**
     * Fetches all {@link LvDMApplication}s for a given instance URL.
     *
     * @param instanceUrl instance URL
     * @return list of {@link LvDMApplication}s
     */
    List<LvDMApplication> getApplications(String instanceUrl);

    /**
     * Fetches all {@link Environment}s for a given {@link LvDMApplication}.
     *
     * @param application a {@link LvDMApplication}
     * @return list of {@link Environment}s
     */
    List<Environment> getEnvironments(LvDMApplication application);

    /**
     * Fetches all {@link EnvironmentComponent}s for a given {@link LvDMApplication} and {@link Environment}.
     *
     * @param application a {@link LvDMApplication}
     * @param environment an {@link Environment}
     * @return list of {@link EnvironmentComponent}s
     */
    List<EnvironmentComponent> getEnvironmentComponents(LvDMApplication application, Environment environment);

    /**
     * Fetches all {@link EnvironmentStatus}es for a given {@link LvDMApplication} and {@link Environment}.
     *
     * @param application a {@link LvDMApplication}
     * @param environment an {@link Environment}
     * @return list of {@link EnvironmentStatus}es
     */
    List<LvDMEnvResCompData> getEnvironmentResourceStatusData(LvDMApplication application, Environment environment);
}
