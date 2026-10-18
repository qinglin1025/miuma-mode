$ErrorActionPreference='Stop'
$base=Join-Path $PSScriptRoot 'tools'
New-Item -ItemType Directory -Force $base | Out-Null
function FetchChecked($url,$path,$algorithm,$checksum){
    if((Test-Path -LiteralPath $path) -and ((Get-FileHash -Algorithm $algorithm -LiteralPath $path).Hash -eq $checksum)){return}
    Invoke-WebRequest -Uri $url -OutFile $path
    if((Get-FileHash -Algorithm $algorithm -LiteralPath $path).Hash -ne $checksum){throw "Download checksum mismatch: $path"}
}
$info=Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
$package=$info[0].binary.package
FetchChecked $package.link "$base\jdk.zip" SHA256 $package.checksum
if(-not(Test-Path "$base\jdk")){Expand-Archive -LiteralPath "$base\jdk.zip" -DestinationPath "$base\jdk"}
Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/repository2-3.xml' -OutFile "$base\repository.xml"
[xml]$repo=Get-Content -Raw "$base\repository.xml"
foreach($id in @('platforms;android-35','build-tools;35.0.0','platform-tools')){
    $p=$repo.SelectNodes('//*[local-name()="remotePackage"]') | Where-Object {$_.path -eq $id}
    if(-not $p){throw "SDK package unavailable: $id"}
    $a=@($p.archives.archive) | Where-Object {-not $_.'host-os' -or $_.'host-os' -eq 'windows'} | Select-Object -First 1
    $file=Join-Path $base $a.complete.url
    FetchChecked ('https://dl.google.com/android/repository/'+$a.complete.url) $file SHA1 $a.complete.checksum.InnerText
    Expand-Archive -LiteralPath $file -DestinationPath "$base\sdk" -Force
    Write-Output "Ready: $id"
}
Write-Output 'Ready. Run ./build.ps1'
