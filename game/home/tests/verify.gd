extends SceneTree

const CropStage = preload("res://crop_stage.gd")

func _initialize() -> void:
	var crop := CropStage.new()
	for i in range(5):
		crop.sync_growth(float(i) / 4.0)
		assert(crop.growth_phase == i)
		assert(crop.is_harvestable() == (i == 4))
	var texture := crop.clip_texture(load("res://art/tiny-farm.png"), 7, 3)
	assert(texture.region == Rect2(112, 48, 16, 16))
	var scene := load("res://home.tscn") as PackedScene
	assert(scene != null)
	var world := scene.instantiate()
	var pond: Dictionary = world._pond_cells([[23.0, 12.0], [24.0, 12.0], [24.0, 13.0]])
	assert(pond.has(Vector2i(23, 12) + Vector2i.RIGHT))
	assert(pond.has(Vector2i(24, 12) + Vector2i.DOWN))
	assert(not pond.has(Vector2i(23, 12) + Vector2i.LEFT))
	# Packed tilesheets have 12 columns without gutters; every ID must resolve to its own cell.
	var atlas := load("res://art/tiny-farm.png") as Texture2D
	for index in range(132):
		var region: AtlasTexture = world._region(atlas, index)
		assert(region.region == Rect2((index % 12) * 16, (index / 12) * 16, 16, 16))
	root.add_child.call_deferred(world)
	create_timer(0.25).timeout.connect(func() -> void:
		print("HOME_GODOT_CHECKS_PASS: crop stages, atlas regions, scene loading, explicit fixture rendering")
		quit(0))
