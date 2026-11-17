Const NET_PORT%        = 23513
Const NET_MAX_PLAYERS% = 4
Const NET_TICK_HZ%     = 20

Global SquadNetSession%
Global SquadNetName$ = "PLAYER"
Global SquadNetHost$ = "127.0.0.1"
Global SquadNetPort$ = Str(NET_PORT)
Global SquadNetStatus$ = "OFFLINE"
Global SquadNetNotice$, SquadNetNoticeUntil%
Global SquadNetIsHost%
Global SquadNetHasWelcome%
Global SquadNetLocalSlot% = -1
Global SquadNetStarted%
Global SquadNetPendingStart%
Global SquadNetStartSeed%
Global SquadNetJoining%, SquadNetJoinDeadline%
Global SquadNetRosterSynced%

Dim SquadNetSlots$(NET_MAX_PLAYERS-1)
Dim SquadNetSlotPeer%(NET_MAX_PLAYERS-1)

;-------------------------------------------------- helpers ------------------------------------------------

Function SquadNetCleanName$(s$)
	Local out$ = "", ch$
	For i% = 1 To Len(s$)
		ch$ = Mid(s$, i%, 1)
		If ch$ <> "|" Then
			out$ = out$ + ch$
			If Len(out$) >= 16 Then Exit
		EndIf
	Next
	out$ = Trim(out$)
	If out$ = "" Then out$ = "PLAYER"
	Return out$
End Function

Function SquadNetField$(s$, n%)
	Local f% = 1, start% = 1
	For i% = 1 To Len(s$)
		If Mid(s$, i%, 1) = "|" Then
			If f% = n% Then Return Mid(s$, start%, i%-start%)
			f% = f% + 1
			start% = i% + 1
		EndIf
	Next
	If f% <> n% Then Return ""
	If start% > Len(s$) Then Return ""
	Return Mid(s$, start%)
End Function

Function SquadNetPeerExists%(sid%)
	If SquadNetSession = 0 Then Return False
	Local n% = NetPeerCount(SquadNetSession)
	If n% <= 0 Then Return False
	For i% = 1 To n%
		If NetPeerId(SquadNetSession, i%) = sid% Then Return True
	Next
	Return False
End Function

Function SquadNetRoster$()
	Local s$ = "roster"
	For i% = 0 To NET_MAX_PLAYERS-1
		s$ = s$ + "|" + SquadNetSlots$(i%)
	Next
	Return s$
End Function

Function SquadNetApplyRoster(msg$)
	Local old$, cur$, wasSynced% = SquadNetRosterSynced
	SquadNetRosterSynced = True
	For i% = 0 To NET_MAX_PLAYERS-1
		old$ = SquadNetSlots$(i%)
		cur$ = SquadNetField$(msg$, i%+2)
		If cur$ <> old$ Then
			If wasSynced And (i% <> SquadNetLocalSlot) Then
				If cur$ = "" Then
					If old$ <> "" Then SquadNetStatus$ = old$+" LEFT"
				Else
					If old$ = "" Then SquadNetStatus$ = cur$+" JOINED"
				EndIf
			EndIf
			SquadNetSlots$(i%) = cur$
		EndIf
	Next
End Function

;-------------------------------------------------- session ------------------------------------------------

Function SquadNetHostLobby(port%)
	If port% < 1 Or port% > 65535 Then
		SquadNetStatus$ = "BAD PORT"
		Return
	EndIf
	SquadNetDisconnect()
	SquadNetStatus$ = "HOSTING"
	SquadNetSession = NetCreateSession(port%)
	If SquadNetSession = 0 Then
		SquadNetStatus$ = "HOST FAILED"
		Return
	EndIf
	NetSetMaxPeers(SquadNetSession, NET_MAX_PLAYERS-1)
	SquadNetIsHost = True
	SquadNetHasWelcome = True
	SquadNetLocalSlot = 0
	SquadNetSlots$(0) = SquadNetCleanName$(SquadNetName$)
	SquadNetHostSendRoster()
	SquadNetStatus$ = "IN LOBBY"
End Function

