extends Node3D

const GRID := 9
const CELL := 0.70
const BOARD_SIZE := GRID * CELL
const SHAPES := [
	[Vector2i(0, 0)],
	[Vector2i(0, 0), Vector2i(1, 0)],
	[Vector2i(0, 0), Vector2i(0, 1)],
	[Vector2i(0, 0), Vector2i(1, 0), Vector2i(0, 1)],
	[Vector2i(0, 0), Vector2i(1, 0), Vector2i(2, 0)],
	[Vector2i(0, 0), Vector2i(0, 1), Vector2i(0, 2)],
	[Vector2i(0, 0), Vector2i(1, 0), Vector2i(2, 0), Vector2i(1, 1)],
	[Vector2i(0, 0), Vector2i(1, 0), Vector2i(0, 1), Vector2i(1, 1)],
	[Vector2i(0, 0), Vector2i(1, 0), Vector2i(2, 0), Vector2i(3, 0)],
	[Vector2i(0, 0), Vector2i(0, 1), Vector2i(0, 2), Vector2i(1, 2)],
	[Vector2i(1, 0), Vector2i(0, 1), Vector2i(1, 1), Vector2i(2, 1)],
	[Vector2i(0, 0), Vector2i(1, 0), Vector2i(1, 1), Vector2i(2, 1)]
]
const BLOCK_COLORS := [
	Color("#ff6b6b"), Color("#ffb84d"), Color("#f7df59"),
	Color("#67d98b"), Color("#45c7d9"), Color("#638bff"),
	Color("#a77bfa"), Color("#f47bc4")
]

var board: Array = []
var block_nodes: Array = []
var tray: Array = []
var tray_buttons: Array[Button] = []
var selected_slot := -1
var score := 0
var camera: Camera3D
var score_label: Label
var message_label: Label
var root_ui: Control

func _ready() -> void:
	randomize()
	_init_board()
	_create_world()
	_create_board()
	_create_ui()
	_refill_tray()
	_update_hud()

func _init_board() -> void:
	for r in range(GRID):
		var row: Array = []
		var node_row: Array = []
		for c in range(GRID):
			row.append(false)
			node_row.append(null)
		board.append(row)
		block_nodes.append(node_row)

func _mat(color: Color, roughness := 0.72, metallic := 0.0) -> StandardMaterial3D:
	var material := StandardMaterial3D.new()
	material.albedo_color = color
	material.roughness = roughness
	material.metallic = metallic
	return material

func _box(parent: Node3D, size: Vector3, pos: Vector3, material: Material, bevel := false) -> MeshInstance3D:
	var mesh_instance := MeshInstance3D.new()
	var mesh := BoxMesh.new()
	mesh.size = size
	mesh_instance.mesh = mesh
	mesh_instance.material_override = material
	mesh_instance.position = pos
	parent.add_child(mesh_instance)
	return mesh_instance

func _create_world() -> void:
	var world := WorldEnvironment.new()
	var environment := Environment.new()
	environment.background_mode = Environment.BG_COLOR
	environment.background_color = Color("#8bd7ed")
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_color = Color("#fff4d8")
	environment.ambient_light_energy = 0.72
	environment.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	world.environment = environment
	add_child(world)

	camera = Camera3D.new()
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 15.5
	camera.near = 0.05
	camera.far = 100.0
	add_child(camera)
	camera.position = Vector3(0.0, 17.0, 0.01)
	camera.look_at_from_position(Vector3(0.0, 17.0, 0.01), Vector3(0.0, 0.0, 0.0), Vector3(0.0, 0.0, -1.0))
	camera.current = true

	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-48, -32, 0)
	sun.light_energy = 1.35
	sun.shadow_enabled = true
	add_child(sun)

	var water := MeshInstance3D.new()
	var water_mesh := CylinderMesh.new()
	water_mesh.top_radius = 7.3
	water_mesh.bottom_radius = 7.3
	water_mesh.height = 0.38
	water.mesh = water_mesh
	water.material_override = _mat(Color("#39b8c8"), 0.24, 0.12)
	water.position.y = -0.82
	add_child(water)

	var island := MeshInstance3D.new()
	var island_mesh := CylinderMesh.new()
	island_mesh.top_radius = 6.4
	island_mesh.bottom_radius = 6.15
	island_mesh.height = 0.72
	island.mesh = island_mesh
	island.material_override = _mat(Color("#8cce78"))
	island.position.y = -0.30
	add_child(island)

	var grass := MeshInstance3D.new()
	var grass_mesh := CylinderMesh.new()
	grass_mesh.top_radius = 6.25
	grass_mesh.bottom_radius = 6.25
	grass_mesh.height = 0.12
	grass.mesh = grass_mesh
	grass.material_override = _mat(Color("#a8df80"))
	grass.position.y = 0.08
	add_child(grass)

	_add_tree(Vector3(-4.65, 0.10, -3.8), 1.0)
	_add_tree(Vector3(4.55, 0.10, -3.7), 0.88)
	_add_tree(Vector3(-4.8, 0.10, 3.6), 0.78)
	_add_tree(Vector3(4.7, 0.10, 3.6), 0.96)
	_add_tree(Vector3(-5.0, 0.10, 0.1), 0.72)
	_add_tree(Vector3(5.0, 0.10, 0.2), 0.72)
	_add_flower(Vector3(-3.95, 0.18, -4.6), Color("#ff80ae"))
	_add_flower(Vector3(3.95, 0.18, -4.6), Color("#ffe16b"))
	_add_flower(Vector3(-4.1, 0.18, 4.4), Color("#a98cff"))
	_add_flower(Vector3(4.05, 0.18, 4.45), Color("#ff8b6b"))
	_add_rock(Vector3(-3.8, 0.20, 3.4))
	_add_rock(Vector3(3.8, 0.20, -3.3))

