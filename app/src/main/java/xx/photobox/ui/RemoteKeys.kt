package xx.photobox.ui

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * A full-screen view worked from a TV remote or a keyboard: ← and → call
 * [onLeft] and [onRight], OK [onOk]. The view takes the focus when it opens,
 * so the keys reach it with nothing picked first. Only while it holds the
 * focus itself: with a button of its own focused, the arrows move between
 * the buttons and OK presses the one focused, as anywhere else.
 */
@Composable
fun Modifier.remoteKeys(onLeft: () -> Unit, onRight: () -> Unit, onOk: () -> Unit): Modifier {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val left by rememberUpdatedState(onLeft)
    val right by rememberUpdatedState(onRight)
    val ok by rememberUpdatedState(onOk)
    LaunchedEffect(Unit) { focus.requestFocus() }
    return this
        .focusRequester(focus)
        .onFocusChanged { focused = it.isFocused }
        .onKeyEvent { event ->
            if (!focused || event.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> left()
                Key.DirectionRight -> right()
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> ok()
                else -> return@onKeyEvent false
            }
            true
        }
        .focusable()
}
