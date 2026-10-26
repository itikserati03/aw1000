package com.aw1000.manager

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun DataScreen(m: AppModel) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(0) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    m.logVersion // langgan kemas kini log
    val today = LocalDate.now()

    val dates: List<LocalDate>; val prevDates: List<LocalDate>
    if (mode == 0) {
        dates = listOf(date); prevDates = listOf(date.minusDays(1))
    } else {
        val ym = YearMonth.from(date)
        dates = (1..ym.lengthOfMonth()).map { ym.atDay(it) }
        val pm = ym.minusMonths(1)
        prevDates = (1..pm.lengthOfMonth()).map { pm.atDay(it) }
    }
    val cur = m.dataLog.sum(dates)
    val prev = m.dataLog.sum(prevDates)

    // data carta
    val chartDates: List<LocalDate> = if (mode == 0) (13 downTo 0).map { date.minusDays(it.toLong()) } else dates
    val rawVals = chartDates.map { m.dataLog.usage(it).total.toDouble() }
    val useGb = (rawVals.maxOrNull() ?: 0.0) >= 1e9
    val div = if (useGb) 1e9 else 1e6
    val vals = rawVals.map { (it / div).toFloat() }
    val labels = chartDates.mapIndexed { i, d -> if (i == 0 || i == chartDates.size - 1 || i == chartDates.size / 2) d.dayOfMonth.toString() else "" }
    val hi = if (mode == 0) chartDates.size - 1 else date.dayOfMonth - 1

    val label = if (mode == 0) "${date.dayOfMonth} ${Fmt.months[date.monthValue - 1]} ${date.year}"
    else "${Fmt.months[date.monthValue - 1]} ${date.year}"
    val canNext = if (mode == 0) date < today else YearMonth.from(date) < YearMonth.from(today)

    fun shift(dir: Int) {
        date = if (mode == 0) date.plusDays(dir.toLong()) else date.plusMonths(dir.toLong())
        if (date > today) date = today
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Data Penggunaan", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        SegTabs(listOf("Harian", "Bulanan"), mode) { mode = it }
        NeonCard(pad = 4.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { shift(-1) }) { Icon(Icons.Filled.ChevronLeft, "Sebelumnya", tint = C.cyan) }
                Text(
                    label, color = C.text, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickable {
                        DatePickerDialog(ctx, { _, y, mo, d -> date = LocalDate.of(y, mo + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth)
                            .also { it.datePicker.maxDate = System.currentTimeMillis() }.show()
                    }.padding(10.dp)
                )
                IconButton(onClick = { shift(1) }, enabled = canNext) {
                    Icon(Icons.Filled.ChevronRight, "Seterusnya", tint = if (canNext) C.cyan else C.muted)
                }
            }
        }
        NeonCard {
            Text("Jumlah Penggunaan", color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(Fmt.bytes(cur.total), color = C.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Turun ${Fmt.bytes(cur.rx)}  •  Naik ${Fmt.bytes(cur.tx)}", color = C.muted, fontSize = 12.sp)
            if (prev.total > 0) {
                val pct = (cur.total - prev.total) * 100.0 / prev.total
                Text(
                    "${if (pct >= 0) "▲" else "▼"} ${Fmt.d1(Math.abs(pct))}% berbanding ${if (mode == 0) "hari" else "bulan"} sebelumnya",
                    color = if (pct >= 0) C.warn else C.good, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            BarChart(vals, labels, hi, if (useGb) "GB" else "MB", Modifier.fillMaxWidth().height(170.dp))
        }
        NeonCard {
            Text("Penggunaan Setiap Peranti", color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            val clients = m.snap?.clients ?: emptyList()
            val list = cur.dev.entries.sortedByDescending { it.value }
            if (list.isEmpty()) {
                Text(
                    if (m.nlbwMsg != null) "Tiada data per peranti: ${m.nlbwMsg}"
                    else "Belum ada rekod per peranti untuk tempoh ini. Rekod bermula selepas bacaan nlbwmon kedua.",
                    color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                val top = list.first().value.toFloat()
                list.forEach { (mac, b) ->
                    val c = clients.firstOrNull { it.mac == mac }
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(c?.host?.takeIf { it != "-" } ?: mac, color = C.text, fontSize = 14.sp)
                            Text(Fmt.bytes(b), color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("${c?.ip ?: "-"}  •  $mac", color = C.muted, fontSize = 11.sp)
                        ThinBar(b.toFloat() / top)
                    }
                }
            }
        }
        NeonCard {
            Text("Cara data direkodkan", color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                "Jumlah diambil daripada kaunter trafik WAN router dan disimpan dalam telefon hanya semasa aplikasi dibuka; " +
                    "tempoh aplikasi tertutup tidak direkodkan. Data per peranti memerlukan pakej nlbwmon pada router " +
                    "(opkg install nlbwmon). Tiada sejarah dicipta sebelum aplikasi mula merekod.",
                color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
