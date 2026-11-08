# Sobe o ambiente completo e testa a autenticação ponta a ponta pelo gateway.
# Uso (na raiz do projeto):  powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
# Resultados: smoke-test.log (resumo), compose-up.log, compose-ps.log e compose-logs.log (só se algo falhar).

$ErrorActionPreference = 'Continue'
Set-Location (Join-Path $PSScriptRoot '..')
$log = Join-Path (Get-Location) 'smoke-test.log'
Set-Content -Path $log -Value '' -Encoding utf8
$falhas = 0

function Log($msg) {
    $line = '[{0:HH:mm:ss}] {1}' -f (Get-Date), $msg
    Write-Host $line
    Add-Content -Path $log -Value $line -Encoding utf8
}

function Check($nome, $ok, $detalhe = '') {
    if (-not $ok) { $script:falhas++ }
    Log ('{0} {1} {2}' -f $(if ($ok) { 'OK   ' } else { 'FALHA' }), $nome, $detalhe)
}

function Call($method, $path, $body = $null, $token = $null, $base = 'http://localhost:8080') {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Method = $method; Uri = "$base$path"; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 30 }
    if ($null -ne $body) {
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Compress))
        $params['ContentType'] = 'application/json; charset=utf-8'
    }
    try {
        $r = Invoke-WebRequest @params
        return @{ Status = [int]$r.StatusCode; Body = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) { return @{ Status = [int]$resp.StatusCode; Body = $_.ErrorDetails.Message } }
        return @{ Status = 0; Body = $_.Exception.Message }
    }
}

function Json($text) { try { return $text | ConvertFrom-Json } catch { return $null } }

# ---------- 1. Subir o ambiente ----------
if (-not (Test-Path .env)) { Copy-Item .env.example .env; Log 'Criado .env a partir de .env.example' }
Log 'docker compose up -d --build (a primeira vez demora: compila as 11 imagens)...'
docker compose up -d --build 2>&1 | Out-File -Encoding utf8 compose-up.log
Log "docker compose terminou com código $LASTEXITCODE (detalhes em compose-up.log)"

# ---------- 2. Esperar os serviços ----------
$servicos = [ordered]@{
    8080 = 'api-gateway'; 8081 = 'auth-service'; 8082 = 'bank-service'; 8083 = 'registry-service'
    8084 = 'contract-service'; 8085 = 'document-service'; 8086 = 'routing-service'; 8087 = 'integration-service'
    8088 = 'notification-service'; 8089 = 'audit-service'; 8090 = 'operations-service'
}
$limite = (Get-Date).AddMinutes(8)
$foraDoAr = @()
foreach ($porta in $servicos.Keys) {
    $up = $false
    while (-not $up -and (Get-Date) -lt $limite) {
        $h = Call 'GET' '/actuator/health' $null $null "http://localhost:$porta"
        if ($h.Status -eq 200) { $up = $true } else { Start-Sleep -Seconds 3 }
    }
    Check ("health {0} (:{1})" -f $servicos[$porta], $porta) $up
    if (-not $up) { $foraDoAr += $servicos[$porta] }
}
docker compose ps -a 2>&1 | Out-File -Encoding utf8 compose-ps.log
if ($foraDoAr.Count -gt 0) {
    Set-Content -Path compose-logs.log -Value '' -Encoding utf8
    foreach ($s in $foraDoAr) {
        Add-Content -Path compose-logs.log -Value "===== $s =====" -Encoding utf8
        docker compose logs --no-color --tail 120 $s 2>&1 | Out-File -Append -Encoding utf8 compose-logs.log
    }
}

# ---------- 3. Infraestrutura ----------
foreach ($ui in @(@('RabbitMQ', 'http://localhost:15672'), @('Grafana', 'http://localhost:3000/api/health'),
                  @('Jaeger', 'http://localhost:16686'), @('Prometheus', 'http://localhost:9090/-/ready'),
                  @('Mailpit', 'http://localhost:8025/api/v1/info'))) {
    $r = Call 'GET' '' $null $null $ui[1]
    Check ("{0} responde" -f $ui[0]) ($r.Status -eq 200) ("HTTP {0}" -f $r.Status)
}

# ---------- 4. Autenticação pelo gateway ----------
$senha = 'Hub@dev2026!'

$sem = Call 'GET' '/api/v1/users'
Check 'API sem token é recusada no gateway (401)' ($sem.Status -eq 401) ("HTTP {0}" -f $sem.Status)

$errada = Call 'POST' '/api/v1/auth/login' @{ email = 'admin@banco-exemplo.dev'; password = 'senha-errada-123' }
Check 'senha errada responde 401 genérico' ($errada.Status -eq 401) ("HTTP {0}" -f $errada.Status)

$login = Call 'POST' '/api/v1/auth/login' @{ email = 'admin@banco-exemplo.dev'; password = $senha }
$tokens = Json $login.Body
Check 'login do admin do Banco Exemplo' ($login.Status -eq 200 -and $tokens.accessToken) ("HTTP {0}" -f $login.Status)

