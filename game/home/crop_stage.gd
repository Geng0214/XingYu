# Adapted from Tall Tales Games' MIT Crop resource (2025).
# Android owns the harvest clock and inventory; this resource only selects its visual phase.
class_name HomeCropStage
extends Resource

enum GrowthPhase { GERMINATING, BUDDING, FLOWERING, MATURING, MATURE }
var growth_phase: GrowthPhase = GrowthPhase.GERMINATING

func sync_growth(progress: float) -> void:
	growth_phase = clampi(int(progress * 4.0), GrowthPhase.GERMINATING, GrowthPhase.MATURE) as GrowthPhase

func is_harvestable() -> bool:
	return growth_phase == GrowthPhase.MATURE

func clip_texture(atlas: Texture2D, column: int, row: int) -> AtlasTexture:
	var result := AtlasTexture.new()
	result.atlas = atlas
	result.region = Rect2(column * 16, row * 16, 16, 16)
	return result
