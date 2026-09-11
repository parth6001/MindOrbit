$ErrorActionPreference = 'Stop'
$destination = Join-Path $PSScriptRoot '..\app\src\main\res\raw'
New-Item -ItemType Directory -Force $destination | Out-Null
$tones = @{ soft = @(523.25, 659.25); gentle = @(440, 554.37); digital = @(880, 1174.66); bell = @(783.99, 1046.50); calm = @(349.23, 440) }
foreach ($name in $tones.Keys) {
    $sampleRate = 22050
    $samples = $sampleRate * 2
    $stream = [System.IO.File]::Create((Join-Path $destination "$name.wav"))
    $writer = [System.IO.BinaryWriter]::new($stream)
    try {
        $writer.Write([System.Text.Encoding]::ASCII.GetBytes('RIFF'))
        $writer.Write([int](36 + $samples * 2))
        $writer.Write([System.Text.Encoding]::ASCII.GetBytes('WAVEfmt '))
        $writer.Write([int]16)
        $writer.Write([int16]1)
        $writer.Write([int16]1)
        $writer.Write([int]$sampleRate)
        $writer.Write([int]($sampleRate * 2))
        $writer.Write([int16]2)
        $writer.Write([int16]16)
        $writer.Write([System.Text.Encoding]::ASCII.GetBytes('data'))
        $writer.Write([int]($samples * 2))
        for ($i = 0; $i -lt $samples; $i++) {
            $t = $i / $sampleRate
            $envelope = [Math]::Min(1, $t * 30) * [Math]::Exp(-2.5 * $t)
            $wave = [Math]::Sin(2 * [Math]::PI * $tones[$name][0] * $t) * 0.65 + [Math]::Sin(2 * [Math]::PI * $tones[$name][1] * $t) * 0.35
            $writer.Write([int16]($wave * $envelope * 10000))
        }
    } finally { $writer.Dispose(); $stream.Dispose() }
}
