package tv.own.owntv.features.live

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: the whole synopsis of what a channel is showing now (and what follows), in one popup.
 * The cards cut it to two or three lines; this is where the rest can be read.
 */
@Composable
fun SynopsisPopup(
    channel: ChannelEntity,
    load: suspend (ChannelEntity) -> Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>,
    onDismiss: () -> Unit,
) {
    val formatTime = rememberSystemTimeFormatter()
    val programmes by produceState<Pair<EpgProgrammeEntity?, EpgProgrammeEntity?>?>(null, channel.id) {
        value = runCatching { load(channel) }.getOrDefault(null to null)
    }
    val close = remember { FocusRequester() }
    LaunchedEffect(programmes) { runCatching { close.requestFocus() } }
    val now = programmes?.first
    val next = programmes?.second
    StagePopup(
        onDismiss = onDismiss,
        eyebrow = channel.name.uppercase(),
        eyebrowAccent = true,
        title = now?.title ?: stringResource(R.string.glcn_synopsis_full),
        body = now?.let { stringResource(R.string.content_epg_time_range, formatTime(it.startMs), formatTime(it.stopMs)) },
        width = 1008.mpx,
        buttons = {
            StageButton(stringResource(R.string.content_close), onClick = onDismiss, height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.focusRequester(close))
        },
    ) {
        Body(now?.description, stringResource(R.string.glcn_synopsis_none), programmes != null)
        if (next != null) {
            Spacer(Modifier.height(22.mpx))
            Text(
                stringResource(R.string.content_live_next_label, formatTime(next.startMs)),
                style = stageText(15, 800),
                color = StageColors.Dim,
            )
            Spacer(Modifier.height(6.mpx))
            Text(next.title, style = stageText(20, 600), color = StageColors.Muted)
            if (!next.description.isNullOrBlank()) {
                Spacer(Modifier.height(6.mpx))
                Text(next.description.orEmpty(), style = stageText(18, 400), color = StageColors.Muted)
            }
        }
    }
}

@Composable
private fun ColumnScope.Body(text: String?, empty: String, loaded: Boolean) {
    if (!text.isNullOrBlank()) Text(text, style = stageText(19, 400), color = StageColors.Muted)
    else if (loaded) Text(empty, style = stageText(19, 400), color = StageColors.Dim)
}
