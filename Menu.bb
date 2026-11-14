Global SelectedInputBox%
Global SavePath$ = "Saves\"
Global PrevSave$, CurrSave$
Global SelectedMap% = -1
Global MainMenuTab%

Global RandomSeed$, RandomSeedNumeric%, HasNumericSeed%
Function GetRandomSeed%()
	If HasNumericSeed Then Return RandomSeedNumeric Else Return GenerateSeedNumber(RandomSeed)
End Function

Function UpdateMainMenu()
	Color 0,0,0
	Rect 0,0,GraphicWidth,GraphicHeight,True

	ShowPointer()

	SetFont Font2
	Color 255,255,255
	Text GraphicWidth/2, GraphicHeight/2-170*UIScale, "ACE", True
	SetFont Font1

	Local w% = 280*UIScale
	Local x% = (GraphicWidth-w)/2
	Local y% = GraphicHeight/2-20*UIScale

	If DrawButton(x,y,w,50*UIScale,"START") Then
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
		HidePointer()
		FlushKeys()
		FlushMouse()
	EndIf

	If DrawButton(x,y+70*UIScale,w,50*UIScale,"QUIT") Then End
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

Function DrawButton%(x%, y%, width%, height%, txt$, bigfont% = False, waitForMouseUp%=False, disabled%=False)
	Local clicked% = False

	DrawFrame (x, y, width, height)
	If (Not disabled) And MouseOn(x, y, width, height) Then
		Color 45,45,45
		If (MouseHit1 And (Not waitForMouseUp)) Or (MouseUp1 And waitForMouseUp) Then
			clicked = True
			PlaySound_Strict(ButtonSFX)
		EndIf
		Rect(x + 4, y + 4, width - 8, height - 8)
	EndIf

	If disabled Then
		Color 100,100,100
	Else
		Color 255,255,255
	EndIf
	If bigfont Then SetFont Font2 Else SetFont Font1
	Text(x + width / 2, y + height / 2, txt, True, True)

	Return clicked
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
