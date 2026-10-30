param([Parameter(Mandatory=$true)][string]$InputFolder, [string]$FilePattern = 'SEC_Demo_*.pdf')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
[Windows.Storage.StorageFile, Windows.Storage, ContentType=WindowsRuntime] | Out-Null
[Windows.Data.Pdf.PdfDocument, Windows.Data.Pdf, ContentType=WindowsRuntime] | Out-Null
[Windows.Storage.Streams.InMemoryRandomAccessStream, Windows.Storage.Streams, ContentType=WindowsRuntime] | Out-Null
[Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType=WindowsRuntime] | Out-Null
[Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType=WindowsRuntime] | Out-Null
$awaitMethod = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1
$awaitAction = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and !$_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction' } | Select-Object -First 1
function Await-Result($operation, [Type]$resultType) {
    $task = $awaitMethod.MakeGenericMethod($resultType).Invoke($null, @($operation))
    $task.Wait()
    return $task.Result
}
$engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
if (!$engine) { throw 'No Windows OCR language is installed.' }
$outputFolder = Join-Path (Split-Path -Parent $PSScriptRoot) '.tools/synthetic-ocr'
New-Item -ItemType Directory -Force -Path $outputFolder | Out-Null
foreach ($pdfFile in Get-ChildItem -LiteralPath $InputFolder -Filter $FilePattern -File | Sort-Object Name) {
    $file = Await-Result ([Windows.Storage.StorageFile]::GetFileFromPathAsync($pdfFile.FullName)) ([Windows.Storage.StorageFile])
    $page = $null
    if ($pdfFile.Extension -ieq '.pdf') {
        $pdf = Await-Result ([Windows.Data.Pdf.PdfDocument]::LoadFromFileAsync($file)) ([Windows.Data.Pdf.PdfDocument])
        $page = $pdf.GetPage(0)
        $stream = New-Object Windows.Storage.Streams.InMemoryRandomAccessStream
        $options = New-Object Windows.Data.Pdf.PdfPageRenderOptions
        $scale = 2000.0 / [Math]::Max($page.Size.Width, $page.Size.Height)
        $options.DestinationWidth = [uint32]($page.Size.Width * $scale)
        $options.DestinationHeight = [uint32]($page.Size.Height * $scale)
        $task = $awaitAction.Invoke($null, @($page.RenderToStreamAsync($stream, $options)))
        $task.Wait()
    } else {
        [Windows.Storage.FileAccessMode, Windows.Storage, ContentType=WindowsRuntime] | Out-Null
        [Windows.Storage.Streams.IRandomAccessStream, Windows.Storage.Streams, ContentType=WindowsRuntime] | Out-Null
        $stream = Await-Result ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
    }
    $decoder = Await-Result ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
    $bitmap = Await-Result ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
    $result = Await-Result ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])
    $lines = @($result.Lines | ForEach-Object { ($_.Words | ForEach-Object { $_.Text }) -join ' ' })
    [IO.File]::WriteAllLines((Join-Path $outputFolder ($pdfFile.BaseName + '.txt')), [string[]]$lines, [Text.UTF8Encoding]::new($false))
    Write-Output ($pdfFile.Name + ': ' + $lines.Count + ' OCR lines')
    $bitmap.Dispose(); $stream.Dispose(); if ($page) { $page.Dispose() }
}
Write-Output 'Local Windows OCR inspection complete. This is not a run of Android ML Kit.'
exit 0
