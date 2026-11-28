# SPDX-License-Identifier: GPL-3.0-or-later
param([string]$Operation = "mount")
$ErrorActionPreference = "Stop"
$image = Join-Path $PWD "platform-volume.vhd"
$script = Join-Path $PWD "platform-diskpart.txt"
if ($Operation -eq "cleanup") {
    if (Test-Path $image) {
        @("select vdisk file=`"$image`"", "detach vdisk") | Set-Content -Encoding ascii $script
        diskpart /s $script
        if ($LASTEXITCODE -ne 0) { throw "diskpart detach failed: $LASTEXITCODE" }
    }
    exit
}
if ($env:PLATFORM_FILESYSTEM_CASE -eq "windows-ntfs") {
    $volume = $PWD.Path
} elseif ($env:PLATFORM_FILESYSTEM_CASE -eq "windows-exfat") {
    @(
        "create vdisk file=`"$image`" maximum=256 type=expandable",
        "select vdisk file=`"$image`"",
        "attach vdisk",
        "create partition primary",
        "format fs=exfat quick label=ICSForms",
        "assign letter=R"
    ) | Set-Content -Encoding ascii $script
    diskpart /s $script
    if ($LASTEXITCODE -ne 0) { throw "diskpart setup failed: $LASTEXITCODE" }
    $volume = "R:\"
} else {
    throw "Unknown case: $env:PLATFORM_FILESYSTEM_CASE"
}
"PLATFORM_FILESYSTEM_ROOT=$volume" | Out-File -FilePath $env:GITHUB_ENV -Encoding utf8 -Append
Get-Volume | Format-Table DriveLetter, FileSystem, FileSystemLabel
