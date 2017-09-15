package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.*;
import com.capitalone.dashboard.model.LvDMApplication;
import com.capitalone.dashboard.model.LvDMCollector;
import com.capitalone.dashboard.model.LvDMEnvResCompData;
import com.capitalone.dashboard.repository.*;
import com.capitalone.dashboard.repository.LvDMApplicationRepository;
import com.capitalone.dashboard.repository.LvDMCollectorRepository;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects {@link EnvironmentComponent} and {@link EnvironmentStatus} data from
 * {@link LvDMApplication}s.
 */
@Component
public class LvDMCollectorTask extends CollectorTask<LvDMCollector> {
    @SuppressWarnings({"unused", "PMD.UnusedPrivateField"})
    private static final Logger LOGGER = LoggerFactory.getLogger(LvDMCollectorTask.class);

    private final LvDMCollectorRepository lvDMCollectorRepository;
    private final LvDMApplicationRepository lvDMApplicationRepository;
    private final LvDMClient lvDMClient;
    private final LvDMSettings lvDMSettings;

    private final EnvironmentComponentRepository envComponentRepository;
    private final EnvironmentStatusRepository environmentStatusRepository;

    private final ComponentRepository dbComponentRepository;

    @Autowired
    public LvDMCollectorTask(TaskScheduler taskScheduler,
                             LvDMCollectorRepository lvDMCollectorRepository,
                             LvDMApplicationRepository lvDMApplicationRepository,
                             EnvironmentComponentRepository envComponentRepository,
                             EnvironmentStatusRepository environmentStatusRepository,
                             LvDMSettings lvDMSettings, LvDMClient lvDMClient,
                             ComponentRepository dbComponentRepository) {
        super(taskScheduler, "LvDM");
        this.lvDMCollectorRepository = lvDMCollectorRepository;
        this.lvDMApplicationRepository = lvDMApplicationRepository;
        this.lvDMSettings = lvDMSettings;
        this.lvDMClient = lvDMClient;
        this.envComponentRepository = envComponentRepository;
        this.environmentStatusRepository = environmentStatusRepository;
        this.dbComponentRepository = dbComponentRepository;
    }

    @Override
    public LvDMCollector getCollector() {
        return LvDMCollector.prototype(lvDMSettings.getServers(), lvDMSettings.getNiceNames());
    }

    @Override
    public BaseCollectorRepository<LvDMCollector> getCollectorRepository() {
        return lvDMCollectorRepository;
    }

    @Override
    public String getCron() {
        return lvDMSettings.getCron();
    }

    @Override
    public void collect(LvDMCollector collector) {
        for (String instanceUrl : collector.getLvDMServers()) {

            logBanner(instanceUrl);

            long start = System.currentTimeMillis();

            clean(collector);

            addNewApplications(lvDMClient.getApplications(instanceUrl),
                    collector);
            updateData(enabledApplications(collector, instanceUrl));

            log("Finished", start);
        }
    }

    /**
     * Clean up unused deployment collector items
     *
     * @param collector the {@link LvDMCollector}
     */
    @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
    private void clean(LvDMCollector collector) {
        deleteUnwantedJobs(collector);
        Set<ObjectId> uniqueIDs = new HashSet<>();
        for (com.capitalone.dashboard.model.Component comp : dbComponentRepository
                .findAll()) {
            if (comp.getCollectorItems() == null || comp.getCollectorItems().isEmpty()) continue;
            List<CollectorItem> itemList = comp.getCollectorItems().get(
                    CollectorType.Deployment);
            if (itemList == null) continue;
            for (CollectorItem ci : itemList) {
                if (ci == null) continue;
                uniqueIDs.add(ci.getId());
            }
        }
        List<LvDMApplication> appList = new ArrayList<>();
        Set<ObjectId> udId = new HashSet< >();
        udId.add(collector.getId());
        for (LvDMApplication app : lvDMApplicationRepository.findByCollectorIdIn(udId)) {
            if (app != null) {
                app.setEnabled(uniqueIDs.contains(app.getId()));
                appList.add(app);
            }
        }
        lvDMApplicationRepository.save(appList);
    }

