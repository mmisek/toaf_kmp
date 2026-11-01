package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
object ResourceMapper {
    fun stringResource(baseResourceName: String): StringResource? {
        val resourceName = baseResourceName.replace(".","")
        val prefix = resourceName.split('_')
        if (prefix.isEmpty()){
            Logger.i {"NO RESOURCE for : $resourceName"}
            return null
         }
        val twoPartPrefix = if (prefix.size > 1) (prefix[0] + prefix[1]).lowercase() else ""
        when (twoPartPrefix) {
           "bcul322" -> return ResourceMapperBcuL322.stringResource(resourceName)
           "lcml322" -> return ResourceMapperLcmL322.stringResource(resourceName)
           "pdcl322" -> return ResourceMapperPdcL322.stringResource(resourceName)
           "srsl322" -> return ResourceMapperSrsL322.stringResource(resourceName)
        }
        return when (prefix[0].lowercase()) {
           "abs" -> ResourceMapperAbs.stringResource(resourceName)
           "abs53" -> ResourceMapperAbs53.stringResource(resourceName)
           "asc" -> ResourceMapperAsc.stringResource(resourceName)
           "activityadvancecoding" -> ResourceMapperActivityAdvanceCoding.stringResource(resourceName)
           "activitycodingbcu" -> ResourceMapperActivityCodingBcu.stringResource(resourceName)
           "activityeepromprograming" -> ResourceMapperActivityEepromPrograming.stringResource(resourceName)
           "activityflashprograming" -> ResourceMapperActivityFlashPrograming.stringResource(resourceName)
           "atc" -> ResourceMapperAtc.stringResource(resourceName)
           "bcu" -> ResourceMapperBcu.stringResource(resourceName)
           "bmbt" -> ResourceMapperBmbt.stringResource(resourceName)
           "cc" -> ResourceMapperCc.stringResource(resourceName)
           "ccu" -> ResourceMapperCcu.stringResource(resourceName)
           "coding" -> ResourceMapperCoding.stringResource(resourceName)
           "dde" -> ResourceMapperDde.stringResource(resourceName)
           "dsc" -> ResourceMapperDsc.stringResource(resourceName)
           "edc15" -> ResourceMapperEdc15.stringResource(resourceName)
           "ehc" -> ResourceMapperEhc.stringResource(resourceName)
           "kombi" -> ResourceMapperKombi.stringResource(resourceName)
           "elv" -> ResourceMapperElv.stringResource(resourceName)
           "ems" -> ResourceMapperEms.stringResource(resourceName)
           "ews" -> ResourceMapperEws.stringResource(resourceName)
           "gateway" -> ResourceMapperGateway.stringResource(resourceName)
           "gs20" -> ResourceMapperGs20.stringResource(resourceName)
           "gs8601" -> ResourceMapperGs8601.stringResource(resourceName)
           "ike" -> ResourceMapperIke.stringResource(resourceName)
           "hevac" -> ResourceMapperHevac.stringResource(resourceName)
           "ipk" -> ResourceMapperIpk.stringResource(resourceName)
           "jatco" -> ResourceMapperJatco.stringResource(resourceName)
           "lcm" -> ResourceMapperLcm.stringResource(resourceName)
           "lws" -> ResourceMapperLws.stringResource(resourceName)
           "me72" -> ResourceMapperMe72.stringResource(resourceName)
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
           "srs" -> ResourceMapperSrs.stringResource(resourceName)
           "srs2" -> ResourceMapperSrs2.stringResource(resourceName)
           "txu" -> ResourceMapperTxu.stringResource(resourceName)
           "video" -> ResourceMapperVideo.stringResource(resourceName)
           "webasto" -> ResourceMapperWebasto.stringResource(resourceName)
           "zcs" -> ResourceMapperZcs.stringResource(resourceName)
                else -> {
                  Logger.i {"NO RESOURCE for : $resourceName" }
                    null
                }
            }
       }
    }
