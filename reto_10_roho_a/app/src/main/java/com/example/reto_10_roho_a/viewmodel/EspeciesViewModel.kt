package com.example.reto_10_roho_a.viewmodel

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reto_10_roho_a.data.Categoria
import com.example.reto_10_roho_a.data.Especie
import com.example.reto_10_roho_a.data.EspecieRepository
import com.example.reto_10_roho_a.data.Filtro
import com.example.reto_10_roho_a.data.FotoRepository
import com.example.reto_10_roho_a.data.GbifRepository
import com.example.reto_10_roho_a.data.Grupo
import com.example.reto_10_roho_a.data.InfoGbif
import com.example.reto_10_roho_a.data.Nivel
import com.example.reto_10_roho_a.data.Observaciones
import com.example.reto_10_roho_a.data.cargarImagen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Estado de una carga asíncrona. */
sealed interface Carga<out T> {
    data object Cargando : Carga<Nothing>
    data class Error(val mensaje: String) : Carga<Nothing>
    data class Listo<T>(val datos: T) : Carga<T>
}

/** Pestañas de la pantalla principal. */
enum class Pestana(val titulo: String) { EXPLORAR("Explorar"), TAXONOMIA("Taxonomía"), RESUMEN("Resumen"), JUEGO("Juego") }

/** Pantallas apiladas encima de la principal. */
sealed interface Pantalla {
    data class Detalle(val especie: Especie) : Pantalla
    data class Mapa(val especie: Especie) : Pantalla
}

/** Estadísticas del listado completo. */
data class Resumen(
    val total: Int,
    val porCategoria: List<Pair<Categoria, Int>>,
    val porReino: List<Grupo>,
    val porClase: List<Grupo>,
)

/** Ficha cargada de GBIF (datos) y foto de una especie. */
class FichaEstado {
    var info by mutableStateOf<Carga<InfoGbif>>(Carga.Cargando)
    var foto by mutableStateOf<Bitmap?>(null)
    var credito by mutableStateOf<String?>(null)
    /** true mientras se busca/descarga la foto; así se distingue "cargando" de "sin foto". */
    var cargandoFoto by mutableStateOf(true)
}

/** Estado del juego "adivina la categoría". */
data class Pregunta(val especie: Especie, val respondida: Categoria? = null)

class EspeciesViewModel : ViewModel() {
    private val especies = EspecieRepository()
    private val gbif = GbifRepository()
    private val fotos = FotoRepository()

    // ---- Navegación ----
    var pestana by mutableStateOf(Pestana.EXPLORAR)
    val pila = mutableStateListOf<Pantalla>()

    fun abrirDetalle(e: Especie) {
        pila += Pantalla.Detalle(e)
        cargarDetalle(e)
    }

    fun abrirMapa(e: Especie) {
        pila += Pantalla.Mapa(e)
        cargarMapa(e)
    }

    /** Vuelve atrás; devuelve false si ya se está en la pantalla principal. */
    fun atras(): Boolean {
        if (pila.isEmpty()) return false
        pila.removeAt(pila.lastIndex)
        return true
    }

    // ---- Explorar (lista con filtros enviados al servicio) ----
    var filtro by mutableStateOf(Filtro())
        private set
    var lista by mutableStateOf<List<Especie>>(emptyList())
        private set
    var totalLista by mutableStateOf(0)
        private set
    var cargandoLista by mutableStateOf(false)
        private set
    /** true mientras se recarga la lista desde cero (cambio de filtros), no al paginar. */
    var recargandoLista by mutableStateOf(false)
        private set
    var errorLista by mutableStateOf<String?>(null)
        private set
    var clasesDisponibles by mutableStateOf<List<Grupo>>(emptyList())
        private set
    private var trabajoLista: Job? = null
    private var generacionLista = 0

    val hayMas get() = lista.size < totalLista

