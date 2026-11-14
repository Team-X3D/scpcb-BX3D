Function ExecuteCfg%(file$)
	If FileType(file) <> 1 Then Return 0

	Local f% = ReadFile(file)
	While (Not Eof(f))
		Local l$ = ReadLine(f)
		If l <> "" And Instr(l, ";") <> 1 Then ExecuteConsoleCommand(l, True)
	Wend
	CloseFile(f)
	Return 1
End Function

Function ExecuteConsoleCommand(cmd$, quiet% = False)
	ConsoleQuiet = quiet
	Local prevUsedConsole% = UsedConsole
	UsedConsole = True
	ConsoleReissue = Null
	ConsoleScroll = 0
	CreateConsoleMsg(cmd,255,255,0,True)

	Local name$
	Local args$
	If Instr(cmd, " ") > 0 Then
		name$ = Lower(Left(cmd, Instr(cmd, " ") - 1))
		args$ = Trim(Right(cmd, Len(cmd) - Instr(cmd, " ")))
	Else
		name$ = Lower(cmd)
		args$ = ""
	EndIf

	Select name$
		Case "help"
			CreateConsoleMsg("  godmode [on/off] | noclip [on/off] | kill")
			CreateConsoleMsg("  teleport [room] | tele [x y z]")
			CreateConsoleMsg("  showfps [on/off]")

		Case "asd"
			GodMode = True
			NoClip = True
			WireframeState = 1
			WireFrame 1
			CameraFogNear = 20
			CameraFogFar = 30
			CreateConsoleMsg("godmode, noclip and wireframe enabled")

		Case "godmode", "god"
			If args$ = "on" Lor args$ = "1" Then
				GodMode = True
			ElseIf args$ = "off" Lor args$ = "0" Then
				GodMode = False
			Else
				GodMode = (Not GodMode)
			EndIf
			CreateConsoleMsg("godmode " + Str(GodMode))

		Case "noclip", "fly"
			If args$ = "on" Lor args$ = "1" Then
				NoClip = True
			ElseIf args$ = "off" Lor args$ = "0" Then
				NoClip = False
			Else
				NoClip = (Not NoClip)
			EndIf
			CreateConsoleMsg("noclip " + Str(NoClip))

		Case "notarget"
			NoTarget = (Not NoTarget)
			CreateConsoleMsg("notarget " + Str(NoTarget))

		Case "wireframe"
			If args$ = "on" Lor args$ = "1" Then
				WireframeState = 1
			ElseIf args$ = "off" Lor args$ = "0" Then
				WireframeState = 0
			Else
				WireframeState = (Not WireframeState)
			EndIf
			WireFrame WireframeState
			CreateConsoleMsg("wireframe " + Str(WireframeState))

		Case "showfps"
			ShowFPS = (Not ShowFPS)
			CreateConsoleMsg("showfps " + Str(ShowFPS))

		Case "tele"
			Local px# = Float(Piece$(args$,1," "))
			Local py# = Float(Piece$(args$,2," "))
			Local pz# = Float(Piece$(args$,3," "))
			PositionEntity Collider,px,py,pz
			ResetEntity Collider
			CreateConsoleMsg("teleported to "+px+"|"+py+"|"+pz)

		Case "teleport"
			Local wanted$ = Lower(args$)
			For r.Rooms = Each Rooms
				If Lower(r\RoomTemplate\Name) = wanted$ Then
					Teleport(r)
					CreateConsoleMsg("teleported to "+r\RoomTemplate\Name)
					Return
				EndIf
			Next
			CreateConsoleMsg("room not found: "+args$,255,0,0)

		Case "camerafog"
			CameraFogNear = Float(Piece$(args$,1," "))
			CameraFogFar = Float(Piece$(args$,2," "))
			CreateConsoleMsg("camera fog "+CameraFogNear+" / "+CameraFogFar)

		Case "fov"
			FOV = Float(args$)
			CreateConsoleMsg("fov "+FOV)

		Case "gamma"
			ScreenGamma = Float(args$)
			CreateConsoleMsg("gamma "+ScreenGamma)

		Case "timescale"
			TimeScale = Float(args$)
			CreateConsoleMsg("timescale "+TimeScale)

		Case "kill", "suicide"
			Kill()

		Default
			CreateConsoleMsg("Unknown command: "+name$,255,0,0)
	End Select
End Function