Function SquadNetConnect(host$, port%)
	If host$ = "" Then
		SquadNetStatus$ = "ENTER ADDRESS"
		Return
	EndIf
	If port% < 1 Or port% > 65535 Then
		SquadNetStatus$ = "BAD PORT"
		Return
	EndIf
	SquadNetDisconnect()
	SquadNetSession = NetJoinSession(host$, port%)
	If SquadNetSession = 0 Then
		SquadNetStatus$ = "NO LOBBY"
		Return
	EndIf
	SquadNetJoining = True
	SquadNetJoinDeadline = MilliSecs()+10000
	SquadNetStatus$ = "CONNECTING"
End Function

Function SquadNetDisconnect()
	If SquadNetSession <> 0 Then CloseNetSession SquadNetSession
	SquadNetSession = 0
	SquadNetIsHost = False
	SquadNetHasWelcome = False
	SquadNetLocalSlot = -1
	SquadNetStarted = False
	SquadNetPendingStart = False
	SquadNetRosterSynced = False
	SquadNetJoining = False
	SquadNetJoinDeadline = 0
	For i% = 0 To NET_MAX_PLAYERS-1
		SquadNetSlots$(i%) = ""
		SquadNetSlotPeer%(i%) = 0
	Next
	SquadNetStatus$ = "OFFLINE"
End Function

Function SquadNetInLobby%()
	If SquadNetSession = 0 Then Return False
	If SquadNetIsHost Then Return True
	Return SquadNetHasWelcome And SquadNetLocalSlot >= 0
End Function

Function SquadNetSlotName$(slot%)
	If slot% < 0 Or slot% > NET_MAX_PLAYERS-1 Then Return ""
	Return SquadNetSlots$(slot%)
End Function

;-------------------------------------------------- host side -----------------------------------------------

Function SquadNetHostSendRoster()
	If SquadNetSession = 0 Then Return
	If NetConnected(SquadNetSession) = 0 Then Return
	NetBroadcast(SquadNetSession, SquadNetRoster$(), True)
End Function

Function SquadNetHostRefuse(sid%)
	If SquadNetSession = 0 Then Return
	If NetConnected(SquadNetSession) Then NetSend(SquadNetSession, sid%, "deny", True)
	NetKick(SquadNetSession, sid%)
End Function

Function SquadNetHostHello(sid%, name$)
	If SquadNetStarted Then
		SquadNetHostRefuse(sid%)
		Return
	EndIf
	For i% = 0 To NET_MAX_PLAYERS-1
		If SquadNetSlotPeer%(i%) = sid% Then Return
	Next
	Local slot% = -1
	For i% = 1 To NET_MAX_PLAYERS-1
		If SquadNetSlotPeer%(i%) = 0 Then
			slot% = i%
			Exit
		EndIf
	Next
	If slot% = -1 Then
		SquadNetHostRefuse(sid%)
		Return
	EndIf
	SquadNetSlotPeer%(slot%) = sid%
	SquadNetSlots$(slot%) = SquadNetCleanName$(name$)
	SquadNetStatus$ = SquadNetSlots$(slot%)+" JOINED"
	If NetConnected(SquadNetSession) Then NetSend(SquadNetSession, sid%, "slot|"+Str(slot%), True)
	SquadNetHostSendRoster()
End Function

Function SquadNetHostSweep()
	Local changed% = False
	For i% = 1 To NET_MAX_PLAYERS-1
		If SquadNetSlotPeer%(i%) <> 0 Then
			If SquadNetPeerExists%(SquadNetSlotPeer%(i%)) = 0 Then
				If SquadNetSlots$(i%) <> "" Then SquadNetStatus$ = SquadNetSlots$(i%)+" LEFT"
				SquadNetSlotPeer%(i%) = 0
				SquadNetSlots$(i%) = ""
				changed = True
			EndIf
		EndIf
	Next
	If changed Then SquadNetHostSendRoster()
End Function

;-------------------------------------------------- messages ------------------------------------------------

