Global SelectedInputBox%
Global SelectedMap% = -1

Global MenuHoverSFX% = LoadSound_Strict("SFX\UI\Menu1.wav")
Global MenuClickSFX% = LoadSound_Strict("SFX\UI\Menu3.wav")
Global MenuHoverCooldown%

Global MenuVersion$

Global MenuStaticIMG%, MenuStaticFrame%, MenuStaticLast%, MenuStaticFrames%
Global MenuContext%
Global MenuEntryCount%
Global MenuSelected%
Global MenuIntroStart%
Global MenuIntroDone%
Global MenuWasOpen%
Global MenuTitleSeen%
Global MenuBarPos#, MenuBarFrom#, MenuBarTarget#, MenuBarStart%

Dim MenuChar$(4, 13)
Dim MenuCharW#(4, 13)
Dim MenuEntryLen%(4)

Dim MenuTitleChar$(2)
Dim MenuTitleCharW#(2)
Global MenuTitleLen%, MenuTitleW#, MenuTitleH#

Global RandomSeed$, RandomSeedNumeric%, HasNumericSeed%
Function GetRandomSeed%()
	If HasNumericSeed Then Return RandomSeedNumeric Else Return GenerateSeedNumber(RandomSeed)
End Function

Function MenuInitText()
	MenuVersion$ = "BUILD " + VersionNumber
	MenuTitleChar$(0) = "A"
	MenuTitleChar$(1) = "C"
	MenuTitleChar$(2) = "E"
	MenuTitleLen = 3

	SetFont Font2
	MenuTitleW = 0.0
	For i% = 0 To MenuTitleLen-1
		MenuTitleCharW#(i) = StringWidth(MenuTitleChar$(i))
		MenuTitleW = MenuTitleW + MenuTitleCharW#(i)
	Next
	MenuTitleW = MenuTitleW + 10.0 * UIScale * (MenuTitleLen-1)
	MenuTitleH = StringHeight("A")
	SetFont Font1
End Function
MenuInitText()

Function MenuInitStatic()
	MenuStaticFrames = 24
	MenuStaticIMG = LoadAnimImage("GFX\UI\EPILEPTICS_WATCHOUT.gif", 1024, 576, 0, MenuStaticFrames)
	If MenuStaticIMG <> 0 Then
		ScaleImage MenuStaticIMG, Float(GraphicWidth)/1024.0, Float(GraphicHeight)/576.0
	EndIf
	MenuStaticFrame = 0
	MenuStaticLast = MilliSecs()
End Function
MenuInitStatic()

Function MenuLoadEntry(i%, s$)
	Local n% = Len(s$)
	MenuEntryLen%(i) = n
	For j% = 0 To n-1
		MenuChar$(i,j) = Upper(Mid(s$, j+1, 1))
		MenuCharW#(i,j) = StringWidth(MenuChar$(i,j))
	Next
End Function

Function MenuEaseOut#(t#)
	t = Max(Min(t, 1.0), 0.0)
	Return 1.0 - (1.0-t)*(1.0-t)
End Function

Function MenuSetup(kind%)
	MenuContext = kind
	SetFont Font1
	If kind = 1 Then
		MenuEntryCount = 2
		MenuLoadEntry(0, "START")
		MenuLoadEntry(1, "QUIT")
		MenuIntroDone = MenuTitleSeen
		MenuTitleSeen = True
	Else
		MenuEntryCount = 3
		MenuLoadEntry(0, "RESUME")
		MenuLoadEntry(1, "QUIT TO TITLE")
		MenuLoadEntry(2, "QUIT")
		MenuIntroDone = False
	EndIf
	MenuSelected = 0
	MenuIntroStart = MilliSecs()
	MenuBarPos = GraphicHeight*0.52
	MenuBarFrom = MenuBarPos
	MenuBarTarget = MenuBarPos
	MenuBarStart = MilliSecs()
End Function

Function MenuClick()
	Local cchn% = PlaySound_Strict(MenuClickSFX)
	If cchn <> 0 Then ChannelVolume cchn, 0.8*SFXVolume
End Function

Function MenuSetSelected(i%)
	If i% < 0 Then i% = MenuEntryCount-1
	If i% >= MenuEntryCount Then i% = 0
	If i% = MenuSelected Then Return
	MenuSelected = i%
	MenuBarFrom = MenuBarPos
	MenuBarTarget = GraphicHeight*0.52 + i%*(42.0+10.0)*UIScale
	MenuBarStart = MilliSecs()
	If MilliSecs() > MenuHoverCooldown Then
		Local hchn% = PlaySound_Strict(MenuHoverSFX)
		If hchn <> 0 Then ChannelVolume hchn, 0.8*SFXVolume
		MenuHoverCooldown = MilliSecs() + 120
	EndIf
