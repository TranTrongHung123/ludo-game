param(
    [Parameter(Position = 0)]
    [ValidateSet("db", "server", "build", "test", "status", "stop", "help")]
    [string]$Command = "help"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$mavenWrapper = Join-Path $projectRoot "mvnw.cmd"

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Lenh '$FilePath' ket thuc voi ma loi $LASTEXITCODE."
    }
}

function Invoke-Maven {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    Invoke-Checked $mavenWrapper @Arguments
}

function Show-Help {
    Write-Host "Ludo Game - lenh chay nhanh"
    Write-Host ""
    Write-Host "  .\ludo.ps1 db       Khoi dong MySQL"
    Write-Host "  .\ludo.ps1 server   Build va chay Game Server"
    Write-Host "  Unity Client       Mo client-unity trong Unity Hub (xem RUN.md)"
    Write-Host "  .\ludo.ps1 build    Build Java backend (common + server)"
    Write-Host "  .\ludo.ps1 test     Chay Maven verify"
    Write-Host "  .\ludo.ps1 status   Xem trang thai MySQL"
    Write-Host "  .\ludo.ps1 stop     Dung MySQL"
}

Push-Location $projectRoot
try {
    switch ($Command) {
        "db" {
            Invoke-Checked "docker" "compose" "up" "-d" "mysql"
            Invoke-Checked "docker" "compose" "ps"
        }
        "server" {
            Write-Host "Dang build Server..."
            Invoke-Maven "-q" "-pl" "server" "-am" "-DskipTests" "install"
            Write-Host "Game Server dang chay tai 127.0.0.1:5555 (Ctrl+C de dung)."
            Invoke-Maven "-pl" "server" "exec:java"
        }
        "build" {
            Invoke-Maven "install"
        }
        "test" {
            Invoke-Maven "--batch-mode" "--no-transfer-progress" "verify"
        }
        "status" {
            Invoke-Checked "docker" "compose" "ps"
        }
        "stop" {
            Invoke-Checked "docker" "compose" "down"
        }
        "help" {
            Show-Help
        }
    }
} finally {
    Pop-Location
}
