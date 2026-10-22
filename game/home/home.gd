extends Node2D

const CropStage = preload("res://crop_stage.gd")
const TOWN: Texture2D = preload("res://art/tiny-town.png")
const FARM: Texture2D = preload("res://art/tiny-farm.png")
const PEOPLE: Texture2D = preload("res://art/tiny-dungeon.png")
const TILE := 32.0
var bridge: Object
var state: Dictionary = {}
var last_packet := ""
var clock := 0.0
var atlas_regions: Dictionary = {}
var effects: Array[Dictionary] = []
var previous_version := -1
var previous_plots: Array = []
var previous_resources: Array = []

func _ready() -> void:
	Engine.max_fps = 30
	# The shipped scene requires its Android host. Desktop preview is an explicit authoring mode.
	var args := OS.get_cmdline_user_args()
	if args.size() == 2 and args[0] == "--preview":
		state = JSON.parse_string(FileAccess.get_file_as_string(args[1]))
	else:
		bridge = Engine.get_singleton("XingyuHome")
		assert(bridge != null, "XingyuHome Android host is required")
	texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	RenderingServer.set_default_clear_color(Color("658754"))

func _process(delta: float) -> void:
	var changed := false
	if not state.is_empty() and not state.reduceMotion:
		clock += delta
	if bridge != null:
		var packet: String = bridge.snapshot()
		if not packet.is_empty() and packet != last_packet:
			var json := JSON.new()
			var result := json.parse(packet)
			if result != OK:
				bridge.report_failure("家园场景数据无法读取")
				set_process(false)
				return
			state = json.data
			assert(state.protocol == 1)
			last_packet = packet
			changed = true
			_capture_effects()
	if not state.is_empty() and state.reduceMotion:
		clock = 0
		effects.clear()
	for i in range(effects.size() - 1, -1, -1):
		effects[i].age += delta
		if effects[i].age > 1.2:
			effects.remove_at(i)
	if changed or not state.is_empty() and not state.reduceMotion:
		queue_redraw()

func _capture_effects() -> void:
	if not state.reduceMotion and previous_version >= 0 and int(state.version) != previous_version:
		for i in range(state.plots.size()):
			var before = previous_plots[i]
			var after = state.plots[i]
			var changed := (before == null) != (after == null)
			if before != null and after != null:
				changed = before.crop != after.crop or before.watered != after.watered
			if changed:
				var tile: Array = state.geometry.plots[i]
				effects.append({"at": Vector2(float(tile[0]) + 1.0, float(tile[1]) + 1.0) * TILE, "age": 0.0, "water": state.plots[i] != null and state.plots[i].watered})
		for i in range(state.resources.size()):
			if previous_resources[i] and not state.resources[i]:
				var tile: Array = state.geometry.resources[i].tile
				effects.append({"at": Vector2(float(tile[0]) + 0.5, float(tile[1]) + 0.5) * TILE, "age": 0.0, "water": false})
	previous_version = int(state.version)
	previous_plots = state.plots.duplicate(true)
	previous_resources = state.resources.duplicate()

func _region(atlas: Texture2D, index: int) -> AtlasTexture:
	var key := str(atlas.get_instance_id()) + ":" + str(index)
	if not atlas_regions.has(key):
		atlas_regions[key] = CropStage.new().clip_texture(atlas, index % 12, index / 12)
	return atlas_regions[key]

func _tile(atlas: Texture2D, index: int, at: Vector2, size: Vector2 = Vector2(TILE, TILE), tint: Color = Color.WHITE) -> void:
	draw_texture_rect(_region(atlas, index), Rect2(at, size), false, tint)

func _cell(atlas: Texture2D, index: int, x: int, y: int) -> void:
	_tile(atlas, index, Vector2(x, y) * TILE)

func _point(pair: Array) -> Vector2:
	return Vector2(float(pair[0]), float(pair[1]))

