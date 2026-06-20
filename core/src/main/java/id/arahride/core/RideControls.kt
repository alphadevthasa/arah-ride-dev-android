package id.arahride.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Navy = Color(0xFF14243A)
private val Mint = Color(0xFFBDF4D0)

@Composable
fun RideHeader(role: String, snapshot: Snapshot?) {
    MaterialTheme {
        Column(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (role == "driver") "ARAH / DRIVER" else "ARAH", color = Color.White, fontWeight = FontWeight.Bold)
                Text(if (snapshot?.driverOnline == true) "● Driver online" else "○ Driver offline", color = Mint)
            }
            Text("Peta © OpenStreetMap contributors · OpenFreeMap", color = Color.LightGray,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun RidePanel(
    role: String, snapshot: Snapshot?, pickup: Point, dropoff: Point, selecting: String,
    error: String?, onSelect: (String) -> Unit, onAction: (String) -> Unit
) {
    MaterialTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (error != null) {
                Card(Modifier.fillMaxWidth()) { Text(error, Modifier.padding(14.dp), color = Color(0xFF9B1C1C)) }
                Spacer(Modifier.height(8.dp))
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp)) {
                    if (role == "customer") CustomerContent(snapshot, pickup, dropoff, selecting, onSelect, onAction)
                    else DriverContent(snapshot, onAction)
                }
            }
        }
    }
}

@Composable
private fun CustomerContent(snapshot: Snapshot?, pickup: Point, dropoff: Point, selecting: String,
                            onSelect: (String) -> Unit, onAction: (String) -> Unit) {
    val ride = snapshot?.ride
    when (ride?.status) {
        null -> {
            Text("Mau ke mana hari ini?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Ketuk peta untuk memilih titik. Pilih jenis titik dahulu.")
            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedButton(onClick = { onSelect("pickup") }) { Text(if (selecting == "pickup") "● Jemput" else "Jemput") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onSelect("dropoff") }) { Text(if (selecting == "dropoff") "● Tujuan" else "Tujuan") }
            }
            Text("Jemput: ${pickup.label()}")
            Text("Tujuan: ${dropoff.label()}")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onAction("create") }, enabled = snapshot?.driverOnline == true,
                colors = ButtonDefaults.buttonColors(containerColor = Navy), modifier = Modifier.fillMaxWidth()) {
                Text(if (snapshot?.driverOnline == true) "Pesan perjalanan" else "Menunggu Driver online")
            }
        }
        "requested" -> {
            Title("Mencari Driver")
            Text("Pesanan terkirim. Driver akan segera merespons.")
            ActionButton("Batalkan pesanan", "cancel", onAction)
        }
        "accepted" -> {
            Title("Driver menuju titik jemput")
            DriverInfo(ride)
            Text("Posisi Driver tampil langsung di peta.")
            ActionButton("Lihat posisi Driver", "focus_driver", onAction)
            ActionButton("Batalkan pesanan", "cancel", onAction)
        }
        "arrived" -> { Title("Driver sudah tiba"); DriverInfo(ride); Text("Temui Driver di titik jemput."); ActionButton("Lihat posisi Driver", "focus_driver", onAction) }
        "in_progress" -> { Title("Perjalanan berlangsung"); DriverInfo(ride); Text("Posisi Driver diperbarui langsung."); ActionButton("Lihat posisi Driver", "focus_driver", onAction) }
        "completed" -> { Title("Perjalanan selesai"); Text("Terima kasih sudah menggunakan Arah."); ActionButton("Pesan lagi", "reset", onAction) }
        "cancelled" -> { Title("Pesanan dibatalkan"); ActionButton("Buat pesanan baru", "reset", onAction) }
    }
}

@Composable
private fun DriverContent(snapshot: Snapshot?, onAction: (String) -> Unit) {
    if (snapshot == null) { Title("Menghubungkan..."); return }
    if (!snapshot.driverOnline) {
        Title("Siap menerima pesanan?")
        Text("Aktifkan status online. Lokasi akan dibagikan kepada Customer saat online.")
        ActionButton("Mulai online", "online", onAction)
        return
    }
    val ride = snapshot.ride
    when (ride?.status) {
        null, "completed", "cancelled" -> {
            Title("Anda online")
            Text("Menunggu pesanan baru di sekitar Anda.")
            ActionButton("Berhenti online", "offline", onAction)
        }
        "requested" -> {
            Title("Pesanan baru")
            Text("Jemput: ${ride.pickup.label()}")
            Text("Tujuan: ${ride.dropoff.label()}")
            ActionButton("Terima pesanan", "accept", onAction)
        }
        "accepted" -> { Title("Menuju penjemputan"); TripPoints(ride); ActionButton("Saya sudah tiba", "arrive", onAction) }
        "arrived" -> { Title("Menunggu Customer"); TripPoints(ride); ActionButton("Mulai perjalanan", "start", onAction) }
        "in_progress" -> { Title("Dalam perjalanan"); TripPoints(ride); ActionButton("Selesaikan perjalanan", "complete", onAction) }
    }
}

@Composable private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
}

@Composable private fun DriverInfo(ride: Ride) {
    Text("${ride.driverName} · ${ride.driverPlate}", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@Composable private fun TripPoints(ride: Ride) {
    Text("Jemput: ${ride.pickup.label()}")
    Text("Tujuan: ${ride.dropoff.label()}")
}

@Composable private fun ActionButton(label: String, action: String, onAction: (String) -> Unit) {
    Spacer(Modifier.height(12.dp))
    Button(onClick = { onAction(action) }, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text(label) }
}
