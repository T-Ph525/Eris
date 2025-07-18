package com.apps.aivision.ui.language

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apps.aivision.data.model.LanguageModel
import com.apps.aivision.ui.ui_components.ToolBar
import java.util.*
import com.apps.aivision.R
import com.apps.aivision.components.click
import com.apps.aivision.ui.theme.Barlow


@Composable
fun LanguageScreen(
    navigateToBack: () -> Unit,
    languageViewModel: LanguageViewModel = hiltViewModel(),
) {
    val languageList: List<LanguageModel> = listOf(
        LanguageModel(
            name = "Arabic", code = "ar"
        ),
        LanguageModel(
            name = "Chinese", code = "zh"
        ), LanguageModel(
                name = "English", code = "en"
        ), LanguageModel(
            name = "French", code = "fr"
        ), LanguageModel(
            name = "German", code = "de"
        ), LanguageModel(
            name = "Norwegian", code = "no"
        )
        , LanguageModel(
            name = "Spanish", code = "es"
        )
    )


    LaunchedEffect(true) {
        languageViewModel.getCurrentLanguage()
    }

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        ToolBar(
            onClickAction = navigateToBack,
            image = com.apps.aivision.R.drawable.round_arrow_back_24,
            text = stringResource(R.string.language),
            MaterialTheme.colorScheme.onSurface
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .padding(horizontal = 16.dp)
        )
        {

            items(languageList) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(vertical = 15.dp)
                        .click {
                            languageViewModel.selectedValue.value = it.code
                            languageViewModel.setCurrentLanguage(it.code, it.name)
                            changeLanguage(context, it.code)
                            changeLanguage(context.applicationContext, it.code)
                            navigateToBack()
                        }
                ) {
                    Text(
                        text = it.name,
                       /* color = MaterialTheme.colorScheme.surface,*/
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.W600,
                            fontFamily = Barlow,
                            lineHeight = 25.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (languageViewModel.selectedValue.value == it.code) {
                        Icon(
                            painter = painterResource(id = R.drawable.done),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(27.dp)
                        )
                    }


                }

                Divider(
                    color = MaterialTheme.colorScheme.secondary, thickness = 1.dp,
                )
            }
        }
    }

}

fun changeLanguage(context: Context, language: String) {
    val locale = Locale(language)
    Locale.setDefault(locale)
    val config = Configuration()
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)

}