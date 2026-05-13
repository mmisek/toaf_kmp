package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.BuildKonfig
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.getPlatformType
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.alert_module_info_close
import toafmobile.composeapp.generated.resources.dialog_about_app_name
import toafmobile.composeapp.generated.resources.dialog_about_author
import toafmobile.composeapp.generated.resources.dialog_about_contact
import toafmobile.composeapp.generated.resources.dialog_about_contact_email
import toafmobile.composeapp.generated.resources.dialog_about_greetings
import toafmobile.composeapp.generated.resources.dialog_about_tab_about
import toafmobile.composeapp.generated.resources.dialog_about_tab_licences
import toafmobile.composeapp.generated.resources.dialog_about_tittle
import toafmobile.composeapp.generated.resources.dialog_about_web_page
import toafmobile.composeapp.generated.resources.dialog_about_web_page_link

@Composable
fun DialogAbout(onClose : () -> Unit)
{
    val appNameText = stringResource(Res.string.dialog_about_app_name)
    val webPageText = stringResource(Res.string.dialog_about_web_page)
    val webPageLinkText = stringResource(Res.string.dialog_about_web_page_link)
    val contactText = stringResource(Res.string.dialog_about_contact)
    val emailLinkText = stringResource(Res.string.dialog_about_contact_email)
    val authorText = stringResource(Res.string.dialog_about_author)
    val greetingsText = stringResource(Res.string.dialog_about_greetings)
    val tabAboutText = stringResource(Res.string.dialog_about_tab_about)
    val tabLicencesText = stringResource(Res.string.dialog_about_tab_licences)
    val libraries by produceLibraries {
        Res.readBytes("files/aboutlibraries.json").decodeToString()
    }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

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
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp))
            {
                Text(
                    text = stringResource(Res.string.dialog_about_tittle),
                    fontSize = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
            }
        },
        text = {
            val aboutText = buildAnnotatedString {
                appendLine(String.format(appNameText, getPlatformType().name.uppercase(), BuildKonfig.VERSION_NAME,
                    BuildKonfig.VERSION_CODE))
                append(webPageText)
                val webPageLink = LinkAnnotation.Url(
                    url = webPageLinkText,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = ToafColors.toafToastLink,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                )
                withLink(webPageLink){
                    appendLine(webPageLinkText)
                }
                append(contactText)
                val emailLink = LinkAnnotation.Url(
                    url = "mailto:$emailLinkText",
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = ToafColors.toafToastLink,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                )
                withLink(emailLink)
                {
                    appendLine(emailLinkText)
                }
                appendLine(authorText)
                appendLine(greetingsText)
            }

            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                if (tabAboutText.isNotEmpty() && tabLicencesText.isNotEmpty()) {
                    val tabs = listOf(tabAboutText, tabLicencesText)
                    PrimaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = ToafColors.toafTextColorWhite
                    )
                    {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text(title, color = ToafColors.toafTextColorWhite) }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp))
                {
                    when (selectedTabIndex) {
                        0 -> {
                            val scroll = rememberScrollState()
                            Text(
                                text = aboutText,
                                modifier = Modifier.fillMaxWidth().verticalScroll(scroll),
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                        1 -> libraries?.let {
                            LibrariesContainer(
                            libraries = it,
                            modifier = Modifier.fillMaxWidth())
                        } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
            }
        },
        confirmButton = {
            Column {
                ToafButtons.GreenButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    buttonText = Res.string.alert_module_info_close,
                    onClick = { onClose() })
            }
        }
    )
}





@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
fun DialogAboutPreview()
{
    DialogAbout(onClose = {})
}
