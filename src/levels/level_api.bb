Global LevelName$
Global LevelSpawnSet%
Global LevelSpawnX#, LevelSpawnY#, LevelSpawnZ#, LevelSpawnYaw#
Global CurrentLevel$ = "test"

Function LevelBegin(name$)
	LevelName$ = name$
	LevelSpawnSet = False
End Function

Function LevelEnd()
End Function

Function LevelRoom.Rooms(name$, file$, x#, y#, z#, yaw# = 0.0, s# = 1.0, fx% = 0)
	Local rt.RoomTemplates = CreateRoomTemplate(name$)
	Local r.Rooms = New Rooms
	r\RoomTemplate = rt
	r\obj = CreatePivot()
	r\zone = 0
	r\angle = 0

	Local cx# = x#, cy# = y#, cz# = z#
	Local w# = 4.0, h# = 4.0, d# = 4.0

	PositionEntity r\obj, x#, y#, z#

	If file$ <> "" Then
		Local m% = LoadMesh(file$)
		If m = 0 Then CreateConsoleMsg("level mesh not found: "+file$, 255, 0, 0)
		If m <> 0 Then
			If s# <> 1.0 Then ScaleEntity m, s#, s#, s#
			PositionEntity m, x#, y#, z#
			RotateEntity m, 0, yaw#, 0

			Local verts% = 0
			Local minX# = 999999.0, minY# = 999999.0, minZ# = 999999.0
			Local maxX# = -999999.0, maxY# = -999999.0, maxZ# = -999999.0
			For si% = 1 To CountSurfaces(m)
				Local srf% = GetSurface(m, si%)
				For vi% = 0 To CountVertices(srf%)-1
					TFormPoint VertexX(srf%, vi%), VertexY(srf%, vi%), VertexZ(srf%, vi%), m, 0
					Local wx# = TFormedX(), wy# = TFormedY(), wz# = TFormedZ()
					If wx# < minX# Then minX# = wx#
					If wy# < minY# Then minY# = wy#
					If wz# < minZ# Then minZ# = wz#
					If wx# > maxX# Then maxX# = wx#
					If wy# > maxY# Then maxY# = wy#
					If wz# > maxZ# Then maxZ# = wz#
					verts% = verts% + 1
				Next
			Next
			If verts% = 0 Then CreateConsoleMsg("level mesh has no surfaces: "+file$, 255, 0, 0)
			If verts% > 0 Then
				cx# = (minX#+maxX#)*0.5
				cy# = (minY#+maxY#)*0.5
				cz# = (minZ#+maxZ#)*0.5
				w# = maxX#-minX#
				h# = maxY#-minY#
				d# = maxZ#-minZ#
			EndIf

			PositionEntity r\obj, cx#, cy#, cz#
			EntityParent m, r\obj, True
			If fx% <> 0 Then EntityFX m, fx%
			EntityType m, HIT_MAP
			EntityPickMode m, 2
		EndIf
	EndIf

	r\x# = cx# : r\z# = cz#
	rt\MinX# = -w#*0.5 : rt\MaxX# = w#*0.5
	rt\MinY# = -h#*0.5 : rt\MaxY# = h#*0.5
	rt\MinZ# = -d#*0.5 : rt\MaxZ# = d#*0.5

	r\cullMarker = CreateCube(r\obj)
	ScaleEntity r\cullMarker, 0.05, 0.05, 0.05
	PositionEntity r\cullMarker, 0.0, 0.0, 0.0
	EntityAlpha r\cullMarker, 0.0
	EntityPickMode r\cullMarker, 0
	EntityType r\cullMarker, 0
	Return r
End Function

Function LevelLight(r.Rooms, x#, y#, z#, range#, red% = 180, green% = 180, blue% = 180, kind% = 2)
	AddLight r, x#, y#, z#, kind%, range#, red%, green%, blue%
End Function

Function LevelItem%(name$, x#, y#, z#, yaw# = 0.0)
	Local it.Items = CreateItem(name$, x#, y#, z#)
	If it = Null Then Return False
	RotateEntity it\collider, 0, yaw#, 0
	Return True
End Function

Function LevelSpawn(r.Rooms, x#, y#, z#, yaw#)
	LevelSpawnSet = True
	LevelSpawnX# = x# : LevelSpawnY# = y# : LevelSpawnZ# = z#
	LevelSpawnYaw# = yaw#
	MTStartRoom = r
End Function


Type LevelPortal
	Field a.Rooms
	Field b.Rooms
	Field marker%
End Type

Global VisToken%
Global PortalCount%
Dim VisQueue.Rooms(63)

Function LevelPortal(a.Rooms, b.Rooms, x#, y#, z#, yaw#, w# = 2.0, h# = 2.5)
	Local p.LevelPortal = New LevelPortal
	p\a = a
	p\b = b
	Local m% = CreateMesh()
	Local srf% = CreateSurface(m)
	Local hw# = w#*0.5, hh# = h#*0.5
	AddVertex srf%, -hw#, -hh#, 0, 0, 0
	AddVertex srf%,  hw#, -hh#, 0, 1, 0
	AddVertex srf%,  hw#,  hh#, 0, 1, 1
	AddVertex srf%, -hw#,  hh#, 0, 0, 1
	AddTriangle srf%, 0, 1, 2
	AddTriangle srf%, 0, 2, 3
	PositionEntity m, x#, y#, z#
	RotateEntity m, 0, yaw#, 0
	EntityAlpha m, 0.0
	EntityPickMode m, 0
	EntityType m, 0
	p\marker = m
	PortalCount = PortalCount + 1
End Function

Function LevelUpdateVisibility()
	VisToken = VisToken + 1
	If VisToken > 1000000 Then
		VisToken = 1
		For r.Rooms = Each Rooms
			r\visToken = 0
		Next
	EndIf
	If PlayerRoom = Null Then Return

	PlayerRoom\visToken = VisToken
	PlayerRoom\visDepth = 0
	VisQueue(0) = PlayerRoom
	Local head% = 0, tail% = 1
	While head < tail
		Local cur.Rooms = VisQueue(head)
		head = head + 1
		If cur\visDepth < 3 Then
			For p.LevelPortal = Each LevelPortal
				Local other.Rooms = Null
				If p\a = cur Then other = p\b
				If p\b = cur Then other = p\a
				If other <> Null Then
					If other\visToken <> VisToken Then
						If EntityInView(p\marker, Camera) Then
							other\visToken = VisToken
							other\visDepth = cur\visDepth + 1
							If tail% < 63 Then
								VisQueue(tail) = other
								tail = tail + 1
							EndIf
						EndIf
					EndIf
				EndIf
			Next
		EndIf
	Wend
End Function
