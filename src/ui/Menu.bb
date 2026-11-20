Global SelectedInputBox%
Global SelectedMap% = -1

Global MenuHoverSFX% = LoadSound_Strict("SFX\UI\Menu1.wav")
Global MenuClickSFX% = LoadSound_Strict("SFX\UI\Menu3.wav")
Global MenuHoverCooldown%

Global MenuVersion$

Global SquadOpen%
Dim SquadNames$(3)
Dim SquadNameFont%(4)
Global SquadImage%

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

Function SquadRefresh()
	For i% = 0 To 3
		SquadNames$(i) = SquadNetSlotName(i%)
	Next
	If SquadNames$(0) = "" Then SquadNames$(0) = "PLAYER"
End Function

Function SquadInit()
	SquadRefresh()
	For i% = 0 To 4
		SquadNameFont%(i%) = LoadFont_Strict("GFX\font\arial\Arial.ttf", Int((19-i%*2)*UIScale))
	Next
	SquadImage = LoadImage_Strict("GFX\fatty2.png")
	Local innerW# = GraphicHeight*0.27*0.54 - 2.0*UIScale
	Local imgW# = innerW#*1.8
	If SquadImage <> 0 Then
		If ImageWidth(SquadImage) > 0 Then
			Local s# = imgW# / ImageWidth(SquadImage)
			ScaleImage SquadImage, s#, s#
		EndIf
	EndIf
End Function
SquadInit()

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
	SquadOpen = False
	SetFont Font1
	If kind = 1 Then
		MenuEntryCount = 3
		MenuLoadEntry(0, "SINGLEPLAYER")
		MenuLoadEntry(1, "MULTIPLAYER")
		MenuLoadEntry(2, "QUIT")
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

Function MenuHoverSound()
	If MilliSecs() > MenuHoverCooldown Then
		Local hchn% = PlaySound_Strict(MenuHoverSFX)
		If hchn <> 0 Then ChannelVolume hchn, 0.8*SFXVolume
		MenuHoverCooldown = MilliSecs() + 120
	EndIf
End Function

