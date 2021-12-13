function AsyncPowershell($Cmds) {
  try {
    $MaxRunspace = $Cmds.Length
    $RunspacePool = [RunspaceFactory]::CreateRunspacePool(1, $MaxRunspace)
    $RunspacePool.Open()

    $aryPowerShell  = New-Object System.Collections.ArrayList
    $aryIAsyncResult  = New-Object System.Collections.ArrayList
    for ( $i = 0; $i -lt $MaxRunspace; $i++ ) {
      $Cmd = $Cmds[$i]
      $PowerShell = [PowerShell]::Create()
      $PowerShell.RunspacePool = $RunspacePool
      $PowerShell.AddScript($Cmd)
      $PowerShell.AddCommand("Out-String")
      $IAsyncResult = $PowerShell.BeginInvoke()

      $aryPowerShell.Add($PowerShell)
      $aryIAsyncResult.Add($IAsyncResult)
    }

    while ( $aryPowerShell.Count -gt 0 ) {
      for ( $i = 0; $i -lt $aryPowerShell.Count; $i++ ) {
        $PowerShell = $aryPowerShell[$i]
        $IAsyncResult = $aryIAsyncResult[$i]

        if($PowerShell -ne $null) {
          if($IAsyncResult.IsCompleted) {
            $Result = $PowerShell.EndInvoke($IAsyncResult)
            Write-host $Result
            $PowerShell.Dispose()
            $aryPowerShell.RemoveAt($i)
            $aryIAsyncResult.RemoveAt($i)
            {break outer}
          }
        }
      }
      Start-Sleep -Milliseconds 100
    }
  } catch [Exception] {
    Write-Host $_.Exception.Message;
  } finally {
    $RunspacePool.Close()
  }
}

Set-Location -Path ${PSScriptRoot}

$PSCmds = @(
  "endpoint/deploy.ps1",
  "subscriber/deploy.ps1",
  "maintenance/deploy.ps1"
)

$res = AsyncPowershell $PSCmds
