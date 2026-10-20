param([switch]$TestOnly)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$projectRoot="."
$jdk=(Get-ChildItem -Directory "$projectRoot\tools\jdk" | Select-Object -First 1).FullName
if(-not $jdk){throw 'Java missing. See tools/download instructions.'}
$java="$jdk\bin\java.exe"
$javac="$jdk\bin\javac.exe"
$bt="$projectRoot\tools\sdk\android-15"
$android="$projectRoot\tools\sdk\android-35\android.jar"
$build="$projectRoot\build"
function Check([string]$stage){if($LASTEXITCODE -ne 0){throw "$stage failed with exit code $LASTEXITCODE"}}
New-Item -ItemType Directory -Force "$build\tests" | Out-Null
& $javac -encoding UTF-8 -d "$build\tests" "$projectRoot\app\src\main\java\cn\niuma\app\Session.java" "$projectRoot\tests\SessionTest.java" "$projectRoot\app\src\main\java\cn\niuma\app\PopupOptions.java" "$projectRoot\tests\PopupOptionsTest.java" "$projectRoot\app\src\main\java\cn\niuma\app\WeeklySchedule.java" "$projectRoot\tests\WeeklyScheduleTest.java"
Check 'Test compile'
& $java -cp "$build\tests" cn.niuma.app.SessionTest
Check 'Session tests'
& $java -cp "$build\tests" cn.niuma.app.PopupOptionsTest
Check 'Popup option tests'
& $java -cp "$build\tests" cn.niuma.app.WeeklyScheduleTest
Check 'Weekly schedule tests'
if($TestOnly){exit 0}
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$out="$build\$stamp"
New-Item -ItemType Directory -Force "$out\generated","$out\classes","$out\dex","$projectRoot\dist","$projectRoot\private" | Out-Null
& "$bt\aapt2.exe" compile --dir "$projectRoot\app\src\main\res" -o "$out\resources.zip"
Check 'Resource compile'
& "$bt\aapt2.exe" link -o "$out\unsigned.apk" -I $android --manifest "$projectRoot\app\src\main\AndroidManifest.xml" --java "$out\generated" --auto-add-overlay "$out\resources.zip"
Check 'Resource link'
$sources=@(Get-ChildItem -Recurse "$projectRoot\app\src\main\java","$out\generated" -Filter '*.java' | ForEach-Object { $_.FullName })
& $javac -encoding UTF-8 --release 8 -classpath $android -d "$out\classes" @sources
Check 'Java compile'
& "$jdk\bin\jar.exe" cf "$out\classes.jar" -C "$out\classes" .
Check 'Classes archive'
& $java -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --lib $android --min-api 26 --output "$out\dex" "$out\classes.jar"
Check 'Dex'
& "$jdk\bin\jar.exe" uf "$out\unsigned.apk" -C "$out\dex" classes.dex
Check 'APK assembly'
& "$bt\zipalign.exe" -f -p 4 "$out\unsigned.apk" "$out\aligned.apk"
Check 'Zip alignment'
$key="$projectRoot\private\niuma-personal.p12"
$secret="$projectRoot\private\signing-password.xml"
if(-not (Test-Path -LiteralPath $key)){
    if(Test-Path -LiteralPath $secret){throw 'Password exists but key missing; restore signing key before building updates.'}
    $pass=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
    ConvertTo-SecureString $pass -AsPlainText -Force | Export-Clixml -LiteralPath $secret
    $env:NIUMA_SIGNING_PASSWORD=$pass
    & "$jdk\bin\keytool.exe" -genkeypair -keystore $key -storetype PKCS12 -storepass:env NIUMA_SIGNING_PASSWORD -keypass:env NIUMA_SIGNING_PASSWORD -alias niuma -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Niuma Personal, OU=Personal Use, O=Niuma, C=CN"
    Check 'Signing key generation'
}else{
    $secure=Import-Clixml -LiteralPath $secret
    $env:NIUMA_SIGNING_PASSWORD=[System.Net.NetworkCredential]::new('',$secure).Password
}
try{
    & $java -jar "$bt\lib\apksigner.jar" sign --ks $key --ks-key-alias niuma --ks-pass env:NIUMA_SIGNING_PASSWORD --key-pass env:NIUMA_SIGNING_PASSWORD --out "$projectRoot\dist\niuma-mode-0.1.3.apk" "$out\aligned.apk"
    Check 'APK signing'
}finally{Remove-Item Env:NIUMA_SIGNING_PASSWORD -ErrorAction SilentlyContinue}
& $java -jar "$bt\lib\apksigner.jar" verify --verbose --print-certs "$projectRoot\dist\niuma-mode-0.1.3.apk"
Check 'APK signature verification'
& "$bt\zipalign.exe" -c 4 "$projectRoot\dist\niuma-mode-0.1.3.apk"
Check 'Final alignment check'
& "$bt\aapt2.exe" dump badging "$projectRoot\dist\niuma-mode-0.1.3.apk"
Check 'APK manifest inspection'
Get-FileHash -Algorithm SHA256 "$projectRoot\dist\niuma-mode-0.1.3.apk" | Format-List
Write-Output "APK: $projectRoot\dist\niuma-mode-0.1.3.apk"
