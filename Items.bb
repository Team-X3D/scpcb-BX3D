
Const MaxItemAmount% = 10
Global ItemAmount%
Dim Inventory.Items(MaxItemAmount + 1)
Global InvSelect%, SelectedItem.Items

Global ClosestItem.Items

Global LastItemID%

Type ItemTemplates
	Field displayname$
	Field name$
	Field group$
	
	Field sound%
	
	Field found%
	
	Field obj%, objpath$, parentobjpath$
	Field invimg%,invimg2%,invimgpath$
	Field imgpath$, img%
	
	Field isAnim%
	
	Field scale#
	;Field bumptex%
	Field tex%, texpath$
End Type 

Function CreateItemTemplate.ItemTemplates(name$, group$, displayname$, objpath$, invimgpath$, imgpath$, scale#, texturepath$ = "",invimgpath2$="",Anim%=0, texflags%=9)
	Local it.ItemTemplates = New ItemTemplates, n
	
	
	;if another item shares the same object, copy it
	For it2.itemtemplates = Each ItemTemplates
		If it2\objpath = objpath And it2\obj <> 0 Then it\obj = CopyEntity(it2\obj) : it\parentobjpath=it2\objpath : Exit
	Next
	
	If it\obj = 0 Then
		If Anim<>0 Then
			it\obj = LoadAnimMesh_Strict(objpath)
			it\isAnim=True
		Else
			it\obj = LoadMesh_Strict(objpath)
			it\isAnim=False
		EndIf
		it\objpath = objpath
	EndIf
	it\objpath = objpath
	
	Local texture%
	
	If texturepath <> "" Then
		For it2.itemtemplates = Each ItemTemplates
			If it2\texpath = texturepath And it2\tex<>0 Then
				texture = it2\tex
				Exit
			EndIf
		Next
		If texture=0 Then texture=LoadTexture_Strict(texturepath,texflags%) : it\texpath = texturepath; : DebugLog texturepath
		EntityTexture it\obj, texture
		it\tex = texture
	EndIf  
	
	it\scale = scale
	ScaleEntity it\obj, scale, scale, scale, True
	
	;if another item shares the same object, copy it
	For it2.itemtemplates = Each ItemTemplates
		If it2\invimgpath = invimgpath And it2\invimg <> 0 Then
			it\invimg = it2\invimg ;CopyImage()
			If it2\invimg2<>0 Then
				it\invimg2=it2\invimg2 ;CopyImage()
			EndIf
			Exit
		EndIf
	Next
	If it\invimg=0 Then
		it\invimg = LoadImageHUDScaled(invimgpath, 64)
		it\invimgpath = invimgpath
	EndIf
	
	If (invimgpath2 <> "") Then
		If it\invimg2=0 Then
			it\invimg2 = LoadImageHUDScaled(invimgpath2, 64)
		EndIf
	Else
		it\invimg2 = 0
	EndIf
	
	it\imgpath = imgpath
	
	;If imgpath<>"" Then
	;	it\img=LoadImage(imgpath)
	;	
	;	;DebugLog imgpath
	;	
	;	If it\img<>0 Then ScaleImage(it\img, UIScale, UIScale)
	;EndIf
	
	it\group = group
	it\name = name
	it\displayname = GetModdedINIString(StringsFile, "Item", name)
	If it\displayname = "" Then
		it\displayname = displayname
		If it\displayname = "" Then
			it\displayname = name
		EndIf
	EndIf
	
	it\sound = 1

	HideEntity it\obj
	
	Return it
	
End Function

Function InitItemTemplatesFromFile(file$)
	If FileType(file) <> 1 Then Return

	Local name$, group$, displayname$
	Local model$, textureoverride$, textureflags%, anim%
	Local invicon$, invicon2$
	Local document$
	Local sound%, scale#
	Local hasCustomColor%, r%, g%, b%

	Local f% = OpenFile(file)
	While Not Eof(f)
		Local l$ = Trim(ReadLine(f))
		If l <> "" And Instr(l, "#") <> 1 And Instr(l, ";") <> 1 Then
			Local splitterPos = Instr(l, "=")
			If splitterPos = 0 And Instr(l, "[") = 1 Then
				If name <> "" Then
					Local itt.ItemTemplates = CreateItemTemplate(name, group, displayname, model, invicon, document, scale, textureoverride, invicon2, anim, textureflags)
					If hasCustomColor Then
						EntityColor(itt\obj, r, g, b)
					EndIf
					itt\sound = sound
				EndIf

				name = Trim(Mid(l, 2, Len(l) - 2))
				If name = "" then
					RuntimeErrorExt("Empty item name.")
				EndIf
				group = ""
				displayname = ""
				model = ""
				textureoverride = ""
				textureflags = 9
				anim = 0
				invicon = ""
				invicon2 = ""
				document = ""
				sound = 1
				scale = 1.0
				hasCustomColor = False
			Else
				If splitterPos = 0 Then Continue
				Local key$ = Trim(Left(l, splitterPos - 1))
				Local value$ = Trim(Right(l, Len(l) - splitterPos))
				Select key
					Case "group"
						group = value
					Case "display name"
						displayname = value
					Case "model"
						model = value
					Case "invicon"
						invicon = value
					Case "document"
						document = value
					Case "scale"
						scale = Float(value)
					Case "texture override"
						textureoverride = value
					Case "invicon2"
						invicon2 = value
					Case "anim"
						anim = Int(value)
					Case "texture flags"
						textureflags = Int(value)
					Case "sound"
						sound = Int(value)
					Case "color"
						hasCustomColor = True
						r = Int(Piece(value,1,","))
						g = Int(Piece(value,2,","))
						b = Int(Piece(value,3,","))
					Default
						RuntimeErrorExt("Unknown key "+Chr(34)+key+Chr(34)+" in item "+Chr(34)+name+Chr(34)+".")
				End Select
			EndIf
		EndIf
	Wend
	CloseFile(f)
	
	If name <> "" Then
		itt.ItemTemplates = CreateItemTemplate(name, group, displayname, model, invicon, document, scale, textureoverride, invicon2, anim, textureflags)
		If hasCustomColor Then
			EntityColor(itt\obj, r, g, b)
		EndIf
		itt\sound = sound
	EndIf

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

	Local it.ItemTemplates,it2.ItemTemplates
	For it = Each ItemTemplates
		If (it\tex<>0) Then
			If (it\texpath<>"") Then
				For it2=Each ItemTemplates
					If (it2<>it) And (it2\tex=it\tex) Then
						it2\tex = 0
					EndIf
				Next
			EndIf
			FreeTexture it\tex : it\tex = 0
		EndIf
	Next
	
End Function 

Function FindItemTemplate.ItemTemplates(name$)
	name$ = Lower(name$)
	For itt.Itemtemplates = Each ItemTemplates
		If Lower(itt\name) = name Lor Lower(itt\displayname) = name Then
			Return itt
		End If
	Next
End Function

Type Inventories
	Field Items.Items[20]
	Field Size%
End Type

Type Items
	Field displayname$
	Field collider%,model%
	Field itemtemplate.ItemTemplates
	Field DropSpeed#
	
	Field r%,g%,b%,a#
	
	Field level
	
	Field SoundChn%
	
	Field dist#, disttimer#
	
	Field state#, state2#
	
	Field Picked%,Dropped%
	
	Field invimg%
	Field xspeed#,zspeed#
	Field Inventory.Inventories
	Field ID%

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
			i\displayname = it\displayname
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
	
	i\invimg = i\itemtemplate\invimg
	
	i\ID=LastItemID+1
	LastItemID=i\ID

	EntityType(i\collider, HIT_ITEM)
	
	CatchErrors("CreateItem")
	Return i
End Function

Function RemoveItem(i.Items, inGame%=True)
	CatchErrors("Uncaught (RemoveItem)")
	Local n
	FreeEntity(i\model) : FreeEntity(i\collider) : i\collider = 0
	
	If i\Picked And inGame Then
		For n% = 0 To MaxItemAmount - 1
			If Inventory(n) = i
				DebugLog "Removed "+i\itemtemplate\name+" from slot "+n
				Inventory(n) = Null
				ItemAmount = ItemAmount-1
				Exit
			EndIf
		Next
		
	EndIf
	If i\itemtemplate\img <> 0
		FreeImageHUDScaled i\itemtemplate\img
		i\itemtemplate\img = 0
	EndIf
	If i\Inventory <> Null Then Delete i\Inventory
	Delete i
	
	CatchErrors("RemoveItem")
End Function


Function UpdateItems()
	CatchErrors("Uncaught (UpdateItems)")
	Local i.Items, i2.Items
	Local xtemp#, ytemp#, ztemp#
	Local pick%

	Local HideDist# = HideDistance*0.5

	ClosestItem = Null
	For i.Items = Each Items
		i\Dropped = 0

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

	Local n% = -1
	For i% = 0 To MaxItemAmount - 1
		If Inventory(i) = Null Then
			n = i
			Exit
		EndIf
	Next

	If n = -1 Then
		Msg = I_Loc\MessageItem_Full
		MsgTimer = 70 * 5
		Return
	EndIf

	If item\itemtemplate\sound <> 66 Then PlaySound_Strict(PickSFX(item\itemtemplate\sound))
	item\Picked = True
	item\Dropped = -1
	item\itemtemplate\found = True
	ItemAmount = ItemAmount + 1
	Inventory(n) = item
	HideEntity(item\collider)
End Function



;~IDEal Editor Parameters:
;~F#B#1E
;~C#Blitz3D