func _draw() -> void:
	if state.is_empty():
		return
	var camera: Dictionary = state.camera
	var zoom := float(camera.scale)
	draw_set_transform(Vector2(-float(camera.left), -float(camera.top)) * zoom, 0.0, Vector2.ONE * zoom)
	_ground()
	_pond()
	_house_floor()
	for i in range(state.geometry.plots.size()):
		_plot(i)
	for tile in state.geometry.fences:
		_tile(TOWN, 81, _point(tile) * TILE)
	for tile in state.path:
		draw_circle((_point(tile) + Vector2(0.5, 0.5)) * TILE, 2.5, Color("ffe3a4"))
	var objects: Array[Dictionary] = []
	for tile in state.geometry.trees:
		objects.append({"kind": "TREE", "at": _point(tile), "ready": true})
	for i in range(state.geometry.resources.size()):
		var resource: Dictionary = state.geometry.resources[i]
		objects.append({"kind": resource.kind, "at": _point(resource.tile), "ready": state.resources[i]})
	for actor in state.actors:
		objects.append({"kind": "ACTOR", "at": Vector2(float(actor.x), float(actor.y)), "actor": actor})
	for place in ["WORKBENCH", "BENCH", "MAILBOX", "CAMPFIRE"]:
		objects.append({"kind": "PROP", "at": _point(state.geometry.places[place]), "place": place})
	if state.interior:
		for place in ["SOFA", "TABLE", "BED", "KITCHEN", "BOOKSHELF", "WARDROBE", "PLANT"]:
			objects.append({"kind": "PROP", "at": _point(state.geometry.places[place]), "place": place})
	objects.append({"kind": "HOUSE", "at": Vector2(14, 9)})
	objects.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return a.at.y < b.at.y)
	for object in objects:
		match object.kind:
			"TREE", "ROCK", "BUSH": _resource(object)
			"ACTOR": _actor(object.actor)
			"HOUSE": _house()
			"PROP": _prop(object.place, object.at * TILE)
	_world_symbols()
	if state.selected != null:
		var focus := (_point(state.selected) + Vector2(0.5, 0.5)) * TILE
		draw_arc(focus, 21.0 + sin(clock * 4.0), 0, TAU, 24, Color("fff2bb"), 2.0)
	for effect in effects:
		for i in range(7):
			var angle := float(i) / 7.0 * TAU
			var center: Vector2 = effect.at + Vector2(cos(angle), sin(angle)) * effect.age * 24.0 + Vector2(0, -effect.age * 25.0)
			draw_circle(center, 2.0, Color(0.6, 0.85, 1.0, 1.0 - effect.age / 1.2) if effect.water else Color(1.0, 0.87, 0.5, 1.0 - effect.age / 1.2))
	var hour := int(state.hour)
	if hour >= 19 or hour < 6:
		draw_rect(Rect2(0, 0, int(state.geometry.width) * TILE, int(state.geometry.height) * TILE), Color(0.08, 0.12, 0.28, 0.18))

func _ground() -> void:
	for y in range(int(state.geometry.height)):
		for x in range(int(state.geometry.width)):
			_cell(TOWN, 0, x, y)
			var seed := (x * 73 + y * 137) % 41
			if seed < 8:
				_cell(TOWN, 1, x, y)
			if seed == 10:
				_cell(TOWN, 2, x, y)
			if x == 0 or y < 2 or x == int(state.geometry.width) - 1 or y == int(state.geometry.height) - 1:
				draw_rect(Rect2(Vector2(x, y) * TILE, Vector2.ONE * TILE), Color(0.2, 0.35, 0.2, 0.18))
	var path_cells: Dictionary = {}
	for area in [Rect2i(13, 9, 3, 15), Rect2i(8, 10, 14, 2), Rect2i(14, 18, 8, 2), Rect2i(16, 13, 6, 2)]:
		for y in range(area.position.y, area.end.y):
			for x in range(area.position.x, area.end.x):
				path_cells[Vector2i(x, y)] = true
	for cell: Vector2i in path_cells:
		var column := 0 if not path_cells.has(cell + Vector2i.LEFT) else (2 if not path_cells.has(cell + Vector2i.RIGHT) else 1)
		var row := 1 if not path_cells.has(cell + Vector2i.UP) else (3 if not path_cells.has(cell + Vector2i.DOWN) else 2)
		_cell(TOWN, row * 12 + column, cell.x, cell.y)
	# Distinct flower borders frame the farm without becoming hidden collision objects.
	for at in [Vector2(4, 13), Vector2(20, 5), Vector2(17, 23), Vector2(9, 18)]:
		_tile(TOWN, 2, at * TILE)

