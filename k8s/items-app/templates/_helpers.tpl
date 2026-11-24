{{/*
Expand the name of the chart.
*/}}
{{- define "items-app.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "items-app.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "items-app.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "items-app.labels" -}}
helm.sh/chart: {{ include "items-app.chart" . }}
{{ include "items-app.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "items-app.selectorLabels" -}}
app.kubernetes.io/name: {{ include "items-app.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Імена компонентів рішення
*/}}
{{- define "items-app.apiName" -}}
{{- printf "%s-api" (include "items-app.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "items-app.frontendName" -}}
{{- printf "%s-frontend" (include "items-app.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "items-app.dbName" -}}
{{- printf "%s-db" (include "items-app.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "items-app.migrationName" -}}
{{- printf "%s-db-migration" (include "items-app.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Мітки окремого компонента: спільні мітки + app.kubernetes.io/component
*/}}
{{- define "items-app.componentLabels" -}}
{{ include "items-app.labels" .root }}
app.kubernetes.io/component: {{ .component }}
{{- end }}

{{- define "items-app.componentSelectorLabels" -}}
{{ include "items-app.selectorLabels" .root }}
app.kubernetes.io/component: {{ .component }}
{{- end }}

{{/*
JDBC ConnectionString до бази даних. Залежить від імені release і namespace,
тому для кожного середовища формується автоматично.
*/}}
{{- define "items-app.dbUrl" -}}
{{- printf "jdbc:postgresql://%s.%s.svc.cluster.local:%v/%s" (include "items-app.dbName" .) .Release.Namespace .Values.database.port .Values.database.name }}
{{- end }}

{{/*
Повна адреса Service бекенду всередині кластера (для проксі /api у nginx фронтенду)
*/}}
{{- define "items-app.apiUpstream" -}}
{{- printf "http://%s.%s.svc.cluster.local:%v" (include "items-app.apiName" .) .Release.Namespace .Values.api.service.port }}
{{- end }}

{{/*
Ім'я Secret з обліковими даними бази даних
*/}}
{{- define "items-app.dbSecretName" -}}
{{- printf "%s-db-secret" (include "items-app.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}