Function SquadNetHandleMsg(msg$)
	Local tag$ = SquadNetField$(msg$, 1)
	If SquadNetIsHost Then
		If tag$ = "hello" Then SquadNetHostHello(NetMsgSender(SquadNetSession), SquadNetField$(msg$, 2))
		Return
	EndIf
	If tag$ = "roster" Then
		SquadNetApplyRoster(msg$)
	ElseIf tag$ = "slot" Then
		SquadNetLocalSlot = Int(SquadNetField$(msg$, 2))
		SquadNetStatus$ = "IN LOBBY"
	ElseIf tag$ = "deny" Then
		SquadNetDisconnect()
		SquadNetStatus$ = "SESSION ALREADY STARTED"
	ElseIf tag$ = "start" Then
		SquadNetStartSeed = Int(SquadNetField$(msg$, 2))
		SquadNetPendingStart = True
	EndIf
End Function

Function SquadNetUpdate()
	If SquadNetSession = 0 Then Return

	If SquadNetJoining Then
		If MilliSecs() > SquadNetJoinDeadline Then
			SquadNetDisconnect()
			SquadNetStatus$ = "CONNECTION TIMED OUT"
			Return
		EndIf
	EndIf

	NetPoll(SquadNetSession)

	Local e$ = "", wasInLobby%, wasJoining%
	Repeat
		e$ = NetEvent(SquadNetSession)
		If e$ = "" Then Exit
		If e$ = "welcome" Then
			SquadNetHasWelcome = True
			SquadNetJoining = False
			If NetConnected(SquadNetSession) Then NetSend(SquadNetSession, 0, "hello|"+SquadNetCleanName$(SquadNetName$), True)
		ElseIf e$ = "disconnected" Then
			If SquadNetIsHost = 0 Then
				wasInLobby = SquadNetHasWelcome
				wasJoining = SquadNetJoining
				SquadNetDisconnect()
				If wasInLobby Then
					SquadNetNotice$ = "HOST CLOSED THE CONNECTION"
					SquadNetNoticeUntil = MilliSecs()+6000
					SquadOpen = False
				ElseIf wasJoining Then
					SquadNetStatus$ = "NO LOBBY OR FULL"
				EndIf
				Return
			EndIf
		EndIf
	Forever

	Local msg$ = NetRecv(SquadNetSession)
	While msg$ <> "" And SquadNetSession <> 0
		If SquadNetIsHost = 0 Lor NetMsgSender(SquadNetSession) <> 0 Then SquadNetHandleMsg(msg$)
		If SquadNetSession = 0 Then Exit
		msg$ = NetRecv(SquadNetSession)
	Wend
	If SquadNetSession = 0 Then Return

	If SquadNetIsHost Then SquadNetHostSweep()
End Function

;-------------------------------------------------- start --------------------------------------------------

Function SquadNetStartFloor()
	If SquadNetSession = 0 Then Return
	If SquadNetIsHost = 0 Then Return
	If NetConnected(SquadNetSession) = 0 Then Return
	SquadNetStartSeed = GetRandomSeed()
	If SquadNetStartSeed = 0 Then SquadNetStartSeed = MilliSecs()
	SquadNetStarted = True
	NetBroadcast(SquadNetSession, "start|"+Str(SquadNetStartSeed), True)
	SquadNetPendingStart = True
End Function

Function SquadNetBeginGame()
	SquadNetPendingStart = False
	HasNumericSeed = True
	RandomSeedNumeric = SquadNetStartSeed
	RandomSeed$ = Str(SquadNetStartSeed)
	SeedRnd GetRandomSeed()
	SetErrorMsg(7, GetSeedString(False))
	LoadEntities()
	LoadAllSounds()
	InitRoomTemplates()
	InitNewGame()
	SquadNetPrintChecksum()
	MainMenuOpen = False
	MenuOpen = False
	MenuWasOpen = False
	SquadOpen = False
	HidePointer()
	FlushKeys()
	FlushMouse()
End Function

Function SquadNetPrintChecksum()
	Local h% = 0, n% = 0
	For r.Rooms = Each Rooms
		Local s$ = r\RoomTemplate\Name + "|" + Str(r\x) + "|" + Str(r\y) + "|" + Str(r\z)
		For i% = 1 To Len(s$)
			h = (h * 31 + Asc(Mid(s$, i%, 1))) And $FFFFFF
		Next
		n% = n% + 1
	Next
	Local who$ = "CLIENT"
	If SquadNetIsHost Then who$ = "HOST"
	CreateConsoleMsg("COOP "+who$+" seed "+Str(SquadNetStartSeed)+" rooms "+Str(n%)+" checksum "+Hex(h))
End Function
