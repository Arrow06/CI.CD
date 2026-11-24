# Items Web Application

## a. Короткий опис сервісу
Вебсервіс реалізує базовий REST API для керування сутностями `Item` (каталог елементів). Додаток забезпечує повноцінний набір CRUD-операцій (створення, читання списку та окремого елемента за ID, оновлення, видалення) із поверненням відповідей у форматі JSON, коректними HTTP-статусами та автодокументацією через OpenAPI / Swagger UI.

| Метод  | Endpoint          | Опис                               | Статуси  |
|--------|-------------------|------------------------------------|----------|
| GET    | `/api/items`      | Отримати список об'єктів           | 200      |
| GET    | `/api/items/{id}` | Отримати об'єкт за ідентифікатором | 200, 404 |
| POST   | `/api/items`      | Створити новий об'єкт              | 201      |
| PUT    | `/api/items/{id}` | Оновити наявний об'єкт             | 200, 404 |
| DELETE | `/api/items/{id}` | Видалити об'єкт                    | 204, 404 |

Дані зберігаються в PostgreSQL (Spring Data JPA / Hibernate). Застосунок складається з компонентів:

| Компонент | Папка | Образ у Docker Hub | Опис |
|-----------|-------|--------------------|------|
| API | `.` | `arrow06/items-api-gha` | Spring Boot REST API, `ddl-auto=validate` |
| Frontend | `frontend/` | `arrow06/items-frontend` | React + Vite SPA (CRUD для `Item`), роздається nginx |
| Migrator | `migrator/` | `arrow06/items-migrator` | консольний застосунок (Flyway), створює схему БД |
| Database | — | `postgres:17-alpine` | PostgreSQL у StatefulSet з PVC |
| Helm chart | `k8s/items-app/` | — | встановлює всі компоненти в Kubernetes |

### Code-First
Схему БД описує JPA-сутність `Item` (`src/main/java/org/example/lab1/Item.java`). DDL згенеровано з сутності засобами Hibernate:
```bash
./mvnw test -Dtest=Lab1ApplicationTests -DargLine="-Dspring.profiles.active=schema"
# результат: target/generated-schema.sql
```
Згенерований скрипт збережено як міграцію `migrator/src/main/resources/db/migration/V1__create_items_table.sql`. API лише перевіряє відповідність схеми сутностям (`spring.jpa.hibernate.ddl-auto=validate`).

ConnectionString задається змінними середовища `DB_URL`, `DB_USER`, `DB_PASSWORD` (для API і для migrator). У Kubernetes `DB_URL` береться з ConfigMap, логін і пароль — із Secret; значення для кожного середовища задаються у values-файлі (наприклад, `k8s/values.lab.yaml`).

## b. Використана мова та фреймворк
* **Мова програмування:** Java 17
* **Фреймворк:** Spring Boot (Spring Web, Spring Data JPA, Springdoc OpenAPI)
* **ORM / міграції:** Hibernate, Flyway
* **База даних:** PostgreSQL 17 (у тестах — H2)
* **Frontend:** React 19 + Vite, nginx
* **Система збирання:** Maven

## c. Інструкція як зібрати та запустити Docker image

### 1. Збирання Docker-образу
Переконайтеся, що Docker запущено, та виконайте команду в кореневій директорії проєкту:
```bash
docker build -t items-api:latest .
```

### 2. Запуск контейнера
API потребує PostgreSQL зі створеною схемою:
```bash
docker network create items-net
docker run -d --name items-db --network items-net -e POSTGRES_DB=items -e POSTGRES_USER=items -e POSTGRES_PASSWORD=items postgres:17-alpine
docker build -t items-migrator:latest migrator
docker run --rm --network items-net -e DB_URL=jdbc:postgresql://items-db:5432/items -e DB_USER=items -e DB_PASSWORD=items items-migrator:latest
docker run -d -p 8080:8080 --name items-api --network items-net -e DB_URL=jdbc:postgresql://items-db:5432/items items-api:latest
```

### 3. Перевірка роботи
Адреси нижче працюють лише тоді, коли контейнер запущено на комп'ютері (крок 2).

* API: http://localhost:8080/api/items
* Swagger UI: http://localhost:8080/swagger-ui/index.html
* OpenAPI специфікація: http://localhost:8080/v3/api-docs

Приклад запиту:
```bash
curl -X POST http://localhost:8080/api/items \
  -H "Content-Type: application/json" \
  -d '{"name":"Book","description":"Some book"}'
```

### Образ із Docker Hub
Образ `arrow06/items-api` (Azure DevOps) зібрано для платформи `linux/arm64`. Образи, які публікує GitHub Actions (`arrow06/items-api-gha`, `arrow06/items-frontend`, `arrow06/items-migrator`), зібрано для `linux/amd64` і `linux/arm64`.

```bash
docker pull arrow06/items-api:latest
docker run -d -p 8080:8080 arrow06/items-api:latest
```

