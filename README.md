# Items REST API Service

## a. Короткий опис сервісу
Вебсервіс реалізує базовий REST API для керування сутностями `Item` (каталог елементів). Додаток забезпечує повноцінний набір CRUD-операцій (створення, читання списку та окремого елемента за ID, оновлення, видалення) із поверненням відповідей у форматі JSON, коректними HTTP-статусами та автодокументацією через OpenAPI / Swagger UI.

| Метод  | Endpoint          | Опис                               | Статуси  |
|--------|-------------------|------------------------------------|----------|
| GET    | `/api/items`      | Отримати список об'єктів           | 200      |
| GET    | `/api/items/{id}` | Отримати об'єкт за ідентифікатором | 200, 404 |
| POST   | `/api/items`      | Створити новий об'єкт              | 201      |
| PUT    | `/api/items/{id}` | Оновити наявний об'єкт             | 200, 404 |
| DELETE | `/api/items/{id}` | Видалити об'єкт                    | 204, 404 |

Дані зберігаються в пам'яті (без бази даних).

## b. Використана мова та фреймворк
* **Мова програмування:** Java 17
* **Фреймворк:** Spring Boot (Spring Web, Springdoc OpenAPI)
* **Система збирання:** Maven

## c. Інструкція як зібрати та запустити Docker image

### 1. Збирання Docker-образу
Переконайтеся, що Docker запущено, та виконайте команду в кореневій директорії проєкту:
```bash
docker build -t items-api:latest .
```

### 2. Запуск контейнера
```bash
docker run -d -p 8080:8080 --name items-api items-api:latest
```

### 3. Перевірка роботи
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
