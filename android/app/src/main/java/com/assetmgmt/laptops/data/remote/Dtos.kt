package com.assetmgmt.laptops.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class Page<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 25,
    val hasMore: Boolean = false,
)

@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class LoginResponse(val token: String, val user: User)

@Serializable
data class User(
    val userId: String,
    val username: String,
    val name: String,
    val role: String,
    val status: String = "ACTIVE",
    val lastLoginAt: String? = null,
) {
    val isAdmin get() = role == "ADMIN"
}

@Serializable data class MeResponse(val user: User)

@Serializable
data class Branch(
    val branchId: String,
    val name: String,
    val code: String,
    val city: String = "",
    val state: String = "",
    val address: String? = null,
    val contactNumber: String? = null,
    val status: String = "ACTIVE",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val totalAssets: Int = 0,
    val assigned: Int = 0,
    val available: Int = 0,
    val repair: Int = 0,
    val dead: Int = 0,
    val sold: Int = 0,
    val retired: Int = 0,
    val employeeCount: Int = 0,
    val repairCost: Double = 0.0,
)

@Serializable
data class Employee(
    val employeeId: String,
    val employeeCode: String,
    val name: String,
    val branchId: String,
    val branchName: String? = null,
    val branchCode: String? = null,
    val department: String? = null,
    val designation: String? = null,
    val contactNumber: String? = null,
    val email: String? = null,
    val joiningDate: String? = null,
    val status: String = "ACTIVE",
    val currentAssetId: String? = null,
    val currentAssetSerial: String? = null,
    val currentAssetModel: String? = null,
    val currentAssetBrand: String? = null,
    val currentAssetStatus: String? = null,
    val currentAssetTitle: String? = null,
    val assignmentDate: String? = null,
    val assignmentStatus: String? = null,
    val handoverStatus: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class DeadInfo(
    val deadDate: String? = null,
    val reason: String? = null,
    val remarks: String? = null,
    val previousEmployeeId: String? = null,
    val markedBy: String? = null,
)

@Serializable
data class SoldInfo(
    val soldDate: String? = null,
    val remarks: String? = null,
    val previousEmployeeId: String? = null,
    val soldBy: String? = null,
)

@Serializable
data class Asset(
    val assetId: String,
    val assetTag: String? = null,
    val serialNumber: String = "",
    val brand: String = "",
    val model: String = "",
    val processor: String? = null,
    val ram: String? = null,
    val storage: String? = null,
    val operatingSystem: String? = null,
    val purchaseDate: String? = null,
    val purchasePrice: Double? = null,
    val warrantyStartDate: String? = null,
    val warrantyEndDate: String? = null,
    val status: String = "AVAILABLE",
    val currentBranchId: String = "",
    val currentEmployeeId: String? = null,
    val branchName: String? = null,
    val branchCode: String? = null,
    val employeeName: String? = null,
    val employeeCode: String? = null,
    val employeeDesignation: String? = null,
    val employeeDepartment: String? = null,
    val displayEmployeeId: String? = null,
    val displayEmployeeName: String? = null,
    val displayEmployeeCode: String? = null,
    val assignmentId: String? = null,
    val assignmentDate: String? = null,
    val repairCount: Int = 0,
    val totalRepairCost: Double = 0.0,
    val lastRepairDate: String? = null,
    val lastRepairCost: Double = 0.0,
    val maxRepairCost: Double = 0.0,
    val avgRepairCost: Double = 0.0,
    val deadInfo: DeadInfo? = null,
    val soldInfo: SoldInfo? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val title get() = "$brand $model".trim()
}

@Serializable
data class Assignment(
    val assignmentId: String,
    val assetId: String,
    val employeeId: String,
    val branchId: String,
    val assignmentDate: String? = null,
    val releaseDate: String? = null,
    val assignedBy: String? = null,
    val remarks: String? = null,
    val status: String = "ACTIVE",
    val type: String? = null,
    val releaseReason: String? = null,
    val assetStatusAfter: String? = null,
    val employeeName: String? = null,
    val employeeCode: String? = null,
    val serialNumber: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val branchName: String? = null,
    val assetTitle: String? = null,
    val employeeDesignation: String? = null,
    val employeeDepartment: String? = null,
)

@Serializable
data class Repair(
    val repairId: String,
    val assetId: String,
    val employeeId: String? = null,
    val branchId: String? = null,
    val repairDate: String? = null,
    val issueType: String = "",
    val problemDescription: String? = null,
    val vendor: String? = null,
    val status: String = "SENT_FOR_REPAIR",
    val estimatedCost: Double? = null,
    val finalCost: Double? = null,
    val repairStartDate: String? = null,
    val repairCompletionDate: String? = null,
    val warranty: String? = null,
    val remarks: String? = null,
    val createdBy: String? = null,
    val serialNumber: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val assetStatus: String? = null,
    val employeeName: String? = null,
    val employeeCode: String? = null,
    val employeeDesignation: String? = null,
    val branchName: String? = null,
)

@Serializable
data class RepairDetails(
    val repair: Repair,
    val asset: Asset? = null,
    val previousRepairs: List<Repair> = emptyList(),
)

@Serializable
data class Replacement(
    val replacementId: String,
    val employeeId: String? = null,
    val branchId: String? = null,
    val oldAssetId: String,
    val newAssetId: String,
    val replacementDate: String? = null,
    val reason: String = "",
    val oldAssetStatus: String? = null,
    val remarks: String? = null,
    val employeeName: String? = null,
    val employeeCode: String? = null,
    val branchName: String? = null,
    val oldSerialNumber: String? = null,
    val newSerialNumber: String? = null,
    val oldModel: String? = null,
    val newModel: String? = null,
    val oldBrand: String? = null,
    val newBrand: String? = null,
    val designation: String? = null,
    val handoverStatus: String? = null,
    val handoverDate: String? = null,
    val status: String? = null,
    val handoverReason: String? = null,
    val handoverRemarks: String? = null,
    val previousAssignmentDate: String? = null,
    val currentAssignmentDate: String? = null,
    val currentAssignmentId: String? = null,
)

@Serializable
data class HistoryEvent(
    val historyId: String,
    val assetId: String,
    val eventType: String,
    val employeeId: String? = null,
    val employeeName: String? = null,
    val branchName: String? = null,
    val previousStatus: String? = null,
    val newStatus: String? = null,
    val date: String? = null,
    val user: String? = null,
    val remarks: String? = null,
)

@Serializable
data class RepairSummary(
    val repairCount: Int = 0,
    val totalRepairCost: Double = 0.0,
    val lastRepairDate: String? = null,
    val latestRepairCost: Double = 0.0,
    val averageRepairCost: Double = 0.0,
    val highestRepairCost: Double = 0.0,
)

@Serializable
data class ReplacementSummary(
    val previousAssetId: String,
    val replacementDate: String? = null,
    val reason: String = "",
    val employeeName: String? = null,
)

@Serializable
data class AssetDetails(
    val asset: Asset,
    val currentAssignment: Assignment? = null,
    val repairSummary: RepairSummary = RepairSummary(),
    val replacementSummary: ReplacementSummary? = null,
    val repairs: List<Repair> = emptyList(),
    val replacements: List<Replacement> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
    val lifecycle: List<HistoryEvent> = emptyList(),
)

@Serializable
data class PreviousAsset(
    val assignmentId: String,
    val assetId: String,
    val serialNumber: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val assignmentDate: String? = null,
    val releaseDate: String? = null,
    val releaseReason: String? = null,
    val statusAfterRelease: String? = null,
    val currentAssetStatus: String? = null,
)

@Serializable
data class EmployeeAssets(
    val employee: Employee,
    val current: Asset? = null,
    val previous: List<PreviousAsset> = emptyList(),
    val repairs: List<Repair> = emptyList(),
    val replacements: List<Replacement> = emptyList(),
)

@Serializable
data class BranchAssets(val branch: Branch, val assets: Page<Asset>)

@Serializable
data class StockSummary(
    val total: Int = 0,
    val available: Int = 0,
    val assigned: Int = 0,
    val repair: Int = 0,
    val dead: Int = 0,
    val retired: Int = 0,
    val sold: Int = 0,
)

@Serializable
data class BranchStock(
    val branchId: String,
    val branchName: String = "",
    val branchCode: String = "",
    val total: Int = 0,
    val available: Int = 0,
    val assigned: Int = 0,
    val repair: Int = 0,
    val dead: Int = 0,
    val retired: Int = 0,
    val sold: Int = 0,
)

@Serializable
data class StockOverview(val summary: StockSummary, val branches: List<BranchStock> = emptyList())

@Serializable
data class DashboardTotals(
    val totalAssets: Int = 0,
    val assigned: Int = 0,
    val available: Int = 0,
    val repair: Int = 0,
    val dead: Int = 0,
    val retired: Int = 0,
    val sold: Int = 0,
    val branches: Int = 0,
    val employeesWithAssets: Int = 0,
    val totalEmployees: Int = 0,
    val totalRepairCost: Double = 0.0,
    val totalRepairs: Int = 0,
)

@Serializable data class StatusCount(val status: String, val count: Int)
@Serializable data class MonthCost(val month: String, val cost: Double = 0.0, val count: Int = 0)
@Serializable data class BranchCost(val branchId: String? = null, val branchName: String? = null, val cost: Double = 0.0, val count: Int = 0)

@Serializable
data class Dashboard(
    val totals: DashboardTotals,
    val statusChart: List<StatusCount> = emptyList(),
    val branchWise: List<BranchStock> = emptyList(),
    val repairCostByMonth: List<MonthCost> = emptyList(),
    val repairCostByBranch: List<BranchCost> = emptyList(),
)

@Serializable
data class SearchResult(
    val query: String = "",
    val exactAssetId: String? = null,
    val assets: List<Asset> = emptyList(),
    val employees: List<Employee> = emptyList(),
    val branches: List<Branch> = emptyList(),
)

@Serializable data class ReportColumn(val key: String, val label: String, val type: String? = null)

@Serializable
data class Report(
    val name: String = "",
    val title: String = "",
    val columns: List<ReportColumn> = emptyList(),
    val rows: List<JsonObject> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val hasMore: Boolean = false,
    val totals: JsonObject? = null,
)

@Serializable data class AuditUser(val userId: String? = null, val username: String = "", val role: String? = null)

@Serializable
data class AuditLog(
    val auditId: String,
    val action: String,
    val entityType: String = "",
    val entityId: String = "",
    val user: AuditUser = AuditUser(),
    val oldValues: JsonElement? = null,
    val newValues: JsonElement? = null,
    val remarks: String? = null,
    val date: String? = null,
)

@Serializable data class ErrorBody(val code: String? = null, val message: String? = null, val details: JsonElement? = null)
@Serializable data class ErrorEnvelope(val error: ErrorBody? = null)
