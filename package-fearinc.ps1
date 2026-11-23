$ErrorActionPreference = 'Stop'

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$inputDir = Join-Path $project 'target\jpackage-input'
$outputDir = Join-Path $project 'target\FearInc-package'

Push-Location $project
try {
  mvn -q clean package -DskipTests
  if ($LASTEXITCODE -ne 0) { throw 'Maven packaging failed.' }

  Remove-Item $inputDir -Recurse -Force -ErrorAction SilentlyContinue
  Remove-Item $outputDir -Recurse -Force -ErrorAction SilentlyContinue
  New-Item $inputDir -ItemType Directory | Out-Null
  New-Item $outputDir -ItemType Directory | Out-Null

  Copy-Item (Join-Path $project 'target\FearInc.jar') $inputDir
  Copy-Item (Join-Path $project 'content') $inputDir -Recurse
  Copy-Item (Join-Path $project 'web') $inputDir -Recurse

  $jpackageArgs = @(
    '--type', 'app-image',
    '--name', 'FearInc',
    '--input', $inputDir,
    '--main-jar', 'FearInc.jar',
    '--main-class', 'trainer.Main',
    '--dest', $outputDir,
    '--app-version', '1.0',
    '--java-options', '-Dtrainer.root=.',
    '--java-options', '-Dtrainer.packaged=true',
    '--java-options', '-Dtrainer.openBrowser=true'
  )

  & jpackage @jpackageArgs
  if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }

  Write-Host "Created: $outputDir\FearInc\FearInc.exe"
}
finally {
  Pop-Location
}
