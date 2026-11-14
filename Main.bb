Const VersionNumber$ = "0.0.1"
;Only change this if the version given isn't working with the current build version - ENDSHN
Const CompatibleNumber$ = "0.0.1"

SetDarkMode(True)

InitErrorMsgs(11, True)
SetErrorMsg(0, "An error occured in ACE")
SetErrorMsg(1, "Please send the generated minidump along with a screenshot of this window!")
SetErrorMsg(2, "---------------------------------------------------")
SetErrorMsg(3, "OS: " + SystemProperty("os") + " " + (32 + (GetEnv("ProgramFiles(X86)") <> 0) * 32) + " Bit (Build: " + SystemProperty("osbuild") + ")")
SetErrorMsg(4, "CPU: " + Trim(SystemProperty("cpuname")) + " (Arch: " + SystemProperty("cpuarch") + ", " + GetEnv("NUMBER_OF_PROCESSORS") + " Threads)")

SetErrorMsg(8, "Caught exception: " + "_CaughtError_")

Function CatchErrors(location$)
	SetErrorMsg(9, location)
End Function

Function RuntimeErrorExt%(Message$)
	SetErrorMsg(8, "Caught exception: " + Message)
	RuntimeError(Message)
End Function

Include "StrictLoads.bb"

Global DataDir$ = InitDataDir()
Global OptionFile$ = InitOptionsFile()
Global ModsFile$ = DataDir + "mods.ini"
Const OptionDefaultFile$ = "defaults.ini"

Function InitDataDir$()
	Local dir$ = GetEnv("AppData") + "\Krimbopple"
	If FileType(dir) <> 2 Then CreateDir(dir)
	dir = dir + "\ACE\"
	If FileType(dir) <> 2 Then CreateDir(dir)
	Return dir
End Function

Function InitOptionsFile$()
	Local file$ = DataDir + "options.ini"
	If FileType(file) <> 1 Lor HasCLIFlag("defaults") Lor HasCLIFlag("default") Then
		Local f% = WriteFile(file)
		CloseFile(f)
	EndIf
	Return file
End Function

Function GetOptionString$(section$, key$)
	Local opt$ = GetINIString(OptionFile, section, key)
	If opt = "" Then Return GetINIString(OptionDefaultFile, section, key)
	Return opt
End Function

Function GetOptionInt%(section$, key$)
	Return ParseINIInt(GetOptionString(section, key))
End Function

Function GetOptionFloat#(section$, key$)
	Return Float(GetOptionString(section, key))
End Function

Function HasCLIFlag%(name$)
	name = "-" + name
	Local cmd$ = CommandLine()
	Local pos% = Instr(cmd, " " + name + " ")
	If pos <> 0 Then Return True
	pos = Instr(cmd, name + " ")
	If pos = 1 Then Return True
	pos = Instr(cmd, " " + name)
	If pos <> 0 And pos = Len(cmd) - Len(name) Then Return True
	Return cmd = name
End Function

Function GetCLIInt%(name$, def%=0)
	Local txt$ = GetCLIString(name)
	If txt <> "" Then Return Int(txt)
	return def
End Function

Function GetCLIString$(name$, def$="")
	name = "-" + name + " "
	Local cmd$ = CommandLine()
	Local begin% = Instr(cmd, " " + name)
	If begin = 0 Then
		begin = Instr(cmd, name)
		If begin <> 1 Return def
	Else
		begin = begin + 1
	EndIf
	begin = begin + Len(name)
	Local end% = Instr(cmd, " ", begin)
	If end = 0 Then end = Len(cmd) + 1
	Return Trim(Mid(cmd, begin, end - begin))
End Function

Include "Blitz_File_FileName.bb"

Include "DevilParticleSystem.bb"

Global SteamLastStatus%
Global SteamActive% = False
Global SteamRichPresenceActive% = False
If SteamActive Then
	If Steam_RestartAppIfNecessary(2178380) Then Return
	If Steam_Init() <> 0 Then RuntimeErrorExt("Steam failed to initialize")
EndIf

; Used for rich presence
Global PlayerArea%

Global IsRestart% = False
.Start
Global IsRunning% = True
Global ShouldRestart% = False

Include "ModManager.bb"
Global HasDubbedAudio%
Global ModsEnabled% = GetOptionInt("general", "enable mods") And (Not HasCLIFlag("nomods"))
If ModsEnabled Then ReloadMods()

Global Font1%, Font2%, Font3%, Font4%, Font5%, Font6%, Font7%
Global ConsoleFont%

Global MenuWhite%, MenuBlack%
Global ButtonSFX% = LoadSound_Strict("SFX\Interact\Button.ogg")

Global DubbedAudio% = GetOptionInt("audio", "dubbed audio")
Global UsesDubbedAudio% = DubbedAudio And HasDubbedAudio

Global EnableSFXRelease% = GetOptionInt("audio", "sfx release")

Global SubtitlesEnabled% = GetOptionInt("audio", "subtitles")
Global ClosedCaptionsEnabled% = GetOptionInt("audio", "closed captions")

Global CanOpenConsole% = True

Global UseNumericSeeds% = GetOptionInt("general", "numeric seeds")

Dim ArrowIMG(4)

;[Block]

Const LauncherWidth% = 640
Const LauncherHeight% = 480
Global LauncherEnabled% = GetOptionInt("launcher", "launcher enabled")

Global GraphicWidth% = GetCLIInt("width", GetCLIInt("w", GetOptionInt("graphics", "width")))
Global GraphicHeight% = GetCLIInt("height", GetCLIInt("h", GetOptionInt("graphics", "height")))
If GraphicWidth <= 0 Then GraphicWidth = DesktopWidth()
If GraphicHeight <= 0 Then GraphicHeight = DesktopHeight()

Global Depth% = 0
Global Fullscreen% = GetOptionInt("graphics", "fullscreen")

Global ShowFPS = GetOptionInt("graphics", "show FPS")

Global WireframeState
Global IsBirthday = Left(CurrentDate(), 7) = "15 Apr " Lor HasCLIFlag("birthday")
Global IsHalloween = Left(CurrentDate(), 7) = "31 Oct " Lor HasCLIFlag("halloween")
Global BirthdayHat = False

Global BorderlessWindowed% = GetOptionInt("graphics", "borderless windowed")
Global RealGraphicWidth%,RealGraphicHeight%

; For borderless windowed
Global ScaledGraphicWidth%,ScaledGraphicHeight%
Global ScaledOffsetX%=0,ScaledOffsetY%=0

ApplyWindowModeCLIOverrides()

Function ApplyWindowModeCLIOverrides()
	If HasCLIFlag("noborder") Lor HasCLIFlag("borderless") Then BorderlessWindowed = True : Return
	If HasCLIFlag("fullscreen") Lor HasCLIFlag("full") Then BorderlessWindowed = False : Fullscreen = True : Return
	If HasCLIFlag("window") Lor HasCLIFlag("windowed") Lor HasCLIFlag("sw") Lor HasCLIFlag("startwindowed") Then BorderlessWindowed = False : Fullscreen = False
End Function

Global EnableRoomLights% = GetOptionInt("graphics", "room lights enabled")

Global TextureDetails% = GetOptionInt("graphics", "texture details")
Global TextureFloat#
Select TextureDetails%
	Case 0
		TextureFloat# = 0.8
	Case 1
		TextureFloat# = 0.4
	Case 2
		TextureFloat# = 0.0
	Case 3
		TextureFloat# = -0.4
	Case 4
		TextureFloat# = -0.8
End Select
Global ConsoleOpening% = GetOptionInt("console", "auto opening")
Global SFXVolume# = GetOptionFloat("audio", "sound volume")

Const StringsFile$ = "Data\strings.ini"
Include "Localization.bb"

Global I_Loc.LocalizationTable
If I_Loc <> Null Then Delete I_Loc ; Happens on reload
I_Loc = New LocalizationTable
For m.ActiveMods = Each ActiveMods
	Local modPath$ = m\Path + StringsFile
	If FileType(modPath) = 1 Then LoadLocalization(I_Loc, modPath)
Next
LoadLocalization(I_Loc, StringsFile)



Global GFXDriverName$ = GFXDriverName(1)

;New "fake fullscreen" - ENDSHN Psst, it's called borderless windowed mode --Love Mark,
If BorderlessWindowed
	DebugLog "Using Faked Fullscreen"
	Graphics3DExt DesktopWidth(), DesktopHeight(), 0, 4
	
	RealGraphicWidth = DesktopWidth()
	RealGraphicHeight = DesktopHeight()
	
	Local scaleX# = Float(RealGraphicWidth) / GraphicWidth, scaleY# = Float(RealGraphicHeight) / GraphicHeight
	Local scale# = Min(scaleX, scaleY)
	ScaledGraphicWidth% = scale * GraphicWidth
	ScaledGraphicHeight% = scale * GraphicHeight
	If scaleX > scaleY Then
		ScaledOffsetX = (RealGraphicWidth - ScaledGraphicWidth) / 2
	Else
		ScaledOffsetY = (RealGraphicHeight - ScaledGraphicHeight) / 2
	EndIf

	Fullscreen = False
Else
	RealGraphicWidth = GraphicWidth
	RealGraphicHeight = GraphicHeight
	ScaledGraphicWidth = GraphicWidth
	ScaledGraphicHeight = GraphicHeight
	If Fullscreen Then
		Graphics3DExt(GraphicWidth, GraphicHeight, 0, 1)
	Else
		Graphics3DExt(GraphicWidth, GraphicHeight, 0, 2)
	End If
EndIf

Global GrayScaleFX% = LoadEffect_Strict("GFX\shaders\Grayscale.fx")
Global UIScale# = CalculateMenuScale()
Global HUDScaleScalar# = Max(1.0, UIScale)
Global MinHUDScaleFactor#
Global MaxHUDScaleFactor#
CalculateHUDScaleFactorLimits()
Global HUDScaleFactor# = Max(MinHUDScaleFactor, Min(MaxHUDScaleFactor, GetOptionFloat("graphics", "hud scale factor")))
Global HUDScale# = HUDScaleScalar * HUDScaleFactor

Function CalculateHUDScaleFactorLimits()
	Local minHUDScale# = 1.0
	Local maxHUDScale# = Max(1.5, UIScale * 2)
	MinHUDScaleFactor = minHUDScale / HUDScaleScalar
	MaxHUDScaleFactor = maxHUDScale / HUDScaleScalar
End Function

Type HUDScaledImage
	Field Img%
	Field BaseWidth%
	Field BaseHeight%
End Type
Delete Each HUDScaledImage

Function LoadImageHUDScaled(file$, fixedSizeX% = 0, fixedSizeY% = 0)
	Local img%
	Local h.HUDScaledImage = New HUDScaledImage
	If fixedSizeX = 0 Then
		img = LoadImage_Strict(file, HUDScale, 8)
		h\BaseWidth = ImageWidthUnscaled(img) * LoadImageScaleResult
		h\BaseHeight = ImageHeightUnscaled(img) * LoadImageScaleResult
	Else
		If fixedSizeY = 0 Then fixedSizeY = fixedSizeX
		img = LoadImage_Strict(file, 0, 8)
		ResizeImageFast(img, fixedSizeX * HUDScale, fixedSizeY * HUDScale)
		h\BaseWidth = fixedSizeX
		h\BaseHeight = fixedSizeY
	EndIf
	h\Img = img
	Return img
End Function

Function FreeImageHUDScaled(img%)
	For h.HUDScaledImage = Each HUDScaledImage
		If h\Img = img Then
			Delete h
			Exit
		EndIf
	Next
	FreeImage(img)
End Function

