Option Explicit

Dim shell, fso, folder, serverFile
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

folder = fso.GetParentFolderName(WScript.ScriptFullName)
serverFile = fso.BuildPath(folder, "server.js")
shell.CurrentDirectory = folder

' Window style 0 keeps the local server completely hidden.
shell.Run "node """ & serverFile & """", 0, False
