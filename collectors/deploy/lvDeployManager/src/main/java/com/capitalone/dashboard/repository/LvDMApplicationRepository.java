package com.capitalone.dashboard.repository;

import com.capitalone.dashboard.model.LvDMApplication;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

/**
 * Repository for {@link LvDMApplication}s.
 */
public interface LvDMApplicationRepository extends BaseCollectorItemRepository<LvDMApplication> {

    /**
     * Find a {@link LvDMApplication} by UDeploy instance URL and UDeploy application id.
     *
     * @param collectorId ID of the {@link com.capitalone.dashboard.model.LvDMCollector}
     * @param instanceUrl UDeploy instance URL
     * @param applicationId UDeploy application ID
     * @return a {@link LvDMApplication} instance
     */
    @Query(value="{ 'collectorId' : ?0, options.instanceUrl : ?1, options.applicationId : ?2}")
    LvDMApplication findLvDMApplication(ObjectId collectorId, String instanceUrl, String applicationId);

    /**
     * Finds all {@link LvDMApplication}s for the given instance URL.
     *
     * @param collectorId ID of the {@link com.capitalone.dashboard.model.LvDMCollector}
     * @param instanceUrl UDeploy instance URl
     * @return list of {@link LvDMApplication}s
     */
    @Query(value="{ 'collectorId' : ?0, options.instanceUrl : ?1, enabled: true}")
    List<LvDMApplication> findEnabledApplications(ObjectId collectorId, String instanceUrl);
}
