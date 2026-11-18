$login = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"sunzhihua","password":"123456"}' -TimeoutSec 15
$token = $login.data.token
$req = [Net.HttpWebRequest]::Create("http://127.0.0.1:8081/api/v1/ai/ws/hermes/stream?token=$token")
$req.Timeout = 10000
try {
    $resp = $req.GetResponse()
    Write-Output ("SSE HTTP STATUS: " + [int]$resp.StatusCode)
    Write-Output ("CONTENT-TYPE: " + $resp.ContentType)
    $stream = $resp.GetResponseStream()
    $reader = New-Object IO.StreamReader($stream)
    $sb = New-Object Text.StringBuilder
    $deadline = (Get-Date).AddSeconds(4)
    $respStream = $stream
    $buf = New-Object byte[] 2048
    while ((Get-Date) -lt $deadline) {
        if ($stream.DataAvailable) {
            $n = $stream.Read($buf, 0, $buf.Length)
            if ($n -gt 0) { [void]$sb.Append([Text.Encoding]::UTF8.GetString($buf, 0, $n)) }
        } else { Start-Sleep -Milliseconds 200 }
    }
    $resp.Close()
    Write-Output ("SSE FIRST BYTES: " + $sb.ToString().Substring(0, [Math]::Min(120, $sb.Length)))
} catch {
    Write-Output ("SSE FAIL: " + $_.Exception.Message)
}
