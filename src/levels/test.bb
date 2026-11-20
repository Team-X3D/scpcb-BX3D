Function BuildLevel_test%()
	LevelBegin("Test")

	Local r.Rooms = LevelRoom("test", "GFX\levels\test\room.fbx", 0, 0, 0, 0, 1.0, 1)
	LevelLight r, 0, 3, 0, 30
	LevelSpawn r, 0, 0.4, 0, 0

	LevelEnd()
	Return True
End Function