Function UpdateHUDScaleFactor(newFactor#)
	If newFactor = HUDScaleFactor Then Return

	HUDScaleFactor = newFactor
	HUDScale = HUDScaleScalar * HUDScaleFactor
	For h.HUDScaledImage = Each HUDScaledImage
		ResizeImageFast(h\Img, h\BaseWidth * HUDScale, h\BaseHeight * HUDScale)
	Next

	; Hardcoding because I'm LAME!
	FreeFont Font3 : FreeFont Font4
	Font3% = LoadFont_Strict("GFX\font\arial\Arial.ttf", Int(15 * HUDScale))
	Font4% = LoadFont_Strict("GFX\font\arial\Arial.ttf", Int(42 * HUDScale))
End Function

Function CalculateMenuScale#()
	Local short% = Min(GraphicWidth, GraphicHeight)
	If short > 1024 Then Return short / 1024.0
	If short > 840 Then Return 1
	Return short / 840.0
End Function

SetBuffer(BackBuffer())

Global CurTime%, PrevTime%, LoopDelay%, AccumulatedLoopDelay#, FPSfactor#, FPSfactor2#, PrevFPSFactor#
Global TimeScale# = 1.0
Local CheckFPS%, ElapsedLoops%, FPS%

Global Framelimit% = GetOptionInt("graphics", "framelimit")
Global Vsync% = GetOptionInt("graphics", "vsync")

Global Opt_AntiAlias = GetOptionInt("graphics", "antialias")

Global CurrFrameLimit# = (Framelimit%-17)/280.0

Global ScreenGamma# = GetOptionFloat("graphics", "screengamma")
;If Fullscreen Then UpdateScreenGamma()

Global ViewBobScale# = GetOptionFloat("graphics", "view bob")
Global BobPhase#, BobPowerSmooth#

Global FOV% = GetOptionInt("graphics", "fov")
Const DEFAULT_FOV% = 59

Global HUDStartX%, HUDEndX%, HUDStartY%, HUDEndY%
Global HUDOffsetScale# = GetOptionFloat("graphics", "hud offset")

UpdateHUDOffsets()

Function UpdateHUDOffsets()
	If GraphicWidth > GraphicHeight Then
		HUDStartY = 0 : HUDEndY = GraphicHeight
		HUDStartX = Int(HUDOffsetScale * GraphicWidth / 2)
		HUDEndX = GraphicWidth - HUDStartX
	Else
		HUDStartX = 0 : HUDEndX = GraphicWidth
		HUDStartY = Int(HUDOffsetScale * GraphicHeight / 2)
		HUDEndY = GraphicHeight - HUDStartY
	EndIf
End Function

Const HIT_MAP% = 1, HIT_PLAYER% = 2, HIT_ITEM% = 3, HIT_APACHE% = 4, HIT_DEAD% = 6
SeedRnd MilliSecs()

;[End block]

Global GameSaved%

Global CanSave% = True

AppTitle "ACE"

PlayStartupVideos()

;---------------------------------------------------------------------------------------------------------------------

;[Block]

Global CursorIMG% = LoadImage_Strict("GFX\cursor.png", 1.0)


Font1% = LoadFont_Strict("GFX\font\arial\Arial.ttf", Int(19 * UIScale))
Font2% = LoadFont_Strict("GFX\font\arial\Arialbd.ttf", Int(52 * UIScale))
Font3% = LoadFont_Strict("GFX\font\arial\Arial.ttf", Int(15 * HUDScale))
Font4% = LoadFont_Strict("GFX\font\arial\Arial.ttf", Int(42 * HUDScale))
Font5% = LoadFont_Strict("GFX\font\arial\Arialbd.ttf", Int(58 * UIScale))
Font6% = LoadFont_Strict("GFX\font\arial\Arial.ttf", Int(25 * UIScale))
Font7% = LoadFont_Strict("GFX\font\arial\Arialbd.ttf", Int(13 * UIScale))

Global CreditsFont%,CreditsFont2%

ConsoleFont% = Font1

Include "Subtitles.bb"
LoadSubtitles()

SetFont Font2
PlayEpilepsyWarning()

Global BlinkMeterIMG% = LoadImageHUDScaled("GFX\blinkmeter.png")
Global MenuMeterIMG% = LoadImage_Strict("GFX\blinkmeter.png", UIScale)

; - -Viewport.
Global viewport_center_x% = RealGraphicWidth / 2, viewport_center_y% = RealGraphicHeight / 2

; -- Mouselook.
Global mouselook_x_inc# = 0.3 ; This sets both the sensitivity and direction (+/-) of the mouse on the X axis.
Global mouselook_y_inc# = 0.3 ; This sets both the sensitivity and direction (+/-) of the mouse on the Y axis.
Global mouse_x_speed_1#, mouse_y_speed_1#

Global MoveInputCancelling% = GetOptionInt("general", "move input cancelling")

Global KEY_RIGHT = GetOptionInt("binds", "Right key")
Global KEY_LEFT = GetOptionInt("binds", "Left key")
Global KEY_UP = GetOptionInt("binds", "Up key")
Global KEY_DOWN = GetOptionInt("binds", "Down key")

Global KEY_BLINK = GetOptionInt("binds", "Blink key")
Global KEY_SPRINT = GetOptionInt("binds", "Sprint key")
Global KEY_INV = GetOptionInt("binds", "Inventory key")
Global KEY_CROUCH = GetOptionInt("binds", "Crouch key")
Global KEY_SAVE = GetOptionInt("binds", "Save key")
Global KEY_CONSOLE = 61

Global MouseSmooth# = GetOptionFloat("controls", "mouse smoothing")

Global Mesh_MinX#, Mesh_MinY#, Mesh_MinZ#
Global Mesh_MaxX#, Mesh_MaxY#, Mesh_MaxZ#
Global Mesh_MagX#, Mesh_MagY#, Mesh_MagZ#

;player stats -------------------------------------------------------------------------------------------------------
Global KillTimer#, KillAnim%, FallTimer#, DeathTimer#
Global Sanity#, ForceMove#, ForceAngle#
Global RestoreSanity%

Global Playable% = True

Global BLINKFREQ#
Global BlinkTimer#, EyeIrritation#, EyeStuck#, BlinkEffect# = 1.0, BlinkEffectTimer#

Global Stamina#, StaminaEffect#=1.0, StaminaEffectTimer#

Global CameraShakeTimer#, Vomit%, VomitTimer#, Regurgitate%

Global SCP1025state#[6]

Global HeartBeatRate#, HeartBeatTimer#, HeartBeatVolume#

Global WearingGasMask%, WearingHazmat%, WearingVest%, Wearing714%, WearingNightVision%, WearingPumpkin%
Global NVTimer#

Global SuperMan%, SuperManTimer#

Global LostHands%

Global Injuries#, Bloodloss#, Infect#, HealTimer#

Global RefinedItems%


;player coordinates, angle, speed, movement etc ---------------------------------------------------------------------
Global DropSpeed#, HeadDropSpeed#, CurrSpeed#
Global user_camera_pitch#, side#
Global Crouch%, CrouchState#

Global PlayerZone%, PlayerRoom.Rooms

Global GrabbedEntity%

Global InvertMouse% = GetOptionInt("controls", "invert mouse y")
Global MouseHit1%, MouseDown1%, MouseHit2%, DoubleClick%, LastMouseHit1%, LastMouseHit1X%, LastMouseHit1Y%, MouseUp1%

Global GodMode%, NoClip%, NoClipSpeed# = 2.0

Global CoffinDistance# = 100.0

Global PlayerSoundVolume#

;camera/lighting effects (blur, camera shake, etc)-------------------------------------------------------------------
Global Shake#

Global ExplosionTimer#, ExplosionSFX%

Global LightsOn% = True

Global SoundTransmission%

;menus, GUI ---------------------------------------------------------------------------------------------------------
Global MainMenuOpen%, MenuOpen%, StopHidingTimer#, InvOpen%
Global OtherOpen.Items = Null

Global SelectedEnding$, EndingScreen%, EndingTimer#

Global MsgTimer#, Msg$, DeathMSG$

Global AccessCode%, KeypadInput$, KeypadTimer#, KeypadMSG$

Const HARPCODE% = 7816

Global DrawHandIcon%
Dim DrawArrowIcon%(4)

;misc ---------------------------------------------------------------------------------------------------------------


Global MTFtimer#, MTFrooms.Rooms[10], MTFroomState%[10]

Dim RadioState#(10)
Dim RadioState3%(10)
Dim RadioState4%(9)
Dim RadioCHN%(8)

Dim OldAiPics%(5)

Global SpeedRunMode% = GetOptionInt("general", "speed run mode")
Global PlayTime%, DeathTime%
; 0 = Running; 1 = Stopped; 2 = Pre-made save loaded; 3 = Ending reached
Global TimerStopped% = True
Global PreMadeSaveLoaded% = False
Global ConsoleFlush%
Global ConsoleFlushSnd% = 0, ConsoleMusFlush% = 0, ConsoleMusPlay% = 0

Global InfiniteStamina% = False
Global NVBlink%
Global IsNVGBlinking% = False

;[End block]


;----------------------------------------------  Console -----------------------------------------------------

Global ConsoleOpen%, ConsoleInput$
Global ConsoleScroll#,ConsoleScrollDragging%
Global ConsoleMouseMem%
Global ConsoleReissue.ConsoleMsg = Null
Global ConsoleR% = 255,ConsoleG% = 255,ConsoleB% = 255
Global ConsoleQuiet% = False

Type ConsoleMsg
	Field txt$
	Field isCommand%
	Field r%,g%,b%
End Type

Include "Bindings.bb"
Include "Console.bb"

If FileType(DataDir + "cfg\autoexec.cfg") = 1 Then
ExecuteCfg(DataDir + "cfg\autoexec.cfg") EndIf

Function ExecAutoExec()
	Local autoExec$ = DataDir + "autoexec.cfg"
	If FileType(autoExec) = 1 Then
		ExecuteCfg(autoExec)
	Else
		Local f% = WriteFile(autoExec)
		WriteLine(f, "; Enter commands here to execute at each startup and load!")
		CloseFile(f)
	EndIf
End Function

Function CreateConsoleMsg(txt$,r%=-1,g%=-1,b%=-1,isCommand%=False)
	If ConsoleQuiet Then Return

	Local c.ConsoleMsg = New ConsoleMsg
	Insert c Before First ConsoleMsg
	
	c\txt = txt
	c\isCommand = isCommand
	
	c\r = r
	c\g = g
	c\b = b
	
	If (c\r<0) Then c\r = ConsoleR
	If (c\g<0) Then c\g = ConsoleG
	If (c\b<0) Then c\b = ConsoleB
End Function

Function UpdateConsole()
	
	If CanOpenConsole = False Then
		ConsoleOpen = False
		Return
	EndIf
	
	If ConsoleOpen Then
		Local cm.ConsoleMsg
		
		SetFont ConsoleFont
		
		ConsoleR = 255 : ConsoleG = 255 : ConsoleB = 255
		
		Local x% = 0, y% = GraphicHeight-300*UIScale, width% = GraphicWidth, height% = 300*UIScale-30*UIScale
		Local StrTemp$, StrTemp2$, StrTemp3$
		Local temp%,  i%
		DrawFrame x,y,width,height+30*UIScale
		
		Local consoleHeight% = 15*UIScale
		Local scrollbarHeight% = 0
		For cm.ConsoleMsg = Each ConsoleMsg
			consoleHeight = consoleHeight + 15*UIScale
		Next
		If consoleHeight = 0 Then
			scrollbarHeight = height
		Else
			scrollbarHeight = (Float(height)/Float(consoleHeight))*height
		EndIf
		If scrollbarHeight>height Then scrollbarHeight = height
		If consoleHeight<height Then consoleHeight = height
		
		Color 50,50,50
		Local inBar% = MouseOn(x+width-26*UIScale,y,26*UIScale,height)
		If inBar Then Color 70,70,70
		Rect x+width-26*UIScale,y,26*UIScale,height,True
		
		
		Color 120,120,120
		Local inBox% = MouseOn(x+width-23*UIScale,y+height-scrollbarHeight+(ConsoleScroll*scrollbarHeight/height),20*UIScale,scrollbarHeight)
		If inBox Then Color 200,200,200
		If ConsoleScrollDragging Then Color 255,255,255
		Rect x+width-23*UIScale,y+height-scrollbarHeight+(ConsoleScroll*scrollbarHeight/height),20*UIScale,scrollbarHeight,True
		
		If Not MouseDown(1) Then
			ConsoleScrollDragging=False
		ElseIf ConsoleScrollDragging Then
			ConsoleScroll = ConsoleScroll+((ScaledMouseY()-ConsoleMouseMem)*height/scrollbarHeight)
			ConsoleMouseMem = ScaledMouseY()
		EndIf
		
		If (Not ConsoleScrollDragging) Then
			If MouseHit1 Then
				If inBox Then
					ConsoleScrollDragging=True
					ConsoleMouseMem = ScaledMouseY()
				ElseIf inBar Then
					ConsoleScroll = ConsoleScroll+((ScaledMouseY()-(y+height))*consoleHeight/height+(height/2))
					ConsoleScroll = ConsoleScroll/2
				EndIf
			EndIf
		EndIf
		
		ConsoleScroll = ConsoleScroll - 15*UIScale * MouseZSpeed()
		
		Local reissuePos%
		If KeyHit(200) Then
			reissuePos% = 0
			If (ConsoleReissue=Null) Then
				ConsoleReissue=First ConsoleMsg
				
				While (ConsoleReissue<>Null)
					If (ConsoleReissue\isCommand) Then
						Exit
					EndIf
					reissuePos = reissuePos - 15*UIScale
					ConsoleReissue = After ConsoleReissue
				Wend
				
			Else
				cm.ConsoleMsg = First ConsoleMsg
				While cm<>Null
					If cm=ConsoleReissue Then Exit
					reissuePos = reissuePos-15*UIScale
					cm = After cm
				Wend
				ConsoleReissue = After ConsoleReissue
				reissuePos = reissuePos-15*UIScale
				
				While True
					If (ConsoleReissue=Null) Then
						ConsoleReissue=First ConsoleMsg
						reissuePos = 0
					EndIf
				
					If (ConsoleReissue\isCommand) Then
						Exit
					EndIf
					reissuePos = reissuePos - 15*UIScale
					ConsoleReissue = After ConsoleReissue
				Wend
			EndIf
			
			If ConsoleReissue<>Null Then
				ConsoleInput = ConsoleReissue\txt
				ConsoleScroll = reissuePos+(height/2)
			EndIf
		EndIf
		
		If KeyHit(208) Then
			reissuePos% = -consoleHeight+15*UIScale
			If (ConsoleReissue=Null) Then
				ConsoleReissue=Last ConsoleMsg
				
				While (ConsoleReissue<>Null)
					If (ConsoleReissue\isCommand) Then
						Exit
					EndIf
					reissuePos = reissuePos + 15*UIScale
					ConsoleReissue = Before ConsoleReissue
				Wend
				
			Else
				cm.ConsoleMsg = Last ConsoleMsg
				While cm<>Null
					If cm=ConsoleReissue Then Exit
					reissuePos = reissuePos+15*UIScale
					cm = Before cm
				Wend
				ConsoleReissue = Before ConsoleReissue
				reissuePos = reissuePos+15*UIScale
				
				While True
					If (ConsoleReissue=Null) Then
						ConsoleReissue=Last ConsoleMsg
						reissuePos=-consoleHeight+15*UIScale
					EndIf
				
					If (ConsoleReissue\isCommand) Then
						Exit
					EndIf
					reissuePos = reissuePos + 15*UIScale
					ConsoleReissue = Before ConsoleReissue
				Wend
			EndIf
			
			If ConsoleReissue<>Null Then
				ConsoleInput = ConsoleReissue\txt
				ConsoleScroll = reissuePos+(height/2)
			EndIf
		EndIf
		
		If ConsoleScroll<-consoleHeight+height Then ConsoleScroll = -consoleHeight+height
		If ConsoleScroll>0 Then ConsoleScroll = 0
		
		Color 255, 255, 255
		
		SelectedInputBox = 2
		Local oldConsoleInput$ = ConsoleInput
		ConsoleInput = InputBox(x, y + height, width, 30*UIScale, ConsoleInput, 2, -1)
		If oldConsoleInput<>ConsoleInput Then
			ConsoleReissue = Null
		EndIf
		ConsoleInput = Left(ConsoleInput, 100)
		
		If KeyHit(28) And ConsoleInput <> "" Then
			ExecuteConsoleCommand(ConsoleInput)
			ConsoleInput = ""
		End If
		
		Local TempY% = y + height - 25*UIScale - ConsoleScroll
		Local count% = 0
		For cm.ConsoleMsg = Each ConsoleMsg
			count = count+1
			If count>1000 Then
				Delete cm
			Else
				If TempY >= y And TempY < y + height - 20*UIScale Then
					If cm=ConsoleReissue Then
						Color cm\r/4,cm\g/4,cm\b/4
						Rect x+3*UIScale,TempY-2*UIScale,width-30*UIScale,24*UIScale,True
					EndIf
					Color cm\r,cm\g,cm\b
					If cm\isCommand Then
						Text(x + 20*UIScale, TempY+5*UIScale, "> "+cm\txt)
					Else
						Text(x + 20*UIScale, TempY+5*UIScale, cm\txt)
					EndIf
				EndIf
				TempY = TempY - 15*UIScale
			EndIf
			
		Next
		
		Color 255,255,255
		
		If Fullscreen Then DrawImage CursorIMG, ScaledMouseX(),ScaledMouseY()
	End If
	
	SetFont Font1
	
End Function

ConsoleR = 0 : ConsoleG = 255 : ConsoleB = 255
CreateConsoleMsg("Console commands: ")
CreateConsoleMsg("  - godmode [on/off] | noclip [on/off] | kill")
CreateConsoleMsg("  - teleport [room] | tele [x y z]")
CreateConsoleMsg("  - showfps [on/off]")

Function Teleport(r.Rooms)
	PositionEntity (Collider, EntityX(r\obj), EntityY(r\obj)+0.3, EntityZ(r\obj))
	ResetEntity(Collider)
	MouseLook(False)
	UpdateDoors()
	UpdateRooms()
	For it.Items = Each Items
		it\disttimer = 0
	Next
	PlayerRoom = r
	UpdateRooms()
	UpdateDoorsTimer = 0
	UpdateDoors()
	UpdateRoomLightsTimer = 0
	UpdateRoomLights(Camera)
End Function

;---------------------------------------------------------------------------------------------------

Global DebugHUD%

Global BlurVolume#, BlurTimer#

Global LightBlink#, LightFlash#

Global BumpEnabled% = GetOptionInt("graphics", "bump mapping enabled")
Global HUDenabled% = GetOptionInt("graphics", "HUD enabled")
Global HUDenabledLegacy% = GetOptionInt("graphics", "HUD enabled legacy")

Global Camera%, CameraShake#, CurrCameraZoom#

Global Brightness%
Global CameraFogNear#
Global CameraFogFar#

Global StoredCameraFogFar# = CameraFogFar

Global MouseSens# = GetOptionFloat("controls", "mouse sensitivity")

Global ark_blur_cam%

Function CreateOverlayCamera()
	Local cam% = CreateCamera()
	CameraProjMode cam,2
	CameraZoom cam,0.1
	CameraClsMode cam, 0, 0
	CameraRange cam, 0.1, 1.5
	MoveEntity cam, 0, 0, 10000
	ark_blur_cam = cam
	CameraViewport cam,0,0,GraphicWidth,GraphicHeight
End Function

Dim LightSpriteTex(10)

;----------------------------------------------  Sounds -----------------------------------------------------

;[Block]

Global SoundEmitter%
Global TempSounds%[10]
Global TempSoundCHN%
Global TempSoundIndex% = 0

;The Music now has to be pre-defined, as the new system uses streaming instead of the usual sound loading system Blitz3D has
Dim Music$(40)
Music(0) = "The Dread"
Music(1) = "HeavyContainment"
Music(2) = "EntranceZone"
Music(3) = "PD"
Music(4) = "079"
Music(5) = "GateB1"
Music(6) = "GateB2"
Music(7) = "Room3Storage"
Music(8) = "Room049"
Music(9) = "8601"
Music(10) = "106"
Music(11) = "Menu"
Music(12) = "8601Cancer"
Music(13) = "Intro"
Music(15) = "PDTrench"
Music(16) = "205"
Music(17) = "GateA"
Music(18) = "1499"
Music(19) = "1499Danger"
Music(20) = "049Chase"
Music(21) = "..\Ending\MenuBreath"
Music(22) = "914"
Music(23) = "Ending"
Music(24) = "Credits"
Music(25) = "SaveMeFrom"
Music(26) = "HalloweenFields"

Global MusicVolume# = GetOptionFloat("audio", "music volume")
;Global MusicCHN% = StreamSound_Strict("SFX\Music\"+Music(2)+".ogg", MusicVolume, CurrMusicStream)

Global CurrMusicStream, MusicCHN
MusicCHN = StreamSound_Strict("SFX\Music\"+Music(2)+".ogg",MusicVolume)

Global CurrMusicVolume# = 1.0, NowPlaying%=2, ShouldPlay%=11
Global CurrMusic% = 1

Dim OpenDoorSFX%(3,3), CloseDoorSFX%(3,3)

Global ButtonSFX2 

Global OpenDoorFastSFX
Global CautionSFX% 

Global NuclearSirenSFX%

Global CameraSFX  

Global StoneDragSFX% 

Global GunshotSFX% 
Global Gunshot2SFX% 
Global Gunshot3SFX% 
Global BullethitSFX% 

Global TeslaIdleSFX 
Global TeslaActivateSFX 
Global TeslaPowerUpSFX 

Global MagnetUpSFX%, MagnetDownSFX
Global FemurBreakerSFX%
Global EndBreathCHN%
Global EndBreathSFX%

Dim DecaySFX%(5)

Global BurstSFX 

Dim RustleSFX%(3)

Global Use914SFX%
Global Death914SFX% 

Dim DripSFX%(4)

Global LeverSFX%, LightSFX% 
Global ButtGhostSFX% 

Dim RadioSFX(5,10) 

Global RadioSquelch 
Global RadioStatic 
Global RadioBuzz 

Global ElevatorBeepSFX, ElevatorMoveSFX  

Dim PickSFX%(10)

Global AmbientSFXCHN%, CurrAmbientSFX%
Dim AmbientSFXAmount(6)
;0 = light containment, 1 = heavy containment, 2 = entrance
AmbientSFXAmount(0)=8 : AmbientSFXAmount(1)=11 : AmbientSFXAmount(2)=12
;3 = general, 4 = pre-breach
AmbientSFXAmount(3)=15 : AmbientSFXAmount(4)=5
;5 = forest
AmbientSFXAmount(5)=10

Dim AmbientSFX%(6, 15)

Dim OldManSFX%(8)

Dim Scp173SFX%(3)

Dim HorrorSFX%(20)

Dim IntroSFX%(20)

;IntroSFX(13) = LoadSound_Strict("SFX\intro\shoot1.ogg")
;IntroSFX(14) = LoadSound_Strict("SFX\intro\shoot2.ogg")


Dim AlarmSFX%(5)

Dim CommotionState%(25)

Global HeartBeatSFX 

Global VomitSFX%

Dim BreathSFX(2,5)
Global BreathCHN%


Dim NeckSnapSFX(3)

Dim DamageSFX%(9)

Dim MTFSFX%(8)

Dim CoughSFX%(3)
Global CoughCHN%, VomitCHN%

Global MachineSFX% 
Global ApacheSFX
Global CurrStepSFX
Dim StepSFX%(5, 2, 8) ;(normal/metal, walk/run, id)

Dim Step2SFX(6)

;[End block]

;New Sounds and Meshes/Other things in SCP:CB 1.3 - ENDSHN
;[Block]
;Global NTF_1499EnterSFX% = LoadSound_Strict("SFX\SCP\1499\Enter.ogg")
;Global NTF_1499LeaveSFX% = LoadSound_Strict("SFX\SCP\1499\Exit.ogg")

Global PlayCustomMusic% = False, CustomMusic% = 0

Global Monitor2, Monitor3, MonitorTexture2, MonitorTexture3, MonitorTexture4, MonitorTextureOff
Global MonitorTimer# = 0.0, MonitorTimer2# = 0.0, UpdateCheckpoint1%, UpdateCheckpoint2%

;This variable is for when a camera detected the player
	;False: Player is not seen (will be set after every call of the Main Loop
	;True: The Player got detected by a camera
Global PlayerDetected%
Global PrevInjuries#,PrevBloodloss#
Global NoTarget% = False

Global GuaranteedOmni% = False

Global NVGImages[2]
NVGImages[0] = LoadImageHUDScaled("GFX\battery_green.png")
NVGImages[1] = LoadImageHUDScaled("GFX\battery_blue.png")

Global Wearing1499% = False
Global AmbientLight%, AmbientLightNVG%
Global AmbientLightRoomTex%, AmbientLightRoomVal%

Global EnableUserTracks% = GetOptionInt("audio", "enable user tracks")
Global UserTrackMode% = GetOptionInt("audio", "user track setting")
Global UserTrackCheck% = -1, UserTrackCheck2% = 0
Global UserTrackMusicAmount% = 0, CurrUserTrack%, UserTrackFlag% = False
Dim UserTrackName$(256)

Global NTF_1499PrevX#
Global NTF_1499PrevY#
Global NTF_1499PrevZ#
Global NTF_1499PrevRoom.Rooms
Global NTF_1499X#
Global NTF_1499Y#
Global NTF_1499Z#
Global NTF_1499Sky%

Global OptionsMenu% = 0
Global QuitMSG% = 0

Global InFacility% = True

Global PrevSFXVolume# = SFXVolume#
Global DeafPlayer% = False
Global DeafTimer# = 0.0

Global IsZombie% = False

Global room2gw_brokendoor% = False
Global room2gw_x# = 0.0
Global room2gw_z# = 0.0

Global menuroomscale# = 8.0 / 2048.0

Global ParticleAmount% = GetOptionInt("graphics","particle amount")

Dim NavImages(5)
For i = 0 To 3
	NavImages(i) = LoadImageHUDScaled("GFX\navigator\roomborder"+i+".png")
Next
NavImages(4) = LoadImageHUDScaled("GFX\navigator\batterymeter.png")

Global NavBG%

Global LightConeModel

Global ParticleEffect[7]

Const MaxDTextures=9
Global DTextures[MaxDTextures]

Global NPC049OBJ, NPC0492OBJ
Global ClerkOBJ

Global IntercomStreamCHN%

Global ForestNPC,ForestNPCTex,ForestNPCData#[3]
;[End Block]

;-----------------------------------------  Images ----------------------------------------------------------

Global PauseMenuIMG% = 0

Global SprintIcon% = 0
Global BlinkIcon% = 0
Global CrouchIcon% = 0
Global HandIcon% = 0
Global HandIconStump% = 0
Global HandIcon2% = 0

Function GetUsedHandIcon%()
	If LostHands Then
		If HandIconStump = 0 Then HandIconStump = LoadImageHUDScaled("GFX\Halloween\handsymbol_stump.png")
		Return HandIconStump
	EndIf
	Return HandIcon
End Function

Global StaminaMeterIMG% = 0

Global Panel294 = 0, Using294%, Input294$

;----------------------------------------------  Items  -----------------------------------------------------

Include "Items.bb"

;--------------------------------------- Particles ------------------------------------------------------------

Include "Particles.bb"

;-------------------------------------  Doors --------------------------------------------------------------

Global ClosestButton%, ClosestDoor.Doors
Global SelectedDoor.Doors, UpdateDoorsTimer#
Global DoorTempID%
Type Doors
	Field obj%, obj2%, frameobj%, buttons%[2]
	Field locked%, open%, angle%, openstate#, fastopen%
	Field dir%
	Field timer%, timerstate#
	Field room.Rooms
	
	Field DisableWaypoint%
	
	Field dist#
	
	Field SoundCHN%
	
	Field Code$
	
	Field ID%
	
	Field Level%
	Field LevelDest%
	
	Field AutoClose%
	
	Field LinkedDoor.Doors
	
	Field IsElevatorDoor% = False
	
	Field MTFClose% = True
	Field NPCCalledElevator% = False
	
	Field DoorHitOBJ%
End Type 

Dim BigDoorOBJ(2), HeavyDoorObj(2)
Dim OBJTunnel(7)

Function CreateDoor.Doors(lvl, x#, y#, z#, angle#, room.Rooms, dopen% = False,  big% = False, code$="", useCollisionMesh% = False)
	Local d.Doors, parent, i%
	If room <> Null Then parent = room\obj
	
	Local d2.Doors
	
	d.Doors = New Doors
	If big=1 Then
		d\obj = CopyEntity(BigDoorOBJ(0))
		ScaleEntity(d\obj, 55 * RoomScale, 55 * RoomScale, 55 * RoomScale)
		d\obj2 = CopyEntity(BigDoorOBJ(1))
		ScaleEntity(d\obj2, 55 * RoomScale, 55 * RoomScale, 55 * RoomScale)
		
		d\frameobj = CopyEntity(DoorColl)	;CopyMesh				
		ScaleEntity(d\frameobj, RoomScale, RoomScale, RoomScale)
		EntityType d\frameobj, HIT_MAP
		EntityAlpha d\frameobj, 0.0
	ElseIf big=2 Then
		d\obj = CopyEntity(HeavyDoorObj(0))
		ScaleEntity(d\obj, RoomScale, RoomScale, RoomScale)
		d\obj2 = CopyEntity(HeavyDoorObj(1))
		ScaleEntity(d\obj2, RoomScale, RoomScale, RoomScale)
		
		d\frameobj = CopyEntity(DoorFrameOBJ)
	ElseIf big=3 Then
		For d2 = Each Doors
			If d2 <> d And d2\dir = 3 Then
				d\obj = CopyEntity(d2\obj)
				d\obj2 = CopyEntity(d2\obj2)
				ScaleEntity d\obj, RoomScale, RoomScale, RoomScale
				ScaleEntity d\obj2, RoomScale, RoomScale, RoomScale
				Exit
			EndIf
		Next
		If d\obj=0 Then
			d\obj = LoadMesh_Strict("GFX\map\elevatordoor.b3d")
			d\obj2 = CopyEntity(d\obj)
			ScaleEntity d\obj, RoomScale, RoomScale, RoomScale
			ScaleEntity d\obj2, RoomScale, RoomScale, RoomScale
		EndIf
		d\frameobj = CopyEntity(DoorFrameOBJ)
	Else
		d\obj = CopyEntity(DoorOBJ)
		ScaleEntity(d\obj, (204.0 * RoomScale) / MeshWidth(d\obj), 312.0 * RoomScale / MeshHeight(d\obj), 16.0 * RoomScale / MeshDepth(d\obj))
		
		d\frameobj = CopyEntity(DoorFrameOBJ)
		d\obj2 = CopyEntity(DoorOBJ)
		
		ScaleEntity(d\obj2, (204.0 * RoomScale) / MeshWidth(d\obj), 312.0 * RoomScale / MeshHeight(d\obj), 16.0 * RoomScale / MeshDepth(d\obj))
		;entityType d\obj2, HIT_MAP
	End If
	
	;scaleentity(d\obj, 0.1, 0.1, 0.1)
	PositionEntity d\frameobj, x, y, z	
	ScaleEntity(d\frameobj, (8.0 / 2048.0), (8.0 / 2048.0), (8.0 / 2048.0))
	EntityPickMode d\frameobj,2
	EntityType d\obj, HIT_MAP
	EntityType d\obj2, HIT_MAP
	
	d\ID = DoorTempID
	DoorTempID=DoorTempID+1
	
	d\Code = code
	
	d\Level = lvl
	d\LevelDest = 66
	
	For i% = 0 To 1
		If code <> "" Then 
			d\buttons[i]= CopyEntity(ButtonCodeOBJ)
			EntityFX(d\buttons[i], 1)
		Else
			d\buttons[i] = CopyEntity(ButtonOBJ)
		EndIf
		
		ScaleEntity(d\buttons[i], 0.03, 0.03, 0.03)
	Next
	
	If big=1 Then
		PositionEntity d\buttons[0], x - 432.0 * RoomScale, y + 0.7, z + 192.0 * RoomScale
		PositionEntity d\buttons[1], x + 432.0 * RoomScale, y + 0.7, z - 192.0 * RoomScale
		RotateEntity d\buttons[0], 0, 90, 0
		RotateEntity d\buttons[1], 0, 270, 0
	Else
		PositionEntity d\buttons[0], x + 0.6, y + 0.7, z - 0.1
		PositionEntity d\buttons[1], x - 0.6, y + 0.7, z + 0.1
		RotateEntity d\buttons[1], 0, 180, 0		
	End If
	EntityParent(d\buttons[0], d\frameobj)
	EntityParent(d\buttons[1], d\frameobj)
	EntityPickMode(d\buttons[0], 2)
	EntityPickMode(d\buttons[1], 2)
	
	PositionEntity d\obj, x, y, z
	
	RotateEntity d\obj, 0, angle, 0
	RotateEntity d\frameobj, 0, angle, 0
	
	If d\obj2 <> 0 Then
		PositionEntity d\obj2, x, y, z
		If big=1 Then
			RotateEntity(d\obj2, 0, angle, 0)
		Else
			RotateEntity(d\obj2, 0, angle + 180, 0)
		EndIf
		EntityParent(d\obj2, parent)
	EndIf
	
	EntityParent(d\frameobj, parent)
	EntityParent(d\obj, parent)
	
	d\angle = angle
	d\open = dopen		
	
	EntityPickMode(d\obj, 2)
	If d\obj2 <> 0 Then
		EntityPickMode(d\obj2, 2)
	EndIf
	
	EntityPickMode d\frameobj,2
	
	If Rand(8) = 1 And d\open And big = False Then d\AutoClose = True
	d\dir=big
	d\room=room
	
	d\MTFClose = True
	
	If useCollisionMesh Then
		For d2.Doors = Each Doors
			If d2 <> d Then
				If d2\DoorHitOBJ <> 0 Then
					d\DoorHitOBJ = CopyEntity(d2\DoorHitOBJ,d\frameobj)
					EntityAlpha d\DoorHitOBJ,0.0
					EntityFX d\DoorHitOBJ,1
					EntityType d\DoorHitOBJ,HIT_MAP
					EntityColor d\DoorHitOBJ,255,0,0
					HideEntity d\DoorHitOBJ
					Exit
				EndIf
			EndIf
		Next
		If d\DoorHitOBJ=0 Then
			d\DoorHitOBJ = LoadMesh_Strict("GFX\doorhit.b3d",d\frameobj)
			EntityAlpha d\DoorHitOBJ,0.0
			EntityFX d\DoorHitOBJ,1
			EntityType d\DoorHitOBJ,HIT_MAP
			EntityColor d\DoorHitOBJ,255,0,0
			HideEntity d\DoorHitOBJ
		EndIf
	EndIf
	
	Return d
	
End Function

Function CreateButton(x#,y#,z#, pitch#,yaw#,roll#=0)
	Local obj = CopyEntity(ButtonOBJ)	
	
	ScaleEntity(obj, 0.03, 0.03, 0.03)
	
	PositionEntity obj, x,y,z
	RotateEntity obj, pitch,yaw,roll
	
	EntityPickMode(obj, 2)	
	
	Return obj
End Function

Function UpdateDoors()
	
	Local i%, d.Doors, x#, z#, dist#
	If UpdateDoorsTimer =< 0 Then
		For d.Doors = Each Doors
			Local xdist# = Abs(EntityX(Collider)-EntityX(d\obj,True))
			Local zdist# = Abs(EntityZ(Collider)-EntityZ(d\obj,True))
			
			d\dist = xdist+zdist
			
			If d\dist > HideDistance*2 Then
				If d\obj <> 0 Then HideEntity d\obj
				If d\frameobj <> 0 Then HideEntity d\frameobj
				If d\obj2 <> 0 Then HideEntity d\obj2
				If d\buttons[0] <> 0 Then HideEntity d\buttons[0]
				If d\buttons[1] <> 0 Then HideEntity d\buttons[1]				
			Else
				If d\obj <> 0 Then ShowEntity d\obj
				If d\frameobj <> 0 Then ShowEntity d\frameobj
				If d\obj2 <> 0 Then ShowEntity d\obj2
				If d\buttons[0] <> 0 Then ShowEntity d\buttons[0]
				If d\buttons[1] <> 0 Then ShowEntity d\buttons[1]
			EndIf
			
			If PlayerRoom\RoomTemplate\Name$ = "room2sl"
				If ValidRoom2slCamRoom(d\room)
					If d\obj <> 0 Then ShowEntity d\obj
					If d\frameobj <> 0 Then ShowEntity d\frameobj
					If d\obj2 <> 0 Then ShowEntity d\obj2
					If d\buttons[0] <> 0 Then ShowEntity d\buttons[0]
					If d\buttons[1] <> 0 Then ShowEntity d\buttons[1]
				EndIf
			EndIf
		Next
		
		UpdateDoorsTimer = 30
	Else
		UpdateDoorsTimer = Max(UpdateDoorsTimer-FPSfactor,0)
	EndIf
	
	ClosestButton = 0
	ClosestDoor = Null
	
	For d.Doors = Each Doors
		If d\dist < HideDistance*2 Or d\IsElevatorDoor>0 Then ;Make elevator doors update everytime because if not, this can cause a bug where the elevators suddenly won't work, most noticeable in room2tunnel - ENDSHN
			
			If (d\openstate >= 180 Or d\openstate <= 0) And GrabbedEntity = 0 Then
				For i% = 0 To 1
					If d\buttons[i] <> 0 Then
						If Abs(EntityX(Collider)-EntityX(d\buttons[i],True)) < 1.0 Then 
							If Abs(EntityZ(Collider)-EntityZ(d\buttons[i],True)) < 1.0 Then 
								dist# = Distance(EntityX(Collider, True), EntityZ(Collider, True), EntityX(d\buttons[i], True), EntityZ(d\buttons[i], True));entityDistance(collider, d\buttons[i])
								If dist < 0.7 Then
									Local temp% = CreatePivot()
									PositionEntity temp, EntityX(Camera), EntityY(Camera), EntityZ(Camera)
									PointEntity temp,d\buttons[i]
									
									If EntityPick(temp, 0.6) = d\buttons[i] Then
										If ClosestButton = 0 Then
											ClosestButton = d\buttons[i]
											ClosestDoor = d
										Else
											If dist < EntityDistance(Collider, ClosestButton) Then ClosestButton = d\buttons[i] : ClosestDoor = d
										End If							
									End If
									
									FreeEntity temp
									
								EndIf							
							EndIf
						EndIf
						
					EndIf
				Next
			EndIf
			
			If d\open Then
				If d\openstate < 180 Then
					Select d\dir
						Case 0
							d\openstate = Min(180, d\openstate + FPSfactor * 2 * (d\fastopen+1))
							MoveEntity(d\obj, Sin(d\openstate) * (d\fastopen*2+1) * FPSfactor / 80.0, 0, 0)
							If d\obj2 <> 0 Then MoveEntity(d\obj2, Sin(d\openstate)* (d\fastopen+1) * FPSfactor / 80.0, 0, 0)		
						Case 1
							d\openstate = Min(180, d\openstate + FPSfactor * 0.8)
							MoveEntity(d\obj, Sin(d\openstate) * FPSfactor / 180.0, 0, 0)
							If d\obj2 <> 0 Then MoveEntity(d\obj2, -Sin(d\openstate) * FPSfactor / 180.0, 0, 0)
						Case 2
							d\openstate = Min(180, d\openstate + FPSfactor * 2 * (d\fastopen+1))
							MoveEntity(d\obj, Sin(d\openstate) * (d\fastopen+1) * FPSfactor / 85.0, 0, 0)
							If d\obj2 <> 0 Then MoveEntity(d\obj2, Sin(d\openstate)* (d\fastopen*2+1) * FPSfactor / 120.0, 0, 0)
						Case 3
							d\openstate = Min(180, d\openstate + FPSfactor * 2 * (d\fastopen+1))
							MoveEntity(d\obj, Sin(d\openstate) * (d\fastopen*2+1) * FPSfactor / 162.0, 0, 0)
							If d\obj2 <> 0 Then MoveEntity(d\obj2, Sin(d\openstate)* (d\fastopen*2+1) * FPSfactor / 162.0, 0, 0)
						Case 4 ;Used for 914 only
							d\openstate = Min(180, d\openstate + FPSfactor * 1.4)
							MoveEntity(d\obj, Sin(d\openstate) * FPSfactor / 114.0, 0, 0)
					End Select
				Else
					d\fastopen = 0
					ResetEntity(d\obj)
					If d\obj2 <> 0 Then ResetEntity(d\obj2)
					If d\timerstate > 0 Then
						d\timerstate = Max(0, d\timerstate - FPSfactor)
						If d\timerstate + FPSfactor > 110 And d\timerstate <= 110 Then d\SoundCHN = PlaySound2(CautionSFX, Camera, d\obj)
						;If d\timerstate = 0 Then d\open = (Not d\open) : PlaySound2(CloseDoorSFX(Min(d\dir,1),Rand(0, 2)), Camera, d\obj)
						Local sound%
						If d\dir = 1 Then sound% = Rand(0, 1) Else sound% = Rand(0, 2)
						If d\timerstate = 0 Then d\open = (Not d\open) : d\SoundCHN = PlaySound2(CloseDoorSFX(d\dir,sound%), Camera, d\obj)
					EndIf
					If d\AutoClose And RemoteDoorOn = True Then
						If EntityDistance(Camera, d\obj) < 2.1 Then
							If (Not Wearing714) Then PlaySound_Strict HorrorSFX(7)
							d\open = False : d\SoundCHN = PlaySound2(CloseDoorSFX(Min(d\dir,1), Rand(0, 2)), Camera, d\obj) : d\AutoClose = False
						EndIf
					EndIf				
				EndIf
			Else
				If d\openstate > 0 Then
					Select d\dir
						Case 0
							d\openstate = Max(0, d\openstate - FPSfactor * 2 * (d\fastopen+1))
							MoveEntity(d\obj, Sin(d\openstate) * -FPSfactor * (d\fastopen+1) / 80.0, 0, 0)
							If d\obj2 <> 0 Then MoveEntity(d\obj2, Sin(d\openstate) * (d\fastopen+1) * -FPSfactor / 80.0, 0, 0)	
						Case 1
							d\openstate = Max(0, d\openstate - FPSfactor*0.8)
							MoveEntity(d\obj, Sin(d\openstate) * -FPSfactor / 180.0, 0, 0)
							If d\obj2 <> 0 Then MoveEntity(d\obj2, Sin(d\openstate) * FPSfactor / 180.0, 0, 0)
							If ParticleAmount=2 And d\openstate < 15 And d\openstate+FPSfactor => 15
								Local particles% = SetEmitter(d\frameobj, ParticleEffect[2])
								EntityOrder(particles, -1)
							EndIf
						Case 2
							d\openstate = Max(0, d\openstate - FPSfactor * 2 * (d\fastopen+1))
							MoveEntity(d\obj, Sin(d\openstate) * -FPSfactor * (d\fastopen+1) / 85.0, 0, 0)
							If d\obj2 <> 0 Then MoveEntity(d\obj2, Sin(d\openstate) * (d\fastopen+1) * -FPSfactor / 120.0, 0, 0)
						Case 3
							d\openstate = Max(0, d\openstate - FPSfactor * 2 * (d\fastopen+1))
							MoveEntity(d\obj, Sin(d\openstate) * -FPSfactor * (d\fastopen+1) / 162.0, 0, 0)
							If d\obj2 <> 0 Then MoveEntity(d\obj2, Sin(d\openstate) * (d\fastopen+1) * -FPSfactor / 162.0, 0, 0)
						Case 4 ;Used for 914 only
							d\openstate = Min(180, d\openstate - FPSfactor * 1.4)
							MoveEntity(d\obj, Sin(d\openstate) * -FPSfactor / 114.0, 0, 0)
					End Select
					
					TFormPoint(EntityX(Collider), 0, EntityZ(Collider), 0, d\frameobj)
					Local dx# = TFormedX() * EntityScaleX(d\frameobj, True)
					Local dz# = TFormedZ() * EntityScaleZ(d\frameobj, True)

					If Abs(dz)<0.15 And Abs(dx)<0.7*((d\dir=1)*2+1) Then
						z# = CurveValue(0.15*Sgn(dz), dz, 5)
						TFormPoint(TFormedX(), 0, z / EntityScaleZ(d\frameobj, True), d\frameobj, 0)
						PositionEntity Collider, TFormedX(), EntityY(Collider), TFormedZ()
					EndIf
					
					If d\DoorHitOBJ <> 0 Then
						ShowEntity d\DoorHitOBJ
					EndIf
				Else
					d\fastopen = 0
					PositionEntity(d\obj, EntityX(d\frameobj, True), EntityY(d\frameobj, True), EntityZ(d\frameobj, True))
					If d\obj2 <> 0 Then PositionEntity(d\obj2, EntityX(d\frameobj, True), EntityY(d\frameobj, True), EntityZ(d\frameobj, True))
					If d\obj2 <> 0 And d\dir = 0 Then
						MoveEntity(d\obj, 0, 0, 8.0 * RoomScale)
						MoveEntity(d\obj2, 0, 0, 8.0 * RoomScale)
					EndIf
					If d\DoorHitOBJ <> 0 Then
						HideEntity d\DoorHitOBJ
					EndIf
				EndIf
			EndIf
			
		EndIf
		
		If d\DoorHitOBJ<>0 Then
			If DebugHUD Then
				EntityAlpha d\DoorHitOBJ,0.5
			Else
				EntityAlpha d\DoorHitOBJ,0.0
			EndIf
		EndIf
	Next
End Function

Global ElevatorButtonLastPressMillis%
Global ElevatorButtonSpamCount%

Function UseDoor(d.Doors, showmsg%=True, playsfx%=True)
	If d\locked Then
		If showmsg = True Then
			PlaySound_Strict ButtonSFX2
			If d\open Then
				Msg = I_Loc\MessageButton_Nothing
			Else
				Msg = I_Loc\MessageButton_DoorLocked
			EndIf
			MsgTimer = 70 * 5
		EndIf
		Return
	EndIf

	d\open = (Not d\open)
	If d\LinkedDoor <> Null Then d\LinkedDoor\open = (Not d\LinkedDoor\open)
	
	Local sound = 0
	;If d\dir = 1 Then sound = 0 Else sound=Rand(0, 2)
	If d\dir = 1 Then sound=Rand(0, 1) Else sound=Rand(0, 2)
	
	If playsfx=True And d\dir < 4 Then
		If d\open Then
			If d\LinkedDoor <> Null Then d\LinkedDoor\timerstate = d\LinkedDoor\timer
			d\timerstate = d\timer
			d\SoundCHN = PlaySound2 (OpenDoorSFX(d\dir, sound), Camera, d\obj)
		Else
			d\SoundCHN = PlaySound2 (CloseDoorSFX(d\dir, sound), Camera, d\obj)
		EndIf
	Else
		If d\open Then
			If d\LinkedDoor <> Null Then d\LinkedDoor\timerstate = d\LinkedDoor\timer
			d\timerstate = d\timer
		EndIf
	EndIf
	
End Function






Function RemoveDoor(d.Doors)
	If d\buttons[0] <> 0 Then EntityParent d\buttons[0], 0
	If d\buttons[1] <> 0 Then EntityParent d\buttons[1], 0	
	
	If d\obj <> 0 Then FreeEntity d\obj
	If d\obj2 <> 0 Then FreeEntity d\obj2
	If d\frameobj <> 0 Then FreeEntity d\frameobj
	If d\buttons[0] <> 0 Then FreeEntity d\buttons[0]
	If d\buttons[1] <> 0 Then FreeEntity d\buttons[1]
	
	Delete d
End Function

Global DebugMapGen% = GetOptionInt("debug", "show map gen")
Global DebugForestGen% = GetOptionInt("debug", "show forest gen")

Include "MapSystem.bb"

Include "NPCs.bb"



Collisions HIT_PLAYER, HIT_MAP, 2, 2
Collisions HIT_PLAYER, HIT_PLAYER, 1, 3
Collisions HIT_ITEM, HIT_MAP, 2, 2
Collisions HIT_APACHE, HIT_APACHE, 1, 2
Collisions HIT_DEAD, HIT_MAP, 2, 2

;----------------------------------- meshes and textures ----------------------------------------------------------------
Include "Effects.bb"
Global ResizeTexture%

Function InitFastResize()
	ResizeTexture = CreateTexture(SMALLEST_POWER_TWO, SMALLEST_POWER_TWO, 1 + 2 + 256 + 1024)
	InitPostProcess()
End Function

Global FogTexture%, Fog%
Global GasMaskTexture%, GasMaskOverlay%
Global InfectTexture%, InfectOverlay%
Global DarkTexture%, Dark%
Global Collider%, Head%

Global FogNVTexture%
Global NVTexture%, NVOverlay%

Global PumpkinTexture%, PumpkinTexture2%, PumpkinOverlay%

Global TeslaTexture%

Global LightTexture%, Light%
Dim LightSpriteTex%(5)
Global DoorOBJ%, DoorFrameOBJ%

Global LeverOBJ%, LeverBaseOBJ%

Global DoorColl%
Global ButtonOBJ%, ButtonCodeOBJ%

Dim DecalTextures%(23)

Global Monitor%, MonitorTexture%
Global CamBaseOBJ%, CamOBJ%

Global LiquidObj%,MTFObj%,GuardObj%,ClassDObj%
Global ApacheObj%,ApacheRotorObj%

Global UnableToMove% = False
Global ShouldEntitiesFall% = True
Global PlayerFallingPickDistance# = 10.0

Global Save_MSG$ = ""
Global Save_MSG_Timer# = 0.0
Global Save_MSG_Y# = 0.0

Global MTF_CameraCheckTimer# = 0.0
Global MTF_CameraCheckDetected% = False

;---------------------------------------------------------------------------------------------------

Include "menu.bb"
MainMenuOpen = True

;---------------------------------------------------------------------------------------------------

FlushKeys()
FlushMouse()

LoopDelay = MilliSecs()
AccumulatedLoopDelay = 0.0

Global UpdateParticles_Time# = 0.0

Global CurrTrisAmount%

Global Input_ResetTime# = 0

Type SCP427
	Field Using%
	Field Timer#
	Field Sound[2]
	Field SoundCHN[2]
End Type

Global I_427.SCP427 = New SCP427

Type MapZones
	Field Transition%[2]
	Field HasCustomForest%
	Field HasCustomMT%
End Type

Global I_Zone.MapZones = New MapZones


;----------------------------------------------------------------------------------------------------------------------------------------------------
;----------------------------------------------       		MAIN LOOP                 ---------------------------------------------------------------
;----------------------------------------------------------------------------------------------------------------------------------------------------

Global TotalVidMem = TotalVidMem()
Global TotalPhysMem = TotalPhys()

While IsRunning
	SetErrorMsg(5, "GPU: " + GFXDriverName + " (" + (TotalVidMem - (AvailVidMem() / 1024)) + "MB/" + TotalVidMem + " MB)")
	SetErrorMsg(6, "Global memory status: (" + (TotalPhysMem - (AvailPhys() / 1024)) + "MB/" + TotalPhysMem + " MB)")

	Cls
	
	CurTime = MilliSecs()
	If Framelimit > 0 Then
	    ; Capped to prevent lag spikes as a result of invalid values.
		AccumulatedLoopDelay = Min(50, AccumulatedLoopDelay + 1000.0 / Framelimit - (CurTime - LoopDelay))
		If AccumulatedLoopDelay > 0 Then
			Delay AccumulatedLoopDelay
			LoopDelay = MilliSecs()
			AccumulatedLoopDelay = AccumulatedLoopDelay - (LoopDelay - CurTime)
			CurTime = LoopDelay
		Else
			AccumulatedLoopDelay = 0
			LoopDelay = CurTime
		EndIf
	EndIf

	Local ElapsedTime% = CurTime - PrevTime
	PrevTime = CurTime
	If (SpeedRunMode Lor (Not (MainMenuOpen Lor MenuOpen))) And SelectedEnding="" And TimerStopped=0 Then PlayTime = PlayTime + ElapsedTime
	PrevFPSFactor = FPSfactor
	FPSfactor = Min(ElapsedTime / 1000.0 * 70, 5.0) * TimeScale
	FPSfactor2 = FPSfactor

	If IsPaused() Then FPSfactor = 0
	
	;Counting the fps
	If CheckFPS < CurTime Then
		FPS = ElapsedLoops
		ElapsedLoops = 0
		CheckFPS = CurTime+1000
	EndIf
	ElapsedLoops = ElapsedLoops + 1
	
	If Input_ResetTime<=0.0
		DoubleClick = False
		MouseHit1 = MouseHit(1)
		If MouseHit1 Then
			If CurTime - LastMouseHit1 < 800 And Abs(MouseX() - LastMouseHit1X) < 4 And Abs(MouseY() - LastMouseHit1Y) < 4 Then DoubleClick = True
			LastMouseHit1 = CurTime
			LastMouseHit1X = MouseX()
			LastMouseHit1Y = MouseY()
		EndIf
		
		Local prevmousedown1 = MouseDown1
		MouseDown1 = MouseDown(1)
		If prevmousedown1 = True And MouseDown1=False Then MouseUp1 = True Else MouseUp1 = False
		
		MouseHit2 = MouseHit(2)
		
		If (Not MouseDown1) And (Not MouseHit1) Then GrabbedEntity = 0
	Else
		Input_ResetTime = Max(Input_ResetTime-FPSfactor2,0.0)
	EndIf
	
	UpdateMusic()
	If EnableSFXRelease Then AutoReleaseSounds()
	
	If MainMenuOpen Then
		If ShouldPlay = 21 Then
			EndBreathSFX = LoadSound(DetermineModdedSoundPath("SFX\Ending\MenuBreath.ogg"))
			EndBreathCHN = PlaySound(EndBreathSFX)
			ShouldPlay = 66
		ElseIf ShouldPlay = 66
			If (Not ChannelPlaying(EndBreathCHN)) Then
				FreeSound(EndBreathSFX)
				ShouldPlay = 11
			EndIf
		Else
			ShouldPlay = 11
		EndIf
		UpdateMainMenu()
	Else
		UpdateStreamSounds()
		
		For s.FireAndForgetSounds = Each FireAndForgetSounds
			UpdateFireAndForgetSounds(s)
		Next

		ShouldPlay = Min(PlayerZone,2)
		
		DrawHandIcon = False
		
		RestoreSanity = True
		ShouldEntitiesFall = True
		
		
		If PlayerRoom\RoomTemplate\Name <> "pocketdimension" And PlayerRoom\RoomTemplate\Name <> "gatea" And PlayerRoom\RoomTemplate\Name <> "exit1" And PlayerRoom\RoomTemplate\Name <> "halloweenpd" And (Not IsAnyMenuOpen()) Then 
			
			If Rand(1500/FPSfactor) = 1 Then
				For i = 0 To 5
					If AmbientSFX(i,CurrAmbientSFX)<>0 Then
						If ChannelPlaying(AmbientSFXCHN)=0 Then FreeSound_Strict AmbientSFX(i,CurrAmbientSFX) : AmbientSFX(i,CurrAmbientSFX) = 0
					EndIf			
				Next
				
				PositionEntity (SoundEmitter, EntityX(Camera) + Rnd(-1.0, 1.0), 0.0, EntityZ(Camera) + Rnd(-1.0, 1.0))
				
				If Rand(3)=1 Then PlayerZone = 3
				
				
				CurrAmbientSFX = Rand(0,AmbientSFXAmount(PlayerZone)-1)
				
				Select PlayerZone
					Case 0,1,2
						If AmbientSFX(PlayerZone,CurrAmbientSFX)=0 Then AmbientSFX(PlayerZone,CurrAmbientSFX)=LoadSound_Strict("SFX\Ambient\Zone"+(PlayerZone+1)+"\ambient"+(CurrAmbientSFX+1)+".ogg")
					Case 3
						If AmbientSFX(PlayerZone,CurrAmbientSFX)=0 Then AmbientSFX(PlayerZone,CurrAmbientSFX)=LoadSound_Strict("SFX\Ambient\General\ambient"+(CurrAmbientSFX+1)+".ogg")
					Case 4
						If AmbientSFX(PlayerZone,CurrAmbientSFX)=0 Then AmbientSFX(PlayerZone,CurrAmbientSFX)=LoadSound_Strict("SFX\Ambient\Pre-breach\ambient"+(CurrAmbientSFX+1)+".ogg")
					Case 5
						If AmbientSFX(PlayerZone,CurrAmbientSFX)=0 Then AmbientSFX(PlayerZone,CurrAmbientSFX)=LoadSound_Strict("SFX\Ambient\Forest\ambient"+(CurrAmbientSFX+1)+".ogg")
				End Select
				
				AmbientSFXCHN = PlaySound2(AmbientSFX(PlayerZone,CurrAmbientSFX), Camera, SoundEmitter)
			EndIf
			
		EndIf
		
		If (Not IsPaused()) And EndingTimer=>0 Then
			LightVolume = CurveValue(TempLightVolume, LightVolume, 50.0)
			CameraFogRange(Camera, CameraFogNear*LightVolume,CameraFogFar*LightVolume)
			CameraFogColor(Camera, 0, 0, 0)
			CameraFogMode Camera,1
			CameraRange(Camera, 0.05, Min(CameraFogFar*LightVolume*1.5,28))	
			CameraClsColor(Camera, 0, 0, 0)
			
			AmbientLight 0, 0, 0
			PlayerSoundVolume = CurveValue(0.0, PlayerSoundVolume, 5.0)
			
			UpdateDeafPlayer()
			UpdateEmitters()
			MouseLook()
			MovePlayer()
			InFacility = CheckForPlayerInFacility()
			UpdateDoors()
			UpdateRooms()
			UpdateRoomLights(Camera)
			UpdateDecals()
			UpdateItems()
			UpdateParticles()
			UpdateParticles_Time# = UpdateParticles_Time#+FPSfactor
			If UpdateParticles_Time#=>1
				UpdateDevilEmitters()
				UpdateParticles_Devil()
				UpdateParticles_Time# = UpdateParticles_Time# - 1
			EndIf
			ProcessBindings()
		EndIf
		
		If InfiniteStamina% Then Stamina = Min(100, Stamina + (100.0-Stamina)*0.01*FPSfactor)
		
		CatchErrors("Uncaught (UpdateWorld)")
		If FPSfactor=0
			UpdateWorld(0)
		Else
			UpdateWorld()
		EndIf
		CatchErrors("UpdateWorld")
		RenderWorld2()
		
		If KeyHit(1) And (Not ConsoleOpen) Then
			MenuOpen = (Not MenuOpen)
			If MenuOpen Then
				ShowPointer()
			Else
				HidePointer()
			EndIf
			UpdateMenuState()
		EndIf

		If KeyHit(KEY_CONSOLE) Then
			If CanOpenConsole
				ConsoleOpen = (Not ConsoleOpen)
				If SteamActive Then
					If ConsoleOpen Then
						Steam_OpenOnScreenKeyboard(0, GraphicWidth / 2, GraphicHeight / 2, GraphicWidth / 2, GraphicHeight / 2)
					Else
						Steam_CloseOnScreenKeyboard()
					EndIf
				EndIf
				UpdateMenuState()
			EndIf
		EndIf

		DrawGUI()

		UpdateConsole()
		
		If MsgTimer > 0 Then
			If HUDenabled Lor HUDenabledLegacy Then
				;If temp = True -> move the message below
				Local temp% = False
				If (Not InvOpen And OtherOpen = Null) Then
					If SelectedItem <> Null
						If SelectedItem\itemtemplate\group = "paper" Or SelectedItem\itemtemplate\name = "oldpaper"
							temp% = True
						EndIf
					EndIf
				EndIf
				
				Local messageOpacity% = Min(MsgTimer / 2, 255)
				If (Not temp%)
					; Push text up if subtitles box has lots of text.
					Local h# = (GraphicHeight / 2) + 200
					If SubtitlesEnabled Then h = Min(h, SubBox\curTop-SubtitleTextHeight*2)
					Color 0,0,0
					Text((GraphicWidth / 2)+1, h+1, Msg, True, False)
					Color messageOpacity, messageOpacity, messageOpacity
					Text((GraphicWidth / 2), h, Msg, True, False)
				Else
					Color 0,0,0
					Text((GraphicWidth / 2)+1, (GraphicHeight * 0.94) + 1, Msg, True, False)
					Color messageOpacity, messageOpacity, messageOpacity
					Text((GraphicWidth / 2), (GraphicHeight * 0.94), Msg, True, False)
				EndIf
			EndIf
			MsgTimer=MsgTimer-FPSfactor2 
		End If
		
		Color 255, 255, 255
		If ShowFPS Then SetFont ConsoleFont : Text 20, 20, Format(I_Loc\HUD_Fps, FPS) : SetFont Font1
		
		DrawMenu()
	End If
	
	ApplyBorderlessResizing()
	
	CatchErrors("Main loop / uncaught")

	Flip Vsync Lor MainMenuOpen
Wend

If ShouldRestart Then
	IsRestart = True
	Goto Start
EndIf

If SteamActive Then Steam_Shutdown()

Function Restart()
	Cls
	StopStream_Strict(MusicCHN)
	MusicCHN = 0
	ClearLoadedINIFiles()
	IsRunning = False
	ShouldRestart = True
End Function

Function GetCurrentPlayerArea%()
	If MainMenuOpen Then Return -1
	Select PlayerRoom\RoomTemplate\Name
		Case "173" Return 4
		Case "room860" If EntityY(Collider,True)>4.0 Then Return 5
		Case "dimension1499" Return 100
		Case "pocketdimension" Return 101
		Case "gatea" Return 102
		Case "exit1" Return 103
		Case "room2tunnel" If EntityY(Collider,True)>4.0 Then Return 104
		Case "halloweenpd", "halloweenfields" Return 666
		Default Return PlayerZone
	End Select
End Function

;----------------------------------------------------------------------------------------------------------------------------------------------------
;----------------------------------------------------------------------------------------------------------------------------------------------------
;----------------------------------------------------------------------------------------------------------------------------------------------------


Function Kill()
	If GodMode Then Return
	
	If BreathCHN <> 0 Then
		If ChannelPlaying(BreathCHN) Then StopChannel(BreathCHN)
	EndIf
	
	If KillTimer >= 0 Then
		KillAnim = Rand(0,1)
		PlaySound_Strict(DamageSFX(0))
		KillTimer = Min(-1, KillTimer)
		PositionEntity(Head, EntityX(Camera, True), EntityY(Camera, True), EntityZ(Camera, True), True)
		ResetEntity (Head)
		RotateEntity(Head, 0, EntityYaw(Camera), 0)		
	EndIf
End Function


Function UpdateMenuState()
	If IsAnyMenuOpen() Then
		PauseSounds()
	Else
		ResumeSounds()
		MouseXSpeed() : MouseYSpeed() : MouseZSpeed() : mouse_x_speed_1#=0.0 : mouse_y_speed_1#=0.0
	EndIf
	FlushKeys()
End Function

Function IsAnyMenuOpen()
	Return MenuOpen Lor ConsoleOpen Lor InvOpen Lor OtherOpen<>Null Lor (SelectedItem <> Null And SelectedItem\Inventory <> Null) Lor Using294
End Function

Function IsPaused()
	Return IsAnyMenuOpen() Lor SelectedDoor <> Null Lor SelectedScreen <> Null
End Function

Type CreditsLine
	Field txt$
	Field id%
	Field stay%
End Type

Global CreditsTimer# = 0.0
Global CreditsScreen%

Function InitCredits()
	Local cl.CreditsLine
	Local file% = OpenFile("Credits.txt")
	Local l$
	
	CreditsFont% = LoadFont_Strict("GFX\font\arial\Arial.ttf", Int(21 * UIScale))
	CreditsFont2% = LoadFont_Strict("GFX\font\arial\Arialbd.ttf", Int(35 * UIScale))
	
	If CreditsScreen = 0
		CreditsScreen = LoadImage_Strict("GFX\creditsscreen.pt", UIScale)
	EndIf
	
	Repeat
		l = ReadLine(file)
		If l = "-" Then
			Local m.ActiveMods = Last ActiveMods
			While m <> Null
				InitCreditsFromFile(m\Path + "Credits.txt")
				m = Before m
			Wend
		Else
			cl = New CreditsLine
			cl\txt = l
		EndIf
	Until Eof(file)
	CloseFile(file)
	
	Delete First CreditsLine
	CreditsTimer = 0
End Function

Function InitCreditsFromFile(creditsPath$)
	If FileType(creditsPath) <> 1 Then Return

	Local f% = OpenFile(creditsPath)
	Repeat
		Local l$ = ReadLine(f)
		Local cl.CreditsLine = New CreditsLine
		cl\txt = l
	Until Eof(f)
	CloseFile(f)
End Function

Function DrawCredits()
    Local credits_Y# = (EndingTimer+2000)/2+(GraphicHeight+10)
    Local cl.CreditsLine
    Local id%
    Local endlinesamount%
	Local LastCreditLine.CreditsLine
	
    Cls
	
	If Rand(1,300)>1
		DrawImage CreditsScreen, GraphicWidth/2-ImageWidth(CreditsScreen)/2, GraphicHeight/2-ImageHeight(CreditsScreen)/2
	EndIf
	
	id = 0
	endlinesamount = 0
	LastCreditLine = Null
	Color 255,255,255
	For cl = Each CreditsLine
		cl\id = id
		If Left(cl\txt,1)="*"
			SetFont CreditsFont2
			If cl\stay=False
				Text GraphicWidth/2,credits_Y+(24*cl\id*UIScale),Right(cl\txt,Len(cl\txt)-1),True
			EndIf
		ElseIf Left(cl\txt,1)="/"
			LastCreditLine = Before(cl)
		Else
			SetFont CreditsFont
			If cl\stay=False
				Text GraphicWidth/2,credits_Y+(24*cl\id*UIScale),cl\txt,True
			EndIf
		EndIf
		If LastCreditLine<>Null
			If cl\id>LastCreditLine\id
				cl\stay = True
			EndIf
		EndIf
		If cl\stay
			endlinesamount=endlinesamount+1
		EndIf
		id=id+1
	Next
	If (credits_Y+(24*LastCreditLine\id*UIScale))<-StringHeight(LastCreditLine\txt)
		CreditsTimer=CreditsTimer+(0.5*FPSfactor2)
		If CreditsTimer>=0.0 And CreditsTimer<255.0
			Color Max(Min(CreditsTimer,255),0),Max(Min(CreditsTimer,255),0),Max(Min(CreditsTimer,255),0)
		ElseIf CreditsTimer>=255.0
			Color 255,255,255
			If CreditsTimer>500.0
				CreditsTimer=-255.0
			EndIf
		Else
			Color Max(Min(-CreditsTimer,255),0),Max(Min(-CreditsTimer,255),0),Max(Min(-CreditsTimer,255),0)
			If CreditsTimer>=-1.0
				CreditsTimer=-1.0
			EndIf
		EndIf
		DebugLog CreditsTimer
	EndIf
	If CreditsTimer<>0.0
		For cl = Each CreditsLine
			If cl\stay
				SetFont CreditsFont
				If Left(cl\txt,1)="/"
					Text GraphicWidth/2,(GraphicHeight/2)+(endlinesamount/2)+(24*cl\id*UIScale),Right(cl\txt,Len(cl\txt)-1),True
				Else
					Text GraphicWidth/2,(GraphicHeight/2)+(24*(cl\id-LastCreditLine\id)*UIScale)-((endlinesamount/2)*24*UIScale),cl\txt,True
				EndIf
			EndIf
		Next
	EndIf
	
	If GetKey() Then CreditsTimer=-1
	
	If CreditsTimer=-1
		FreeFont CreditsFont
		FreeFont CreditsFont2
		FreeImage CreditsScreen
		CreditsScreen = 0
		FreeImage EndingScreen
		EndingScreen = 0
		Delete Each CreditsLine
        NullGame(False)
        StopStream_Strict(MusicCHN)
        ShouldPlay = 21
        MenuOpen = False
        MainMenuOpen = True
        FlushKeys()
	EndIf
    
End Function

;--------------------------------------- player controls -------------------------------------------

Global MoveX%, MoveZ%
Global PlayerStepped%

Function MovePlayer()
	CatchErrors("Uncaught (MovePlayer)")
	Local Sprint# = 1.0, Speed# = 0.018, i%, angle#
	PlayerStepped = False
	
	If SuperMan Then
		Speed = Speed * 3
		
		SuperManTimer=SuperManTimer+FPSfactor
		
		CameraShake = Sin(SuperManTimer / 5.0) * (SuperManTimer / 1500.0)
		
		If SuperManTimer > 70 * 50 Then
			DeathMSG = I_Loc\DeathMessage_914Superman
			Kill()
			ShowEntity Fog
		Else
			BlurTimer = 500		
			HideEntity Fog
		EndIf
	End If
	
	If DeathTimer > 0 Then
		DeathTimer=DeathTimer-FPSfactor
		If DeathTimer < 1 Then DeathTimer = -1.0
	ElseIf DeathTimer < 0 
		Kill()
	EndIf
	
	If CurrSpeed > 0 Then
        Stamina = Min(Stamina + 0.15 * FPSfactor/1.25, 100.0)
    Else
        Stamina = Min(Stamina + 0.15 * FPSfactor*1.25, 100.0)
    EndIf
	
	If StaminaEffectTimer > 0 Then
		StaminaEffectTimer = StaminaEffectTimer - (FPSfactor/70)
	Else
		If StaminaEffect <> 1.0 Then StaminaEffect = 1.0
	EndIf
	
	Local temp#
	
	If PlayerRoom\RoomTemplate\Name<>"pocketdimension" Then 
		If KeyDown(KEY_SPRINT) Then
			If Stamina < 5 Then
				temp = 0
				If WearingGasMask>0 Or Wearing1499>0 Then temp=1
				If ChannelPlaying(BreathCHN)=False Then BreathCHN = PlaySound_Strict(BreathSFX((temp), 0))
			ElseIf Stamina < 50
				If BreathCHN=0 Then
					temp = 0
					If WearingGasMask>0 Or Wearing1499>0 Then temp=1
					BreathCHN = PlaySound_Strict(BreathSFX((temp), Rand(1,3)))
					ChannelVolume BreathCHN, Min((70.0-Stamina)/70.0,1.0)*SFXVolume
				Else
					If ChannelPlaying(BreathCHN)=False Then
						temp = 0
						If WearingGasMask>0 Or Wearing1499>0 Then temp=1
						BreathCHN = PlaySound_Strict(BreathSFX((temp), Rand(1,3)))
						ChannelVolume BreathCHN, Min((70.0-Stamina)/70.0,1.0)*SFXVolume			
					EndIf
				EndIf
			EndIf
		EndIf
	EndIf
	
	For i = 0 To MaxItemAmount-1
		If Inventory(i)<>Null Then
			If Inventory(i)\itemtemplate\name = "finevest" Then
				Stamina = Min(Stamina, 60)
				Exit
			EndIf
		EndIf
	Next
	
	If Wearing714 Then
		Stamina = Min(Stamina, 10)
		Sanity = Max(-850, Sanity)
	EndIf
	
	If IsZombie Then Crouch = False
	
	If Abs(CrouchState-Crouch)<0.001 Then 
		CrouchState = Crouch
	Else
		CrouchState = CurveValue(Crouch, CrouchState, 10.0)
	EndIf
	
	If (Not NoClip) Then
		If ForceMove > 0 Lor Playable And (MoveX <> 0 Lor MoveZ <> 0) Then
			If Crouch = 0 And (KeyDown(KEY_SPRINT)) And Stamina > 0.0 And (Not IsZombie) Then
				Sprint = 2.5
				Stamina = Stamina - FPSfactor * 0.4 * StaminaEffect
				If Stamina <= 0 Then Stamina = -20.0
			End If
			
			If PlayerRoom\RoomTemplate\Name = "pocketdimension" Then 
				If EntityY(Collider)<2000*RoomScale Or EntityY(Collider)>2608*RoomScale Then
					Stamina = 0
					Speed = 0.015
					Sprint = 1.0					
				EndIf
			EndIf	
			
			If ForceMove>0 Then Speed=Speed*ForceMove
			
			If SelectedItem<>Null Then
				If SelectedItem\itemtemplate\name = "firstaid" Or SelectedItem\itemtemplate\name = "finefirstaid" Or SelectedItem\itemtemplate\name = "firstaid2" Then 
					Sprint = 0
				EndIf
			EndIf
			
			temp# = (Shake Mod 360)
			Local tempchn%
			If (Not UnableToMove%) Then Shake# = (Shake + FPSfactor * Min(Sprint, 1.5) * 7) Mod 720
			If temp < 180 And (Shake Mod 360) >= 180 And KillTimer>=0 And FallTimer >= 0 Then
				PlayerStepped = True
				If CurrStepSFX=0 Then
					temp = GetStepSound(Collider)
					
					If Sprint = 1.0 Then
						PlayerSoundVolume = Max(2.5-(Crouch*0.6),PlayerSoundVolume)
						tempchn% = PlaySound_Strict(StepSFX(temp, 0, Rand(0, 7)))
					Else
						PlayerSoundVolume = Max(4.0,PlayerSoundVolume)
						tempchn% = PlaySound_Strict(StepSFX(temp, 1, Rand(0, 7)))
					End If
				ElseIf CurrStepSFX=1
					tempchn% = PlaySound_Strict(Step2SFX(Rand(0, 2)))
				ElseIf CurrStepSFX=2
					tempchn% = PlaySound_Strict(Step2SFX(Rand(3,5)))
					If Sprint = 1.0 Then
						PlayerSoundVolume = Max(2.5-(Crouch*0.6),PlayerSoundVolume)
					Else
						PlayerSoundVolume = Max(4.0,PlayerSoundVolume)
					End If
				ElseIf CurrStepSFX=3
					If Sprint = 1.0 Then
						PlayerSoundVolume = Max(2.5-(Crouch*0.6),PlayerSoundVolume)
						tempchn% = PlaySound_Strict(StepSFX(0, 0, Rand(0, 7)))
					Else
						PlayerSoundVolume = Max(4.0,PlayerSoundVolume)
						tempchn% = PlaySound_Strict(StepSFX(0, 1, Rand(0, 7)))
					End If
				EndIf
				If tempchn <> 0 Then ChannelVolume tempchn, (1.0-(Crouch*0.6))*SFXVolume#
			EndIf	
		EndIf
	Else ;noclip on
		If (KeyDown(KEY_SPRINT)) Then 
			Sprint = 2.5
		ElseIf KeyDown(KEY_CROUCH)
			Sprint = 0.5
		EndIf
	EndIf
	
	If KeyHit(KEY_CROUCH) And Playable Then Crouch = (Not Crouch)
	
	Local temp2# = (Speed * Sprint) / (1.0+CrouchState)
	
	If NoClip Then 
		Shake = 0
		CurrSpeed = 0
		CrouchState = 0
		Crouch = 0
		
		RotateEntity Collider, WrapAngle(EntityPitch(Camera)), WrapAngle(EntityYaw(Camera)), 0
		
		temp2 = temp2 * NoClipSpeed
		
		If KeyDown(KEY_DOWN) Then MoveEntity Collider, 0, 0, -temp2*FPSfactor
		If KeyDown(KEY_UP) Then MoveEntity Collider, 0, 0, temp2*FPSfactor
		
		If KeyDown(KEY_LEFT) Then MoveEntity Collider, -temp2*FPSfactor, 0, 0
		If KeyDown(KEY_RIGHT) Then MoveEntity Collider, temp2*FPSfactor, 0, 0	
		
		ResetEntity Collider
	Else
		temp2# = temp2 / Max((Injuries+3.0)/3.0,1.0)
		If Injuries > 0.5 Then 
			temp2 = temp2*Min((Sin(Shake/2)+1.2),1.0)
		EndIf
		
		temp = False
		If (Not IsZombie%)
			If MoveInputCancelling Then
				MoveZ = KeyDown(KEY_DOWN) - KeyDown(KEY_UP)
				MoveX = KeyDown(KEY_LEFT) - KeyDown(KEY_RIGHT)
			Else
				If KeyHit(KEY_DOWN) Then
					MoveZ = 1
				Else If KeyHit(KEY_UP)
					MoveZ = -1
				Else If MoveZ = 1 And (Not KeyDown(KEY_DOWN)) Then
					MoveZ = -KeyDown(KEY_UP)
				Else If MoveZ = -1 And (Not KeyDown(KEY_UP)) Then
					MoveZ = KeyDown(KEY_DOWN)
				Else If MoveZ = 0 Then
					MoveZ = KeyDown(KEY_DOWN) - KeyDown(KEY_UP)
				EndIf

				If KeyHit(KEY_LEFT) Then
					MoveX = 1
				Else If KeyHit(KEY_RIGHT)
					MoveX = -1
				Else If MoveX = 1 And (Not KeyDown(KEY_LEFT)) Then
					MoveX = -KeyDown(KEY_RIGHT)
				Else If MoveX = -1 And (Not KeyDown(KEY_RIGHT)) Then
					MoveX = KeyDown(KEY_LEFT)
				Else If MoveX = 0 Then
					MoveX = KeyDown(KEY_LEFT) - KeyDown(KEY_RIGHT)
				EndIf
			EndIf

			If moveZ > 0 And Playable Then
				temp = True
				If moveX = 0 Then angle = 180 Else angle = 135 * moveX
			ElseIf moveZ < 0 And Playable Then
				temp = True
				If moveX = 0 Then angle = 0 Else angle = 45 * moveX
			ElseIf ForceMove>0 Then
				temp=True
				angle = ForceAngle
			Else If Playable And moveX <> 0 Then
				temp = True
				angle = 90 * moveX
			EndIf
		Else
			temp=True
			angle = ForceAngle
		EndIf
		
		angle = WrapAngle(EntityYaw(Collider,True)+angle+90.0)
		
		If temp Then 
			CurrSpeed = CurveValue(temp2, CurrSpeed, 20.0)
		Else
			CurrSpeed = Max(CurveValue(0.0, CurrSpeed-0.1, 1.0),0.0)
		EndIf
		
		If (Not UnableToMove%) And KillTimer >= 0 And FallTimer >= 0 Then TranslateEntity Collider, Cos(angle)*CurrSpeed * FPSfactor, 0, Sin(angle)*CurrSpeed * FPSfactor, True
		
		Local CollidedFloor% = False
		For i = 1 To CountCollisions(Collider)
			If CollisionY(Collider, i) < EntityY(Collider) - 0.25 Then CollidedFloor = True
		Next
		
		If CollidedFloor = True Then
			If DropSpeed# < - 0.07 Then 
				If CurrStepSFX=0 Then
					PlaySound_Strict(StepSFX(GetStepSound(Collider), 0, Rand(0, 7)))
				ElseIf CurrStepSFX=1
					PlaySound_Strict(Step2SFX(Rand(0, 2)))
				ElseIf CurrStepSFX=2
					PlaySound_Strict(Step2SFX(Rand(3, 5)))
				ElseIf CurrStepSFX=3
					PlaySound_Strict(StepSFX(0, 0, Rand(0, 7)))
				EndIf
				PlayerSoundVolume = Max(3.0,PlayerSoundVolume)
			EndIf
			DropSpeed# = 0
		Else
			;DropSpeed# = Min(Max(DropSpeed - 0.006 * FPSfactor, -2.0), 0.0)
			If PlayerFallingPickDistance#<>0.0
				Local pick = LinePick(EntityX(Collider),EntityY(Collider),EntityZ(Collider),0,-PlayerFallingPickDistance,0)
				If pick
					DropSpeed# = Min(Max(DropSpeed - 0.006 * FPSfactor, -2.0), 0.0)
				Else
					DropSpeed# = 0
				EndIf
			Else
				DropSpeed# = Min(Max(DropSpeed - 0.006 * FPSfactor, -2.0), 0.0)
			EndIf
		EndIf
		PlayerFallingPickDistance# = 10.0
		
		If (Not UnableToMove%) And ShouldEntitiesFall Then TranslateEntity Collider, 0, DropSpeed * FPSfactor, 0
	EndIf
	
	ForceMove = False
	
	If Injuries > 1.0 Then
		temp2 = Bloodloss
		BlurTimer = Max(Max(Sin(MilliSecs()/100.0)*Bloodloss*30.0,Bloodloss*2*(2.0-CrouchState)),BlurTimer)
		If (Not I_427\Using And I_427\Timer < 70*360) Then
			Bloodloss = Min(Bloodloss + (Min(Injuries,3.5)/300.0)*FPSfactor,100)
		EndIf
		
		If temp2 <= 60 And Bloodloss > 60 Then
			Msg = I_Loc\Message_BloodlossFaint
			MsgTimer = 70*4
		EndIf
	EndIf
	
	
	If Bloodloss > 0 Then
		If Injuries > 1.0 And Rnd(200/FPSfactor)<Min(Injuries,4.0) Then
			Local pvt = CreatePivot()
			PositionEntity pvt, EntityX(Collider)+Rnd(-0.05,0.05),EntityY(Collider)-0.05,EntityZ(Collider)+Rnd(-0.05,0.05)
			TurnEntity pvt, 90, 0, 0
			EntityPick(pvt,0.3)
			Local de.decals = CreateDecal(Rand(15,16), PickedX(), PickedY()+0.005, PickedZ(), 90, Rand(360), 0)
			de\size = Rnd(0.03,0.08)*Min(Injuries,3.0) : EntityAlpha(de\obj, 1.0) : ScaleSprite de\obj, de\size, de\size
			tempchn% = PlaySound_Strict (DripSFX(Rand(0,2)))
			ChannelVolume tempchn, Rnd(0.0,0.8)*SFXVolume
			ChannelPitch tempchn, Rand(20000,30000)
			
			FreeEntity pvt
		EndIf
		
		CurrCameraZoom = Max(CurrCameraZoom, (Sin(Float(MilliSecs())/20.0)+1.0)*Bloodloss*0.2)
		
		If Bloodloss > 60 Then Crouch = True
		If Bloodloss => 100 Then 
			Kill()
			HeartBeatVolume = 0.0
		ElseIf Bloodloss > 80.0
			HeartBeatRate = Max(150-(Bloodloss-80)*5,HeartBeatRate)
			HeartBeatVolume = Max(HeartBeatVolume, 0.75+(Bloodloss-80.0)*0.0125)	
		ElseIf Bloodloss > 35.0
			HeartBeatRate = Max(70+Bloodloss,HeartBeatRate)
			HeartBeatVolume = Max(HeartBeatVolume, (Bloodloss-35.0)/60.0)			
		EndIf
	EndIf
	
	If HealTimer > 0 Then
		DebugLog HealTimer
		HealTimer = HealTimer - (FPSfactor / 70)
		Bloodloss = Min(Bloodloss + (2 / 400.0) * FPSfactor, 100)
		Injuries = Max(Injuries - (FPSfactor / 70) / 30, 0.0)
	EndIf
		
	If Playable Then
		If KeyHit(KEY_BLINK) Then BlinkTimer = 0
		If KeyDown(KEY_BLINK) And BlinkTimer < - 10 Then BlinkTimer = -10
	EndIf
	
	
	If HeartBeatVolume > 0 Then
		If HeartBeatTimer <= 0 Then
			tempchn = PlaySound_Strict (HeartBeatSFX)
			ChannelVolume tempchn, HeartBeatVolume*SFXVolume#
			
			HeartBeatTimer = 70.0*(60.0/Max(HeartBeatRate,1.0))
		Else
			HeartBeatTimer = HeartBeatTimer - FPSfactor
		EndIf
		
		HeartBeatVolume = Max(HeartBeatVolume - FPSfactor*0.05, 0)
	EndIf
	
	CatchErrors("MovePlayer")
End Function

Function ZoomCamera(fov%)
	CameraZoom(Camera, Min(1.0+(CurrCameraZoom/400.0),1.1) / Tan((ATan(Tan(fov%/2.0)*RealGraphicWidth/RealGraphicHeight))))
End Function

Function MouseLook(handleInput% = True)
	Local i%
	
	CameraShake = Max(CameraShake - (FPSfactor / 10), 0)
	
	;CameraZoomTemp = CurveValue(CurrCameraZoom,CameraZoomTemp, 5.0)
	ZoomCamera(FOV)
	CurrCameraZoom = Max(CurrCameraZoom - FPSfactor, 0)
	
	If KillTimer >= 0 And FallTimer >=0 Then
		
		HeadDropSpeed = 0
		
		;fixing the black screen bug with some bubblegum code 
		If IsNaN(EntityX(Collider)) Then
			PositionEntity Collider, EntityX(Camera, True), EntityY(Camera, True) - 0.5, EntityZ(Camera, True), True
			Msg = "EntityX(Collider) = NaN, RESETTING COORDINATES    -    New coordinates: "+EntityX(Collider)
			MsgTimer = 300				
		EndIf
		;EndIf
		
		Local roll# = Max(Min(Sin(Shake/2)*2.5*Min(Injuries+0.25,3.0),8.0),-8.0)*ViewBobScale

		Local bobAmount# = Min(CurrSpeed / 0.018, 2.5)
		Local bobTarget# = Min(bobAmount, 1.8)
		BobPowerSmooth = CurveValue(bobTarget, BobPowerSmooth, 10.0)
		Local bobPower# = BobPowerSmooth
		Local bobScale# = ViewBobScale * (1.0 - CrouchState*0.65)

		If bobAmount > 0.02 Then
			BobPhase = BobPhase + FPSfactor * (6.28318 * (1.25 + bobAmount*0.35) / 70.0)
			If BobPhase > 6.28318 Then BobPhase = BobPhase - 6.28318
		EndIf

		Local side# = Sin(BobPhase) * 0.028 * bobPower * bobScale
		Local up# = (Sin(Shake) / (20.0+CrouchState*20.0))*0.6*ViewBobScale
		Local fwd# = Cos(BobPhase*2.0) * 0.012 * bobPower * bobScale
		Local bobRoll# = Sin(BobPhase) * 1.2 * bobPower * bobScale

		PositionEntity Camera, EntityX(Collider), EntityY(Collider), EntityZ(Collider)
		RotateEntity Camera, 0, EntityYaw(Collider), (roll + bobRoll)*0.5

		MoveEntity Camera, side, up + 0.6 + CrouchState * -0.3, fwd
		
		;RotateEntity Collider, EntityPitch(Collider), EntityYaw(Collider), 0
		;moveentity player, side, up, 0	
		; -- Update the smoothing que To smooth the movement of the mouse.
		Local rawX# = MouseXSpeed() * (MouseSens + 0.6)
		Local rawY# = MouseYSpeed() * (MouseSens + 0.6)
		Local S# = (6.0 / (MouseSens + 1.0)) * MouseSmooth
		Local tau# = S / 70.0

		If MouseSmooth <= 0 Then
			mouse_x_speed_1# = rawX
			mouse_y_speed_1# = rawY
		Else
			mouse_x_speed_1# = SmoothMouseValue(rawX, mouse_x_speed_1, tau)
			mouse_y_speed_1# = SmoothMouseValue(rawY, mouse_y_speed_1, tau)
		EndIf

		If IsNaN(mouse_x_speed_1) Lor (Not handleInput) Then mouse_x_speed_1 = 0
		If IsNaN(mouse_y_speed_1) Lor (Not handleInput) Then mouse_y_speed_1 = 0

		If InvertMouse Then mouse_y_speed_1 = -mouse_y_speed_1
		
		Local the_yaw# = mouse_x_speed_1 * mouselook_x_inc# / (1.0+WearingVest)
		Local the_pitch# = mouse_y_speed_1 * mouselook_y_inc# / (1.0+WearingVest)
		
		TurnEntity Collider, 0.0, -the_yaw#, 0.0, 1 ; Turn the user on the Y (yaw) axis.
		user_camera_pitch# = user_camera_pitch# + the_pitch#
		; -- Limit the user;s camera To within 180 degrees of pitch rotation. ;EntityPitch(); returns useless values so we need To use a variable To keep track of the camera pitch.
		If user_camera_pitch# > 70.0 Then user_camera_pitch# = 70.0
		If user_camera_pitch# < - 70.0 Then user_camera_pitch# = -70.0
		
		RotateEntity Camera, WrapAngle(user_camera_pitch + Rnd(-CameraShake*FPSfactor, CameraShake*FPSfactor) * ViewBobScale), WrapAngle(EntityYaw(Collider) + Rnd(-CameraShake*FPSfactor, CameraShake*FPSfactor) * ViewBobScale), roll + bobRoll ; Pitch the user;s camera up And down.
		
		If PlayerRoom\RoomTemplate\Name = "pocketdimension" Lor PlayerRoom\RoomTemplate\Name = "halloweenpd" Then
			If EntityY(Collider)<2000*RoomScale Or EntityY(Collider)>2608*RoomScale Then
				RotateEntity Camera, WrapAngle(EntityPitch(Camera)),WrapAngle(EntityYaw(Camera)), roll+WrapAngle(Sin(MilliSecs()/150.0)*30.0)*ViewBobScale ; Pitch the user;s camera up And down.
			EndIf
		EndIf
		
	Else
		HideEntity Collider
		PositionEntity Camera, EntityX(Head), EntityY(Head), EntityZ(Head)
		
		Local CollidedFloor% = False
		For i = 1 To CountCollisions(Head)
			If CollisionY(Head, i) < EntityY(Head) - 0.01 Then CollidedFloor = True
		Next
		
		If CollidedFloor = True Then
			HeadDropSpeed# = 0
		Else
			
			If KillAnim = 0 Then 
				MoveEntity Head, 0, 0, HeadDropSpeed
				RotateEntity(Head, CurveAngle(-90.0, EntityPitch(Head), 20.0), EntityYaw(Head), EntityRoll(Head))
				RotateEntity(Camera, CurveAngle(EntityPitch(Head) - 40.0, EntityPitch(Camera), 40.0), EntityYaw(Camera), EntityRoll(Camera))
			Else
				MoveEntity Head, 0, 0, -HeadDropSpeed
				RotateEntity(Head, CurveAngle(90.0, EntityPitch(Head), 20.0), EntityYaw(Head), EntityRoll(Head))
				RotateEntity(Camera, CurveAngle(EntityPitch(Head) + 40.0, EntityPitch(Camera), 40.0), EntityYaw(Camera), EntityRoll(Camera))
			EndIf
			
			HeadDropSpeed# = HeadDropSpeed - 0.002 * FPSfactor
		EndIf
		
		If InvertMouse Then
			TurnEntity (Camera, -MouseYSpeed() * 0.05 * FPSfactor, -MouseXSpeed() * 0.15 * FPSfactor, 0)
		Else
			TurnEntity (Camera, MouseYSpeed() * 0.05 * FPSfactor, -MouseXSpeed() * 0.15 * FPSfactor, 0)
		End If
		
	EndIf
	
	;pölyhiukkasia
	If ParticleAmount=2
		If Rand(35/FPSfactor) = 1 Then
			Local pvt% = CreatePivot()
			PositionEntity(pvt, EntityX(Camera, True), EntityY(Camera, True), EntityZ(Camera, True))
			RotateEntity(pvt, 0, Rnd(360), 0)
			If Rand(2) = 1 Then
				MoveEntity(pvt, 0, Rnd(-0.5, 0.5), Rnd(0.5, 1.0))
			Else
				MoveEntity(pvt, 0, Rnd(-0.5, 0.5), Rnd(0.5, 1.0))
			End If
			
			Local p.Particles = CreateParticle(EntityX(pvt), EntityY(pvt), EntityZ(pvt), 2, 0.002, 0, 300)
			p\speed = 0.001
			RotateEntity(p\pvt, Rnd(-20, 20), Rnd(360), 0)
			
			p\SizeChange = -0.00001
			
			FreeEntity pvt
		End If
	EndIf
	
	If SelectedEnding = "" And handleInput Then MoveMouse viewport_center_x, viewport_center_y
End Function

;--------------------------------------- GUI, menu etc ------------------------------------------------


Function CanTeleportToHalloweenRoom()
	Select PlayerRoom\RoomTemplate\Name
		Case "gatea", "dimension1499", "pocketdimension", "halloweenpd", "halloweenfields"
			Return False
		Case "exit1", "room860", "room1123"
			Return EntityY(Collider)<1
		Default
			Return True
	End Select
End Function

Function IsTeleportingFromHalloweenCandy()
	For i.Items = Each Items
		If i\itemtemplate\group = "candy" And i\state > 0 Then
			Return True
		EndIf
	Next
	Return False
End Function

Function TeleportFromHalloweenRoom%()
	For r.Rooms = Each Rooms
		If r\RoomTemplate\Name = "room4info" Then
			Teleport(r)
			Return True
		EndIf
	Next
	If Not found Then
		For r.Rooms = Each Rooms
			If r\RoomTemplate\Name = "room2testroom2" Then
				Teleport(r)
				Return True
			EndIf
		Next
	EndIf
	Return False
End Function

Function FillHalloweenPDWithBones(r.Rooms)
	Local i% = 0
	While r\Objects[i] <> 0
		CreateItem("bone", EntityX(r\Objects[i], True), EntityY(r\Objects[i], True), EntityZ(r\Objects[i], True))
		i = i + 1
	Wend
End Function



Function ResetDiseases()
	DeathTimer = 0
	Infect = 0
	Stamina = 100
	For i = 0 To 5
		SCP1025state[i]=0
	Next
	If StaminaEffect > 1.0 Then
		StaminaEffect = 1.0
		StaminaEffectTimer = 0.0
	EndIf
End Function


Global ShowMap% = False

Function DrawMap()
	Local cellSize% = 20 * HUDScale
	Local startX% = HUDEndX - (MapWidth + 1) * cellSize
	Local startY% = HUDEndY - (MapHeight + 1) * cellSize

	For r.Rooms = Each Rooms					
		If PlayerRoom\x = r\x And PlayerRoom\z = r\z Then
			Color 0, 255, 0
		Else
			Color r\RoomTemplate\r, r\RoomTemplate\g, r\RoomTemplate\b
		EndIf
		
		Local x% = startX + ((18 - (r\x / 8)) * cellSize)
		Local y% = startY + ((r\z / 8) * cellSize)

		If MouseOn(x, y, cellSize, cellSize) 
			Color 100, 255, 100
			If MouseHit1
				Teleport(r)
			EndIf
		EndIf
		Rect(x, y, cellSize, cellSize, 1)
	Next
End Function

Function DrawTimer()
	SetFont(Font2)
	Local durText$ = FormatDuration(PlayTime)
	Local x% = HUDEndX - StringWidth(durText) - 24 * UIScale
	Local y% = HUDStartY + 24 * UIScale
	Color 0, 0, 0
	Text(x + 3 * UIScale, y + 3 * UIScale, durText)
	If UsedConsole Then
		Color 150, 150, 150
	Else If First ActiveMods <> Null Then
		Color 200, 200, 200
	Else If PreMadeSaveLoaded Then
		Color 175, 175, 175
	Else
		Color 255, 255, 255
	EndIf
	Text(x, y, durText)
	SetFont(Font1)
End Function

Function PadLeft$(txt$, padding$, targetLen%)
	Local req% = (targetLen - Len(txt)) / Len(padding)
	Return String(padding, req) + txt
End Function

Function FormatDuration$(totalMillis%, highPrecision%=True)
	Local ret$
	Local millis% = totalMillis Mod 1000
	Local totalSeconds% = totalMillis / 1000
	Local seconds% = totalSeconds Mod 60
	Local totalMinutes% = totalSeconds / 60
	Local minutes = totalMinutes Mod 60
	Local totalHours% = totalMinutes / 60
	Local hours% = totalHours Mod 24
	Local totalDays% = totalHours / 24
	Local hadLarger = False
	If totalDays > 0 Then
		ret = ret + Str(totalDays) + ":"
	EndIf
	If totalHours > 0 Lor (Not highPrecision) Then
		ret = ret + PadLeft(Str(hours), "0", 2) + ":"
		hadLarger = True
	EndIf
	ret = ret + PadLeft(Str(minutes), "0", 2) + ":" + PadLeft(Str(seconds), "0", 2)
	If highPrecision Then
		Return ret + "." + PadLeft(Str(millis), "0", 3)
	Else
		Return ret
	EndIf
End Function

Function DrawGUI()
	If Not HUDenabled Then Return

	Color 255,255,255
	Local cx% = GraphicWidth/2
	Local cy% = GraphicHeight/2
	Rect cx-1,cy-1,3,3,True

	Local pw% = Int(250*UIScale)
	Local ph% = Int(104*UIScale)
	Local px% = GraphicWidth-pw-Int(24*UIScale)
	Local py% = GraphicHeight-ph-Int(24*UIScale)
	Local bw% = pw-Int(28*UIScale)
	Local health# = Max(Min(100.0-Injuries,100.0),0.0)
	Local stamina# = Max(Min(Stamina,100.0),0.0)

	For i% = 1 To ph-2
		Local shade% = 24-Int(20.0*i%/ph)
		Color shade,shade,shade
		Rect px+1,py+i%,pw-2,1,False
	Next

	Color 255,255,255
	Local b% = Int(8*UIScale)
	Rect px,py,b,1,False
	Rect px,py,1,b,False
	Rect px+pw-b,py,b,1,False
	Rect px+pw-1,py,1,b,False
	Rect px,py+ph-1,b,1,False
	Rect px,py+ph-b,1,b,False
	Rect px+pw-b,py+ph-1,b,1,False
	Rect px+pw-1,py+ph-b,1,b,False

	Color 255,255,255
	SetFont Font7
	Text px+Int(14*UIScale),py+Int(10*UIScale),"HEALTH",False,False
	Color 45,45,45
	Rect px+Int(14*UIScale),py+Int(28*UIScale),bw,Int(8*UIScale),True
	Color 255,255,255
	Rect px+Int(14*UIScale),py+Int(28*UIScale),bw*health/100.0,Int(8*UIScale),True

	Text px+Int(14*UIScale),py+Int(52*UIScale),"STAMINA",False,False
	Color 45,45,45
	Rect px+Int(14*UIScale),py+Int(70*UIScale),bw,Int(8*UIScale),True
	Color 255,255,255
	Rect px+Int(14*UIScale),py+Int(70*UIScale),bw*stamina/100.0,Int(8*UIScale),True
	SetFont Font1
End Function

Function DrawMenu()
	If Not MenuOpen Then
		MenuWasOpen = False
		Return
	EndIf

	If Not MenuWasOpen Then
		MenuSetup(2)
		MenuWasOpen = True
	EndIf

	Local act% = MenuInput()
	MenuDraw()

	If act% = 0 Then
		MenuOpen = False
		HidePointer()
		UpdateMenuState()
	ElseIf act% = 1 Then
		MenuOpen = False
		NullGame(False)
		MainMenuOpen = True
		MenuWasOpen = False
	ElseIf act% = 2 Then
		End
	EndIf
End Function


Function GetSeedString$(loc%=True)
	If loc Then
		If HasNumericSeed Then
			Return I_Loc\Menu_SeedNumeric+" "+Str(RandomSeedNumeric)
		Else
			Return I_Loc\Menu_Seed+" "+RandomSeed
		EndIf
	Else
		If HasNumericSeed Then
			Return "Map seed (numeric): "+Str(RandomSeedNumeric)
		Else
			Return "Map seed: "+RandomSeed
		EndIf
	EndIf
End Function

Function MouseOn%(x%, y%, width%, height%)
	If ScaledMouseX() > x And ScaledMouseX() < x + width Then
		If ScaledMouseY() > y And ScaledMouseY() < y + height Then
			Return True
		End If
	End If
	Return False
End Function

;----------------------------------------------------------------------------------------------

Include "LoadAllSounds.bb"
Function LoadEntities()
	CatchErrors("Uncaught (LoadEntities)")
	DrawLoading(0)
	
	Local i%
	
	For i=0 To 9
		TempSounds[i]=0
	Next
	
	If PauseMenuIMG = 0 Then PauseMenuIMG% = LoadImage_Strict("GFX\menu\pausemenu.jpg", UIScale)
	
	If SprintIcon = 0 Then SprintIcon% = LoadImageHUDScaled("GFX\sprinticon.png")
	If BlinkIcon = 0 Then BlinkIcon% = LoadImageHUDScaled("GFX\blinkicon.png")
	If CrouchIcon = 0 Then CrouchIcon% = LoadImageHUDScaled("GFX\sneakicon.png")
	If HandIcon = 0 Then HandIcon% = LoadImageHUDScaled("GFX\handsymbol.png")
	If HandIcon2 = 0 Then HandIcon2% = LoadImageHUDScaled("GFX\handsymbol2.png")

	If StaminaMeterIMG = 0 Then StaminaMeterIMG% = LoadImageHUDScaled("GFX\staminameter.png")

	If Panel294 = 0 Then Panel294 = LoadImageHUDScaled("GFX\294panel.jpg")


	
	Brightness% = GetModdedINIFloat(MapOptions, "facility", "brightness")
	CameraFogNear# = GetModdedINIFloat(MapOptions, "facility", "camera fog near")
	CameraFogFar# = GetModdedINIFloat(MapOptions, "facility", "camera fog far")
	StoredCameraFogFar# = CameraFogFar
	
	;TextureLodBias
	
	AmbientLightRoomTex% = CreateTexture(2,2,1+256+1024)
	AmbientLight = GetModdedINIInt(MapOptions, "facility", "ambient light")
	AmbientLightNVG = GetModdedINIInt(MapOptions, "facility", "ambient light nvg")

	TextureBlend AmbientLightRoomTex,5
	SetBuffer(TextureBuffer(AmbientLightRoomTex))
	ClsColor 0,0,0
	Cls
	SetBuffer BackBuffer()
	AmbientLightRoomVal = 0
	
	SoundEmitter = CreatePivot()
	
	Camera = CreateCamera()
	CameraViewport Camera,0,0,GraphicWidth,GraphicHeight
	CameraRange(Camera, 0.05, CameraFogFar)
	CameraFogMode (Camera, 1)
	CameraFogRange (Camera, CameraFogNear, CameraFogFar)
	AmbientLight 0, 0, 0
	
	ScreenTexs[0] = CreateTexture(512, 512, 1+256+1024)
	ScreenTexs[1] = CreateTexture(512, 512, 1+256+1024)
	ScreenTexs[2] = CreateTexture(512, 512, 8192)
	
	CreateOverlayCamera()
	CameraProjMode ark_blur_cam,0
	;Listener = CreateListener(Camera)
	
	FogTexture = LoadTexture_Strict("GFX\fog.jpg", 1)
	
	Fog = CreateSprite(ark_blur_cam)
	ScaleSprite(Fog, 1.0, Max(Float(GraphicHeight) / Float(GraphicWidth), 0.8))
	EntityTexture(Fog, FogTexture)
	EntityBlend (Fog, 2)
	EntityOrder Fog, -1000
	MoveEntity(Fog, -PixelWidth, PixelHeight, 1.0)
	
	GasMaskTexture = LoadTexture_Strict("GFX\GasmaskOverlay.jpg", 1)
	GasMaskOverlay = CreateSprite(ark_blur_cam)
	ScaleSprite(GasMaskOverlay, 1.0, Max(Float(GraphicHeight) / Float(GraphicWidth), 0.8))
	EntityTexture(GasMaskOverlay, GasMaskTexture)
	EntityBlend (GasMaskOverlay, 2)
	EntityFX(GasMaskOverlay, 1)
	EntityOrder GasMaskOverlay, -1003
	MoveEntity(GasMaskOverlay, -PixelWidth, PixelHeight, 1.0)
	HideEntity(GasMaskOverlay)
	
	InfectTexture = LoadTexture_Strict("GFX\InfectOverlay.jpg", 1)
	InfectOverlay = CreateSprite(ark_blur_cam)
	ScaleSprite(InfectOverlay, 1.0, Max(Float(GraphicHeight) / Float(GraphicWidth), 0.8))
	EntityTexture(InfectOverlay, InfectTexture)
	EntityBlend (InfectOverlay, 3)
	EntityFX(InfectOverlay, 1)
	EntityOrder InfectOverlay, -1003
	MoveEntity(InfectOverlay, -PixelWidth, PixelHeight, 1.0)
	;EntityAlpha (InfectOverlay, 255.0)
	HideEntity(InfectOverlay)
	
	NVTexture = LoadTexture_Strict("GFX\NightVisionOverlay.jpg", 1)
	NVOverlay = CreateSprite(ark_blur_cam)
	ScaleSprite(NVOverlay, 1.0, Max(Float(GraphicHeight) / Float(GraphicWidth), 0.8))
	EntityTexture(NVOverlay, NVTexture)
	EntityBlend (NVOverlay, 2)
	EntityFX(NVOverlay, 1)
	EntityOrder NVOverlay, -1003
	MoveEntity(NVOverlay, -PixelWidth, PixelHeight, 1.0)
	HideEntity(NVOverlay)
	NVBlink = CreateSprite(ark_blur_cam)
	ScaleSprite(NVBlink, 1.0, Max(Float(GraphicHeight) / Float(GraphicWidth), 0.8))
	EntityColor(NVBlink,0,0,0)
	EntityFX(NVBlink, 1)
	EntityOrder NVBlink, -1005
	MoveEntity(NVBlink, -PixelWidth, PixelHeight, 1.0)
	HideEntity(NVBlink)
	
	FogNVTexture = LoadTexture_Strict("GFX\fogNV.jpg", 1)

	LoadSubtitleEntities()
	
	DrawLoading(5)
	
	DarkTexture = CreateTexture(1, 1, 1 + 2)
	SetBuffer TextureBuffer(DarkTexture)
	Cls
	SetBuffer BackBuffer()
	
	Dark = CreateSprite(ark_blur_cam)
	ScaleSprite(Dark, 1.0, Max(Float(GraphicHeight) / Float(GraphicWidth), 0.8))
	EntityTexture(Dark, DarkTexture)
	EntityBlend (Dark, 1)
	EntityOrder Dark, -1002
	MoveEntity(Dark, -PixelWidth, PixelHeight, 1.0)
	EntityAlpha Dark, 0.0
	
	LightTexture = CreateTexture(1, 1, 1 + 2+256)
	SetBuffer TextureBuffer(LightTexture)
	ClsColor 255, 255, 255
	Cls
	ClsColor 0, 0, 0
	SetBuffer BackBuffer()
	
	TeslaTexture = LoadTexture_Strict("GFX\map\tesla.jpg", 1+2)
	
	Light = CreateSprite(ark_blur_cam)
	ScaleSprite(Light, 1.0, Max(Float(GraphicHeight) / Float(GraphicWidth), 0.8))
	EntityTexture(Light, LightTexture)
	EntityBlend (Light, 1)
	EntityOrder Light, -1002
	MoveEntity(Light, -PixelWidth, PixelHeight, 1.0)
	HideEntity Light
	
	Collider = CreatePivot()
	EntityRadius Collider, 0.15, 0.30
	EntityPickMode(Collider, 1)
	EntityType Collider, HIT_PLAYER
	
	Head = CreatePivot()
	EntityRadius Head, 0.15
	EntityType Head, HIT_PLAYER
	
	
	LiquidObj = LoadMesh_Strict("GFX\items\cupliquid.x") ;optimized the cups dispensed by 294
	HideEntity LiquidObj
	
	MTFObj = LoadAnimMesh_Strict("GFX\npcs\MTF2.b3d") ;optimized MTFs
	GuardObj = LoadAnimMesh_Strict("GFX\npcs\guard.b3d") ;optimized Guards
	;GuardTex = LoadTexture_Strict("GFX\npcs\body.jpg") ;optimized the guards even more
	
	;If BumpEnabled Then
	;	bump1 = LoadTexture_Strict("GFX\npcs\mtf_newnormal01.png")
	;	;TextureBlend bump1, FE_BUMP ;USE DOT3
	;		
	;	For i = 2 To CountSurfaces(MTFObj)
	;		sf = GetSurface(MTFObj,i)
	;		b = GetSurfaceBrush( sf )
	;		t1 = GetBrushTexture(b,0)
	;		
	;		Select Lower(StripPath(TextureName(t1)))
	;			Case "MTF_newdiffuse02.png"
	;				
	;				BrushTexture b, bump1, 0, 0
	;				BrushTexture b, t1, 0, 1
	;				PaintSurface sf,b
	;		End Select
	;		FreeBrush b
	;		FreeTexture t1
	;	Next
	;	FreeTexture bump1	
	;EndIf
	
	
	
	ClassDObj = LoadAnimMesh_Strict("GFX\npcs\classd.b3d") ;optimized Class-D's and scientists/researchers
	ApacheObj = LoadAnimMesh_Strict("GFX\apache.b3d") ;optimized Apaches (helicopters)
	ApacheRotorObj = LoadAnimMesh_Strict("GFX\apacherotor.b3d") ;optimized the Apaches even more
	
	HideEntity MTFObj
	HideEntity GuardObj
	HideEntity ClassDObj
	HideEntity ApacheObj
	HideEntity ApacheRotorObj
	
	;Other NPCs pre-loaded
	;[Block]
	NPC049OBJ = LoadAnimMesh_Strict("GFX\npcs\scp-049.b3d")
	HideEntity NPC049OBJ
	NPC0492OBJ = LoadAnimMesh_Strict("GFX\npcs\zombie1.b3d")
	HideEntity NPC0492OBJ
	ClerkOBJ = LoadAnimMesh_Strict("GFX\npcs\clerk.b3d")
	HideEntity ClerkOBJ	
	;[End Block]
	
;	For i=0 To 4
;		Select True
;			Case i=2
;				tempStr="2c"
;			Case i>2
;				tempStr=Str(i)
;			Default
;				tempStr=Str(i+1)
;		End Select
;		OBJTunnel(i)=LoadRMesh("GFX\map\mt"+tempStr+".rmesh",Null)
;		HideEntity OBJTunnel(i)
;	Next
	
;	OBJTunnel(0)=LoadRMesh("GFX\map\mt1.rmesh",Null)	
;	HideEntity OBJTunnel(0)				
;	OBJTunnel(1)=LoadRMesh("GFX\map\mt2.rmesh",Null)	
;	HideEntity OBJTunnel(1)
;	OBJTunnel(2)=LoadRMesh("GFX\map\mt2c.rmesh",Null)	
;	HideEntity OBJTunnel(2)				
;	OBJTunnel(3)=LoadRMesh("GFX\map\mt3.rmesh",Null)	
;	HideEntity OBJTunnel(3)	
;	OBJTunnel(4)=LoadRMesh("GFX\map\mt4.rmesh",Null)	
;	HideEntity OBJTunnel(4)				
;	OBJTunnel(5)=LoadRMesh("GFX\map\mt_elevator.rmesh",Null)
;	HideEntity OBJTunnel(5)
;	OBJTunnel(6)=LoadRMesh("GFX\map\mt_generator.rmesh",Null)
;	HideEntity OBJTunnel(6)
	
	LightSpriteTex(0) = LoadTexture_Strict("GFX\light1.jpg", 1)
	LightSpriteTex(1) = LoadTexture_Strict("GFX\light2.jpg", 1)
	LightSpriteTex(2) = LoadTexture_Strict("GFX\lightsprite.jpg",1)
	
	DrawLoading(10)
	
	DoorOBJ = LoadMesh_Strict("GFX\map\door01.x")
	HideEntity DoorOBJ
	DoorFrameOBJ = LoadMesh_Strict("GFX\map\doorframe.x")
	HideEntity DoorFrameOBJ
	
	HeavyDoorObj(0) = LoadMesh_Strict("GFX\map\heavydoor1.x")
	HideEntity HeavyDoorObj(0)
	HeavyDoorObj(1) = LoadMesh_Strict("GFX\map\heavydoor2.x")
	HideEntity HeavyDoorObj(1)
	
	DoorColl = LoadMesh_Strict("GFX\map\doorcoll.x")
	HideEntity DoorColl
	
	ButtonOBJ = LoadMesh_Strict("GFX\map\Button.x")
	HideEntity ButtonOBJ
	ButtonCodeOBJ = LoadMesh_Strict("GFX\map\ButtonCode.x")
	HideEntity ButtonCodeOBJ	
	
	BigDoorOBJ(0) = LoadMesh_Strict("GFX\map\ContDoorLeft.x")
	HideEntity BigDoorOBJ(0)
	BigDoorOBJ(1) = LoadMesh_Strict("GFX\map\ContDoorRight.x")
	HideEntity BigDoorOBJ(1)
	
	LeverBaseOBJ = LoadMesh_Strict("GFX\map\leverbase.x")
	HideEntity LeverBaseOBJ
	LeverOBJ = LoadMesh_Strict("GFX\map\leverhandle.x")
	HideEntity LeverOBJ
	
	;For i = 0 To 1
	;	HideEntity BigDoorOBJ(i)
	;	;If BumpEnabled And 0 Then
	;	If BumpEnabled
	;		
	;		Local bumptex = LoadTexture_Strict("GFX\map\containmentdoorsbump.jpg")
	;		;TextureBlend bumptex, FE_BUMP
	;		Local tex = LoadTexture_Strict("GFX\map\containment_doors.jpg")	
	;		EntityTexture BigDoorOBJ(i), bumptex, 0, 0
	;		EntityTexture BigDoorOBJ(i), tex, 0, 1
	;		
	;		;FreeEntity tex
	;		;FreeEntity bumptex
	;		FreeTexture tex
	;		FreeTexture bumptex
	;	EndIf
	;Next
	
	DrawLoading(15)
	
	For i = 0 To 5
		GorePics(i) = LoadTexture_Strict("GFX\895pics\pic" + (i + 1) + ".jpg")
	Next
	
	OldAiPics(0) = LoadTexture_Strict("GFX\AIface.jpg")
	OldAiPics(1) = LoadTexture_Strict("GFX\AIface2.jpg")	
	
	DrawLoading(20)
	
	For i = 0 To 6
		DecalTextures(i) = LoadTexture_Strict("GFX\decal" + (i + 1) + ".png", 1 + 2)
	Next
	DecalTextures(7) = LoadTexture_Strict("GFX\items\INVpaperstrips.png", 1 + 2)
	For i = 8 To 12
		DecalTextures(i) = LoadTexture_Strict("GFX\decalpd"+(i-7)+".jpg", 1 + 2)	
	Next
	For i = 13 To 14
		DecalTextures(i) = LoadTexture_Strict("GFX\bullethole"+(i-12)+".jpg", 1 + 2)	
	Next	
	For i = 15 To 16
		DecalTextures(i) = LoadTexture_Strict("GFX\blooddrop"+(i-14)+".png", 1 + 2)	
	Next
	DecalTextures(17) = LoadTexture_Strict("GFX\decal8.png", 1 + 2)	
	DecalTextures(18) = LoadTexture_Strict("GFX\decalpd6.dc", 1 + 2)	
	DecalTextures(20) = LoadTexture_Strict("GFX\decal427.png", 1 + 2)
	DecalTextures(21) = LoadTexture_Strict("GFX\smoke2.png", 1 + 2)
	DecalTextures(22) = LoadTexture_Strict("GFX\bd\Confetti.png", 1 + 2)
	
	DrawLoading(24)
	
	Monitor = LoadMesh_Strict("GFX\map\monitor.b3d")
	HideEntity Monitor
	MonitorTexture = LoadTexture_Strict("GFX\monitortexture.jpg")
	
	CamBaseOBJ = LoadMesh_Strict("GFX\map\cambase.x")
	HideEntity(CamBaseOBJ)
	CamOBJ = LoadMesh_Strict("GFX\map\CamHead.b3d")
	HideEntity(CamOBJ)

	Monitor2 = LoadMesh_Strict("GFX\map\monitor_checkpoint.b3d")
	HideEntity Monitor2
	Monitor3 = LoadMesh_Strict("GFX\map\monitor_checkpoint.b3d")
	HideEntity Monitor3
	MonitorTexture2 = LoadTexture_Strict("GFX\map\LockdownScreen2.jpg")
	MonitorTexture3 = LoadTexture_Strict("GFX\map\LockdownScreen.jpg")
	MonitorTexture4 = LoadTexture_Strict("GFX\map\LockdownScreen3.jpg")
	MonitorTextureOff = CreateTexture(1,1)
	SetBuffer TextureBuffer(MonitorTextureOff)
	ClsColor 0,0,0
	Cls
	SetBuffer BackBuffer()
	LightConeModel = LoadMesh_Strict("GFX\lightcone.b3d")
	HideEntity LightConeModel
	
	Local name$
	For i = 2 To CountSurfaces(Monitor2)
		Local sf = GetSurface(Monitor2,i)
		Local b = GetSurfaceBrush(sf)
		If b<>0 Then
			Local t1 = GetBrushTexture(b,0)
			If t1<>0 Then
				name$ = StripPath(TextureName(t1))
				If Lower(name) <> "monitortexture.jpg"
					BrushTexture b, MonitorTextureOff, 0, 0
					PaintSurface sf,b
				EndIf
				If name<>"" Then FreeTexture t1
			EndIf
			FreeBrush b
		EndIf
	Next
	For i = 2 To CountSurfaces(Monitor3)
		sf = GetSurface(Monitor3,i)
		b = GetSurfaceBrush(sf)
		If b<>0 Then
			t1 = GetBrushTexture(b,0)
			If t1<>0 Then
				name$ = StripPath(TextureName(t1))
				If Lower(name) <> "monitortexture.jpg"
					BrushTexture b, MonitorTextureOff, 0, 0
					PaintSurface sf,b
				EndIf
				If name<>"" Then FreeTexture t1
			EndIf
			FreeBrush b
		EndIf
	Next
	
	UserTrackMusicAmount% = 0
	If EnableUserTracks Then
		Local dirPath$ = "SFX\Radio\UserTracks\"
		If FileType(dirPath)<>2 Then
			CreateDir(dirPath)
		EndIf
		
		Local Dir% = ReadDir("SFX\Radio\UserTracks\")
		Repeat
			Local file$=NextFile(Dir)
			If file$="" Then Exit
			If FileType("SFX\Radio\UserTracks\"+file$) = 1 Then
				Local ext$ = File_GetExtension(file$)
				For i=1 To SoundExtensionCount
					If ext = SoundExtensions[i-1] Then
						Local test = LoadSound("SFX\Radio\UserTracks\"+file$)
						If test<>0
							UserTrackName$(UserTrackMusicAmount%) = file$
							UserTrackMusicAmount% = UserTrackMusicAmount% + 1
						EndIf
						FreeSound test
					EndIf
				Next
			EndIf
		Forever
		CloseDir Dir
	EndIf
	If EnableUserTracks Then DebugLog "User Tracks found: "+UserTrackMusicAmount
	
	DrawLoading(25)

	InitItemTemplates()
	
	DrawLoading(35)

	ParticleTextures(0) = LoadTexture_Strict("GFX\smoke.png", 1 + 2)
	ParticleTextures(1) = LoadTexture_Strict("GFX\flash.jpg", 1 + 2)
	ParticleTextures(2) = LoadTexture_Strict("GFX\dust.png", 1 + 2)
	ParticleTextures(3) = LoadTexture_Strict("GFX\npcs\hg.pt", 1 + 2)
	ParticleTextures(4) = LoadTexture_Strict("GFX\map\sun.jpg", 1 + 2)
	ParticleTextures(5) = LoadTexture_Strict("GFX\bloodsprite.png", 1 + 2)
	ParticleTextures(6) = LoadTexture_Strict("GFX\smoke2.png", 1 + 2)
	ParticleTextures(7) = LoadTexture_Strict("GFX\spark.jpg", 1 + 2)
	ParticleTextures(8) = LoadTexture_Strict("GFX\particle.png", 1 + 2)
	
	
	NavBG = CreateTexture(GraphicWidth,GraphicHeight, 1 + 1024)

	;NPCtypeD - different models with different textures (loaded using "CopyEntity") - ENDSHN
	;[Block]
	For i=1 To MaxDTextures-1
		DTextures[i] = CopyEntity(ClassDObj)
		HideEntity DTextures[i]
	Next
	;Gonzales
	Local tex = LoadTexture_Strict("GFX\npcs\gonzales.jpg")
	EntityTexture DTextures[1],tex
	FreeTexture tex
	;SCP-970 corpse
	tex = LoadTexture_Strict("GFX\npcs\corpse.jpg")
	EntityTexture DTextures[2],tex
	FreeTexture tex
	;scientist 1
	tex = LoadTexture_Strict("GFX\npcs\scientist.jpg")
	EntityTexture DTextures[3],tex
	FreeTexture tex
	;scientist 2
	tex = LoadTexture_Strict("GFX\npcs\scientist2.jpg")
	EntityTexture DTextures[4],tex
	FreeTexture tex
	;janitor
	tex = LoadTexture_Strict("GFX\npcs\janitor.jpg")
	EntityTexture DTextures[5],tex
	FreeTexture tex
	;106 Victim
	tex = LoadTexture_Strict("GFX\npcs\106victim.jpg")
	EntityTexture DTextures[6],tex
	FreeTexture tex
	;2nd ClassD
	tex = LoadTexture_Strict("GFX\npcs\classd2.jpg")
	EntityTexture DTextures[7],tex
	FreeTexture tex
	;035 victim
	tex = LoadTexture_Strict("GFX\npcs\035victim.jpg")
	EntityTexture DTextures[8],tex
	FreeTexture tex
	
	;[End Block]

	InitMaterials()

	tex = 0
	If IsHalloween Then
		tex = LoadTexture_Strict("GFX\map\Halloween\miscsigns3_Halloween.png", 1)
	Else If IsBirthday Then
		tex = LoadTexture_Strict("GFX\map\miscsigns3.ae", 1)
	Else
		tex = LoadTexture_Strict("GFX\map\Halloween\miscsigns3_Pumpkin.png", 1)
	End If
	If tex <> 0 Then
		TextureBlend(tex, 5)
		AddTextureToCache(tex, "GFX\map\miscsigns3.jpg")
	EndIf

	OBJTunnel(0)=LoadRMesh("GFX\map\mt1.rmesh",Null)	
	HideEntity OBJTunnel(0)				
	OBJTunnel(1)=LoadRMesh("GFX\map\mt2.rmesh",Null)	
	HideEntity OBJTunnel(1)
	OBJTunnel(2)=LoadRMesh("GFX\map\mt2c.rmesh",Null)	
	HideEntity OBJTunnel(2)				
	OBJTunnel(3)=LoadRMesh("GFX\map\mt3.rmesh",Null)	
	HideEntity OBJTunnel(3)	
	OBJTunnel(4)=LoadRMesh("GFX\map\mt4.rmesh",Null)	
	HideEntity OBJTunnel(4)				
	OBJTunnel(5)=LoadRMesh("GFX\map\mt_elevator.rmesh",Null)
	HideEntity OBJTunnel(5)
	OBJTunnel(6)=LoadRMesh("GFX\map\mt_generator.rmesh",Null)
	HideEntity OBJTunnel(6)
	
	DrawLoading(37)

	;TextureLodBias TextureBias
	TextureLodBias TextureFloat#
	;Devil Particle System
	;ParticleEffect[] numbers:
	;	0 - electric spark
	;	1 - smoke effect
	
	Local t0

	InitParticles(Camera)
	
	;Spark Effect (short)
	ParticleEffect[0] = CreateTemplate()
	SetTemplateEmitterBlend(ParticleEffect[0], 3)
	SetTemplateInterval(ParticleEffect[0], 1)
	SetTemplateParticlesPerInterval(ParticleEffect[0], 6)
	SetTemplateEmitterLifeTime(ParticleEffect[0], 6)
	SetTemplateParticleLifeTime(ParticleEffect[0], 20, 30)
	SetTemplateTexture(ParticleEffect[0], "GFX\Spark.png", 2, 3)
	SetTemplateOffset(ParticleEffect[0], -0.1, 0.1, -0.1, 0.1, -0.1, 0.1)
	SetTemplateVelocity(ParticleEffect[0], -0.0375, 0.0375, -0.0375, 0.0375, -0.0375, 0.0375)
	SetTemplateAlignToFall(ParticleEffect[0], True, 45)
	SetTemplateGravity(ParticleEffect[0], 0.001)
	SetTemplateAlphaVel(ParticleEffect[0], True)
	;SetTemplateSize(ParticleEffect[0], 0.0625, 0.125, 0.7, 1)
	SetTemplateSize(ParticleEffect[0], 0.03125, 0.0625, 0.7, 1)
	SetTemplateColors(ParticleEffect[0], $0000FF, $6565FF)
	SetTemplateFloor(ParticleEffect[0], 0.0, 0.5)
	
	;Smoke effect (for some vents)
	ParticleEffect[1] = CreateTemplate()
	SetTemplateEmitterBlend(ParticleEffect[1], 1)
	SetTemplateInterval(ParticleEffect[1], 1)
	SetTemplateEmitterLifeTime(ParticleEffect[1], 3)
	SetTemplateParticleLifeTime(ParticleEffect[1], 30, 45)
	SetTemplateTexture(ParticleEffect[1], "GFX\smoke2.png", 2, 1)
	;SetTemplateOffset(ParticleEffect[1], -.3, .3, -.3, .3, -.3, .3)
	SetTemplateOffset(ParticleEffect[1], 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
	;SetTemplateVelocity(ParticleEffect[1], -.04, .04, .1, .2, -.04, .04)
	SetTemplateVelocity(ParticleEffect[1], 0.0, 0.0, 0.02, 0.025, 0.0, 0.0)
	SetTemplateAlphaVel(ParticleEffect[1], True)
	;SetTemplateSize(ParticleEffect[1], 3, 3, .5, 1.5)
	SetTemplateSize(ParticleEffect[1], 0.4, 0.4, 0.5, 1.5)
	SetTemplateSizeVel(ParticleEffect[1], .01, 1.01)

	;Big doors closing
	ParticleEffect[2] = CreateTemplate()
	SetTemplateEmitterBlend(ParticleEffect[2], 3)
	SetTemplateEmitterLifeTime(ParticleEffect[2], 0)
	SetTemplateParticlesPerInterval(ParticleEffect[2], 80)
	SetTemplateParticleLifeTime(ParticleEffect[2], 90, 100)
	SetTemplateTexture(ParticleEffect[2], "GFX\dust.png", 2, 1)
	SetTemplateOffset(ParticleEffect[2], -0.2, 0.2, 0.0, 1.2, -0.2, 0.2)
	SetTemplateVelocity(ParticleEffect[2], -0.005, 0.005, -0.0017, 0.0017, -0.005, 0.005)
	SetTemplateSizeVel(ParticleEffect[2], -0.000005, 1)
	SetTemplateSize(ParticleEffect[2], 0.005, 0.005)
	SetTemplateAlphaVel(ParticleEffect[2], True)
	
	;BDC
	If IsBirthday Then
		For i = 3 To 7
			ParticleEffect[i] = CreateTemplate()
			SetTemplateEmitterBlend(ParticleEffect[i], 1)
			SetTemplateEmitterLifeTime(ParticleEffect[i], 60)
			SetTemplateParticleLifeTime(ParticleEffect[i], 500, 500)
			SetTemplateTexture(ParticleEffect[i], "GFX\bd\Confetti" + Str(i-2) + ".png", 2, 1)
			SetTemplateVelocity(ParticleEffect[i], -0.005, 0.005, 0, 0.005, -0.005, 0.005)
			SetTemplateOffset(ParticleEffect[i], -0.05, 0.05, 0.05, 0.05, -0.05, 0.05)
			SetTemplateGravity(ParticleEffect[i], 0.0001)
			SetTemplateSize(ParticleEffect[i], 0.01, 0.01)
			SetTemplateRotation(ParticleEffect[i], -30, 30)
		Next
	EndIf

	Room2slCam = CreateCamera()
	CameraViewport(Room2slCam, 0, 0, 128, 128)
	CameraRange Room2slCam, 0.05, 6.0
	CameraZoom(Room2slCam, 0.8)
	HideEntity(Room2slCam)
	
	DrawLoading(40)
	
	CatchErrors("LoadEntities")
End Function

Function InitNewGame()
	CatchErrors("Uncaught (InitNewGame)")
	Local i%, d.Doors, it.Items, r.Rooms, sc.SecurityCams

	DrawLoading(45)

	PlayTime = 0
	TimerStopped = False
	HideDistance# = 15.0
	HeartBeatRate = 70

	CreateMap(50, 19)
	DrawLoading(70)
	InitWayPoints(71, 9)
	DrawLoading(79)

	For d.Doors = Each Doors
		EntityParent(d\obj, 0)
		If d\obj2 <> 0 Then EntityParent(d\obj2, 0)
		If d\frameobj <> 0 Then EntityParent(d\frameobj, 0)
		If d\buttons[0] <> 0 Then EntityParent(d\buttons[0], 0)
		If d\buttons[1] <> 0 Then EntityParent(d\buttons[1], 0)

		If d\obj2 <> 0 And d\dir = 0 Then
			MoveEntity(d\obj, 0, 0, 8.0 * RoomScale)
			MoveEntity(d\obj2, 0, 0, 8.0 * RoomScale)
		EndIf
	Next

	For it.Items = Each Items
		ResetEntity(it\collider)
		EntityParent(it\collider, 0)
	Next

	DrawLoading(81)
	For sc.SecurityCams = Each SecurityCams
		sc\angle = EntityYaw(sc\obj) + sc\angle
		EntityParent(sc\obj, 0)
	Next

	For r.Rooms = Each Rooms
		For i = 0 To MaxRoomLights-1
			If r\Lights[i] <> 0 Then
				EntityParent(r\Lights[i],0)
			Else
				Exit
			EndIf
		Next

	Next

	If MTStartRoom <> Null Then
		PositionEntity(Collider, EntityX(MTStartRoom\obj), EntityY(MTStartRoom\obj) + 0.5, EntityZ(MTStartRoom\obj))
		PlayerRoom = MTStartRoom
	EndIf

	ResetAllRMeshes()
	TurnEntity(Collider, 0, Rand(160, 200), 0)
	ResetEntity Collider

	SetFont Font1
	HidePointer()
	BlinkTimer = -10
	Stamina = 100

	Playable = False
	For i% = 0 To 70
		FPSfactor = 1.0
		FlushKeys()
		MovePlayer()
		UpdateDoors()
		UpdateWorld()
		If (Int(Float(i)*0.27) <> Int(Float(i-1)*0.27)) Then
			DrawLoading(80+Int(Float(i)*0.27))
		EndIf
	Next
	Playable = True

	FreeTextureCache
	DrawLoading(100)

	MoveMouse viewport_center_x,viewport_center_y
	ResetInput()
	ExecAutoExec()

	DropSpeed = 0
	PrevTime = MilliSecs()
	CatchErrors("InitNewGame")
End Function


Function NullGame(playbuttonsfx%=True)
	CatchErrors("Uncaught (NullGame)")
	Local i%, x%, y%, lvl
	Local itt.ItemTemplates, s.Screens, lt.LightTemplates, d.Doors, m.Materials
	Local wp.WayPoints, twp.TempWayPoints, r.Rooms, it.Items
	
	KillSounds()
	If playbuttonsfx Then PlaySound_Strict ButtonSFX
	
	FreeParticles()
	
	ClearTextureCache
	
	If Not SpeedRunMode Then PlayTime = 0

	DebugHUD = False
	TimeScale = 1.0
	
	UnableToMove% = False
	
	
	DeathMSG$=""
	
	SelectedMap = -1
	
	UsedConsole = False
	
	DoorTempID = 0
	RoomTempID = 0
	
	GameSaved = 0
	
	HideDistance# = 15.0
	
	For lvl = 0 To 0
		For x = 0 To MapWidth+1
			For y = 0 To MapHeight+1
				MapTemp(x, y) = 0
				MapFound(x, y) = 0
			Next
		Next
	Next
	
	For itt.ItemTemplates = Each ItemTemplates
		itt\found = False
	Next
	
	DropSpeed = 0
	Shake = 0
	CurrSpeed = 0
	
	DeathTimer=0
	
	HeartBeatVolume = 0
	
	StaminaEffect = 1.0
	StaminaEffectTimer = 0
	BlinkEffect = 1.0
	BlinkEffectTimer = 0
	
	Bloodloss = 0
	Injuries = 0
	Infect = 0
	
	For i = 0 To 5
		SCP1025state[i] = 0
	Next
	
	SelectedEnding = ""
	EndingTimer = 0
	ExplosionTimer = 0
	
	CameraShake = 0
	Shake = 0
	BobPhase = 0
	BobPowerSmooth = 0
	LightFlash = 0
	
	user_camera_pitch = 0.0
	mouse_y_speed_1 = 0.0
	mouse_x_speed_1 = 0.0

	GodMode = 0
	NoClip = 0
	WireframeState = 0
	WireFrame 0
	WearingGasMask = 0
	WearingHazmat = 0
	WearingVest = 0
	Wearing714 = 0
	WearingPumpkin = 0
	PumpkinOverlay = 0
	If WearingNightVision Then
		CameraFogFar = StoredCameraFogFar
		WearingNightVision = 0
	EndIf
	I_427\Using = 0
	I_427\Timer = 0.0
	
	ForceMove = 0.0
	ForceAngle = 0.0	
	Playable = True
	
	CoffinDistance = 100
	
	For s.Screens = Each Screens
		If s\img <> 0 Then FreeImage s\img : s\img = 0
		Delete s
	Next
	
	ConsoleInput = ""
	ConsoleOpen = False
	
	EyeIrritation = 0
	EyeStuck = 0
	
	ShouldPlay = 0
	
	KillTimer = 0
	FallTimer = 0
	Stamina = 100
	BlurTimer = 0
	SuperMan = False
	SuperManTimer = 0
	LostHands = False
	Sanity = 0
	RestoreSanity = True
	Crouch = False
	CrouchState = 0.0
	LightVolume = 0.0
	Vomit = False
	VomitTimer = 0.0
	SecondaryLightOn# = True
	PrevSecondaryLightOn# = True
	RemoteDoorOn = True
	SoundTransmission = False
	
	InfiniteStamina% = False
	
	Msg = ""
	MsgTimer = 0
	
	SelectedItem = Null
	
	For i = 0 To MaxItemAmount - 1
		Inventory(i) = Null
	Next
	SelectedItem = Null
	
	ClosestButton = 0
	
	room2gw_brokendoor = False
	room2gw_x = 0.0
	room2gw_z = 0.0

	Delete Each Doors
	
	;ClearWorld
	
	Delete Each Triggerboxes
	Delete Each LightTemplates
	Delete Each Materials
	Delete Each WayPoints
	Delete Each Rooms	
	Delete Each Inventories
	Delete Each Items

	For itt.ItemTemplates = Each ItemTemplates
		FreeImageHUDScaled(itt\invimg)
		If itt\invimg2 <> 0 Then FreeImageHUDScaled(itt\invimg2)
		If itt\img <> 0 Then FreeImageHUDScaled(itt\img)
		Delete itt
	Next

	Delete Each Props
	Delete Each Decals
	Delete Each NPCs

	For sc.securitycams = Each SecurityCams
		Delete sc
	Next
	
	For em.emitters = Each Emitters
		Delete em
	Next	
	
	For p.particles = Each Particles
		Delete p
	Next
	
	ResetAllRMeshes()
	
	For i = 0 To 5
		If ChannelPlaying(RadioCHN(i)) Then StopChannel(RadioCHN(i))
	Next
	
	NTF_1499PrevX# = 0.0
	NTF_1499PrevY# = 0.0
	NTF_1499PrevZ# = 0.0
	NTF_1499PrevRoom = Null
	NTF_1499X# = 0.0
	NTF_1499Y# = 0.0
	NTF_1499Z# = 0.0
	Wearing1499% = False

	Delete Each Dummy1499
	
	DeleteElevatorObjects()
	
	DeleteDevilEmitters()
	
	LostHands = False

	NoTarget% = False
	
	OptionsMenu% = 0
	QuitMSG% = 0
	
	SFXVolume# = PrevSFXVolume
	DeafPlayer% = False
	DeafTimer# = 0.0
	
	If GuaranteedOmni <> 2 Then GuaranteedOmni = False Else UsedConsole = True

	If ShowMap <> 2 Then ShowMap = False Else UsedConsole = True

	IsZombie% = False
	
	
	For fr.Forest = Each Forest
		DestroyForest(fr)
	Next

	SetErrorMsg(7, "")

	;DeInitExt
	
	CatchErrors("Clear World")
	; Don't clear shaders
	ClearWorld(1, 1, 1, 0)
	CatchErrors("Uncaught (Clear World)")

	Camera = 0
	ark_blur_cam = 0
	Collider = 0
	Sky = 0
	InitFastResize()
	
	CatchErrors("NullGame")
End Function


;--------------------------------------- music & sounds ----------------------------------------------

Type FireAndForgetSounds
	Field Chn%
	Field Camera%
	Field Entity%
	Field Range#
	Field Volume#
	Field UseSFXVolume%
End Type

Function PlaySound2%(SoundHandle%, cam%, entity%, range# = 10, volume# = 1.0, useSFXVolume% = True)
	range# = Max(range, 1.0)
	Local soundchn% = 0
	
	If volume > 0 Then 
		Local s.FireAndForgetSounds = New FireAndForgetSounds
		s\Chn = PlaySound_Strict (SoundHandle)
		s\Camera = cam
		s\Entity = entity
		s\Range = range
		s\Volume = volume
		s\UseSFXVolume = useSFXVolume
		soundchn = s\Chn
		UpdateFireAndForgetSounds(s)
	EndIf
	
	Return soundchn
End Function

Function UpdateFireAndForgetSounds(s.FireAndForgetSounds)
	If (Not ChannelPlaying(s\Chn)) Lor (Not EntityExist(s\Entity)) Lor (Not EntityExist(s\Camera)) Then UpdateChannelVolumeWithSubtitles(s\Chn, 0) : Delete s : Return

	Local dist# = EntityDistance(s\Camera, s\Entity) / s\Range
	Local panvalue# = Sin(-DeltaYaw(s\Camera,s\Entity))
	
	UpdateChannelVolumeWithSubtitles(s\Chn, s\Volume * Max(0, 1 - dist), False, s\UseSFXVolume)
	ChannelPan(s\Chn, panvalue)	
End Function

Function LoopSound2%(SoundHandle%, Chn%, cam%, entity%, range# = 10, volume# = 1.0)
	range# = Max(range,1.0)
	
	If volume>0 Then
		
		Local dist# = EntityDistance(cam, entity) / range#
		;If 1 - dist# > 0 And 1 - dist# < 1 Then
			
			Local panvalue# = Sin(-DeltaYaw(cam,entity))
			
			If Chn = 0 Then
				Chn% = PlaySound_Strict (SoundHandle)
			Else
				If (Not ChannelPlaying(Chn)) Then Chn% = PlaySound_Strict (SoundHandle)
			EndIf
			
			UpdateChannelVolumeWithSubtitles(Chn, volume# * (1 - dist#))
			ChannelPan(Chn, panvalue)
		;EndIf
	Else
		If Chn <> 0 Then
			UpdateChannelVolumeWithSubtitles(Chn, 0)
		EndIf 
	EndIf
	
	Return Chn
End Function

Function LoadTempSound(file$)
	If TempSounds[TempSoundIndex]<>0 Then FreeSound_Strict(TempSounds[TempSoundIndex])
	Local TempSound = LoadSound_Strict(file)
	TempSounds[TempSoundIndex] = TempSound
	
	TempSoundIndex=(TempSoundIndex+1) Mod 10
	
	Return TempSound
End Function


Function UpdateMusic()
	
	If ConsoleFlush Then
		If Not ChannelPlaying(ConsoleMusPlay) Then ConsoleMusPlay = PlaySound(ConsoleMusFlush)
	ElseIf (Not PlayCustomMusic)
		If NowPlaying <> ShouldPlay ; playing the wrong clip, fade out
			CurrMusicVolume# = Max(CurrMusicVolume - (FPSfactor / 250.0), 0)
			If CurrMusicVolume = 0
				If NowPlaying<66
					StopStream_Strict(MusicCHN)
				EndIf
				NowPlaying = ShouldPlay
				MusicCHN = 0
				CurrMusic=0
			EndIf
		Else ; playing the right clip
			CurrMusicVolume = CurrMusicVolume + (MusicVolume - CurrMusicVolume) * (0.1*FPSfactor)
		EndIf
		
		If NowPlaying < 66
			If CurrMusic = 0
				MusicCHN = StreamSound_Strict("SFX\Music\"+Music(NowPlaying)+".ogg",0.0)
				CurrMusic = 1
			EndIf
			SetStreamVolume_Strict(MusicCHN,CurrMusicVolume)
		EndIf
	Else
		If FPSfactor > 0 Or OptionsMenu = 2 Then
			;CurrMusicVolume = 1.0
			If (Not ChannelPlaying(MusicCHN)) Then MusicCHN = PlaySound_Strict(CustomMusic)
			ChannelVolume MusicCHN,1.0*MusicVolume
		EndIf
	EndIf
	
End Function 

Function PauseSounds()
	For s.FireAndForgetSounds = Each FireAndForgetSounds
		PauseChannel(s\Chn)
	Next
	
	For n.npcs = Each NPCs
		If n\soundchn <> 0 Then
			If (Not n\soundchn_isstream)
				PauseChannelWithSubtitles(n\soundchn)
			Else
				SetStreamPaused_Strict(n\soundchn,True)
			EndIf
		EndIf
		If n\soundchn2 <> 0 Then
			If (Not n\soundchn2_isstream)
				PauseChannelWithSubtitles(n\soundchn2)
			Else
				SetStreamPaused_Strict(n\soundchn2,True)
			EndIf
		EndIf
	Next	
	
	For d.doors = Each Doors
		If d\soundchn <> 0 Then
			PauseChannelWithSubtitles(d\soundchn)
		EndIf
	Next
	
	For dem.DevilEmitters = Each DevilEmitters
		If dem\soundchn <> 0 Then
			PauseChannelWithSubtitles(dem\soundchn)
		EndIf
	Next
	
	If AmbientSFXCHN <> 0 Then
		PauseChannelWithSubtitles(AmbientSFXCHN)
	EndIf
	
	If BreathCHN <> 0 Then
		PauseChannelWithSubtitles(BreathCHN)
	EndIf
	
	If CoughCHN <> 0 Then
		PauseChannelWithSubtitles(CoughCHN)
	EndIf
	
	If VomitCHN <> 0 Then
		PauseChannelWithSubtitles(VomitCHN)
	EndIf
	
	If IntercomStreamCHN <> 0
		SetStreamPaused_Strict(IntercomStreamCHN,True)
	EndIf
End Function

Function ResumeSounds()
	For s.FireAndForgetSounds = Each FireAndForgetSounds
		ResumeChannel(s\Chn)
		UpdateFireAndForgetSounds(s)
	Next
	
	For n.npcs = Each NPCs
		If n\soundchn <> 0 Then
			If (Not n\soundchn_isstream)
				ResumeChannelWithSubtitles(n\soundchn)
			Else
				SetStreamPaused_Strict(n\soundchn,False)
			EndIf
		EndIf
		If n\soundchn2 <> 0 Then
			If (Not n\soundchn2_isstream)
				ResumeChannelWithSubtitles(n\soundchn2)
			Else
				SetStreamPaused_Strict(n\soundchn2,False)
			EndIf
		EndIf
	Next	
	
	For d.doors = Each Doors
		If d\soundchn <> 0 Then
			ResumeChannelWithSubtitles(d\soundchn)
		EndIf
	Next
	
	For dem.DevilEmitters = Each DevilEmitters
		If dem\soundchn <> 0 Then
			ResumeChannelWithSubtitles(dem\soundchn)
		EndIf
	Next
	
	If AmbientSFXCHN <> 0 Then
		ResumeChannelWithSubtitles(AmbientSFXCHN)
	EndIf	
	
	If BreathCHN <> 0 Then
		ResumeChannelWithSubtitles(BreathCHN)
	EndIf
	
	If CoughCHN <> 0 Then
		ResumeChannelWithSubtitles(CoughCHN)
	EndIf
	
	If VomitCHN <> 0 Then
		ResumeChannelWithSubtitles(VomitCHN)
	EndIf
	
	If IntercomStreamCHN <> 0
		SetStreamPaused_Strict(IntercomStreamCHN,False)
	EndIf
End Function

Function KillSounds()
	Local i%,n.NPCs,d.Doors,dem.DevilEmitters,snd.Sound
	
	For s.FireAndForgetSounds = Each FireAndForgetSounds
		StopChannel(s\Chn)
		Delete s
	Next
	For i=0 To 9
		If TempSounds[i]<>0 Then FreeSound_Strict TempSounds[i] : TempSounds[i]=0
	Next
	For n.NPCs = Each NPCs
		If n\SoundChn <> 0 Then
			If (Not n\SoundChn_IsStream)
				StopChannel(n\SoundChn)
			Else
				StopStream_Strict(n\SoundChn) : n\SoundChn_isStream = False
			EndIf
			n\SoundChn = 0
		EndIf
		If n\SoundChn2 <> 0 Then
			If (Not n\SoundChn2_IsStream)
				StopChannel(n\SoundChn2)
			Else
				StopStream_Strict(n\SoundChn2) : n\SoundChn2_isStream = False
			EndIf
			n\SoundChn2 = 0
		EndIf
	Next	
	For d.Doors = Each Doors
		If d\SoundCHN <> 0 Then
			StopChannel(d\SoundCHN) : d\SoundCHN = 0
		EndIf
	Next
	For dem.DevilEmitters = Each DevilEmitters
		If dem\SoundCHN <> 0 Then
			StopChannel(dem\SoundCHN) : dem\SoundCHN = 0
		EndIf
	Next
	If AmbientSFXCHN <> 0 Then
		StopChannel(AmbientSFXCHN) : AmbientSFXCHN = 0
	EndIf
	If BreathCHN <> 0 Then
		StopChannel(BreathCHN) : BreathCHN = 0
	EndIf
	If CoughCHN <> 0 Then
		StopChannel(CoughCHN) : CoughCHN = 0
	EndIf
	If VomitCHN <> 0 Then
		StopChannel(VomitCHN) : VomitCHN = 0
	EndIf
	For i = 0 To 6
		If RadioCHN(i) <> 0 Then
			StopChannel(RadioCHN(i)) : RadioCHN(i) = 0
		EndIf
	Next
	If IntercomStreamCHN <> 0
		StopStream_Strict(IntercomStreamCHN) : IntercomStreamCHN = 0
	EndIf
	If EnableSFXRelease
		For snd.Sound = Each Sound
			If snd\internalHandle <> 0 Then
				FreeSound snd\internalHandle
				snd\internalHandle = 0
				snd\releaseTime = 0
			EndIf
		Next
	EndIf
	
	For snd.Sound = Each Sound
		For i = 0 To 31
			If snd\channels[i]<>0 Then
				StopChannel snd\channels[i] : snd\channels[i] = 0
			EndIf
		Next
	Next

	ClearSubtitles()
	
	DebugLog "Terminated all sounds"
	
End Function

Function GetStepSound(entity%)
    Local picker%,brush%,texture%,name$
    Local mat.Materials
    
    picker = LinePick(EntityX(entity),EntityY(entity),EntityZ(entity),0,-1,0)
    If picker <> 0 Then
        If GetEntityType(picker) <> HIT_MAP Then Return 0
        brush = GetSurfaceBrush(GetSurface(picker,CountSurfaces(picker)))
        If brush <> 0 Then
            texture = GetBrushTexture(brush,3)
            If texture <> 0 Then
                name = StripPath(TextureName(texture))
                If (name <> "") Then FreeTexture(texture)
				For mat.Materials = Each Materials
					If mat\name = name Then
						If mat\StepSound > 0 Then
							FreeBrush(brush)
							Return mat\StepSound-1
						EndIf
						Exit
					EndIf
				Next                
			EndIf
			texture = GetBrushTexture(brush,2)
			If texture <> 0 Then
				name = StripPath(TextureName(texture))
				If (name <> "") Then FreeTexture(texture)
				For mat.Materials = Each Materials
					If mat\name = name Then
						If mat\StepSound > 0 Then
							FreeBrush(brush)
							Return mat\StepSound-1
						EndIf
						Exit
					EndIf
				Next                
			EndIf
			texture = GetBrushTexture(brush,1)
			If texture <> 0 Then
				name = StripPath(TextureName(texture))
				If (name <> "") Then FreeTexture(texture)
				FreeBrush(brush)
				For mat.Materials = Each Materials
					If mat\name = name Then
						If mat\StepSound > 0 Then
							Return mat\StepSound-1
						EndIf
						Exit
					EndIf
				Next                
			EndIf
		EndIf
	EndIf
    
    Return 0
End Function

;--------------------------------------- random -------------------------------------------------------

Function f2s$(n#, count%)
	Return Left(n, Len(Int(Str(n)))+count+1)
End Function

Function AnimateNPC(n.NPCs, start#, quit#, speed#, loop=True)
	Local newTime#
	
	If speed > 0.0 Then 
		newTime = Max(Min(n\Frame + speed * FPSfactor,quit),start)
		
		If loop And newTime => quit Then
			newTime = start
		EndIf
	Else
		If start < quit Then
			Local temp% = start
			start = quit
			quit = temp
		EndIf
		
		If loop Then
			newTime = n\Frame + speed * FPSfactor
			
			If newTime < quit Then 
				newTime = start
			Else If newTime > start 
				newTime = quit
			EndIf
		Else
			newTime = Max(Min(n\Frame + speed * FPSfactor,start),quit)
		EndIf
	EndIf
	SetNPCFrame(n, newTime)
	
End Function

Function SetNPCFrame(n.NPCs, frame#)
	If (Abs(n\Frame-frame)<0.001) Then Return
	
	SetAnimTime n\obj, frame
	
	n\Frame = frame
End Function

Function Animate2#(entity%, curr#, start%, quit%, speed#, loop=True)
	
	Local newTime#
	
	If speed > 0.0 Then 
		newTime = Max(Min(curr + speed * FPSfactor,quit),start)
		
		If loop Then
			If newTime => quit Then 
				;SetAnimTime entity, start
				newTime = start
			Else
				;SetAnimTime entity, newTime
			EndIf
		Else
			;SetAnimTime entity, newTime
		EndIf
	Else
		If start < quit Then
			Local temp% = start
			start = quit
			quit = temp
		EndIf
		
		If loop Then
			newTime = curr + speed * FPSfactor
			
			If newTime < quit Then newTime = start
			If newTime > start Then newTime = quit
			
			;SetAnimTime entity, newTime
		Else
			;SetAnimTime (entity, Max(Min(curr + speed * FPSfactor,start),quit))
			newTime = Max(Min(curr + speed * FPSfactor,start),quit)
		EndIf
	EndIf
	
	SetAnimTime entity, newTime
	Return newTime
	
End Function 



Global Keyboard294Layers%, Keyboard294X%, Keyboard294Y%, Keyboard294Width%, Keyboard294Height%, Keyboard294TileWidth#, Keyboard294TileHeight#, Keyboard294ResetLayerOnInput%
Global Keyboard294ActiveLayer%
Dim Keyboard294$(0,0,0)






;--------------------------------------- math -------------------------------------------------------

Function GenerateSeedNumber(seed$)
 	Local temp% = 0
 	Local shift% = 0
 	For i = 1 To Len(seed)
 		temp = temp Xor (Asc(Mid(seed,i,1)) Shl shift)
 		shift=(shift+1) Mod 24
	Next
 	Return temp
End Function

Function Distance#(x1#, y1#, x2#, y2#)
	Local x# = x2 - x1, y# = y2 - y1
	Return(Sqr(x*x + y*y))
End Function


Function CurveValue#(number#, old#, smooth#, factor#)

	If factor = 0 Then Return old
	
	If number < old Then
		Return Max(old + (number - old) * (1.0 / smooth * factor), number)
	Else
		Return Min(old + (number - old) * (1.0 / smooth * factor), number)
	EndIf
End Function

Function CurveValue#(number#, old#, smooth#)
	Return CurveValue(number, old, smooth, FPSfactor)
End Function

Function CurveAngle#(val#, old#, smooth#)
	If FPSfactor = 0 Then Return old
	
   Local diff# = WrapAngle(val) - WrapAngle(old)
   If diff > 180 Then diff = diff - 360
   If diff < - 180 Then diff = diff + 360
   Return WrapAngle(old + diff * (1.0 / smooth * FPSfactor))
End Function

Function SmoothMouseValue#(target#, current#, tau#)
    If tau <= 0 Then Return target
    Local dt# = FPSfactor / 70.0
    Local alpha# = 1.0 - Exp(-dt / tau)
    Return current + (target - current) * alpha
End Function

Function WrapAngle#(angle#)
	If angle = INFINITY Then Return 0.0
	While angle < 0
		angle = angle + 360
	Wend 
	While angle >= 360
		angle = angle - 360
	Wend
	Return angle
End Function

Function point_direction#(x1#,z1#,x2#,z2#)
	Local dx#, dz#
	dx = x1 - x2
	dz = z1 - z2
	Return ATan2(dz,dx)
End Function

Function point_distance#(x1#,z1#,x2#,z2#)
	Local dx#,dy#
	dx = x1 - x2
	dy = z1 - z2
	Return Sqr((dx*dx)+(dy*dy)) 
End Function

Function angleDist#(a0#,a1#)
	Local b# = a0-a1
	Local bb#
	If b<-180.0 Then
		bb = b+360.0
	Else If b>180.0 Then
		bb = b-360.0
	Else
		bb = b
	EndIf
	Return bb
End Function

Function Lerp#(a#, b#, f#)
    Return a * (1.0 - f) + (b * f)
End Function

Function Unlerp#(a#, b#, f#)
	Return (f - a) / (b - a)
End Function

Function PopCount%(value%)
	Local cnt% = 0
	While value <> 0
		If value And 1 Then cnt = cnt + 1
		value = value Shr 1
	Wend
	Return cnt
End Function

;--------------------------------------- decals -------------------------------------------------------

Type Decals
	Field obj%
	Field SizeChange#, Size#, MaxSize#
	Field AlphaChange#, Alpha#
	Field blendmode%
	Field fx%
	Field ID%
	Field Timer#
	
	Field lifetime#
	
	Field x#, y#, z#
	Field pitch#, yaw#, roll#
End Type

Function CreateDecal.Decals(id%, x#, y#, z#, pitch#, yaw#, roll#)
	Local d.Decals = New Decals
	
	d\x = x
	d\y = y
	d\z = z
	d\pitch = pitch
	d\yaw = yaw
	d\roll = roll
	
	d\MaxSize = 1.0
	
	d\Alpha = 1.0
	d\Size = 1.0
	d\obj = CreateSprite()
	d\blendmode = 1
	
	EntityTexture(d\obj, DecalTextures(id))
	EntityFX(d\obj, 0)
	SpriteViewMode(d\obj, 2)
	PositionEntity(d\obj, x, y, z)
	RotateEntity(d\obj, pitch, yaw, roll)
	
	d\ID = id
	
	If DecalTextures(id) = 0 Or d\obj = 0 Then Return Null
	
	Return d
End Function

Function UpdateDecals()
	Local d.Decals
	For d.Decals = Each Decals
		If d\SizeChange <> 0 Then
			d\Size=d\Size + d\SizeChange * FPSfactor
			ScaleSprite(d\obj, d\Size, d\Size)
			
			Select d\ID
				Case 0
					If d\Timer <= 0 Then
						Local angle# = Rand(360)
						Local temp# = Rnd(d\Size)
						Local d2.Decals = CreateDecal(1, EntityX(d\obj) + Cos(angle) * temp, EntityY(d\obj) - 0.0005, EntityZ(d\obj) + Sin(angle) * temp, EntityPitch(d\obj), Rnd(360), EntityRoll(d\obj))
						d2\Size = Rnd(0.1, 0.5) : ScaleSprite(d2\obj, d2\Size, d2\Size)
						PlaySound2(DecaySFX(Rand(1, 3)), Camera, d2\obj, 10.0, Rnd(0.1, 0.5))
						;d\Timer = d\Timer + Rand(50,150)
						d\Timer = Rand(50, 100)
					Else
						d\Timer= d\Timer-FPSfactor
					End If
				;Case 6
				;	EntityBlend d\obj, 2
			End Select
			
			If d\Size >= d\MaxSize Then d\SizeChange = 0 : d\Size = d\MaxSize
		End If
		
		If d\AlphaChange <> 0 Then
			d\Alpha = Min(d\Alpha + FPSfactor * d\AlphaChange, 1.0)
			EntityAlpha(d\obj, d\Alpha)
		End If
		
		If d\lifetime > 0 Then
			d\lifetime=Max(d\lifetime-FPSfactor,5)
		EndIf
		
		If d\Size <= 0 Or d\Alpha <= 0 Or d\lifetime=5.0  Then
			FreeEntity(d\obj)
			Delete d
		End If
	Next
End Function


;--------------------------------------- INI-functions -------------------------------------------------------

Type INIFile
	Field name$
	Field bank%
	Field bankOffset% = 0
	Field size%
End Type

Function ReadINILine$(file.INIFile)
	Local rdbyte%
	Local firstbyte% = True
	Local offset% = file\bankOffset
	Local bank% = file\bank
	Local retStr$ = ""
	rdbyte = PeekByte(bank,offset)
	While ((firstbyte) Or ((rdbyte<>13) And (rdbyte<>10))) And (offset<file\size)
		rdbyte = PeekByte(bank,offset)
		If ((rdbyte<>13) And (rdbyte<>10)) Then
			firstbyte = False
			retStr=retStr+Chr(rdbyte)
		EndIf
		offset=offset+1
	Wend
	file\bankOffset = offset
	Return retStr
End Function

Function UpdateINIFile$(filename$, file.INIFile)
	CatchErrors("Uncaught (UpdateINIFile) " + filename)

	If file\bank<>0 Then FreeBank file\bank
	Local f% = ReadFile(filename)
	Local fleSize% = 1
	While fleSize<FileSize(filename)
		fleSize=fleSize*2
	Wend
	file\bank = CreateBank(fleSize)
	file\size = 0
	While Not Eof(f)
		PokeByte(file\bank,file\size,ReadByte(f))
		file\size=file\size+1
	Wend
	CloseFile(f)

	CatchErrors("UpdateINIFile " + filename)
End Function

Function GetINIString$(file$, section$, parameter$, defaultvalue$="")
	Local TemporaryString$ = ""
	
	Local fileLower$ = Lower(file)
	Local lfile.INIFile = Null
	For k.INIFile = Each INIFile
		If k\name = fileLower Then
			lfile = k
			Exit
		EndIf
	Next
	
	If lfile = Null Then
		DebugLog "CREATE BANK FOR "+file
		lfile = New INIFile
		lfile\name = fileLower
		lfile\bank = 0
		UpdateINIFile(file, lfile)
	EndIf
	
	lfile\bankOffset = 0
	
	section = Lower(section)
	
	;While Not Eof(f)
	While lfile\bankOffset<lfile\size
		Local strtemp$ = ReadINILine(lfile)
		If Left(strtemp,1) = "[" Then
			strtemp$ = Lower(strtemp)
			If Mid(strtemp, 2, Len(strtemp)-2)=section Then
				Repeat
					TemporaryString = ReadINILine(lfile)
					If Lower(Trim(Left(TemporaryString, Max(Instr(TemporaryString, "=") - 1, 0)))) = Lower(parameter) Then
						;CloseFile f
						Return Trim( Right(TemporaryString,Len(TemporaryString)-Instr(TemporaryString,"=")) )
					EndIf
				Until (Left(TemporaryString, 1) = "[") Or (lfile\bankOffset>=lfile\size)
				
				;CloseFile f
				Return defaultvalue
			EndIf
		EndIf
	Wend
	
	Return defaultvalue
End Function

Function ClearLoadedINIFiles()
	For ini.INIFile = Each INIFile
		If ini\bank <> 0 Then FreeBank ini\bank
		Delete ini
	Next
End Function

Function ParseINIInt%(txt$)
	txt = Lower(Trim(txt))
	If txt = "true" Then
		Return 1
	ElseIf txt = "false"
		Return 0
	Else
		Return Int(txt)
	EndIf
End Function

Function GetINIInt%(file$, section$, parameter$, defaultvalue% = 0)
	Local txt$ = GetINIString(file$, section$, parameter$, defaultvalue)
	Return ParseINIInt(txt)
End Function

Function GetINIFloat#(file$, section$, parameter$, defaultvalue# = 0.0)
	Return Float(GetINIString(file$, section$, parameter$, defaultvalue))
End Function


Function GetINIString2$(file$, start%, parameter$, defaultvalue$="")
	Local TemporaryString$ = ""
	Local f% = ReadFile(file)
	
	Local n%=0
	While Not Eof(f)
		Local strtemp$ = ReadLine(f)
		n=n+1
		If n=start Then 
			Repeat
				TemporaryString = ReadLine(f)
				If Lower(Trim(Left(TemporaryString, Max(Instr(TemporaryString, "=") - 1, 0)))) = Lower(parameter) Then
					CloseFile f
					Return Trim( Right(TemporaryString,Len(TemporaryString)-Instr(TemporaryString,"=")) )
				EndIf
			Until Left(TemporaryString, 1) = "[" Or Eof(f)
			CloseFile f
			Return defaultvalue
		EndIf
	Wend
	
	CloseFile f	
	
	Return defaultvalue
End Function

Function GetINIInt2%(file$, start%, parameter$, defaultvalue$="")
	Local txt$ = GetINIString2(file$, start%, parameter$, defaultvalue$)
	If Lower(txt) = "true" Then
		Return 1
	ElseIf Lower(txt) = "false"
		Return 0
	Else
		Return Int(txt)
	EndIf
End Function

Function GetINIFloat2#(file$, start%, parameter$, defaultvalue# = 0.0)
	Return Float(GetINIString2(file$, start%, parameter$, defaultvalue#))
End Function


Function GetINISectionLocation%(file$, section$)
	Local Temp%
	Local f% = ReadFile(file)
	
	section = Lower(section)
	
	Local n%=0
	While Not Eof(f)
		Local strtemp$ = ReadLine(f)
		n=n+1
		If Left(strtemp,1) = "[" Then
			strtemp$ = Lower(strtemp)
			Temp = Instr(strtemp, section)
			While Temp>0
				If (Mid(strtemp, Temp-1, 1)="[" Or Mid(strtemp, Temp-1, 1)="|") And (Mid(strtemp, Temp+Len(section), 1)="]" Or Mid(strtemp, Temp+Len(section), 1)="|") Then
					CloseFile f
					Return n
				EndIf
				Temp = Instr(strtemp, section, Temp+Len(section)+1)
			Wend
		EndIf
	Wend
	
	CloseFile f
	Return -1
End Function

; Returns properly capitalized name
Function GetProperINISectionName$(file$, loc%, section$)
	section = Lower(section)
	
	Local f% = ReadFile(file)
	For i = 2 To loc
		ReadLine(f)
	Next

	Local strtemp$ = ReadLine(f)
	strtemp = Mid(strtemp, Instr(Lower(strtemp), section), Len(section))
	CloseFile f
	Return strtemp
End Function


Function PutINIValue%(file$, INI_sSection$, INI_sKey$, INI_sValue$)
	
	; Returns: True (Success) Or False (Failed)
	
	If INI_sSection <> "" Then INI_sSection = "[" + Trim$(INI_sSection) + "]"
	Local INI_sUpperSection$ = Upper$(INI_sSection)
	INI_sKey = Trim$(INI_sKey)
	INI_sValue = Trim$(INI_sValue)
	Local INI_sFilename$ = file$
	
	; Retrieve the INI Data (If it exists)
	
	Local INI_sContents$ = INI_FileToString(INI_sFilename)
	
		; (Re)Create the INI file updating/adding the SECTION, KEY And VALUE
	
	Local INI_bWrittenKey% = False
	Local INI_bSectionFound% = INI_sSection = ""
	Local INI_sCurrentSection$ = ""
	
	Local INI_lFileHandle% = WriteFile(INI_sFilename)
	If INI_lFileHandle = 0 Then Return False ; Create file failed!
	
	Local INI_lOldPos% = 1
	Local INI_lPos% = Instr(INI_sContents, Chr$(0))
	
	While (INI_lPos <> 0)
		
		Local INI_sTemp$ = Mid$(INI_sContents, INI_lOldPos, (INI_lPos - INI_lOldPos))
		
		If (INI_sTemp <> "") Then
			
			If Left$(INI_sTemp, 1) = "[" And Right$(INI_sTemp, 1) = "]" Then
				
					; Process SECTION
				
				If (INI_sCurrentSection = INI_sUpperSection) And (INI_bWrittenKey = False) Then
					INI_bWrittenKey = INI_CreateKey(INI_lFileHandle, INI_sKey, INI_sValue)
				End If
				INI_sCurrentSection = Upper$(INI_CreateSection(INI_lFileHandle, INI_sTemp))
				If (INI_sCurrentSection = INI_sUpperSection) Then INI_bSectionFound = True
				
			Else
				Local lEqualsPos% = Instr(INI_sTemp, "=")
				If (lEqualsPos <> 0) Then
					; KEY=VALUE
					If (INI_sCurrentSection = INI_sUpperSection) And (Upper$(Trim$(Left$(INI_sTemp, (lEqualsPos - 1)))) = Upper$(INI_sKey)) Then
						If (INI_sValue <> "") Then INI_CreateKey INI_lFileHandle, INI_sKey, INI_sValue
						INI_bWrittenKey = True
					Else
						WriteLine INI_lFileHandle, INI_sTemp
					End If
				Else 
					WriteLine INI_lFileHandle, INI_sTemp
				End If
				
			End If
			
		End If
		
			; Move through the INI file...
		
		INI_lOldPos = INI_lPos + 1
		INI_lPos% = Instr(INI_sContents, Chr$(0), INI_lOldPos)
		
	Wend
	
		; KEY wasn;t found in the INI file - Append a New SECTION If required And create our KEY=VALUE Line
	
	If (INI_bWrittenKey = False) Then
		If (INI_bSectionFound = False) Then INI_CreateSection INI_lFileHandle, INI_sSection
		INI_CreateKey INI_lFileHandle, INI_sKey, INI_sValue
	End If
	
	CloseFile INI_lFileHandle
	
	Return True ; Success
	
End Function

Function INI_FileToString$(INI_sFilename$)
	
	Local INI_sString$ = ""
	Local INI_lFileHandle%= ReadFile(INI_sFilename)
	If INI_lFileHandle <> 0 Then
		While Not(Eof(INI_lFileHandle))
			INI_sString = INI_sString + ReadLine$(INI_lFileHandle) + Chr$(0)
		Wend
		CloseFile INI_lFileHandle
	End If
	Return INI_sString
	
End Function

Function INI_CreateSection$(INI_lFileHandle%, INI_sNewSection$)
	
	If FilePos(INI_lFileHandle) <> 0 Then WriteLine INI_lFileHandle, "" ; Blank Line between sections
	WriteLine INI_lFileHandle, INI_sNewSection
	Return INI_sNewSection
	
End Function

Function INI_CreateKey%(INI_lFileHandle%, INI_sKey$, INI_sValue$)
	
	WriteLine INI_lFileHandle, INI_sKey + " = " + INI_sValue
	Return True
	
End Function

;Save options to .ini.
Function SaveOptionsINI()
	
	PutINIValue(OptionFile, "controls", "mouse sensitivity", MouseSens)
	PutINIValue(OptionFile, "controls", "invert mouse y", InvertMouse)
	PutINIValue(OptionFile, "graphics", "HUD enabled", HUDenabled)
	PutINIValue(OptionFile, "graphics", "screengamma", ScreenGamma)
	PutINIValue(OptionFile, "graphics", "antialias", Opt_AntiAlias)
	PutINIValue(OptionFile, "graphics", "vsync", Vsync)
	PutINIValue(OptionFile, "graphics", "show FPS", ShowFPS)
	PutINIValue(OptionFile, "graphics", "framelimit", Framelimit%)
	PutINIValue(OptionFile, "launcher", "launcher enabled", LauncherEnabled%)
	PutINIValue(OptionFile, "graphics", "texture details", TextureDetails%)
	PutINIValue(OptionFile, "console", "enabled", CanOpenConsole%)
	PutINIValue(OptionFile, "console", "auto opening", ConsoleOpening%)
	PutINIValue(OptionFile, "general", "speed run mode", SpeedRunMode%)
	PutINIValue(OptionFile, "general", "numeric seeds", UseNumericSeeds%)
	PutINIValue(OptionFile, "controls", "mouse smoothing", MouseSmooth)
	PutINIValue(OptionFile, "graphics", "hud scale factor", HUDScaleFactor)
	PutINIValue(OptionFile, "graphics", "hud offset", HUDOffsetScale)
	PutINIValue(OptionFile, "graphics", "view bob", ViewBobScale)
	PutINIValue(OptionFile, "graphics", "fov", FOV)
	
	PutINIValue(OptionFile, "audio", "music volume", MusicVolume)
	PutINIValue(OptionFile, "audio", "sound volume", PrevSFXVolume)
	PutINIValue(OptionFile, "audio", "sfx release", EnableSFXRelease)
	PutINIValue(OptionFile, "audio", "enable user tracks", EnableUserTracks%)
	PutINIValue(OptionFile, "audio", "user track setting", UserTrackMode%)
	PutINIValue(OptionFile, "audio", "dubbed audio", DubbedAudio)
	PutINIValue(OptionFile, "audio", "subtitles", SubtitlesEnabled)
	PutIniValue(OptionFile, "audio", "closed captions", ClosedCaptionsEnabled)
	
	PutINIValue(OptionFile, "binds", "Right key", KEY_RIGHT)
	PutINIValue(OptionFile, "binds", "Left key", KEY_LEFT)
	PutINIValue(OptionFile, "binds", "Up key", KEY_UP)
	PutINIValue(OptionFile, "binds", "Down key", KEY_DOWN)
	PutINIValue(OptionFile, "binds", "Blink key", KEY_BLINK)
	PutINIValue(OptionFile, "binds", "Sprint key", KEY_SPRINT)
	PutINIValue(OptionFile, "binds", "Inventory key", KEY_INV)
	PutINIValue(OptionFile, "binds", "Crouch key", KEY_CROUCH)
	PutINIValue(OptionFile, "binds", "Save key", KEY_SAVE)
	PutINIValue(OptionFile, "binds", "Console key", KEY_CONSOLE)
	
End Function

;--------------------------------------- MakeCollBox -functions -------------------------------------------------------


; Create a collision box For a mesh entity taking into account entity scale
; (will not work in non-uniform scaled space)
Function MakeCollBox(mesh%)
	Local sx# = EntityScaleX(mesh, 1)
	Local sy# = Max(EntityScaleY(mesh, 1), 0.001)
	Local sz# = EntityScaleZ(mesh, 1)
	GetMeshExtents(mesh)
	EntityBox mesh, Mesh_MinX * sx, Mesh_MinY * sy, Mesh_MinZ * sz, Mesh_MagX * sx, Mesh_MagY * sy, Mesh_MagZ * sz
End Function

; Find mesh extents
Function GetMeshExtents(Mesh%)
	Local s%, surf%, surfs%, v%, verts%, x#, y#, z#
	Local minx# = INFINITY
	Local miny# = INFINITY
	Local minz# = INFINITY
	Local maxx# = -INFINITY
	Local maxy# = -INFINITY
	Local maxz# = -INFINITY
	
	surfs = CountSurfaces(Mesh)
	
	For s = 1 To surfs
		surf = GetSurface(Mesh, s)
		verts = CountVertices(surf)
		
		For v = 0 To verts - 1
			x = VertexX(surf, v)
			y = VertexY(surf, v)
			z = VertexZ(surf, v)
			
			If (x < minx) Then minx = x
			If (x > maxx) Then maxx = x
			If (y < miny) Then miny = y
			If (y > maxy) Then maxy = y
			If (z < minz) Then minz = z
			If (z > maxz) Then maxz = z
		Next
	Next
	
	Mesh_MinX = minx
	Mesh_MinY = miny
	Mesh_MinZ = minz
	Mesh_MaxX = maxx
	Mesh_MaxY = maxy
	Mesh_MaxZ = maxz
	Mesh_MagX = maxx-minx
	Mesh_MagY = maxy-miny
	Mesh_MagZ = maxz-minz
	
End Function


Global SMALLEST_POWER_TWO#

Function Graphics3DExt%(width%,height%,depth%=32,mode%=2)
	Graphics3D width,height,depth,mode
	SMALLEST_POWER_TWO = 512.0
	While SMALLEST_POWER_TWO < Width Lor SMALLEST_POWER_TWO < Height
		SMALLEST_POWER_TWO = SMALLEST_POWER_TWO * 2.0
	Wend
	InitFastResize()
End Function

Function ApplyBorderlessResizing()
	If BorderlessWindowed And (RealGraphicWidth<>GraphicWidth Lor RealGraphicHeight<>GraphicHeight) Then
		CopyRectStretch(0, 0, GraphicWidth, GraphicHeight, 0, 0, ScaledGraphicWidth, ScaledGraphicHeight, BackBuffer(), TextureBuffer(ResizeTexture))
		; We need to move the picture into the center of the screen, need to clear the rest.
		If ScaledOffsetX<>0 Lor ScaledOffsetY<>0 Then Cls
		CopyRect(0, 0, ScaledGraphicWidth, ScaledGraphicHeight, ScaledOffsetX, ScaledOffsetY, TextureBuffer(ResizeTexture), BackBuffer())
	EndIf
End Function

Function RenderWorld2()
	CatchErrors("Uncaught (RenderWorld2)")

	CameraProjMode ark_blur_cam,0
	CameraProjMode Camera,1
	
	AmbientLight 0, 0, 0
	
	IsNVGBlinking% = False
	HideEntity NVBlink
	
	CameraViewport Camera,0,0,GraphicWidth,GraphicHeight
	
	Local hasBattery% = 2
	Local power% = 0
	If (WearingNightVision=1) Or (WearingNightVision=2)
		For i% = 0 To MaxItemAmount - 1
			If (Inventory(i)<>Null) Then
				If (WearingNightVision = 1 And Inventory(i)\itemtemplate\name = "nvgoggles") Or (WearingNightVision = 2 And Inventory(i)\itemtemplate\name = "supernv") Then
					Inventory(i)\state = Inventory(i)\state - (FPSfactor * (0.02 * WearingNightVision))
					power%=Int(Inventory(i)\state)
					If Inventory(i)\state<=0.0 Then ;this nvg can't be used
						hasBattery = 0
						Msg = I_Loc\MessageItem_NvgBatDead
						BlinkTimer = -1.0
						MsgTimer = 350
						Exit
					ElseIf Inventory(i)\state<=100.0 Then
						hasBattery = 1
					EndIf
				EndIf
			EndIf
		Next
		
		If (hasBattery) Then
			RenderWorld()
		EndIf
	Else
		RenderWorld()
	EndIf
	
	CurrTrisAmount = TrisRendered()

	UpdatePostProcess()

	If hasBattery=0 And WearingNightVision<>3
		IsNVGBlinking% = True
		ShowEntity NVBlink%
	EndIf
	
	If BlinkTimer < - 16 Or BlinkTimer > - 6
		If WearingNightVision=2 And hasBattery<>0 Then ;show a HUD
			NVTimer=NVTimer-FPSfactor
			
			If NVTimer<=0.0 Then
				For np.NPCs = Each NPCs
					np\NVX = EntityX(np\Collider,True)
					np\NVY = EntityY(np\Collider,True)
					np\NVZ = EntityZ(np\Collider,True)
				Next
				IsNVGBlinking% = True
				ShowEntity NVBlink%
				If NVTimer<=-10
					NVTimer = 600.0
				EndIf
			EndIf
			
			Color 255,255,255
			
			SetFont Font3
			
			Local plusY% = 0
			If hasBattery=1 Then plusY% = 40
			
			Text GraphicWidth/2,HUDStartY+(20+plusY)*HUDScale,I_Loc\HUD_NvgRefresh,True,False
			
			Text GraphicWidth/2,HUDStartY+(60+plusY)*HUDScale,Max(f2s(NVTimer/60.0,1),0.0),True,False
			Text GraphicWidth/2,HUDStartY+(100+plusY)*HUDScale,I_Loc\HUD_NvgRefreshSeconds,True,False
			
			Local temp% = CreatePivot(), temp2% = CreatePivot()
			PositionEntity temp, EntityX(Collider), EntityY(Collider), EntityZ(Collider)
			
			Color 255,255,255;*(NVTimer/600.0)
			
			For np.NPCs = Each NPCs
				If np\NVName<>"" And (Not np\HideFromNVG) Then ;don't waste your time if the string is empty
					PositionEntity temp2,np\NVX,np\NVY,np\NVZ
					Local dist# = EntityDistance(temp2,Collider)
					If dist<23.5 Then ;don't draw text if the NPC is too far away
						PointEntity temp, temp2
						Local yawvalue# = WrapAngle(EntityYaw(Camera) - EntityYaw(temp))
						Local xvalue# = 0.0
						If yawvalue > 90 And yawvalue <= 180 Then
							xvalue# = Sin(90)/90*yawvalue
						Else If yawvalue > 180 And yawvalue < 270 Then
							xvalue# = Sin(270)/yawvalue*270
						Else
							xvalue = Sin(yawvalue)
						EndIf
						Local pitchvalue# = WrapAngle(EntityPitch(Camera) - EntityPitch(temp))
						Local yvalue# = 0.0
						If pitchvalue > 90 And pitchvalue <= 180 Then
							yvalue# = Sin(90)/90*pitchvalue
						Else If pitchvalue > 180 And pitchvalue < 270 Then
							yvalue# = Sin(270)/pitchvalue*270
						Else
							yvalue# = Sin(pitchvalue)
						EndIf
						
						If (Not IsNVGBlinking%)
						Text GraphicWidth / 2 + xvalue * (GraphicWidth / 2),GraphicHeight / 2 - yvalue * (GraphicHeight / 2),np\NVName,True,True
						Text GraphicWidth / 2 + xvalue * (GraphicWidth / 2),GraphicHeight / 2 - yvalue * (GraphicHeight / 2) + 30.0 * HUDScale,Format(I_Loc\HUD_NvgMeters, f2s(dist,1)),True,True
					EndIf
				EndIf
				EndIf
			Next
			
			SetFont Font1
			
			FreeEntity (temp) : FreeEntity (temp2)
			
			Color 0,0,55
			For k=0 To 10
				Rect HUDStartX+45*HUDScale,GraphicHeight*0.5-(k*20)*HUDScale,54*HUDScale,10*HUDScale,True
			Next
			Color 0,0,255
			For l=0 To Floor((power%+50)*0.01)
				Rect HUDStartX+45*HUDScale,GraphicHeight*0.5-(l*20)*HUDScale,54*HUDScale,10*HUDScale,True
			Next
			DrawImage NVGImages[1],HUDStartX+40*HUDScale,GraphicHeight*0.5+30*HUDScale
			
			Color 255,255,255
		ElseIf WearingNightVision=1 And hasBattery<>0
			Color 0,55,0
			For k=0 To 10
				Rect HUDStartX+45*HUDScale,GraphicHeight*0.5-(k*20)*HUDScale,54*HUDScale,10*HUDScale,True
			Next
			Color 0,255,0
			For l=0 To Floor((power%+50)*0.01)
				Rect HUDStartX+45*HUDScale,GraphicHeight*0.5-(l*20)*HUDScale,54*HUDScale,10*HUDScale,True
			Next
			DrawImage NVGImages[0],HUDStartX+40*HUDScale,GraphicHeight*0.5+30*HUDScale
		EndIf
	EndIf
	
	;render sprites
	CameraProjMode ark_blur_cam,2
	CameraProjMode Camera,0
	RenderWorld()
	CameraProjMode ark_blur_cam,0
	
	If BlinkTimer < - 16 Or BlinkTimer > - 6
		If (WearingNightVision=1 Or WearingNightVision=2) And (hasBattery=1) And ((MilliSecs() Mod 800) < 400) Then
			Color 255,0,0
			SetFont Font3
			
			Text GraphicWidth/2,20*HUDScale,I_Loc\HUD_NvgBatlow,True,False
			Color 255,255,255
			SetFont Font1
		EndIf
	EndIf

	CatchErrors("RenderWorld2")
End Function


;--------------------------------------- Some new 1.3 -functions -------------------------------------------------------


Function CheckForPlayerInFacility()
	;False (=0): NPC is not in facility (mostly meant for "dimension1499")
	;True (=1): NPC is in facility
	;2: NPC is in tunnels (maintenance tunnels/049 tunnels/939 storage room, etc...)
	
	If EntityY(Collider)>100.0
		Return False
	EndIf
	If EntityY(Collider)< -10.0
		Return 2
	EndIf
	If EntityY(Collider)> 7.0 And EntityY(Collider)<=100.0
		Return 2
	EndIf
	
	Return True
End Function


Function ControlSoundVolume()
	Local snd.Sound,i
	
	For snd.Sound = Each Sound
		For i=0 To 31
			;If snd\channels[i]<>0 Then
			;	ChannelVolume snd\channels[i],SFXVolume#
			;Else
				ChannelVolume snd\channels[i],SFXVolume#
			;EndIf
		Next
	Next
	
End Function

Function UpdateDeafPlayer()
	
	If DeafTimer > 0
		DeafTimer = DeafTimer-FPSfactor
		SFXVolume# = 0.0
		If SFXVolume# > 0.0
			ControlSoundVolume()
		EndIf
		DebugLog DeafTimer
	Else
		DeafTimer = 0
		;If SFXVolume# < PrevSFXVolume#
		;	SFXVolume# = Min(SFXVolume# + (0.001*PrevSFXVolume)*FPSfactor,PrevSFXVolume#)
		;	ControlSoundVolume()
		;Else
			SFXVolume# = PrevSFXVolume#
			If DeafPlayer Then ControlSoundVolume()
			DeafPlayer = False
		;EndIf
	EndIf
	
End Function

Function CheckTriggers$(r.Rooms, x#, y#, z#)
	Local tb.Triggerboxes = r\FirstTriggerbox
	While tb <> Null
		If x>tb\MinX And x<tb\MaxX And y>tb\MinY And y<tb\MaxY And z>tb\MinZ And z<tb\MaxZ
			Return tb\Name
		EndIf

		tb = tb\Successor
	Wend

	Return ""	
End Function

Function ScaledMouseX%()
	Return Float(MouseX()-ScaledOffsetX)/ScaledGraphicWidth*GraphicWidth
End Function

Function ScaledMouseY%()
	Return Float(MouseY()-ScaledOffsetY)/ScaledGraphicHeight*GraphicHeight
End Function

Function PlayAnnouncement(file$) ;This function streams the announcement currently playing
	
	If IntercomStreamCHN <> 0 Then
		StopStream_Strict(IntercomStreamCHN) : IntercomStreamCHN = 0
	EndIf
	
	IntercomStreamCHN = StreamSound_Strict(file$,SFXVolume,0)
	
End Function

Function UpdateStreamSounds()
	If FPSfactor > 0 Then
		If IntercomStreamCHN <> 0 Then
			SetStreamVolume_Strict(IntercomStreamCHN,SFXVolume)
		EndIf
	EndIf

End Function

Function TeleportEntity(entity%,x#,y#,z#,customradius#=0.3,isglobal%=False,pickrange#=2.0,dir%=0)
	Local pvt,pick
	;dir = 0 - towards the floor (default)
	;dir = 1 - towrads the ceiling (mostly for PD decal after leaving dimension)
	
	pvt = CreatePivot()
	PositionEntity(pvt, x,y+0.05,z,isglobal)
	If dir%=0
		RotateEntity pvt,90,0,0
	Else
		RotateEntity pvt,-90,0,0
	EndIf
	pick = EntityPick(pvt,pickrange)
	If pick<>0
		If dir%=0
			PositionEntity(entity, x,PickedY()+customradius#+0.02,z,isglobal)
		Else
			PositionEntity(entity, x,PickedY()+customradius#-0.02,z,isglobal)
		EndIf
		DebugLog "Entity teleported successfully"
	Else
		PositionEntity(entity,x,y,z,isglobal)
		DebugLog "Warning: no ground found when teleporting an entity"
	EndIf
	FreeEntity pvt
	ResetEntity entity
	DebugLog "Teleported entity to: "+EntityX(entity)+"/"+EntityY(entity)+"/"+EntityZ(entity)
	
End Function


Function PlayStartupVideos()
	If GetOptionInt("general","play startup video") = 0 Lor IsRestart Or HasCLIFlag("novid") Then Return

	Local img% = LoadImage("GFX\bx3dface.png")
	Local snd% = LoadSound("SFX\bx3d.wav")
	If img = 0 Or snd = 0 Then
		If img <> 0 Then FreeImage img
		If snd <> 0 Then FreeSound snd
		Return
	EndIf

	Local chn% = PlaySound(snd)

	Cls
	DrawImage img, (GraphicWidth - ImageWidth(img)) / 2, (GraphicHeight - ImageHeight(img)) / 2
	Flip

	While ChannelPlaying(chn)
		Delay 50
	Wend

	FreeSound snd
	FreeImage img
	Cls
	Flip
End Function

Function PlayEpilepsyWarning()
	If IsRestart Or HasCLIFlag("novid") Then Return

	FlushKeys()
	FlushMouse()

	Cls
	Color 255,255,255
	SetFont Font2
	Text GraphicWidth/2, GraphicHeight*0.34, "EPILEPSY WARNING", True
	SetFont Font1
	Text GraphicWidth/2, GraphicHeight*0.46, "This game contains flashing imagery and rapid visual patterns.", True
	Text GraphicWidth/2, GraphicHeight*0.52, "Do not continue if you are sensitive to flashing lights.", True
	Text GraphicWidth/2, GraphicHeight*0.68, "Press any key or click to continue.", True
	Flip

	While GetKey() = 0 And MouseHit(1) = 0
		Delay 30
	Wend

	FlushKeys()
	FlushMouse()
	Cls
	Flip
End Function

Function CanUseItem(canUseWithHazmat%, canUseWithGasMask%, canUseWithEyewear%)
	If (canUseWithHazmat = False And WearingHazmat<>0) Then
		Msg = I_Loc\MessageItem_HazmatNouse
		MsgTimer = 70*5
		Return False
	ElseIf (canUseWithGasMask = False And (WearingGasMask<>0 Or Wearing1499<>0))
		Msg = I_Loc\MessageItem_GasmaskNouse
		MsgTimer = 70*5
		Return False
	ElseIf (canUseWithEyewear = False And (WearingNightVision<>0 Or WearingPumpkin<>0))
		Msg = I_Loc\MessageItem_NvgNouse
		MsgTimer = 70*5
		Return False
	EndIf
	
	Return True
End Function

Function TakeOffPumpkin()
	If WearingPumpkin Then NoTarget = False
	WearingPumpkin = False
End Function

Function ResetInput()
	
	FlushKeys()
	FlushMouse()
	MouseHit1 = 0
	MouseHit2 = 0
	MouseDown1 = 0
	MouseUp1 = 0
	MouseHit(1)
	MouseHit(2)
	MouseDown(1)
	GrabbedEntity = 0
	Input_ResetTime# = 10.0
	
End Function


;~IDEal Editor Parameters:
;~F#39#D8#DCD#162D#242C#2B2A
;~B#11E0#145E#1C07
;~C#Blitz3D