if ($tokens.accessToken) {
    $me = Call 'GET' '/api/v1/auth/me' $null $tokens.accessToken
    $perfil = Json $me.Body
    Check '/me identifica a instituição e o papel' ($me.Status -eq 200 -and $perfil.institution.name -eq 'Banco Exemplo S.A.' -and $perfil.roles -contains 'BANK_ADMIN') `
        ("{0} / {1} / {2} permissões" -f $perfil.institution.name, ($perfil.roles -join ','), @($perfil.permissions).Count)

    $users = Call 'GET' '/api/v1/users?size=100' $null $tokens.accessToken
    $pagina = Json $users.Body
    $outras = @($pagina.items | Where-Object { $_.institutionId -ne '01900000-0000-7000-8000-000000000101' })
    Check 'banco só vê usuários do próprio banco' ($users.Status -eq 200 -and $outras.Count -eq 0) `
        ("{0} usuários, {1} de outras instituições" -f @($pagina.items).Count, $outras.Count)

    $outro = Call 'GET' '/api/v1/users/01900000-0000-7000-8000-000000001201' $null $tokens.accessToken
    Check 'usuário de outro banco responde 404' ($outro.Status -eq 404) ("HTTP {0}" -f $outro.Status)

    $refresh = Call 'POST' '/api/v1/auth/refresh' @{ refreshToken = $tokens.refreshToken }
    $novos = Json $refresh.Body
    Check 'refresh gera novo par de tokens' ($refresh.Status -eq 200 -and $novos.refreshToken -ne $tokens.refreshToken) ("HTTP {0}" -f $refresh.Status)
    $reuso = Call 'POST' '/api/v1/auth/refresh' @{ refreshToken = $tokens.refreshToken }
    Check 'reuso do refresh antigo é recusado' ($reuso.Status -eq 401) ("HTTP {0}" -f $reuso.Status)
    $depois = Call 'POST' '/api/v1/auth/refresh' @{ refreshToken = $novos.refreshToken }
    Check 'reuso revogou a sessão inteira' ($depois.Status -eq 401) ("HTTP {0}" -f $depois.Status)

    $cliente = Call 'POST' '/api/v1/api-clients' @{ name = 'ERP smoke test'; scopes = @('contract:read'); rateLimitPerMinute = 60 } $tokens.accessToken
    $criado = Json $cliente.Body
    Check 'admin do banco cria credencial de API' ($cliente.Status -eq 201) ("HTTP {0}" -f $cliente.Status)
    if ($criado.clientSecret) {
        $basic = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes("$($criado.client.clientId):$($criado.clientSecret)"))
        try {
            $t = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/oauth2/token' -Headers @{ Authorization = "Basic $basic" } `
                -ContentType 'application/x-www-form-urlencoded' -Body 'grant_type=client_credentials' -TimeoutSec 30
            Check 'sistema do banco obtém token (client credentials)' ([bool]$t.access_token) ("expira em {0}s" -f $t.expires_in)
            $meCliente = Json (Call 'GET' '/api/v1/auth/me' $null $t.access_token).Body
            Check 'token do cliente tem só o escopo concedido' ($meCliente.apiClient -and (@($meCliente.permissions) -join ',') -eq 'contract:read') (@($meCliente.permissions) -join ',')
        } catch {
            Check 'sistema do banco obtém token (client credentials)' $false $_.Exception.Message
        }
    }
}

$antes = Json (Call 'GET' '/api/v1/messages' $null $null 'http://localhost:8025').Body
$reset = Call 'POST' '/api/v1/auth/password-reset/request' @{ email = 'operador@banco-exemplo.dev' }
Check 'pedido de redefinição de senha (202)' ($reset.Status -eq 202) ("HTTP {0}" -f $reset.Status)
Start-Sleep -Seconds 3
$depoisMail = Json (Call 'GET' '/api/v1/messages' $null $null 'http://localhost:8025').Body
Check 'e-mail de redefinição chegou no Mailpit' ($depoisMail.total -gt $antes.total) ("{0} -> {1} mensagens" -f $antes.total, $depoisMail.total)

$auditor = Json (Call 'POST' '/api/v1/auth/login' @{ email = 'auditor@hub.dev'; password = $senha }).Body
if ($auditor.accessToken) {
    $todos = Json (Call 'GET' '/api/v1/users?size=100' $null $auditor.accessToken).Body
    $insts = @($todos.items | Select-Object -ExpandProperty institutionId -Unique)
    Check 'auditor do Hub vê usuários de todas as instituições' ($insts.Count -ge 4) ("{0} instituições" -f $insts.Count)
    $tentativa = Call 'POST' '/api/v1/users' @{ fullName = 'X'; email = 'x@hub.dev'; roles = @('HUB_AUDITOR') } $auditor.accessToken
    Check 'auditor não pode criar usuários (403)' ($tentativa.Status -eq 403) ("HTTP {0}" -f $tentativa.Status)
}

Log ('Concluído: {0} falha(s).' -f $falhas)
