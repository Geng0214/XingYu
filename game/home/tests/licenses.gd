extends SceneTree

func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	assert(args.size() == 1)
	var output := FileAccess.open(args[0], FileAccess.WRITE)
	assert(output != null)
	output.store_string("Godot Engine 4.7.2\nhttps://godotengine.org/license/\n\n" + Engine.get_license_text() + "\n\n")
	for component in Engine.get_copyright_info():
		output.store_string(component.name + "\n")
		for part in component.parts:
			output.store_string("Files: " + str(part.files) + "\nCopyright: " + str(part.copyright) + "\nLicense: " + str(part.license) + "\n")
		output.store_string("\n")
	var licenses := Engine.get_license_info()
	for name in licenses:
		output.store_string(name + "\n" + licenses[name] + "\n\n")
	output.close()
	quit(0)
