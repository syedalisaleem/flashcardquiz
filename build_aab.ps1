# Builds an Android App Bundle (.aab) for Play Store upload.
# Requires: Android SDK, JDK 17+, and Gradle.
#
# Usage: powershell build_aab.ps1
# Output: dist/FlashcardQuiz.aab

$ErrorActionPreference = "Stop"

$Root = $PSScriptRoot
$Android = Join-Path $Root "android"
$OutDir = Join-Path $Root "dist"
$AabOut = Join-Path $OutDir "FlashcardQuiz.aab"

# Check for Java
$JavaHome = $env:JAVA_HOME
if (-not $JavaHome) {
    $JavaHome = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
}
if (-not (Test-Path "$JavaHome\bin\java.exe")) {
    throw "Java 17 not found. Set JAVA_HOME or install Eclipse Adoptium JDK 17."
}

# Check for Android SDK
$Sdk = $env:ANDROID_HOME
if (-not $Sdk) { $Sdk = "C:\Users\callm\AppData\Local\Android\Sdk" }
if (-not (Test-Path $Sdk)) {
    throw "Android SDK not found at $Sdk. Set ANDROID_HOME."
}

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $Sdk

function Step($name) { Write-Host "==> $name" -ForegroundColor Cyan }

Step "Building AAB (release)..."
Push-Location $Android
try {
    if (-not (Test-Path "gradlew.bat")) {
        # Download gradle wrapper if not present
        Step "Downloading Gradle wrapper..."
        & "$JavaHome\bin\java.exe" -cp "" org.gradle.wrapper.GradleWrapperMain --version 2>$null
        if (-not (Test-Path "gradlew.bat")) {
            # Create a minimal wrapper
            @"
@rem Gradle Wrapper Startup Script
@if "%DEBUG%"=="" @echo off
set DIRNAME=%~dp0
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
set JAVA_EXE=java.exe
if defined JAVA_HOME set JAVA_EXE=%JAVA_HOME%/bin/java.exe
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
"@ | Out-File -FilePath "gradlew.bat" -Encoding ascii
        }
    }

    Step "Running Gradle assembleRelease..."
    & ".\gradlew.bat" assembleRelease --no-daemon --stacktrace
    if ($LASTEXITCODE -ne 0) { throw "Gradle assembleRelease failed" }

    Step "Running Gradle bundleRelease..."
    & ".\gradlew.bat" bundleRelease --no-daemon --stacktrace
    if ($LASTEXITCODE -ne 0) { throw "Gradle bundleRelease failed" }
} finally {
    Pop-Location
}

# Copy AAB to dist
$AabFile = Get-ChildItem (Join-Path $Android "app\build\outputs\bundle\release") -Filter *.aab -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $AabFile) {
    throw "AAB file not found in app/build/outputs/bundle/release/"
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
Copy-Item $AabFile.FullName $AabOut -Force

$final = Get-Item $AabOut
Write-Host ""
Write-Host "AAB ready: $($final.FullName) ($([math]::Round($final.Length / 1MB, 1)) MB)" -ForegroundColor Green
Write-Host ""
Write-Host "To upload to Play Store:" -ForegroundColor Yellow
Write-Host "  1. Go to https://play.google.com/console" -ForegroundColor Yellow
Write-Host "  2. Create an app listing" -ForegroundColor Yellow
Write-Host "  3. Release > Production > Create new release" -ForegroundColor Yellow
Write-Host "  4. Upload FlashcardQuiz.aab" -ForegroundColor Yellow
