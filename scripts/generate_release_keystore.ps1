# Generates a release keystore (PKCS12) for LocalStream signing.
# Usage:
#   pwsh scripts/generate_release_keystore.ps1 [-Password <pass>] [-Alias localstream]
# Leaves android/release-keystore.jks and android/key.properties ready to use.
param(
    [string]$Alias = "localstream",
    [string]$Password = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$keystore = Join-Path $repoRoot "apps\mobile\android\release-keystore.jks"

if ([string]::IsNullOrEmpty($Password)) {
    $bytes = New-Object byte[] 18
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $Password = -join ($bytes | ForEach-Object { '{0:X2}' -f $_ })
}

if (Test-Path $keystore) {
    Write-Host "Keystore already exists: $keystore"
} else {
    & keytool -genkeypair -v `
        -keystore $keystore `
        -storetype JKS `
        -alias $Alias `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $Password -keypass $Password `
        -dname "CN=LocalStream, OU=Mobile, O=LocalStream, L=Local, ST=Local, C=US"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

$keyPropsPath = Join-Path $repoRoot "apps\mobile\android\key.properties"
@"
storeFile=release-keystore.jks
storePassword=$Password
keyAlias=$Alias
keyPassword=$Password
"@ | Set-Content -Path $keyPropsPath -Encoding UTF8

Write-Host "Keystore created: $keystore"
Write-Host "key.properties written: $keyPropsPath"
Write-Host "Store password: $Password  (keep it safe; if lost the key is unusable)"
Write-Host "Add these same values as CI secrets: LS_KEYSTORE_BASE64, LS_STORE_PASSWORD, LS_KEY_ALIAS, LS_KEY_PASSWORD"