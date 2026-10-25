package id.apotek.lestari.data

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Every server action of the web app, as a typed Retrofit call.
 * All actions share the same endpoint: POST "actions" with
 * body { action: "<name>", args: {...} } — see Models.kt ActionRequest.
 * Action names mirror server/src/actions.ts exactly (17 actions).
 */
interface ApiService {
    @POST("actions") suspend fun listMedicines(@Body req: ActionRequest<ListMedicinesArgs>): ListMedicinesResponse
    @POST("actions") suspend fun findMedicineByBarcode(@Body req: ActionRequest<BarcodeArgs>): FindByBarcodeResponse
    @POST("actions") suspend fun createMedicine(@Body req: ActionRequest<MedicineArgs>): IdResponse
    @POST("actions") suspend fun updateMedicine(@Body req: ActionRequest<UpdateMedicineArgs>): OkResponse
    @POST("actions") suspend fun deleteMedicine(@Body req: ActionRequest<IdArgs>): OkResponse
    @POST("actions") suspend fun bulkImportMedicines(@Body req: ActionRequest<BulkImportArgs>): BulkImportResponse
    @POST("actions") suspend fun downloadTemplate(@Body req: ActionRequest<EmptyArgs>): DownloadResponse
    @POST("actions") suspend fun downloadExport(@Body req: ActionRequest<EmptyArgs>): DownloadResponse
    @POST("actions") suspend fun createSale(@Body req: ActionRequest<CreateSaleArgs>): CreateSaleResponse
    @POST("actions") suspend fun listSales(@Body req: ActionRequest<ListLimitArgs>): ListSalesResponse
    @POST("actions") suspend fun listCashEntries(@Body req: ActionRequest<ListLimitArgs>): ListCashResponse
    @POST("actions") suspend fun createCashEntry(@Body req: ActionRequest<CreateCashEntryArgs>): IdResponse
    @POST("actions") suspend fun deleteCashEntry(@Body req: ActionRequest<IdArgs>): OkResponse
    @POST("actions") suspend fun getDashboard(@Body req: ActionRequest<EmptyArgs>): Dashboard
    @POST("actions") suspend fun getProfitReport(@Body req: ActionRequest<ProfitArgs>): ProfitReport
    @POST("actions") suspend fun downloadProfitReport(@Body req: ActionRequest<ProfitArgs>): DownloadResponse
    @POST("actions") suspend fun getWebAppManifest(@Body req: ActionRequest<EmptyArgs>): ManifestResponse
}
