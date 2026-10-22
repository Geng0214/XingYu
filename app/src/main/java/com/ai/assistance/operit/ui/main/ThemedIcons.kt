package com.ai.assistance.operit.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon as Material3Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal val LocalCompanionIconAppearance = staticCompositionLocalOf { CompanionIconAppearance.FILLED }

@Composable
internal fun ThemedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val appearance = LocalCompanionIconAppearance.current
    val iconModifier = if (appearance == CompanionIconAppearance.TONAL) {
        modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).padding(4.dp)
    } else modifier
    Material3Icon(
        imageVector = imageVector.withAppearance(appearance),
        contentDescription = contentDescription,
        modifier = iconModifier,
        tint = if (appearance == CompanionIconAppearance.TONAL) MaterialTheme.colorScheme.onSecondaryContainer else tint
    )
}

private data class IconFamilyVariants(
    val filled: () -> ImageVector,
    val outlined: () -> ImageVector
)

/*
 * The app uses Material icons from several pages and stores their style as one display
 * preference. Keeping both variants here makes every existing Icon call follow that
 * preference without changing screen-specific icon colors, sizes, or descriptions.
 */
private val materialIconFamilies = mapOf(
    // Planner icons must register both appearances; strict lookups otherwise crash when opening a calendar or editor.
    "Checklist" to IconFamilyVariants({ Icons.Filled.Checklist }, { Icons.Outlined.Checklist }),
    "CalendarMonth" to IconFamilyVariants({ Icons.Filled.CalendarMonth }, { Icons.Outlined.CalendarMonth }),
    "EventAvailable" to IconFamilyVariants({ Icons.Filled.EventAvailable }, { Icons.Outlined.EventAvailable }),
    "PeopleAlt" to IconFamilyVariants({ Icons.Filled.PeopleAlt }, { Icons.Outlined.PeopleAlt }),
    // The game uses these controls in every theme; an unregistered family throws while entering the world.
    "Map" to IconFamilyVariants({ Icons.Filled.Map }, { Icons.Outlined.Map }),
    "KeyboardArrowUp" to IconFamilyVariants({ Icons.Filled.KeyboardArrowUp }, { Icons.Outlined.KeyboardArrowUp }),
    "KeyboardArrowLeft" to IconFamilyVariants({ Icons.Filled.KeyboardArrowLeft }, { Icons.Outlined.KeyboardArrowLeft }),
    "KeyboardArrowRight" to IconFamilyVariants({ Icons.Filled.KeyboardArrowRight }, { Icons.Outlined.KeyboardArrowRight }),
    "WaterDrop" to IconFamilyVariants({ Icons.Filled.WaterDrop }, { Icons.Outlined.WaterDrop }),
    "Backpack" to IconFamilyVariants({ Icons.Filled.Backpack }, { Icons.Outlined.Backpack }),
    "Chair" to IconFamilyVariants({ Icons.Filled.Chair }, { Icons.Outlined.Chair }),
    "CardGiftcard" to IconFamilyVariants({ Icons.Filled.CardGiftcard }, { Icons.Outlined.CardGiftcard }),
    "Handyman" to IconFamilyVariants({ Icons.Filled.Handyman }, { Icons.Outlined.Handyman }),
    "Stars" to IconFamilyVariants({ Icons.Filled.Stars }, { Icons.Outlined.Stars }),
    "Bolt" to IconFamilyVariants({ Icons.Filled.Bolt }, { Icons.Outlined.Bolt }),
    "LocalCafe" to IconFamilyVariants({ Icons.Filled.LocalCafe }, { Icons.Outlined.LocalCafe }),
    "CleaningServices" to IconFamilyVariants({ Icons.Filled.CleaningServices }, { Icons.Outlined.CleaningServices }),
    "LocalFlorist" to IconFamilyVariants({ Icons.Filled.LocalFlorist }, { Icons.Outlined.LocalFlorist }),
    "Bedtime" to IconFamilyVariants({ Icons.Filled.Bedtime }, { Icons.Outlined.Bedtime }),
    "AccountCircle" to IconFamilyVariants(
        filled = { Icons.Filled.AccountCircle },
        outlined = { Icons.Outlined.AccountCircle }
    ),
    "Add" to IconFamilyVariants(
        filled = { Icons.Filled.Add },
        outlined = { Icons.Outlined.Add }
    ),
    "AddAPhoto" to IconFamilyVariants(
        filled = { Icons.Filled.AddAPhoto },
        outlined = { Icons.Outlined.AddAPhoto }
    ),
    "AddCircleOutline" to IconFamilyVariants(
        filled = { Icons.Filled.AddCircleOutline },
        outlined = { Icons.Outlined.AddCircleOutline }
    ),
    "AddPhotoAlternate" to IconFamilyVariants(
        filled = { Icons.Filled.AddPhotoAlternate },
        outlined = { Icons.Outlined.AddPhotoAlternate }
    ),
    "ArrowDownward" to IconFamilyVariants(
        filled = { Icons.Filled.ArrowDownward },
        outlined = { Icons.Outlined.ArrowDownward }
    ),
    "ArrowDropDown" to IconFamilyVariants(
        filled = { Icons.Filled.ArrowDropDown },
        outlined = { Icons.Outlined.ArrowDropDown }
    ),
    "ArrowUpward" to IconFamilyVariants(
        filled = { Icons.Filled.ArrowUpward },
        outlined = { Icons.Outlined.ArrowUpward }
    ),
    "AutoAwesome" to IconFamilyVariants(
        filled = { Icons.Filled.AutoAwesome },
        outlined = { Icons.Outlined.AutoAwesome }
    ),
    "Bookmarks" to IconFamilyVariants(
        filled = { Icons.Filled.Bookmarks },
        outlined = { Icons.Outlined.Bookmarks }
    ),
    "BrokenImage" to IconFamilyVariants(
        filled = { Icons.Filled.BrokenImage },
        outlined = { Icons.Outlined.BrokenImage }
    ),
    "Call" to IconFamilyVariants(
        filled = { Icons.Filled.Call },
        outlined = { Icons.Outlined.Call }
    ),
    "CallEnd" to IconFamilyVariants(
        filled = { Icons.Filled.CallEnd },
        outlined = { Icons.Outlined.CallEnd }
    ),
    "CheckCircle" to IconFamilyVariants(
        filled = { Icons.Filled.CheckCircle },
        outlined = { Icons.Outlined.CheckCircle }
    ),
    "CheckCircleOutline" to IconFamilyVariants(
        filled = { Icons.Filled.CheckCircleOutline },
        outlined = { Icons.Outlined.CheckCircleOutline }
    ),
    "Checkroom" to IconFamilyVariants(
        filled = { Icons.Filled.Checkroom },
        outlined = { Icons.Outlined.Checkroom }
    ),
    "ChevronLeft" to IconFamilyVariants(
        filled = { Icons.Filled.ChevronLeft },
        outlined = { Icons.Outlined.ChevronLeft }
    ),
    "ChevronRight" to IconFamilyVariants(
        filled = { Icons.Filled.ChevronRight },
        outlined = { Icons.Outlined.ChevronRight }
    ),
    "Close" to IconFamilyVariants(
        filled = { Icons.Filled.Close },
        outlined = { Icons.Outlined.Close }
    ),
    "Cloud" to IconFamilyVariants(
        filled = { Icons.Filled.Cloud },
        outlined = { Icons.Outlined.Cloud }
    ),
    "ContentCopy" to IconFamilyVariants(
        filled = { Icons.Filled.ContentCopy },
        outlined = { Icons.Outlined.ContentCopy }
    ),
    "Delete" to IconFamilyVariants(
        filled = { Icons.Filled.Delete },
        outlined = { Icons.Outlined.Delete }
    ),
    "DeleteOutline" to IconFamilyVariants(
        filled = { Icons.Filled.DeleteOutline },
        outlined = { Icons.Outlined.DeleteOutline }
    ),
    "Description" to IconFamilyVariants(
        filled = { Icons.Filled.Description },
        outlined = { Icons.Outlined.Description }
    ),
    "DragHandle" to IconFamilyVariants(
        filled = { Icons.Filled.DragHandle },
        outlined = { Icons.Outlined.DragHandle }
    ),
    "Eco" to IconFamilyVariants(
        filled = { Icons.Filled.Eco },
        outlined = { Icons.Outlined.Eco }
    ),
    "Edit" to IconFamilyVariants(
        filled = { Icons.Filled.Edit },
        outlined = { Icons.Outlined.Edit }
    ),
    // ThemedIcon resolves every supplied vector by name; each routed icon needs an explicit pair to avoid a render-time missing-key crash.
    "ExpandLess" to IconFamilyVariants(
        filled = { Icons.Filled.ExpandLess },
        outlined = { Icons.Outlined.ExpandLess }
    ),
    "ExpandMore" to IconFamilyVariants(
        filled = { Icons.Filled.ExpandMore },
        outlined = { Icons.Outlined.ExpandMore }
    ),
    "EmojiEmotions" to IconFamilyVariants(
        filled = { Icons.Filled.EmojiEmotions },
        outlined = { Icons.Outlined.EmojiEmotions }
    ),
    "EnhancedEncryption" to IconFamilyVariants(
        filled = { Icons.Filled.EnhancedEncryption },
        outlined = { Icons.Outlined.EnhancedEncryption }
    ),
    "ErrorOutline" to IconFamilyVariants(
        filled = { Icons.Filled.ErrorOutline },
        outlined = { Icons.Outlined.ErrorOutline }
    ),
    "Event" to IconFamilyVariants(
        filled = { Icons.Filled.Event },
        outlined = { Icons.Outlined.Event }
    ),
    "Explore" to IconFamilyVariants(
        filled = { Icons.Filled.Explore },
        outlined = { Icons.Outlined.Explore }
    ),
    "Extension" to IconFamilyVariants(
        filled = { Icons.Filled.Extension },
        outlined = { Icons.Outlined.Extension }
    ),
    "FavoriteBorder" to IconFamilyVariants(
        filled = { Icons.Filled.FavoriteBorder },
        outlined = { Icons.Outlined.FavoriteBorder }
    ),
    "FileDownload" to IconFamilyVariants(
        filled = { Icons.Filled.FileDownload },
        outlined = { Icons.Outlined.FileDownload }
    ),
    "FormatQuote" to IconFamilyVariants(
        filled = { Icons.Filled.FormatQuote },
        outlined = { Icons.Outlined.FormatQuote }
    ),
    "Forum" to IconFamilyVariants(
        filled = { Icons.Filled.Forum },
        outlined = { Icons.Outlined.Forum }
    ),
    "GraphicEq" to IconFamilyVariants(
        filled = { Icons.Filled.GraphicEq },
        outlined = { Icons.Outlined.GraphicEq }
    ),
    "Grass" to IconFamilyVariants(
        filled = { Icons.Filled.Grass },
        outlined = { Icons.Outlined.Grass }
    ),
    "Groups" to IconFamilyVariants(
        filled = { Icons.Filled.Groups },
        outlined = { Icons.Outlined.Groups }
    ),
    "Headphones" to IconFamilyVariants(
        filled = { Icons.Filled.Headphones },
        outlined = { Icons.Outlined.Headphones }
    ),
    "HealthAndSafety" to IconFamilyVariants(
        filled = { Icons.Filled.HealthAndSafety },
        outlined = { Icons.Outlined.HealthAndSafety }
    ),
    "Hearing" to IconFamilyVariants(
        filled = { Icons.Filled.Hearing },
        outlined = { Icons.Outlined.Hearing }
    ),
    "Home" to IconFamilyVariants(
        filled = { Icons.Filled.Home },
        outlined = { Icons.Outlined.Home }
    ),
    "Hub" to IconFamilyVariants(
        filled = { Icons.Filled.Hub },
        outlined = { Icons.Outlined.Hub }
    ),
    "Image" to IconFamilyVariants(
        filled = { Icons.Filled.Image },
        outlined = { Icons.Outlined.Image }
    ),
    "Info" to IconFamilyVariants(
        filled = { Icons.Filled.Info },
        outlined = { Icons.Outlined.Info }
    ),
    "Inventory2" to IconFamilyVariants(
        filled = { Icons.Filled.Inventory2 },
        outlined = { Icons.Outlined.Inventory2 }
    ),
    "KeyboardArrowDown" to IconFamilyVariants(
        filled = { Icons.Filled.KeyboardArrowDown },
        outlined = { Icons.Outlined.KeyboardArrowDown }
    ),
    "LibraryMusic" to IconFamilyVariants(
        filled = { Icons.Filled.LibraryMusic },
        outlined = { Icons.Outlined.LibraryMusic }
    ),
    "Link" to IconFamilyVariants(
        filled = { Icons.Filled.Link },
        outlined = { Icons.Outlined.Link }
    ),
    "LocationCity" to IconFamilyVariants(
        filled = { Icons.Filled.LocationCity },
        outlined = { Icons.Outlined.LocationCity }
    ),
    "Lock" to IconFamilyVariants(
        filled = { Icons.Filled.Lock },
        outlined = { Icons.Outlined.Lock }
    ),
    "Mic" to IconFamilyVariants(
        filled = { Icons.Filled.Mic },
        outlined = { Icons.Outlined.Mic }
    ),
    "MoreHoriz" to IconFamilyVariants(
        filled = { Icons.Filled.MoreHoriz },
        outlined = { Icons.Outlined.MoreHoriz }
    ),
    "MoreVert" to IconFamilyVariants(
        filled = { Icons.Filled.MoreVert },
        outlined = { Icons.Outlined.MoreVert }
    ),
    "Movie" to IconFamilyVariants(
        filled = { Icons.Filled.Movie },
        outlined = { Icons.Outlined.Movie }
    ),
    "MusicNote" to IconFamilyVariants(
        filled = { Icons.Filled.MusicNote },
        outlined = { Icons.Outlined.MusicNote }
    ),
    "Notifications" to IconFamilyVariants(
        filled = { Icons.Filled.Notifications },
        outlined = { Icons.Outlined.Notifications }
    ),
    "NotificationsActive" to IconFamilyVariants(
        filled = { Icons.Filled.NotificationsActive },
        outlined = { Icons.Outlined.NotificationsActive }
    ),
    "OndemandVideo" to IconFamilyVariants(
        filled = { Icons.Filled.OndemandVideo },
        outlined = { Icons.Outlined.OndemandVideo }
    ),
    "Palette" to IconFamilyVariants(
        filled = { Icons.Filled.Palette },
        outlined = { Icons.Outlined.Palette }
    ),
    "Pause" to IconFamilyVariants(
        filled = { Icons.Filled.Pause },
        outlined = { Icons.Outlined.Pause }
    ),
    "Person" to IconFamilyVariants(
        filled = { Icons.Filled.Person },
        outlined = { Icons.Outlined.Person }
    ),
    "PersonAdd" to IconFamilyVariants(
        filled = { Icons.Filled.PersonAdd },
        outlined = { Icons.Outlined.PersonAdd }
    ),
    // Role deletion is available in every theme; register both variants before the strict runtime lookup.
    "PersonRemove" to IconFamilyVariants(
        filled = { Icons.Filled.PersonRemove },
        outlined = { Icons.Outlined.PersonRemove }
    ),
    "Place" to IconFamilyVariants(
        filled = { Icons.Filled.Place },
        outlined = { Icons.Outlined.Place }
    ),
    "PlayArrow" to IconFamilyVariants(
        filled = { Icons.Filled.PlayArrow },
        outlined = { Icons.Outlined.PlayArrow }
    ),
    "PushPin" to IconFamilyVariants(
        filled = { Icons.Filled.PushPin },
        outlined = { Icons.Outlined.PushPin }
    ),
    "RecordVoiceOver" to IconFamilyVariants(
        filled = { Icons.Filled.RecordVoiceOver },
        outlined = { Icons.Outlined.RecordVoiceOver }
    ),
    "Refresh" to IconFamilyVariants(
        filled = { Icons.Filled.Refresh },
        outlined = { Icons.Outlined.Refresh }
    ),
    "Remove" to IconFamilyVariants(
        filled = { Icons.Filled.Remove },
        outlined = { Icons.Outlined.Remove }
    ),
    "RestartAlt" to IconFamilyVariants(
        filled = { Icons.Filled.RestartAlt },
        outlined = { Icons.Outlined.RestartAlt }
    ),
    "Restaurant" to IconFamilyVariants(
        filled = { Icons.Filled.Restaurant },
        outlined = { Icons.Outlined.Restaurant }
    ),
    "SaveAlt" to IconFamilyVariants(
        filled = { Icons.Filled.SaveAlt },
        outlined = { Icons.Outlined.SaveAlt }
    ),
    "Schedule" to IconFamilyVariants(
        filled = { Icons.Filled.Schedule },
        outlined = { Icons.Outlined.Schedule }
    ),
    "School" to IconFamilyVariants(
        filled = { Icons.Filled.School },
        outlined = { Icons.Outlined.School }
    ),
    "Search" to IconFamilyVariants(
        filled = { Icons.Filled.Search },
        outlined = { Icons.Outlined.Search }
    ),
    "Security" to IconFamilyVariants(
        filled = { Icons.Filled.Security },
        outlined = { Icons.Outlined.Security }
    ),
    "Settings" to IconFamilyVariants(
        filled = { Icons.Filled.Settings },
        outlined = { Icons.Outlined.Settings }
    ),
    "SportsEsports" to IconFamilyVariants(
        filled = { Icons.Filled.SportsEsports },
        outlined = { Icons.Outlined.SportsEsports }
    ),
    "Star" to IconFamilyVariants(
        filled = { Icons.Filled.Star },
        outlined = { Icons.Outlined.Star }
    ),
    "StarBorder" to IconFamilyVariants(
        filled = { Icons.Filled.StarBorder },
        outlined = { Icons.Outlined.StarBorder }
    ),
    "Stop" to IconFamilyVariants(
        filled = { Icons.Filled.Stop },
        outlined = { Icons.Outlined.Stop }
    ),
    "Translate" to IconFamilyVariants(
        filled = { Icons.Filled.Translate },
        outlined = { Icons.Outlined.Translate }
    ),
    "UploadFile" to IconFamilyVariants(
        filled = { Icons.Filled.UploadFile },
        outlined = { Icons.Outlined.UploadFile }
    ),
    "VideoLibrary" to IconFamilyVariants(
        filled = { Icons.Filled.VideoLibrary },
        outlined = { Icons.Outlined.VideoLibrary }
    ),
    "Visibility" to IconFamilyVariants(
        filled = { Icons.Filled.Visibility },
        outlined = { Icons.Outlined.Visibility }
    ),
    "Wallpaper" to IconFamilyVariants(
        filled = { Icons.Filled.Wallpaper },
        outlined = { Icons.Outlined.Wallpaper }
    ),
    "WarningAmber" to IconFamilyVariants(
        filled = { Icons.Filled.WarningAmber },
        outlined = { Icons.Outlined.WarningAmber }
    ),
    "Widgets" to IconFamilyVariants(
        filled = { Icons.Filled.Widgets },
        outlined = { Icons.Outlined.Widgets }
    ),
    "Yard" to IconFamilyVariants(
        filled = { Icons.Filled.Yard },
        outlined = { Icons.Outlined.Yard }
    )
)

