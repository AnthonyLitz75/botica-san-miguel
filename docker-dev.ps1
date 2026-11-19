<#
.SYNOPSIS
    Script de gestión ergonómica para el entorno Docker de desarrollo de Botica San Miguel.
.DESCRIPTION
    Permite iniciar, detener, consultar logs y monitorear los servicios de desarrollo.
.EXAMPLE
    .\docker-dev.ps1 up
    .\docker-dev.ps1 logs -Service backend
    .\docker-dev.ps1 status
    .\docker-dev.ps1 down
#>

param (
    [Parameter(Position=0)]
    [ValidateSet("up", "down", "stop", "restart", "logs", "status", "build", "shell", "test")]
    [string]$Action = "up",

    [Parameter(Position=1)]
    [string]$Service = ""
)

$ComposeFile = "docker-compose.dev.yml"

switch ($Action) {
    "up" {
        Write-Host "Iniciando entorno de desarrollo con Docker Compose..." -ForegroundColor Green
        docker compose -f $ComposeFile up -d --build
        Write-Host ""
        Write-Host "Servicios iniciados. Verificando estado:" -ForegroundColor Cyan
        docker compose -f $ComposeFile ps
        Write-Host ""
        Write-Host "URLs disponibles:" -ForegroundColor Yellow
        Write-Host "  - Aplicacion Web:  http://localhost:8080/acceso/login"
        Write-Host "  - PostgreSQL Host: localhost:5433 (BD: farmacia_aed, User: postgres)"
        Write-Host "  - Depurador JVM:   localhost:5005"
        Write-Host "  - LiveReload:      localhost:35729"
    }
    "down" {
        Write-Host "Deteniendo contenedores (conservando datos en volumenes)..." -ForegroundColor Yellow
        docker compose -f $ComposeFile down
    }
    "stop" {
        Write-Host "Pausando contenedores (conservando estado)..." -ForegroundColor Yellow
        docker compose -f $ComposeFile stop
    }
    "restart" {
        if ($Service) {
            Write-Host "Reiniciando servicio '$Service'..." -ForegroundColor Cyan
            docker compose -f $ComposeFile restart $Service
        } else {
            Write-Host "Reiniciando todos los servicios..." -ForegroundColor Cyan
            docker compose -f $ComposeFile restart
        }
    }
    "logs" {
        if ($Service) {
            docker compose -f $ComposeFile logs -f $Service
        } else {
            docker compose -f $ComposeFile logs -f
        }
    }
    "status" {
        docker compose -f $ComposeFile ps
    }
    "build" {
        Write-Host "Reconstruyendo imagenes de desarrollo..." -ForegroundColor Cyan
        docker compose -f $ComposeFile build
    }
    "shell" {
        $target = if ($Service) { $Service } else { "backend" }
        Write-Host "Abriendo shell interactivo en '$target'..." -ForegroundColor Cyan
        docker compose -f $ComposeFile exec $target sh
    }
    "test" {
        Write-Host "Ejecutando suite de pruebas End-to-End con Microsoft Playwright..." -ForegroundColor Cyan
        npx playwright test
    }
}
