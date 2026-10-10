package com.assetmgmt.laptops.data.repository

import com.assetmgmt.laptops.data.local.SessionStore
import com.assetmgmt.laptops.data.remote.ApiService
import com.assetmgmt.laptops.data.remote.AppJson
import com.assetmgmt.laptops.data.remote.Asset
import com.assetmgmt.laptops.data.remote.AssetDetails
import com.assetmgmt.laptops.data.remote.Assignment
import com.assetmgmt.laptops.data.remote.AuditLog
import com.assetmgmt.laptops.data.remote.Branch
import com.assetmgmt.laptops.data.remote.BranchAssets
import com.assetmgmt.laptops.data.remote.Dashboard
import com.assetmgmt.laptops.data.remote.Employee
import com.assetmgmt.laptops.data.remote.EmployeeAssets
import com.assetmgmt.laptops.data.remote.ErrorEnvelope
import com.assetmgmt.laptops.data.remote.LoginRequest
import com.assetmgmt.laptops.data.remote.Page
import com.assetmgmt.laptops.data.remote.Query
import com.assetmgmt.laptops.data.remote.Repair
import com.assetmgmt.laptops.data.remote.RepairDetails
import com.assetmgmt.laptops.data.remote.Replacement
import com.assetmgmt.laptops.data.remote.Report
import com.assetmgmt.laptops.data.remote.SearchResult
import com.assetmgmt.laptops.data.remote.StockOverview
import com.assetmgmt.laptops.data.remote.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.IOException

/** Error shown to the user. Raw server / database errors are never surfaced. */
class ApiException(message: String, val code: String? = null, val details: JsonElement? = null) : Exception(message)

fun query(vararg pairs: Pair<String, Any?>): Query =
    buildMap { pairs.forEach { (k, v) -> if (v != null && v.toString().isNotBlank()) put(k, v.toString()) } }

/** Builds a JSON body. Blank values are omitted unless [keepBlank] (used for edits so fields can be cleared). */
fun body(vararg pairs: Pair<String, Any?>, keepBlank: Boolean = false): JsonObject = JsonObject(
    buildMap {
        pairs.forEach { (k, v) ->
            when (v) {
                null -> if (keepBlank) put(k, JsonNull)
                is Number -> put(k, JsonPrimitive(v))
                is Boolean -> put(k, JsonPrimitive(v))
                else -> {
                    val s = v.toString().trim()
                    if (s.isNotEmpty() || keepBlank) put(k, JsonPrimitive(s))
                }
            }
        }
    },
)

class AssetRepository(private val api: ApiService, private val session: SessionStore) {

    /** Bumps after every successful write so visible screens reload (dashboard counts etc.). */
    private val _version = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _version

