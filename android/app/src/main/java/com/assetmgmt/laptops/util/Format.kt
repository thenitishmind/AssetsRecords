package com.assetmgmt.laptops.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
private val dateTimeFmt = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
private val inrFmt: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
    maximumFractionDigits = 2
    minimumFractionDigits = 0
}

/** Business dates are calendar days stored at UTC midnight, so they are rendered in UTC. */
fun fmtDate(iso: String?): String = runCatching {
    if (iso.isNullOrBlank()) "—" else dateFmt.format(Instant.parse(iso).atZone(ZoneOffset.UTC))
}.getOrDefault(iso ?: "—")

fun fmtDateTime(iso: String?): String = runCatching {
    if (iso.isNullOrBlank()) "—" else dateTimeFmt.format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrDefault(iso ?: "—")

fun fmtMoney(v: Double?): String = if (v == null) "—" else "₹" + inrFmt.format(v)

fun fmtInt(v: Int): String = NumberFormat.getIntegerInstance(Locale("en", "IN")).format(v)

fun today(): String = LocalDate.now().toString()

/** yyyy-MM-dd from an ISO instant (UTC), used to pre-fill date pickers. */
fun isoToDay(iso: String?): String = runCatching { Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate().toString() }.getOrDefault("")

fun String?.orDash(): String = if (this.isNullOrBlank()) "—" else this

fun prettyEnum(s: String?): String =
    s?.lowercase()?.split('_')?.joinToString(" ") { it.replaceFirstChar(Char::uppercase) } ?: "—"