func _add_tree(pos: Vector3, scale_factor: float) -> void:
	var tree := Node3D.new()
	tree.position = pos
	tree.scale = Vector3.ONE * scale_factor
	add_child(tree)
	var trunk := MeshInstance3D.new()
	var trunk_mesh := CylinderMesh.new()
	trunk_mesh.top_radius = 0.16
	trunk_mesh.bottom_radius = 0.25
	trunk_mesh.height = 1.35
	trunk.mesh = trunk_mesh
	trunk.material_override = _mat(Color("#8b5737"))
	trunk.position.y = 0.70
	tree.add_child(trunk)
	for i in range(3):
		var crown := MeshInstance3D.new()
		var sphere := SphereMesh.new()
		sphere.radius = 0.72 - i * 0.06
		sphere.height = 1.30 - i * 0.08
		crown.mesh = sphere
		crown.material_override = _mat([Color("#48a85d"), Color("#59bd68"), Color("#76cf70")][i])
		crown.position = Vector3((i - 1) * 0.25, 1.45 + i * 0.38, 0)
		tree.add_child(crown)
	var fruit := MeshInstance3D.new()
	var fruit_mesh := SphereMesh.new()
	fruit_mesh.radius = 0.12
	fruit_mesh.height = 0.24
	fruit.mesh = fruit_mesh
	fruit.material_override = _mat(Color("#ffd45c"), 0.32)
	fruit.position = Vector3(0.48, 1.78, -0.48)
	tree.add_child(fruit)

func _add_flower(pos: Vector3, petal_color: Color) -> void:
	var flower := Node3D.new()
	flower.position = pos
	add_child(flower)
	var stem := MeshInstance3D.new()
	var stem_mesh := CylinderMesh.new()
	stem_mesh.top_radius = 0.035
	stem_mesh.bottom_radius = 0.045
	stem_mesh.height = 0.58
	stem.mesh = stem_mesh
	stem.material_override = _mat(Color("#3c9c55"))
	stem.position.y = 0.29
	flower.add_child(stem)
	for i in range(5):
		var petal := MeshInstance3D.new()
		var petal_mesh := SphereMesh.new()
		petal_mesh.radius = 0.16
		petal_mesh.height = 0.12
		petal.mesh = petal_mesh
		petal.material_override = _mat(petal_color, 0.38)
		var angle := TAU * float(i) / 5.0
		petal.position = Vector3(cos(angle) * 0.17, 0.62, sin(angle) * 0.17)
		flower.add_child(petal)
	var center := MeshInstance3D.new()
	var center_mesh := SphereMesh.new()
	center_mesh.radius = 0.11
	center.mesh = center_mesh
	center.material_override = _mat(Color("#ffd75e"), 0.35)
	center.position.y = 0.64
	flower.add_child(center)

func _add_rock(pos: Vector3) -> void:
	var rock := MeshInstance3D.new()
	var mesh := SphereMesh.new()
	mesh.radius = 0.42
	mesh.height = 0.58
	rock.mesh = mesh
	rock.material_override = _mat(Color("#a5b5b2"))
	rock.position = pos
	rock.scale = Vector3(1.25, 0.68, 0.85)
	add_child(rock)

