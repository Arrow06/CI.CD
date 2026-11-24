package org.example.backup;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobSpec;

import java.util.List;
import java.util.Map;

/**
 * Будує бажаний CronJob для DatabaseBackup і порівнює керовані поля.
 */
public class CronJobTemplate {

    static final String API_VERSION = "backup.example.com/v1alpha1";
    static final String KIND = "DatabaseBackup";
    static final String MANAGED_BY = "databasebackup-controller";

    private static final String BACKUP_SCRIPT = """
            set -u
            ts=$(date -u +%Y%m%dT%H%M%SZ)
            final="/backups/${PGDATABASE}-${ts}-${HOSTNAME}.dump"
            tmp="${final}.tmp"
            echo "Starting pg_dump of database ${PGDATABASE} on ${PGHOST}:${PGPORT}"
            if ! pg_dump -Fc -f "$tmp"; then
              rm -f "$tmp"
              echo "Backup FAILED" >&2
              exit 1
            fi
            mv "$tmp" "$final"
            echo "Backup created: $final ($(du -h "$final" | cut -f1))"
            """;

    private final String backupImage;

    public CronJobTemplate(String backupImage) {
        this.backupImage = backupImage;
    }

    public CronJob desired(GenericKubernetesResource backup) {
        Map<String, Object> spec = backup.get("spec");
        String name = backup.getMetadata().getName();

        return new CronJobBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(backup.getMetadata().getNamespace())
                    .addToLabels("app.kubernetes.io/name", "databasebackup")
                    .addToLabels("app.kubernetes.io/instance", name)
                    .addToLabels("app.kubernetes.io/managed-by", MANAGED_BY)
                    .withOwnerReferences(ownerReference(backup))
                .endMetadata()
                .withNewSpec()
                    .withSchedule((String) spec.get("schedule"))
                    .withTimeZone((String) spec.getOrDefault("timeZone", "Etc/UTC"))
                    .withConcurrencyPolicy("Forbid")
                    .withSuccessfulJobsHistoryLimit(3)
                    .withFailedJobsHistoryLimit(3)
                    .withNewJobTemplate()
                        .withNewSpec()
                            .withBackoffLimit(2)
                            .withNewTemplate()
                                .withNewMetadata()
                                    .addToLabels("app.kubernetes.io/name", "databasebackup")
                                    .addToLabels("app.kubernetes.io/instance", name)
                                .endMetadata()
                                .withNewSpec()
                                    .withRestartPolicy("Never")
                                    .addNewContainer()
                                        .withName("pg-dump")
                                        .withImage(backupImage)
                                        .withCommand("/bin/sh", "-c", BACKUP_SCRIPT)
                                        .addNewEnvFrom()
                                            .withNewSecretRef()
                                                .withName((String) spec.get("connectionSecretName"))
                                            .endSecretRef()
                                        .endEnvFrom()
                                        .addNewVolumeMount()
                                            .withName("backups")
                                            .withMountPath("/backups")
                                        .endVolumeMount()
                                    .endContainer()
                                    .addNewVolume()
                                        .withName("backups")
                                        .withNewPersistentVolumeClaim()
                                            .withClaimName((String) spec.get("backupPVCName"))
                                        .endPersistentVolumeClaim()
                                    .endVolume()
                                .endSpec()
                            .endTemplate()
                        .endSpec()
                    .endJobTemplate()
                .endSpec()
                .build();
    }

    public static boolean isOwnedBy(CronJob cronJob, String ownerUid) {
        return cronJob.getMetadata().getOwnerReferences().stream()
                .anyMatch(ref -> KIND.equals(ref.getKind()) && ownerUid.equals(ref.getUid()));
    }

    /**
     * Значення керованих полів CronJob. API-сервер додає до об'єкта значення за замовчуванням,
     * тому порівнюються лише поля, які задає контролер.
     */
    public static List<Object> managedFields(CronJob cronJob) {
        CronJobSpec spec = cronJob.getSpec();
        PodSpec pod = spec.getJobTemplate().getSpec().getTemplate().getSpec();
        Container container = pod.getContainers().isEmpty() ? new Container() : pod.getContainers().get(0);
        return List.of(
                String.valueOf(spec.getSchedule()),
                String.valueOf(spec.getTimeZone()),
                String.valueOf(spec.getConcurrencyPolicy()),
                String.valueOf(container.getImage()),
                String.valueOf(container.getCommand()),
                String.valueOf(container.getEnvFrom()),
                String.valueOf(container.getVolumeMounts().stream()
                        .map(m -> m.getName() + ":" + m.getMountPath()).toList()),
                String.valueOf(pod.getVolumes().stream()
                        .map(v -> v.getName() + ":" + (v.getPersistentVolumeClaim() == null
                                ? null : v.getPersistentVolumeClaim().getClaimName())).toList()),
                String.valueOf(cronJob.getMetadata().getOwnerReferences().stream()
                        .map(OwnerReference::getUid).toList()));
    }

    private static OwnerReference ownerReference(GenericKubernetesResource backup) {
        return new OwnerReferenceBuilder()
                .withApiVersion(API_VERSION)
                .withKind(KIND)
                .withName(backup.getMetadata().getName())
                .withUid(backup.getMetadata().getUid())
                .withController(true)
                .build();
    }
}