    private void deleteUnwantedJobs(LvDMCollector collector) {

        List<LvDMApplication> deleteAppList = new ArrayList<>();
        Set<ObjectId> udId = new HashSet<>();
        udId.add(collector.getId());
        for (LvDMApplication app : lvDMApplicationRepository.findByCollectorIdIn(udId)) {
            if (!collector.getLvDMServers().contains(app.getInstanceUrl()) ||
                    (!app.getCollectorId().equals(collector.getId()))) {
                deleteAppList.add(app);
            }
        }

        lvDMApplicationRepository.delete(deleteAppList);

    }

    private List<EnvironmentComponent> getEnvironmentComponent(List<LvDMEnvResCompData> dataList, Environment environment, LvDMApplication application) {
        List<EnvironmentComponent> returnList = new ArrayList<>();
        for (LvDMEnvResCompData data : dataList) {
            EnvironmentComponent component = new EnvironmentComponent();
            component.setComponentName(data.getComponentName());
            component.setCollectorItemId(data.getCollectorItemId());
            component.setComponentVersion(data
                    .getComponentVersion());
            component.setDeployed(data.isDeployed());
            component.setEnvironmentName(data
                    .getEnvironmentName());

            component.setEnvironmentName(environment.getName());
            component.setAsOfDate(data.getAsOfDate());
            String environmentURL = StringUtils.removeEnd(
                    application.getInstanceUrl(), "/")
                    + "/#environment/" + environment.getId();
            component.setEnvironmentUrl(environmentURL);

            returnList.add(component);
        }
        return returnList;
    }


    private List<EnvironmentStatus> getEnvironmentStatus(List<LvDMEnvResCompData> dataList) {
        List<EnvironmentStatus> returnList = new ArrayList<>();
        for (LvDMEnvResCompData data : dataList) {
            EnvironmentStatus status = new EnvironmentStatus();
            status.setCollectorItemId(data.getCollectorItemId());
            status.setComponentID(data.getComponentID());
            status.setComponentName(data.getComponentName());
            status.setEnvironmentName(data.getEnvironmentName());
            status.setOnline(data.isOnline());
            status.setResourceName(data.getResourceName());

            returnList.add(status);
        }
        return returnList;
    }


    /**
     * For each {@link LvDMApplication}, update the current
     * {@link EnvironmentComponent}s and {@link EnvironmentStatus}.
     *
     * @param lvDMApplications list of {@link LvDMApplication}s
     */
    private void updateData(List<LvDMApplication> lvDMApplications) {
        for (LvDMApplication application : lvDMApplications) {
            List<EnvironmentComponent> compList = new ArrayList<>();
            List<EnvironmentStatus> statusList = new ArrayList<>();
            long startApp = System.currentTimeMillis();

            for (Environment environment : lvDMClient
                    .getEnvironments(application)) {

                List<LvDMEnvResCompData> combinedDataList = lvDMClient
                        .getEnvironmentResourceStatusData(application,
                                environment);

                compList.addAll(getEnvironmentComponent(combinedDataList, environment, application));
                statusList.addAll(getEnvironmentStatus(combinedDataList));
            }
            if (!compList.isEmpty()) {
                List<EnvironmentComponent> existingComponents = envComponentRepository
                        .findByCollectorItemId(application.getId());
                envComponentRepository.delete(existingComponents);
                envComponentRepository.save(compList);
            }
            if (!statusList.isEmpty()) {
                List<EnvironmentStatus> existingStatuses = environmentStatusRepository
                        .findByCollectorItemId(application.getId());
                environmentStatusRepository.delete(existingStatuses);
                environmentStatusRepository.save(statusList);
            }

            log(" " + application.getApplicationName(), startApp);
        }
    }