func _create_board() -> void:
	var board_base := MeshInstance3D.new()
	var base_mesh := BoxMesh.new()
	base_mesh.size = Vector3(BOARD_SIZE + 0.35, 0.26, BOARD_SIZE + 0.35)
	board_base.mesh = base_mesh
	board_base.material_override = _mat(Color("#4f9d66"))
	board_base.position = Vector3(0, 0.22, 0)
	add_child(board_base)
	for r in range(GRID):
		for c in range(GRID):
			var cell := MeshInstance3D.new()
			var mesh := BoxMesh.new()
			mesh.size = Vector3(CELL * 0.91, 0.10, CELL * 0.91)
			cell.mesh = mesh
			cell.material_override = _mat(Color("#d9efbd") if (r + c) % 2 == 0 else Color("#c9e5a9"))
			cell.position = _cell_position(r, c, 0.40)
			add_child(cell)

func _cell_position(row: int, col: int, y: float) -> Vector3:
	return Vector3((col - 4) * CELL, y, (row - 4) * CELL)

func _create_block(row: int, col: int, color_index: int) -> MeshInstance3D:
	var block := MeshInstance3D.new()
	var mesh := BoxMesh.new()
	mesh.size = Vector3(CELL * 0.88, 0.42, CELL * 0.88)
	block.mesh = mesh
	block.material_override = _mat(BLOCK_COLORS[color_index % BLOCK_COLORS.size()], 0.30, 0.04)
	block.position = _cell_position(row, col, 0.68)
	add_child(block)
	var shine := MeshInstance3D.new()
	var shine_mesh := BoxMesh.new()
	shine_mesh.size = Vector3(CELL * 0.54, 0.025, CELL * 0.12)
	shine.mesh = shine_mesh
	shine.material_override = _mat(Color(1, 1, 1, 0.42), 0.22)
	shine.position = Vector3(0, 0.215, -CELL * 0.29)
	block.add_child(shine)
	return block

func _create_ui() -> void:
	var layer := CanvasLayer.new()
	add_child(layer)
	root_ui = Control.new()
	root_ui.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	layer.add_child(root_ui)

	var top := PanelContainer.new()
	top.anchor_left = 0.045
	top.anchor_right = 0.955
	top.anchor_top = 0.025
	top.anchor_bottom = 0.135
	top.add_theme_stylebox_override("panel", _panel_style(Color("#235c4c"), Color("#b7e68a")))
	root_ui.add_child(top)
	var top_row := HBoxContainer.new()
	top_row.add_theme_constant_override("separation", 18)
	top.add_child(top_row)
	var title := Label.new()
	title.text = "JARDIM EM BLOCOS"
	title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	title.add_theme_font_size_override("font_size", 25)
	title.add_theme_color_override("font_color", Color("#fff5d9"))
	title.add_theme_color_override("font_shadow_color", Color("#163d35"))
	title.add_theme_constant_override("shadow_offset_x", 2)
	title.add_theme_constant_override("shadow_offset_y", 2)
	top_row.add_child(title)
	score_label = Label.new()
	score_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	score_label.add_theme_font_size_override("font_size", 23)
	score_label.add_theme_color_override("font_color", Color("#ffe18a"))
	top_row.add_child(score_label)

	var bottom := PanelContainer.new()
	bottom.anchor_left = 0.045
	bottom.anchor_right = 0.955
	bottom.anchor_top = 0.835
	bottom.anchor_bottom = 0.975
	bottom.add_theme_stylebox_override("panel", _panel_style(Color("#235c4c"), Color("#b7e68a")))
	root_ui.add_child(bottom)
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", 8)
	bottom.add_child(column)
	message_label = Label.new()
	message_label.text = "Escolha uma peça e toque no tabuleiro"
	message_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	message_label.add_theme_font_size_override("font_size", 17)
	message_label.add_theme_color_override("font_color", Color("#fff5d9"))
	column.add_child(message_label)
	var tray_row := HBoxContainer.new()
	tray_row.add_theme_constant_override("separation", 10)
	column.add_child(tray_row)
	for i in range(3):
		var button := Button.new()
		button.custom_minimum_size = Vector2(0, 82)
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		button.add_theme_font_size_override("font_size", 22)
		button.add_theme_color_override("font_color", Color("#ffffff"))
		button.pressed.connect(_select_piece.bind(i))
		tray_row.add_child(button)
		tray_buttons.append(button)

func _panel_style(bg: Color, border: Color) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = bg
	style.border_color = border
	style.set_border_width_all(3)
	style.set_corner_radius_all(22)
	style.content_margin_left = 16
	style.content_margin_right = 16
	style.content_margin_top = 10
	style.content_margin_bottom = 10
	style.shadow_color = Color(0, 0, 0, 0.22)
	style.shadow_size = 8
	return style

