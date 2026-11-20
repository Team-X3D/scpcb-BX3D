Include "src/levels/test.bb"

Function BuildLevel%(name$)
	Select Lower(name$)
		Case "test"
			Return BuildLevel_test()
	End Select
	Return False
End Function