Function MenuMoveBar(y#)
	MenuBarFrom = MenuBarPos
	MenuBarTarget = y#
	MenuBarStart = MilliSecs()
End Function

Function MenuUpdateBar()
	Local barT# = Float(MilliSecs()-MenuBarStart) / Float(120)
	If barT# >= 1.0 Then
		MenuBarPos = MenuBarTarget
	Else
		MenuBarPos = MenuBarFrom + (MenuBarTarget-MenuBarFrom)*MenuEaseOut(barT#)
	EndIf
End Function

Function MenuDrawBrackets(x#, y#, w#, h#)
	Local b% = Int(8.0*UIScale)
	Color 255, 255, 255
	Rect x#, y#, b%, 1, False
	Rect x#, y#, 1, b%, False
	Rect x#+w#-b%, y#, b%, 1, False
	Rect x#+w#-1, y#, 1, b%, False
	Rect x#, y#+h#-1, b%, 1, False
	Rect x#, y#+h#-b%, 1, b%, False
	Rect x#+w#-b%, y#+h#-1, b%, 1, False
	Rect x#+w#-1, y#+h#-b%, 1, b%, False
End Function

Function MenuSpacedText#(x#, y#, s$, gap# = 2.0)
	Local x0# = x#
	For j% = 1 To Len(s$)
		Local c$ = Mid(s$, j%, 1)
		Text x#, y#, c$, False, True
		x# = x# + StringWidth(c$) + gap#*UIScale
	Next
	Return x#-x0#
End Function

Function MenuSetSelected(i%)
	If i% < 0 Then i% = MenuEntryCount-1
	If i% >= MenuEntryCount Then i% = 0
	If i% = MenuSelected Then Return
	MenuSelected = i%
	MenuMoveBar(GraphicHeight*0.52 + i%*(42.0+10.0)*UIScale)
	MenuHoverSound()
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

	MenuUpdateBar()

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
				MenuDrawBrackets(x#, by#, ew#, eh#)
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

Function SquadTextWidth#(s$, gap#)
	Local totalW# = 0.0
	For j% = 1 To Len(s$)
		totalW# = totalW# + StringWidth(Mid(s$, j%, 1))
		If j% > 1 Then totalW# = totalW# + gap#
	Next
	Return totalW#
End Function

Function SquadDrawName(x#, y#, maxW#, s$, fit%=False)
	s$ = Upper(s$)
	Local gap# = 2.0*UIScale
	If fit Then
		For i% = 0 To 4
			SetFont SquadNameFont%(i%)
			If SquadTextWidth(s$, gap#) <= maxW# Then Exit
		Next
	EndIf
	Local n% = Len(s$)
	Local totalW# = SquadTextWidth(s$, gap#)
	While n% > 0 And totalW# > maxW#
		n% = n% - 1
		totalW# = SquadTextWidth(Left(s$, n%), gap#)
	Wend
	Local tx# = x# - totalW#/2.0
	For j% = 1 To n%
		Local c$ = Mid(s$, j%, 1)
		Text tx#, y#, c$, False, True
		tx# = tx# + StringWidth(c$) + gap#
	Next
	SetFont Font1
End Function

Function SquadCampaignTab(x#, y#, w#, h#, label$, selected%)
	SetFont Font1
	If selected Then
		Color 80, 80, 80
		Rect x#, y#, w#, h#, True
		Color 0, 0, 0
	Else
		Color 80, 80, 80
		Rect x#, y#, w#, h#, False
		Color 255, 255, 255
	EndIf
	SquadDrawName(x#+w#/2.0, y#+h#/2.0, w#-8.0*UIScale, label$)
	If selected Then MenuDrawBrackets(x#, y#, w#, h#)
End Function

Function SquadMenuButton%(x#, y#, w#, h#, label$)
	Local over% = MouseOn(Int(x#), Int(y#), Int(w#), Int(h#))
	If over% Then
		Color 80, 80, 80
		Rect x#, y#, w#, h#, True
		Color 0, 0, 0
	Else
		Color 80, 80, 80
		Rect x#, y#, w#, h#, False
		Color 255, 255, 255
	EndIf
	SquadDrawName(x#+w#/2.0, y#+h#/2.0, w#-36.0*UIScale, label$)
	If over% Then MenuDrawBrackets(x#, y#, w#, h#)
	Return over% And MouseHit1
End Function

Function MenuDrawMultiplayer()
	ShowPointer()
	Color 0, 0, 0
	Rect 0,0,GraphicWidth,GraphicHeight,True
	MenuDrawStatic()

	SetFont Font2
	Color 255, 255, 255
	Local hx# = 48.0*UIScale
	Local hy# = GraphicHeight*0.08
	Local hw# = MenuSpacedText(hx#, hy#, "MULTIPLAYER", 10.0)
	Rect hx#, hy#+StringHeight("A")/2.0+8.0*UIScale, hw#, 1, False

	Local formW# = 420.0*UIScale
	Local formX# = (GraphicWidth-formW#)/2.0
	Local labelW# = 110.0*UIScale
	Local rowH# = 30.0*UIScale
	Local ny# = GraphicHeight*0.22

	SetFont Font7
	Color 200, 200, 200
	Text formX#, ny#+rowH#/2.0, "NAME", False, True
	SquadNetName$ = InputBox(Int(formX#+labelW#), Int(ny#), Int(formW#-labelW#), Int(rowH#), SquadNetName$, 5, 0, True)

	Local ay# = ny#+rowH#+12.0*UIScale
	Color 200, 200, 200
	Text formX#, ay#+rowH#/2.0, "ADDRESS", False, True
	SquadNetHost$ = InputBox(Int(formX#+labelW#), Int(ay#), Int(formW#-labelW#), Int(rowH#), SquadNetHost$, 3, 0, True)

	Local py# = ay#+rowH#+12.0*UIScale
	Color 200, 200, 200
	Text formX#, py#+rowH#/2.0, "PORT", False, True
	SquadNetPort$ = InputBox(Int(formX#+labelW#), Int(py#), Int(120.0*UIScale), Int(rowH#), SquadNetPort$, 4, 0, True)

	Local by# = py#+rowH#+24.0*UIScale
	SetFont Font1
	If SquadMenuButton(formX#, by#, formW#, 42.0*UIScale, "HOST LOBBY") Then
		MenuClick()
		SquadNetHostLobby(Int(Trim(SquadNetPort$)))
	EndIf
	Local jy# = by#+52.0*UIScale
	Local backY# = jy#+52.0*UIScale
	If SquadMenuButton(formX#, jy#, formW#, 42.0*UIScale, "JOIN LOBBY") Then
		MenuClick()
		SquadNetConnect(Trim(SquadNetHost$), Int(Trim(SquadNetPort$)))
	EndIf
	If SquadMenuButton(formX#, backY#, formW#, 42.0*UIScale, "BACK") Then
		MenuClick()
		SquadNetDisconnect()
		SquadOpen = False
	EndIf

	SetFont Font7
	Color 200, 200, 200
	Text GraphicWidth/2.0, backY#+54.0*UIScale, SquadNetStatus$, True, True
	SetFont Font1
End Function

Function MenuDrawSquad()
	ShowPointer()
	Color 0, 0, 0
	Rect 0,0,GraphicWidth,GraphicHeight,True
	MenuDrawStatic()

	SetFont Font2
	Color 255, 255, 255
	Local hx# = 48.0*UIScale
	Local hy# = GraphicHeight*0.08
	Local hw# = MenuSpacedText(hx#, hy#, "SQUAD", 10.0)
	Rect hx#, hy#+StringHeight("A")/2.0+8.0*UIScale, hw#, 1, False

	SetFont Font1
	Local tabH# = 32.0*UIScale
	Local tabY# = GraphicHeight*0.18
	Local tabGap# = 12.0*UIScale
	Local t1$ = "CAMPAIGN 01"
	Local t2$ = "CAMPAIGN 02"
	Local t3$ = "CAMPAIGN 03"
	Local w1# = StringWidth(t1$) + 24.0*UIScale
	Local w2# = StringWidth(t2$) + 24.0*UIScale
	Local w3# = StringWidth(t3$) + 24.0*UIScale
	Local tx# = (GraphicWidth-(w1#+w2#+w3#+2.0*tabGap#))/2.0
	SquadCampaignTab(tx#, tabY#, w1#, tabH#, t1$, True)
	tx# = tx# + w1# + tabGap#
	SquadCampaignTab(tx#, tabY#, w2#, tabH#, t2$, False)
	tx# = tx# + w2# + tabGap#
	SquadCampaignTab(tx#, tabY#, w3#, tabH#, t3$, False)

	Local bh# = GraphicHeight*0.27
	Local bw# = bh#*0.54
	Local gap# = 24.0*UIScale
	Local bx# = (GraphicWidth-(4*bw#+3*gap#))/2.0
	Local by# = (GraphicHeight-bh#)/2.0
	SetFont Font1
	Local nh# = StringHeight("A") + 12.0*UIScale
	Local occupied%

	For i% = 0 To 3
		Local x# = bx# + i%*(bw#+gap#)
		Color 80, 80, 80
		Rect x#, by#, bw#, bh#, False

		If SquadNames$(i%) = "" Then
			Color 80, 80, 80
			SquadDrawName(x#+bw#/2.0, by#+bh#-nh#/2.0, bw#-8.0*UIScale, "OPEN", True)
		Else
			occupied = occupied + 1
			If SquadImage <> 0 Then
				Viewport Int(x#+1.0*UIScale), Int(by#+1.0*UIScale), Int(bw#-2.0*UIScale), Int(bh#-nh#-1.0*UIScale)
				DrawImage SquadImage, x#+(bw#-ImageWidth(SquadImage))/2.0+bw#*0.15, by#+bh#-nh#-ImageHeight(SquadImage)-2.0*UIScale
				Viewport 0, 0, GraphicWidth, GraphicHeight
			EndIf
			If i% = SquadNetLocalSlot Then
				Color 80, 80, 80
				Rect x#+1.0*UIScale, by#+bh#-nh#, bw#-2.0*UIScale, nh#-1.0*UIScale, True
				Color 0, 0, 0
			Else
				Color 255, 255, 255
			EndIf
			SquadDrawName(x#+bw#/2.0, by#+bh#-nh#/2.0, bw#-8.0*UIScale, SquadNames$(i%), True)
			If i% = SquadNetLocalSlot Then MenuDrawBrackets(x#, by#, bw#, bh#)
		EndIf
	Next

	SetFont Font7
	Color 255, 255, 255
	If SquadNetLocalSlot >= 0 Then Text bx#+SquadNetLocalSlot*(bw#+gap#)+bw#/2.0, by#-14.0*UIScale, "YOU", True, True
	Color 200, 200, 200
	Text GraphicWidth/2.0, by#+bh#+24.0*UIScale, Str(occupied)+" / "+Str(NET_MAX_PLAYERS), True, True

	Local sw# = 420.0*UIScale
	Local sh# = 42.0*UIScale
	Local sx# = (GraphicWidth-sw#)/2.0
	Local sy# = GraphicHeight*0.76
	If SquadNetIsHost Then
		If SquadMenuButton(sx#, sy#, sw#, sh#, "START") Then
			MenuClick()
			SquadNetStartFloor()
		EndIf
	Else
		Color 80, 80, 80
		Rect sx#, sy#, sw#, sh#, True
		SetFont Font1
		Color 120, 120, 120
		SquadDrawName(GraphicWidth/2.0, sy#+sh#/2.0, sw#-36.0*UIScale, "WAITING FOR HOST")
	EndIf

	SetFont Font1
	Local lw# = 200.0*UIScale
	Local lh# = 30.0*UIScale
	If SquadMenuButton((GraphicWidth-lw#)/2.0, GraphicHeight*0.86, lw#, lh#, "LEAVE LOBBY") Then
		MenuClick()
		SquadNetDisconnect()
	EndIf

	SetFont Font7
	Color 200, 200, 200
	Text GraphicWidth/2.0, GraphicHeight*0.86+lh#+16.0*UIScale, SquadNetStatus$, True, True
	SetFont Font1
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

	If SquadNetNotice$ <> "" Then
		If MilliSecs() < SquadNetNoticeUntil Then
			SetFont Font7
			Color 200, 200, 200
			Text GraphicWidth/2.0, GraphicHeight-44.0*UIScale, SquadNetNotice$, True, True
			SetFont Font1
		Else
			SquadNetNotice$ = ""
		EndIf
	EndIf

	Color 255, 255, 255
	Text 24.0*UIScale, GraphicHeight-26.0*UIScale, MenuVersion$, False, False
End Function

Function UpdateMainMenu()
	If MenuContext <> 1 Then MenuSetup(1)
	SquadNetUpdate()

	If SquadOpen Then
		If SquadNetInLobby() Then
			SquadRefresh()
			MenuDrawSquad()
		Else
			MenuDrawMultiplayer()
		EndIf
		If SquadNetPendingStart Then
			SquadNetBeginGame()
			Return
		EndIf
		If KeyHit(1) Then
			If SquadNetInLobby() Then
				SquadNetDisconnect()
			Else
				SquadNetDisconnect()
				SquadOpen = False
			EndIf
			MenuClick()
		EndIf
		Return
	EndIf

	Local act% = MenuInput()
	MenuDraw()

	If act% = 0 Then
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
		SquadNetDisconnect()
		SquadOpen = True
	ElseIf act% = 2 Then
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

Function InputBox$(x%, y%, width%, height%, Txt$, ID% = 0, virtualKeyboardMode=0, alignLeft%=False)
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
	If alignLeft Then
		Text(x + 8 * UIScale, y + height / 2, Txt, False, True)
	Else
		Text(x + width / 2, y + height / 2, Txt, True, True)
	EndIf

	If (Not MouseOnBox) And MouseHit1 And SelectedInputBox = ID Then SelectedInputBox = 0

	If SelectedInputBox = ID Then
		Local cursorX# = x + width / 2 + StringWidth(Txt) / 2
		If alignLeft Then cursorX = x + 8 * UIScale + StringWidth(Txt)
		If (MilliSecs() Mod 800) < 400 Then Rect(cursorX + 2, y + height / 2 - 17 * UIScale / 2, 2, 17 * UIScale)
		Txt = TextInput(Txt)
	EndIf

	Return Txt
End Function

Global InvActionSelected%, InvOpenedAt%, InvInputHandled%
Global InvPreviewCam%, InvPreviewPivot%, InvPreviewMesh%
Global InvPreviewTemplate.ItemTemplates
Global InvSlideStart%, InvSlideDir%, InvIconSize%, InvIconsDirty%
Dim InvIcon%(32)

Function InvBig%()
	Return Int(GraphicHeight*0.34)
End Function

Function InvSmall%()
	Return Int(GraphicHeight*0.17)
End Function

Function InvCY#()
	Return GraphicHeight*0.46
End Function

Function InvSlotX#(k%)
	If k% = 0 Then Return GraphicWidth/2.0
	Local gap# = GraphicHeight*0.03
	Local d# = InvBig()/2.0 + gap# + InvSmall()/2.0 + (Abs(k%)-1)*(InvSmall()+gap#)
	Return GraphicWidth/2.0 + Sgn(k%)*d#
End Function

Function InvListX#()
	Return 48.0*UIScale
End Function

Function InvRowW#()
	Return 320.0*UIScale
End Function

Function InvActX#()
	Return GraphicWidth-48.0*UIScale-InvRowW()
End Function

Function InvActY#(i%)
	Return GraphicHeight*0.08-21.0*UIScale+i%*52.0*UIScale
End Function

Function InvActionLabel$(item.Items, i%)
	If i% = 1 Then Return "DROP"
	Return "USE"
End Function

Function InvDrawWrapped(x#, y#, txt$, maxw#, maxlines%)
	Local n% = 0
	While txt$ <> "" And n% < maxlines%
		Local cut% = Len(txt$)
		While cut% > 0 And StringWidth(Left(txt$, cut%)) > maxw#
			cut% = cut%-1
			While cut% > 0 And Mid(txt$, cut%+1, 1) <> " "
				cut% = cut%-1
			Wend
		Wend
		If cut% = 0 Then cut% = Len(txt$)
		Text x#, y#+n%*18.0*UIScale, Trim(Left(txt$, cut%)), False, False
		txt$ = Trim(Mid(txt$, cut%+1))
		n% = n%+1
	Wend
End Function

Function InventoryResetPreview()
	If InvPreviewMesh <> 0 Then FreeEntity InvPreviewMesh
	If InvPreviewCam <> 0 Then FreeEntity InvPreviewCam
	If InvPreviewPivot <> 0 Then FreeEntity InvPreviewPivot
	InvPreviewMesh = 0
	InvPreviewCam = 0
	InvPreviewPivot = 0
	InvPreviewTemplate = Null
	For i% = 0 To 32
		If InvIcon(i%) <> 0 Then
			FreeImage InvIcon(i%)
			InvIcon(i%) = 0
		EndIf
	Next
	InvIconsDirty = True
End Function

Function SetPreviewFullbright(entity%)
	If entity = 0 Then Return
	If EntityClass(entity) = "Mesh" Then EntityFX entity, 1
	For i% = 1 To CountChildren(entity)
		SetPreviewFullbright(GetChild(entity, i))
	Next
End Function

Function InvSetMesh(item.Items)
	If InvPreviewCam = 0 Then
		InvPreviewCam = CreateCamera()
		CameraClsColor InvPreviewCam, 0, 0, 0
		CameraClsMode InvPreviewCam, False, True
		CameraRange InvPreviewCam, 0.05, 4.0
		CameraProjMode InvPreviewCam, 0
		InvPreviewPivot = CreatePivot()
		PositionEntity InvPreviewPivot, 10000.0, 10000.0, 10000.0
		PositionEntity InvPreviewCam, 10000.0, 10000.0, 9998.4
		PointEntity InvPreviewCam, InvPreviewPivot
	EndIf

	If item\itemtemplate <> InvPreviewTemplate Then
		If InvPreviewMesh <> 0 Then FreeEntity InvPreviewMesh
		InvPreviewTemplate = item\itemtemplate
		InvPreviewMesh = CopyEntity(InvPreviewTemplate\obj, InvPreviewPivot)
		SetPreviewFullbright(InvPreviewMesh)

		Local cx# = 0.0
		Local cy# = 0.0
		Local cz# = 0.0
		Local maxdim# = 1.0
		Local mesh% = FindItemMesh(InvPreviewMesh)
		If mesh <> 0 Then
			GetMeshExtents(mesh)
			If Mesh_MagX >= 0.0 And Mesh_MagY >= 0.0 And Mesh_MagZ >= 0.0 Then
				cx = (Mesh_MinX+Mesh_MaxX)*0.5
				cy = (Mesh_MinY+Mesh_MaxY)*0.5
				cz = (Mesh_MinZ+Mesh_MaxZ)*0.5
				maxdim = Max(Mesh_MagX, Max(Mesh_MagY, Mesh_MagZ))
			EndIf
		EndIf
		If maxdim <= 0.0 Then maxdim = 1.0

		Local s# = 1.5/maxdim
		ScaleEntity InvPreviewMesh, s, s, s
		PositionEntity InvPreviewMesh, -cx*s, -cy*s, -cz*s
	EndIf
End Function

Function InvRender(x%, y%, w%, h%)
	SetBuffer BackBuffer()
	CameraProjMode Camera, 0
	CameraProjMode ark_blur_cam, 0
	CameraViewport InvPreviewCam, x%, y%, w%, h%
	CameraProjMode InvPreviewCam, 1
	ShowEntity InvPreviewMesh
	RenderWorld()
	HideEntity InvPreviewMesh
	CameraProjMode InvPreviewCam, 0
End Function

Function InvBuildIcons()
	InvIconSize = InvSmall()

	Local texSize% = 1
	While texSize < InvIconSize
		texSize = texSize*2
	Wend
	Local iconTex% = CreateTexture(texSize, texSize, 1)

	For i% = 0 To ItemAmount-1
		If InvIcon(i%) <> 0 Then FreeImage InvIcon(i%)
		InvSetMesh(InvItems(i%))
		CameraClsColor InvPreviewCam, 0, 0, 0
		RotateEntity InvPreviewPivot, 0, 25.0, 0
		CameraClsMode InvPreviewCam, True, True
		InvRender(0, 0, InvIconSize, InvIconSize)

		CopyRect 0, 0, InvIconSize, InvIconSize, 0, 0, BackBuffer(), TextureBuffer(iconTex)

		InvIcon(i%) = CreateImage(InvIconSize, InvIconSize)
		CopyRect 0, 0, InvIconSize, InvIconSize, 0, 0, TextureBuffer(iconTex), ImageBuffer(InvIcon(i%))
		MaskImage InvIcon(i%), 0, 0, 0
	Next

	FreeTexture iconTex

	CameraClsColor InvPreviewCam, 0, 0, 0
	CameraClsMode InvPreviewCam, False, True
	InvIconsDirty = False
End Function

Function InvSelectItem(i%)
	If i% < 0 Or i% >= ItemAmount Or i% = InvSelected Then Return
	InvSlideDir = Sgn(i%-InvSelected)
	InvSlideStart = MilliSecs()
	InvSelected = i%
	InvActionSelected = 0
	MenuMoveBar(InvActY(0))
	MenuHoverSound()
End Function

Function InvDoAction()
	Local item.Items = InvItems(InvSelected)
	If item = Null Then Return

	If InvActionSelected = 0 Then
		CreateConsoleMsg("Used "+item\itemtemplate\displayname)
		RemoveItem(item)
	Else
		DropItem(item)
	EndIf

	InvActionSelected = 0
	If InvSelected >= ItemAmount Then InvSelected = ItemAmount-1
	If InvSelected < 0 Then InvSelected = 0
	MenuMoveBar(InvActY(0))
	InvIconsDirty = True
	If ItemAmount <= 0 Then InventoryResetPreview()
End Function

Function InventoryInput()
	InvInputHandled = False

	If MainMenuOpen Then Return

	If KeyHit(KEY_INV) Then
		If InvOpen Then
			InvOpen = False
			HidePointer()
			UpdateMenuState()
		ElseIf (Not IsAnyMenuOpen()) And (Not IsZombie) And VomitTimer >= 0 And (Not UnableToMove) And SelectedDoor = Null And SelectedScreen = Null And EndingTimer = 0 Then
			InvOpen = True
			InvOpenedAt = MilliSecs()
			InvIconsDirty = True
			InvSlideStart = 0
			InvActionSelected = 0
			If InvSelected >= ItemAmount Then InvSelected = ItemAmount-1
			If InvSelected < 0 Then InvSelected = 0
			MenuBarPos = InvActY(0)
			MenuBarFrom = MenuBarPos
			MenuBarTarget = MenuBarPos
			MenuBarStart = MilliSecs()
			ShowPointer()
			UpdateMenuState()
		EndIf
		InvInputHandled = True
		Return
	EndIf

	If Not InvOpen Then Return

	If KeyHit(1) Then
		InvOpen = False
		HidePointer()
		UpdateMenuState()
		InvInputHandled = True
		Return
	EndIf

	If ItemAmount <= 0 Then Return

	If KeyHit(203) Or KeyHit(KEY_LEFT) Then InvSelectItem(InvSelected-1)
	If KeyHit(205) Or KeyHit(KEY_RIGHT) Then InvSelectItem(InvSelected+1)

	If MouseHit1 Then
		For k% = -3 To 3
			Local idx% = InvSelected+k%
			If k% <> 0 And idx% >= 0 And idx% < ItemAmount Then
				If MouseOn(InvSlotX(k%)-InvSmall()/2.0, InvCY()-InvSmall()/2.0, InvSmall(), InvSmall()) Then InvSelectItem(idx%)
			EndIf
		Next
	EndIf

	Local hit% = -1
	For i% = 0 To 1
		If MouseOn(InvActX(), InvActY(i%), InvRowW(), 42.0*UIScale) Then hit% = i%
	Next

	Local nsel% = InvActionSelected
	If hit% >= 0 Then nsel% = hit%
	If KeyHit(200) Or KeyHit(KEY_UP) Or KeyHit(208) Or KeyHit(KEY_DOWN) Then nsel% = 1-InvActionSelected
	If nsel% <> InvActionSelected Then
		InvActionSelected = nsel%
		MenuMoveBar(InvActY(nsel%))
		MenuHoverSound()
	EndIf

	If KeyHit(28) Or (MouseHit1 And hit% >= 0) Then
		MenuClick()
		InvDoAction()
	EndIf
End Function

Function DrawInventory()
	SquadNetUpdate()
	If Not InvOpen Then Return

	Local item.Items = Null
	If InvSelected >= 0 And InvSelected < ItemAmount Then item = InvItems(InvSelected)

	If item <> Null And InvIconsDirty Then InvBuildIcons()

	Color 0, 0, 0
	Rect 0, 0, GraphicWidth, GraphicHeight, True
	MenuDrawStatic()

	Local cx# = GraphicWidth/2.0
	Local cy# = InvCY()
	Local big% = InvBig()
	Local sm% = InvSmall()

	If item <> Null Then
		InvSetMesh(item)
		RotateEntity InvPreviewPivot, 0, ((MilliSecs()-InvOpenedAt) Mod 45000)*0.008, 0
		InvRender(Int(cx#-big%/2.0), Int(cy#-big%/2.0), big%, big%)
	EndIf

	SetFont Font2
	Color 255, 255, 255
	Local hy# = GraphicHeight*0.08
	Local hw# = MenuSpacedText(InvListX(), hy#, "INVENTORY", 10.0)
	Rect InvListX(), hy#+StringHeight("A")/2.0+8.0*UIScale, hw#, 1, False

	SetFont Font1
	If item = Null Then
		Color 255, 255, 255
		MenuSpacedText(cx#-StringWidth("EMPTY")/2.0, cy#, "EMPTY")
		Return
	EndIf

	Local t# = 1.0
	If InvSlideStart <> 0 Then t# = MenuEaseOut(Float(MilliSecs()-InvSlideStart)/140.0)
	For k% = -3 To 3
		Local idx% = InvSelected+k%
		If k% <> 0 And idx% >= 0 And idx% < ItemAmount Then
			If InvIcon(idx%) <> 0 Then
				Local ix# = InvSlotX(k%+InvSlideDir)*(1.0-t#) + InvSlotX(k%)*t#
				DrawImage InvIcon(idx%), ix#-sm%/2.0, cy#-sm%/2.0
			EndIf
		EndIf
	Next

	MenuDrawBrackets(cx#-big%/2.0, cy#-big%/2.0, big%, big%)

	SetFont Font7
	Color 200, 200, 200
	Text cx#, cy#+big%/2.0+18.0*UIScale, Str(InvSelected+1)+" / "+Str(ItemAmount), True, True

	Local ny# = GraphicHeight*0.73
	SetFont Font1
	Color 255, 255, 255
	MenuSpacedText(InvListX(), ny#, Upper(item\itemtemplate\displayname))
	SetFont Font7
	Color 200, 200, 200
	InvDrawWrapped(InvListX(), ny#+26.0*UIScale, item\itemtemplate\description, GraphicWidth*0.6, 3)

	SetFont Font1
	Local rh# = 42.0*UIScale
	MenuUpdateBar()
	Color 80, 80, 80
	Rect InvActX(), MenuBarPos, InvRowW(), rh#, True
	For i% = 0 To 1
		If i% = InvActionSelected Then
			Color 0, 0, 0
		Else
			Color 255, 255, 255
		EndIf
		MenuSpacedText(InvActX()+18.0*UIScale, InvActY(i%)+rh#/2.0, InvActionLabel(item, i%))
	Next
	MenuDrawBrackets(InvActX(), MenuBarPos, InvRowW(), rh#)
End Function