func _refill_tray() -> void:
	tray.clear()
	for i in range(3):
		tray.append(SHAPES[randi_range(0, SHAPES.size() - 1)].duplicate())
	selected_slot = -1
	_refresh_tray()

func _refresh_tray() -> void:
	for i in range(3):
		if i >= tray.size() or tray[i].is_empty():
			tray_buttons[i].text = "✓"
			tray_buttons[i].disabled = true
			continue
		tray_buttons[i].disabled = false
		var shape: Array = tray[i]
		var lines := ""
		var max_x := 0
		var max_y := 0
		for p in shape:
			max_x = maxi(max_x, p.x)
			max_y = maxi(max_y, p.y)
		for y in range(max_y + 1):
			for x in range(max_x + 1):
				var occupied := false
				for p in shape:
					if p == Vector2i(x, y):
						occupied = true
				lines += "■ " if occupied else "  "
			if y < max_y:
				lines += "\n"
		tray_buttons[i].text = lines
		var style := _panel_style(Color("#367d5b") if i != selected_slot else Color("#b77b2f"), Color("#a8e18b") if i != selected_slot else Color("#ffe18a"))
		tray_buttons[i].add_theme_stylebox_override("normal", style)
		tray_buttons[i].add_theme_stylebox_override("hover", style)
		tray_buttons[i].add_theme_stylebox_override("pressed", style)

func _select_piece(index: int) -> void:
	if index >= tray.size() or tray[index].is_empty():
		return
	selected_slot = index
	message_label.text = "Agora toque em uma posição livre no tabuleiro"
	_refresh_tray()

func _input(event: InputEvent) -> void:
	var point := Vector2.ZERO
	var pressed := false
	if event is InputEventScreenTouch and event.pressed:
		point = event.position
		pressed = true
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		point = event.position
		pressed = true
	if pressed and selected_slot >= 0:
		_try_place_at(point)

func _try_place_at(screen_point: Vector2) -> void:
	var ray_origin := camera.project_ray_origin(screen_point)
	var ray_dir := camera.project_ray_normal(screen_point)
	if absf(ray_dir.y) < 0.0001:
		return
	var distance := (0.45 - ray_origin.y) / ray_dir.y
	if distance < 0:
		return
	var hit := ray_origin + ray_dir * distance
	var col := int(floor((hit.x + BOARD_SIZE / 2.0) / CELL))
	var row := int(floor((hit.z + BOARD_SIZE / 2.0) / CELL))
	if row < 0 or row >= GRID or col < 0 or col >= GRID:
		return
	_place_piece(row, col)

func _place_piece(row: int, col: int) -> void:
	var shape: Array = tray[selected_slot]
	for p in shape:
		var r: int = row + p.y
		var c: int = col + p.x
		if r < 0 or r >= GRID or c < 0 or c >= GRID or board[r][c]:
			message_label.text = "Não cabe aqui! Tente outra posição."
			return
	var color_index := randi_range(0, BLOCK_COLORS.size() - 1)
	for p in shape:
		var r: int = row + p.y
		var c: int = col + p.x
		board[r][c] = true
		block_nodes[r][c] = _create_block(r, c, color_index)
	score += shape.size()
	tray[selected_slot] = []
	selected_slot = -1
	_clear_completed_lines()
	if _tray_empty():
		_refill_tray()
	else:
		_refresh_tray()
	message_label.text = "Boa! Continue combinando as peças."
	_update_hud()

func _tray_empty() -> bool:
	for shape in tray:
		if not shape.is_empty():
			return false
	return true

func _clear_completed_lines() -> void:
	var cleared: Dictionary = {}
	for r in range(GRID):
		var full := true
		for c in range(GRID):
			if not board[r][c]:
				full = false
				break
		if full:
			for c in range(GRID):
				cleared[Vector2i(r, c)] = true
	for c in range(GRID):
		var full := true
		for r in range(GRID):
			if not board[r][c]:
				full = false
				break
		if full:
			for r in range(GRID):
				cleared[Vector2i(r, c)] = true
	if cleared.is_empty():
		return
	for cell in cleared.keys():
		var r: int = cell.x
		var c: int = cell.y
		board[r][c] = false
		if is_instance_valid(block_nodes[r][c]):
			block_nodes[r][c].queue_free()
		block_nodes[r][c] = null
	score += cleared.size() * 4
	message_label.text = "LINHAS ELIMINADAS! +%d" % (cleared.size() * 4)

func _update_hud() -> void:
	if is_instance_valid(score_label):
		score_label.text = "PONTOS  %d" % score
