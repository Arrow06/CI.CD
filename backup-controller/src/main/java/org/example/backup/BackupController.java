package org.example.backup;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Контролер DatabaseBackup: LIST + WATCH ресурсів, черга звірки без дублікатів,
 * спостереження за CronJob і періодична повна звірка.
 */
public class BackupController {

    private static final Logger log = LoggerFactory.getLogger(BackupController.class);

    private static final ResourceDefinitionContext DATABASE_BACKUP = new ResourceDefinitionContext.Builder()
            .withGroup("backup.example.com")
            .withVersion("v1alpha1")
            .withKind(CronJobTemplate.KIND)
            .withPlural("databasebackups")
            .withNamespaced(true)
            .build();

    private final KubernetesClient client;
    private final String namespace;
    private final CronJobTemplate template;
    private final long resyncSeconds;

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Set<String> queued = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public BackupController(KubernetesClient client, String namespace, CronJobTemplate template, long resyncSeconds) {
        this.client = client;
        this.namespace = namespace;
        this.template = template;
        this.resyncSeconds = resyncSeconds;
    }

    public void run() throws InterruptedException {
        log.info("Starting DatabaseBackup controller in namespace {}", namespace);

        Thread worker = new Thread(this::processQueue, "reconcile-worker");
        worker.setDaemon(true);
        worker.start();

        Thread cronJobWatcher = new Thread(
                () -> watchForever("CronJob", cronJobs(), this::onCronJobEvent), "cronjob-watch");
        cronJobWatcher.setDaemon(true);
        cronJobWatcher.start();

        scheduler.scheduleWithFixedDelay(
                this::resync, resyncSeconds, resyncSeconds, TimeUnit.SECONDS);

        watchForever("DatabaseBackup", backups(), this::onBackupEvent);
    }

    private NonNamespaceOperation<GenericKubernetesResource, GenericKubernetesResourceList,
            Resource<GenericKubernetesResource>> backups() {
        return client.genericKubernetesResources(DATABASE_BACKUP).inNamespace(namespace);
    }

    private NonNamespaceOperation<CronJob, CronJobList, Resource<CronJob>> cronJobs() {
        return client.batch().v1().cronjobs().inNamespace(namespace);
    }

    /**
     * LIST поточних об'єктів, потім WATCH з resourceVersion цього списку.
     * Після завершення потоку (зокрема 410 Gone) список читається заново.
     */
    private <T extends HasMetadata, L extends KubernetesResourceList<T>> void watchForever(
            String kind, NonNamespaceOperation<T, L, ? extends Resource<T>> operation,
            BiConsumer<Watcher.Action, T> handler) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                L snapshot = operation.list();
                String resourceVersion = snapshot.getMetadata().getResourceVersion();
                log.info("LIST {}: {} object(s), resourceVersion={}",
                        kind, snapshot.getItems().size(), resourceVersion);
                snapshot.getItems().forEach(item -> handler.accept(null, item));

