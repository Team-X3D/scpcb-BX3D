Global ItemNetNext%

Function ItemNetReset()
	ItemNetNext = 0
End Function

Function ItemNetById.Items(id%)
	For i.Items = Each Items
		If i\netid = id% Then Return i
	Next
	Return Null
End Function

Function ItemNetSendTake(id%)
	If SquadNetSession = 0 Then Return
	If NetConnected(SquadNetSession) = 0 Then Return
	NetBroadcast(SquadNetSession, "itake|"+Str(id%), True)
End Function

Function ItemNetSendDrop(id%, x#, y#, z#)
	If SquadNetSession = 0 Then Return
	If NetConnected(SquadNetSession) = 0 Then Return
	NetBroadcast(SquadNetSession, "idrop|"+Str(id%)+"|"+Str(x#)+"|"+Str(y#)+"|"+Str(z#), True)
End Function

Function ItemNetHandle%(msg$, tag$)
	If tag$ = "itake" Then
		Local taken.Items = ItemNetById(Int(SquadNetField$(msg$, 2)))
		If taken <> Null Then
			taken\Picked = False
			taken\taken = True
			HideEntity taken\collider
		EndIf
		Return True
	EndIf
	If tag$ = "idrop" Then
		Local dropped.Items = ItemNetById(Int(SquadNetField$(msg$, 2)))
		If dropped <> Null Then
			dropped\taken = False
			dropped\Picked = False
			ShowEntity dropped\collider
			PositionEntity dropped\collider, Float(SquadNetField$(msg$, 3)), Float(SquadNetField$(msg$, 4)), Float(SquadNetField$(msg$, 5)), True
			ResetEntity dropped\collider
		EndIf
		Return True
	EndIf
	Return False
End Function
