Global SquadNetSession%
Global SquadNetName$ = "PLAYER"
Global SquadNetHost$ = "127.0.0.1"
Global SquadNetPort$ = "23513"
Global SquadNetStatus$ = "OFFLINE"

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
	SquadNetStatus$ = "CONNECTING"
	SquadNetSession = NetJoinSession(host$, port%)
	If SquadNetSession = 0 Then
		SquadNetStatus$ = "NO LOBBY"
		Return
	EndIf
	SquadNetStatus$ = "JOINING"
End Function

Function SquadNetDisconnect()
	If SquadNetSession <> 0 Then CloseNetSession SquadNetSession
	SquadNetSession = 0
	SquadNetStatus$ = "OFFLINE"
End Function

Function SquadNetUpdate()
	If SquadNetSession = 0 Then Return
	NetPoll SquadNetSession
	Local e$
	Repeat
		e$ = NetEvent(SquadNetSession)
		If e$ = "welcome" Then
			SquadNetStatus$ = "IN LOBBY"
		ElseIf e$ = "disconnected" Then
			SquadNetStatus$ = "DISCONNECTED"
		EndIf
	Until e$ = ""
	If NetConnected(SquadNetSession) Then
		If SquadNetStatus$ = "CONNECTING" Then SquadNetStatus$ = "JOINING"
	ElseIf SquadNetStatus$ <> "OFFLINE" Then
		SquadNetStatus$ = "DISCONNECTED"
	EndIf
End Function

Function SquadNetInLobby%()
	If SquadNetSession = 0 Then Return False
	If NetIsAuthority(SquadNetSession) Then Return True
	Return SquadNetStatus$ = "IN LOBBY"
End Function

Function SquadNetSlotName$(slot%)
	If slot% = 0 Then
		If Trim(SquadNetName$) = "" Then Return "PLAYER"
		Return SquadNetName$
	EndIf
	If SquadNetSession = 0 Then Return ""
	If NetConnected(SquadNetSession) = 0 Then Return ""
	Local count% = NetPeerCount(SquadNetSession)
	If count% <= 1 Then Return ""
	Local localId% = NetSessionId(SquadNetSession)
	Local found% = 1
	For i% = 1 To count%
		Local id% = NetPeerId(SquadNetSession, i%)
		If id% <> localId% Then
			If found% = slot% Then Return "PEER "+Str(id%)
			found% = found% + 1
		EndIf
	Next
	Return ""
End Function