func _pond() -> void:
	var tiles: Array = state.geometry.pond
	var cells := _pond_cells(tiles)
	for tile in tiles:
		var at := _point(tile) * TILE
		draw_rect(Rect2(at, Vector2.ONE * TILE), Color("7baeb2"))
		var phase := clock * 0.65 + float(tile[0]) * 0.6 + float(tile[1])
		draw_line(at + Vector2(4, 10 + sin(phase) * 2), at + Vector2(13, 10 + sin(phase) * 2), Color("a4d0cb"), 2)
		for direction in [Vector2i.LEFT, Vector2i.RIGHT, Vector2i.UP, Vector2i.DOWN]:
			if not cells.has(Vector2i(int(tile[0]), int(tile[1])) + direction):
				var offset := Vector2(0, 0)
				var size := Vector2(TILE, 4)
				if direction.y == 1: offset.y = TILE - 4
				if direction.x != 0:
					size = Vector2(4, TILE)
					if direction.x == 1: offset.x = TILE - 4
				draw_rect(Rect2(at + offset, size), Color("d0da9c"))
	# A pier is scenery on the shore, with the same pond target as the map geometry.
	for y in range(15, 18):
		var at := Vector2(21, y) * TILE
		draw_rect(Rect2(at, Vector2(TILE, TILE)), Color("a5754d"))
		for line in range(4):
			draw_rect(Rect2(at + Vector2(2, line * 8), Vector2(28, 5)), Color("d8ad70"))

func _pond_cells(tiles: Array) -> Dictionary:
	# JSON numbers become floats; normalize once so internal neighbours are never mistaken for shoreline.
	var cells: Dictionary = {}
	for tile in tiles:
		cells[Vector2i(int(tile[0]), int(tile[1]))] = true
	return cells

func _plot(index: int) -> void:
	var at := _point(state.geometry.plots[index]) * TILE
	var plot = state.plots[index]
	draw_rect(Rect2(at - Vector2(2, 2), Vector2(68, 68)), Color("c39763"))
	draw_rect(Rect2(at, Vector2(64, 64)), Color("815a3c"))
	for furrow in range(6):
		draw_rect(Rect2(at + Vector2(3, 4 + furrow * 10), Vector2(58, 4)), Color("654734"))
	if plot == null:
		return
	var crop := CropStage.new()
	crop.sync_growth(float(plot.progress))
	var columns := [4, 5, 6, 7, 8]
	var row := 0
	match plot.crop:
		"TOMATO":
			row = 3
			columns = [4, 4, 5, 6, 8]
		"COTTON":
			row = 4
			columns = [4, 5, 6, 6, 8]
	for y in range(2):
		for x in range(2):
			_tile(FARM, row * 12 + int(columns[crop.growth_phase]), at + Vector2(x, y) * TILE)
			if plot.crop == "COTTON" and crop.growth_phase >= 3:
				draw_circle(at + Vector2(x, y) * TILE + Vector2(10, 10), 4.0, Color("fff1dc"))
				draw_circle(at + Vector2(x, y) * TILE + Vector2(20, 13), 4.0, Color("fff1dc"))
	if plot.watered and not crop.is_harvestable():
		draw_circle(at + Vector2(55, 6), 3, Color("b5e2ed"))
	if crop.is_harvestable():
		_star(at + Vector2(53, -4 + sin(clock * 3.0 + index) * 2), Color("fff5be"))

func _resource(object: Dictionary) -> void:
	var at: Vector2 = object.at * TILE
	match object.kind:
		"TREE":
			if object.ready:
				_shadow(Rect2(at + Vector2(-10, 18), Vector2(52, 14)), Color(0.2, 0.35, 0.2, 0.22))
				draw_rect(Rect2(at + Vector2(9, -12), Vector2(14, 40)), Color("8f6044"))
				draw_rect(Rect2(at + Vector2(12, -10), Vector2(5, 36)), Color("c28b58"))
				if (int(object.at.x) + int(object.at.y)) % 2 == 0:
					_tile(TOWN, 5, at + Vector2(-24, -67), Vector2(80, 80))
				else:
					_tile(FARM, 15, at + Vector2(-16, -60), Vector2(64, 80))
			else:
				_p(at, "654734", 7, 18, 20, 12)
				_p(at, "ca9562", 7, 16, 20, 7)
		"ROCK": _tile(FARM, 89 if object.ready else 77, at)
		"BUSH": _tile(FARM, 78 if object.ready else 39, at)

func _house_floor() -> void:
	if not state.interior:
		return
	for y in range(3, 10):
		for x in range(10, 19):
			draw_rect(Rect2(Vector2(x, y) * TILE, Vector2.ONE * TILE), Color("d9ac79"))
			draw_line(Vector2(x, y + 1) * TILE, Vector2(x + 1, y + 1) * TILE, Color("c19365"), 1)