    fun cambiarFiltro(nuevo: Filtro, conRetraso: Boolean = false) {
        filtro = nuevo
        recargarLista(conRetraso)
    }

    /** Fija un filtro y muestra la pestaña Explorar (usado desde la taxonomía y el resumen). */
    fun explorar(nuevo: Filtro) {
        pestana = Pestana.EXPLORAR
        cambiarFiltro(nuevo)
    }

    fun cambiarReino(reino: String?) {
        cambiarFiltro(filtro.conNivel(Nivel.REINO, reino))
        clasesDisponibles = emptyList()
        viewModelScope.launch {
            runCatching { especies.grupos(Nivel.CLASE, Filtro(reino = reino)) }
                .onSuccess { clasesDisponibles = it }
        }
    }

    fun recargarLista(conRetraso: Boolean = false) {
        trabajoLista?.cancel()
        val generacion = ++generacionLista
        trabajoLista = viewModelScope.launch {
            recargandoLista = true
            if (conRetraso) delay(350)
            cargandoLista = true
            errorLista = null
            try {
                val total = especies.contar(filtro)
                lista = especies.listar(filtro, 0)
                totalLista = total
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorLista = e.message ?: "Error desconocido"
            } finally {
                // Un trabajo cancelado por un filtro más reciente no debe apagar el indicador del nuevo
                if (generacion == generacionLista) {
                    cargandoLista = false
                    recargandoLista = false
                }
            }
        }
    }