## d. Команда запуску тестів
```bash
./mvnw test
```
(на Windows: `mvnw.cmd test`)

## CI (Azure DevOps)
Pipeline описано у файлі `azure-pipelines.yml`. Він автоматично запускається після push у гілку `master` і виконує:
1. збирання проєкту (Maven);
2. запуск unit-тестів;
3. збирання Docker-образу;
4. присвоєння тегу `latest`;
5. публікацію образу в Docker Hub (`arrow06/items-api`).

Pipeline виконується на self-hosted агенті `mac-agent` (пул `Default`), бо хмарні агенти Microsoft для організації недоступні. Щоб pipeline запустився, комп'ютер з агентом має бути увімкнений.

## CI (GitHub Actions)
Workflow `.github/workflows/ci.yml` запускається після push у гілки `master`, `feature/*` і `bugfix/*`. Зміни лише в папці `docs/` або у файлі `README.md` його не запускають.

* `build-n-test` — збирання API та unit-тести (`./mvnw -B clean package`).
* `build-migrator` — збирання консольного застосунку міграцій.
* `build-backup-controller` — збирання контролера резервного копіювання.
* `build-frontend` — `npm ci` і `npm run build` для React-клієнта.
* `helm-lint-n-package` — `helm lint` і `helm package` для чарта `k8s/items-app`; пакет `.tgz` завантажується як артефакт `helm-chart`.
* `docker-api`, `docker-frontend`, `docker-migrator`, `docker-backup-controller` — reusable workflow `.github/workflows/docker-pack-n-push.yml`. Для `master` вони публікують образи `arrow06/items-api-gha`, `arrow06/items-frontend`, `arrow06/items-migrator`, `arrow06/databasebackup-controller` (тег `latest`) у Docker Hub, для інших гілок зберігають образи як артефакти `docker-image-api`, `docker-image-frontend`, `docker-image-migrator`, `docker-image-backup-controller`.

