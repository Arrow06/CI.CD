package org.example.backup;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

public class BackupControllerApplication {

    public static void main(String[] args) throws InterruptedException {
        String namespace = env("WATCH_NAMESPACE", "demo");
        String backupImage = env("BACKUP_IMAGE", "postgres:17-alpine");
        long resyncSeconds = Long.parseLong(env("RESYNC_SECONDS", "60"));

        KubernetesClient client = new KubernetesClientBuilder().build();
        Runtime.getRuntime().addShutdownHook(new Thread(client::close));

        new BackupController(client, namespace, new CronJobTemplate(backupImage), resyncSeconds).run();
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
