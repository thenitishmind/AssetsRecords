package com.assetmgmt.laptops.data.remote

import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.QueryMap
import retrofit2.http.Streaming

typealias Query = Map<String, String>

/** Mirrors the backend REST API (all paths are relative to .../api/). */
interface ApiService {
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): LoginResponse
    @GET("auth/me") suspend fun me(): MeResponse
    @POST("auth/change-password") suspend fun changePassword(@Body body: JsonObject): JsonObject

    @GET("dashboard") suspend fun dashboard(): Dashboard
    @GET("search") suspend fun search(@QueryMap q: Query): SearchResult
    @GET("stock") suspend fun stock(): StockOverview
    @GET("stock/assets") suspend fun stockAssets(@QueryMap q: Query): Page<Asset>

    @GET("branches") suspend fun branches(@QueryMap q: Query): Page<Branch>
    @GET("branches/{id}") suspend fun branch(@Path("id") id: String): Branch
    @POST("branches") suspend fun createBranch(@Body body: JsonObject): Branch
    @PUT("branches/{id}") suspend fun updateBranch(@Path("id") id: String, @Body body: JsonObject): Branch
    @DELETE("branches/{id}") suspend fun deactivateBranch(@Path("id") id: String): Branch
    @GET("branches/{id}/assets") suspend fun branchAssets(@Path("id") id: String, @QueryMap q: Query): BranchAssets

    @GET("employees") suspend fun employees(@QueryMap q: Query): Page<Employee>
    @GET("employees/{id}") suspend fun employee(@Path("id") id: String): Employee
    @POST("employees") suspend fun createEmployee(@Body body: JsonObject): Employee
    @PUT("employees/{id}") suspend fun updateEmployee(@Path("id") id: String, @Body body: JsonObject): Employee
    @DELETE("employees/{id}") suspend fun deactivateEmployee(@Path("id") id: String): Employee
    @GET("employees/{id}/assets") suspend fun employeeAssets(@Path("id") id: String): EmployeeAssets

    @GET("assets") suspend fun assets(@QueryMap q: Query): Page<Asset>
    @GET("assets/{id}") suspend fun assetDetails(@Path("id") id: String): AssetDetails
    @POST("assets") suspend fun createAsset(@Body body: JsonObject): Asset
    @PUT("assets/{id}") suspend fun updateAsset(@Path("id") id: String, @Body body: JsonObject): Asset
    @PUT("assets/{id}/status") suspend fun changeAssetStatus(@Path("id") id: String, @Body body: JsonObject): Asset
    @POST("assets/{id}/mark-dead") suspend fun markDead(@Path("id") id: String, @Body body: JsonObject): Asset

    @POST("assignments") suspend fun assign(@Body body: JsonObject): Assignment
    @PUT("assignments/{id}") suspend fun updateAssignment(@Path("id") id: String, @Body body: JsonObject): Assignment
    @POST("assignments/{id}/return") suspend fun returnLaptop(@Path("id") id: String, @Body body: JsonObject): Assignment
    @POST("replacements") suspend fun replace(@Body body: JsonObject): Replacement
    @GET("replacements") suspend fun replacements(@QueryMap q: Query): Page<Replacement>

    @GET("repairs") suspend fun repairs(@QueryMap q: Query): Page<Repair>
    @GET("repairs/{id}") suspend fun repairDetails(@Path("id") id: String): RepairDetails
    @POST("repairs") suspend fun createRepair(@Body body: JsonObject): Repair
    @PUT("repairs/{id}") suspend fun updateRepair(@Path("id") id: String, @Body body: JsonObject): Repair

    @GET("reports/{name}") suspend fun report(@Path("name") name: String, @QueryMap q: Query): Report
    @Streaming @GET("reports/{name}") suspend fun exportReport(@Path("name") name: String, @QueryMap q: Query): ResponseBody

    @GET("users") suspend fun users(@QueryMap q: Query): Page<User>
    @POST("users") suspend fun createUser(@Body body: JsonObject): User
    @PUT("users/{id}") suspend fun updateUser(@Path("id") id: String, @Body body: JsonObject): User
    @GET("audit-logs") suspend fun auditLogs(@QueryMap q: Query): Page<AuditLog>
}
