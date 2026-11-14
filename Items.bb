
Const MaxItemAmount% = 8
Global ItemAmount%
Dim InvItems.Items(MaxItemAmount)
Global InvSelected%

Global ClosestItem.Items

Type ItemTemplates
	Field displayname$
	Field name$
	Field description$
	Field obj%
End Type

Function FindItemMesh%(entity%)
	If entity = 0 Then Return 0
	If CountSurfaces(entity) > 0 Then Return entity

	Local i%
	For i = 1 To CountChildren(entity)
		Local found% = FindItemMesh(GetChild(entity, i))
		If found <> 0 Then Return found
	Next

	Return 0
End Function

Function CreateItemTemplate.ItemTemplates(name$, displayname$, description$, objpath$)
	Local it.ItemTemplates = New ItemTemplates

	it\name = name
	it\displayname = Upper(displayname)
	If it\displayname = "" Then it\displayname = Upper(name)
	it\description = description
	it\obj = LoadMesh_Strict(objpath)

	Local mesh% = FindItemMesh(it\obj)
	If mesh <> 0 Then
		Local maxdim# = Max(MeshWidth(mesh), Max(MeshHeight(mesh), MeshDepth(mesh)))
		If maxdim > 0.0 Then ScaleEntity it\obj, 0.18/maxdim, 0.18/maxdim, 0.18/maxdim
	EndIf

	HideEntity it\obj

	Return it
End Function

Function InitItemTemplatesFromFile(file$)
	If FileType(file) <> 1 Then Return

	Local name$, displayname$, description$, mesh$
	Local itt.ItemTemplates

	Local f% = OpenFile(file)
	While Not Eof(f)
		Local l$ = Trim(ReadLine(f))
		If l <> "" And Instr(l, "#") <> 1 And Instr(l, ";") <> 1 Then
			Local splitterPos = Instr(l, "=")
			If splitterPos = 0 And Instr(l, "[") = 1 Then
				If name <> "" Then
					itt = CreateItemTemplate(name, displayname, description, mesh)
				EndIf

				name = Trim(Mid(l, 2, Len(l) - 2))
				If name = "" Then RuntimeErrorExt("Empty item name.")
				displayname = ""
				description = ""
				mesh = ""
			Else
				If splitterPos = 0 Then Continue
				Local key$ = Trim(Left(l, splitterPos - 1))
				Local value$ = Trim(Right(l, Len(l) - splitterPos))
				Select key
					Case "name"
						displayname = value
					Case "description"
						description = value
					Case "mesh"
						mesh = value
					Default
						RuntimeErrorExt("Unknown key "+Chr(34)+key+Chr(34)+" in item "+Chr(34)+name+Chr(34)+".")
				End Select
			EndIf
		EndIf
	Wend
	CloseFile(f)

	If name <> "" Then itt = CreateItemTemplate(name, displayname, description, mesh)
End Function

Const ITEMS_DATA_PATH$ = "Data\items.ini"

Function InitItemTemplates()
	Local hasOverride%
	For m.ActiveMods = Each ActiveMods
		Local modPath$ = m\Path + ITEMS_DATA_PATH
		InitItemTemplatesFromFile(modPath)
		If FileType(modPath + ".OVERRIDE") = 1 Then
			hasOverride = True
			Exit
		EndIf
	Next

	If Not hasOverride Then InitItemTemplatesFromFile(ITEMS_DATA_PATH)
End Function

Function FindItemTemplate.ItemTemplates(name$)
	name$ = Lower(name$)
	For itt.Itemtemplates = Each ItemTemplates
		If Lower(itt\name) = name Lor Lower(itt\displayname) = name Then
			Return itt
		End If
	Next
End Function

Type Items
	Field collider%,model%
	Field itemtemplate.ItemTemplates
	Field DropSpeed#

	Field dist#, disttimer#

	Field Picked%

	Field xspeed#,zspeed#

End Type