Облікові дані Docker Hub зберігаються в GitHub Secrets: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`.

## Kubernetes (Helm chart)
Чарт `k8s/items-app` створено командою `helm create` і змінено під застосунок. Версії задано в `Chart.yaml` (`version` — версія чарта, `appVersion` — версія застосунку), параметри — у `values.yaml`.

Чарт створює:
* `StorageClass`, `PersistentVolumeClaim` (через `volumeClaimTemplates`) і `PersistentVolume` (створює provisioner) для БД;
* `StatefulSet` і headless `Service` для PostgreSQL;
* `Job` міграції БД (запускається при кожному `helm install`/`upgrade`);
* `ConfigMap` (`DB_URL`) і `Secret` (`DB_USER`, `DB_PASSWORD`);
* `Deployment` і `Service` для API та frontend;
* `Ingress` (клас `nginx`): `/api` → API, `/` → frontend.

### Пакування
```bash
helm lint k8s/items-app
helm package k8s/items-app --destination dist
# результат: dist/items-app-0.1.0.tgz
```

### Підготовка кластера (kind + NGINX Ingress)
```bash
kind create cluster --config k8s/kind-webapp.yaml
kubectl apply -f https://kind.sigs.k8s.io/examples/ingress/deploy-ingress-nginx.yaml
kubectl wait -n ingress-nginx --for=condition=ready pod -l app.kubernetes.io/component=controller --timeout=180s
```
Додайте в `/etc/hosts` рядок:
```
127.0.0.1 webapp.lab.test
```

### Встановлення
З папки чарта:
```bash
helm upgrade --install lab k8s/items-app -n webapp-lab --create-namespace -f k8s/values.lab.yaml
```
або з пакета:
```bash
helm upgrade --install lab dist/items-app-0.1.0.tgz -n webapp-lab --create-namespace -f k8s/values.lab.yaml
```

Перевірка:
```bash
kubectl -n webapp-lab get all,pvc,ingress,configmap,secret
kubectl get pv,storageclass
helm test lab -n webapp-lab
```
Застосунок відкривається за адресою http://webapp.lab.test/, API — http://webapp.lab.test/api/items.

Видалення:
```bash
helm uninstall lab -n webapp-lab
```

Основні параметри `values.yaml`:

| Параметр | Опис |
|----------|------|
| `api.image.*`, `frontend.image.*`, `migration.image.*` | образи та теги компонентів |
| `database.name`, `database.user`, `database.password` | параметри БД (ConnectionString) |
| `database.persistence.size` | розмір тому БД |
| `database.storageClass.*` | StorageClass (provisioner, reclaimPolicy) |
| `migration.enabled` | чи запускати Job міграції |
| `ingress.enabled`, `ingress.className`, `ingress.host` | налаштування Ingress |

## Резервне копіювання PostgreSQL (DatabaseBackup)
Власний ресурс `DatabaseBackup` (`backup.example.com/v1alpha1`) описує розклад резервного копіювання, Secret із параметрами підключення до БД і PVC для архівів. Контролер (`backup-controller/`, Java, fabric8 kubernetes-client) для кожного `DatabaseBackup` створює і підтримує `CronJob` з таким самим іменем.

| Об'єкт | Хто створює |
|--------|-------------|
| `CronJob` | власний контролер `databasebackup-controller` (з `DatabaseBackup`) |
| `Job` | вбудований контролер CronJob у kube-controller-manager (за розкладом) |
| `Pod` | вбудований контролер Job у kube-controller-manager |

Контролер:
* під час запуску виконує LIST ресурсів `DatabaseBackup` у namespace `demo` і WATCH з `resourceVersion` списку; після завершення потоку або `410 Gone` повторює LIST;
* також спостерігає за `CronJob` і раз на 60 с виконує повну звірку, тому видалений CronJob створюється знову;
* створює відсутній CronJob або оновлює керовані поля (розклад, часовий пояс, шаблон Job), встановлює `ownerReferences` на `DatabaseBackup`; якщо CronJob з таким іменем належить іншому власнику, записує конфлікт у лог;
* ставить кожен ключ у чергу звірки лише один раз, тож однакові події не створюють дублікатів.

Job запускає `pg_dump -Fc` (образ `postgres:17-alpine`) з параметрами з Secret (`envFrom.secretRef`), записує архів у тимчасовий файл у `/backups` (PVC) і після успіху перейменовує його на `<db>-<час>-<pod>.dump`. Шлях архіву виводиться в лог, а при помилці Job завершується з ненульовим кодом.

Маніфести знаходяться в `k8s/backup/`:

| Файл | Вміст |
|------|-------|
| `databasebackup-crd.yaml` | CustomResourceDefinition `databasebackups.backup.example.com` |
| `databasebackup.yaml` | приклад ресурсу `users-db-backup` |
| `databasebackup-invalid.yaml` | ресурс без `spec.schedule` для перевірки валідації |
| `namespace.yaml` | namespace `demo` |
| `users-db-connection-secret.yaml` | Secret з `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` (демонстраційні значення) |
| `backup-pvc.yaml` | PVC `postgres-backups` для архівів |
| `controller-rbac.yaml` | ServiceAccount, Role і RoleBinding контролера |
| `controller-deployment.yaml` | Deployment контролера |
| `restore-check-pod.yaml` | допоміжний Pod із PostgreSQL-клієнтом для перевірки відновлення |

### Підготовка Secret
Secret містить параметри підключення до БД із лабораторної № 4 (release `lab`, namespace `webapp-lab`). `PGHOST` — DNS-ім'я Service PostgreSQL, доступне з будь-якого Pod кластера:
```
lab-items-app-db.webapp-lab.svc.cluster.local
```
Для іншої БД змініть значення в `users-db-connection-secret.yaml` або створіть Secret командою:
```bash
kubectl -n demo create secret generic users-db-connection \
  --from-literal=PGHOST=lab-items-app-db.webapp-lab.svc.cluster.local \
  --from-literal=PGPORT=5432 \
  --from-literal=PGDATABASE=items_lab \
  --from-literal=PGUSER=items_lab \
  --from-literal=PGPASSWORD=lab-password
```

### Встановлення
```bash
kubectl apply -f k8s/backup/databasebackup-crd.yaml
kubectl wait --for=condition=Established crd/databasebackups.backup.example.com
kubectl apply -f k8s/backup/namespace.yaml
kubectl apply -f k8s/backup/users-db-connection-secret.yaml -f k8s/backup/backup-pvc.yaml
kubectl apply -f k8s/backup/controller-rbac.yaml -f k8s/backup/controller-deployment.yaml
kubectl apply -f k8s/backup/databasebackup.yaml
kubectl get databasebackups.backup.example.com -n demo
```

### Перевірка
```bash
kubectl get databasebackup,cronjob -n demo
kubectl -n demo logs deploy/databasebackup-controller

kubectl create job backup-manual --from=cronjob/users-db-backup -n demo
kubectl wait --for=condition=complete job/backup-manual -n demo --timeout=120s
kubectl logs job/backup-manual -n demo

kubectl apply -f k8s/backup/restore-check-pod.yaml
kubectl -n demo exec restore-check -- sh -c 'pg_restore --list "$(ls -t /backups/*.dump | head -1)"'
kubectl -n demo exec restore-check -- sh -c 'createdb restore_test && pg_restore --no-owner -d restore_test "$(ls -t /backups/*.dump | head -1)" && psql -d restore_test -c "select * from items"'

SA=system:serviceaccount:demo:databasebackup-controller
kubectl auth can-i list databasebackups.backup.example.com -n demo --as=$SA
kubectl auth can-i create cronjobs.batch -n demo --as=$SA
kubectl auth can-i list secrets -n demo --as=$SA
```
