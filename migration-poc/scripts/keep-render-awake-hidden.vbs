Option Explicit

Dim shell, fileSystem, scriptDirectory, scriptPath, command
Set shell = CreateObject("WScript.Shell")
Set fileSystem = CreateObject("Scripting.FileSystemObject")
scriptDirectory = fileSystem.GetParentFolderName(WScript.ScriptFullName)
scriptPath = fileSystem.BuildPath(scriptDirectory, "keep-render-awake.ps1")
command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File " & Chr(34) & scriptPath & Chr(34)
shell.Run command, 0, False