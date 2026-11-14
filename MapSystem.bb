Type Materials
	Field name$
	Field Diff
	Field Bump
	
	Field StepSound%
End Type

Const MATERIALS_DATA_PATH$ = "Data\materials.ini"
Const spacing# = 2.0 ; MT tunnel meshes are 512 mesh units and not the usual 2048
Const MT_GRID_SIZE% = 19

Function InitMaterials()
	Local hasOverride%
	For m.ActiveMods = Each ActiveMods
		Local modMatPath$ = m\Path + MATERIALS_DATA_PATH
		If FileType(modMatPath) = 1 Then
			LoadMaterials(modMatPath)
			If FileType(modMatPath + ".OVERRIDE") = 1 Then
				hasOverride = True
				Exit
			EndIf
		EndIf
	Next
	If Not hasOverride Then LoadMaterials(MATERIALS_DATA_PATH)
End Function

Function LoadMaterials(file$)
	CatchErrors("Uncaught (LoadMaterials)")
	;If Not BumpEnabled Then Return
	
	Local TemporaryString$
	Local mat.Materials = Null
	Local StrTemp$ = ""
	
	Local f = OpenFile(file)
	
	While Not Eof(f)
		TemporaryString = Trim(ReadLine(f))
		If Left(TemporaryString,1) = "[" Then
			TemporaryString = Mid(TemporaryString, 2, Len(TemporaryString) - 2)
			
			mat.Materials = New Materials
			
			mat\name = Lower(TemporaryString)
			
			If BumpEnabled Then
				StrTemp = GetINIString(file, TemporaryString, "bump")
				If StrTemp <> "" Then 
					mat\Bump =  LoadTexture_Strict(StrTemp)
					
					TextureBlend mat\Bump, 6
					TextureBumpEnvMat mat\Bump,0,0,-0.012
					TextureBumpEnvMat mat\Bump,0,1,-0.012
					TextureBumpEnvMat mat\Bump,1,0,0.012
					TextureBumpEnvMat mat\Bump,1,1,0.012
					TextureBumpEnvOffset mat\Bump,0.5
					TextureBumpEnvScale mat\Bump,1.0				
				EndIf
			EndIf
			
			mat\StepSound = (GetINIInt(file, TemporaryString, "stepsound")+1)
		EndIf
	Wend
	
	CloseFile f
	
	CatchErrors("LoadMaterials")
End Function

;RMESH STUFF;;;;

Function StripFilename$(file$)
	Local mi$=""
	Local lastSlash%=0
	If Len(file)>0
		For i%=1 To Len(file)
			mi=Mid(file$,i,1)
			If mi="\" Or mi="/" Then
				lastSlash=i
			EndIf
		Next
	EndIf
	
	Return Left(file,lastSlash)
End Function

Function GetTextureFromCache%(name$)
	For tc.Materials=Each Materials
		If tc\name = name Then Return tc\Diff
	Next
	Return 0
End Function

Function GetBumpFromCache%(name$)
	For tc.Materials=Each Materials
		If tc\name = name Then Return tc\Bump
	Next
	Return 0
End Function

Function GetCache.Materials(name$)
	For tc.Materials=Each Materials
		If tc\name = name Then Return tc
	Next
	Return Null
End Function

Function AddTextureToCache(texture%, nameOverride$="")
	Local tc.Materials=GetCache(StripPath(TextureName(texture)))
	If tc.Materials=Null Then
		tc.Materials=New Materials
		If nameOverride<>"" Then
			tc\name=StripPath(nameOverride)
		Else
			tc\name=StripPath(TextureName(texture))
		EndIf
		If BumpEnabled Then
			Local temp$=""
			Local hasOverride%
			For m.ActiveMods = Each ActiveMods
				Local modMatPath$ = m\Path + MATERIALS_DATA_PATH
				If FileType(modMatPath) = 1 Then
					temp = GetINIString(modMatPath,tc\name,"bump")
					If temp <> "" Lor FileType(modMatPath + ".OVERRIDE") = 1 Then
						hasOverride = True
						Exit
					EndIf
				EndIf
			Next
			If (Not hasOverride) And temp="" Then temp=GetINIString(MATERIALS_DATA_PATH,tc\name,"bump")
			If temp<>"" Then
				tc\Bump=LoadTexture_Strict(temp)
				TextureBlend tc\Bump,6
				TextureBumpEnvMat tc\Bump,0,0,-0.012
				TextureBumpEnvMat tc\Bump,0,1,-0.012
				TextureBumpEnvMat tc\Bump,1,0,0.012
				TextureBumpEnvMat tc\Bump,1,1,0.012
				TextureBumpEnvOffset tc\Bump,0.5
				TextureBumpEnvScale tc\Bump,1.0
			Else
				tc\Bump=0
			EndIf
		EndIf
		tc\Diff=0
	EndIf
	If tc\Diff=0 Then tc\Diff=texture
End Function

Function ClearTextureCache()
	For tc.Materials=Each Materials
		If tc\Diff<>0 Then FreeTexture tc\Diff
		If tc\Bump<>0 Then FreeTexture tc\Bump
		Delete tc
	Next
End Function

Function FreeTextureCache()
	For tc.Materials=Each Materials
		If tc\Diff<>0 Then FreeTexture tc\Diff
		If tc\Bump<>0 Then FreeTexture tc\Bump
		tc\Diff = 0 : tc\Bump = 0
	Next
End Function

Const RMESH_MAX_VERSION% = 2