    private List<LvDMApplication> enabledApplications(
            LvDMCollector collector, String instanceUrl) {
        return lvDMApplicationRepository.findEnabledApplications(
                collector.getId(), instanceUrl);
    }

    /**
     * Add any new {@link LvDMApplication}s.
     *
     * @param applications list of {@link LvDMApplication}s
     * @param collector    the {@link LvDMCollector}
     */
    private void addNewApplications(List<LvDMApplication> applications,
                                    LvDMCollector collector) {
        long start = System.currentTimeMillis();
        int count = 0;

        log("All apps", start, applications.size());
        for (LvDMApplication application : applications) {
        	LvDMApplication existing = findExistingApplication(collector, application);

        	String niceName = getNiceName(application, collector);
            if (existing == null) {
                application.setCollectorId(collector.getId());
                application.setEnabled(false);
                application.setDescription(application.getApplicationName());
                if (StringUtils.isNotEmpty(niceName)) {
                	application.setNiceName(niceName);
                }
                try {
                    lvDMApplicationRepository.save(application);
                } catch (org.springframework.dao.DuplicateKeyException ce) {
                    log("Duplicates items not allowed", 0);

                }
                count++;
            } else if (StringUtils.isEmpty(existing.getNiceName()) && StringUtils.isNotEmpty(niceName)) {
				existing.setNiceName(niceName);
				lvDMApplicationRepository.save(existing);
            }

        }
        log("New apps", start, count);
    }

    private LvDMApplication findExistingApplication(LvDMCollector collector,
                                     LvDMApplication application) {
        return lvDMApplicationRepository.findLvDMApplication(
                collector.getId(), application.getInstanceUrl(),
                application.getApplicationId());
    }
    
    private String getNiceName(LvDMApplication application, LvDMCollector collector) {
        if (CollectionUtils.isEmpty(collector.getLvDMServers())) return "";
        List<String> servers = collector.getLvDMServers();
        List<String> niceNames = collector.getNiceNames();
        if (CollectionUtils.isEmpty(niceNames)) return "";
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).equalsIgnoreCase(application.getInstanceUrl()) && niceNames.size() > i) {
                return niceNames.get(i);
            }
        }
        return "";
    }

    @SuppressWarnings("unused")
	private boolean changed(EnvironmentStatus status, EnvironmentStatus existing) {
        return existing.isOnline() != status.isOnline();
    }

    @SuppressWarnings("unused")
	private EnvironmentStatus findExistingStatus(
            final EnvironmentStatus proposed,
            List<EnvironmentStatus> existingStatuses) {

        return Iterables.tryFind(existingStatuses,
                new Predicate<EnvironmentStatus>() {
                    @Override
                    public boolean apply(EnvironmentStatus existing) {
                        return existing.getEnvironmentName().equals(
                                proposed.getEnvironmentName())
                                && existing.getComponentName().equals(
                                proposed.getComponentName())
                                && existing.getResourceName().equals(
                                proposed.getResourceName());
                    }
                }).orNull();
    }

    @SuppressWarnings("unused")
	private boolean changed(EnvironmentComponent component,
                            EnvironmentComponent existing) {
        return existing.isDeployed() != component.isDeployed()
                || existing.getAsOfDate() != component.getAsOfDate() || !existing.getComponentVersion().equalsIgnoreCase(component.getComponentVersion());
    }

    @SuppressWarnings("unused")
	private EnvironmentComponent findExistingComponent(
            final EnvironmentComponent proposed,
            List<EnvironmentComponent> existingComponents) {

        return Iterables.tryFind(existingComponents,
                new Predicate<EnvironmentComponent>() {
                    @Override
                    public boolean apply(EnvironmentComponent existing) {
                        return existing.getEnvironmentName().equals(
                                proposed.getEnvironmentName())
                                && existing.getComponentName().equals(
                                proposed.getComponentName());

                    }
                }).orNull();
    }
}
