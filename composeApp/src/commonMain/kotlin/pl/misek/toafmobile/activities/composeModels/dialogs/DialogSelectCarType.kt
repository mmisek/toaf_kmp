package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.definitions.ToafColors
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

object DialogSelectCarType {
    @Composable
    fun Show(currentIndex : Int, onClose : () -> Unit, onSelect: (index : Int) -> Unit)
    {

        val options = mapOf(
            0 to Res.string.car_type_rover_75,
            1 to Res.string.car_type_rover_25,
            2 to Res.string.car_type_range_rover_l322,
            3 to Res.string.car_type_land_rover_freelander_1
        )
        var selectedItemIndex by remember { mutableIntStateOf(-1) }
        selectedItemIndex = currentIndex
        AlertDialog(
            shape = toafShape,
            modifier = Modifier.border(
                width = 1.dp,
                shape = toafShape,
                color = ToafColors.toafTextColorWhite
            ),

            containerColor = ToafColors.toafBackground,
            onDismissRequest = {onClose()},
            title = {
                Column(modifier = Modifier.fillMaxWidth().padding(1.dp))
                {
                    Text(
                        text = stringResource(Res.string.dialog_select_car_type_tittle),
                        color = ToafColors.toafTextColorWhite,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(5.dp)
                ) {
                    options.forEach { (index, label) ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp) // Standardowa wysokość elementu dotykowego
                                .selectable(
                                    selected = (selectedItemIndex == index),
                                    onClick = {
                                        selectedItemIndex = index
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedItemIndex == index),
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color.Green,
                                    unselectedColor = Color.Gray
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = stringResource(label),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                ToafButtons.GreenButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    buttonText = Res.string.dialog_select_car_type_ok,
                    onClick = {onSelect(selectedItemIndex)})
            },
            dismissButton = {
                ToafButtons.RedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    buttonText = Res.string.dialog_select_car_type_cancel,
                    onClick = { onClose() })
            }
        )
    }
}
@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
fun DialogSelectCarTypePreview()
{
    DialogSelectCarType.Show(currentIndex = 1, onClose = {}, onSelect = {})
}