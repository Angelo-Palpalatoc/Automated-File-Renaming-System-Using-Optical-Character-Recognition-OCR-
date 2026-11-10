$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$graphifyExe = Join-Path $projectRoot '.tools/graphify-tool/graphifyy/Scripts/graphify.exe'

if (!(Test-Path -LiteralPath $graphifyExe)) {
    $installedCommand = Get-Command graphify.exe -ErrorAction SilentlyContinue
    if ($installedCommand) {
        $graphifyExe = $installedCommand.Source
    } else {
        throw 'Graphify is not installed. Install graphifyy 0.9.104 with uv, then rerun this script.'
    }
}

$graphRoot = Join-Path $projectRoot '.tools/graphify-project'
$graphPath = Join-Path $graphRoot 'graphify-out/graph.json'
$vaultPath = Join-Path $projectRoot '.tools/secrename-knowledge-vault'
$env:GRAPHIFY_BACKEND = 'none'
$env:GRAPHIFY_QUERY_LOG_DISABLE = '1'

Push-Location $projectRoot
try {
    & $graphifyExe extract $projectRoot --code-only --out $graphRoot --max-workers 4
    if ($LASTEXITCODE -ne 0) { throw 'Graphify code extraction failed.' }

    & $graphifyExe cluster-only $graphRoot --graph $graphPath --no-label --no-viz
    if ($LASTEXITCODE -ne 0) { throw 'Graphify clustering failed.' }

    & $graphifyExe export obsidian --graph $graphPath --dir $vaultPath
    if ($LASTEXITCODE -ne 0) { throw 'Graphify Obsidian export failed.' }

    Write-Output "Graph: $graphPath"
    Write-Output "Obsidian vault: $vaultPath"
} finally {
    Pop-Location
}
