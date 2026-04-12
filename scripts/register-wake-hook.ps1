# Run this once as Administrator (right-click PowerShell > Run as Administrator)
# Creates a Windows scheduled task that kills zombie Kotlin LSP processes when the PC wakes from sleep

$action = New-ScheduledTaskAction `
    -Execute 'wmic' `
    -Argument 'process where "commandline like ''%KotlinLspServerKt%''" delete'

# Trigger on Event ID 107 = system resume from sleep (Kernel-Power source)
$triggerXml = @"
<QueryList>
  <Query Id="0" Path="System">
    <Select Path="System">
      *[System[Provider[@Name='Microsoft-Windows-Kernel-Power'] and EventID=107]]
    </Select>
  </Query>
</QueryList>
"@

$CIMTriggerClass = Get-CimClass `
    -ClassName MSFT_TaskEventTrigger `
    -Namespace Root/Microsoft/Windows/TaskScheduler

$trigger = New-CimInstance -CimClass $CIMTriggerClass -ClientOnly
$trigger.Subscription = $triggerXml
$trigger.Enabled = $true

$settings = New-ScheduledTaskSettingsSet `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 1) `
    -MultipleInstances IgnoreNew

Register-ScheduledTask `
    -TaskName 'KillKotlinLspOnWake' `
    -Action $action `
    -Trigger $trigger `
    -RunLevel Highest `
    -Description 'Kills zombie KotlinLspServerKt JVM processes after PC wakes from sleep, so the Kotlin LSP can reconnect cleanly in Claude Code.' `
    -Force

Write-Host "Task registered. Test it with: Start-ScheduledTask -TaskName KillKotlinLspOnWake"