func _house() -> void:
	var outside: bool = not state.interior
	if outside:
		_shadow(Rect2(10 * TILE, 9 * TILE + 8, 9 * TILE, 24), Color(0.19, 0.27, 0.18, 0.28))
	for y in range(3, 10):
		for x in range(10, 19):
			if not outside and x > 10 and x < 18 and y > 3 and y < 9:
				continue
			if not outside and y == 9 and x == 14:
				continue
			elif y >= 7 or not outside:
				_cell(TOWN, 72 if x == 10 else (75 if x == 18 else 73), x, y)
			else:
				var column := 4 if x == 10 else (6 if x == 18 else 5)
				var row := 4 if y == 3 else 5
				_cell(TOWN, row * 12 + column, x, y)
	if outside:
		_cell(TOWN, 84, 11, 8)
		_cell(TOWN, 84, 17, 8)
		_tile(TOWN, 85, Vector2(14, 8) * TILE, Vector2(32, 64))
		_p(Vector2(16, 3) * TILE, "654734", 6, -10, 14, 36)
		_p(Vector2(16, 3) * TILE, "d5b17d", 4, -14, 18, 6)

# Authored pixel props share the native action pictures. Tools in the atlas are not furniture.
func _p(at: Vector2, hex: String, x: float, y: float, width: float, height: float) -> void:
	draw_rect(Rect2(at + Vector2(x, y), Vector2(width, height)), Color(hex))

func _prop(place: String, at: Vector2) -> void:
	var colors: Dictionary = state.furniture
	match place:
		"SOFA", "BENCH":
			_p(at, "654734", 5, 24, 5, 10)
			_p(at, "654734", 54, 24, 5, 10)
			_p(at, "654734", 2, 2, 60, 25)
			_p(at, colors.SOFA if place == "SOFA" else "c28b58", 4, 4, 56, 13)
			_p(at, "e4bf83", 4, 19, 56, 6)
			if place == "SOFA": _p(at, "f7eac7", 9, 7, 14, 10)
		"TABLE", "WORKBENCH":
			_p(at, "654734", 5, 16, 5, 18)
			_p(at, "654734", 54, 16, 5, 18)
			_p(at, "654734", 0, 4, 64, 15)
			_p(at, colors.TABLE if place == "TABLE" else "d8ad70", 0, 0, 64, 13)
			_p(at, "f3d79d", 5, 2, 54, 2)
			if place == "WORKBENCH":
				_tile(FARM, 86, at + Vector2(9, -9), Vector2(20, 20))
				_tile(FARM, 84, at + Vector2(32, -7), Vector2(20, 20))
		"BED":
			_p(at, "654734", 0, 0, 64, 64)
			_p(at, "c28b58", 3, 3, 58, 58)
			_p(at, "f7eac7", 7, 7, 50, 17)
			_p(at, colors.BED, 5, 27, 54, 31)
			_p(at, "f7eac7", 5, 28, 54, 4)
		"KITCHEN":
			_p(at, "654734", 0, 0, 64, 32)
			_p(at, "d8ad70", 2, 4, 60, 26)
			_p(at, "bbc3ab", 0, -2, 64, 8)
			_p(at, "3e493c", 7, -2, 22, 6)
			_p(at, "3e493c", 31, 7, 2, 23)
			_tile(FARM, 124, at + Vector2(4, -17), Vector2(24, 24))
		"BOOKSHELF":
			_p(at, "654734", 0, -8, 64, 40)
			_p(at, "d8ad70", 3, -5, 58, 34)
			for shelf in range(2):
				for book in range(7):
					_p(at, ["ca7960", "7b9fa1", "749352"][book % 3], 6 + book * 7, -3 + shelf * 17, 5, 13)
				_p(at, "654734", 3, 11 + shelf * 17, 58, 3)
		"WARDROBE":
			_p(at, "654734", 0, -12, 32, 44)
			_p(at, "d8ad70", 3, -9, 26, 38)
			_p(at, "654734", 15, -9, 2, 38)
			_p(at, "f7eac7", 10, 8, 3, 4)
			_p(at, "f7eac7", 20, 8, 3, 4)
		"PLANT":
			_p(at, "654734", 5, 18, 23, 14)
			_p(at, "c28b58", 7, 19, 19, 11)
			_tile(FARM, 83, at + Vector2(2, -7), Vector2(28, 28), Color(colors.PLANT))
		"MAILBOX":
			_p(at, "654734", 13, 10, 6, 23)
			_p(at, "3e493c", 1, -5, 30, 23)
			_p(at, "7b9fa1", 3, -3, 26, 19)
			_p(at, "f7eac7", 5, 1, 22, 11)
			_p(at, "ca7960", 25, -11, 4, 16)
		"CAMPFIRE":
			for i in range(6):
				var angle := float(i) / 6 * TAU
				_tile(FARM, 77, at + Vector2(8, 8) + Vector2(cos(angle), sin(angle)) * 13, Vector2(16, 16))
			_p(at, "654734", 8, 16, 20, 9)
			_p(at, "d48646", 11, -3 + sin(clock * 7) * 2, 14, 24)
			_p(at, "ffc66a", 14, -8 + sin(clock * 7) * 2, 8, 24)
			_p(at, "fff1c2", 17, 2, 4, 14)

