package pl.misek.toafmobile.koin

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.basicAuth
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.interfaces.ToafWifiEventManager
import pl.misek.toafmobile.interfaces.ToafWifiManager
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.l314.ModuleCCU
import pl.misek.toafmobile.modules.l322.ModuleDSC
import pl.misek.toafmobile.modules.l322.ModuleEHC
import pl.misek.toafmobile.modules.l322.ModuleELV
import pl.misek.toafmobile.modules.l322.ModuleGS20
import pl.misek.toafmobile.modules.l322.ModuleGS8601
import pl.misek.toafmobile.modules.l322.ModuleHEVAC
import pl.misek.toafmobile.modules.l322.ModuleLSM
import pl.misek.toafmobile.modules.l322.ModuleLWS
import pl.misek.toafmobile.modules.l322.ModuleME72
import pl.misek.toafmobile.modules.l322.ModuleMFL
import pl.misek.toafmobile.modules.l322.ModuleTXU
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
import pl.misek.toafmobile.network.ToafApi
import pl.misek.toafmobile.protocols.ProtocolFactory
import pl.misek.toafmobile.viewModels.LiveDateViewModel
import pl.misek.toafmobile.viewModels.UserInfoViewModel

expect fun platformModules() : Module
val commonModules = module {
    single { ProtocolFactory() }
    single { ModulesManager() }

    single { get<ProtocolFactory>().protocolDs2 }
    single { get<ProtocolFactory>().protocolKwp }
    single { get<ProtocolFactory>().protocolKwpStar }
    single { get<ProtocolFactory>().protocolDde }
    single { get<ProtocolFactory>().protocolEdc15 }
    //L322
    single(named(KoinNames.ModuleL322Lsm.name)) { ModuleLSM(protocolDS2 = get())}
    single(named(KoinNames.ModuleL322Dsc.name)) { ModuleDSC(protocolDS2 = get())}
    single(named(KoinNames.ModuleL322Ehc.name)) { ModuleEHC(protocolDS2 = get())}
    single(named(KoinNames.ModuleL322Elv.name)) { ModuleELV(protocolDS2 = get())}
    single(named(KoinNames.ModuleL322Gs20.name)) { ModuleGS20(protocolDS2 = get())}
    single(named(KoinNames.ModuleL322Gs8601.name)) { ModuleGS8601(protocolDS2 = get())}
    single(named(KoinNames.ModuleL322Hevac.name)) { ModuleHEVAC(protocolDS2 = get())}
    single(named(KoinNames.ModuleL322Lws.name)) { ModuleLWS(protocolDS2 = get())}
    single(named(KoinNames.ModuleL322Mfl.name)) { ModuleMFL(protocolDS2 = get())}
    single(named(KoinNames.ModuleL322Srs.name)) { pl.misek.toafmobile.modules.l322.ModuleSRS(protocolDS2 = get())}
    single(named(KoinNames.ModuleL322Bcu.name)) { pl.misek.toafmobile.modules.l322.ModuleBCU(protocolDS2 = get())}
    single(named(KoinNames.ModuleL322Radio.name)) { pl.misek.toafmobile.modules.l322.ModuleRadio(protocolDS2 = get())}
    single(named(KoinNames.ModuleL322Txu.name)) { ModuleTXU(protocolKWP = get()) }
    single(named(KoinNames.ModuleMe72.name)) { ModuleME72(kwp = get()) }

    //R40
    single(named(KoinNames.ModuleR40Ipk.name)) { ModuleIPK(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Ews.name)) { ModuleEWS(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Abs.name)) { ModuleABS(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Atc.name)) { ModuleATC(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Bmbt.name)) { ModuleBMBT(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Cc.name)) { ModuleCC(protocolKWP = get())}
    single(named(KoinNames.ModuleR40Dde.name)) { ModuleDDE(kwp = get())}
    single(named(KoinNames.ModuleR40Ems.name)) { ModuleEMS(kwp = get())}
    single(named(KoinNames.ModuleR40Jatco.name)) { ModuleJATCO(kwp = get())}
    single(named(KoinNames.ModuleR40Mems.name)) { ModuleMEMS(kwp = get())}
    single(named(KoinNames.ModuleR40NavMk3.name)) { ModuleNavMk3(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Pdc.name)) { ModulePDC(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Rs.name)) { ModuleRS(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Shd.name)) { ModuleSHD(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Video.name)) { ModuleVideo(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Webasto.name)) { ModuleWebasto(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Bcu.name)) { ModuleBCU(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Radio.name)) { ModuleRadio(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Smac.name)) { ModuleSMAC(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Lsm.name)) { pl.misek.toafmobile.modules.r40.ModuleLSM(protocolDS2 = get())}
    single(named(KoinNames.ModuleR40Srs.name)) { ModuleSRS(protocolKWP = get()) }
    single(named(KoinNames.ModuleR40Srs2.name)) { ModuleSRS2(protocolKWPStar = get()) }
    //RJ0
    single(named(KoinNames.ModuleRJOScu.name)) { ModuleSCU(kwp = get())}
    single(named(KoinNames.ModuleRJOEdc15.name)) { ModuleEDC15(protocol = get())}
    //L20
    single(named(KoinNames.ModuleL20Ccu.name)) { ModuleCCU(kwp = get())}

    viewModel { (module : IModule) ->
        LiveDateViewModel(
            module = module,
            logFolder = get(named(KoinNames.LiveDataLogFolder.name))
        )
    }
    viewModel { UserInfoViewModel(get(),get()) }
    single { Database(get()) }
    single {
        val database : Database = get()
        HttpClient {
            install(HttpTimeout){
                requestTimeoutMillis = 5000
                connectTimeoutMillis = 5000
                socketTimeoutMillis = 5000
            }
            install(ContentNegotiation){
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(Logging) {
                logger = object : Logger{
                    override fun log(message: String) {
                        co.touchlab.kermit.Logger.i { "ToafApi : $message"}
                    }
                }
                level = LogLevel.ALL
            }
            install(Auth)
            {
                basic {
                    cacheTokens = false
                    sendWithoutRequest { request ->
                        true
                    }
                    credentials {
                        val userInfo = database.getLoginAndPassword()
                        if (userInfo != null)
                        {
                            co.touchlab.kermit.Logger.i {"CurrentSettings : $userInfo"}
                            BasicAuthCredentials(username = userInfo.first, password = userInfo.second)
                        }
                        else
                        {
                            null
                        }
                    }
                }
            }
            defaultRequest {
                header(HttpHeaders.ContentType, io.ktor.http.ContentType.Application.Json)
            }
            followRedirects = false
        }
    }
    single { ToafApi(client = get(), database = get()) }
    single { ToafWifiManager() }
    single { ToafWifiEventManager(get()) }
}

fun initKoin(appDeclaration: KoinAppDeclaration = {})
{
    startKoin {
        appDeclaration()
        modules(commonModules,platformModules())
    }
}