private val autoMirroredMaterialIconFamilies = mapOf(
    "KeyboardArrowLeft" to IconFamilyVariants(
        filled = { Icons.AutoMirrored.Filled.KeyboardArrowLeft },
        outlined = { Icons.AutoMirrored.Outlined.KeyboardArrowLeft }
    ),
    "KeyboardArrowRight" to IconFamilyVariants(
        filled = { Icons.AutoMirrored.Filled.KeyboardArrowRight },
        outlined = { Icons.AutoMirrored.Outlined.KeyboardArrowRight }
    ),
    "ArrowBack" to IconFamilyVariants(
        filled = { Icons.AutoMirrored.Filled.ArrowBack },
        outlined = { Icons.AutoMirrored.Outlined.ArrowBack }
    ),
    "ArrowForward" to IconFamilyVariants(
        filled = { Icons.AutoMirrored.Filled.ArrowForward },
        outlined = { Icons.AutoMirrored.Outlined.ArrowForward }
    ),
    "Chat" to IconFamilyVariants(
        filled = { Icons.AutoMirrored.Filled.Chat },
        outlined = { Icons.AutoMirrored.Outlined.Chat }
    ),
    "HelpOutline" to IconFamilyVariants(
        filled = { Icons.AutoMirrored.Filled.HelpOutline },
        outlined = { Icons.AutoMirrored.Outlined.HelpOutline }
    ),
    "MenuBook" to IconFamilyVariants(
        filled = { Icons.AutoMirrored.Filled.MenuBook },
        outlined = { Icons.AutoMirrored.Outlined.MenuBook }
    ),
    "ReceiptLong" to IconFamilyVariants(
        filled = { Icons.AutoMirrored.Filled.ReceiptLong },
        outlined = { Icons.AutoMirrored.Outlined.ReceiptLong }
    ),
    "Send" to IconFamilyVariants(
        filled = { Icons.AutoMirrored.Filled.Send },
        outlined = { Icons.AutoMirrored.Outlined.Send }
    ),
    "VolumeUp" to IconFamilyVariants(
        filled = { Icons.AutoMirrored.Filled.VolumeUp },
        outlined = { Icons.AutoMirrored.Outlined.VolumeUp }
    )
)

internal fun ImageVector.withAppearance(appearance: CompanionIconAppearance): ImageVector {
    val vectorName = name
    val iconName = vectorName.substringAfterLast('.')
    val families = if (vectorName.startsWith("AutoMirrored.")) {
        autoMirroredMaterialIconFamilies
    } else {
        materialIconFamilies
    }
    val variants = families.getValue(iconName)
    return when (appearance) {
        CompanionIconAppearance.FILLED -> variants.filled()
        CompanionIconAppearance.OUTLINED -> variants.outlined()
        CompanionIconAppearance.TONAL -> variants.filled()
    }
}