End Function

Function MenuInput%()
	If Not MenuIntroDone Then
		Local elapsed% = MilliSecs()-MenuIntroStart
		If elapsed >= 1200 Then
			MenuIntroDone = True
		ElseIf elapsed > 120 Then
			If GetKey() <> 0 Or MouseHit1 Then MenuIntroDone = True
		EndIf
		Return -1
	EndIf

	Local ex# = (GraphicWidth-420.0*UIScale)/2.0
	Local ew# = 420.0*UIScale
	Local eh# = 42.0*UIScale

	For i% = 0 To MenuEntryCount-1
		If MouseOn(ex#, GraphicHeight*0.52 + i%*(42.0+10.0)*UIScale, ew#, eh#) Then
			If MenuSelected <> i% Then MenuSetSelected(i%)
			If MouseHit1 Then
				MenuClick()
				Return i%
			EndIf
		EndIf
	Next

	If KeyHit(200) Or KeyHit(KEY_UP) Then MenuSetSelected(MenuSelected-1)
	If KeyHit(208) Or KeyHit(KEY_DOWN) Then MenuSetSelected(MenuSelected+1)
	If KeyHit(28) Then
		MenuClick()
		Return MenuSelected
	EndIf

	Return -1
End Function

Function MenuDrawEntries(t#)
	Local ex# = (GraphicWidth-420.0*UIScale)/2.0
	Local ew# = 420.0*UIScale
	Local eh# = 42.0*UIScale
	Local by# = GraphicHeight*0.52 + MenuSelected*(42.0+10.0)*UIScale

	Local barT# = Float(MilliSecs()-MenuBarStart) / Float(120)
	If barT# >= 1.0 Then
		MenuBarPos = MenuBarTarget
	Else
		MenuBarPos = MenuBarFrom + (MenuBarTarget-MenuBarFrom)*MenuEaseOut(barT#)
	EndIf

	For i% = 0 To MenuEntryCount-1
		Local p# = 1.0
		If Not MenuIntroDone Then
			p# = MenuEaseOut((t#*1200-820-i%*80)/Float(220))
		EndIf

		Local x# = ex# - (1.0-p#)*24.0*UIScale
		Local y# = GraphicHeight*0.52 + i%*(42.0+10.0)*UIScale

		If p# > 0.0 Then
			If i% = MenuSelected And MenuIntroDone Then
				Color 80, 80, 80
				Rect x#, MenuBarPos, ew#, eh#, True
			EndIf
	
			If i% = MenuSelected And MenuIntroDone Then
				Color 0, 0, 0
			ElseIf i% = MenuSelected Then
				Color 255, 255, 255
			Else
				Color 255, 255, 255
			EndIf
			Local tx# = x# + 18.0*UIScale
			Local ty# = y# + (42.0*UIScale)/2.0
			For j% = 0 To MenuEntryLen%(i)-1
				Text tx#, ty#, MenuChar$(i,j), False, True
				tx# = tx# + MenuCharW#(i,j) + 2.0*UIScale
			Next
	
			If i% = MenuSelected And MenuIntroDone Then
				Local b% = Int(8.0*UIScale)
				Color 255, 255, 255
				Rect x#, by#, b%, 1, False
				Rect x#, by#, 1, b%, False
				Rect x#+ew#-b%, by#, b%, 1, False
				Rect x#+ew#-1, by#, 1, b%, False
				Rect x#, by#+eh#-1, b%, 1, False
				Rect x#, by#+eh#-b%, 1, b%, False
				Rect x#+ew#-b%, by#+eh#-1, b%, 1, False
				Rect x#+ew#-1, by#+eh#-b%, 1, b%, False
			EndIf
			EndIf
	Next
End Function

Function MenuDrawStatic()
	If MenuStaticIMG = 0 Then Return
	If MilliSecs()-MenuStaticLast >= 80 Then
		MenuStaticFrame = (MenuStaticFrame+1) Mod MenuStaticFrames
		MenuStaticLast = MilliSecs()
	EndIf
	DrawImage MenuStaticIMG, 0, 0, MenuStaticFrame
End Function

Function MenuDraw()
	Local t# = 1.0
	If Not MenuIntroDone Then t# = Min(Float(MilliSecs()-MenuIntroStart)/Float(1200), 1.0)

	ShowPointer()
	Color 0, 0, 0
	Rect 0,0,GraphicWidth,GraphicHeight,True
	MenuDrawStatic()

	Local typeT# = Max(Min((t#*1200-180)/Float(520),1.0),0.0)
	Local visible% = Int(typeT#*MenuTitleLen)
	Local tx# = (GraphicWidth-MenuTitleW)/2.0
	Local ty# = GraphicHeight*0.20
	SetFont Font2
	Color 255, 255, 255
	For i% = 0 To visible%-1
		Text tx#, ty#, MenuTitleChar$(i), False, True
		tx# = tx# + MenuTitleCharW#(i) + 10.0*UIScale
	Next
	Local ruleT# = Max(Min((t#*1200-760)/Float(240),1.0),0.0)
	If ruleT# > 0.0 Then
		Color 255, 255, 255
		Rect (GraphicWidth-MenuTitleW*ruleT#)/2.0, ty#+MenuTitleH/2.0+8.0*UIScale, MenuTitleW*ruleT#, 1, False
	EndIf
	SetFont Font1

	MenuDrawEntries(t#)

	Color 255, 255, 255
	Text 24.0*UIScale, GraphicHeight-26.0*UIScale, MenuVersion$, False, False
End Function

Function UpdateMainMenu()
	If MenuContext <> 1 Then MenuSetup(1)

	Local act% = MenuInput()
	MenuDraw()

	If act% = 0 Then
		If UseNumericSeeds Then
			If RandomSeedNumeric = 0 Then RandomSeedNumeric = MilliSecs()
		Else
			If RandomSeed = "" Then RandomSeed = Abs(MilliSecs())
		EndIf

		SeedRnd GetRandomSeed()
		SetErrorMsg(7, GetSeedString(False))

		LoadEntities()
		LoadAllSounds()
		InitRoomTemplates()
		InitNewGame()
		MainMenuOpen = False
		MenuOpen = False
		MenuWasOpen = False
		HidePointer()
		FlushKeys()
		FlushMouse()
	ElseIf act% = 1 Then
		End
	EndIf
End Function

Function DrawLoading(percent%, shortloading% = False)
	If GraphicWidth = 0 Then Return

	Cls
	Color 0,0,0
	Rect 0,0,GraphicWidth,GraphicHeight,True

	Local w% = GraphicWidth*0.5
	Local h% = 6*UIScale
	Local x% = (GraphicWidth-w)/2
	Local y% = GraphicHeight*0.5

	Color 40,40,40
	Rect x,y,w,h,True
	Color 255,255,255
	Rect x,y,w*Min(Max(percent,0),100)/100.0,h,True

	SetFont Font1
	Color 220,220,220
	Text GraphicWidth/2, y-24*UIScale, "LOADING "+Str(Min(Max(percent,0),100))+"%", True

	Flip False
End Function

Function DrawFrame(x%, y%, width%, height%, xoffset%=0, yoffset%=0, scrollY%=True)
	Color 20,20,20
	Rect x,y,width,height,True
	Color 120,120,120
	Rect x,y,width,1,False
	Rect x,y+height-1,width,1,False
	Rect x,y,1,height,False
	Rect x+width-1,y,1,height,False
End Function

Function InputBox$(x%, y%, width%, height%, Txt$, ID% = 0, virtualKeyboardMode=0)
	Color 255,255,255
	Rect(x,y,width,height,True)

	Local MouseOnBox% = False
	If MouseOn(x, y, width, height) Then
		Color 45,45,45
		MouseOnBox = True
		If MouseHit1 Then
			SelectedInputBox = ID
			FlushKeys
		EndIf
	EndIf

	Color 10,10,10
	Rect(x + 2, y + 2, width - 4, height - 4)
	Color 255,255,255
	Text(x + width / 2, y + height / 2, Txt, True, True)

	If (Not MouseOnBox) And MouseHit1 And SelectedInputBox = ID Then SelectedInputBox = 0

	If SelectedInputBox = ID Then
		If (MilliSecs() Mod 800) < 400 Then Rect(x + width / 2 + StringWidth(Txt) / 2 + 2, y + height / 2 - 17 * UIScale / 2, 2, 17 * UIScale)
		Txt = TextInput(Txt)
	EndIf

	Return Txt
End Function
