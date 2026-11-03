package pl.misek.toafmobile.modules

import androidx.compose.runtime.Composable
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.l322.ModuleDSC
import pl.misek.toafmobile.modules.l322.ModuleEHC
import pl.misek.toafmobile.modules.l322.ModuleELV
import pl.misek.toafmobile.modules.l322.ModuleGS20
import pl.misek.toafmobile.modules.l322.ModuleHEVAC
import pl.misek.toafmobile.modules.l322.ModuleLWS
import pl.misek.toafmobile.modules.l322.ModuleMFL
import pl.misek.toafmobile.modules.r40.ModuleABS
import pl.misek.toafmobile.modules.r40.ModuleATC
import pl.misek.toafmobile.modules.r40.ModuleBCU
import pl.misek.toafmobile.modules.r40.ModuleBMBT
import pl.misek.toafmobile.modules.r40.ModuleCC
import pl.misek.toafmobile.modules.r40.ModuleDDE
import pl.misek.toafmobile.modules.r40.ModuleEMS
import pl.misek.toafmobile.modules.r40.ModuleEWS
import pl.misek.toafmobile.modules.r40.ModuleIPK
import pl.misek.toafmobile.modules.r40.ModuleJATCO
import pl.misek.toafmobile.modules.r40.ModuleLSM
import pl.misek.toafmobile.modules.r40.ModuleMEMS
import pl.misek.toafmobile.modules.r40.ModuleNavMk3
import pl.misek.toafmobile.modules.r40.ModulePDC
import pl.misek.toafmobile.modules.r40.ModuleRS
import pl.misek.toafmobile.modules.r40.ModuleRadio
import pl.misek.toafmobile.modules.r40.ModuleSHD
import pl.misek.toafmobile.modules.r40.ModuleSMAC
import pl.misek.toafmobile.modules.r40.ModuleSRS
import pl.misek.toafmobile.modules.r40.ModuleSRS2
import pl.misek.toafmobile.modules.r40.ModuleVideo
import pl.misek.toafmobile.modules.r40.ModuleWebasto
import pl.misek.toafmobile.modules.rjo.ModuleEDC15
import pl.misek.toafmobile.modules.rjo.ModuleSCU

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.l314.ModuleCCU
import pl.misek.toafmobile.modules.l314.ModuleKOMBI
import pl.misek.toafmobile.modules.l314.ModuleASC
import pl.misek.toafmobile.modules.rjo.ModuleABS53
import pl.misek.toafmobile.modules.l322.ModuleGS8601
import pl.misek.toafmobile.modules.l322.ModuleME72
import pl.misek.toafmobile.modules.l322.ModuleTXU

class ModulesManager() : KoinComponent
{
    private var allModules = emptyList<IModule>()
    init {
        initModules()
    }
    private fun initModules() {
        allModules = mutableListOf(
            get<ModuleDDE>(named(KoinNames.ModuleR40Dde.name)),
            get<ModuleEMS>(named(KoinNames.ModuleR40Ems.name)),
            get<ModuleMEMS>(named(KoinNames.ModuleR40Mems.name)),
            get<ModuleEDC15>(named(KoinNames.ModuleRJOEdc15.name)),
            get<ModuleME72>(named(KoinNames.ModuleMe72.name)),

            get<ModuleJATCO>(named(KoinNames.ModuleR40Jatco.name)),
            get<ModuleSRS>(named(KoinNames.ModuleR40Srs.name)),
            get<ModuleSRS2>(named(KoinNames.ModuleR40Srs2.name)),
            get<pl.misek.toafmobile.modules.r40.ModuleGateway>(named(KoinNames.ModuleR40Gateway.name)),
            get<pl.misek.toafmobile.modules.r40.ModuleX12>(named(KoinNames.ModuleR40X12.name)),
            get<ModuleBCU>(named(KoinNames.ModuleR40Bcu.name)),

            get<ModuleEWS>(named(KoinNames.ModuleR40Ews.name)),
            get<ModuleLSM>(named(KoinNames.ModuleR40Lsm.name)),
            get<ModuleIPK>(named(KoinNames.ModuleR40Ipk.name)),
            get<ModuleABS>(named(KoinNames.ModuleR40Abs.name)),
            get<ModuleCC>(named(KoinNames.ModuleR40Cc.name)),
            get<ModuleRS>(named(KoinNames.ModuleR40Rs.name)),
            get<ModulePDC>(named(KoinNames.ModuleR40Pdc.name)),
            get<ModuleATC>(named(KoinNames.ModuleR40Atc.name)),
            get<ModuleSMAC>(named(KoinNames.ModuleR40Smac.name)),
            get<ModuleSHD>(named(KoinNames.ModuleR40Shd.name)),
            get<ModuleMFL>(named(KoinNames.ModuleL322Mfl.name)),
            get<ModuleLWS>(named(KoinNames.ModuleL322Lws.name)),
            get<ModuleTXU>(named(KoinNames.ModuleL322Txu.name)),
            get<pl.misek.toafmobile.modules.l322.ModuleBCU>(named(KoinNames.ModuleL322Bcu.name)),
            get<pl.misek.toafmobile.modules.l322.ModuleLSM>(named(KoinNames.ModuleL322Lsm.name)),
            get<pl.misek.toafmobile.modules.l322.ModuleSRS>(named(KoinNames.ModuleL322Srs.name)),
            get<ModuleELV>(named(KoinNames.ModuleL322Elv.name)),
            get<ModuleEHC>(named(KoinNames.ModuleL322Ehc.name)),
            get<ModuleDSC>(named(KoinNames.ModuleL322Dsc.name)),
            get<ModuleHEVAC>(named(KoinNames.ModuleL322Hevac.name)),
            get<ModuleGS20>(named(KoinNames.ModuleL322Gs20.name)),
            get<ModuleGS8601>(named(KoinNames.ModuleL322Gs8601.name)),
            get<ModuleWebasto>(named(KoinNames.ModuleR40Webasto.name)),
            get<ModuleBMBT>(named(KoinNames.ModuleR40Bmbt.name)),
            get<ModuleVideo>(named(KoinNames.ModuleR40Video.name)),
            get<ModuleRadio>(named(KoinNames.ModuleR40Radio.name)),
            get<pl.misek.toafmobile.modules.l322.ModuleRadio>(named(KoinNames.ModuleL322Radio.name)),
            get<pl.misek.toafmobile.modules.l322.ModulePDC>(named(KoinNames.ModuleL322Pdc.name)),
            get<pl.misek.toafmobile.modules.l322.ModuleIKE>(named(KoinNames.ModuleL322Ike.name)),
            //modules.add(new ModuleEMS(kwp_ems2k));
            get<ModuleNavMk3>(named(KoinNames.ModuleR40NavMk3.name)),
            get<ModuleSCU>(named(KoinNames.ModuleRJOScu.name)),
            get<ModuleABS53>(named(KoinNames.ModuleRJOAbs.name)),
            get<ModuleCCU>(named(KoinNames.ModuleL20Ccu.name)),
            get<ModuleKOMBI>(named(KoinNames.ModuleL20Kombi.name)),
            get<ModuleASC>(named(KoinNames.ModuleL20Asc.name)))
    }

    @Composable
    fun getCurrentModules(): List<IModule> {
        val database : Database = koinInject()
        val carTypeFromDatabase =
            runBlocking { database.getIntSettingValueByName(DatabaseSettingFields.TOAF_CURRENT_CAR_TYPE) }
        val currentCarType: CarType = CarType.entries[carTypeFromDatabase]
        return allModules.filter { x: IModule -> x.getSupportedCarTypes().contains(currentCarType) }
    }
}