                CompletableFuture<WatcherException> closed = new CompletableFuture<>();
                try (Watch ignored = operation.withResourceVersion(resourceVersion).watch(new Watcher<T>() {
                    @Override
                    public void eventReceived(Action action, T item) {
                        handler.accept(action, item);
                    }

                    @Override
                    public void onClose(WatcherException cause) {
                        closed.complete(cause);
                    }

                    @Override
                    public void onClose() {
                        closed.complete(null);
                    }
                })) {
                    WatcherException cause = closed.get();
                    if (cause != null && cause.isHttpGone()) {
                        log.warn("WATCH {}: 410 Gone, repeating LIST", kind);
                    } else {
                        log.warn("WATCH {} closed ({}), repeating LIST", kind,
                                cause == null ? "stream ended" : cause.getMessage());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("WATCH {} failed: {}. Retrying in 5s", kind, e.getMessage());
                sleep(5);
            }
        }
    }

    private void onBackupEvent(Watcher.Action action, GenericKubernetesResource backup) {
        String name = backup.getMetadata().getName();
        log.info("{} DatabaseBackup {}/{}", action == null ? "INITIAL" : action, namespace, name);
        if (action == Watcher.Action.DELETED) {
            log.info("CronJob {}/{} will be removed by the garbage collector (ownerReferences)", namespace, name);
            return;
        }
        enqueue(name);
    }

    private void onCronJobEvent(Watcher.Action action, CronJob cronJob) {
        if (action == null || action == Watcher.Action.ADDED) {
            return;
        }
        cronJob.getMetadata().getOwnerReferences().stream()
                .filter(ref -> CronJobTemplate.KIND.equals(ref.getKind()))
                .forEach(ref -> {
                    log.info("{} CronJob {}/{} -> reconcile DatabaseBackup {}",
                            action, namespace, cronJob.getMetadata().getName(), ref.getName());
                    enqueue(ref.getName());
                });
    }

    private void resync() {
        try {
            backups().list().getItems().forEach(backup -> enqueue(backup.getMetadata().getName()));
        } catch (Exception e) {
            log.error("Periodic resync failed: {}", e.getMessage());
        }
    }

    /** Ключ потрапляє в чергу лише один раз, доки його не оброблено. */
    private void enqueue(String name) {
        if (queued.add(name)) {
            queue.add(name);
        }
    }

    private void processQueue() {
        while (true) {
            String name;
            try {
                name = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            queued.remove(name);
            try {
                reconcile(name);
            } catch (Exception e) {
                log.error("Reconcile {}/{} failed: {}. Retrying in 10s", namespace, name, e.getMessage());
                scheduler.schedule(() -> enqueue(name), 10, TimeUnit.SECONDS);
            }
        }
    }

    void reconcile(String name) {
        GenericKubernetesResource backup = backups().withName(name).get();
        if (backup == null) {
            log.info("DatabaseBackup {}/{} not found, nothing to do", namespace, name);
            return;
        }

        CronJob desired = template.desired(backup);
        CronJob actual = cronJobs().withName(name).get();

        if (actual == null) {
            try {
                cronJobs().resource(desired).create();
                log.info("Created CronJob {}/{} (schedule \"{}\", timeZone {})", namespace, name,
                        desired.getSpec().getSchedule(), desired.getSpec().getTimeZone());
            } catch (KubernetesClientException e) {
                if (e.getCode() != 409) {
                    throw e;
                }
                log.info("CronJob {}/{} already exists, reconciling again", namespace, name);
                enqueue(name);
            }
            return;
        }

        if (!CronJobTemplate.isOwnedBy(actual, backup.getMetadata().getUid())) {
            log.warn("CONFLICT: CronJob {}/{} exists and is not owned by DatabaseBackup {} (uid {}), skipping",
                    namespace, name, name, backup.getMetadata().getUid());
            return;
        }

        if (CronJobTemplate.managedFields(actual).equals(CronJobTemplate.managedFields(desired))) {
            log.info("CronJob {}/{} is up to date", namespace, name);
            return;
        }

        String oldSchedule = actual.getSpec().getSchedule();
        cronJobs().withName(name).edit(cronJob -> {
            cronJob.getMetadata().setLabels(desired.getMetadata().getLabels());
            cronJob.getMetadata().setOwnerReferences(desired.getMetadata().getOwnerReferences());
            cronJob.getSpec().setSchedule(desired.getSpec().getSchedule());
            cronJob.getSpec().setTimeZone(desired.getSpec().getTimeZone());
            cronJob.getSpec().setConcurrencyPolicy(desired.getSpec().getConcurrencyPolicy());
            cronJob.getSpec().setSuccessfulJobsHistoryLimit(desired.getSpec().getSuccessfulJobsHistoryLimit());
            cronJob.getSpec().setFailedJobsHistoryLimit(desired.getSpec().getFailedJobsHistoryLimit());
            cronJob.getSpec().setJobTemplate(desired.getSpec().getJobTemplate());
            return cronJob;
        });
        log.info("Updated CronJob {}/{} (schedule \"{}\" -> \"{}\")", namespace, name,
                oldSchedule, desired.getSpec().getSchedule());
    }

    private static void sleep(long seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
