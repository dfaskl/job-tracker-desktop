Option Explicit
Dim shell, files, folder, engine, command, result
Set shell = CreateObject("WScript.Shell")
Set files = CreateObject("Scripting.FileSystemObject")
folder = files.GetParentFolderName(WScript.ScriptFullName)
engine = shell.ExpandEnvironmentStrings("%SystemRoot%") & "\System32\WindowsPowerShell\v1.0\powershell.exe"
shell.CurrentDirectory = folder
command = Chr(34) & engine & Chr(34) & " -NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File " & Chr(34) & folder & "\start.ps1" & Chr(34) & " -Watch"
' Remain attached to the scheduler, but never create a visible console window.
result = shell.Run(command, 0, True)
WScript.Quit result