Function LoadRMesh(file$,rt.RoomTemplates)
	Local path$ = StripFilename(file)
	file = DetermineModdedPath(file)

	CatchErrors("Uncaught (LoadRMesh " + Chr(34) + file + Chr(34) + ")")
	;generate a texture made of white
	Local blankTexture%
	blankTexture=CreateTexture(4,4,1,1)
	ClsColor 255,255,255
	SetBuffer TextureBuffer(blankTexture)
	Cls
	SetBuffer BackBuffer()
	
	ClsColor 0,0,0
	
	;read the file
	Local f%=ReadFile(file)
	Local i%,j%,k%,x#,y#,z#
	Local vertex%
	Local temp1i%,temp2i%,temp3i%
	Local temp1#,temp2#,temp3#
	Local temp1s$, temp2s$
	
	Local collisionMeshes% = CreatePivot()
	
	Local hasTriggerBox% = False
	
	For i=0 To 3 ;reattempt up to 3 times
		If f=0 Then
			f=ReadFile(file)
		Else
			Exit
		EndIf
	Next
	If f=0 Then RuntimeErrorExt "Error reading file "+Chr(34)+file+Chr(34)
	Local version% = -1
	Local isRMesh$ = ReadString(f)
	Select isRMesh
		Case "RoomMesh" ;Continue
		Case "RoomMesh.HasTriggerBox" hasTriggerBox% = True
		Case "RM" version = ReadByte(f) : hasTriggerBox = True
		Default RuntimeErrorExt Chr(34)+file+Chr(34)+" is Not RMESH ("+isRMesh+")"
	End Select

	If version > RMESH_MAX_VERSION Then RuntimeErrorExt "RMESH version of " + Chr(34)+file+Chr(34) + " (" + version + ") + is too new for this version of the game ("+RMESH_MAX_VERSION+")"
	
	Local count%,count2%
	
	Local obj%=CreatePivot()

	;drawn meshes
	Local Opaque%=CreateMesh(),Alpha%=CreateMesh()
	
	count = ReadInt(f)
	Local childMesh%
	Local surf%,tex%[2],brush%
	
	Local isAlpha%
	
	Local u#,v#
	
	For i=1 To count ;drawn mesh
		EntityParent ReadMesh(path, f, blankTexture, Alpha, Opaque), collisionMeshes
	Next
	
	If version >= 0 Then
		Local noCollCount% = ReadInt(f)
		For i=1 To noCollCount
			ReadMesh(path, f, blankTexture, Alpha, Opaque, False)
		Next
	EndIf

	Local hiddenMesh%
	hiddenMesh=CreateMesh()
	
	count=ReadInt(f) ;invisible collision mesh
	For i%=1 To count
		surf=CreateSurface(hiddenMesh)
		count2=ReadInt(f) ;vertices
		For j%=1 To count2
			;world coords
			x=ReadFloat(f) : y=ReadFloat(f) : z=ReadFloat(f)
			vertex=AddVertex(surf,x,y,z)
		Next
		
		count2=ReadInt(f) ;polys
		For j%=1 To count2
			temp1i = ReadInt(f) : temp2i = ReadInt(f) : temp3i = ReadInt(f)
			AddTriangle(surf,temp1i,temp2i,temp3i)
			AddTriangle(surf,temp1i,temp3i,temp2i)
		Next
	Next
	
	;trigger boxes
	If hasTriggerBox
		Local lastTriggerbox.TempTriggerboxes

		Local triggerboxCount% = ReadInt(f)
		DebugLog "TriggerBoxEnable"
		For k = 1 To triggerboxCount
			Local tb.TempTriggerboxes = New TempTriggerboxes
			
			tb\Obj = CreateMesh(obj)
			EntityAlpha(tb\Obj, 0.2)
			EntityColor(tb\Obj, 255,255,0)
			HideEntity(tb\Obj)

			count = ReadInt(f)
			For i%=1 To count
				surf=CreateSurface(tb\Obj)
				count2=ReadInt(f)
				For j%=1 To count2
					x=ReadFloat(f) : y=ReadFloat(f) : z=ReadFloat(f)
					vertex=AddVertex(surf,x,y,z)
				Next
				count2=ReadInt(f)
				For j%=1 To count2
					temp1i = ReadInt(f) : temp2i = ReadInt(f) : temp3i = ReadInt(f)
					AddTriangle(surf,temp1i,temp2i,temp3i)
					AddTriangle(surf,temp1i,temp3i,temp2i)
				Next
			Next
			tb\Name = ReadString(f)

			If lastTriggerbox = Null Then
				rt\FirstTempTriggerbox = tb
			Else
				lastTriggerbox\Successor = tb
			EndIf
			lastTriggerbox = tb
		Next
	EndIf
	
	Local lastItem.TempItems
	Local lastDoor.TempDoors
	Local range#, lcolor$, intensity#
	Local r%, g%, b%
	Local angles$, pitch#, yaw#, roll#

	count=ReadInt(f) ;point entities
	For i%=1 To count
		temp1s=ReadString(f)
		Select temp1s
			Case "screen"
				
				temp1=ReadFloat(f)*RoomScale
				temp2=ReadFloat(f)*RoomScale
				temp3=ReadFloat(f)*RoomScale
				
				temp2s$ =ReadString(f)
				
				If temp1<>0 Or temp2<>0 Or temp3<>0 Then 
					Local ts.TempScreens = New TempScreens	
					ts\x = temp1
					ts\y = temp2
					ts\z = temp3
					ts\imgpath = temp2s
					ts\roomtemplate = rt
				EndIf
				
			Case "waypoint"
				
				temp1=ReadFloat(f)*RoomScale
				temp2=ReadFloat(f)*RoomScale
				temp3=ReadFloat(f)*RoomScale
				
				Local w.TempWayPoints = New TempWayPoints
				w\roomtemplate = rt
				w\x = temp1
				w\y = temp2
				w\z = temp3
				
			Case "light"

				; TODO
				Local hasSprite% = True
				If version >= 2 Then hasSprite = ReadByte(f)
				
				temp1=ReadFloat(f)*RoomScale
				temp2=ReadFloat(f)*RoomScale
				temp3=ReadFloat(f)*RoomScale
				
				If temp1<>0 Or temp2<>0 Or temp3<>0 Then 
					range# = ReadFloat(f)/2000.0
					lcolor$=ReadString(f)
					intensity# = Min(ReadFloat(f)*0.8,1.0)
					r%=Int(Piece(lcolor,1," "))*intensity
					g%=Int(Piece(lcolor,2," "))*intensity
					b%=Int(Piece(lcolor,3," "))*intensity
					
					AddTempLight(rt, temp1,temp2,temp3, 2, range, r,g,b)
				Else
					ReadFloat(f) : ReadString(f) : ReadFloat(f)
				EndIf
				
			Case "spotlight"
				
				; TODO
				hasSprite% = True
				If version >= 2 Then hasSprite = ReadByte(f)

				temp1=ReadFloat(f)*RoomScale
				temp2=ReadFloat(f)*RoomScale
				temp3=ReadFloat(f)*RoomScale
				
				If temp1<>0 Or temp2<>0 Or temp3<>0 Then 
					range# = ReadFloat(f)/2000.0
					lcolor$=ReadString(f)
					intensity# = Min(ReadFloat(f)*0.8,1.0)
					r%=Int(Piece(lcolor,1," "))*intensity
					g%=Int(Piece(lcolor,2," "))*intensity
					b%=Int(Piece(lcolor,3," "))*intensity
					
					Local lt.LightTemplates = AddTempLight(rt, temp1,temp2,temp3, 2, range, r,g,b)
					angles$=ReadString(f)
					pitch#=Piece(angles,1," ")
					yaw#=Piece(angles,2," ")
					lt\pitch = pitch
					lt\yaw = yaw
					
					lt\innerconeangle = ReadInt(f)
					lt\outerconeangle = ReadInt(f)
				Else
					ReadFloat(f) : ReadString(f) : ReadFloat(f) : ReadString(f) : ReadInt(f) : ReadInt(f)
				EndIf
				
			Case "soundemitter"
				
				temp1i=0
				
				For j = 0 To MaxRoomEmitters-1
					If rt\TempSoundEmitter[j]=0 Then
						rt\TempSoundEmitterX[j]=ReadFloat(f)*RoomScale
						rt\TempSoundEmitterY[j]=ReadFloat(f)*RoomScale
						rt\TempSoundEmitterZ[j]=ReadFloat(f)*RoomScale
						rt\TempSoundEmitter[j]=ReadInt(f)
						
						rt\TempSoundEmitterRange[j]=ReadFloat(f)
						temp1i=1
						Exit
					EndIf
				Next
				
				If temp1i=0 Then
					ReadFloat(f)
					ReadFloat(f)
					ReadFloat(f)
					ReadInt(f)
					ReadFloat(f)
				EndIf
				
			Case "playerstart"
				
				temp1=ReadFloat(f) : temp2=ReadFloat(f) : temp3=ReadFloat(f)
				
				angles$=ReadString(f)
				pitch#=Piece(angles,1," ")
				yaw#=Piece(angles,2," ")
				roll#=Piece(angles,3," ")
				If Camera Then
					PositionEntity Camera,temp1,temp2,temp3
					RotateEntity Camera,pitch,yaw,roll
				EndIf
			Case "model"
				file = ReadString(f)
				If file<>""
					Local model = CreatePropObj(file)
					
					temp1=ReadFloat(f) : temp2=ReadFloat(f) : temp3=ReadFloat(f)
					PositionEntity model,temp1,temp2,temp3
					
					temp1=ReadFloat(f) : temp2=ReadFloat(f) : temp3=ReadFloat(f)
					RotateEntity model,temp1,temp2,temp3
					
					temp1=ReadFloat(f) : temp2=ReadFloat(f) : temp3=ReadFloat(f)
					ScaleEntity model,temp1,temp2,temp3
					
					Local collType% = HIT_MAP
					If version >= 2 Then
						collType = ReadByte(f)
						EntityFX(model, ReadInt(f))
						Local texOverride$ = ReadString(f)
						If texOverride <> "" Then
							Local t% = GetTextureFromCache(texOverride)
							If t = 0 Then
								t = LoadTexture(file+texOverride)
								If t = 0 Then t = LoadTexture("GFX\map\"+texOverride)
								If t = 0 Then t = LoadTexture("GFX\map\Props\"+texOverride)
								If t <> 0 Then AddTextureToCache(t)
							EndIf
							If t <> 0 Then EntityTexture model, t
						EndIf
					EndIf

					EntityParent model,Opaque
					EntityType model,collType
					EntityPickMode model,2
				Else
					DebugLog "file = 0"
					temp1=ReadFloat(f) : temp2=ReadFloat(f) : temp3=ReadFloat(f)
					If version >= 2 Then ReadByte(f) : ReadInt(f) : ReadString(f)
					DebugLog temp1+", "+temp2+", "+temp3
					
					;Stop
				EndIf
			Case "item"
				Local it.TempItems = New TempItems
				
				it\X = ReadFloat(f) * RoomScale
				it\Y = ReadFloat(f) * RoomScale
				it\Z = ReadFloat(f) * RoomScale

				it\Name = ReadString(f)
				If version < 0 Then
					Local tempName$ = ReadString(f)
					Local itt.ItemTemplates = FindItemTemplate(it\Name)
					If itt = Null Then itt = FindItemTemplate(tempName)
					If itt = Null Then
						RuntimeErrorExt("Item template for "+Chr(34)+it\Name+Chr(34)+" not found.")
					Else
						it\Name = itt\name
					EndIf
				EndIf

				it\HasCustomAngle = ReadByte(f)
				it\AngleX = ReadFloat(f)
				it\AngleY = ReadFloat(f)
				it\AngleZ = ReadFloat(f)

				it\State = ReadFloat(f)
				it\State2 = ReadFloat(f)

				it\Chance = ReadFloat(f)

				If lastItem = Null Then
					rt\FirstTempItem = it
				Else
					lastItem\Successor = it
				EndIf
				lastItem = it

			Case "door"
				Local d.TempDoors = New TempDoors

				d\X = ReadFloat(f) * RoomScale
				d\Y = ReadFloat(f) * RoomScale
				d\Z = ReadFloat(f) * RoomScale

				d\Dir = ReadInt(f)
				If version => 1 Then
					d\Index = ReadInt(f)
				Else
					d\Index = -1
				EndIf
				ReadInt(f)
				d\Code = ReadString(f)
				d\Angle = ReadFloat(f)
				d\SpawnOpen = ReadByte(f)
				If version >= 0 Then
					d\Locked = ReadByte(f)
					d\DeleteHalf = ReadByte(f)
				EndIf
				d\AllowRemoteControl = ReadByte(f)
				If version >= 0 Then
					d\Button1PosX = ReadFloat(f) * RoomScale
					d\Button1PosY = ReadFloat(f) * RoomScale
					d\Button1PosZ = ReadFloat(f) * RoomScale
					d\Button1RotX = ReadFloat(f)
					d\Button1RotY = ReadFloat(f)
					d\Button1RotZ = ReadFloat(f)
					d\Button2PosX = ReadFloat(f) * RoomScale
					d\Button2PosY = ReadFloat(f) * RoomScale
					d\Button2PosZ = ReadFloat(f) * RoomScale
					d\Button2RotX = ReadFloat(f)
					d\Button2RotY = ReadFloat(f)
					d\Button2RotZ = ReadFloat(f)
				EndIf

				If lastDoor = Null Then
					rt\FirstTempDoor = d
				Else
					lastDoor\Successor = d
				EndIf
				lastDoor = d
		End Select
	Next
		
	temp1i=CopyMesh(Alpha)
	FlipMesh temp1i
	AddMesh temp1i,Alpha
	FreeEntity temp1i
	
	If brush <> 0 Then FreeBrush brush
	
	AddMesh Alpha,Opaque
	FreeEntity Alpha
	
	EntityFX Opaque,11
	
	EntityAlpha hiddenMesh,0.0
	EntityAlpha Opaque,1.0
	
	;EntityType Opaque,HIT_MAP
	EntityType hiddenMesh,HIT_MAP
	FreeTexture blankTexture
	
	;AddMesh hiddenMesh,BigRoomMesh
	
	obj=CreatePivot()
	CreatePivot(obj) ;skip "meshes" object
	EntityParent Opaque,obj
	EntityParent hiddenMesh,obj
	CreatePivot(obj) ;skip "pointentites" object
	CreatePivot(obj) ;skip "solidentites" object
	EntityParent collisionMeshes,obj
	
	CloseFile f
	
	CatchErrors("LoadRMesh")
	Return obj%
	
End Function

Function ReadMesh%(file$, f%, blankTexture%, Alpha%, Opaque%, coll% = True)
	Local temp1i, temp2i, temp3i
	
	Local childMesh=CreateMesh()
	
	Local surf=CreateSurface(childMesh)
	
	Local brush=CreateBrush()
	
	Local tex%[2]
	tex[0]=0 : tex[1]=0

	Local isAlpha=0
	For j=0 To 1
		temp1i=ReadByte(f)
		If temp1i<>0 Then
			Local temp1s$=ReadString(f)
			tex[j]=GetTextureFromCache(temp1s)
			If tex[j]=0 Then ;texture is not in cache
				Local flags% = 1
				If temp1i=>3 Then flags = 3
				tex[j]=LoadTexture(file+temp1s,flags)
				If tex[j] = 0 Then tex[j] = LoadTexture("GFX\map\" + temp1s,flags)
				
				If tex[j]<>0 Then
					If temp1i=1 Then TextureBlend tex[j],5
					If Instr(Lower(temp1s),"_lm")<>0 Then
						TextureBlend tex[j],3
					EndIf
					AddTextureToCache(tex[j])
				EndIf
				
			EndIf
			If tex[j]<>0 Then
				isAlpha=2
				If temp1i=3 Then isAlpha=1
				
				TextureCoords tex[j],1-j
			EndIf
		EndIf
	Next
	
	If isAlpha=1 Then
		If tex[1]<>0 Then
			TextureBlend tex[1],2
			BrushTexture brush,tex[1],0,0
		Else
			BrushTexture brush,blankTexture,0,0
		EndIf
	Else
		If tex[0]<>0 And tex[1]<>0 Then
			Local bumptex% = GetBumpFromCache(StripPath(TextureName(tex[1])))
			;If bumptex<>0 Then
			;	DebugLog StripPath(TextureName(bumptex))
			;	Stop
			;EndIf
			For j=0 To 1
				BrushTexture brush,tex[j],0,j+1+(bumptex<>0)
			Next
			
			BrushTexture brush,AmbientLightRoomTex,0
			If (bumptex<>0) Then
				BrushTexture brush,bumptex,0,1
			EndIf
		Else
			For j=0 To 1
				If tex[j]<>0 Then
					BrushTexture brush,tex[j],0,j
				Else
					BrushTexture brush,blankTexture,0,j
				EndIf
			Next
		EndIf
	EndIf
	
	surf=CreateSurface(childMesh)
	
	If isAlpha>0 Then PaintSurface surf,brush
	
	FreeBrush brush : brush = 0
	
	Local count2=ReadInt(f) ;vertices
	
	For j%=1 To count2
		;world coords
		Local x#=ReadFloat(f), y#=ReadFloat(f), z#=ReadFloat(f)
		Local vertex=AddVertex(surf,x#,y#,z#)
		
		;texture coords
		For k%=0 To 1
			Local u#=ReadFloat(f), v#=ReadFloat(f)
			VertexTexCoords surf,vertex,u,v,0.0,k
		Next
		
		;colors
		temp1i=ReadByte(f)
		temp2i=ReadByte(f)
		temp3i=ReadByte(f)
		VertexColor surf,vertex,temp1i,temp2i,temp3i,1.0
	Next
	
	count2=ReadInt(f) ;polys
	For j%=1 To count2
		temp1i = ReadInt(f) : temp2i = ReadInt(f) : temp3i = ReadInt(f)
		AddTriangle(surf,temp1i,temp2i,temp3i)
	Next
	
	If isAlpha=1 Then
		AddMesh childMesh,Alpha
		EntityAlpha childMesh,0.0
	Else
		AddMesh childMesh,Opaque
		EntityAlpha childMesh,0.0
		If coll Then
			EntityType childMesh,HIT_MAP
			EntityPickMode childMesh,2
			
			;make collision double-sided
			Local flipChild% = CopyMesh(childMesh)
			FlipMesh(flipChild)
			AddMesh flipChild,childMesh
			FreeEntity flipChild
		EndIf
	EndIf

	Return childMesh
End Function

Function ResetAllRMeshes()
	Delete Each TempWayPoints
	Delete Each TempScreens
	Delete Each TempTriggerboxes
	Delete Each TempItems
	Delete Each TempDoors
	For rt.RoomTemplates = Each RoomTemplates
		rt\FirstTempTriggerbox = Null
		rt\FirstTempItem = Null
		rt\FirstTempDoor = Null
		If rt\obj <> 0 Then FreeEntity(rt\obj) : rt\obj = 0
	Next
End Function

;-----------;;;;

Function StripPath$(file$) 
	Local name$=""
	If Len(file$)>0 
		For i=Len(file$) To 1 Step -1 
			
			Local mi$=Mid$(file$,i,1) 
			If mi$="\" Or mi$="/" Then Return name$
			
			name$=mi$+name$ 
		Next 
		
	EndIf 
	
	Return name$ 
End Function

Function Piece$(s$,entry,char$=" ")
	Local p
	While Instr(s,char+char)
		s=Replace(s,char+char,char)
	Wend
	For n=1 To entry-1
		p=Instr(s,char)
		If p=0 Then Return ""
		s=Right(s,Len(s)-p)
	Next
	p=Instr(s,char)
	Local a$
	If p<1
		a=s
	Else
		a=Left(s,p-1)
	EndIf
	Return a
End Function


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;Forest gen consts
Const gridsize% = 10
Const deviation_chance% = 40 ;out of 100
Const branch_chance% = 65
Const branch_max_life% = 4
Const branch_die_chance% = 18
Const max_deviation_distance% = 3
Const return_chance% = 27
Const center = 5 ;(gridsize-1) / 2
Const min_door_pos% = 3, max_door_pos% = 7

Type Forest
	Field TileMesh%[ROOM4] ; 0 is unused
	Field DetailMesh%[4]
	Field grid%[(gridsize*gridsize)+11]
	Field TileEntities%[(gridsize*gridsize)+1]
	Field Forest_Pivot%
	
	Field Door%[2]
	Field DetailEntities%[2]
End Type

Function move_forward%(dir%,pathx%,pathy%,retval%=0)
	;move 1 unit along the grid in the designated direction
	If dir = 1 Then
		If retval=0 Then
			Return pathx
		Else
			Return pathy+1
		EndIf
	EndIf
	If retval=0 Then
		Return pathx-1+dir
	Else
		Return pathy
	EndIf
End Function

Function chance%(chanc%)
	;perform a chance given a probability
	Return (Rand(0,100)<=chanc)
End Function

Function turn_if_deviating%(max_deviation_distance_%,pathx%,center_%,dir%,retval%=0)
	;check if deviating and return the answer. if deviating, turn around
	Local current_deviation% = center_ - pathx
	Local deviated% = False
	If (dir = 0 And current_deviation >= max_deviation_distance_) Or (dir = 2 And current_deviation <= -max_deviation_distance_) Then
		dir = (dir + 2) Mod 4
		deviated = True
	EndIf
	If retval=0 Then Return dir Else Return deviated
End Function

Function GenForestGrid(fr.Forest)
	CatchErrors("Uncaught (GenForestGrid)")
	
	Local door1_pos%,door2_pos%
	Local i%,j%
	door1_pos=Rand(min_door_pos,max_door_pos)
	door2_pos=Rand(min_door_pos,max_door_pos)
	
	;clear the grid
	For i=0 To gridsize-1
		For j=0 To gridsize-1
			fr\grid[(j*gridsize)+i]=0
		Next
	Next
	
	;set the position of the concrete and doors
	;For i=0 To gridsize-1
	;	fr\grid[i]=2
	;	fr\grid[((gridsize-1)*gridsize)+i]=2
	;Next
	fr\grid[door1_pos]=3
	fr\grid[((gridsize-1)*gridsize)+door2_pos]=3
	
	;generate the path
	Local pathx = door2_pos
	Local pathy = 1
	Local dir = 1 ;0 = left, 1 = up, 2 = right
	fr\grid[((gridsize-1-pathy)*gridsize)+pathx] = 1
	
	Local deviated%
	
	While pathy < gridsize -4
		If dir = 1 Then ;determine whether to go forward or to the side
			If chance(deviation_chance) Then
				;pick a branch direction
				dir = 2 * Rand(0,1)
				;make sure you have not passed max side distance
				dir = turn_if_deviating(max_deviation_distance,pathx,center,dir)
				deviated = turn_if_deviating(max_deviation_distance,pathx,center,dir,1)
				If deviated Then fr\grid[((gridsize-1-pathy)*gridsize)+pathx]=1
				pathx=move_forward(dir,pathx,pathy)
				pathy=move_forward(dir,pathx,pathy,1)
			EndIf
			
		Else
			;we are going to the side, so determine whether to keep going or go forward again
			dir = turn_if_deviating(max_deviation_distance,pathx,center,dir)
			deviated = turn_if_deviating(max_deviation_distance,pathx,center,dir,1)
			If deviated Or chance(return_chance) Then dir = 1
			
			pathx=move_forward(dir,pathx,pathy)
			pathy=move_forward(dir,pathx,pathy,1)
			;if we just started going forward go twice so as to avoid creating a potential 2x2 line
			If dir=1 Then
				fr\grid[((gridsize-1-pathy)*gridsize)+pathx]=1
				pathx=move_forward(dir,pathx,pathy)
				pathy=move_forward(dir,pathx,pathy,1)
			EndIf
		EndIf
		
		;add our position to the grid
		fr\grid[((gridsize-1-pathy)*gridsize)+pathx]=1
		
	Wend
	;finally, bring the path back to the door now that we have reached the end
	dir = 1
	While pathy < gridsize-2
		pathx=move_forward(dir,pathx,pathy)
		pathy=move_forward(dir,pathx,pathy,1)
		fr\grid[((gridsize-1-pathy)*gridsize)+pathx]=1
	Wend
	
	If pathx<>door1_pos Then
		dir=0
		If door1_pos>pathx Then dir=2
		While pathx<>door1_pos
			pathx=move_forward(dir,pathx,pathy)
			pathy=move_forward(dir,pathx,pathy,1)
			fr\grid[((gridsize-1-pathy)*gridsize)+pathx]=1
		Wend
	EndIf
	
	;attempt to create new branches
	Local new_y%,temp_y%,new_x%
	Local branch_pos%
	new_y=-3 ;used for counting off; branches will only be considered once every 4 units so as to avoid potentially too many branches
	While new_y<gridsize-6
		new_y=new_y+4
		temp_y=new_y
		new_x=0
		If chance(branch_chance) Then
			;create a branch at this spot
			;determine if on left or on right
			branch_pos=2*Rand(0,1)
			;get leftmost or rightmost path in this row
			Local leftmost=gridsize-1
			Local rightmost=0
			For i=0 To gridsize-1
				If fr\grid[((gridsize-1-new_y)*gridsize)+i]=1 Then
					If i<leftmost Then leftmost=i
					If i>rightmost Then rightmost=i
				EndIf
			Next
			If branch_pos=0 Then new_x=leftmost-1 Else new_x=rightmost+1
			;before creating a branch make sure it won't pass the border and there are no 1's above or below
			If new_x>=0 And new_x<gridsize And fr\grid[((gridsize-1-temp_y-1)*gridsize)+new_x]<>1 And fr\grid[((gridsize-1-temp_y+1)*gridsize)+new_x]<>1 Then
				fr\grid[((gridsize-1-temp_y)*gridsize)+new_x]=-1 ;make -1s so you don't confuse your branch for a path; will be changed later
				If branch_pos=0 Then
					new_x=leftmost-2
				Else
					new_x=rightmost+2
				EndIf
				;before continuing the branch make sure it won't pass the border
				If new_x>=0 And new_x<gridsize Then
					fr\grid[((gridsize-1-temp_y)*gridsize)+new_x]=-1 ;branch out twice to avoid creating an unwanted 2x2 path with the real path
					i = 2
					While i<branch_max_life
						i=i+1
						If chance(branch_die_chance) Then Exit
						If Rand(0,3)=0 Then ;have a higher chance to go up to confuse the player
							If branch_pos = 0 Then
								new_x=new_x-1
							Else
								new_x=new_x+1
							EndIf
						Else
							temp_y=temp_y+1
						EndIf
						
						;before continuing the branch make sure it won't pass the border and there are no 1's above
						If new_x<0 Or new_x>=gridsize Or fr\grid[((gridsize-1-temp_y-1)*gridsize)+new_x]=1 Then Exit
						
						fr\grid[((gridsize-1-temp_y)*gridsize)+new_x]=-1 ;make -1s so you don't confuse your branch for a path; will be changed later
						If temp_y>=gridsize-2 Then Exit
					Wend
				EndIf
			EndIf
		EndIf
	Wend
	
	If DebugForestGen Then
		Local x%, y%
		Repeat
			Cls()

			Local tileSize = Max(Min(32 * HUDScale, Min(GraphicWidth / gridsize, (GraphicHeight - 50 * HUDScale) / gridsize)), 3)
			Local totalWidth = gridsize * tileSize - 2, totalHeight = gridsize * tileSize - 2
			Local xStart = GraphicWidth / 2 - totalWidth / 2, yStart = GraphicHeight / 2 - totalHeight / 2 - 50 * HUDScale

			i=gridsize-1
			For x=0 To gridsize-1
				For y=0 To gridsize-1
					If fr\grid[x+(y*gridsize)]=0 Then
						Color(50,50,50)
						Rect(xStart + i*tileSize,yStart + y*tileSize,tileSize-2,tileSize-2)
					Else
						Color(255,255,255)
						Rect(xStart + i*tileSize,yStart + y*tileSize,tileSize-2,tileSize-2)
					EndIf
				Next
				i=i-1
			Next
			
			i=gridsize-1
			For x=0 To gridsize-1
				For y=0 To gridsize-1
					If MouseOn(xStart + i*tileSize,yStart + y*tileSize,tileSize,tileSize) Then
						Color(255,0,0)
					Else
						Color(0,0,0)
					EndIf
					Text(xStart + i*tileSize+2,yStart + y*tileSize+2,fr\grid[x+(y*gridsize)])
				Next
				i=i-1
			Next

			If Fullscreen Then DrawImage(CursorIMG,ScaledMouseX(),ScaledMouseY())
			Color(255, 255, 255)
			Text(GraphicWidth / 2, GraphicHeight - 25 * HUDScale, I_Loc\Menu_Pressany, True, True)
			
			Flip()
		Until GetKey() <> 0
		FlushKeys()
	EndIf
	
	;change branches from -1s to 1s
	For i=1 To gridsize-2
		For j=0 To gridsize-1
			If fr\grid[(i*gridsize)+j]=-1 Then
				fr\grid[(i*gridsize)+j]=1
			EndIf
		Next
	Next
	
	CatchErrors("GenForestGrid")
End Function

Function PlaceForest(fr.Forest,x#,y#,z#,r.Rooms)
	CatchErrors("Uncaught (PlaceForest)")
	;local variables
	Local tx%,ty%
	Local tile_size#=12.0
	Local tile_type%
	Local tile_entity%,detail_entity%
	
	Local tempf1#,tempf2#,tempf3#,tempf4#
	Local i%
	
	fr\Forest_Pivot=CreatePivot()
	PositionEntity fr\Forest_Pivot,x,y,z,True
	
	;load assets
	
	Local hmap[ROOM4 + 1], mask[ROOM4 + 1]
	Local GroundTexture = LoadTexture_Strict("GFX\map\forest\forestfloor.jpg")
	Local PathTexture = LoadTexture_Strict("GFX\map\forest\forestpath.jpg")
	
	hmap[ROOM1]=LoadTexture_Strict("GFX\map\forest\forest1h.png",hmapFlags)
	mask[ROOM1]=LoadTexture_Strict("GFX\map\forest\forest1h_mask.png",maskFlags)
	
	hmap[ROOM2]=LoadTexture_Strict("GFX\map\forest\forest2h.png",hmapFlags)
	mask[ROOM2]=LoadTexture_Strict("GFX\map\forest\forest2h_mask.png",maskFlags)
	
	hmap[ROOM2C]=LoadTexture_Strict("GFX\map\forest\forest2Ch.png",hmapFlags)
	mask[ROOM2C]=LoadTexture_Strict("GFX\map\forest\forest2Ch_mask.png",maskFlags)
	
	hmap[ROOM3]=LoadTexture_Strict("GFX\map\forest\forest3h.png",hmapFlags)
	mask[ROOM3]=LoadTexture_Strict("GFX\map\forest\forest3h_mask.png",maskFlags)
	
	hmap[ROOM4]=LoadTexture_Strict("GFX\map\forest\forest4h.png",hmapFlags)
	mask[ROOM4]=LoadTexture_Strict("GFX\map\forest\forest4h_mask.png",maskFlags)
	
	For i = ROOM1 To ROOM4
		fr\TileMesh[i]=load_terrain(hmap[i],0.03,GroundTexture,PathTexture,mask[i])
	Next
	
	;detail meshes
	fr\DetailMesh[0]=LoadMesh_Strict("GFX\map\forest\detail\treetest4.b3d");1.b3d)
	fr\DetailMesh[1]=LoadMesh_Strict("GFX\map\forest\detail\rock.b3d")
	fr\DetailMesh[2]=LoadMesh_Strict("GFX\map\forest\detail\treetest5.b3d")
	fr\DetailMesh[3]=LoadRMesh("GFX\map\forest\wall.rmesh",Null)
	
	For i%=ROOM1 To ROOM4
		HideEntity fr\TileMesh[i]
	Next
	For i%=0 To 3
		HideEntity fr\DetailMesh[i]
	Next
	
	tempf3=MeshWidth(fr\TileMesh[ROOM1])
	tempf1=tile_size/tempf3
	
	For tx%=0 To gridsize-1
		For ty%=1 To gridsize-2
			If fr\grid[(ty*gridsize)+tx]=1 Then 
				
				tile_type = 0
				If tx+1<gridsize Then tile_type = (fr\grid[(ty*gridsize)+tx+1]>0)
				If tx-1=>0 Then tile_type = tile_type+(fr\grid[(ty*gridsize)+tx-1]>0)
				
				If ty+1<gridsize Then tile_type = tile_type+(fr\grid[((ty+1)*gridsize)+tx]>0)
				If ty-1=>0 Then tile_type = tile_type+(fr\grid[((ty-1)*gridsize)+tx]>0)
				
				;fr\grid[(ty*gridsize)+tx]=tile_type
				
				Local angle%=0
				Select tile_type
					Case 1
						tile_entity = CopyEntity(fr\TileMesh[ROOM1])
						
						If fr\grid[((ty+1)*gridsize)+tx]>0 Then
							angle = 180
						ElseIf fr\grid[(ty*gridsize)+tx-1]>0
							angle = 270
						ElseIf fr\grid[(ty*gridsize)+tx+1]>0
							angle = 90
						End If
						
						tile_type = ROOM1
					Case 2
						If fr\grid[((ty-1)*gridsize)+tx]>0 And fr\grid[((ty+1)*gridsize)+tx]>0 Then
							tile_entity = CopyEntity(fr\TileMesh[ROOM2])
							tile_type = ROOM2
						ElseIf fr\grid[(ty*gridsize)+tx+1]>0 And fr\grid[(ty*gridsize)+tx-1]>0
							tile_entity = CopyEntity(fr\TileMesh[ROOM2])
							angle = 90
							tile_type = ROOM2
						Else
							tile_entity = CopyEntity(fr\TileMesh[ROOM2C])
							If fr\grid[(ty*gridsize)+tx-1]>0 And fr\grid[((ty+1)*gridsize)+tx]>0 Then
								angle = 180
							ElseIf fr\grid[(ty*gridsize)+tx+1]>0 And fr\grid[((ty-1)*gridsize)+tx]>0
								
							ElseIf fr\grid[(ty*gridsize)+tx-1]>0 And fr\grid[((ty-1)*gridsize)+tx]>0
								angle = 270
							Else
								angle = 90
							EndIf
							tile_type = ROOM2C
						EndIf
					Case 3
						tile_entity = CopyEntity(fr\TileMesh[ROOM3])
						
						If fr\grid[((ty-1)*gridsize)+tx]=0 Then
							angle = 180
						ElseIf fr\grid[(ty*gridsize)+tx-1]=0
							angle = 90
						ElseIf fr\grid[(ty*gridsize)+tx+1]=0
							angle = 270
						End If
						
						tile_type = ROOM3
					Case 4
						tile_entity = CopyEntity(fr\TileMesh[ROOM4])	
						tile_type = ROOM4
					Default 
						DebugLog "tile_type: "+tile_type
				End Select
				
				If tile_type > 0 Then 
					PlaceForestProps(tile_entity, hmap[tile_type], tempf3, 0.03, fr\DetailMesh[0], fr\DetailMesh[2], fr\DetailMesh[1])
					
					ScaleEntity tile_entity,tempf1,tempf1,tempf1
					
					Local itemPlaced[4]
					;2, 5, 8
					Local it.Items = Null
					If (ty Mod 3)=2 And itemPlaced[Floor(ty/3)]=False Then
						itemPlaced[Floor(ty/3)]=True
						If tile_type=ROOM1 Then
							it.Items = CreateItem("log"+Int(Floor(ty/3)+1), 0.4,0.2,0)
						ElseIf tile_type=ROOM2C
							it.Items = CreateItem("log"+Int(Floor(ty/3)+1), 1.7,0.2,-0.4)
						Else
							it.Items = CreateItem("log"+Int(Floor(ty/3)+1), 0,0.2,0)
						EndIf
						EntityParent(it\collider, tile_entity)
					EndIf
					
					TurnEntity tile_entity, 0, angle, 0
					
					PositionEntity tile_entity,x+(tx*tile_size),y,z+(ty*tile_size),True
					
					EntityType tile_entity,HIT_MAP
					EntityFX tile_entity,1
					EntityParent tile_entity,fr\Forest_Pivot
					EntityPickMode tile_entity,2
					
					If it<>Null Then EntityParent it\collider,0
					
					fr\TileEntities[tx+(ty*gridsize)] = tile_entity
				Else
					DebugLog "INVALID TILE @ ("+tx+", "+ty+ "): "+tile_type
				EndIf
			EndIf
			
		Next
	Next
	
	;place the wall		
	For i = 0 To 1
		ty = ((gridsize-1)*i)
		
		For tx = min_door_pos To max_door_pos
			If fr\grid[(ty*gridsize)+tx]=3 Then
				fr\DetailEntities[i]=CopyEntity(fr\DetailMesh[3])
				ScaleEntity fr\DetailEntities[i],RoomScale,RoomScale,RoomScale
				
				fr\Door[i] = CopyEntity(r\Objects[3])
				PositionEntity fr\Door[i],68*RoomScale,32.0*RoomScale,0,True
				RotateEntity fr\Door[i], 0,180,0
				ScaleEntity fr\Door[i],46*RoomScale,44*RoomScale,46*RoomScale,True
				EntityParent fr\Door[i],fr\DetailEntities[i]
				;SetAnimTime fr\Door[i], 0
				
				Local frame = CopyEntity(r\Objects[2],fr\Door[i])
				PositionEntity frame,0,32.0*RoomScale,0,True
				ScaleEntity frame,45*RoomScale,44*RoomScale,80*RoomScale,True
				EntityParent frame,fr\DetailEntities[i]
				
				EntityType fr\DetailEntities[i],HIT_MAP
				;EntityParent frame,fr\DetailEntities[i]
				EntityPickMode fr\DetailEntities[i],2
				
				PositionEntity fr\DetailEntities[i],x+(tx*tile_size),y,z+(ty*tile_size)+(tile_size/2)-(tile_size*i),True
				RotateEntity fr\DetailEntities[i],0,180*i,0
				
				EntityParent fr\DetailEntities[i],fr\Forest_Pivot
				Exit
			EndIf		
		Next		
	Next

	FreeTexture GroundTexture
	FreeTexture PathTexture
	For i = ROOM1 To ROOM4
		FreeTexture(hmap[i])
		FreeTexture(mask[i])
	Next

	HideEntity fr\Forest_Pivot
	
	CatchErrors("PlaceForest")
End Function

Const hmapFlags% = 1+16+32+256+512+16384
Const maskFlags% = hmapFlags+2

Function PlaceForest_MapCreator(fr.Forest,x#,y#,z#,r.Rooms)
	CatchErrors("Uncaught (PlaceForest_MapCreator)")
	;local variables
	Local tx%,ty%
	Local tile_size#=12.0
	Local tile_type%
	Local tile_entity%,detail_entity%
	
	Local tempf1#,tempf2#,tempf3#,tempf4#
	Local i%
	
	fr\Forest_Pivot=CreatePivot()
	PositionEntity fr\Forest_Pivot,x,y,z,True
	
	;load assets
	
	Local hmap[ROOM4 + 1], mask[ROOM4 + 1]
	Local GroundTexture = LoadTexture_Strict("GFX\map\forest\forestfloor.jpg")
	Local PathTexture = LoadTexture_Strict("GFX\map\forest\forestpath.jpg")
	
	hmap[ROOM1]=LoadTexture_Strict("GFX\map\forest\forest1h.png",hmapFlags)
	mask[ROOM1]=LoadTexture_Strict("GFX\map\forest\forest1h_mask.png",maskFlags)
	
	hmap[ROOM2]=LoadTexture_Strict("GFX\map\forest\forest2h.png",hmapFlags)
	mask[ROOM2]=LoadTexture_Strict("GFX\map\forest\forest2h_mask.png",maskFlags)
	
	hmap[ROOM2C]=LoadTexture_Strict("GFX\map\forest\forest2Ch.png",hmapFlags)
	mask[ROOM2C]=LoadTexture_Strict("GFX\map\forest\forest2Ch_mask.png",maskFlags)
	
	hmap[ROOM3]=LoadTexture_Strict("GFX\map\forest\forest3h.png",hmapFlags)
	mask[ROOM3]=LoadTexture_Strict("GFX\map\forest\forest3h_mask.png",maskFlags)
	
	hmap[ROOM4]=LoadTexture_Strict("GFX\map\forest\forest4h.png",hmapFlags)
	mask[ROOM4]=LoadTexture_Strict("GFX\map\forest\forest4h_mask.png",maskFlags)
	
	For i = ROOM1 To ROOM4
		fr\TileMesh[i]=load_terrain(hmap[i],0.03,GroundTexture,PathTexture,mask[i])
	Next
	
	;detail meshes
	fr\DetailMesh[0]=LoadMesh_Strict("GFX\map\forest\detail\treetest4.b3d");1.b3d)
	fr\DetailMesh[1]=LoadMesh_Strict("GFX\map\forest\detail\rock.b3d")
	fr\DetailMesh[2]=LoadMesh_Strict("GFX\map\forest\detail\treetest5.b3d")
	fr\DetailMesh[3]=LoadRMesh("GFX\map\forest\wall.rmesh",Null)
	
	For i%=ROOM1 To ROOM4
		HideEntity fr\TileMesh[i]
	Next
	For i%=0 To 3
		HideEntity fr\DetailMesh[i]
	Next
	
	tempf3=MeshWidth(fr\TileMesh[ROOM1])
	tempf1=tile_size/tempf3
	
	DebugLog "ForestINIT"
	
	For tx%=0 To gridsize-1
		For ty%=0 To gridsize-1
			If fr\grid[(ty*gridsize)+tx]>0 Then 
				
				tile_type = 0
				Local angle%=0
				
				tile_type = Ceil(Float(fr\grid[(ty*gridsize)+tx])/4.0)
				If tile_type = 6 Then
					tile_type = 2
				EndIf
				angle = (fr\grid[(ty*gridsize)+tx] Mod 4)*90
				
				tile_entity = CopyEntity(fr\TileMesh[tile_type])
				
				DebugLog "Tile: "+tile_type+"| Angle: "+angle
				
				If tile_type > 0 Then 
					PlaceForestProps(tile_entity, hmap[tile_type], tempf3, 0.03, fr\DetailMesh[0], fr\DetailMesh[2], fr\DetailMesh[1])
					
					ScaleEntity tile_entity,tempf1,tempf1,tempf1
					
					Local itemPlaced[4]
					;2, 5, 8
					Local it.Items = Null
					If (ty Mod 3)=2 And itemPlaced[Floor(ty/3)]=False Then
						itemPlaced[Floor(ty/3)]=True
						If tile_type=ROOM1 Then
							it.Items = CreateItem("log"+Int(Floor(ty/3)+1), 0.4,0.2,0)
						ElseIf tile_type=ROOM2C
							it.Items = CreateItem("log"+Int(Floor(ty/3)+1), 1.7,0.2,-0.4)
						Else
							it.Items = CreateItem("log"+Int(Floor(ty/3)+1), 0,0.2,0)
						EndIf
						EntityParent(it\collider, tile_entity)
					EndIf
					
					TurnEntity tile_entity, 0, angle, 0
					
					PositionEntity tile_entity,x+(tx*tile_size),y,z+(ty*tile_size),True
					
					DebugLog "tile_entity: "+(x+(tx*tile_size))+"|"+(y)+"|"+(z+(ty*tile_size))
					
					EntityType tile_entity,HIT_MAP
					EntityFX tile_entity,1
					EntityParent tile_entity,fr\Forest_Pivot
					EntityPickMode tile_entity,2
					
					If it<>Null Then EntityParent it\collider,0
					
					fr\TileEntities[tx+(ty*gridsize)] = tile_entity
				Else
					DebugLog "INVALID TILE @ ("+tx+", "+ty+ "): "+tile_type
				EndIf
				
				If Ceil(fr\grid[(ty*gridsize)+tx]/4.0)=6 Then
					For i = 0 To 1
						If fr\Door[i]=0 Then
							fr\DetailEntities[i]=CopyEntity(fr\DetailMesh[3])
							ScaleEntity fr\DetailEntities[i],RoomScale,RoomScale,RoomScale
							
							fr\Door[i] = CopyEntity(r\Objects[3])
							PositionEntity fr\Door[i],68*RoomScale,32.0*RoomScale,0,True
							RotateEntity fr\Door[i], 0,180,0
							ScaleEntity fr\Door[i],46*RoomScale,44*RoomScale,46*RoomScale,True
							EntityParent fr\Door[i],fr\DetailEntities[i]
							
							Local frame = CopyEntity(r\Objects[2],fr\Door[i])
							PositionEntity frame,0,32.0*RoomScale,0,True
							ScaleEntity frame,45*RoomScale,44*RoomScale,80*RoomScale,True
							EntityParent frame,fr\DetailEntities[i]
							
							EntityType fr\DetailEntities[i],HIT_MAP
							EntityPickMode fr\DetailEntities[i],2
							
							PositionEntity fr\DetailEntities[i],x+(tx*tile_size),y,z+(ty*tile_size),True
							RotateEntity fr\DetailEntities[i],0,angle+180,0
							MoveEntity fr\DetailEntities[i],0,0,-6
							
							EntityParent fr\DetailEntities[i],fr\Forest_Pivot
							Exit
						EndIf
					Next
				EndIf
			Else
				DebugLog "NO TILE FOUND @ ("+tx+", "+ty+ ")"
			EndIf
		Next
	Next
	
	FreeTexture GroundTexture
	FreeTexture PathTexture
	For i = ROOM1 To ROOM4
		FreeTexture(hmap[i])
		FreeTexture(mask[i])
	Next

	DebugLog "ForestINIT END"
	
	CatchErrors("PlaceForest_MapCreator")
End Function

Function PlaceForestProps(parent%, hmap%, tempf3#, yscale#, treeMesh%, leafMesh%, rockMesh%, minHeight%=100, maxHeight%=260, maxHeightCap%=255, treeProp%=7, rockMinSize#=1, rockMaxSize#=1)
	;place trees and other details
	;only placed on spots where the value of the heightmap is above minHeight
	Local width = TextureWidth(hmap)
	Local buf% = TextureBuffer(hmap)
	LockBuffer(buf)
	tempf4# = (tempf3/Float(width))
	For lx = 3 To width-2
		For ly = 3 To width-2
			Local red% = (ReadPixelFast(lx,width-ly,buf) And $FF0000) Shr 16
			
			If Min(red, maxHeightCap) > Rand(minHeight, maxHeight) Then
				Select Rand(0,treeProp)
					Case 0 ;add a rock
						Local detail_entity=CopyEntity(rockMesh)
						EntityFX(detail_entity, 1)
						;EntityType detail_entity,HIT_MAP
						Local tempf2#=Rnd(rockMinSize,rockMaxSize)
						ScaleEntity detail_entity,tempf2,tempf2,tempf2,True
						
						PositionEntity detail_entity,lx*tempf4-(tempf3/2.0),red*yscale-1.3,ly*tempf4-(tempf3/2.0),True
						
						RotateEntity detail_entity,0.0,Rnd(360.0),0.0,True
					Default ;create a tree
						detail_entity%=CopyEntity(treeMesh)
						EntityFX(detail_entity, 1)
						;EntityType detail_entity,HIT_MAP
						tempf2#=Rnd(0.25,0.4)
						
						For i = 0 To 3
							Local d=CopyEntity(leafMesh)
							EntityFX(d, 1)
							;ScaleEntity d,tempf2*1.1,tempf2,tempf2*1.1,True
							RotateEntity d, 0, 90*i+Rnd(-20,20), 0
							EntityParent(d,detail_entity)
							
						Next
						
						ScaleEntity detail_entity,tempf2*1.1,tempf2,tempf2*1.1,True
						PositionEntity detail_entity,lx*tempf4-(tempf3/2.0),red*yscale-Rnd(3.0,3.2),ly*tempf4-(tempf3/2.0),True
						RotateEntity detail_entity,Rnd(-5,5),Rnd(360.0),0.0,True
						
						;EntityAutoFade(detail_entity,4.0,6.0)
				End Select
				
				;PositionEntity detail_entity,Rnd(0.0,tempf3)-(tempf3/2.0),red*yscale-0.05,Rnd(0.0,tempf3)-(tempf3/2.0),True
				EntityParent detail_entity,parent
			EndIf
		Next
	Next
	UnlockBuffer(buf)
End Function

Function DestroyForest(fr.Forest, nullgrid%=True)
	CatchErrors("Uncaught (DestroyForest)")
	Local tx%,ty%
	For tx% = 0 To gridsize-1
		For ty% = 0 To gridsize-1
			If fr\TileEntities[tx+(ty*gridsize)]<>0 Then
				FreeEntity fr\TileEntities[tx+(ty*gridsize)] : fr\TileEntities[tx+(ty*gridsize)] = 0
				If nullgrid Then fr\grid[tx+(ty*gridsize)] = 0
			EndIf
		Next
	Next
	For i = 0 To 1
		If fr\Door[i]<>0 Then FreeEntity fr\Door[i] : fr\Door[i] = 0
		If fr\DetailEntities[i]<>0 Then FreeEntity fr\DetailEntities[i] : fr\DetailEntities[i] = 0
	Next
	If fr\Forest_Pivot<>0 Then FreeEntity fr\Forest_Pivot : fr\Forest_Pivot=0
	For i%=ROOM1 To ROOM4
		If fr\TileMesh[i]<>0 Then FreeEntity fr\TileMesh[i] : fr\TileMesh[i]=0
	Next
	For i%=0 To 3
		If fr\DetailMesh[i]<>0 Then FreeEntity fr\DetailMesh[i] : fr\DetailMesh[i]=0
	Next
	
	Delete fr
	CatchErrors("DestroyForest")
End Function


Function UpdateForest(fr.Forest)
	CatchErrors("Uncaught (UpdateForest)")
	;local variables
	Local tx%,ty%
	
	For tx% = 0 To gridsize-1
		For ty% = 0 To gridsize-1
			If fr\TileEntities[tx+(ty*gridsize)]<>0 Then
				If Abs(EntityX(Collider,True)-EntityX(fr\TileEntities[tx+(ty*gridsize)],True))<HideDistance Then
					If Abs(EntityZ(Collider,True)-EntityZ(fr\TileEntities[tx+(ty*gridsize)],True))<HideDistance Then
						ShowEntity fr\TileEntities[tx+(ty*gridsize)]
					Else
						HideEntity fr\TileEntities[tx+(ty*gridsize)]
					EndIf
				Else
					HideEntity fr\TileEntities[tx+(ty*gridsize)]
				EndIf
			EndIf
		Next
	Next
		
	CatchErrors("UpdateForest")
End Function

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

Const MaxRoomLights% = 32
Const MaxRoomEmitters% = 8
Const MaxRoomObjects% = 30
COnst MaxRoomItems% = 32


Const ROOM1% = 1, ROOM2% = 2, ROOM2C% = 3, ROOM3% = 4, ROOM4% = 5

Global RoomTempID% = 0
Type RoomTemplates
	Field obj%, id%
	Field objPath$
	
	Field zone%[ZONEAMOUNT]
	
	;Field ambience%
	
	Field TempSoundEmitter%[MaxRoomEmitters]
	Field TempSoundEmitterX#[MaxRoomEmitters],TempSoundEmitterY#[MaxRoomEmitters],TempSoundEmitterZ#[MaxRoomEmitters]
	Field TempSoundEmitterRange#[MaxRoomEmitters]
	
	Field Shape%, Name$
	Field Commonness%, Large%
	Field SetRoom#, SetRoomPriority%
	Field DisableDecals%

	Field R%, G%, B%
	
	Field FirstTempTriggerbox.TempTriggerboxes

	Field FirstTempItem.TempItems

	Field FirstTempDoor.TempDoors
	
	Field UseLightCones%
	
	Field DisableOverlapCheck%
	
	Field MinX#, MinY#, MinZ#
	Field MaxX#, MaxY#, MaxZ#
End Type 	

Type TempTriggerboxes
	Field Obj%
	Field Name$
	Field Successor.TempTriggerboxes
End Type

Type TempItems
	Field Name$
	Field X#, Y#, Z#
	Field HasCustomAngle%, AngleX#, AngleY#, AngleZ#
	Field State#, State2#
	Field Chance#
	Field Successor.TempItems
End Type

Type TempDoors
	Field Dir%
	Field Index%
	Field Code$
	Field X#, Y#, Z#
	Field Angle#
	Field SpawnOpen%
	Field Locked%
	Field DeleteHalf%
	Field AllowRemoteControl%
	Field Button1PosX#, Button1PosY#, Button1PosZ#
	Field Button1RotX#, Button1RotY#, Button1RotZ#
	Field Button2PosX#, Button2PosY#, Button2PosZ#
	Field Button2RotX#, Button2RotY#, Button2RotZ#
	Field Successor.TempDoors
End Type

Function CreateRoomTemplate.RoomTemplates(name$)
	Local rt.RoomTemplates = New RoomTemplates
	
	rt\SetRoom = -1

	rt\Name = Lower(name)

	rt\R = 255 : rt\G = 255 : rt\B = 255
	
	rt\id = RoomTempID
	RoomTempID=RoomTempID+1
	
	Return rt
End Function

Function LoadRoomTemplates(file$)
	CatchErrors("Uncaught (LoadRoomTemplates)")
	Local TemporaryString$, i%
	Local rt.RoomTemplates = Null
	Local StrTemp$ = ""
	
	Local f = OpenFile(file)
	
	While Not Eof(f)
		Local l$ = Trim(ReadLine(f))
		If Left(l,1) = "[" Then
			l = Mid(l, 2, Len(l) - 2)

			rt = Null
			For rtt.RoomTemplates = Each RoomTemplates
				If rtt\Name = l Then rt = rtt : Exit
			Next

			If rt = Null Then rt = CreateRoomTemplate(l)
		Else If l <> "" And Instr(l, "#") <> 1 And Instr(l, ";") <> 1 Then
			Local splitterPos% = Instr(l, "=")
			If splitterPos = 0 Then Continue
			Local key$ = Lower(Trim(Left(l, splitterPos - 1)))
			Local value$ = Trim(Right(l, Len(l) - splitterPos))
			Select key
				Case "mesh path", "meshpath" rt\objPath = value
				Case "shape"
					Select Lower(value)
						Case "room1", "1" rt\Shape = ROOM1
						Case "room2", "2" rt\Shape = ROOM2
						Case "room2c", "2c" rt\Shape = ROOM2C
						Case "room3", "3" rt\Shape = ROOM3
						Case "room4", "4" rt\Shape = ROOM4
					End Select
				Case "zone1", "zone2", "zone3" rt\zone[Int(Right(key, 1))-1] = Int(value)
				Case "commonness" rt\Commonness = Max(Min(Int(value), 100), 0)
				Case "large" rt\Large = ParseINIInt(value)
				Case "set room", "setroom" rt\SetRoom = Float(value)
				Case "set room priority", "setroompriority" rt\SetRoomPriority = Int(value)
				Case "disable decals", "disabledecals" rt\DisableDecals = ParseINIInt(value)
				Case "use volume lighting", "usevolumelighting" rt\UseLightCones = ParseINIInt(value)
				Case "disable overlap check", "disableoverlapcheck" rt\DisableOverlapCheck = ParseINIInt(value)
				Case "r" rt\R = Int(value)
				Case "g" rt\G = Int(value)
				Case "b" rt\B = Int(value)
			End Select
		EndIf
	Wend
	
	i = 1
	Repeat
		StrTemp = GetINIString(file, "room ambience", "ambience"+i)
		If StrTemp = "" Then Exit
		
		RoomAmbience[i]=LoadSound_Strict(StrTemp)
		i=i+1
	Forever
	
	CloseFile f
	
	CatchErrors("LoadRoomTemplates")
End Function


Function LoadRoomMesh(rt.RoomTemplates)
	
	If Instr(rt\objPath,".rm")<>0 Then
		rt\obj = LoadRMesh(rt\objPath, rt)
	EndIf
	
	If (Not rt\obj) Then RuntimeErrorExt "Failed to load map file "+Chr(34)+rt\objPath+Chr(34)+"."
	
	CalculateRoomTemplateExtents(rt)
	
	HideEntity(rt\obj)
	
End Function


Const ROOMS_DATA_PATH$ = "Data\rooms.ini"

Function InitRoomTemplates()
	Delete Each RoomTemplates
	Delete Each LightTemplates

	Local modRooms$
	Local hasOverride%
	Local lowest.ActiveMods = Last ActiveMods
	For m.ActiveMods = Each ActiveMods
		modRooms$ = m\Path + ROOMS_DATA_PATH
		If FileType(m\Path + ROOMS_DATA_PATH) = 1 And FileType(m\Path + ROOMS_DATA_PATH + ".OVERRIDE") = 1 Then
			hasOverride = True
			lowest = m
			Exit
		EndIf
	Next
	; We load the vanilla templates first, so that they will also be placed within the map first (to be potentially overriden with mod rooms).
	If Not hasOverride Then LoadRoomTemplates(ROOMS_DATA_PATH)
	While lowest<>Null
		modRooms$ = lowest\Path + ROOMS_DATA_PATH
		If FileType(modRooms) = 1 Then
			LoadRoomTemplates(modRooms)
		EndIf
		lowest = Before lowest
	Wend

	Local sorted%
	Repeat
		Sorted = True
		For rt.RoomTemplates = Each RoomTemplates
			Local rtt.RoomTemplates = After rt
			While rtt <> Null And rtt\SetRoomPriority > rt\SetRoomPriority
			sorted = False
				Insert rtt Before rt
				rtt = After rt
			Wend
		Next
	Until sorted

	For rt.RoomTemplates = Each RoomTemplates
		DebugLog(rt\Name + "  " + rt\SetRoomPriority + " " + rt\SetRoom)
	Next

End Function

Const MapOptions$ = "Data\map.ini"
Const RoomScale# = 8.0 / 2048.0
Const ZONEAMOUNT = 3
Global MapWidth%, MapHeight%
Dim MapTemp%(0,0)
Dim MapFound%(0,0)

Global RoomAmbience%[20]

Global Sky

Global HideDistance# = 15.0

Global SecondaryLightOn# = True
Global PrevSecondaryLightOn# = True
Global RemoteDoorOn = True
Global Contained106 = False

Const ROOM_DOORS_COUNT% = 9
Type Rooms
	Field zone%
	
	Field found%
	
	Field obj%
	Field x#, y#, z#
	Field angle%
	Field RoomTemplate.RoomTemplates
	
	Field dist#
	
	Field SoundCHN%
	
	Field fr.Forest
	
	Field SoundEmitter%[MaxRoomEmitters]
	Field SoundEmitterObj%[MaxRoomEmitters]
	Field SoundEmitterRange#[MaxRoomEmitters]
	Field SoundEmitterCHN%[MaxRoomEmitters]
	
	Field Lights%[MaxRoomLights]
	Field LightIntensity#[MaxRoomLights]
	
	Field LightSprites%[MaxRoomLights]	
	
	Field Objects%[MaxRoomObjects]
	Field Levers%[11]
	Field RoomDoors.Doors[ROOM_DOORS_COUNT]
	Field NPC.NPCs[12]
	Field grid.Grids
	
	Field Adjacent.Rooms[4]
	Field AdjDoor.Doors[4]
	
	Field Textures%[10]
	
	Field MaxLights% = 0
	Field LightSpriteHidden%[MaxRoomLights]
	Field LightSpritesPivot%[MaxRoomLights]
	Field LightSprites2%[MaxRoomLights]
	Field LightHidden%[MaxRoomLights]
	Field LightFlicker%[MaxRoomLights]
	Field FirstTriggerbox.Triggerboxes
	Field MaxWayPointY#
	Field LightR#[MaxRoomLights],LightG#[MaxRoomLights],LightB#[MaxRoomLights]
	Field LightCone%[MaxRoomLights]
	Field LightConeSpark%[MaxRoomLights]
	Field LightConeSparkTimer#[MaxRoomLights]
	
	Field MinX#, MinY#, MinZ#
	Field MaxX#, MaxY#, MaxZ#
End Type 

Global MTStartRoom.Rooms

Type Triggerboxes
	Field Obj%
	Field MinX#, MinY#, MinZ#
	Field MaxX#, MaxY#, MaxZ#
	Field Name$
	Field Successor.Triggerboxes
End Type

Const gridsz%=19 ;Same size as the main map itself (better for the map creator)
Type Grids
	Field grid%[gridsz*gridsz]
	Field angles%[gridsz*gridsz]
	Field Meshes%[7]
	Field Entities%[gridsz*gridsz]
	Field waypoints.WayPoints[gridsz*gridsz]
End Type

Function UpdateGrid(grid.Grids)
	;local variables
	Local tx%,ty%
	For tx% = 0 To gridsz-1
		For ty% = 0 To gridsz-1
			If grid\Entities[tx+(ty*gridsz)]<>0 Then
				If Abs(EntityY(Collider,True)-EntityY(grid\Entities[tx+(ty*gridsz)],True))>4.0 Then Exit
				If Abs(EntityX(Collider,True)-EntityX(grid\Entities[tx+(ty*gridsz)],True))<HideDistance Then
					If Abs(EntityZ(Collider,True)-EntityZ(grid\Entities[tx+(ty*gridsz)],True))<HideDistance Then
						ShowEntity grid\Entities[tx+(ty*gridsz)]
					Else
						HideEntity grid\Entities[tx+(ty*gridsz)]
					EndIf
				Else
					HideEntity grid\Entities[tx+(ty*gridsz)]
				EndIf
			EndIf
		Next
	Next
End Function

Const MT_HEIGHT# = 16.0

Function PlaceGrid_MapCreator(r.Rooms)
	Local x,y,i
	Local Meshes[7]
	Local dr.Doors,it.Items
	
	For i=0 To 6
		Meshes[i]=CopyEntity(OBJTunnel(i))
		DebugLog i
		HideEntity Meshes[i]
	Next
	
	For y = 0 To (gridsz-1)
		For x = 0 To (gridsz-1)
			If r\grid\grid[x+(y*gridsz)]>0 Then
				Local tile_type = 0
				Local angle%=0
				
				tile_type = r\grid\grid[x+(y*gridsz)]
				angle = r\grid\angles[x+(y*gridsz)]*90.0
				
				Local tile_entity = CopyEntity(Meshes[tile_type-1])
				RotateEntity tile_entity,0,angle,0
				ScaleEntity tile_entity,RoomScale,RoomScale,RoomScale,True
				PositionEntity tile_entity,r\x+x*2.0,MT_HEIGHT,r\z+y*2.0,True
				
				Select r\grid\grid[x+(y*gridsz)]
					Case ROOM1, ROOM2
						AddLight%(Null, r\x+x*2.0, MT_HEIGHT+(372.0*RoomScale), r\z+y*2.0, 2, 500.0 * RoomScale, 255, 255, 255)
					Case ROOM2C,ROOM3,ROOM4
						AddLight%(Null,r\x+x*2.0, MT_HEIGHT+(416.0*RoomScale), r\z+y*2.0, 2, 500.0 * RoomScale, 255, 255, 255)
					Case ROOM4+1
						dr=CreateDoor(r\zone,r\x+(x*2.0)+(Cos(EntityYaw(tile_entity,True))*240.0*RoomScale),MT_HEIGHT,r\z+(y*2.0)+(Sin(EntityYaw(tile_entity,True))*240.0*RoomScale),EntityYaw(tile_entity,True)+90.0,Null,False,3,"")
						PositionEntity dr\buttons[0],EntityX(dr\buttons[0],True)+(Cos(EntityYaw(tile_entity,True))*0.05),EntityY(dr\buttons[0],True)+0.0,EntityZ(dr\buttons[0],True)+(Sin(EntityYaw(tile_entity,True))*0.05),True
						
						AddLight%(Null, r\x+x*2.0+(Cos(EntityYaw(tile_entity,True))*555.0*RoomScale), MT_HEIGHT+(469.0*RoomScale), r\z+y*2.0+(Sin(EntityYaw(tile_entity,True))*555.0*RoomScale), 2, 600.0 * RoomScale, 255, 255, 255)
						
						Local tempInt2=CreatePivot()
						RotateEntity tempInt2,0,EntityYaw(tile_entity,True)+180.0,0,True
						PositionEntity tempInt2,r\x+(x*2.0)+(Cos(EntityYaw(tile_entity,True))*552.0*RoomScale),MT_HEIGHT+(240.0*RoomScale),r\z+(y*2.0)+(Sin(EntityYaw(tile_entity,True))*552.0*RoomScale)
						If r\RoomDoors[1]=Null Then
							r\RoomDoors[1]=dr
							r\Objects[3]=tempInt2
							PositionEntity r\Objects[0],r\x+x*2.0,MT_HEIGHT,r\z+y*2.0,True
							DebugLog "Created door 1 successfully!"
						ElseIf r\RoomDoors[1]<>Null And r\RoomDoors[3]=Null Then
							r\RoomDoors[3]=dr
							r\Objects[5]=tempInt2
							PositionEntity r\Objects[1],r\x+x*2.0,MT_HEIGHT,r\z+y*2.0,True
							DebugLog "Created door 2 successfully!"
						EndIf
					Case ROOM4+2
						AddLight%(Null, r\x+x*2.0-(Sin(EntityYaw(tile_entity,True))*504.0*RoomScale)+(Cos(EntityYaw(tile_entity,True))*16.0*RoomScale), MT_HEIGHT+(396.0*RoomScale), r\z+y*2.0+(Cos(EntityYaw(tile_entity,True))*504.0*RoomScale)+(Sin(EntityYaw(tile_entity,True))*16.0*RoomScale), 2, 500.0 * RoomScale, 255, 200, 200)
						it = CreateItem("scp500",r\x+x*2.0+(Cos(EntityYaw(tile_entity,True))*(-208.0)*RoomScale)-(Sin(EntityYaw(tile_entity,True))*1226.0*RoomScale),MT_HEIGHT+(80.0*RoomScale),r\z+y*2.0+(Sin(EntityYaw(tile_entity,True))*(-208.0)*RoomScale)+(Cos(EntityYaw(tile_entity,True))*1226.0*RoomScale))
						
						it = CreateItem("nvgoggles",r\x+x*2.0-(Sin(EntityYaw(tile_entity,True))*504.0*RoomScale)+(Cos(EntityYaw(tile_entity,True))*16.0*RoomScale), MT_HEIGHT+(80.0*RoomScale), r\z+y*2.0+(Cos(EntityYaw(tile_entity,True))*504.0*RoomScale)+(Sin(EntityYaw(tile_entity,True))*16.0*RoomScale))
				End Select
				
				r\grid\Entities[x+(y*gridsz)]=tile_entity
				Local wayp.WayPoints = CreateWaypoint(r\x+(x*2.0),MT_HEIGHT+0.2,r\z+(y*2.0),Null,r)
				r\grid\waypoints[x+(y*gridsz)]=wayp
				
				Local dist
				If y<gridsz-1 Then
					If r\grid\waypoints[x+((y+1)*gridsz)]<>Null Then
						dist=EntityDistance(r\grid\waypoints[x+(y*gridsz)]\obj,r\grid\waypoints[x+((y+1)*gridsz)]\obj)
						For i=0 To 3
							If r\grid\waypoints[x+(y*gridsz)]\connected[i]=r\grid\waypoints[x+((y+1)*gridsz)] Then
								Exit
							ElseIf r\grid\waypoints[x+(y*gridsz)]\connected[i]=Null Then
								r\grid\waypoints[x+(y*gridsz)]\connected[i]=r\grid\waypoints[x+((y+1)*gridsz)]
								r\grid\waypoints[x+(y*gridsz)]\dist[i]=dist
								Exit
							EndIf
						Next
						For i=0 To 3
							If r\grid\waypoints[x+((y+1)*gridsz)]\connected[i]=r\grid\waypoints[x+(y*gridsz)] Then
								Exit
							ElseIf r\grid\waypoints[x+((y+1)*gridsz)]\connected[i]=Null Then
								r\grid\waypoints[x+((y+1)*gridsz)]\connected[i]=r\grid\waypoints[x+(y*gridsz)]
								r\grid\waypoints[x+((y+1)*gridsz)]\dist[i]=dist
								Exit
							EndIf
						Next
					EndIf
				EndIf
				If y>0 Then
					If r\grid\waypoints[x+((y-1)*gridsz)]<>Null Then
						dist=EntityDistance(r\grid\waypoints[x+(y*gridsz)]\obj,r\grid\waypoints[x+((y-1)*gridsz)]\obj)
						For i=0 To 3
							If r\grid\waypoints[x+(y*gridsz)]\connected[i]=r\grid\waypoints[x+((y-1)*gridsz)] Then
								Exit
							ElseIf r\grid\waypoints[x+(y*gridsz)]\connected[i]=Null Then
								r\grid\waypoints[x+(y*gridsz)]\connected[i]=r\grid\waypoints[x+((y-1)*gridsz)]
								r\grid\waypoints[x+(y*gridsz)]\dist[i]=dist
								Exit
							EndIf
						Next
						For i=0 To 3
							If r\grid\waypoints[x+((y-1)*gridsz)]\connected[i]=r\grid\waypoints[x+(y*gridsz)] Then
								Exit
							ElseIf r\grid\waypoints[x+(y*gridsz)]\connected[i]=Null Then
								r\grid\waypoints[x+((y-1)*gridsz)]\connected[i]=r\grid\waypoints[x+(y*gridsz)]
								r\grid\waypoints[x+((y-1)*gridsz)]\dist[i]=dist
								Exit
							EndIf
						Next
					EndIf
				EndIf
				If x>0 Then
					If r\grid\waypoints[x-1+(y*gridsz)]<>Null Then
						dist=EntityDistance(r\grid\waypoints[x+(y*gridsz)]\obj,r\grid\waypoints[x-1+(y*gridsz)]\obj)
						For i=0 To 3
							If r\grid\waypoints[x+(y*gridsz)]\connected[i]=r\grid\waypoints[x-1+(y*gridsz)] Then
								Exit
							ElseIf r\grid\waypoints[x+(y*gridsz)]\connected[i]=Null Then
								r\grid\waypoints[x+(y*gridsz)]\connected[i]=r\grid\waypoints[x-1+(y*gridsz)]
								r\grid\waypoints[x+(y*gridsz)]\dist[i]=dist
								Exit
							EndIf
						Next
						For i=0 To 3
							If r\grid\waypoints[x-1+(y*gridsz)]\connected[i]=r\grid\waypoints[x+(y*gridsz)] Then
								Exit
							ElseIf r\grid\waypoints[x+(y*gridsz)]\connected[i]=Null Then
								r\grid\waypoints[x-1+(y*gridsz)]\connected[i]=r\grid\waypoints[x+(y*gridsz)]
								r\grid\waypoints[x-1+(y*gridsz)]\dist[i]=dist
								Exit
							EndIf
						Next
					EndIf
				EndIf
				If x<gridsz-1 Then
					If r\grid\waypoints[x+1+(y*gridsz)]<>Null Then
						dist=EntityDistance(r\grid\waypoints[x+(y*gridsz)]\obj,r\grid\waypoints[x+1+(y*gridsz)]\obj)
						For i=0 To 3
							If r\grid\waypoints[x+(y*gridsz)]\connected[i]=r\grid\waypoints[x+1+(y*gridsz)] Then
								Exit
							ElseIf r\grid\waypoints[x+(y*gridsz)]\connected[i]=Null Then
								r\grid\waypoints[x+(y*gridsz)]\connected[i]=r\grid\waypoints[x+1+(y*gridsz)]
								r\grid\waypoints[x+(y*gridsz)]\dist[i]=dist
								Exit
							EndIf
						Next
						For i=0 To 3
							If r\grid\waypoints[x+1+(y*gridsz)]\connected[i]=r\grid\waypoints[x+(y*gridsz)] Then
								Exit
							ElseIf r\grid\waypoints[x+(y*gridsz)]\connected[i]=Null Then
								r\grid\waypoints[x+1+(y*gridsz)]\connected[i]=r\grid\waypoints[x+(y*gridsz)]
								r\grid\waypoints[x+1+(y*gridsz)]\dist[i]=dist
								Exit
							EndIf
						Next
					EndIf
				EndIf
			EndIf
		Next
	Next
	
	For i=0 To 6
		r\grid\Meshes[i]=Meshes[i]
	Next
	
End Function

Function CreateRoom.Rooms(zone%, roomshape%, x#, y#, z#, angle%, name$)
	CatchErrors("Uncaught (CreateRoom)")
	Local r.Rooms = New Rooms
	Local rt.RoomTemplates
	
	r\zone = zone
	
	r\x = x : r\y = y : r\z = z
	
	If name <> "" Then
		name = Lower(name)
		For rt.RoomTemplates = Each RoomTemplates
			If rt\Name = name Then
				r\RoomTemplate = rt
				
				If rt\obj=0 Then LoadRoomMesh(rt)
				
				r\obj = CopyEntity(rt\obj)
				ScaleEntity(r\obj, RoomScale, RoomScale, RoomScale)
				EntityType(r\obj, HIT_MAP)
				EntityPickMode(r\obj, 2)
				
				PositionEntity(r\obj, x, y, z)
				FillRoom(r)
				
				If r\RoomTemplate\UseLightCones
					AddLightCones(r)
				EndIf
				
				r\angle = angle
				If angle <> 0 Then TurnEntity(r\obj, 0, angle, 0)
				CalculateRoomExtents(r)
				Return r
			EndIf
		Next
		RuntimeErrorExt("Room " + Chr(34) + name + Chr(34) + " not found!")
	EndIf
	
	Local temp% = 0
	For rt.RoomTemplates = Each RoomTemplates
		
		For i = 1 To ZONEAMOUNT
			If rt\zone[i-1]=zone Then 
				If rt\Shape = roomshape Then temp=temp+rt\Commonness : Exit
			EndIf
		Next
		
	Next
	
	Local RandomRoom% = Rand(temp)
	temp = 0
	For rt.RoomTemplates = Each RoomTemplates
		For i = 1 To ZONEAMOUNT
			If rt\zone[i-1]=zone And rt\Shape = roomshape Then
				temp=temp+rt\Commonness
				If RandomRoom > temp - rt\Commonness And RandomRoom <= temp Then
					r\RoomTemplate = rt
					
					If rt\obj=0 Then LoadRoomMesh(rt)
					
					r\obj = CopyEntity(rt\obj)
					ScaleEntity(r\obj, RoomScale, RoomScale, RoomScale)
					EntityType(r\obj, HIT_MAP)
					EntityPickMode(r\obj, 2)
					
					PositionEntity(r\obj, x, y, z)
					FillRoom(r)
					
					If r\RoomTemplate\UseLightCones
						AddLightCones(r)
					EndIf
					
					r\angle = angle
					If angle <> 0 Then TurnEntity(r\obj, 0, angle, 0)
					CalculateRoomExtents(r)
					Return r
				End If
			EndIf
		Next
	Next
	
	Delete r
	CatchErrors("CreateRoom")
End Function

; This must be called after the room angle has been finalized!
Function SetupTriggerBoxes(r.Rooms)
	Local sx#, sy#, sz#
	Local pxmin#, pxmax#
	Local pzmin#, pzmax#
	Local tb.Triggerboxes = r\FirstTriggerbox
	
	While tb <> Null
		sx = EntityScaleX(tb\obj, 1)
		sy = Max(EntityScaleY(tb\obj, 1), 0.001)
		sz = EntityScaleZ(tb\obj, 1)
		
		GetMeshExtents(tb\obj)
		
		pxmin = Cos(r\angle) * sx*Mesh_MinX - Sin(r\angle) * sz*Mesh_MinZ + r\x
		pzmin = Sin(r\angle) * sx*Mesh_MinX + Cos(r\angle) * sz*Mesh_MinZ + r\z
		
		pxmax = Cos(r\angle) * sx*Mesh_MaxX - Sin(r\angle) * sz*Mesh_MaxZ + r\x
		pzmax = Sin(r\angle) * sx*Mesh_MaxX + Cos(r\angle) * sz*Mesh_MaxZ + r\z
		
		If pxmin > pxmax Then
			tb\MinX = pxmax
			tb\MaxX = pxmin
		Else
			tb\MinX = pxmin
			tb\MaxX = pxmax
		EndIf
		
		If pzmin > pzmax Then
			tb\MinZ = pzmax
			tb\MaxZ = pzmin
		Else
			tb\MinZ = pzmin
			tb\MaxZ = pzmax
		EndIf
		
		tb\MinY = ((sy*Mesh_MinY)+r\y)
		tb\MaxY = ((sy*Mesh_MaxY)+r\y)
		
		tb = tb\Successor
	Wend
End Function

Function FillRoom(r.Rooms)
	Local lt.LightTemplates
	For lt = Each LightTemplates
		If lt\roomtemplate = r\RoomTemplate Then
			AddLight(r, r\x + lt\x, r\y + lt\y + 0.04, r\z + lt\z, lt\ltype, lt\range, lt\r, lt\g, lt\b)
		EndIf
	Next

	If r\MaxLights = 0 Then
		AddLight(r, r\x, r\y + r\RoomTemplate\MaxY * RoomScale - 0.10, r\z, 2, 6.0, 180, 180, 180)
	EndIf
End Function

Function SnapForward(entity%, dist#)
	If EntityPick(entity, dist) Then
		PositionEntity entity, PickedX(), PickedY(), PickedZ(), True
	EndIf
End Function

Function UpdateRooms()
	CatchErrors("Uncaught (UpdateRooms)")
	Local dist#, i%, j%, r.Rooms
	
	Local x#,z#
	
	;The reason why it is like this:
	;	When the map gets spawned by a seed, it starts from LCZ to HCZ to EZ (bottom to top)
	;	A map loaded by the map creator starts from EZ to HCZ to LCZ (top to bottom) and that's why this little code thing with the (SelectedMap="") needs to be there
	;	- ENDSHN
	If (EntityZ(Collider)/2.0)<I_Zone\Transition[1]-(SelectedMap=-1) Then
		PlayerZone=2
	ElseIf (EntityZ(Collider)/2.0)>=I_Zone\Transition[1]-(SelectedMap=-1) And (EntityZ(Collider)/2.0)<I_Zone\Transition[0]-(SelectedMap=-1) Then
		PlayerZone=1
	Else
		PlayerZone=0
	EndIf
	
	TempLightVolume=0
	Local foundNewPlayerRoom% = PlayerRoom<>Null And IsInRoom(PlayerRoom, EntityX(Collider), EntityZ(Collider))
	If PlayerRoom<>Null And (Not foundNewPlayerRoom) Then
		If Abs(EntityY(Collider) - EntityY(PlayerRoom\obj)) < 1.5 Then
			x = Abs(PlayerRoom\x-EntityX(Collider,True))
			If x < 2.0 Then
				z = Abs(PlayerRoom\z-EntityZ(Collider,True))
				If z < 2.0 Then
					foundNewPlayerRoom = True
				EndIf
			EndIf
			
			If foundNewPlayerRoom = False Then ;it's likely that an adjacent room is the new player room, check for that
				For i=0 To 3
					If PlayerRoom\Adjacent[i]<>Null Then
						x = Abs(PlayerRoom\Adjacent[i]\x-EntityX(Collider,True))
						If x < 2.0 Then
							z = Abs(PlayerRoom\Adjacent[i]\z-EntityZ(Collider,True))
							If z < 2.0 Then
								foundNewPlayerRoom = True
								PlayerRoom = PlayerRoom\Adjacent[i]
								Exit
							EndIf
						EndIf
					EndIf
				Next
			EndIf
		Else
			foundNewPlayerRoom = True ;PlayerRoom stays the same when you're high up, or deep down
		EndIf
	EndIf
	
	For r.Rooms = Each Rooms
		
		x = Abs(r\x-EntityX(Collider,True))
		z = Abs(r\z-EntityZ(Collider,True))
		r\dist = Max(x,z)
		
		
		If x<16 And z < 16 Then
			For i = 0 To MaxRoomEmitters-1
				If r\SoundEmitter[i]<>0 Then 
					dist# = EntityDistance(r\SoundEmitterObj[i],Collider)
					If dist < r\SoundEmitterRange[i] Then
						r\SoundEmitterCHN[i] = LoopSound2(RoomAmbience[r\SoundEmitter[i]],r\SoundEmitterCHN[i], Camera, r\SoundEmitterObj[i],r\SoundEmitterRange[i])
					EndIf
				EndIf
			Next
			
			If (Not foundNewPlayerRoom) And (PlayerRoom<>r) Then				
				If x < 2.0 Then
					If z < 2.0 Then
						If Abs(EntityY(Collider) - EntityY(r\obj)) < 1.5 Then PlayerRoom = r
						foundNewPlayerRoom = True
					EndIf
				EndIf				
			EndIf
		EndIf
		
		Local visible% = False
		If r = PlayerRoom Then visible = True
		If (Not visible) And IsRoomAdjacent(PlayerRoom, r) Then visible = True
		If (Not visible) And r\dist < 16.0 Then
			If EntityInView(GetChild(r\obj, 2), Camera) Then visible = True
		EndIf

		If visible Then
			ShowEntity r\obj
			For i = 0 To MaxRoomLights-1
				If r\Lights[i] <> 0 Then
					dist = EntityDistance(Collider,r\Lights[i])
					If dist < HideDistance Then
						TempLightVolume = TempLightVolume + r\LightIntensity[i]*r\LightIntensity[i]*((HideDistance-dist)/HideDistance)
					EndIf
				Else
					Exit
				EndIf
			Next
		Else
			HideEntity r\obj
		EndIf
	Next
	
	Local mapX = Floor(EntityX(PlayerRoom\obj) / spacing), mapZ = Floor(EntityZ(PlayerRoom\obj) / spacing)
	If mapX >= 0 And mapZ >= 0 And mapX <= MapWidth And mapZ <= MapHeight Then MapFound(mapX, mapZ) = 1
	PlayerRoom\found = True
	
	TempLightVolume = Max(TempLightVolume / 4.5, 1.0)
	
	CatchErrors("UpdateErrors")
End Function

Function IsRoomAdjacent(this.Rooms,that.Rooms)
	If this=Null Then Return False
	If this=that Then Return True
	For i=0 To 3
		If that=this\Adjacent[i] Then Return True
	Next
	Return False
End Function

;-------------------------------------------------------------------------------------------------------

Global LightVolume#, TempLightVolume#
Function AddLight%(room.Rooms, x#, y#, z#, ltype%, range#, r%, g%, b%)
	Local i
	
	If room<>Null Then
		For i = 0 To MaxRoomLights-1
			If room\Lights[i]=0 Then
				room\Lights[i] = CreateLight(ltype)
				;room\LightDist[i] = range
				LightRange(room\Lights[i],range)
				LightColor(room\Lights[i],r,g,b)
				PositionEntity(room\Lights[i],x,y,z,True)
				EntityParent(room\Lights[i],room\obj)
				
				room\LightIntensity[i] = (r+g+b)/255.0/3.0
				
				room\LightSprites[i]= CreateSprite()
				PositionEntity(room\LightSprites[i], x, y, z)
				ScaleSprite(room\LightSprites[i], 0.13 , 0.13)
				EntityTexture(room\LightSprites[i], LightSpriteTex(0))
				EntityBlend (room\LightSprites[i], 3)
				
				EntityParent(room\LightSprites[i], room\obj)
				
				room\LightSpritesPivot[i] = CreatePivot()
				EntityRadius room\LightSpritesPivot[i],0.05
				PositionEntity(room\LightSpritesPivot[i], x, y, z)
				EntityParent(room\LightSpritesPivot[i], room\obj)
				
				room\LightSprites2[i] = CreateSprite()
				PositionEntity(room\LightSprites2[i], x, y, z)
				ScaleSprite(room\LightSprites2[i], 0.6, 0.6)
				EntityTexture(room\LightSprites2[i], LightSpriteTex(2))
				EntityBlend(room\LightSprites2[i], 3)
				EntityOrder(room\LightSprites2[i], -1)
				EntityColor(room\LightSprites2[i], r%, g%, b%)
				EntityParent(room\LightSprites2[i], room\obj)
				EntityFX(room\LightSprites2[i],1)
				RotateEntity(room\LightSprites2[i],0,0,Rand(360))
				SpriteViewMode(room\LightSprites2[i],1)
				room\LightSpriteHidden%[i] = True
				HideEntity room\LightSprites2[i]
				room\LightFlicker%[i] = Rand(1,10)
				
				room\LightR[i] = r
				room\LightG[i] = g
				room\LightB[i] = b
				
				HideEntity room\Lights[i]
				
				room\MaxLights% = room\MaxLights% + 1
				
				Return room\Lights[i]
			EndIf
		Next
	Else
		Local light%,sprite%
		light=CreateLight(ltype)
		LightRange(light,range)
		LightColor(light,r,g,b)
		PositionEntity(light,x,y,z,True)
		sprite=CreateSprite()
		PositionEntity(sprite, x, y, z)
		ScaleSprite(sprite, 0.13 , 0.13)
		EntityTexture(sprite, LightSpriteTex(0))
		EntityBlend (sprite, 3)
		Return light
	EndIf
End Function

Type LightTemplates
	Field roomtemplate.RoomTemplates
	Field ltype%
	Field x#, y#, z#
	Field range#
	Field r%, g%, b%
	
	Field pitch#, yaw#
	Field innerconeangle%, outerconeangle#
End Type 

Function AddTempLight.LightTemplates(rt.RoomTemplates, x#, y#, z#, ltype%, range#, r%, g%, b%)
	Local lt.lighttemplates = New LightTemplates
	lt\roomtemplate = rt
	lt\x = x
	lt\y = y
	lt\z = z
	lt\ltype = ltype
	lt\range = range
	lt\r = r
	lt\g = g
	lt\b = b
	
	Return lt
End Function

;-------------------------------------------------------------------------------------------------------

Type TempWayPoints
	Field x#, y#, z#
	Field roomtemplate.RoomTemplates
End Type 

Type WayPoints
	Field obj
	Field door.Doors
	Field room.Rooms
	Field state%
	;Field tempDist#
	;Field tempSteps%
	Field connected.WayPoints[5]
	Field dist#[5]
	
	Field Fcost#, Gcost#, Hcost#
	
	Field parent.WayPoints
End Type

Function CreateWaypoint.WayPoints(x#,y#,z#,door.Doors, room.Rooms)
	
	Local w.waypoints = New WayPoints
	
	If 1 Then
		w\obj = CreatePivot()
		PositionEntity w\obj, x,y,z	
	Else
		w\obj = CreateSprite()
		PositionEntity(w\obj, x, y, z)
		ScaleSprite(w\obj, 0.15 , 0.15)
		EntityTexture(w\obj, LightSpriteTex(0))
		EntityBlend (w\obj, 3)	
	EndIf
	
	EntityParent w\obj, room\obj
	
	w\room = room
	w\door=door
	
	Return w
End Function

Function InitWayPoints(loadingstart,loadingcount#)
	
	Local d.Doors, w.WayPoints, w2.WayPoints, r.Rooms, ClosestRoom.Rooms
	
	Local x#, y#, z#
	
	Local temper = MilliSecs()
	
	Local dist#, dist2#
	
	For d.Doors = Each Doors
		If d\obj <> 0 Then HideEntity d\obj
		If d\obj2 <> 0 Then HideEntity d\obj2	
		If d\frameobj <> 0 Then HideEntity d\frameobj
		
		If d\room = Null Then 
			ClosestRoom.Rooms = Null
			dist# = 30
			For r.Rooms = Each Rooms
				x# = Abs(EntityX(r\obj,True)-EntityX(d\frameobj,True))
				If x < 20.0 Then
					z# = Abs(EntityZ(r\obj,True)-EntityZ(d\frameobj,True))
					If z < 20.0 Then
						dist2 = x*x+z*z
						If dist2 < dist Then
							ClosestRoom = r
							dist = dist2
						EndIf
					EndIf
				EndIf
			Next
		Else
			ClosestRoom = d\room
		EndIf
		
		If (Not d\DisableWaypoint) Then CreateWaypoint(EntityX(d\frameobj, True), EntityY(d\frameobj, True)+0.18, EntityZ(d\frameobj, True), d, ClosestRoom)
	Next
	
	Local amount# = 0
	For w.WayPoints = Each WayPoints
		EntityPickMode w\obj, 1, True
		EntityRadius w\obj, 0.2
		amount=amount+1
	Next
	
	
	;pvt = CreatePivot()
	
	Local number = 0
	Local iter = 0
	For w.WayPoints = Each WayPoints
		
		number = number + 1
		iter = iter + 1
		If iter = 20 Then 
			DrawLoading(loadingstart+Floor((loadingcount/amount)*number)) 
			iter = 0
		EndIf
		
		w2.WayPoints = After(w)
		
		Local canCreateWayPoint% = False
		
		While (w2<>Null)
			
			If (w\room=w2\room Or w\door<>Null Or w2\door<>Null)
				
				dist# = EntityDistance(w\obj, w2\obj);;Sqr(x*x+y*y+z*z)
				
				If w\room\MaxWayPointY# = 0.0 Or w2\room\MaxWayPointY# = 0.0
					canCreateWayPoint = True
				Else
					If Abs(EntityY(w\obj)-EntityY(w2\obj))<=w\room\MaxWayPointY
						canCreateWayPoint = True
					EndIf
				EndIf
				
				If dist < 7.0 Then
					If canCreateWayPoint
						If EntityVisible(w\obj, w2\obj) Then;e=w2\obj Then 
							For i = 0 To 4
								If w\connected[i] = Null Then
									w\connected[i] = w2.WayPoints 
									w\dist[i] = dist
									Exit
								EndIf
							Next
							
							For n = 0 To 4
								If w2\connected[n] = Null Then 
									w2\connected[n] = w.WayPoints 
									w2\dist[n] = dist
									Exit
								EndIf					
							Next
						EndIf
					EndIf	
				EndIf
			EndIf
			w2 = After(w2)
		Wend
		
	Next
	
	;FreeEntity pvt	
	
	For d.Doors = Each Doors
		If d\obj <> 0 Then ShowEntity d\obj
		If d\obj2 <> 0 Then ShowEntity d\obj2	
		If d\frameobj <> 0 Then ShowEntity d\frameobj		
	Next
	
	For w.WayPoints = Each WayPoints
		EntityPickMode w\obj, 0, 0
		EntityRadius w\obj, 0
		
		For i = 0 To 4
			If w\connected[i]<>Null Then 
				Local tline = CreateLine(EntityX(w\obj,True),EntityY(w\obj,True),EntityZ(w\obj,True),EntityX(w\connected[i]\obj,True),EntityY(w\connected[i]\obj,True),EntityZ(w\connected[i]\obj,True))
				EntityColor(tline, 255,0,0)
				EntityParent tline, w\obj
			EndIf
		Next
	Next
	
	DebugLog "InitWaypoints() - "+(MilliSecs()-temper)
	
End Function

Function RemoveWaypoint(w.WayPoints)
	FreeEntity w\obj
	Delete w
End Function


Function FindPath(n.NPCs, x#, y#, z#)
	
	DebugLog "findpath: "+n\NPCtype
	
	Local temp%, dist#, dist2#
	Local xtemp#, ytemp#, ztemp#
	
	Local w.WayPoints, StartPoint.WayPoints, EndPoint.WayPoints   
	
	Local StartX% = Floor(EntityX(n\Collider,True) / spacing + 0.5), StartZ% = Floor(EntityZ(n\Collider,True) / spacing + 0.5)
	
	Local EndX% = Floor(x / spacing + 0.5), EndZ% = Floor(z / spacing + 0.5)
	
	
	Local CurrX, CurrZ

   ;pathstatus = 0, route hasn't been searched for yet
   ;pathstatus = 1, route found
   ;pathstatus = 2, route not found (target unreachable)
	
	For w.WayPoints = Each WayPoints
		w\state = 0
		w\Fcost = 0
		w\Gcost = 0
		w\Hcost = 0
	Next
	
	n\PathStatus = 0
	n\PathLocation = 0
	For i = 0 To 19
		n\Path[i] = Null
	Next
	
	Local pvt = CreatePivot()
	PositionEntity(pvt, x,y,z, True)   
	
	temp = CreatePivot()
	PositionEntity(temp, EntityX(n\Collider,True), EntityY(n\Collider,True)+0.15, EntityZ(n\Collider,True))
	
	dist = 350.0
	For w.WayPoints = Each WayPoints
		xtemp = EntityX(w\obj,True)-EntityX(temp,True)
          ;If xtemp < 8.0 Then
		ztemp = EntityZ(w\obj,True)-EntityZ(temp,True)
             ;If ztemp < 8.0 Then
		ytemp = EntityY(w\obj,True)-EntityY(temp,True)
                ;If ytemp < 8.0 Then
		dist2# = (xtemp*xtemp)+(ytemp*ytemp)+(ztemp*ztemp)
		If dist2 < dist Then 
			;prefer waypoints that are visible
			If Not EntityVisible(w\obj, temp) Then dist2 = dist2*3
			If dist2 < dist Then 
				dist = dist2
				StartPoint = w
			EndIf
		EndIf
                ;EndIf
             ;EndIf
          ;EndIf
	Next
	DebugLog "DIST: "+dist
	
	FreeEntity temp
	
	If StartPoint = Null Then Return 2
	StartPoint\state = 1      
	
       ;If EndPoint = Null Then
	EndPoint = Null
	dist# = 400.0
	For w.WayPoints = Each WayPoints
		xtemp = EntityX(pvt,True)-EntityX(w\obj,True)
          ;If xtemp =< 8.0 Then
		ztemp = EntityZ(pvt,True)-EntityZ(w\obj,True)
             ;If ztemp =< 8 Then
		ytemp = EntityY(pvt,True)-EntityY(w\obj,True)
		dist2# = (xtemp*xtemp)+(ytemp*ytemp)+(ztemp*ztemp)
		
		If dist2 < dist Then ; And EntityVisible(w\obj, pvt)
			dist = dist2
			EndPoint = w
		EndIf            
             ;EndIf
          ;EndIf
	Next
       ;EndIf
	
	FreeEntity pvt
	
	If EndPoint = StartPoint Then
		If dist < 0.4 Then
			Return 0
		Else
			n\Path[0]=EndPoint
			Return 1               
		EndIf
	EndIf
	If EndPoint = Null Then Return 2
	
       ;aloitus- ja lopetuspisteet l�ydetty, aletaan etsi� reitti�
	
	Repeat
		
		temp% = False
		Local smallest.WayPoints = Null
		dist# = 10000.0
		For w.WayPoints = Each WayPoints
			If w\state = 1 Then
                temp = True
                If (w\Fcost) < dist Then
					dist = w\Fcost
					smallest = w
                EndIf
			EndIf
		Next
		
		If smallest <> Null Then
			
			w = smallest
			w\state = 2
			
			For i = 0 To 4
                If w\connected[i]<>Null Then
					If w\connected[i]\state < 2 Then
						
						Local gtemp#
						If w\connected[i]\state=1 Then ;open list
							gtemp# = w\Gcost+w\dist[i]
							If gtemp < w\connected[i]\Gcost Then ;parempi reitti -> overwrite
								w\connected[i]\Gcost = gtemp
								w\connected[i]\Fcost = w\connected[i]\Gcost + w\connected[i]\Hcost
								w\connected[i]\parent = w
							EndIf
						Else
							w\connected[i]\Hcost# = Abs(EntityX(w\connected[i]\obj,True)-EntityX(EndPoint\obj,True))+Abs(EntityZ(w\connected[i]\obj,True)-EntityZ(EndPoint\obj,True))
							gtemp# = w\Gcost+w\dist[i]
							w\connected[i]\Gcost = gtemp
							w\connected[i]\Fcost = w\Gcost+w\Hcost
							w\connected[i]\parent = w
							w\connected[i]\state=1
						EndIf            
					EndIf
					
                EndIf
			Next
		Else ;open listilt� ei l�ytynyt mit��n
			If EndPoint\state > 0 Then
                StartPoint\parent = Null
                EndPoint\state = 2
                Exit
			EndIf
		EndIf
		
		If EndPoint\state > 0 Then
			StartPoint\parent = Null
			EndPoint\state = 2
			Exit
		EndIf
		
	Until temp = False
	
	If EndPoint\state > 0 Then
		
		Local currpoint.WayPoints = EndPoint
		Local twentiethpoint.WayPoints = EndPoint
		
		Local length = 0
		Repeat
			length = length +1
			currpoint = currpoint\parent
			If length>20 Then
                twentiethpoint = twentiethpoint\parent
			EndIf
		Until currpoint = Null
		
		currpoint.WayPoints = EndPoint
		While twentiethpoint<>Null
			length=Min(length-1,19)
			twentiethpoint = twentiethpoint\parent
			n\Path[length] = twentiethpoint
		Wend
		
		Return 1
	Else
		DebugLog "FUNCTION FindPath() - no route found"
		Return 2 
	EndIf
	
End Function
Function CreateLine(x1#,y1#,z1#, x2#,y2#,z2#, mesh=0)
	
	Local verts
	Local surf
	If mesh = 0 Then 
		mesh=CreateMesh()
		EntityFX(mesh,16)
		surf=CreateSurface(mesh)	
		verts = 0	
		
		AddVertex surf,x1#,y1#,z1#,0,0
	Else
		surf = GetSurface(mesh,1)
		verts = CountVertices(surf)-1
	End If
	
	AddVertex surf,(x1#+x2#)/2,(y1#+y2#)/2,(z1#+z2#)/2,0,0 
	; you could skip creating the above vertex and change the line below to
	; AddTriangle surf,verts,verts+1,verts+0
	; so your line mesh would use less vertices, the drawback is that some videocards (like the matrox g400)
	; aren't able to create a triangle with 2 vertices. so, it's your call :)
	AddVertex surf,x2#,y2#,z2#,1,0
	
	AddTriangle surf,verts,verts+2,verts+1
	
	Return mesh
End Function

;-------------------------------------------------------------------------------------------------------

Global SelectedScreen.Screens
Type Screens
	Field obj%
	Field imgpath$
	Field img
	Field room.Rooms
End Type

Type TempScreens
	Field imgpath$
	Field x#,y#,z#
	Field roomtemplate.RoomTemplates
End Type

Function CreateScreen.Screens(x#,y#,z#,imgpath$,r.Rooms)
	Local s.screens = New Screens
	s\obj = CreatePivot()
	EntityPickMode(s\obj, 1)	
	EntityRadius s\obj, 0.1
	
	PositionEntity s\obj, x,y,z
	s\imgpath = imgpath
	s\room = r
	EntityParent s\obj, r\obj
	
	Return s
End Function

Function UpdateScreens()
	If SelectedScreen <> Null Then Return
	If SelectedDoor <> Null Then Return
	
	For s.screens = Each Screens
		If s\room = PlayerRoom Then
			If EntityDistance(Collider,s\obj)<1.2 Then
				EntityPick(Camera, 1.2)
				If PickedEntity()=s\obj And s\imgpath<>"" Then
					DrawHandIcon=True
					If MouseUp1 Then 
						SelectedScreen=s
						SelectedItem = Null
						s\img = LoadImage_Strict("GFX\screens\"+s\imgpath, UIScale)
						PlaySound_Strict ButtonSFX
						MouseUp1=False
					EndIf
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Dim MapName$(0, 0)
Dim MapRoomID%(ROOM4 + 1)
Dim MapRoom$(ROOM4 + 1, 0)

Dim RoomAmounts(ROOM4 + 1, ZONEAMOUNT)
Dim MinPositions(ROOM4 + 1, 0)
Dim MaxPositions(ROOM4 + 1, 0)

;-------------------------------------------------------------------------------------------------------


Dim GorePics%(10)
Global SelectedMonitor.SecurityCams
Global CoffinCam.SecurityCams
Type SecurityCams
	Field obj%, MonitorObj%
	
	Field BaseObj%, CameraObj%
	
	Field ScrObj%, ScrWidth#, ScrHeight#
	Field Screen%, Cam%, ScrTexture%, ScrOverlay%
	Field angle#, turn#, CurrAngle#
	Field State#, PlayerState%
	
	Field soundCHN%
	
	Field InSight%
	
	Field RenderInterval#
	
	Field room.Rooms
	
	Field FollowPlayer%
	Field CoffinEffect%
	
	Field AllowSaving%
	
	Field MinAngle#, MaxAngle#, dir%
End Type

Global ScreenTexs%[3]

Global CurrRoom2slRenderCam%
Global Room2slCam%

Function CreateSecurityCam.SecurityCams(x#, y#, z#, r.Rooms, screen% = False)
	Local sc.SecurityCams = New SecurityCams
	
	sc\obj = CopyEntity(CamBaseOBJ)
	ScaleEntity(sc\obj, 0.0015, 0.0015, 0.0015)
	sc\CameraObj = CopyEntity(CamOBJ)
	ScaleEntity(sc\CameraObj, 0.01, 0.01, 0.01)
	
	sc\room = r
	
	sc\Screen = screen
	If screen Then
		sc\AllowSaving = True
		
		sc\RenderInterval = 12
		
		Local scale# = RoomScale * 4.5 * 0.4
		
		sc\ScrObj = CreateSprite()
		EntityFX sc\ScrObj, 17
		SpriteViewMode(sc\ScrObj, 2)
		sc\ScrTexture = 0
		EntityTexture sc\ScrObj, ScreenTexs[sc\ScrTexture]
		ScaleSprite(sc\ScrObj, MeshWidth(Monitor) * scale * 0.95 * 0.5, MeshHeight(Monitor) * scale * 0.95 * 0.5)
		
		sc\ScrOverlay = CreateSprite(sc\ScrObj)
		;	scaleSprite(sc\scrOverlay , 0.5, 0.4)
		ScaleSprite(sc\ScrOverlay, MeshWidth(Monitor) * scale * 0.95 * 0.5, MeshHeight(Monitor) * scale * 0.95 * 0.5)
		MoveEntity(sc\ScrOverlay, 0, 0, -0.0005)
		EntityTexture(sc\ScrOverlay, MonitorTexture)
		SpriteViewMode(sc\ScrOverlay, 2)
		EntityBlend(sc\ScrOverlay , 3)
		
		sc\MonitorObj = CopyEntity(Monitor, sc\ScrObj)
		
		ScaleEntity(sc\MonitorObj, scale, scale, scale)
		
		sc\Cam = CreateCamera()
		CameraViewport(sc\Cam, 0, 0, 512, 512)
		CameraRange sc\Cam, 0.05, 8.0 ;6.0
		CameraZoom(sc\Cam, 0.8)
		HideEntity(sc\Cam)	
	End If
	
	PositionEntity(sc\obj, x, y, z)
	
	If r<>Null Then EntityParent(sc\obj, r\obj)
	
	Return sc
End Function



Function UpdateLever(obj, locked=False)
	
	Local dist# = EntityDistance(Camera, obj)
	If dist < 8.0 Then 
		If dist < 0.8 And (Not locked) Then 
			If EntityInView(obj, Camera) Then 
				
				EntityPick(Camera, 0.65)
				
				If PickedEntity() = obj Then
					DrawHandIcon = True
					If MouseHit1 Then GrabbedEntity = obj
				End If
				
				Local prevpitch# = EntityPitch(obj)
				
				If (MouseDown1 Or MouseHit1) Then
					If GrabbedEntity <> 0 Then
						If GrabbedEntity = obj Then
							DrawHandIcon = True 
							;TurnEntity(obj, , 0, 0)
							RotateEntity(GrabbedEntity, Max(Min(EntityPitch(obj)+Max(Min(mouse_y_speed_1 * 8,30.0),-30), 80), -80), EntityYaw(obj), 0)
							
							DrawArrowIcon(0) = True
							DrawArrowIcon(2) = True
							
						EndIf
					EndIf
				EndIf 
				
				If EntityPitch(obj,True) > 75 Then ;p��ll�
					If prevpitch =< 75 Then PlaySound2(LeverSFX, Camera, obj, 1.0)
				ElseIf EntityPitch(obj,True) < -75 ;pois p��lt�
					If prevpitch => -75 Then PlaySound2(LeverSFX, Camera, obj, 1.0)	
				EndIf						
			EndIf
		EndIf
		
		If MouseDown1=False And MouseHit1=False Then 
			If EntityPitch(obj,True) > 0 Then
				RotateEntity(obj, CurveValue(80, EntityPitch(obj), 10), EntityYaw(obj), 0)
			Else
				RotateEntity(obj, CurveValue(-80, EntityPitch(obj), 10), EntityYaw(obj), 0)
			EndIf
			GrabbedEntity = 0
		End If
		
	EndIf
	
	If EntityPitch(obj,True) > 0 Then ;p��ll�
		Return True
	Else ;pois p��lt�
		Return False
	EndIf	
	
End Function

Function UpdateButton(obj)
	
	Local dist# = EntityDistance(Collider, obj);entityDistance(collider, d\buttons[i])
	If dist < 0.8 Then
		Local temp% = CreatePivot()
		PositionEntity temp, EntityX(Camera), EntityY(Camera), EntityZ(Camera)
		PointEntity temp,obj
		
		If EntityPick(temp, 0.65) = obj Then
			If ClosestButton = 0 Then 
				ClosestButton = obj
			Else
				If dist < EntityDistance(Collider, ClosestButton) Then ClosestButton = obj
			End If							
		End If
		
		FreeEntity temp
	EndIf			
	
End Function

Function IsInElevator(entity%, elevator%)
	Return Abs(EntityX(entity, True)-EntityX(elevator,True))<280.0*RoomScale+(0.015*FPSfactor) And Abs(EntityZ(entity,True)-EntityZ(elevator,True))<280.0*RoomScale+(0.015*FPSfactor) And Abs(EntityY(entity,True)-EntityY(elevator,True))<280.0*RoomScale+(0.015*FPSfactor)
End Function


;-------------------------------------------------------------------------------------------------------

Type Props
	Field file$
	Field obj
End Type

Function CreatePropObj(file$)
	Local p.Props
	For p.Props = Each Props
		If p\file = file Then
			Return CopyEntity(p\obj)
		EndIf
	Next
	
	p.Props = New Props
	p\file = file
	p\obj = LoadModdedMeshNonStrict("GFX\Map\Props\"+file)
	If p\obj = 0 Then p\obj = LoadModdedMeshNonStrict("GFX\Map\"+file)
	If p\obj = 0 Then RuntimeErrorExt("Failed to load prop " + file + ".")
	HideEntity p\obj
	Return CopyEntity(p\obj)
End Function

;-------------------------------------------------------------------------------------------------------


Function CreateMapLayout()
	Local x% = Floor(MapWidth / 2)
	Local y% = MapHeight - 2;Rand(3, 5)
	
	For i = y To MapHeight - 1
		MapTemp(x, i) = True
	Next
	
	Repeat
		Local width% = Rand(Floor(MapWidth*0.6), Floor(MapWidth*0.85))
		
		If x > MapWidth*0.6 Then
			width = -width
		ElseIf x > MapWidth*0.4
			x = x-width/2
		EndIf
		
		;make sure the hallway doesn't go outside the array
		If x+width > MapWidth-3 Then
			;x = -width+MapWidth-4
			
			width=MapWidth-3-x
		ElseIf x+width < 2
			
			;x = 3-width
			width=-x+2
		EndIf
		
		x = Min(x, x + width)
		width = Abs(width)
		For i = x To x + width
			MapTemp(Min(i,MapWidth), y) = True
		Next
		
		Local height% = Rand(3, 4)
		If y - height < 1 Then height = y-1
		
		Local yhallways% = Rand(4,5)
		
		If GetZone(y-height)<>GetZone(y-height+1) Then height=height-1
		
		Local temp
		For i = 1 To yhallways
			
			Local x2% = Max(Min(Rand(x, x + width-1),MapWidth-2),2)
			While MapTemp(x2, y - 1) Or MapTemp(x2 - 1, y - 1) Or MapTemp(x2 + 1, y - 1)
				x2=x2+1
			Wend
			
			If x2<x+width Then
				Local tempheight
				If i = 1 Then
					tempheight = height 
					If Rand(2)=1 Then x2 = x Else x2 = x+width
				Else
					tempheight = Rand(1,height)
				EndIf
				
				For y2 = y - tempheight To y
					If GetZone(y2)<>GetZone(y2+1) Then ;a room leading from zone to another
						MapTemp(x2, y2) = 255
					Else
						MapTemp(x2, y2) = True
					EndIf
				Next
				
				If tempheight = height Then temp = x2
			End If
			
		Next
		
		x = temp
		y = y - height
	Until y < 2
End Function

Function CountRooms()
	Dim RoomAmounts(ROOM4 + 1, ZONEAMOUNT)

	For y = 1 To MapHeight - 1
		Local zone% = GetZone(y)
		
		For x = 1 To MapWidth - 1
			If MapTemp(x, y) > 0 Then
				Local temp = Min(MapTemp(x + 1, y),1) + Min(MapTemp(x - 1, y),1)
				temp = temp + Min(MapTemp(x, y + 1),1) + Min(MapTemp(x, y - 1),1)			
				If MapTemp(x,y)<255 Then MapTemp(x, y) = temp
				Select MapTemp(x,y)
					Case 1
						RoomAmounts(ROOM1, zone)=RoomAmounts(ROOM1, zone)+1
					Case 2
						If Min(MapTemp(x + 1, y),1) + Min(MapTemp(x - 1, y),1)= 2 Then
							RoomAmounts(ROOM2, zone)=RoomAmounts(ROOM2, zone)+1	
						ElseIf Min(MapTemp(x, y + 1),1) + Min(MapTemp(x , y - 1),1)= 2
							RoomAmounts(ROOM2, zone)=RoomAmounts(ROOM2, zone)+1	
						Else
							RoomAmounts(ROOM2C, zone) = RoomAmounts(ROOM2C, zone)+1
						EndIf
					Case 3
						RoomAmounts(ROOM3, zone)=RoomAmounts(ROOM3, zone)+1
					Case 4
						RoomAmounts(ROOM4, zone)=RoomAmounts(ROOM4, zone)+1
				End Select
			EndIf
		Next
	Next
End Function

Function ForceRooms()
	Local y_min%, y_max%, x_min%, x_max%

	Local forceRoom1 = GetModdedINIInt(MapOptions, "facility", "force room1")

	;force more room1s (if needed)
	For i = 0 To 2
		Local temp = -RoomAmounts(ROOM1, i)+forceRoom1
		
		If temp > 0 Then
			
			If i = 2 Then y_min = 1 Else y_min = I_Zone\Transition[i]
			If i = 0 Then y_max = MapHeight - 2 Else y_max = I_Zone\Transition[i - 1] - 1
			x_min = 1
			x_max = MapWidth - 2
			
			For y = y_min To y_max
				For x = x_min To x_max
					If MapTemp(x, y) = 0 Then
						
						If (Min(MapTemp(x + 1, y),1) + Min(MapTemp(x - 1, y),1) + Min(MapTemp(x, y + 1),1) + Min(MapTemp(x, y - 1),1)) = 1 Then
							;If Rand(4)=1 Then
							
							Local x2, y2
							If MapTemp(x + 1, y) Then
								x2 = x+1 : y2 = y
							ElseIf MapTemp(x - 1, y)
								x2 = x-1 : y2 = y
							ElseIf MapTemp(x, y+1)
								x2 = x : y2 = y+1	
							ElseIf MapTemp(x, y-1)
								x2 = x : y2 = y-1
							EndIf
							
							Local placed = False
							If MapTemp(x2,y2)>1 And MapTemp(x2,y2)<4 And (y<y_max Or y2<y Or i=0) Then
								Select MapTemp(x2,y2)
									Case 2
										If Min(MapTemp(x2 + 1, y2),1) + Min(MapTemp(x2 - 1, y2),1)= 2 Then
											RoomAmounts(ROOM2, i)=RoomAmounts(ROOM2, i)-1
											RoomAmounts(ROOM3, i)=RoomAmounts(ROOM3, i)+1
											placed = True
										ElseIf Min(MapTemp(x2, y2 + 1),1) + Min(MapTemp(x2, y2 - 1),1)= 2
											RoomAmounts(ROOM2, i)=RoomAmounts(ROOM2, i)-1
											RoomAmounts(ROOM3, i)=RoomAmounts(ROOM3, i)+1
											placed = True
										EndIf
									Case 3
										RoomAmounts(ROOM3, i)=RoomAmounts(ROOM3, i)-1
										RoomAmounts(ROOM4, i)=RoomAmounts(ROOM4, i)+1	
										placed = True
								End Select
								
								If placed Then
									MapTemp(x2,y2)=MapTemp(x2,y2)+1
									
									MapTemp(x, y) = 1
									RoomAmounts(ROOM1, i) = RoomAmounts(ROOM1, i)+1	
									
									temp=temp-1
								EndIf
							EndIf
						EndIf
						
					EndIf
					If temp = 0 Then Exit
				Next
				If temp = 0 Then Exit
			Next
		EndIf
	Next
	
	Local forceRoom4 = GetModdedINIInt(MapOptions, "facility", "force room4")
	Local forceRoom2c = GetModdedINIInt(MapOptions, "facility", "force room2c")

	;force more room4s and room2Cs
	For i = 0 To 2
		
		If i = 2 Then y_min = 1 Else y_min = I_Zone\Transition[i]
		If i = 0 Then y_max = MapHeight - 2 Else y_max = I_Zone\Transition[i - 1] - 2
		x_min = 1
		x_max = MapWidth - 2
		
		temp = -RoomAmounts(ROOM4, i)+forceRoom4

		If temp > 0 Then
			DebugLog "forcing a ROOM4 into zone "+i
			temp=0
			
			For y = y_min To y_max
				For x = x_min To x_max
					If MapTemp(x,y)=3 Then
						placed=False
						Select 1 ;see if adding a ROOM4 is possible
							Case (x<>x_max And MapTemp(x+1,y)+MapTemp(x+1,y+1)+MapTemp(x+1,y-1)+MapTemp(x+2,y)=0)
								MapTemp(x+1,y)=1
								placed=True
							Case (x<>x_min And MapTemp(x-1,y)+MapTemp(x-1,y+1)+MapTemp(x-1,y-1)+MapTemp(x-2,y)=0)
								MapTemp(x-1,y)=1
								placed=True
							Case ((i<>0 Lor y<>y_max) And MapTemp(x,y+1)+MapTemp(x+1,y+1)+MapTemp(x-1,y+1)+MapTemp(x,y+2)=0)
								MapTemp(x,y+1)=1
								placed=True
							Case ((i=2 Lor y<>y_min) And MapTemp(x,y-1)+MapTemp(x+1,y-1)+MapTemp(x-1,y-1)+MapTemp(x,y-2)=0)
								MapTemp(x,y-1)=1
								placed=True
						End Select
						If placed Then
							MapTemp(x,y)=4 ;turn this room into a ROOM4
							DebugLog "ROOM4 forced into slot ("+x+", "+y+")"
							RoomAmounts(ROOM4, i)=RoomAmounts(ROOM4, i)+1
							RoomAmounts(ROOM3, i)=RoomAmounts(ROOM3, i)-1
							RoomAmounts(ROOM1, i)=RoomAmounts(ROOM1, i)+1
							temp = temp - 1
						EndIf
					EndIf
					If temp=0 Then Exit
				Next
				If temp=0 Then Exit
			Next
			
			If temp>0 Then DebugLog "Couldn't place all ROOM4s in zone "+i
		EndIf
		
		temp = -RoomAmounts(ROOM2C, i)+forceRoom2c

		If temp>0 Then
			DebugLog "forcing a ROOM2C into zone "+i

			For y = y_max To y_min Step -1
				For x = x_min To x_max
					If MapTemp(x,y)=1 Then
						placed=False
						Select True ;see if adding some rooms is possible
							Case MapTemp(x-1,y)>0
								If (MapTemp(x+1,y-1)+MapTemp(x+1,y+1)+MapTemp(x+2,y))=0 And x<x_max Then
									If (MapTempSafe(x+1,y-2)+MapTemp(x+2,y-1))=0 And (y>y_min Or i=2) Then
										MapTemp(x,y)=2
										MapTemp(x+1,y)=2
										DebugLog "ROOM2C forced into slot ("+(x+1)+", "+(y)+")"
										MapTemp(x+1,y-1)=1
										placed=True
									Else If (MapTemp(x+1,y+2)+MapTemp(x+2,y+1))=0 And (y<y_max Or i>0) Then
										MapTemp(x,y)=2
										MapTemp(x+1,y)=2
										DebugLog "ROOM2C forced into slot ("+(x+1)+", "+(y)+")"
										MapTemp(x+1,y+1)=1
										placed=True
									EndIf
								EndIf
							Case MapTemp(x+1,y)>0
								If (x-2<0 Lor MapTemp(x-2,y)=0) And (MapTemp(x-1,y-1)+MapTemp(x-1,y+1))=0 And x>x_min Then
									If (x-2<0 Lor MapTemp(x-2,y-1)=0) And MapTempSafe(x-1,y-2)=0 And (y>y_min Or i=2) Then
										MapTemp(x,y)=2
										MapTemp(x-1,y)=2
										DebugLog "ROOM2C forced into slot ("+(x-1)+", "+(y)+")"
										MapTemp(x-1,y-1)=1
										placed=True
									Else If (x-2<0 Lor MapTemp(x-2,y+1)=0) And MapTemp(x-1,y+2)=0 And (y<y_max Or i>0) Then
										MapTemp(x,y)=2
										MapTemp(x-1,y)=2
										DebugLog "ROOM2C forced into slot ("+(x-1)+", "+(y)+")"
										MapTemp(x-1,y+1)=1
										placed=True
									EndIf
								EndIf
							Case MapTemp(x,y-1)>0
								If (MapTemp(x-1,y+1)+MapTemp(x+1,y+1)+MapTemp(x,y+2))=0 And (y<y_max Or i>0) Then
									If (x-2<0 Lor MapTemp(x-2,y+1)=0) And MapTemp(x-1,y+2)=0 And x>x_min Then
										MapTemp(x,y)=2
										MapTemp(x,y+1)=2
										DebugLog "ROOM2C forced into slot ("+(x)+", "+(y+1)+")"
										MapTemp(x-1,y+1)=1
										placed=True
									Else If (MapTemp(x+2,y+1)+MapTemp(x+1,y+2))=0 And x<x_max Then
										MapTemp(x,y)=2
										MapTemp(x,y+1)=2
										DebugLog "ROOM2C forced into slot ("+(x)+", "+(y+1)+")"
										MapTemp(x+1,y+1)=1
										placed=True
									EndIf
								EndIf
							Case MapTemp(x,y+1)>0
								If (MapTemp(x-1,y-1)+MapTemp(x+1,y-1)+MapTempSafe(x,y-2))=0 And (y>y_min Or i=2) Then
									If (x-2<0 Lor MapTemp(x-2,y-1)=0) And MapTempSafe(x-1,y-2)=0 And x>x_min Then
										MapTemp(x,y)=2
										MapTemp(x,y-1)=2
										DebugLog "ROOM2C forced into slot ("+(x)+", "+(y-1)+")"
										MapTemp(x-1,y-1)=1
										placed=True
									Else If (MapTemp(x+2,y-1)+MapTempSafe(x+1,y-2))=0 And x<x_max Then
										MapTemp(x,y)=2
										MapTemp(x,y-1)=2
										DebugLog "ROOM2C forced into slot ("+(x)+", "+(y-1)+")"
										MapTemp(x+1,y-1)=1
										placed=True
									EndIf
								EndIf
						End Select
						If placed Then
							RoomAmounts(ROOM2C, i)=RoomAmounts(ROOM2C, i)+1
							RoomAmounts(ROOM2, i)=RoomAmounts(ROOM2, i)+1
							temp = temp - 1
						EndIf
					EndIf
					If temp=0 Then Exit
				Next
				If temp=0 Then Exit
			Next
			
			If temp>0 Then DebugLog "Couldn't place all ROOM2C in zone "+i
		EndIf
		
	Next
End Function

Function InitializeRoomRanges()
	Local MaxRooms% = 55*MapWidth/20
	For rs = ROOM1 To ROOM4
		MaxRooms=Max(MaxRooms, RoomAmounts(rs, 0)+RoomAmounts(rs, 1)+RoomAmounts(rs, 2)+1)
	Next
	Dim MapRoom$(ROOM4 + 1, MaxRooms)

	Dim MinPositions%(ROOM4 + 1, ZONEAMOUNT)
	Dim MaxPositions%(ROOM4 + 1, ZONEAMOUNT)
	For rs = ROOM1 To ROOM4
		For z = 0 To ZONEAMOUNT-1
			MinPositions(rs, z) = 0
			MaxPositions(rs, z) = RoomAmounts(rs, 0)
			If z > 0 Then
				MinPositions(rs, z) = MinPositions(rs, z) + RoomAmounts(rs, 0)
				MaxPositions(rs, z) = MaxPositions(rs, z) + RoomAmounts(rs, 1)
			EndIf
			If z > 1 Then
				MinPositions(rs, z) = MinPositions(rs, z) + RoomAmounts(rs, 1)
				MaxPositions(rs, z) = MaxPositions(rs, z) + RoomAmounts(rs, 2)
			EndIf
		Next
	Next
End Function

Function SetRooms()
	MapRoom(ROOM1, 0) = "start"	
	
	MapRoom(ROOM1, MaxPositions(ROOM1, 2)-2) = "exit1"
	MapRoom(ROOM1, MaxPositions(ROOM1, 2)-1) = "gateaentrance"
	
	For rt.RoomTemplates = Each RoomTemplates
		If rt\SetRoom >= 0 Then
			For i% = 0 To ZONEAMOUNT-1
				Local zone% = rt\zone[i]
				If zone <> 0 Then
					zone = zone - 1
					Local start% = MinPositions(rt\Shape, zone)
					SetRoom(rt\Name, rt\Shape, start+Floor(rt\SetRoom*Float(RoomAmounts(rt\Shape, zone))),start,MaxPositions(rt\Shape, zone))
				EndIf
			Next

		EndIf
	Next
End Function


Function MTAdjustAngle%(angle%, shape%)
	If angle <> 90 And angle <> 270 Then angle = angle + 180
	If shape = ROOM2C Or shape = ROOM3 Then angle = angle - 90
	Return WrapAngle(angle)
End Function

Function MTNameToShape%(name$)
	Select Lower(name$)
		Case "maintenance tunnel endroom", "maintenance tunnel generator room"
			Return ROOM1
		Case "maintenance tunnel corridor", "maintenance tunnel elevator"
			Return ROOM2
		Case "maintenance tunnel corner"
			Return ROOM2C
		Case "maintenance tunnel t-shaped room"
			Return ROOM3
		Case "maintenance tunnel 4-way room"
			Return ROOM4
	End Select
	Return ROOM1
End Function

; This is fairly shitty.. but it's the 59 room layout from cbmap2 temporarily, until i find a better solution to map gen
Function CreateMTFloor()
	Local f% = ReadFile("Data\mtlayout.txt")
	If f = 0 Then RuntimeErrorExt "Data\mtlayout.txt not found."

	Local l$, n$, x%, y%, ang%, shape%, zone%
	Local r.Rooms
	MTStartRoom = Null

	While Not Eof(f)
		l$ = Trim(ReadLine(f))
		If l$ <> ""
			x = Int(Piece$(l$, 1, ","))
			y = Int(Piece$(l$, 2, ","))
			n$ = Piece$(l$, 3, ",")
			ang = Int(Piece$(l$, 4, ",")) * 90
			shape = MTNameToShape(n$)
			zone = GetZone(y)

			ang = MTAdjustAngle(ang, shape)

			r = CreateRoom(zone, shape, (MT_GRID_SIZE - x) * spacing, 0, y * spacing, ang, n$)
			If r <> Null Then
				If MTStartRoom = Null Then MTStartRoom = r
				If Lower(n$) = "maintenance tunnel endroom" Then MTStartRoom = r
			EndIf
		EndIf
	Wend

	CloseFile f
End Function

Function CreateRooms(loadingstart, loadingcount#)
	CreateMTFloor()
End Function

Function DrawDebugMap()
	For x = 0 To MapWidth - 1
		For y = 0 To MapHeight - 1
			MapName(x,y) = ""
		Next
	Next
	For r.Rooms = Each Rooms
		If r\RoomTemplate\SetRoom => 0 Then MapName(r\x/8,r\z/8) = r\RoomTemplate\Name
	Next
	
	Repeat
		Cls

		Local tileSize = Max(Min(32 * HUDScale, Min(GraphicWidth / MapWidth, (GraphicHeight - 50) / MapHeight)), 3)
		Local totalWidth = MapWidth * tileSize - 2, totalHeight = MapHeight * tileSize - 2
		Local xStart = GraphicWidth / 2 - totalWidth / 2, yStart = GraphicHeight / 2 - totalHeight / 2 - 50 * HUDScale

		For x = 0 To MapWidth - 1
			For y = 0 To MapHeight - 1
				Local mirroredX% = MapWidth - x
				If MapTemp(x, y) = 0 Then
					
					Local zone=GetZone(y)
					
					Color 25+50*zone, 25+50*zone, 25+50*zone
					Rect(xStart + mirroredX * tileSize, yStart + y * tileSize, tileSize - 2, tileSize - 2)
				Else
					If MapTemp(x, y) = 255 Then
						Color 0,200,0
					Else If MapTemp(x,y)=4 Then
						Color 50,50,255
					Else If MapTemp(x,y)=3 Then
						Color 50,255,255
					Else If MapTemp(x,y)=2 Then
						Color 255,255,50
					Else
						Color 255, 255, 255
					EndIf
					Rect(xStart + mirroredX * tileSize, yStart + y * tileSize, tileSize - 2, tileSize - 2)
				End If
			Next
		Next	
		
		For x = 0 To MapWidth - 1
			For y = 0 To MapHeight - 1
				mirroredX% = MapWidth - x
				If MouseX()>xStart + mirroredX*tileSize And MouseX()<xStart + mirroredX*tileSize+tileSize Then
					If MouseY()>yStart + y*tileSize And MouseY()<yStart + y*tileSize+tileSize Then
						Color 255, 0, 0
					Else
						Color 200, 200, 200
					EndIf
				Else
					Color 200, 200, 200
				EndIf
				
				If MapTemp(x, y) > 0 Then
					Text xStart + mirroredX * tileSize +2, yStart + y * tileSize + 2,MapName(x,y)
				End If
			Next
		Next

		If Fullscreen Then DrawImage(CursorIMG,ScaledMouseX(),ScaledMouseY())
		Color 255, 255, 255
		Text(GraphicWidth / 2, GraphicHeight - 25 * HUDScale, I_Loc\Menu_Pressany, True, True)
		
		Flip
	Until GetKey() <> 0
	FlushKeys()
End Function


Function ConnectAdjacentRooms(r.Rooms)
	r\angle = WrapAngle(r\angle)
	r\Adjacent[0]=Null
	r\Adjacent[1]=Null
	r\Adjacent[2]=Null
	r\Adjacent[3]=Null
	For r2.Rooms = Each Rooms
		If r2\y = r\y And r<>r2 Then
			If r2\z=r\z Then
				If (r2\x)=(r\x+spacing) Then
					r\Adjacent[0]=r2
					If r\AdjDoor[0] = Null Then r\AdjDoor[0] = r2\AdjDoor[2]
				ElseIf (r2\x)=(r\x-spacing)
					r\Adjacent[2]=r2
					If r\AdjDoor[2] = Null Then r\AdjDoor[2] = r2\AdjDoor[0]
				EndIf
			ElseIf r2\x=r\x Then
				If (r2\z)=(r\z-spacing) Then
					r\Adjacent[1]=r2
					If r\AdjDoor[1] = Null Then r\AdjDoor[1] = r2\AdjDoor[3]
				ElseIf (r2\z)=(r\z+spacing)
					r\Adjacent[3]=r2
					If r\AdjDoor[3] = Null Then r\AdjDoor[3] = r2\AdjDoor[1]
				EndIf
			EndIf
			If (r\Adjacent[0]<>Null) And (r\Adjacent[1]<>Null) And (r\Adjacent[2]<>Null) And (r\Adjacent[3]<>Null) Then Exit
		EndIf
	Next
End Function

Function CreateMap(loadingstart,loadingcount#)
	DebugLog ("Generating a map using the seed "+GetRandomSeed())
	
	MapWidth% = GetModdedINIInt(MapOptions, "facility", "width")
	MapHeight% = GetModdedINIInt(MapOptions, "facility", "height")

	I_Zone\Transition[0] = Floor(MapHeight * (2.0 / 3.0)) + 1
	I_Zone\Transition[1] = Floor(MapHeight * (1.0 / 3.0)) + 1
	I_Zone\HasCustomForest = False
	I_Zone\HasCustomMT = False
	
	SeedRnd GetRandomSeed()
	
	Dim MapTemp%(MapWidth+1, MapHeight+1)
	Dim MapFound%(MapWidth+1, MapHeight+1)
	Dim MapName$(MapWidth, MapHeight)
	
	Dim MapRoomID%(ROOM4 + 1)
	
	CreateMapLayout()
	CountRooms()
	ForceRooms()
	
	InitializeRoomRanges()
	SetRooms()

	CreateRooms(loadingstart,loadingcount#)
	For r.Rooms = Each Rooms
		SetupTriggerBoxes(r)
	Next
	
	If DebugMapGen Then DrawDebugMap()
	
	For y = 0 To MapHeight
		For x = 0 To MapWidth
			MapTemp(x, y) = Min(MapTemp(x, y),1)
		Next
	Next

	For r.Rooms = Each Rooms
		ConnectAdjacentRooms(r)
	Next
End Function

; Really, really stupid bandaid fix!
; We basically need to allow the room2c forcing to consider extending rooms on y=1, which means they'll be pushed
; up to y=0. This requires bounds checking for a select amount of checks that deal with checking y-1.
Function MapTempSafe(x%,y%)
	If x<0 Or y<0 Or x>MapWidth Or y>MapHeight Then Return 0
	Return MapTemp(x,y)
End Function

Function SetRoom(room_name$,room_type%,pos%,min_pos%,max_pos%) ;place a room without overwriting others
	
	If max_pos<=min_pos Then DebugLog "Can't place "+room_name : Return False
	
	DebugLog "--- SETROOM: "+Upper(room_name)+" ---" + min_pos + " " + max_pos
	Local placed% = False
	If pos>=(min_pos+max_pos)/2 Then
		placed = SetRoomUpper(room_name,room_type,pos,max_pos) Lor SetRoomLower(room_name,room_type,pos-1,min_pos)
	Else
		placed = SetRoomLower(room_name,room_type,pos,min_pos) Lor SetRoomUpper(room_name,room_type,pos+1,max_pos)
	EndIf

	If placed Then DebugLog "--------------" Else DebugLog "couldn't place "+room_name
End Function

Function SetRoomUpper(room_name$,room_type%,pos%,max_pos%)
	If pos>=max_pos Return False
	While MapRoom(room_type,pos)<>""
		DebugLog "found "+MapRoom(room_type,pos)
		pos=pos+1
		If pos>=max_pos Then
			Return False
		EndIf
	Wend
	MapRoom(room_type,pos)=room_name
	Return True
End Function

Function SetRoomLower(room_name$,room_type%,pos%,min_pos%)
	If pos<min_pos Return False
	While MapRoom(room_type,pos)<>""
		DebugLog "found "+MapRoom(room_type,pos) + " " + pos
		pos=pos-1
		If pos<min_pos Then
			Return False
		EndIf
	Wend
	MapRoom(room_type,pos)=room_name
	Return True
End Function

Function GetZone(y%)
	Return Min(Floor((Float(MapHeight-y)/MapHeight*ZONEAMOUNT)),ZONEAMOUNT-1)
End Function

;-------------------------------------------------------------------------------------------------------


Function load_terrain(hmap,yscale#=0.7,t1%,t2%,mask%,mask_off%=5,texture_scale#=4.0,maskZoom#=2.0)
	
	DebugLog "load_terrain: "+hmap
	
	; load the heightmap
	If hmap = 0 Then RuntimeErrorExt "Heightmap image "+hmap+" does not exist."
	
	; store heightmap dimensions
	Local x = TextureWidth(hmap)-1, y = TextureHeight(hmap)-1
	Local lx,ly,index
	
	; load texture and lightmaps
	If t1 = 0 Then RuntimeErrorExt "load_terrain error: invalid texture 1"
	If t2 = 0 Then RuntimeErrorExt "load_terrain error: invalid texture 2"
	If mask = 0 Then RuntimeErrorExt "load_terrain error: invalid texture mask"
	
	; offset to keep centered
	Local maskW = TextureWidth(mask)
	Local maskH = TextureHeight(mask)
	Local offsetX# = (maskW * (1.0 - maskZoom)) / 2.0
	Local offsetY# = (maskH * (1.0 - maskZoom)) / 2.0
	
	TextureCoords t1, 0
	TextureCoords t2, 0
	ScaleTexture t1, x/texture_scale, y/texture_scale
	ScaleTexture t2, x/texture_scale, y/texture_scale
	
	; start building the terrain
	Local mesh = CreateMesh()
	Local surf = CreateSurface(mesh)
	
	; create some verts for the terrain
	For ly = 0 To y
		For lx = 0 To x
			AddVertex surf,lx,0,ly,1.0/lx,1.0/ly
		Next
	Next
	RenderWorld
			
	; connect the verts with faces
	For ly = 0 To y-1
		For lx = 0 To x-1
			AddTriangle surf,lx+((x+1)*ly),lx+((x+1)*ly)+(x+1),(lx+1)+((x+1)*ly)
			AddTriangle surf,(lx+1)+((x+1)*ly),lx+((x+1)*ly)+(x+1),(lx+1)+((x+1)*ly)+(x+1)
		Next
	Next
			
	; position the terrain to center 0,0,0
	Local mesh2% = CopyMesh(mesh,mesh)
	Local surf2% = GetSurface(mesh2,1)
	PositionMesh mesh, -x/2.0,0,-y/2.0
	PositionMesh mesh2, -x/2.0,0.01,-y/2.0
	
	EntityTexture mesh, t1, 0, 0
	EntityTexture mesh2, t2, 0, 0
	EntityFX mesh, 1
	EntityFX mesh2, 1+2+32
	
	; alter vertice height to match the heightmap red channel
	Local HeightMapBuffer = TextureBuffer(hmap)
	Local MaskBuffer = TextureBuffer(mask)
	LockBuffer HeightMapBuffer
	LockBuffer MaskBuffer
	;SetBuffer 
	For lx = 0 To x
		For ly = 0 To y
			Local maskX# = lx * Float(maskW) / Float(TextureWidth(hmap)) * maskZoom + offsetX
			Local maskY# = (TextureWidth(hmap) - ly) * Float(maskH) / Float(TextureWidth(hmap)) * maskZoom + offsetY
			maskX = Min(maskX, maskW-1)
			maskY = Min(maskY, maskH-1)
			
			Local RGB1 = ReadPixelFast(Min(lx, x-1), y - Min(ly, y-1), HeightMapBuffer)
			Local r = (RGB1 And $FF0000) Shr 16
			
			Local alpha# = ((ReadPixelFast(Clamp(maskX-mask_off,0,maskW-1), Clamp(maskY-mask_off,0,maskH-1), MaskBuffer) And $FF000000) Shr 24) / 255.0
			alpha = alpha + ((ReadPixelFast(Clamp(maskX+mask_off,0,maskW-1), Clamp(maskY+mask_off,0,maskH-1), MaskBuffer) And $FF000000) Shr 24) / 255.0
			alpha = alpha + ((ReadPixelFast(Clamp(maskX-mask_off,0,maskW-1), Clamp(maskY+mask_off,0,maskH-1), MaskBuffer) And $FF000000) Shr 24) / 255.0
			alpha = alpha + ((ReadPixelFast(Clamp(maskX+mask_off,0,maskW-1), Clamp(maskY-mask_off,0,maskH-1), MaskBuffer) And $FF000000) Shr 24) / 255.0
			alpha = alpha * 0.25
			alpha = Sqr(alpha)
			If alpha > 1.0 Then alpha = 1.0
			
			index = lx + ((x+1)*ly)
			VertexCoords surf, index , VertexX(surf,index), r*yscale,VertexZ(surf,index)
			VertexCoords surf2, index , VertexX(surf2,index), r*yscale,VertexZ(surf2,index)
			VertexColor surf2, index, 255.0,255.0,255.0,alpha
			; set the terrain texture coordinates
			VertexTexCoords surf,index,lx,-ly 
			VertexTexCoords surf2,index,lx,-ly 
		Next
	Next
	UnlockBuffer MaskBuffer
	UnlockBuffer HeightMapBuffer
	
	UpdateNormals mesh
	UpdateNormals mesh2
	
	EntityBlend mesh2, 1
	
	Return mesh
End Function



Include "Skybox.bb"

Global UpdateRoomLightsTimer# = 0.0

Function UpdateRoomLights(cam%)
	
	Local r.Rooms, i, random#, alpha#, dist#
	
	For r.Rooms = Each Rooms
		If r\dist < HideDistance*0.7 Or r = PlayerRoom Then
			For i = 0 To r\MaxLights%
				If r\Lights%[i]<>0 Then
					If EnableRoomLights% And (SecondaryLightOn>0.5) And cam%=Camera Then
						EntityOrder r\LightSprites2[i],-1
						If UpdateRoomLightsTimer=0.0 Then
							ShowEntity r\LightSprites[i]
							
							If EntityDistance(cam%,r\Lights%[i])<8.5 Then
								If r\LightHidden[i] Then
									ShowEntity r\Lights%[i]
									r\LightHidden[i] = False
								EndIf
							Else
								If (Not r\LightHidden[i]) Then
									HideEntity r\Lights%[i]
									r\LightHidden[i] = True
								EndIf
							EndIf
							
							If (EntityDistance(cam%,r\LightSprites2[i])<8.5 Or r\RoomTemplate\UseLightCones) Then
								If EntityVisible(cam%,r\LightSpritesPivot[i]) Or r\RoomTemplate\UseLightCones Then
									If r\LightSpriteHidden%[i] Then
										ShowEntity r\LightSprites2%[i]
										r\LightSpriteHidden%[i] = False
									EndIf
									If PlayerRoom\RoomTemplate\Name$ = "173" Then
										random# = Rnd(0.38,0.42)
									Else
										If r\LightFlicker%[i]<5 Then
											random# = Rnd(0.38,0.42)
										ElseIf r\LightFlicker%[i]>4 And r\LightFlicker%[i]<10 Then
											random# = Rnd(0.35,0.45)
										Else
											random# = Rnd(0.3,0.5)
										EndIf
									EndIf
									ScaleSprite r\LightSprites2[i],random#,random#
									
									dist# = (EntityDistance(cam%,r\LightSpritesPivot[i])+0.5)/7.5
									dist# = Max(Min(dist#,1.0),0.0)
									alpha# = Float(1.0 - dist)
									
									If alpha# > 0.0 Then
										EntityAlpha r\LightSprites2[i],Max(3*(Brightness/255)*(r\LightIntensity[i]/2),1)*alpha#
									Else
										;Instead of rendering the sprite invisible, just hiding it if the player is far away from it
										If (Not r\LightSpriteHidden%[i]) Then
											HideEntity r\LightSprites2[i]
											r\LightSpriteHidden%[i]=True
										EndIf
									EndIf
									
									If r\RoomTemplate\UseLightCones Then
										If EntityDistance(cam%,r\LightSprites2[i])>=8.5 Or (Not EntityVisible(cam%,r\LightSpritesPivot[i])) Then
											HideEntity r\LightSprites2%[i]
											r\LightSpriteHidden%[i] = True
										EndIf
									EndIf
								Else
									If (Not r\LightSpriteHidden%[i]) Then
										HideEntity r\LightSprites2%[i]
										r\LightSpriteHidden%[i] = True
									EndIf
								EndIf
							Else
								If (Not r\LightSpriteHidden%[i]) Then
									HideEntity r\LightSprites2%[i]
									r\LightSpriteHidden%[i] = True
									If r\LightCone[i]<>0 Then HideEntity r\LightCone[i]
									If r\LightConeSpark[i]<>0 HideEntity r\LightConeSpark[i]
									EndIf
								EndIf
							
							If r\LightCone[i]<>0 Then ShowEntity r\LightCone[i]
							
							If r\LightConeSpark[i]<>0 Then
								If r\LightConeSparkTimer[i]>0 And r\LightConeSparkTimer[i]<10
									ShowEntity r\LightConeSpark[i]
									r\LightConeSparkTimer[i]=r\LightConeSparkTimer[i]+FPSfactor
								Else
									HideEntity r\LightConeSpark[i]
									r\LightConeSparkTimer[i]=0
								EndIf
							EndIf
							
							If r\LightCone[i]<>0 Then
								ScaleEntity r\LightCone[i],0.005+Max(((-0.4+random#)*0.025),0),0.005+Max(((-0.4+random#)*0.025),0),0.005+Max(((-0.4+random#)*0.025),0)
								If r\LightFlicker%[i]>4 Then
									If Rand(400/FPSfactor)=1 Then
										SetEmitter(r\LightSpritesPivot[i],ParticleEffect[0])
										PlaySound2(IntroSFX(Rand(10,12)),cam,r\LightSpritesPivot[i])
										ShowEntity r\LightConeSpark[i]
										r\LightConeSparkTimer[i] = FPSfactor
									EndIf
								EndIf
							EndIf
						Else
							If (EntityDistance(cam%,r\LightSprites2[i])<8.5 Or r\RoomTemplate\UseLightCones) Then
								If PlayerRoom\RoomTemplate\Name$ = "173" Then
									random# = Rnd(0.38,0.42)
								Else
									If r\LightFlicker%[i]<5 Then
										random# = Rnd(0.38,0.42)
									ElseIf r\LightFlicker%[i]>4 And r\LightFlicker%[i]<10 Then
										random# = Rnd(0.35,0.45)
									Else
										random# = Rnd(0.3,0.5)
									EndIf
								EndIf
								
								If (Not r\LightSpriteHidden[i]) Then
									ScaleSprite r\LightSprites2[i],random#,random#
								EndIf
							EndIf
							
							If r\LightCone[i]<>0 Then
								ScaleEntity r\LightCone[i],0.005+Max(((-0.4+random#)*0.025),0),0.005+Max(((-0.4+random#)*0.025),0),0.005+Max(((-0.4+random#)*0.025),0)
							EndIf
							
							If r\LightConeSpark[i]<>0 Then
								If r\LightConeSparkTimer[i]>0 And r\LightConeSparkTimer[i]<10 Then
									ShowEntity r\LightConeSpark[i]
									r\LightConeSparkTimer[i]=r\LightConeSparkTimer[i]+FPSfactor
								Else
									HideEntity r\LightConeSpark[i]
									r\LightConeSparkTimer[i]=0
								EndIf
							EndIf
						EndIf
						UpdateRoomLightsTimer = UpdateRoomLightsTimer + FPSfactor
						If UpdateRoomLightsTimer >= 8 Then
							UpdateRoomLightsTimer = 0.0
						EndIf
					ElseIf cam%=Camera Then
						If SecondaryLightOn<=0.5 Then
							HideEntity r\LightSprites[i]
						Else
							ShowEntity r\LightSprites[i]
						EndIf
						
						If (Not r\LightHidden[i]) Then
							HideEntity r\Lights%[i]
							r\LightHidden[i] = True
						EndIf
						If (Not r\LightSpriteHidden[i]) Then
							HideEntity r\LightSprites2[i]
							r\LightSpriteHidden[i]=True
						EndIf
						If r\LightCone[i]<>0 Then HideEntity r\LightCone[i]
						If r\LightConeSpark[i]<>0 Then HideEntity r\LightConeSpark[i]
					Else
						;This will make the lightsprites not glitch through the wall when they are rendered by the cameras
						EntityOrder r\LightSprites2[i],0
					EndIf
				Else
					Exit
				EndIf
			Next
		EndIf
	Next
	
End Function

Function UpdateCheckpointMonitors(numb%)
	Local i,sf,b,t1
	Local entity%
	
	If numb% = 0
		entity% = Monitor2
		UpdateCheckpoint1 = True
	Else
		entity% = Monitor3
		UpdateCheckpoint2 = True
	EndIf
	
	For i = 2 To CountSurfaces(entity)
		sf = GetSurface(entity,i)
		b = GetSurfaceBrush(sf)
		If b<>0 Then
			t1 = GetBrushTexture(b,0)
			If t1<>0 Then
				Local name$ = StripPath(TextureName(t1))
				If Lower(name) <> "monitortexture.jpg"
					If numb% = 0
						If MonitorTimer# < 50
							BrushTexture b, MonitorTexture2, 0, 0
						Else
							BrushTexture b, MonitorTexture4, 0, 0
						EndIf
					Else
						If MonitorTimer2# < 50
							BrushTexture b, MonitorTexture2, 0, 0
						Else
							BrushTexture b, MonitorTexture3, 0, 0
						EndIf
					EndIf
					PaintSurface sf,b
				EndIf
				If name<>"" Then FreeTexture t1
			EndIf
			FreeBrush b
		EndIf
	Next
	
End Function

Function TurnCheckpointMonitorsOff(numb%)
	Local i,sf,b,t1
	Local entity%
	
	If numb% = 0
		entity% = Monitor2
		UpdateCheckpoint1 = False
		MonitorTimer# = 0.0
	Else
		entity% = Monitor3
		UpdateCheckpoint2 = False
		MonitorTimer2# = 0.0
	EndIf
	
	For i = 2 To CountSurfaces(entity)
		sf = GetSurface(entity,i)
		b = GetSurfaceBrush(sf)
		If b<>0 Then
			t1 = GetBrushTexture(b,0)
			If t1<>0 Then
				Local name$ = StripPath(TextureName(t1))
				If Lower(name) <> "monitortexture.jpg"
					BrushTexture b, MonitorTextureOff, 0, 0
					PaintSurface sf,b
				EndIf
				If name<>"" Then FreeTexture t1
			EndIf
			FreeBrush b
		EndIf
	Next
	
End Function

Function TimeCheckpointMonitors()
	
	If UpdateCheckpoint1
		If MonitorTimer < 100.0
			MonitorTimer# = Min(MonitorTimer# + FPSfactor,100.0)
		Else
			MonitorTimer# = 0.0
		EndIf
	EndIf
	If UpdateCheckpoint2
		If MonitorTimer2 < 100.0
			MonitorTimer2# = Min(MonitorTimer2# + FPSfactor,100.0)
		Else
			MonitorTimer2# = 0.0
		EndIf
	EndIf
	
End Function

Function AmbientLightRooms(value%=0)
	Local mesh%,surf%,brush%,tex0%
	
	If value=AmbientLightRoomVal Then Return
	AmbientLightRoomVal = value
	
	Local oldbuffer% = BackBuffer() ;probably shouldn't make assumptions here but who cares, why wouldn't it use the backbuffer ;GetBuffer()
	
	SetBuffer TextureBuffer(AmbientLightRoomTex)
	
	ClsColor value,value,value
	Cls
	ClsColor 0,0,0
	
	SetBuffer oldbuffer
End Function

;#########################################################################
;CHUNKS FOR 1499
;#########################################################################










Type Dummy1499
	Field anim%
	Field obj%
End Type

;#########################################################################
;END CHUNKS
;#########################################################################

Type ElevatorObj
	Field obj%
	Field InFacility%
	Field door.Doors
End Type

Function AssignElevatorObj.ElevatorObj(obj%,door.Doors,in_facility%)
	Local eo.ElevatorObj = New ElevatorObj
	
	eo\obj% = obj%
	eo\door = door
	eo\InFacility% = in_facility%
	
	Return eo
End Function

Function DeleteElevatorObjects()
	
	Delete Each ElevatorObj
	
End Function

Function ValidRoom2slCamRoom(r.Rooms)
	If (r = Null) Then
		Return False
	EndIf
	
	Local RN$ = r\RoomTemplate\Name$
	
	If RN$ = "room2closets" Then Return True
	If RN$ = "room1archive" Then Return True
	If RN$ = "room3z3" Then Return True
	If RN$ = "room1lifts" Then Return True
	If RN$ = "checkpoint1" Then Return True
	If RN$ = "room2nuke" Then Return True
	If RN$ = "008" Then Return True
	If RN$ = "room1162" Then Return True
	If RN$ = "room966" Then Return True
	If RN$ = "room2ccont" Then Return True
	
	Return False
	
End Function

Function FindAndDeleteFakeMonitor(r.Rooms,x#,y#,z#,Amount%)
	Local i%
	
	For i = 0 To Amount%
		If r\Objects[i]<>0
			If EntityX(r\Objects[i],True) = x#
				If EntityY(r\Objects[i],True) = y#
					If EntityZ(r\Objects[i],True) = z#
						FreeEntity r\Objects[i]
						r\Objects[i]=0
						DebugLog "Deleted Fake Monitor: "+i
						Exit
					EndIf
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function AddLightCones(room.Rooms)
	Local i
	
	For i = 0 To MaxRoomLights-1
		If room\Lights[i]<>0
			room\LightCone[i] = CopyEntity(LightConeModel)
			ScaleEntity room\LightCone[i],0.01,0.01,0.01
			EntityColor room\LightCone[i],room\LightR[i],room\LightG[i],room\LightB[i]
			EntityAlpha room\LightCone[i],0.15
			EntityBlend room\LightCone[i],3
			PositionEntity room\LightCone[i],EntityX(room\LightSpritesPivot[i],True),EntityY(room\LightSpritesPivot[i],True),EntityZ(room\LightSpritesPivot[i],True),True
			EntityParent room\LightCone[i],room\LightSpritesPivot[i]
			
			If room\LightFlicker%[i] > 4
				room\LightConeSpark[i] = CreateSprite()
				ScaleSprite room\LightConeSpark[i],1.0,1.0
				EntityTexture room\LightConeSpark[i],ParticleTextures(8)
				SpriteViewMode room\LightConeSpark[i],2
				EntityFX room\LightConeSpark[i],1
				RotateEntity room\LightConeSpark[i],-90,0,0
				EntityBlend room\LightConeSpark[i],3
				EntityAlpha room\LightConeSpark[i],1.0
				PositionEntity room\LightConeSpark[i],EntityX(room\LightSpritesPivot[i],True),EntityY(room\LightSpritesPivot[i],True)+0.05,EntityZ(room\LightSpritesPivot[i],True),True
				EntityParent room\LightConeSpark[i],room\LightSpritesPivot[i]
			EndIf
		Else
			Exit
		EndIf
	Next
	
End Function

Function CalculateRoomTemplateExtents(r.RoomTemplates)
	If r\DisableOverlapCheck Then Return

	r\MinX = -256.0 : r\MaxX = 256.0
	r\MinZ = -256.0 : r\MaxZ = 256.0
	r\MinY = 0.0 : r\MaxY = 448.0

	DebugLog("roomtemplateextents: "+r\MinX+", "+r\MinY	+", "+r\MinZ	+", "+r\MaxX	+", "+r\MaxY+", "+r\MaxZ)
End Function

Function CalculateRoomExtents(r.Rooms)
	If r\RoomTemplate\DisableOverlapCheck Then Return
	
	;shrink the extents slightly - we don't care if the overlap is smaller than the thickness of the walls
	Local shrinkAmount# = 0.05
	
	;convert from the rooms local space to world space
	TFormVector(r\RoomTemplate\MinX, r\RoomTemplate\MinY, r\RoomTemplate\MinZ, r\obj, 0)
	r\MinX = TFormedX() + r\x
	r\MinY = TFormedY()
	r\MinZ = TFormedZ() + r\z
	
	;convert from the rooms local space to world space
	TFormVector(r\RoomTemplate\MaxX, r\RoomTemplate\MaxY, r\RoomTemplate\MaxZ, r\obj, 0)
	r\MaxX = TFormedX() + r\x
	r\MaxY = TFormedY()
	r\MaxZ = TFormedZ() + r\z
	
	If (r\MinX > r\MaxX) Then
		Local tempX# = r\MaxX
		r\MaxX = r\MinX
		r\MinX = tempX
	EndIf
	If (r\MinZ > r\MaxZ) Then
		Local tempZ# = r\MaxZ
		r\MaxZ = r\MinZ
		r\MinZ = tempZ
	EndIf
	
	r\MinX = r\MinX + shrinkAmount : r\MaxX = r\MaxX - shrinkAmount
	r\MinY = r\MinY + shrinkAmount : r\MaxY = r\MaxY - shrinkAmount
	r\MinZ = r\MinZ + shrinkAmount : r\MaxZ = r\MaxZ - shrinkAmount
	
	DebugLog("roomextents: "+r\MinX+", "+r\MinY	+", "+r\MinZ	+", "+r\MaxX	+", "+r\MaxY+", "+r\MaxZ)
End Function

Function IsInRoom%(r.Rooms, x#, z#)
	Local MaxX#,MinX#,MaxZ#,MinZ#
	If r\MaxX<>0 Lor r\MinX<>0 Lor r\MaxZ<>0 Lor r\MinZ<>0
		MaxX# = r\MaxX
		MinX# = r\MinX
		MaxZ# = r\MaxZ
		MinZ# = r\MinZ
	Else
		Local roomX# = EntityX(r\obj), roomZ# = EntityZ(r\obj)
		MaxX# = roomX + 4.0
		MinX# = roomX - 4.0
		MaxZ# = roomZ + 4.0
		MinZ# = roomZ - 4.0
	EndIf
	Return x > MinX And x < MaxX And z > MinZ And z < MaxZ
End Function









;~IDEal Editor Parameters:
;~F#2#A#35#102#111#118#11F#126#13F#147#14F#143E#145F
;~B#1231
;~C#Blitz3D