Function CreateItem.Items(name$, x#, y#, z#)
	CatchErrors("Uncaught (CreateItem)")

	Local i.Items = New Items
	Local it.ItemTemplates

	name = Lower(name)

	For it.ItemTemplates = Each ItemTemplates
		If Lower(it\name) = name Then
			i\itemtemplate = it
			i\collider = CreatePivot()
			EntityRadius i\collider, 0.01
			EntityPickMode i\collider, 1, False
			i\model = CopyEntity(it\obj,i\collider)
			ShowEntity i\collider
			ShowEntity i\model
			Exit
		EndIf
	Next

	If i\itemtemplate = Null Then RuntimeErrorExt("Item template not found ("+name+")")

	ResetEntity i\collider
	PositionEntity(i\collider, x, y, z, True)
	RotateEntity (i\collider, 0, Rand(360), 0)
	i\dist = EntityDistance(Collider, i\collider)
	i\DropSpeed = 0.0

	EntityType(i\collider, HIT_ITEM)

	CatchErrors("CreateItem")
	Return i
End Function

Function RemoveItem(i.Items)
	CatchErrors("Uncaught (RemoveItem)")
	FreeEntity(i\model) : FreeEntity(i\collider) : i\collider = 0

	If i\Picked Then InventoryRemoveItem(i)

	Delete i

	CatchErrors("RemoveItem")
End Function

Function InventorySlotOf%(item.Items)
	Local i%
	For i = 0 To ItemAmount-1
		If InvItems(i) = item Then Return i
	Next
	Return -1
End Function

Function InventoryRemoveItem(item.Items)
	Local n% = InventorySlotOf(item)
	If n = -1 Then Return

	Local i%
	For i = n To ItemAmount-2
		InvItems(i) = InvItems(i+1)
	Next
	ItemAmount = ItemAmount - 1
	InvItems(ItemAmount) = Null
	If n < InvSelected Then InvSelected = InvSelected-1
	If InvSelected >= ItemAmount Then InvSelected = Max(ItemAmount-1, 0)
End Function

Function UpdateItems()
	CatchErrors("Uncaught (UpdateItems)")
	Local i.Items, i2.Items
	Local xtemp#, ytemp#, ztemp#
	Local pick%

	Local HideDist# = HideDistance*0.5

	ClosestItem = Null
	For i.Items = Each Items
		If (Not i\Picked) Then
			If i\disttimer < MilliSecs() Then
				i\dist = EntityDistance(Camera, i\collider)
				i\disttimer = MilliSecs() + 700
			EndIf

			If i\dist < HideDist Then
				Local hasCollided% = EntityCollided(i\collider, HIT_MAP)
				ShowEntity i\collider

				If i\dist < 1.2 Then
					If ClosestItem = Null Then
						If EntityInView(i\model, Camera) And EntityVisible(i\collider,Camera) Then ClosestItem = i
					ElseIf i\dist < EntityDistance(Camera, ClosestItem\collider) Then
						If EntityInView(i\model, Camera) And EntityVisible(i\collider,Camera) Then ClosestItem = i
					EndIf
				EndIf

				If hasCollided Then
					i\DropSpeed = 0
					i\xspeed = 0.0
					i\zspeed = 0.0
				Else
					If ShouldEntitiesFall Then
						pick = LinePick(EntityX(i\collider, True),EntityY(i\collider, True),EntityZ(i\collider, True),0,-10,0)
						If pick Then
							i\DropSpeed = i\DropSpeed - 0.0004 * FPSfactor
							TranslateEntity i\collider, i\xspeed*FPSfactor, i\DropSpeed * FPSfactor, i\zspeed*FPSfactor, True
						Else
							i\DropSpeed = 0
							i\xspeed = 0.0
							i\zspeed = 0.0
						EndIf
					Else
						i\DropSpeed = 0
						i\xspeed = 0.0
						i\zspeed = 0.0
					EndIf
				EndIf

				If i\dist < HideDist*0.2 Then
					For i2.Items = Each Items
						If i<>i2 And (Not i2\Picked) And i2\dist < HideDist*0.2 Then
							xtemp# = (EntityX(i2\collider,True)-EntityX(i\collider,True))
							ytemp# = (EntityY(i2\collider,True)-EntityY(i\collider,True))
							ztemp# = (EntityZ(i2\collider,True)-EntityZ(i\collider,True))
							Local ed# = (xtemp*xtemp+ztemp*ztemp)
							If ed < 0.07 And Abs(ytemp) < 0.25 Then
								xtemp = xtemp*(0.07-ed)
								ztemp = ztemp*(0.07-ed)
								While Abs(xtemp)+Abs(ztemp) < 0.001
									xtemp = xtemp+Rnd(-0.002,0.002)
									ztemp = ztemp+Rnd(-0.002,0.002)
								Wend
								TranslateEntity i2\collider,xtemp,0,ztemp,True
								TranslateEntity i\collider,-xtemp,0,-ztemp,True
							EndIf
						EndIf
					Next
				EndIf

				If EntityY(i\collider, True) < -35.0 Then RemoveItem(i) : Continue
			Else
				HideEntity i\collider
			EndIf
		Else
			i\DropSpeed = 0
			i\xspeed = 0.0
			i\zspeed = 0.0
		EndIf
	Next

	If ClosestItem <> Null And MouseHit1 Then PickItem(ClosestItem)
End Function

Function PickItem(item.Items)
	If item = Null Then Return

	If ItemAmount >= MaxItemAmount Then
		Msg = I_Loc\MessageItem_Full
		MsgTimer = 70 * 5
		Return
	EndIf

	PlaySound_Strict(PickSFX(1))
	item\Picked = True
	InvItems(ItemAmount) = item
	ItemAmount = ItemAmount + 1
	If ItemAmount = 1 Then InvSelected = 0
	HideEntity(item\collider)
	ViewModelGrabRequest = True
End Function

Function DropItem(item.Items)
	If item = Null Then Return

	PlaySound_Strict(PickSFX(1))

	item\Picked = False
	item\DropSpeed = 0.0
	item\xspeed = 0.0
	item\zspeed = 0.0

	EntityParent(item\collider, 0)
	ShowEntity(item\collider)
	PositionEntity(item\collider, EntityX(Camera), EntityY(Camera), EntityZ(Camera), True)
	RotateEntity(item\collider, EntityPitch(Camera), EntityYaw(Camera)+Rnd(-20,20), 0, True)
	TranslateEntity(item\collider, 0, -0.1, 0.1)
	RotateEntity(item\collider, 0, EntityYaw(Camera)+Rnd(-110,110), 0, True)
	ResetEntity(item\collider)

	InventoryRemoveItem(item)
End Function
