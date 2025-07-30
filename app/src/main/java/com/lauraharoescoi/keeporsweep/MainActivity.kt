package com.lauraharoescoi.keeporsweep

// Importaciones necesarias al principio del archivo
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // Este es el "lanzador" de la petición de permisos.
    // Se encarga de mostrar el diálogo al usuario y recibir su respuesta.
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // El usuario concedió el permiso. ¡Perfecto!
                // Aquí llamaremos a la función para cargar las fotos.
                Toast.makeText(this, "Permiso concedido. ¡Cargando fotos!", Toast.LENGTH_SHORT).show()
                loadPhotos()
            } else {
                // El usuario denegó el permiso.
                // Deberíamos mostrar un mensaje explicando por qué lo necesitamos.
                Toast.makeText(this, "Permiso denegado. La app no puede funcionar sin acceso a la galería.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Carga el diseño visual

        // Comprobamos si ya tenemos el permiso
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Ya tenemos permiso, podemos cargar las fotos directamente.
                Toast.makeText(this, "Ya tienes permiso. ¡Cargando fotos!", Toast.LENGTH_SHORT).show()
                loadPhotos()
            }
            else -> {
                // No tenemos permiso, así que lo solicitamos.
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }
    }

    private fun loadPhotos() {
        // --- PASO SIGUIENTE ---
        // Aquí irá la lógica para buscar las fotos en el dispositivo.
        // Por ahora, lo dejamos vacío.
    }
}