    private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Result.failure(parseHttp(e))
    } catch (e: IOException) {
        Result.failure(ApiException("Cannot reach the server. Check your internet connection and the server address in Settings.", "NETWORK"))
    } catch (e: Exception) {
        Result.failure(ApiException("Something went wrong. Please try again.", "UNKNOWN"))
    }

    private suspend fun <T> mutate(block: suspend () -> T): Result<T> =
        call(block).also { if (it.isSuccess) _version.value += 1 }

    private fun parseHttp(e: HttpException): ApiException {
        val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val env = raw?.let { runCatching { AppJson.decodeFromString<ErrorEnvelope>(it) }.getOrNull() }
        val msg = env?.error?.message ?: when (e.code()) {
            401 -> "Your session has expired. Please sign in again."
            403 -> "You do not have permission to perform this action."
            404 -> "The requested record was not found."
            else -> "Unable to complete the request. Please try again."
        }
        return ApiException(msg, env?.error?.code ?: e.code().toString(), env?.error?.details)
    }

    // ---- auth ----
    suspend fun login(username: String, password: String): Result<User> =
        call { api.login(LoginRequest(username.trim(), password)) }.map { session.signIn(it.token, it.user); it.user }

    suspend fun refreshMe(): Result<User> = call { api.me().user }.onSuccess { session.updateUser(it) }
    suspend fun changePassword(current: String, new: String) =
        call { api.changePassword(body("currentPassword" to current, "newPassword" to new)) }

    // ---- read ----
    suspend fun dashboard(): Result<Dashboard> = call { api.dashboard() }
    suspend fun stock(): Result<StockOverview> = call { api.stock() }
    suspend fun stockAssets(q: Query): Result<Page<Asset>> = call { api.stockAssets(q) }
    suspend fun search(q: String): Result<SearchResult> = call { api.search(query("q" to q)) }
    suspend fun assets(q: Query): Result<Page<Asset>> = call { api.assets(q) }
    suspend fun assetDetails(id: String): Result<AssetDetails> = call { api.assetDetails(id) }
    suspend fun branches(q: Query): Result<Page<Branch>> = call { api.branches(q) }
    suspend fun branch(id: String): Result<Branch> = call { api.branch(id) }
    suspend fun branchAssets(id: String, q: Query): Result<BranchAssets> = call { api.branchAssets(id, q) }
    suspend fun employees(q: Query): Result<Page<Employee>> = call { api.employees(q) }
    suspend fun employee(id: String): Result<Employee> = call { api.employee(id) }
    suspend fun employeeAssets(id: String): Result<EmployeeAssets> = call { api.employeeAssets(id) }
    suspend fun repairs(q: Query): Result<Page<Repair>> = call { api.repairs(q) }
    suspend fun repairDetails(id: String): Result<RepairDetails> = call { api.repairDetails(id) }
    suspend fun replacements(q: Query): Result<Page<Replacement>> = call { api.replacements(q) }
    suspend fun report(name: String, q: Query): Result<Report> = call { api.report(name, q) }
    suspend fun exportReport(name: String, q: Query): Result<ResponseBody> = call { api.exportReport(name, q) }
    suspend fun users(q: Query): Result<Page<User>> = call { api.users(q) }
    suspend fun auditLogs(q: Query): Result<Page<AuditLog>> = call { api.auditLogs(q) }

    // ---- write ----
    suspend fun createBranch(b: JsonObject) = mutate { api.createBranch(b) }
    suspend fun updateBranch(id: String, b: JsonObject) = mutate { api.updateBranch(id, b) }
    suspend fun deactivateBranch(id: String) = mutate { api.deactivateBranch(id) }
    suspend fun createEmployee(b: JsonObject) = mutate { api.createEmployee(b) }
    suspend fun updateEmployee(id: String, b: JsonObject) = mutate { api.updateEmployee(id, b) }
    suspend fun deactivateEmployee(id: String) = mutate { api.deactivateEmployee(id) }
    suspend fun createAsset(b: JsonObject) = mutate { api.createAsset(b) }
    suspend fun updateAsset(id: String, b: JsonObject) = mutate { api.updateAsset(id, b) }
    suspend fun retireAsset(id: String, remarks: String?) =
        mutate { api.changeAssetStatus(id, body("status" to "RETIRED", "remarks" to remarks)) }
    suspend fun markSold(id: String) =
        mutate { api.changeAssetStatus(id, body("status" to "SOLD", "remarks" to "Sold after being marked dead")) }
    suspend fun markDead(id: String, reason: String, remarks: String?) =
        mutate { api.markDead(id, body("reason" to reason, "remarks" to remarks)) }
    suspend fun assign(b: JsonObject): Result<Assignment> = mutate { api.assign(b) }
    suspend fun updateAssignment(id: String, b: JsonObject) = mutate { api.updateAssignment(id, b) }
    suspend fun returnLaptop(assignmentId: String, remarks: String?) =
        mutate { api.returnLaptop(assignmentId, body("remarks" to remarks)) }
    suspend fun replace(b: JsonObject): Result<Replacement> = mutate { api.replace(b) }
    suspend fun createRepair(b: JsonObject): Result<Repair> = mutate { api.createRepair(b) }
    suspend fun updateRepair(id: String, b: JsonObject): Result<Repair> = mutate { api.updateRepair(id, b) }
    suspend fun createUser(b: JsonObject) = mutate { api.createUser(b) }
    suspend fun updateUser(id: String, b: JsonObject) = mutate { api.updateUser(id, b) }
}
