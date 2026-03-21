package pl.misek.toafmobile.generated

import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapper {
    fun stringResource(resourceName: String): StringResource? {
        val prefix = resourceName.split('_')
        if (prefix.isEmpty()){
            println("NO RESOURCE for : $resourceName")
            return null
         }
        return when (prefix[0].lowercase()) {
           "abs" -> ResourceMapperAbs.stringResource(resourceName)
           "activityadvancecoding" -> ResourceMapperActivityAdvanceCoding.stringResource(resourceName)
           "activitycodingbcu" -> ResourceMapperActivityCodingBcu.stringResource(resourceName)
           "activityeepromprograming" -> ResourceMapperActivityEepromPrograming.stringResource(resourceName)
           "activityflashprograming" -> ResourceMapperActivityFlashPrograming.stringResource(resourceName)
           "atc" -> ResourceMapperAtc.stringResource(resourceName)
           "bcu" -> ResourceMapperBcu.stringResource(resourceName)
           "bcul322" -> ResourceMapperBcuL322.stringResource(resourceName)
           "bmbt" -> ResourceMapperBmbt.stringResource(resourceName)
           "cc" -> ResourceMapperCc.stringResource(resourceName)
           "coding" -> ResourceMapperCoding.stringResource(resourceName)
           "dde" -> ResourceMapperDde.stringResource(resourceName)
           "dsc" -> ResourceMapperDsc.stringResource(resourceName)
           "edc15" -> ResourceMapperEdc15.stringResource(resourceName)
           "ehc" -> ResourceMapperEhc.stringResource(resourceName)
           "elv" -> ResourceMapperElv.stringResource(resourceName)
           "ems" -> ResourceMapperEms.stringResource(resourceName)
           "ews" -> ResourceMapperEws.stringResource(resourceName)
           "gs20" -> ResourceMapperGs20.stringResource(resourceName)
           "hevac" -> ResourceMapperHevac.stringResource(resourceName)
           "ipk" -> ResourceMapperIpk.stringResource(resourceName)
           "jatco" -> ResourceMapperJatco.stringResource(resourceName)
           "lcm" -> ResourceMapperLcm.stringResource(resourceName)
           "lcml322" -> ResourceMapperLcmL322.stringResource(resourceName)
           "lws" -> ResourceMapperLws.stringResource(resourceName)
           "mems" -> ResourceMapperMems.stringResource(resourceName)
           "mfl" -> ResourceMapperMfl.stringResource(resourceName)
           "navmk3" -> ResourceMapperNavmk3.stringResource(resourceName)
           "nottranslatable" -> ResourceMapperNottranslatable.stringResource(resourceName)
           "pdc" -> ResourceMapperPdc.stringResource(resourceName)
           "radio" -> ResourceMapperRadio.stringResource(resourceName)
           "rs" -> ResourceMapperRs.stringResource(resourceName)
           "scu" -> ResourceMapperScu.stringResource(resourceName)
           "shd" -> ResourceMapperShd.stringResource(resourceName)
           "smac" -> ResourceMapperSmac.stringResource(resourceName)
           "srs2" -> ResourceMapperSrs2.stringResource(resourceName)
           "srsl322" -> ResourceMapperSrsL322.stringResource(resourceName)
           "srstemic" -> ResourceMapperSrsTemic.stringResource(resourceName)
           "video" -> ResourceMapperVideo.stringResource(resourceName)
           "webasto" -> ResourceMapperWebasto.stringResource(resourceName)
           "zcs" -> ResourceMapperZcs.stringResource(resourceName)
                else -> {
                  println("NO RESOURCE for : $resourceName")
                    null
                }
            }
       }
    }
