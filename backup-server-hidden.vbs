Option Explicit

Dim shell, files, folder, engine, script, command, result
Set shell = CreateObject("WScript.Shell")
Set files = CreateObject("Scripting.FileSystemObject")
folder = files.GetParentFolderName(WScript.ScriptFullName)
engine = shell.ExpandEnvironmentStrings("%SystemRoot%") & "\System32\WindowsPowerShell\v1.0\powershell.exe"
script = files.BuildPath(folder, "backup-server.ps1")
command = Chr(34) & engine & Chr(34) & " -NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File " & Chr(34) & script & Chr(34) & " -DatabaseOnly"

' Wait for the backup so Task Scheduler receives its exit code.
result = shell.Run(command, 0, True)
WScript.Quit result
