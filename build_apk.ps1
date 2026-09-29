# Builds FlashcardQuizApp.apk from the android/ project using the raw Android
# build-tools (no Gradle): aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Requires: Android SDK (build-tools 36.1.0) and JDK 17.

$ErrorActionPreference = "Stop"

$Sdk = "C:\Users\callm\AppData\Local\Android\Sdk"
$BuildTools = Join-Path $Sdk "build-tools\36.1.0"
$JavaHome = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$Platform = Join-Path $Sdk "platforms\android-36.1\android.jar"

$Root = $PSScriptRoot
$Proj = Join-Path $Root "android"
$Build = Join-Path $Root "build\apk"
$OutDir = Join-Path $Root "dist"
$ApkOut = Join-Path $OutDir "FlashcardQuizApp.apk"
$Keystore = Join-Path $Root "android\debug.keystore"

if (-not (Test-Path $Platform)) { throw "android.jar not found at $Platform" }
if (-not (Test-Path (Join-Path $BuildTools "aapt2.exe"))) { throw "build-tools not found at $BuildTools" }

New-Item -ItemType Directory -Force -Path $Build, $OutDir | Out-Null
Get-ChildItem $Build -Recurse -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"

function Step($name) { Write-Host "==> $name" -ForegroundColor Cyan }

# 1. compile resources
Step "aapt2 compile"
& (Join-Path $BuildTools "aapt2.exe") compile --dir (Join-Path $Proj "res") -o (Join-Path $Build "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

# 2. link resources + manifest -> base.apk, emit R.java
Step "aapt2 link"
& (Join-Path $BuildTools "aapt2.exe") link `
  -o (Join-Path $Build "base.apk") `
  -I $Platform `
  --manifest (Join-Path $Proj "AndroidManifest.xml") `
  -R (Join-Path $Build "res.zip") `
  --java (Join-Path $Build "gen") `
  --min-sdk-version 24 `
  --target-sdk-version 36 `
  --version-code 1 `
  --version-name "1.0.0" `
  --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# 3. javac -> classes
Step "javac"
$src = Get-ChildItem (Join-Path $Proj "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force -Path (Join-Path $Build "classes") | Out-Null
& (Join-Path $JavaHome "bin\javac.exe") `
  -source 8 -target 8 `
  -bootclasspath $Platform `
  -classpath (Join-Path $Build "gen") `
  -d (Join-Path $Build "classes") `
  $src
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

# 4. d8 -> classes.dex
Step "d8"
New-Item -ItemType Directory -Force -Path (Join-Path $Build "dex") | Out-Null
& (Join-Path $BuildTools "d8.bat") `
  --release --min-api 24 `
  --lib $Platform `
  --output (Join-Path $Build "dex") `
  (Join-Path $Build "classes\com\flashcardquiz\app\*.class")
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

# 5. merge dex into apk (zip)
Step "merge dex"
$env:PYTHONPATH = ""
& (Join-Path $Root ".venv\Scripts\python.exe") -c @"
import shutil, zipfile
src = r'$($Build -replace "\\", "\\\\")\base.apk'.replace('\\\\', '\\')
dst = r'$($Build -replace "\\", "\\\\")\unsigned.apk'.replace('\\\\', '\\')
dex = r'$($Build -replace "\\", "\\\\")\dex\classes.dex'.replace('\\\\', '\\')
shutil.copy(src, dst)
with zipfile.ZipFile(dst, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
print('dex merged')
"@
if ($LASTEXITCODE -ne 0) { throw "merge failed" }

# 6. zipalign
Step "zipalign"
& (Join-Path $BuildTools "zipalign.exe") -f 4 (Join-Path $Build "unsigned.apk") (Join-Path $Build "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# 7. keystore + sign
if (-not (Test-Path $Keystore)) {
  Step "keytool (creating debug keystore)"
  & (Join-Path $JavaHome "bin\keytool.exe") -genkeypair `
    -keystore $Keystore -alias fq -keyalg RSA -keysize 2048 -validity 10000 `
    -storepass android -keypass android -dname "CN=Flashcard Quiz"
  if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}
Step "apksigner"
& (Join-Path $BuildTools "apksigner.bat") sign `
  --ks $Keystore --ks-pass pass:android --key-pass pass:android `
  --out $ApkOut (Join-Path $Build "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

# 8. verify
Step "verify"
& (Join-Path $BuildTools "apksigner.bat") verify --print-certs $ApkOut
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }

$apk = Get-Item $ApkOut
Write-Host ""
Write-Host "APK ready: $($apk.FullName) ($([math]::Round($apk.Length / 1MB, 1)) MB)" -ForegroundColor Green