    fun cargarMas() {
        if (cargandoLista || !hayMas) return
        trabajoLista = viewModelScope.launch {
            cargandoLista = true
            try {
                lista = lista + especies.listar(filtro, lista.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorLista = e.message
            } finally {
                cargandoLista = false
            }
        }
    }

    // ---- Taxonomía (explorador jerárquico) ----
    var filtroTaxonomia by mutableStateOf(Filtro())
        private set
    var grupos by mutableStateOf<Carga<List<Grupo>>>(Carga.Cargando)
        private set

    /** Nivel que se está mostrando: el primero sin valor fijado. */
    val nivelActual: Nivel?
        get() = Nivel.entries.firstOrNull { filtroTaxonomia.valor(it) == null }

    fun cargarGrupos() {
        val nivel = nivelActual ?: return
        grupos = Carga.Cargando
        viewModelScope.launch {
            grupos = try {
                Carga.Listo(especies.grupos(nivel, filtroTaxonomia))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Carga.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun filtrarTaxonomiaPorCategoria(categoria: Categoria?) {
        filtroTaxonomia = filtroTaxonomia.copy(categoria = categoria)
        cargarGrupos()
    }

    fun bajarNivel(valor: String) {
        val nivel = nivelActual ?: return
        filtroTaxonomia = filtroTaxonomia.conNivel(nivel, valor)
        if (nivelActual != null) cargarGrupos() else explorar(filtroTaxonomia)
    }

    /** Sube hasta el nivel indicado (null = raíz). */
    fun subirA(nivel: Nivel?) {
        var f = filtroTaxonomia
        val desde = nivel?.ordinal ?: 0
        for (n in Nivel.entries) if (n.ordinal >= desde) f = f.conNivel(n, null)
        filtroTaxonomia = f
        cargarGrupos()
    }

    // ---- Resumen ----
    var resumen by mutableStateOf<Carga<Resumen>>(Carga.Cargando)
        private set

    fun cargarResumen() {
        resumen = Carga.Cargando
        viewModelScope.launch {
            resumen = try {
                val porCategoria = Categoria.entries.map { it to especies.contar(Filtro(categoria = it)) }
                Carga.Listo(
                    Resumen(
                        total = porCategoria.sumOf { it.second },
                        porCategoria = porCategoria,
                        porReino = especies.grupos(Nivel.REINO, Filtro()),
                        porClase = especies.grupos(Nivel.CLASE, Filtro()).take(8),
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Carga.Error(e.message ?: "Error desconocido")
            }
        }
    }

    // ---- Juego ----
    var pregunta by mutableStateOf<Carga<Pregunta>>(Carga.Cargando)
        private set
    var aciertos by mutableStateOf(0)
        private set
    var jugadas by mutableStateOf(0)
        private set

    fun nuevaPregunta() {
        pregunta = Carga.Cargando
        viewModelScope.launch {
            pregunta = try {
                val total = if (totalGeneral > 0) totalGeneral else especies.contar(Filtro()).also { totalGeneral = it }
                val especie = especies.aleatoria(total)
                if (especie?.categoria == null) throw IllegalStateException("No se pudo cargar una especie")
                cargarFicha(fichaJuego, especie)
                Carga.Listo(Pregunta(especie))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Carga.Error(e.message ?: "Error desconocido")
            }
        }
    }

    private var totalGeneral = 0

    fun responder(categoria: Categoria) {
        val actual = (pregunta as? Carga.Listo)?.datos ?: return
        if (actual.respondida != null) return
        jugadas++
        if (categoria == actual.especie.categoria) aciertos++
        pregunta = Carga.Listo(actual.copy(respondida = categoria))
    }

    // ---- Fichas (GBIF: datos ampliados; iNaturalist/GBIF: foto) ----
    val ficha = FichaEstado()      // pantalla de detalle
    val fichaJuego = FichaEstado() // pregunta actual del juego
    private var trabajoFichaJuego: Job? = null

    private fun cargarFicha(estado: FichaEstado, e: Especie) {
        estado.info = Carga.Cargando
        estado.foto = null
        estado.credito = null
        estado.cargandoFoto = true
        val trabajo = viewModelScope.launch {
            launch {
                try {
                    val foto = fotos.buscar(e.nombreCientifico, e.gbifKey)
                    if (foto != null) {
                        estado.foto = cargarImagen(foto.url)
                        if (estado.foto != null) estado.credito = "Foto: ${foto.credito} · ${foto.fuente}"
                    }
                } finally {
                    estado.cargandoFoto = false
                }
            }
            val key = e.gbifKey
            if (key == null) {
                estado.info = Carga.Error("Esta especie no tiene identificador GBIF.")
            } else {
                estado.info = try {
                    Carga.Listo(gbif.info(key))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Carga.Error(ex.message ?: "Error desconocido")
                }
            }
        }
        if (estado === fichaJuego) {
            trabajoFichaJuego?.cancel()
            trabajoFichaJuego = trabajo
        }
    }

    private fun cargarDetalle(e: Especie) = cargarFicha(ficha, e)

    fun reintentarDetalle() {
        (pila.lastOrNull() as? Pantalla.Detalle)?.let { cargarDetalle(it.especie) }
    }

    fun reintentarFichaJuego() {
        (pregunta as? Carga.Listo)?.datos?.especie?.let { cargarFicha(fichaJuego, it) }
    }

    // ---- Mapa (GBIF: observaciones) ----
    var observaciones by mutableStateOf<Carga<Observaciones>>(Carga.Cargando)
        private set
    var mapaDeCalor by mutableStateOf(true)

    private fun cargarMapa(e: Especie) {
        observaciones = Carga.Cargando
        val key = e.gbifKey
        if (key == null) {
            observaciones = Carga.Error("Esta especie no tiene identificador GBIF.")
            return
        }
        viewModelScope.launch {
            observaciones = try {
                Carga.Listo(gbif.observaciones(key))
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Carga.Error(ex.message ?: "Error desconocido")
            }
        }
    }

    fun reintentarMapa() {
        (pila.lastOrNull() as? Pantalla.Mapa)?.let { cargarMapa(it.especie) }
    }

    // Debe ir al final: arranca las cargas cuando todas las propiedades ya están inicializadas.
    init {
        recargarLista()
        cargarResumen()
        cargarGrupos()
        nuevaPregunta()
    }
}