func _actor(actor: Dictionary) -> void:
	var foot := Vector2(float(actor.x) + 0.5, float(actor.y) + 1.0) * TILE
	var walking: bool = actor.walking
	var phase := sin(clock * 13.0) if walking else sin(clock * 2.0)
	var bounce := absf(phase) * 2.0 if walking else phase * 0.5
	_shadow(Rect2(foot + Vector2(-12, -4), Vector2(24, 8)), Color(0.18, 0.28, 0.18, 0.25))
	var atlas: Texture2D = FARM if actor.user else PEOPLE
	var size := Vector2(40, 40)
	var rect := Rect2(foot + Vector2(-20, -40 - bounce), size)
	if int(actor.facing) < 0:
		# Godot flips a negative width around the same destination origin; moving it displaced the hit target.
		rect.size.x = -size.x
	draw_texture_rect(_region(atlas, int(actor.sprite)), rect, false)
	if actor.outfit != null:
		match actor.outfit:
			"SUN_HAT":
				draw_rect(Rect2(foot + Vector2(-17, -40 - bounce), Vector2(34, 5)), Color("e8c47a"))
				draw_rect(Rect2(foot + Vector2(-9, -47 - bounce), Vector2(18, 9)), Color("e8c47a"))
			"APRON": draw_rect(Rect2(foot + Vector2(-7, -16 - bounce), Vector2(14, 11)), Color("f4e8ca"))
			"GARDEN_SUIT": draw_rect(Rect2(foot + Vector2(-9, -17 - bounce), Vector2(18, 12)), Color("6d9875"))
	if actor.activity != null and not walking:
		var tile := 85
		match actor.activity:
			"READ":
				_p(foot, "ca7960", 12, -22, 16, 13)
				_p(foot, "f7eac7", 14, -20, 12, 9)
				_star(foot + Vector2(0, -49 + sin(clock * 2.0)), Color("fff1c2"))
				return
			"TEA", "MEAL": tile = 124
			"CLEAN": tile = 84
			"REST": tile = 112
			"WALK": tile = 109
		_tile(FARM, tile, foot + Vector2(10, -20), Vector2(20, 20))
		_star(foot + Vector2(0, -49 + sin(clock * 2.0)), Color("fff1c2"))

func _world_symbols() -> void:
	# A tiny marker makes workstations discoverable without a permanent paragraph or label board.
	for place in ["WORKBENCH", "MAILBOX", "POND"]:
		var at := (_point(state.geometry.places[place]) + Vector2(0.5, 0.0)) * TILE
		_star(at + Vector2(0, -6 + sin(clock * 2.0) * 2), Color("fff0ac"))
	for i in range(3):
		var at := Vector2(7 + i * 5, 17 + i) * TILE + Vector2(sin(clock * 0.7 + i) * 16, sin(clock + i) * 6)
		var wing := 3.0 + absf(sin(clock * 7.0)) * 3.0
		draw_rect(Rect2(at + Vector2(-wing, -1), Vector2(wing * 2, 3)), Color("ffe2a1"))
		draw_line(at + Vector2(0, -2), at + Vector2(0, 3), Color("907358"), 1)

func _star(at: Vector2, color: Color) -> void:
	draw_line(at - Vector2(0, 5), at + Vector2(0, 5), color, 2)
	draw_line(at - Vector2(5, 0), at + Vector2(5, 0), color, 2)

func _shadow(rect: Rect2, color: Color) -> void:
	var points := PackedVector2Array()
	for i in range(20):
		var angle := float(i) / 20.0 * TAU
		points.append(rect.get_center() + Vector2(cos(angle), sin(angle)) * rect.size * 0.5)
	draw_colored_polygon(points, color)
