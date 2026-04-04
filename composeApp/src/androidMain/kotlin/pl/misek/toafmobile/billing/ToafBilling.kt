package pl.misek.toafmobile.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pl.misek.toafmobile.activities.ToafConstants
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.network.ToafApi
import pl.misek.toafmobile.tools.isDebug

class AndroidToafBilling (context: Context, private val database : Database, private val toafApi : ToafApi) : IToafBilling
{
    private var tag = "ToafBilling"
    private var billingClient: BillingClient? = null
    private var productsPrices = emptyMap<String, String>()
    private var productList = mutableListOf<QueryProductDetailsParams.Product>()
    private var productDetailsList: List<ProductDetails>? = null
    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun getElementPrice(elementName: String): String? {
        return productsPrices[elementName]
    }

    override fun buyElement(activity: Any?, productName: String) {
        val act = activity as? Activity ?: return
        productDetailsList?.firstOrNull { it.productId == productName }?.let {
            Log.d(tag, "User want to buy $productName")
            launchBillingFlow(act, it)
        }
    }

    override suspend fun getStripeUrl(elementName: String) = ""

    private fun connectToPlayStore() {
        if (billingClient?.isReady != true) {
            Log.d(tag, "Connecting with Google Play Store")
            billingClient?.startConnection(billingClientStateListener)
        }
    }

    private val billingClientStateListener: BillingClientStateListener =
        object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                Log.d(tag, "Disconnected from Google Play Store")
                billingScope.launch {
                    connectToPlayStore()
                    delay(60_000)
                }
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(tag, "onBillingSetupFinished responseCode : $billingResult")
                    checkPurchases()
                    queryPrices()
                }
                else {
                    Log.d(tag, "onBillingSetupFinished failed : ${billingResult.debugMessage}")
                }
            }
        }

    private fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        val productDetailsParams = listOf(
            ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParams)
            .build()
        val response =
            billingClient?.launchBillingFlow(activity, billingFlowParams)?.responseCode ?: 0
        Log.d(tag, "launchBillingFlow responseCode = $response")
    }


    private fun checkPurchases() {
        if (billingClient?.isReady != true) {
            connectToPlayStore()
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient?.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(tag, "queryPurchasesAsync, purchaseList.Count : ${purchasesList.count()}")
                processPurchases(purchaseList = purchasesList)
                processRefunds(purchaseList = purchasesList)
            } else {
                Log.d(tag, "Error in checkPurchases(): $billingResult")
            }
        }
    }

    private fun processPurchases(purchaseList: List<Purchase>) {
        for (purchase in purchaseList) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                val sku = purchase.products.firstOrNull() ?: continue
                Log.d(tag, "processPurchases : $sku")
                if (isDebug())
                    consumePurchase(purchase)
                else
                    if (purchase.isAcknowledged) continue

                when (sku) {
                    ToafConstants.SKU_TOAF_FULL_VERSION -> {
                        billingScope.launch {
                            if (database.updateStringSettingByName(
                                    DatabaseSettingFields.TOAF_FULL_VERSION,
                                    value = "T"
                                )
                            ) {
                                acknowledgePurchase(purchase)
                                addPurchaseToDatabase(purchase = purchase)
                            }
                        }
                    }

                    ToafConstants.SKU_TOAF_EKA_CODE -> {
                        billingScope.launch {
                            val quantity =
                                database.getIntSettingValueByName(DatabaseSettingFields.TOAF_EKA_CODES)
                            if (database.updateIntSettingByName(
                                    DatabaseSettingFields.TOAF_EKA_CODES,
                                    quantity + purchase.quantity
                                )
                            ) {
                                consumePurchase(purchase)
                                addPurchaseToDatabase(purchase = purchase)
                            }
                        }
                    }

                    ToafConstants.SKU_TOAF_BCU_KEY -> {
                        billingScope.launch {
                            val quantity =
                                database.getIntSettingValueByName(DatabaseSettingFields.TOAF_BCU_KEYS)
                            if (database.updateIntSettingByName(
                                    DatabaseSettingFields.TOAF_BCU_KEYS,
                                    quantity + purchase.quantity
                                )
                            ) {
                                consumePurchase(purchase)
                                addPurchaseToDatabase(purchase = purchase)
                            }
                        }
                    }

                }
            }
        }
    }

    private fun processRefunds(purchaseList: List<Purchase>)
    {
        val activePurchases = purchaseList.flatMap { it.products }
        billingScope.launch {
            val isFullVersionInDatabase = database.getStringSettingValueByName(DatabaseSettingFields.TOAF_FULL_VERSION) == "T"
            val isFullVersionByGoogle = activePurchases.contains(ToafConstants.SKU_TOAF_FULL_VERSION)
            if (isFullVersionInDatabase && !isFullVersionByGoogle)
            {
                database.updateStringSettingByName(DatabaseSettingFields.TOAF_FULL_VERSION, "N")
                Log.d(tag,"Removing ${ToafConstants.SKU_TOAF_FULL_VERSION} from database")
            }
        }
    }

    private suspend fun addPurchaseToDatabase(purchase: Purchase) : Boolean
    {
        val json = buildJsonObject {
            put("purchaseToken",purchase.purchaseToken)
            put("productId", purchase.products[0])
            put("quantity", purchase.quantity)
            put("purchaseState", if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) 0 else purchase.purchaseState)
        }
        return withContext(Dispatchers.IO)
        {
            toafApi.userAddPurchases(json)
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams
            .newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            Log.d(tag, "Purchase : ${purchase.products.first()} acknowledge result : $billingResult")
        }
    }

    private fun queryPrices()
    {
        if (billingClient?.isReady != true)
        {
            connectToPlayStore()
            return
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        billingClient?.queryProductDetailsAsync(params) { billingResult, productList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK)
            {
                productDetailsList = productList.productDetailsList
                productsPrices = productList.productDetailsList.associate { details->
                    val price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: "---"
                    Log.d(tag,"queryProductDetailsAsync ${details.productId} : $price")
                    details.productId to price
                }
            }
        }
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchasesList ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchasesList != null) {
            Log.d(tag,"purchasesUpdatedListener")
            processPurchases(purchasesList)
        }
    }



    private fun consumePurchase(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient?.consumeAsync(consumeParams) { billingResult, _ ->
            Log.d(tag, "consumePurchase result : $billingResult")
        }
    }

    init {
        val productNameList = listOf(
            ToafConstants.SKU_TOAF_FULL_VERSION,
            ToafConstants.SKU_TOAF_BCU_KEY,
            ToafConstants.SKU_TOAF_EKA_CODE
        )
         productList.clear()

        for (product in productNameList) {
            productList.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(product).
                    setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        }

        val pendingPurchasesParams = PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()
        connectToPlayStore()
    }
}

